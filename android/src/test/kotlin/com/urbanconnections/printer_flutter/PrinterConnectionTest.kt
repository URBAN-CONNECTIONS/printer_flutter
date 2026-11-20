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
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
  fun cancelThenReset_sendsTheResetInsteadOfTheNextChunkAndCancels() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    connection.cancelThenReset("RST".toByteArray())

    val error = assertFailsWith<PrinterException> {
      connection.write("DATA".toByteArray(), timeoutMs = 1_000)
    }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertContentEquals("RST".toByteArray(), transport.written.toByteArray())
    assertTrue(connection.resetSent)
    assertTrue(transport.closed)
  }

  @Test
  fun cancelThenReset_endsAWaitForTheMarkerAfterSendingTheReset() {
    val transport = FakeTransport()
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    thread {
      Thread.sleep(50)
      connection.cancelThenReset("RST".toByteArray())
    }

    val error = assertFailsWith<PrinterException> {
      connection.awaitMarker("ENDLINE", timeoutMs = 2_000)
    }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertContentEquals("RST".toByteArray(), transport.written.toByteArray())
    assertTrue(connection.resetSent)
  }

  @Test
  fun cancelThenReset_closesWithoutTheResetWhenAWriteIsBlocked() {
    val transport = FakeTransport()
    transport.blockWrites = true
    val connection = PrinterConnection(transport, watchdog, pollIntervalMs = 5)
    val failure = AtomicReference<Throwable?>()
    val worker = thread {
      try {
        connection.write("DATA".toByteArray(), timeoutMs = 5_000)
      } catch (e: Throwable) {
        failure.set(e)
      }
    }
    Thread.sleep(50)

    connection.cancelThenReset("RST".toByteArray(), hardStopMs = 100)
    worker.join(3_000)

    assertEquals(PrinterErrorCode.CANCELLED, (failure.get() as PrinterException).code)
    assertEquals(0, transport.written.size())
    assertTrue(!connection.resetSent)
    assertTrue(transport.closed)
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
        written.write(b)
      }
    }

    override fun close() {
      closed = true
      closedLatch.countDown()
    }
  }
}
