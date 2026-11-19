package com.urbanconnections.printer_flutter

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

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
 */
class PrinterConnection(
  private val transport: PrinterTransport,
  private val watchdog: ScheduledExecutorService,
  private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
  @Volatile private var closed = false
  @Volatile private var cancelled = false
  @Volatile private var timedOut = false

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

  /** Aborts the connection: any call in progress ends with [PrinterErrorCode.CANCELLED]. */
  fun cancel() {
    cancelled = true
    close()
  }

  fun close() {
    if (closed) return
    closed = true
    try {
      transport.close()
    } catch (_: Exception) {
    }
  }

  private fun checkUsable() {
    if (cancelled) throw PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló")
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

  private fun failure(failCode: PrinterErrorCode, step: String, cause: Throwable): PrinterException {
    close()
    return when {
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
