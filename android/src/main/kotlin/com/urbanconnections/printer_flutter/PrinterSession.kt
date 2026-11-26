package com.urbanconnections.printer_flutter

/** The limits of one job, taken from the arguments of the call. */
class JobLimits(val writeTimeoutMs: Long, val completionTimeoutMs: Long)

/**
 * The connection of the plugin and the order of what happens on it: open (with the optional
 * printer reset), jobs, close and cancel.
 *
 * Calls to [open], [runJob], [runAtomic] and [closePort] run one at a time on the plugin's job thread.
 * A job can be preceded by a probe that checks the printer answers (see [PrinterConnection.probe]).
 * [cancel] is called from another thread and acts only while one of them is running.
 * The connection itself comes from [newConnection], so tests can use a fake transport.
 */
class PrinterSession(
  private val newConnection: (macAddress: String) -> PrinterConnection,
  private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
  private val resetSettleMs: Long = RESET_SETTLE_MS,
  private val closeGraceMs: Long = CLOSE_GRACE_MS,
  private val onWarning: (String) -> Unit = {},
  /**
   * Test seam: called by [cancel] while it holds [lock], after it saw a call running and before it
   * flags it. The default does nothing. Tests block here to stop a cancel exactly at the boundary
   * between two calls.
   */
  private val onCancelWindow: () -> Unit = {},
  /**
   * Test seam: called by [cancel] after it released [lock] and before it closes the connection it
   * saw. The default does nothing. Tests block here to let the call end and the next one start.
   */
  private val beforeCancel: () -> Unit = {},
  /** Milliseconds from a monotonic clock, to time the reconnect window. Injectable for tests. */
  private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
  /** How long a probe waits, unless the printer may still be busy. Injectable for tests. */
  private val probeTimeoutMs: Long = PROBE_TIMEOUT_MS,
) {
  /**
   * Guards [busy] and [cancelRequested] so a cancel is decided against one call only: it never
   * flags a call that started after the one it saw running. Held only for those writes, never
   * while blocking on I/O.
   */
  private val lock = Any()

  /** The number of the call in progress, raised when each call starts. Read and written under [lock]. */
  private var generation = 0L

  /** The open connection, read by [cancel] from another thread. */
  @Volatile private var connection: PrinterConnection? = null

  /** The printer of the connection made by [open], as an upper-case MAC. Job thread only. */
  private var openPrinter = ""

  /**
   * The printers (upper-case MAC) whose last job was sent without asking for confirmation, so they
   * may still be printing it. A printer leaves the set when it answers a probe or confirms a job.
   * Kept in memory. Job thread only.
   */
  private val unconfirmedPrinters = mutableSetOf<String>()

  /** true when the last job or open hung, was cancelled or failed. */
  @Volatile private var lastJobFailed = false

  /** true while [open], [runJob], [runAtomic] or [closePort] is running. */
  @Volatile private var busy = false

  /** true when [cancel] arrived during the running call, even before it had a connection. */
  @Volatile private var cancelRequested = false

  /** The [clock] time at which the last cancelled call ended; null when no call was cancelled. */
  @Volatile private var cancelledCallEndedAt: Long? = null

  /**
   * Opens the connection within [connectTimeoutMs]. With [resetAfterFailure], when the previous
   * job failed, the printer is first reset with `<ESC>!R` and given time to come back.
   */
  fun open(macAddress: String, connectTimeoutMs: Long, resetAfterFailure: Boolean): String = exclusive {
    // Always close any existing connection before opening a new one.
    closeConnection()
    try {
      if (resetAfterFailure && lastJobFailed) resetPrinter(macAddress, connectTimeoutMs)
      throwIfCancelRequested()
      val opened = newConnection(macAddress)
      connection = opened
      openPrinter = macAddress.uppercase()
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
   * Sends a job on the open connection. With [probe], the printer has to answer a probe before any
   * byte of the job is sent, see [probePrinter].
   *
   * With [closePort], and [confirmCompletion] true, waits for the printer to confirm it processed
   * everything (TSPL `OUT` marker) within the limit, then closes the connection. With [closePort]
   * and [confirmCompletion] false, it does not wait: it gives the printer the close grace time to
   * receive the last bytes, then closes. A [cancel] during that time ends it at once with
   * [PrinterErrorCode.CANCELLED].
   * Any failure closes the connection, so the next job starts on a new one.
   */
  fun runJob(
    closePort: Boolean,
    limits: JobLimits,
    confirmCompletion: Boolean = true,
    probe: Boolean = false,
    send: (PrinterConnection) -> Unit,
  ): String = exclusive {
    val current = connection
    if (current == null || !current.isOpen) {
      lastJobFailed = true
      throw PrinterException(
        PrinterErrorCode.NOT_CONNECTED,
        "No hay conexión abierta con la impresora; llama a openPort primero",
      )
    }
    try {
      if (probe) probePrinter(current, openPrinter, limits)
      send(current)
      if (!confirmCompletion) unconfirmedPrinters.add(openPrinter)
      if (closePort) {
        if (confirmCompletion) {
          awaitCompletion(current, openPrinter, limits)
        } else {
          waitUnlessCancelled(closeGraceMs)
        }
        closeConnection()
      }
      lastJobFailed = false
      "Success"
    } catch (e: Throwable) {
      lastJobFailed = true
      closeConnection()
      throw e
    }
  }

  /**
   * One whole job in one call: closes a connection left open by hand (takeover), opens a fresh
   * connection within [connectTimeoutMs], with [probe] checks that the printer answers (see
   * [probePrinter]), runs [send], then, with [confirmCompletion], asks the
   * printer to confirm it processed everything and waits for it within the limit. Without it, the
   * call waits the close grace time so the printer receives the last bytes, and does not wait for
   * the confirmation. The connection is closed on every path.
   *
   * The callers run on the plugin's job thread, so atomic calls run first-in-first-out. A [cancel]
   * ends only the call that is running; the flag is reset when each call starts, so a call that
   * was waiting in the queue starts clean. A cancel during the close grace wait ends the wait at
   * once and the call with [PrinterErrorCode.CANCELLED].
   *
   * Right after a cancelled call, a connection that fails is retried, see [openForAtomic].
   *
   * The time of each phase goes to [phases]; the probe is part of the connect phase. A render
   * measured inside [send] (with [PrintPhase.RENDER]) is taken out of the send phase, so no time
   * is counted twice.
   */
  fun runAtomic(
    macAddress: String,
    connectTimeoutMs: Long,
    confirmCompletion: Boolean,
    limits: JobLimits,
    phases: PhaseTimer,
    probe: Boolean = false,
    send: (PrinterConnection) -> Unit,
  ): String = exclusive {
    // Takeover: a connection left open with openPort is closed first.
    closeConnection()
    val printer = macAddress.uppercase()
    try {
      throwIfCancelRequested()
      val opened = phases.measure(PrintPhase.CONNECT) {
        openForAtomic(macAddress, connectTimeoutMs).also { if (probe) probePrinter(it, printer, limits) }
      }
      phases.measureExcluding(PrintPhase.SEND, PrintPhase.RENDER) { send(opened) }
      if (!confirmCompletion) unconfirmedPrinters.add(printer)
      phases.measure(PrintPhase.CONFIRM) {
        if (confirmCompletion) {
          awaitCompletion(opened, printer, limits)
        } else {
          waitUnlessCancelled(closeGraceMs)
        }
      }
      lastJobFailed = false
      "Success"
    } catch (e: Throwable) {
      lastJobFailed = true
      throw e
    } finally {
      closeConnection()
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
   * It never waits for the socket: [PrinterConnection.cancel] closes it on the watchdog thread, and
   * not at all while a block is being written (see [PrinterConnection.writeBlock]).
   */
  fun cancel() {
    // The check and the flag are one step against the start and end of a call, so a cancel aimed at
    // the call it saw running cannot land on the next one in the queue. The connection is cancelled
    // outside the lock, because closing it may block. A call can end and the next one start, reusing
    // that connection, before the cancel gets there, so the call number is checked again first.
    val (target, seenGeneration) = synchronized(lock) {
      if (!busy) return
      onCancelWindow()
      cancelRequested = true
      Pair(connection, generation)
    }
    if (target == null) return
    beforeCancel()
    if (synchronized(lock) { generation == seenGeneration }) target.cancel()
  }

  /** Called when the plugin detaches: aborts whatever is running. */
  fun shutdown() {
    val target = synchronized(lock) {
      cancelRequested = true
      connection
    }
    target?.cancel()
  }

  /**
   * Opens the connection of an atomic call. A printer may refuse connections for a moment after a
   * cancelled call closed its link, so a connection that fails while less than
   * [RECONNECT_WINDOW_MS] have passed since a cancelled call ended is closed, and after
   * [RECONNECT_PAUSE_MS] a new one is tried. Any other failure is reported as it is, so an
   * unreachable printer does not multiply the connect timeout. A [cancel] ends the pause at once.
   */
  private fun openForAtomic(macAddress: String, connectTimeoutMs: Long): PrinterConnection {
    while (true) {
      // A failed connection is closed for good, so each attempt gets a new one.
      val fresh = newConnection(macAddress)
      connection = fresh
      // A cancel that arrived before the connection was published must still apply.
      if (cancelRequested) fresh.cancel()
      try {
        fresh.open(connectTimeoutMs)
        return fresh
      } catch (e: PrinterException) {
        if (e.code != PrinterErrorCode.NOT_CONNECTED || !isRightAfterACancelledCall()) throw e
        closeConnection()
        waitUnlessCancelled(RECONNECT_PAUSE_MS)
      }
    }
  }

  /**
   * Checks that the printer answers before any byte of a job is sent to it. It waits
   * [probeTimeoutMs], or the completion limit of the call when the previous job on that printer was
   * not confirmed, because it may still be printing. Without an answer it throws and the connection
   * is closed.
   */
  private fun probePrinter(target: PrinterConnection, printer: String, limits: JobLimits) {
    val timeoutMs = if (printer in unconfirmedPrinters) limits.completionTimeoutMs else probeTimeoutMs
    target.probe(timeoutMs)
    unconfirmedPrinters.remove(printer)
  }

  /** Asks the printer to confirm it processed everything and waits for it within the limit. */
  private fun awaitCompletion(target: PrinterConnection, printer: String, limits: JobLimits) {
    target.write(COMPLETION_REQUEST, limits.writeTimeoutMs)
    target.awaitMarker(COMPLETION_MARKER, limits.completionTimeoutMs)
    unconfirmedPrinters.remove(printer)
  }

  private fun isRightAfterACancelledCall(): Boolean {
    val endedAt = cancelledCallEndedAt ?: return false
    return clock() - endedAt < RECONNECT_WINDOW_MS
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
    waitUnlessCancelled(resetSettleMs)
  }

  /**
   * Waits [totalMs] in short slices, so a [cancel] ends the wait with [PrinterErrorCode.CANCELLED].
   * A cancel that arrives during the last slice ends it the same way.
   */
  private fun waitUnlessCancelled(totalMs: Long) {
    var waited = 0L
    while (waited < totalMs) {
      throwIfCancelRequested()
      val slice = minOf(WAIT_SLICE_MS, totalMs - waited)
      sleeper(slice)
      waited += slice
    }
    throwIfCancelRequested()
  }

  private fun throwIfCancelRequested() {
    if (cancelRequested) throw PrinterException(PrinterErrorCode.CANCELLED, "La impresión se canceló")
  }

  private fun closeConnection() {
    connection?.close()
    connection = null
  }

  private inline fun <T> exclusive(block: () -> T): T {
    synchronized(lock) {
      cancelRequested = false
      busy = true
      generation++
    }
    try {
      return block()
    } finally {
      synchronized(lock) {
        busy = false
        if (cancelRequested) cancelledCallEndedAt = clock()
      }
    }
  }

  companion object {
    const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
    const val DEFAULT_WRITE_TIMEOUT_MS = 20_000L
    const val DEFAULT_COMPLETION_TIMEOUT_MS = 60_000L
    const val CLOSE_GRACE_MS = 1_500L
    const val PROBE_TIMEOUT_MS = 2_000L
    const val RESET_SETTLE_MS = 4_000L
    private const val RECONNECT_WINDOW_MS = 5_000L
    private const val RECONNECT_PAUSE_MS = 500L
    private const val WAIT_SLICE_MS = 100L
    private const val NANOS_PER_MILLI = 1_000_000L

    /** Same bytes the TSC SDK sent: an `OUT` marker plus the `ENDLINE` its reader waited for. */
    val COMPLETION_REQUEST: ByteArray =
      ("OUT \"COMPLETED\\r\\n\"\r\n" + "\r\n" + "OUT \"ENDLINE\"\r\n").toByteArray(Charsets.US_ASCII)
    const val COMPLETION_MARKER = "ENDLINE"

    /** TSPL immediate command `<ESC>!R`: resets the printer and deletes downloaded files. */
    val RESET_COMMAND: ByteArray = byteArrayOf(0x1B, '!'.code.toByte(), 'R'.code.toByte())
  }
}
