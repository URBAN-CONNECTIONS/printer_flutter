package com.urbanconnections.printer_flutter

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The order of open, jobs, close, cancel and reset, on fake transports. */
class PrinterSessionTest {
  private lateinit var watchdog: ScheduledExecutorService
  private val transports = mutableListOf<FakeTransport>()
  private val limits = JobLimits(writeTimeoutMs = 2_000, completionTimeoutMs = 300)

  @BeforeTest
  fun setUp() {
    watchdog = Executors.newSingleThreadScheduledExecutor()
  }

  @AfterTest
  fun tearDown() {
    watchdog.shutdownNow()
  }

  private fun session(
    sleeper: (Long) -> Unit = {},
    closeGraceMs: Long = 0,
    onCancelWindow: () -> Unit = {},
    beforeCancel: () -> Unit = {},
    clock: () -> Long = { 0L },
    configure: (FakeTransport) -> Unit = {},
  ) = PrinterSession(
    newConnection = {
      val transport = FakeTransport()
      configure(transport)
      transports.add(transport)
      PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    },
    sleeper = sleeper,
    resetSettleMs = 300,
    closeGraceMs = closeGraceMs,
    onCancelWindow = onCancelWindow,
    beforeCancel = beforeCancel,
    clock = clock,
  )

  private fun PrinterSession.atomicJob(
    phases: PhaseTimer = PhaseTimer(),
    send: (PrinterConnection) -> Unit = { it.write("JOB".toByteArray(), 1_000) },
  ) = runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = phases, send = send)

  /** Runs an atomic call that is cancelled while it sends; [beforeItEnds] runs after the cancel. */
  private fun PrinterSession.cancelledAtomicJob(beforeItEnds: () -> Unit = {}) {
    val error = assertFailsWith<PrinterException> {
      atomicJob {
        cancel()
        beforeItEnds()
        it.write("CUT".toByteArray(), 1_000)
      }
    }
    assertEquals(PrinterErrorCode.CANCELLED, error.code)
  }

  @Test
  fun runJob_withoutAnOpenConnectionFailsWithNotConnected() {
    val session = session()

    val error = assertFailsWith<PrinterException> {
      session.runJob(closePort = false, limits = limits) { it.write("x".toByteArray(), 1_000) }
    }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
  }

  @Test
  fun runJob_withClosePortSendsTheRequestWaitsForTheMarkerAndCloses() {
    val session = session { it.autoReply = true }
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    val result = session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }

    assertEquals("Success", result)
    val transport = transports.single()
    assertContentEquals(
      "JOB".toByteArray() + PrinterSession.COMPLETION_REQUEST,
      transport.written.toByteArray(),
    )
    assertTrue(transport.closed)
  }

  @Test
  fun runJob_withClosePortAndNoConfirmationWaitsTheGraceTimeThenCloses() {
    val slept = mutableListOf<Long>()
    val session = session(sleeper = { slept.add(it) }, closeGraceMs = PrinterSession.CLOSE_GRACE_MS)
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    val result = session.runJob(closePort = true, limits = limits, confirmCompletion = false) {
      it.write("JOB".toByteArray(), 1_000)
    }

    assertEquals("Success", result)
    assertEquals(listOf(PrinterSession.CLOSE_GRACE_MS), slept)
    val transport = transports.single()
    assertContentEquals("JOB".toByteArray(), transport.written.toByteArray()) // no completion request
    assertTrue(transport.closed)
  }

  @Test
  fun runJob_withClosePortByDefaultConfirmsAndDoesNotSleep() {
    val slept = mutableListOf<Long>()
    val session = session(sleeper = { slept.add(it) }, closeGraceMs = PrinterSession.CLOSE_GRACE_MS) {
      it.autoReply = true
    }
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }

    assertTrue(slept.isEmpty())
    assertContentEquals(
      "JOB".toByteArray() + PrinterSession.COMPLETION_REQUEST,
      transports.single().written.toByteArray(),
    )
    assertTrue(transports.single().closed)
  }

  @Test
  fun runJob_withoutClosePortIgnoresConfirmCompletion() {
    val slept = mutableListOf<Long>()
    val session = session(sleeper = { slept.add(it) })
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    session.runJob(closePort = false, limits = limits, confirmCompletion = false) {
      it.write("JOB".toByteArray(), 1_000)
    }

    assertTrue(slept.isEmpty())
    assertFalse(transports.single().closed)
  }

  @Test
  fun runJob_withoutClosePortKeepsTheConnectionOpen() {
    val session = session()
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    session.runJob(closePort = false, limits = limits) { it.write("JOB".toByteArray(), 1_000) }

    val transport = transports.single()
    assertContentEquals("JOB".toByteArray(), transport.written.toByteArray())
    assertFalse(transport.closed)
  }

  @Test
  fun runJob_whenThePrinterDoesNotAnswerTimesOutAndTheNextJobRunsOnANewConnection() {
    val session = session()
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    val error = assertFailsWith<PrinterException> {
      session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }
    }
    assertEquals(PrinterErrorCode.TIMEOUT, error.code)
    assertTrue(transports[0].closed)

    // The failed job left no connection behind: a print fails fast, a new open works.
    val notConnected = assertFailsWith<PrinterException> {
      session.runJob(closePort = false, limits = limits) { it.write("x".toByteArray(), 1_000) }
    }
    assertEquals(PrinterErrorCode.NOT_CONNECTED, notConnected.code)
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    session.runJob(closePort = false, limits = limits) { it.write("NEXT".toByteArray(), 1_000) }
    assertEquals(2, transports.size)
    assertContentEquals("NEXT".toByteArray(), transports[1].written.toByteArray())
  }

  @Test
  fun open_afterAFailedJobResetsThePrinterBeforeConnectingWhenAsked() {
    val session = session()
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    assertFailsWith<PrinterException> {
      session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }
    }

    session.open("AA:BB", 1_000, resetAfterFailure = true)

    // transports: the failed job, the reset link, the new connection.
    assertEquals(3, transports.size)
    assertContentEquals(PrinterSession.RESET_COMMAND, transports[1].written.toByteArray())
    assertTrue(transports[1].closed)
    assertTrue(transports[2].written.size() == 0)
    assertFalse(transports[2].closed)
  }

  @Test
  fun open_doesNotResetAfterASuccessfulJob() {
    val session = session { it.autoReply = true }
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }

    session.open("AA:BB", 1_000, resetAfterFailure = true)

    assertEquals(2, transports.size)
    assertTrue(transports.none { it.written.toByteArray().contentEquals(PrinterSession.RESET_COMMAND) })
  }

  @Test
  fun open_doesNotResetWhenNotAskedEvenAfterAFailure() {
    val session = session()
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    assertFailsWith<PrinterException> {
      session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }
    }

    session.open("AA:BB", 1_000, resetAfterFailure = false)

    assertEquals(2, transports.size)
  }

  @Test
  fun open_resetsOnlyOnceAfterAFailure() {
    val session = session()
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    assertFailsWith<PrinterException> {
      session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }
    }
    session.open("AA:BB", 1_000, resetAfterFailure = true)
    assertEquals(3, transports.size)

    session.open("AA:BB", 1_000, resetAfterFailure = true)

    assertEquals(4, transports.size) // only the new connection, no second reset
  }

  @Test
  fun open_whenTheConnectionFailsThrowsNotConnected() {
    val session = session { it.failConnect = true }

    val error = assertFailsWith<PrinterException> {
      session.open("AA:BB", 1_000, resetAfterFailure = false)
    }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
    assertTrue(transports.single().closed)
  }

  @Test
  fun cancel_whenNothingIsRunningDoesNotCloseTheIdleConnection() {
    val session = session()
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    session.cancel()

    session.runJob(closePort = false, limits = limits) { it.write("JOB".toByteArray(), 1_000) }
    assertFalse(transports.single().closed)
    assertContentEquals("JOB".toByteArray(), transports.single().written.toByteArray())
  }

  @Test
  fun cancel_duringAJobEndsItWithCancelledAndTheNextJobRuns() {
    val session = session { it.blockWrites = true }
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    val started = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    val worker = thread {
      try {
        session.runJob(closePort = false, limits = limits) {
          started.countDown()
          it.write("JOB".toByteArray(), 5_000)
        }
      } catch (e: Throwable) {
        failure.set(e)
      }
    }
    assertTrue(started.await(2, TimeUnit.SECONDS))

    session.cancel()
    worker.join(3_000)

    assertEquals(PrinterErrorCode.CANCELLED, (failure.get() as PrinterException).code)
    assertTrue(transports[0].closed)
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    assertEquals(2, transports.size)
  }

  @Test
  fun cancel_whileThePrinterSettlesAfterAResetEndsOpenWithCancelled() {
    lateinit var session: PrinterSession
    var sleeps = 0
    session = session(sleeper = {
      sleeps++
      if (sleeps == 1) session.cancel()
    })
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    assertFailsWith<PrinterException> {
      session.runJob(closePort = true, limits = limits) { it.write("JOB".toByteArray(), 1_000) }
    }
    val before = transports.size

    val error = assertFailsWith<PrinterException> {
      session.open("AA:BB", 1_000, resetAfterFailure = true)
    }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertEquals(1, sleeps) // the wait ended at once
    assertEquals(before + 1, transports.size) // the reset link only, no new connection
  }

  @Test
  fun runAtomic_confirmingWritesTheJobAndTheRequestWaitsForTheMarkerAndCloses() {
    val session = session { it.autoReply = true }

    val result = session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
      it.write("JOB".toByteArray(), 1_000)
    }

    assertEquals("Success", result)
    val transport = transports.single()
    assertContentEquals(
      "JOB".toByteArray() + PrinterSession.COMPLETION_REQUEST,
      transport.written.toByteArray(),
    )
    assertTrue(transport.closed)
  }

  @Test
  fun runAtomic_withoutConfirmationSkipsTheMarkerWaitsTheGraceTimeAndCloses() {
    val slept = mutableListOf<Long>()
    val session = session(sleeper = { slept.add(it) }, closeGraceMs = PrinterSession.CLOSE_GRACE_MS)

    val result = session.runAtomic("AA:BB", 1_000, confirmCompletion = false, limits = limits, phases = PhaseTimer()) {
      it.write("JOB".toByteArray(), 1_000)
    }

    assertEquals("Success", result)
    assertEquals(listOf(PrinterSession.CLOSE_GRACE_MS), slept)
    val transport = transports.single()
    assertContentEquals("JOB".toByteArray(), transport.written.toByteArray()) // no completion request
    assertTrue(transport.closed)
  }

  @Test
  fun runAtomic_whenTheConnectionCannotBeOpenedFailsWithNotConnectedAndLeavesNothingOpen() {
    var sent = false
    val session = session { it.failConnect = true }

    val error = assertFailsWith<PrinterException> {
      session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) { sent = true }
    }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
    assertFalse(sent)
    assertTrue(transports.single().closed)
    // Nothing is left open: a job on the manual connection fails fast.
    val notConnected = assertFailsWith<PrinterException> {
      session.runJob(closePort = false, limits = limits) { it.write("x".toByteArray(), 1_000) }
    }
    assertEquals(PrinterErrorCode.NOT_CONNECTED, notConnected.code)
  }

  @Test
  fun runAtomic_whenTheMarkerNeverArrivesFailsWithTimeoutAfterSendingEverythingAndCloses() {
    val session = session()

    val error = assertFailsWith<PrinterException> {
      session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
        it.write("JOB".toByteArray(), 1_000)
      }
    }

    assertEquals(PrinterErrorCode.TIMEOUT, error.code)
    val transport = transports.single()
    assertContentEquals(
      "JOB".toByteArray() + PrinterSession.COMPLETION_REQUEST,
      transport.written.toByteArray(),
    )
    assertTrue(transport.closed)
  }

  @Test
  fun runAtomic_cancelDuringTheJobEndsItWithCancelledAndClosesTheConnection() {
    val session = session { it.blockWrites = true }
    val started = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    val worker = thread {
      try {
        session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
          started.countDown()
          it.write("JOB".toByteArray(), 5_000)
        }
      } catch (e: Throwable) {
        failure.set(e)
      }
    }
    assertTrue(started.await(2, TimeUnit.SECONDS))

    session.cancel()
    worker.join(3_000)

    assertEquals(PrinterErrorCode.CANCELLED, (failure.get() as PrinterException).code)
    assertTrue(transports.single().closed)
  }

  @Test
  fun runAtomic_whenTheJobFailsClosesTheConnectionAndKeepsTheError() {
    val session = session()

    val error = assertFailsWith<PrinterException> {
      session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
        throw PrinterException(PrinterErrorCode.CONNECTION_LOST, "lost")
      }
    }

    assertEquals(PrinterErrorCode.CONNECTION_LOST, error.code)
    assertTrue(transports.single().closed)
  }

  @Test
  fun runAtomic_whenTheJobThrowsAnythingElseClosesTheConnection() {
    val session = session()

    assertFailsWith<IllegalStateException> {
      session.runAtomic("AA:BB", 1_000, confirmCompletion = false, limits = limits, phases = PhaseTimer()) {
        throw IllegalStateException("render failed")
      }
    }

    assertTrue(transports.single().closed)
  }

  @Test
  fun runAtomic_takesOverAConnectionLeftOpenByHand() {
    val session = session { it.autoReply = true }
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    assertFalse(transports[0].closed)

    val result = session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
      it.write("JOB".toByteArray(), 1_000)
    }

    assertEquals("Success", result)
    assertEquals(2, transports.size)
    assertTrue(transports[0].closed)
    assertEquals(0, transports[0].written.size()) // the manual connection carried nothing
    assertContentEquals(
      "JOB".toByteArray() + PrinterSession.COMPLETION_REQUEST,
      transports[1].written.toByteArray(),
    )
    assertTrue(transports[1].closed)
  }

  @Test
  fun runAtomic_recordsEachPhaseAndTakesTheRenderOutOfTheSendTime() {
    var now = 0L
    val phases = PhaseTimer { now }
    val session = session(sleeper = { now += it }, closeGraceMs = 50) { now += 5 } // connect takes 5 ms

    session.runAtomic("AA:BB", 1_000, confirmCompletion = false, limits = limits, phases = phases) {
      now += 20
      phases.add(PrintPhase.RENDER, 20)
      now += 10
      it.write("JOB".toByteArray(), 1_000)
    }

    assertEquals(
      "Atomic print phases: connect=5ms render=20ms send=10ms confirm=50ms",
      phases.formatLine(),
    )
  }

  @Test
  fun runAtomic_aPdfStyleJobShowsTheRenderOfEveryPageAndWritesOnlyCountAsSend() {
    var now = 0L
    val phases = PhaseTimer { now }
    val session = session(sleeper = { now += it }, closeGraceMs = 0) { now += 4 }

    // Like printPdf: render a page, write it, render the next one, write it.
    session.runAtomic("AA:BB", 1_000, confirmCompletion = false, limits = limits, phases = phases) { conn ->
      repeat(2) {
        val page = phases.measureRender {
          now += 30
          "PAGE".toByteArray()
        }
        now += 6
        conn.write(page, 1_000)
      }
    }

    assertEquals(60, phases.millis(PrintPhase.RENDER))
    assertEquals(12, phases.millis(PrintPhase.SEND))
    assertEquals(
      "Atomic print phases: connect=4ms render=60ms send=12ms confirm=0ms",
      phases.formatLine(),
    )
  }

  @Test
  fun runAtomic_aTsplOrRawJobShowsZeroRender() {
    var now = 0L
    val phases = PhaseTimer { now }
    val session = session(sleeper = { now += it }, closeGraceMs = 0)

    // Like sendTspl and sendRawBytes: only a write, nothing is rendered.
    session.runAtomic("AA:BB", 1_000, confirmCompletion = false, limits = limits, phases = phases) {
      now += 9
      it.write("SIZE 72 mm, 40 mm".toByteArray(), 1_000)
    }

    assertEquals(0, phases.millis(PrintPhase.RENDER))
    assertEquals(9, phases.millis(PrintPhase.SEND))
  }

  @Test
  fun measureRender_withoutATimerOnlyRunsTheBlock() {
    val timer: PhaseTimer? = null

    val value = timer.measureRender { 42 }

    assertEquals(42, value)
  }

  @Test
  fun runAtomic_recordsThePhasesReachedWhenItFails() {
    var now = 0L
    val phases = PhaseTimer { now }
    val session = session { now += 7 }

    assertFailsWith<PrinterException> {
      session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = phases) {
        now += 3
        throw PrinterException(PrinterErrorCode.CONNECTION_LOST, "lost")
      }
    }

    assertEquals(
      "Atomic print phases: connect=7ms render=0ms send=3ms confirm=0ms",
      phases.formatLine(),
    )
  }

  @Test
  fun runAtomic_aCancelWhileNothingRunsDoesNotAffectTheNextCall() {
    val session = session { it.autoReply = true }

    session.cancel()
    val result = session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
      it.write("JOB".toByteArray(), 1_000)
    }

    assertEquals("Success", result)
  }

  @Test
  fun runAtomic_inAFifoQueueRunsInOrderAndACancelEndsOnlyTheRunningCall() {
    val session = session { if (transports.isEmpty()) it.blockWrites = true else it.autoReply = true }
    val executor = Executors.newSingleThreadExecutor()
    try {
      val started = CountDownLatch(1)
      val order = Collections.synchronizedList(mutableListOf<String>())

      fun atomic(name: String, onStart: () -> Unit = {}) = executor.submit<String> {
        session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
          onStart()
          order.add(name)
          it.write(name.toByteArray(), 5_000)
        }
      }

      val first = atomic("A") { started.countDown() }
      assertTrue(started.await(2, TimeUnit.SECONDS))
      val second = atomic("B")
      val third = atomic("C")

      session.cancel()

      val cancelled = assertFailsWith<ExecutionException> { first.get(3, TimeUnit.SECONDS) }
      assertEquals(PrinterErrorCode.CANCELLED, (cancelled.cause as PrinterException).code)
      assertEquals("Success", second.get(3, TimeUnit.SECONDS))
      assertEquals("Success", third.get(3, TimeUnit.SECONDS))

      assertEquals(listOf("A", "B", "C"), order.toList())
      assertEquals(3, transports.size) // each call on its own connection
      assertTrue(transports.all { it.closed })
      assertContentEquals("B".toByteArray() + PrinterSession.COMPLETION_REQUEST, transports[1].written.toByteArray())
      assertContentEquals("C".toByteArray() + PrinterSession.COMPLETION_REQUEST, transports[2].written.toByteArray())
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun runAtomic_aCancelStoppedAtTheBoundaryNeverCancelsTheNextQueuedCall() {
    // The cancel thread is held after it saw call A running and before it flags it. Meanwhile A is
    // released, so the boundary between A and B is crossed while the cancel is still undecided.
    val reachedWindow = CountDownLatch(1)
    val releaseCancel = CountDownLatch(1)
    val firstCancel = AtomicBoolean(true)
    val session = session(
      onCancelWindow = {
        if (firstCancel.compareAndSet(true, false)) {
          reachedWindow.countDown()
          releaseCancel.await(5, TimeUnit.SECONDS)
        }
      },
    ) { it.autoReply = true }
    val executor = Executors.newSingleThreadExecutor()
    try {
      val aStarted = CountDownLatch(1)
      val finishA = CountDownLatch(1)
      val bStarted = CountDownLatch(1)
      val finishB = CountDownLatch(1)
      val executorThread = AtomicReference<Thread>()

      val a = executor.submit<String> {
        session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
          executorThread.set(Thread.currentThread())
          aStarted.countDown()
          finishA.await(5, TimeUnit.SECONDS)
          it.write("A".toByteArray(), 5_000)
        }
      }
      assertTrue(aStarted.await(2, TimeUnit.SECONDS))
      val b = executor.submit<String> {
        session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
          bStarted.countDown()
          finishB.await(5, TimeUnit.SECONDS)
          it.write("B".toByteArray(), 5_000)
        }
      }
      val canceller = thread { session.cancel() }
      assertTrue(reachedWindow.await(2, TimeUnit.SECONDS))

      // Let A end. Without the lock B starts now; with it, the end of A waits for the cancel.
      finishA.countDown()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (bStarted.count > 0 && executorThread.get().state != Thread.State.BLOCKED) {
        assertTrue(System.nanoTime() < deadline, "neither B started nor A waited for the cancel")
        Thread.sleep(1)
      }
      releaseCancel.countDown()
      canceller.join(3_000)
      finishB.countDown()

      assertEquals("Success", a.get(3, TimeUnit.SECONDS))
      assertEquals("Success", b.get(3, TimeUnit.SECONDS))
      assertEquals(2, transports.size)
      assertContentEquals("B".toByteArray() + PrinterSession.COMPLETION_REQUEST, transports[1].written.toByteArray())
    } finally {
      releaseCancel.countDown()
      executor.shutdownNow()
    }
  }

  @Test
  fun cancel_heldAfterTheLockNeverClosesTheConnectionOfTheNextCall() {
    // Call A leaves the connection open and ends; call B reuses it. The cancel that saw A running is
    // held after it released the lock, so B starts before the cancel acts on the connection.
    val reachedCancel = CountDownLatch(1)
    val releaseCancel = CountDownLatch(1)
    val session = session(
      beforeCancel = {
        reachedCancel.countDown()
        releaseCancel.await(5, TimeUnit.SECONDS)
      },
    ) { it.autoReply = true }
    session.open("AA:BB", 1_000, resetAfterFailure = false)
    val executor = Executors.newSingleThreadExecutor()
    try {
      val aStarted = CountDownLatch(1)
      val finishA = CountDownLatch(1)
      val bStarted = CountDownLatch(1)
      val finishB = CountDownLatch(1)

      val a = executor.submit<String> {
        session.runJob(closePort = false, limits = limits) {
          aStarted.countDown()
          finishA.await(5, TimeUnit.SECONDS)
        }
      }
      assertTrue(aStarted.await(2, TimeUnit.SECONDS))
      val canceller = thread { session.cancel() }
      assertTrue(reachedCancel.await(2, TimeUnit.SECONDS))
      finishA.countDown()
      assertEquals("Success", a.get(3, TimeUnit.SECONDS))

      val b = executor.submit<String> {
        session.runJob(closePort = false, limits = limits) {
          bStarted.countDown()
          finishB.await(5, TimeUnit.SECONDS)
          it.write("B".toByteArray(), 5_000)
        }
      }
      assertTrue(bStarted.await(2, TimeUnit.SECONDS))
      releaseCancel.countDown()
      canceller.join(3_000)
      assertFalse(canceller.isAlive)
      finishB.countDown()

      assertEquals("Success", b.get(3, TimeUnit.SECONDS))
      assertFalse(transports.single().closed)
      assertContentEquals("B".toByteArray(), transports.single().written.toByteArray())
    } finally {
      releaseCancel.countDown()
      executor.shutdownNow()
    }
  }

  @Test
  fun cancel_stillClosesTheConnectionOfTheCallItSawRunning() {
    val reachedCancel = CountDownLatch(1)
    val releaseCancel = CountDownLatch(1)
    val session = session(
      beforeCancel = {
        reachedCancel.countDown()
        releaseCancel.await(5, TimeUnit.SECONDS)
      },
    ) { it.blockWrites = true }
    val executor = Executors.newSingleThreadExecutor()
    try {
      val started = CountDownLatch(1)
      val call = executor.submit<String> {
        session.runAtomic("AA:BB", 1_000, confirmCompletion = true, limits = limits, phases = PhaseTimer()) {
          started.countDown()
          it.write("A".toByteArray(), 5_000)
        }
      }
      assertTrue(started.await(2, TimeUnit.SECONDS))
      val canceller = thread { session.cancel() }
      assertTrue(reachedCancel.await(2, TimeUnit.SECONDS))
      releaseCancel.countDown()
      canceller.join(3_000)

      val failure = assertFailsWith<ExecutionException> { call.get(3, TimeUnit.SECONDS) }
      assertEquals(PrinterErrorCode.CANCELLED, (failure.cause as PrinterException).code)
    } finally {
      releaseCancel.countDown()
      executor.shutdownNow()
    }
  }

  @Test
  fun runAtomic_aQueuedCallRetriesWhileThePrinterRefusesRightAfterACancelAndSucceeds() {
    // Like the RT-320 PB: for a moment after the cancelled link is closed it refuses connections.
    val now = AtomicLong(0)
    val slept = Collections.synchronizedList(mutableListOf<Long>())
    val session = session(
      sleeper = {
        slept.add(it)
        now.addAndGet(it)
      },
      clock = { now.get() },
    ) {
      if (transports.isEmpty()) {
        it.blockWrites = true
      } else {
        it.failConnect = now.get() < 1_200
        it.autoReply = true
      }
    }
    val executor = Executors.newSingleThreadExecutor()
    try {
      val started = CountDownLatch(1)
      val first = executor.submit<String> {
        session.atomicJob {
          started.countDown()
          it.write("A".toByteArray(), 5_000)
        }
      }
      assertTrue(started.await(2, TimeUnit.SECONDS))
      val phases = PhaseTimer { now.get() }
      val second = executor.submit<String> { session.atomicJob(phases) }

      session.cancel()

      val cancelled = assertFailsWith<ExecutionException> { first.get(3, TimeUnit.SECONDS) }
      assertEquals(PrinterErrorCode.CANCELLED, (cancelled.cause as PrinterException).code)
      assertEquals("Success", second.get(3, TimeUnit.SECONDS))

      // The cancelled call, three refused attempts (0, 500, 1000 ms) and the one that connects.
      assertEquals(5, transports.size)
      assertTrue(transports.all { it.closed })
      assertTrue(transports.subList(1, 4).all { it.written.size() == 0 })
      assertContentEquals(
        "JOB".toByteArray() + PrinterSession.COMPLETION_REQUEST,
        transports.last().written.toByteArray(),
      )
      assertEquals(1_500, slept.sum())
      assertTrue(slept.all { it <= 100 }) // in slices, so a cancel ends the wait
      assertEquals(1_500, phases.millis(PrintPhase.CONNECT)) // the retries are part of connect
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun runAtomic_afterACancelAnUnreachablePrinterIsRetriedUntilTheWindowEndsThenFailsNotConnected() {
    var now = 0L
    var refuse = false
    val session = session(sleeper = { now += it }, clock = { now }) { it.failConnect = refuse }
    session.cancelledAtomicJob()
    refuse = true

    val error = assertFailsWith<PrinterException> { session.atomicJob() }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
    assertEquals(5_000, now)
    assertEquals(1 + 11, transports.size) // one attempt every 500 ms from 0 to 5000 ms
    assertTrue(transports.all { it.closed })
  }

  @Test
  fun runAtomic_theRetryWindowStartsWhenTheCancelledCallEnds() {
    var now = 0L
    var refuse = false
    val session = session(sleeper = { now += it }, clock = { now }) {
      it.failConnect = refuse && now < 10_500
      it.autoReply = true
    }
    // The call takes 10 s to end after the cancel; the window counts from its end.
    session.cancelledAtomicJob { now += 10_000 }
    refuse = true

    assertEquals("Success", session.atomicJob())
    assertEquals(3, transports.size)
  }

  @Test
  fun runAtomic_aFailureAfterTheRetryWindowIsAttemptedOnce() {
    var now = 0L
    var refuse = false
    var attemptTakesMs = 0L
    val slept = mutableListOf<Long>()
    val session = session(sleeper = { slept.add(it) }, clock = { now }) {
      now += attemptTakesMs
      it.failConnect = refuse
    }
    session.cancelledAtomicJob()
    refuse = true
    // The call starts inside the window, but its connect fails after it.
    attemptTakesMs = 5_000

    val error = assertFailsWith<PrinterException> { session.atomicJob() }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
    assertEquals(2, transports.size)
    assertTrue(transports.all { it.closed })
    assertTrue(slept.isEmpty())
  }

  @Test
  fun runAtomic_withoutACancelledCallAFailedConnectionIsAttemptedOnce() {
    var refuse = false
    val slept = mutableListOf<Long>()
    val session = session(sleeper = { slept.add(it) }) { it.failConnect = refuse }
    session.cancel() // nothing runs: no call was cancelled
    // A call that fails for another reason (no confirmation) opens no retry window.
    val timeout = assertFailsWith<PrinterException> { session.atomicJob() }
    assertEquals(PrinterErrorCode.TIMEOUT, timeout.code)
    refuse = true

    val error = assertFailsWith<PrinterException> { session.atomicJob() }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
    assertEquals(2, transports.size)
    assertTrue(slept.isEmpty())
  }

  @Test
  fun runAtomic_aCancelDuringTheRetryWaitEndsItWithCancelled() {
    lateinit var session: PrinterSession
    var refuse = false
    var sleeps = 0
    var sent = false
    session = session(sleeper = {
      sleeps++
      session.cancel()
    }) { it.failConnect = refuse }
    session.cancelledAtomicJob()
    refuse = true

    val error = assertFailsWith<PrinterException> { session.atomicJob { sent = true } }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertEquals(1, sleeps) // the wait ended at once
    assertEquals(2, transports.size) // no new connection after the cancel
    assertTrue(transports.all { it.closed })
    assertFalse(sent)
  }

  @Test
  fun open_rightAfterACancelledCallIsStillAttemptedOnce() {
    var refuse = false
    val slept = mutableListOf<Long>()
    val session = session(sleeper = { slept.add(it) }) { it.failConnect = refuse }
    session.cancelledAtomicJob()
    refuse = true

    val error = assertFailsWith<PrinterException> {
      session.open("AA:BB", 1_000, resetAfterFailure = false)
    }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
    assertEquals(2, transports.size)
    assertTrue(slept.isEmpty())
  }

  @Test
  fun closePort_closesTheConnection() {
    val session = session()
    session.open("AA:BB", 1_000, resetAfterFailure = false)

    assertEquals("Success", session.closePort())

    assertTrue(transports.single().closed)
  }

  /** In-memory transport whose blocking calls end only when it is closed, like a socket. */
  private class FakeTransport : PrinterTransport {
    @Volatile var closed = false
    @Volatile var failConnect = false
    @Volatile var blockWrites = false

    /** Answers the completion request the way a TSPL printer does. */
    @Volatile var autoReply = false
    val written = ByteArrayOutputStream()
    private val incoming = ConcurrentLinkedQueue<Byte>()
    private val closedLatch = CountDownLatch(1)

    override fun connect() {
      if (failConnect) throw IOException("refused")
    }

    override val input: InputStream = object : InputStream() {
      override fun available(): Int {
        if (closed) throw IOException("socket closed")
        return incoming.size
      }

      override fun read(): Int = incoming.poll()?.toInt()?.and(0xFF) ?: -1
    }

    override val output: OutputStream = object : OutputStream() {
      override fun write(b: Int) {
        if (closed) throw IOException("socket closed")
        if (blockWrites) {
          closedLatch.await(10, TimeUnit.SECONDS)
          throw IOException("socket closed")
        }
        written.write(b)
        if (autoReply && String(written.toByteArray(), Charsets.US_ASCII).endsWith("OUT \"ENDLINE\"\r\n")) {
          "COMPLETED\r\nENDLINE\r\n".toByteArray().forEach { incoming.add(it) }
        }
      }
    }

    override fun close() {
      closed = true
      closedLatch.countDown()
    }
  }
}
