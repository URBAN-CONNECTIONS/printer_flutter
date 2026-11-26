package com.urbanconnections.printer_flutter

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrinterConnectionTest {
  private lateinit var watchdog: ScheduledExecutorService

  @BeforeTest
  fun setUp() {
    watchdog = Executors.newSingleThreadScheduledExecutor()
  }

  @AfterTest
  fun tearDown() {
    watchdog.shutdownNow()
  }

  @Test
  fun awaitMarker_returnsWhenTheMarkerArrives() {
    val transport = FakeTransport()
    transport.reply("COMPLETED\r\nENDLINE\r\n")
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)

    connection.awaitMarker("ENDLINE", timeoutMs = 1_000)

    assertTrue(connection.isOpen)
  }

  @Test
  fun awaitMarker_findsAMarkerSplitAcrossReads() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    thread {
      transport.reply("COMPLETED\r\nENDL")
      Thread.sleep(50)
      transport.reply("INE\r\n")
    }

    connection.awaitMarker("ENDLINE", timeoutMs = 2_000)
  }

  @Test
  fun awaitMarker_timesOutAndClosesTheTransport() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)

    val error = assertFailsWith<PrinterException> {
      connection.awaitMarker("ENDLINE", timeoutMs = 100)
    }

    assertEquals(PrinterErrorCode.TIMEOUT, error.code)
    assertTrue(transport.closed)
    assertTrue(!connection.isOpen)
  }

  @Test
  fun awaitMarker_reportsALostConnectionInsteadOfLooping() {
    val transport = FakeTransport()
    transport.failReads = true
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)

    val error = assertFailsWith<PrinterException> {
      connection.awaitMarker("ENDLINE", timeoutMs = 5_000)
    }

    assertEquals(PrinterErrorCode.CONNECTION_LOST, error.code)
    assertTrue(transport.closed)
  }

  @Test
  fun cancel_endsAWaitInProgress() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    thread {
      Thread.sleep(50)
      connection.cancel()
    }

    val error = assertFailsWith<PrinterException> {
      connection.awaitMarker("ENDLINE", timeoutMs = 5_000)
    }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
  }

  @Test
  fun write_sendsEveryByteInChunks() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    val payload = ByteArray(PrinterConnection.WRITE_CHUNK_SIZE * 2 + 10) { it.toByte() }

    connection.write(payload, timeoutMs = 1_000)

    assertTrue(payload.contentEquals(transport.written.toByteArray()))
  }

  @Test
  fun write_blockedByThePrinterTimesOut() {
    val transport = FakeTransport()
    transport.blockWrites = true
    val connection = PrinterConnection(transport, watchdog)

    val error = assertFailsWith<PrinterException> {
      connection.write(ByteArray(10), timeoutMs = 100)
    }

    assertEquals(PrinterErrorCode.TIMEOUT, error.code)
    assertTrue(transport.closed)
  }

  @Test
  fun write_afterCloseFailsWithoutBlocking() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    connection.close()

    val error = assertFailsWith<PrinterException> {
      connection.write(ByteArray(10), timeoutMs = 1_000)
    }

    assertEquals(PrinterErrorCode.CONNECTION_LOST, error.code)
  }

  @Test
  fun open_failureIsNotConnected() {
    val transport = FakeTransport()
    transport.failConnect = true
    val connection = PrinterConnection(transport, watchdog)

    val error = assertFailsWith<PrinterException> { connection.open(timeoutMs = 1_000) }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
  }

  @Test
  fun open_stuckConnectGivesUpAtTheLimit() {
    val transport = FakeTransport()
    transport.blockConnect = true
    val connection = PrinterConnection(transport, watchdog)

    val error = assertFailsWith<PrinterException> { connection.open(timeoutMs = 100) }

    assertEquals(PrinterErrorCode.NOT_CONNECTED, error.code)
    assertTrue(transport.closed)
  }

  @Test
  fun open_cancelWhileTheConnectIsBlockedEndsWithCancelledAndClosesAtOnce() {
    val transport = FakeTransport()
    transport.blockConnect = true
    val connection = PrinterConnection(transport, watchdog)
    val canceller = thread {
      Thread.sleep(50)
      connection.cancel()
    }
    val startedAt = System.nanoTime()

    val error = assertFailsWith<PrinterException> { connection.open(timeoutMs = 5_000) }
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
    canceller.join(2_000)

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    transport.awaitClosed()
    assertTrue(!connection.isOpen)
    assertTrue(elapsedMs < 2_000, "open waited $elapsedMs ms after the cancel")
  }

  @Test
  fun probe_asksWithOutAndReturnsWhenThePrinterAnswers() {
    val transport = FakeTransport()
    transport.onFlush = { transport.reply("READY") }
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)

    connection.probe(timeoutMs = 1_000)

    assertEquals("OUT \"READY\"\r\n", String(transport.written.toByteArray(), Charsets.US_ASCII))
    assertTrue(connection.isOpen)
  }

  @Test
  fun probe_withoutAnAnswerFailsWithPrinterNotReadyAndCloses() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)

    val error = assertFailsWith<PrinterException> { connection.probe(timeoutMs = 100) }

    assertEquals(PrinterErrorCode.PRINTER_NOT_READY, error.code)
    assertEquals("La impresora no contesta. Apágala y vuelve a encenderla.", error.message)
    assertTrue(transport.closed)
    assertTrue(!connection.isOpen)
  }

  @Test
  fun probe_whoseWriteIsBlockedFailsWithPrinterNotReadyAndCloses() {
    val transport = FakeTransport()
    transport.blockWrites = true
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)

    val error = assertFailsWith<PrinterException> { connection.probe(timeoutMs = 100) }

    assertEquals(PrinterErrorCode.PRINTER_NOT_READY, error.code)
    assertTrue(transport.closed)
  }

  @Test
  fun probe_givesTheAnswerOnlyWhatTheWriteLeftOfTheLimit() {
    val transport = FakeTransport()
    transport.onFlush = { Thread.sleep(400) }
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    val start = System.nanoTime()

    val error = assertFailsWith<PrinterException> { connection.probe(timeoutMs = 800) }

    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    assertEquals(PrinterErrorCode.PRINTER_NOT_READY, error.code)
    // A limit for each step would end after 1200 ms.
    assertTrue(elapsedMs in 750L..1_150L, "took $elapsedMs ms")
  }

  @Test
  fun probe_cancelledWhileWaitingEndsWithCancelled() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    thread {
      Thread.sleep(50)
      connection.cancel()
    }

    val error = assertFailsWith<PrinterException> { connection.probe(timeoutMs = 5_000) }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    transport.awaitClosed()
  }

  @Test
  fun probe_cancelledWhileWritingEndsWithCancelled() {
    val transport = FakeTransport()
    transport.blockWrites = true
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    thread {
      Thread.sleep(50)
      connection.cancel()
    }

    val error = assertFailsWith<PrinterException> { connection.probe(timeoutMs = 5_000) }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
  }

  @Test
  fun probe_onALostConnectionReportsConnectionLost() {
    val transport = FakeTransport()
    transport.failReads = true
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)

    val error = assertFailsWith<PrinterException> { connection.probe(timeoutMs = 5_000) }

    assertEquals(PrinterErrorCode.CONNECTION_LOST, error.code)
    assertTrue(transport.closed)
  }

  @Test
  fun writeBlock_cancelledMidBlockFinishesTheBlockThenClosesAndEndsCancelled() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    val block = ByteArray(PrinterConnection.WRITE_CHUNK_SIZE * 3) { 7 }
    var cancelled = false
    transport.onWrite = { total ->
      // The cancel arrives after the first chunk has been taken.
      if (!cancelled && total >= PrinterConnection.WRITE_CHUNK_SIZE) {
        cancelled = true
        connection.cancel()
        // The transport must still be open while the block is in progress.
        assertTrue(!transport.closed)
      }
    }

    val error = assertFailsWith<PrinterException> { connection.writeBlock(block, timeoutMs = 1_000) }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertEquals(block.size, transport.written.size()) // the whole block went out
    transport.awaitClosed()
    assertTrue(!connection.isOpen)
  }

  @Test
  fun writeBlock_nothingCanBeWrittenAfterTheCancelledBlock() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    transport.onWrite = { connection.cancel() }

    assertFailsWith<PrinterException> { connection.writeBlock(ByteArray(10), timeoutMs = 1_000) }
    val sizeAfterBlock = transport.written.size()
    val error = assertFailsWith<PrinterException> { connection.write(ByteArray(10), timeoutMs = 1_000) }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertEquals(sizeAfterBlock, transport.written.size())
  }

  @Test
  fun checkCancelled_afterACancelBetweenBlocksThrowsAndTheConnectionIsClosed() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    connection.writeBlock(ByteArray(10), timeoutMs = 1_000)
    connection.checkCancelled() // not cancelled: nothing happens

    connection.cancel()

    val error = assertFailsWith<PrinterException> { connection.checkCancelled() }
    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    transport.awaitClosed()
    assertEquals(10, transport.written.size())
  }

  @Test
  fun writeBlock_afterACancelBetweenBlocksWritesNothing() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    connection.cancel()

    val error = assertFailsWith<PrinterException> { connection.writeBlock(ByteArray(10), timeoutMs = 1_000) }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertEquals(0, transport.written.size())
  }

  @Test
  fun writeBlock_stuckBlockAfterACancelClosesAtTheLimitAndSaysToTurnThePrinterOffAndOn() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    transport.blockWrites = true
    thread {
      Thread.sleep(50)
      connection.cancel()
    }

    val error = assertFailsWith<PrinterException> { connection.writeBlock(ByteArray(10), timeoutMs = 300) }

    assertEquals(PrinterErrorCode.TIMEOUT, error.code)
    assertEquals(PrinterConnection.STUCK_BLOCK_MESSAGE, error.message)
    assertTrue(error.message!!.contains("Apágala y vuelve a encenderla"))
    assertTrue(transport.closed)
  }

  @Test
  fun writeBlock_stuckBlockWithoutACancelTimesOutAsAWrite() {
    val transport = FakeTransport()
    transport.blockWrites = true
    val connection = PrinterConnection(transport, watchdog)

    val error = assertFailsWith<PrinterException> { connection.writeBlock(ByteArray(10), timeoutMs = 100) }

    assertEquals(PrinterErrorCode.TIMEOUT, error.code)
    assertTrue(transport.closed)
  }

  @Test
  fun writeBlock_connectionLostInTheMiddleFailsAsLostAndCloses() {
    val transport = FakeTransport()
    transport.failWritesAfter = 5
    val connection = PrinterConnection(transport, watchdog)

    val error = assertFailsWith<PrinterException> { connection.writeBlock(ByteArray(10), timeoutMs = 1_000) }

    assertEquals(PrinterErrorCode.CONNECTION_LOST, error.code)
    assertTrue(transport.closed)
  }

  @Test
  fun cancel_whileAPlainWriteIsBlockedClosesAtOnce() {
    val transport = FakeTransport()
    transport.blockWrites = true
    val connection = PrinterConnection(transport, watchdog)
    thread {
      Thread.sleep(50)
      connection.cancel()
    }

    val error = assertFailsWith<PrinterException> { connection.write(ByteArray(10), timeoutMs = 5_000) }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    transport.awaitClosed()
  }

  @Test
  fun cancel_returnsBeforeASlowCloseEnds() {
    val transport = FakeTransport()
    val closeStarted = CountDownLatch(1)
    val releaseClose = CountDownLatch(1)
    transport.onClose = {
      closeStarted.countDown()
      releaseClose.await(10, TimeUnit.SECONDS)
    }
    val connection = PrinterConnection(transport, watchdog)

    val start = System.nanoTime()
    connection.cancel()
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

    // cancel() did not wait for the close that is still running on the watchdog thread.
    assertTrue(closeStarted.await(2, TimeUnit.SECONDS))
    assertTrue(elapsedMs < 1_000, "cancel took $elapsedMs ms")
    assertTrue(!transport.closed)
    assertTrue(!connection.isOpen)
    releaseClose.countDown()
    transport.awaitClosed()
  }

  @Test
  fun cancel_closesInTheCallerWhenTheWatchdogIsGone() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog)
    watchdog.shutdownNow()

    connection.cancel()

    assertTrue(transport.closed)
  }

  @Test
  fun cancel_runsTheCloseOnTheWatchdogThread() {
    val transport = FakeTransport()
    var closedOn: Thread? = null
    transport.onClose = { closedOn = Thread.currentThread() }
    val connection = PrinterConnection(transport, watchdog)

    connection.cancel()
    transport.awaitClosed()

    assertTrue(closedOn != null && closedOn !== Thread.currentThread())
  }

  @Test
  fun indexOf_findsTheMarker() {
    val haystack = "xxENDLINE\r\n".toByteArray()
    assertEquals(2, PrinterConnection.indexOf(haystack, "ENDLINE".toByteArray()))
    assertEquals(-1, PrinterConnection.indexOf(haystack, "COMPLETED".toByteArray()))
  }

  /** In-memory transport whose blocking calls end only when it is closed, like a socket. */
  private class FakeTransport : PrinterTransport {
    @Volatile var closed = false
    @Volatile var failReads = false
    @Volatile var failConnect = false
    @Volatile var blockConnect = false
    @Volatile var blockWrites = false

    /** Writes after this many bytes fail with an IOException; null never fails. */
    @Volatile var failWritesAfter: Int? = null

    /** Runs after each write, with the total number of bytes written so far. */
    @Volatile var onWrite: (Int) -> Unit = {}

    /** Runs inside [close], before it marks the transport closed. */
    @Volatile var onClose: () -> Unit = {}

    /** Waits until [close] has run. */
    fun awaitClosed() {
      assertTrue(closedLatch.await(2, TimeUnit.SECONDS), "the transport was not closed")
    }

    /** Runs when the written bytes are flushed. */
    @Volatile var onFlush: () -> Unit = {}
    val written = ByteArrayOutputStream()
    private val incoming = ConcurrentLinkedQueue<Byte>()
    private val closedLatch = CountDownLatch(1)

    fun reply(text: String) {
      text.toByteArray().forEach { incoming.add(it) }
    }

    override fun connect() {
      if (failConnect) throw IOException("refused")
      if (blockConnect) {
        closedLatch.await(10, TimeUnit.SECONDS)
        throw IOException("socket closed")
      }
    }

    override val input: InputStream = object : InputStream() {
      override fun available(): Int {
        if (closed || failReads) throw IOException("socket closed")
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
        if (written.size() >= (failWritesAfter ?: Int.MAX_VALUE)) throw IOException("link lost")
        written.write(b)
        onWrite(written.size())
      }

      override fun flush() = onFlush()
    }

    override fun close() {
      onClose()
      closed = true
      closedLatch.countDown()
    }
  }
}
