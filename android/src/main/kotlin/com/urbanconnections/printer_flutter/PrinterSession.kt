package com.urbanconnections.printer_flutter

/** The limits of one job, taken from the arguments of the call. */
class JobLimits(val writeTimeoutMs: Long, val completionTimeoutMs: Long)

/**
 * The connection of the plugin and the order of what happens on it: open (with the optional
 * printer reset), jobs, close and cancel.
 *
 * Calls to [open], [runJob] and [closePort] run one at a time on the plugin's job thread.
 * [cancel] is called from another thread and acts only while one of them is running.
 * The connection itself comes from [newConnection], so tests can use a fake transport.
 */
class PrinterSession(
  private val newConnection: (macAddress: String) -> PrinterConnection,
  private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
  private val resetSettleMs: Long = RESET_SETTLE_MS,
  private val closeGraceMs: Long = CLOSE_GRACE_MS,
  private val onWarning: (String) -> Unit = {},
) {
  /** The open connection, read by [cancel] from another thread. */
  @Volatile private var connection: PrinterConnection? = null

  /** true when the last job or open hung, was cancelled or failed. */
  @Volatile private var lastJobFailed = false

  /** true while [open], [runJob] or [closePort] is running. */
  @Volatile private var busy = false

  /** true when [cancel] arrived during the running call, even before it had a connection. */
  @Volatile private var cancelRequested = false

  /** true when a cancel already reset the printer: the next open waits for it and does not reset again. */
  @Volatile private var printerResetByCancel = false

  /**
   * Opens the connection within [connectTimeoutMs]. With [resetAfterFailure], when the previous
   * job failed, the printer is first reset with `<ESC>!R` and given time to come back.
   */
  fun open(macAddress: String, connectTimeoutMs: Long, resetAfterFailure: Boolean): String = exclusive {
    // Always close any existing connection before opening a new one.
    closeConnection()
    val alreadyReset = printerResetByCancel
    printerResetByCancel = false
    try {
      if (alreadyReset) {
        settle()
      } else if (resetAfterFailure && lastJobFailed) {
        resetPrinter(macAddress, connectTimeoutMs)
      }
      throwIfCancelRequested()
      val opened = newConnection(macAddress)
      connection = opened
      // A cancel that arrived before the connection was published must still apply.
      if (cancelRequested) opened.cancel()
      opened.open(connectTimeoutMs)
      lastJobFailed = false
      "Success"
    } catch (e: Throwable) {
      lastJobFailed = true
      closeConnection()
      throw e
    }
  }

  /**
   * Sends a job on the open connection. With [closePort], waits for the printer to confirm it
   * processed everything (TSPL `OUT` marker) within the limit, then closes the connection.
   * Any failure closes the connection, so the next job starts on a new one.
   */
  fun runJob(closePort: Boolean, limits: JobLimits, send: (PrinterConnection) -> Unit): String = exclusive {
    val current = connection
    if (current == null || !current.isOpen) {
      lastJobFailed = true
      throw PrinterException(
        PrinterErrorCode.NOT_CONNECTED,
        "No hay conexión abierta con la impresora; llama a openPort primero",
      )
    }
    try {
      send(current)
      if (closePort) {
        current.write(COMPLETION_REQUEST, limits.writeTimeoutMs)
        current.awaitMarker(COMPLETION_MARKER, limits.completionTimeoutMs)
        closeConnection()
      }
      lastJobFailed = false
      "Success"
    } catch (e: Throwable) {
      lastJobFailed = true
      if (current.resetSent) printerResetByCancel = true
      closeConnection()
      throw e
    }
  }

  /** Closes the connection. Gives the printer time to receive the last bytes first, as the TSC SDK did. */
  fun closePort(): String = exclusive {
    val current = connection
    if (current != null && current.isOpen) sleeper(closeGraceMs)
    closeConnection()
    "Success"
  }

  /**
   * Aborts the call in progress: it ends with [PrinterErrorCode.CANCELLED]. Does nothing when no
   * call is running, so a cancel that arrives between two jobs never closes the idle connection.
   */
  fun cancel(resetPrinter: Boolean = false) {
    if (!busy) return
    cancelRequested = true
    val current = connection ?: return
    if (resetPrinter) current.cancelThenReset(RESET_COMMAND) else current.cancel()
  }

  /** Called when the plugin detaches: aborts whatever is running. */
  fun shutdown() {
    cancelRequested = true
    connection?.cancel()
  }

  /**
   * Sends `<ESC>!R` so a printer left waiting for the rest of an image starts clean.
   * The reset drops the Bluetooth link, so this waits for the printer to come back before the
   * caller reconnects. Failing to reset is not an error: the caller still tries to connect.
   * A cancel ends the wait at once.
   */
  private fun resetPrinter(macAddress: String, connectTimeoutMs: Long) {
    val resetConnection = newConnection(macAddress)
    connection = resetConnection
    try {
      if (cancelRequested) resetConnection.cancel()
      resetConnection.open(connectTimeoutMs)
      resetConnection.write(RESET_COMMAND, DEFAULT_WRITE_TIMEOUT_MS)
    } catch (e: PrinterException) {
      if (e.code == PrinterErrorCode.CANCELLED) throw e
      onWarning("Printer reset failed: ${e.message}")
    } finally {
      resetConnection.close()
      connection = null
    }
    settle()
  }

  /** Waits for the printer to come back after a reset, in slices so a cancel ends the wait at once. */
  private fun settle() {
    var waited = 0L
    while (waited < resetSettleMs) {
      throwIfCancelRequested()
      val slice = minOf(SETTLE_SLICE_MS, resetSettleMs - waited)
      sleeper(slice)
      waited += slice
    }
  }

  private fun throwIfCancelRequested() {
    if (cancelRequested) throw PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló")
  }

  private fun closeConnection() {
    connection?.close()
    connection = null
  }

  private inline fun <T> exclusive(block: () -> T): T {
    cancelRequested = false
    busy = true
    try {
      return block()
    } finally {
      busy = false
    }
  }

  companion object {
    const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
    const val DEFAULT_WRITE_TIMEOUT_MS = 20_000L
    const val DEFAULT_COMPLETION_TIMEOUT_MS = 60_000L
    const val CLOSE_GRACE_MS = 1_500L
    const val RESET_SETTLE_MS = 4_000L
    private const val SETTLE_SLICE_MS = 100L

    /** Same bytes the TSC SDK sent: an `OUT` marker plus the `ENDLINE` its reader waited for. */
    val COMPLETION_REQUEST: ByteArray =
      ("OUT \"COMPLETED\\r\\n\"\r\n" + "\r\n" + "OUT \"ENDLINE\"\r\n").toByteArray(Charsets.US_ASCII)
    const val COMPLETION_MARKER = "ENDLINE"

    /** TSPL immediate command `<ESC>!R`: resets the printer and deletes downloaded files. */
    val RESET_COMMAND: ByteArray = byteArrayOf(0x1B, '!'.code.toByte(), 'R'.code.toByte())
  }
}
