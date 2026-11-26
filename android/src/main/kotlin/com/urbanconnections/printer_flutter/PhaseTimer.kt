package com.urbanconnections.printer_flutter

/** The phases of an atomic print call that are timed. */
enum class PrintPhase(val label: String) {
  CONNECT("connect"),
  RENDER("render"),
  SEND("send"),
  CONFIRM("confirm"),
}

/**
 * Records how many milliseconds each [PrintPhase] of one atomic print call took and formats them as
 * one log line. The line holds only phase names and numbers: never a MAC address or a PIN.
 *
 * One timer belongs to one call and is used from the thread that runs it. The clock is injectable
 * (milliseconds) so tests do not depend on real time.
 */
class PhaseTimer(private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }) {
  private val elapsed = LongArray(PrintPhase.values().size)

  /** Runs [block] and adds its duration to [phase], also when it throws. */
  fun <T> measure(phase: PrintPhase, block: () -> T): T {
    val start = clock()
    try {
      return block()
    } finally {
      add(phase, clock() - start)
    }
  }

  /**
   * Like [measure], but leaves out the time that [block] recorded under [excluded] (a phase timed
   * inside it), so the same milliseconds are not counted in two phases.
   */
  fun <T> measureExcluding(phase: PrintPhase, excluded: PrintPhase, block: () -> T): T {
    val excludedBefore = millis(excluded)
    val start = clock()
    try {
      return block()
    } finally {
      add(phase, clock() - start - (millis(excluded) - excludedBefore))
    }
  }

  /** Adds [millis] to [phase]. Negative values are ignored. */
  fun add(phase: PrintPhase, millis: Long) {
    if (millis > 0) elapsed[phase.ordinal] += millis
  }

  /** The milliseconds recorded for [phase]; 0 when it never ran. */
  fun millis(phase: PrintPhase): Long = elapsed[phase.ordinal]

  /** One line such as `Atomic print phases: connect=210ms render=40ms send=900ms confirm=120ms`. */
  fun formatLine(): String =
    PrintPhase.values().joinToString(
      separator = " ",
      prefix = "Atomic print phases: ",
    ) { "${it.label}=${millis(it)}ms" }

  private companion object {
    const val NANOS_PER_MILLI = 1_000_000L
  }
}
