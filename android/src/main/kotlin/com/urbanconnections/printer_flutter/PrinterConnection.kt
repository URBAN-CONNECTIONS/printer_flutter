package com.urbanconnections.printer_flutter

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Error types the plugin reports to Flutter. The names are the PlatformException codes. */
enum class PrinterErrorCode {
  /** The connection to the printer could not be opened. */
  NOT_CONNECTED,

  /** The connection was lost while sending or waiting. */
  CONNECTION_LOST,

  /** The printer did not answer within the limit. */
  TIMEOUT,

  /** The job was cancelled from Flutter. */
  CANCELLED,

  /** The printer did not answer the probe, so nothing of the job was sent. */
  PRINTER_NOT_READY,
}

class PrinterException(
  val code: PrinterErrorCode,
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

/** The byte streams of a printer link. Bluetooth in production, fakes in tests. */
interface PrinterTransport {
  /** Opens the link; blocks until it is open or fails. */
  @Throws(IOException::class)
  fun connect()

  val input: InputStream
  val output: OutputStream

  /** Closes the link. Must unblock any thread blocked in [connect], a read or a write. */
  fun close()
}

/**
 * One connection to a printer where every blocking step has a limit.
 *
 * When a limit expires, or [cancel] is called, the transport is closed. Closing unblocks a
 * connect, read or write stuck on another thread, so no call can wait forever. A closed
 * connection is never reused: open a new one.
 *
 * A block (see [writeBlock]) is never cut by a [cancel]: the close waits until the block ends.
 * Only the limit of the write can close a block in progress. The close that [cancel] asks for runs
 * on the [watchdog] thread, so the caller of [cancel] never waits for the transport.
 */
class PrinterConnection(
  private val transport: PrinterTransport,
  private val watchdog: ScheduledExecutorService,
  private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
  @Volatile private var closed = false
  @Volatile private var cancelled = false
  @Volatile private var timedOut = false

  /** Guards [inBlock] and the decision of [cancel] to close at once or to wait for the block. */
  private val blockLock = Any()

  /** true while [writeBlock] is writing a block. Written under [blockLock]. */
  @Volatile private var inBlock = false

  /** true when [cancel] arrived while a block was being written. Read and written under [blockLock]. */
  @Volatile private var cancelledInBlock = false

  /** Makes sure the transport is closed once, whoever gets there first. */
  private val transportClosed = AtomicBoolean(false)

  val isOpen: Boolean
    get() = !closed

  /** Opens the link within [timeoutMs]. Any failure other than a cancel is [PrinterErrorCode.NOT_CONNECTED]. */
  fun open(timeoutMs: Long) {
    guarded(timeoutMs, PrinterErrorCode.NOT_CONNECTED, "conectar con la impresora") {
      transport.connect()
    }
  }

  /** Writes [bytes] in chunks; each chunk must be accepted within [timeoutMs]. */
  fun write(bytes: ByteArray, timeoutMs: Long) {
    var offset = 0
    while (offset < bytes.size) {
      val length = minOf(WRITE_CHUNK_SIZE, bytes.size - offset)
      val start = offset
      guarded(timeoutMs, PrinterErrorCode.CONNECTION_LOST, "enviar datos a la impresora") {
        transport.output.write(bytes, start, length)
      }
      offset += length
    }
    guarded(timeoutMs, PrinterErrorCode.CONNECTION_LOST, "enviar datos a la impresora") {
      transport.output.flush()
    }
  }

  /**
   * Writes [bytes] as one block (a `BITMAP` band or a `DOWNLOAD` file): a [cancel] that arrives
   * while it is written does not close the transport; the close happens when the block ends, and
   * the call then ends with [PrinterErrorCode.CANCELLED]. Each chunk must still be accepted within
   * [timeoutMs]: if the printer stops taking data the limit closes the connection and the error
   * says the printer may need to be turned off and on.
   */
  fun writeBlock(bytes: ByteArray, timeoutMs: Long) {
    synchronized(blockLock) {
      checkUsable()
      inBlock = true
    }
    var finished = false
    try {
      write(bytes, timeoutMs)
      finished = true
    } finally {
      val cancelRequested = synchronized(blockLock) {
        inBlock = false
        cancelled
      }
      // A cancel that waited for the block closes now, on every path out of it.
      if (cancelRequested) close()
      if (finished && cancelRequested) {
        throw PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló")
      }
    }
  }

  /** Throws [PrinterErrorCode.CANCELLED] when the job was cancelled. Call it between blocks. */
  fun checkCancelled() {
    if (cancelled) throw PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló")
  }

  /**
   * Waits until the printer sends [marker], at most [timeoutMs].
   *
   * Only the bytes actually read are kept, so a marker split across several reads is still
   * found. Reads happen only when bytes are available, so they never block.
   */
  fun awaitMarker(marker: String, timeoutMs: Long) {
    val target = marker.toByteArray(Charsets.US_ASCII)
    val received = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_SIZE)
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)

    while (true) {
      checkUsable()
      val available = try {
        transport.input.available()
      } catch (e: IOException) {
        throw failure(PrinterErrorCode.CONNECTION_LOST, "esperar la respuesta de la impresora", e)
      }

      if (available > 0) {
        val read = try {
          transport.input.read(buffer, 0, minOf(buffer.size, available))
        } catch (e: IOException) {
          throw failure(PrinterErrorCode.CONNECTION_LOST, "esperar la respuesta de la impresora", e)
        }
        if (read < 0) {
          close()
          throw PrinterException(PrinterErrorCode.CONNECTION_LOST, "Se perdió la conexión con la impresora al esperar su respuesta")
        }
        received.write(buffer, 0, read)
        if (indexOf(received.toByteArray(), target) >= 0) return
        continue
      }

      if (System.nanoTime() >= deadline) {
        timedOut = true
        close()
        throw PrinterException(
          PrinterErrorCode.TIMEOUT,
          "La impresora no contestó a tiempo ($timeoutMs ms)",
        )
      }

      try {
        Thread.sleep(pollIntervalMs)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        cancel()
        throw PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló", e)
      }
    }
  }

  /**
   * Checks that the printer answers before a job is sent: writes [PROBE_REQUEST] and waits for
   * [PROBE_MARKER]. One limit of [timeoutMs] covers both: the write is given the limit and the
   * wait what is left of it.
   *
   * A printer that does not take the question or does not answer it in time ends with
   * [PrinterErrorCode.PRINTER_NOT_READY] and the connection closed. A cancel or a lost
   * connection are reported as they are.
   */
  fun probe(timeoutMs: Long) {
    val startedAt = System.nanoTime()
    try {
      // Not [write]: it would give the whole limit to the chunk and again to the flush.
      guarded(timeoutMs, PrinterErrorCode.CONNECTION_LOST, "comprobar la impresora") {
        transport.output.write(PROBE_REQUEST)
        transport.output.flush()
      }
      val spentMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
      awaitMarker(PROBE_MARKER, maxOf(0L, timeoutMs - spentMs))
    } catch (e: PrinterException) {
      // The expired limit already closed the connection.
      if (e.code != PrinterErrorCode.TIMEOUT) throw e
      throw PrinterException(PrinterErrorCode.PRINTER_NOT_READY, NOT_READY_MESSAGE, e)
    }
  }

  /**
   * Aborts the connection: any call in progress ends with [PrinterErrorCode.CANCELLED].
   *
   * Outside a block the transport is closed at once, on the watchdog thread, so this returns
   * without waiting for it. While a block is written, only the flag is set and [writeBlock]
   * closes the transport when the block ends.
   */
  fun cancel() {
    val closeNow = synchronized(blockLock) {
      cancelled = true
      if (inBlock) cancelledInBlock = true
      !inBlock
    }
    if (closeNow) closeOnWatchdog()
  }

  fun close() {
    closed = true
    closeTransport()
  }

  /** Marks the connection closed and closes the transport on the watchdog thread. */
  private fun closeOnWatchdog() {
    closed = true
    try {
      watchdog.execute { closeTransport() }
    } catch (_: RejectedExecutionException) {
      // The watchdog was shut down with the plugin: close here so the socket never leaks.
      closeTransport()
    }
  }

  private fun closeTransport() {
    if (!transportClosed.compareAndSet(false, true)) return
    try {
      transport.close()
    } catch (_: Exception) {
    }
  }

  private fun checkUsable() {
    if (timedOut && cancelledInBlock) throw stuckBlock(null)
    // A cancel does not stop the block in progress: it is checked again when the block ends.
    if (cancelled && !inBlock) throw PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló")
    if (timedOut) throw PrinterException(PrinterErrorCode.TIMEOUT, "La impresora no contestó a tiempo")
    if (closed) throw PrinterException(PrinterErrorCode.CONNECTION_LOST, "La conexión con la impresora está cerrada")
  }

  /** Runs [block] and closes the transport if it takes longer than [timeoutMs]. */
  private fun guarded(timeoutMs: Long, failCode: PrinterErrorCode, step: String, block: () -> Unit) {
    checkUsable()
    val timer = watchdog.schedule({
      timedOut = true
      close()
    }, timeoutMs, TimeUnit.MILLISECONDS)
    try {
      block()
    } catch (e: IOException) {
      throw failure(failCode, step, e)
    } catch (e: SecurityException) {
      throw failure(failCode, step, e)
    } finally {
      timer.cancel(false)
    }
    // The watchdog may have fired just as the block finished.
    checkUsable()
  }

  /** The limit ran out while the block that a cancel waited for was being finished. */
  private fun stuckBlock(cause: Throwable?) =
    PrinterException(PrinterErrorCode.TIMEOUT, STUCK_BLOCK_MESSAGE, cause)

  private fun failure(failCode: PrinterErrorCode, step: String, cause: Throwable): PrinterException {
    close()
    return when {
      timedOut && cancelledInBlock -> stuckBlock(cause)
      cancelled -> PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló", cause)
      timedOut && failCode != PrinterErrorCode.NOT_CONNECTED ->
        PrinterException(PrinterErrorCode.TIMEOUT, "La impresora no contestó a tiempo al $step", cause)
      timedOut -> PrinterException(failCode, "No se pudo $step a tiempo", cause)
      failCode == PrinterErrorCode.NOT_CONNECTED ->
        PrinterException(failCode, "No se pudo $step: ${cause.message}", cause)
      else -> PrinterException(failCode, "Se perdió la conexión con la impresora al $step: ${cause.message}", cause)
    }
  }

  companion object {
    const val DEFAULT_POLL_INTERVAL_MS = 50L
    const val WRITE_CHUNK_SIZE = 4096
    const val READ_BUFFER_SIZE = 256

    /** TSPL `OUT` question of the probe: a printer that is ready sends [PROBE_MARKER] back. */
    val PROBE_REQUEST: ByteArray = "OUT \"READY\"\r\n".toByteArray(Charsets.US_ASCII)
    const val PROBE_MARKER = "READY"
    const val STUCK_BLOCK_MESSAGE =
      "La impresora dejó de recibir datos al cancelar. Apágala y vuelve a encenderla."
    const val NOT_READY_MESSAGE = "La impresora no contesta. Apágala y vuelve a encenderla."

    fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
      if (needle.isEmpty()) return 0
      outer@ for (i in 0..haystack.size - needle.size) {
        for (j in needle.indices) {
          if (haystack[i + j] != needle[j]) continue@outer
        }
        return i
      }
      return -1
    }
  }
}
