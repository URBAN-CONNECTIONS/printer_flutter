package com.urbanconnections.printer_flutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** The per-phase milliseconds and the one log line, with a fake clock. */
class PhaseTimerTest {
  private var now = 0L
  private val timer = PhaseTimer { now }

  @Test
  fun formatLine_listsEveryPhaseInOrder() {
    timer.measure(PrintPhase.CONNECT) { now += 210 }
    timer.measure(PrintPhase.RENDER) { now += 40 }
    timer.measure(PrintPhase.SEND) { now += 900 }
    timer.measure(PrintPhase.CONFIRM) { now += 120 }

    assertEquals(
      "Atomic print phases: connect=210ms render=40ms send=900ms confirm=120ms",
      timer.formatLine(),
    )
  }

  @Test
  fun formatLine_showsZeroForAPhaseThatNeverRan() {
    timer.measure(PrintPhase.CONNECT) { now += 15 }
    timer.measure(PrintPhase.SEND) { now += 7 }

    assertEquals(
      "Atomic print phases: connect=15ms render=0ms send=7ms confirm=0ms",
      timer.formatLine(),
    )
  }

  @Test
  fun formatLine_hasNoAddressOrPin() {
    val line = timer.formatLine()

    assertFalse(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}").containsMatchIn(line))
    assertFalse(line.contains("pin", ignoreCase = true))
  }

  @Test
  fun measure_recordsThePhaseAlsoWhenTheBlockThrows() {
    assertFailsWith<IllegalStateException> {
      timer.measure(PrintPhase.SEND) {
        now += 25
        throw IllegalStateException("boom")
      }
    }

    assertEquals(25, timer.millis(PrintPhase.SEND))
  }

  @Test
  fun measure_addsUpRepeatedRunsOfTheSamePhase() {
    timer.measure(PrintPhase.SEND) { now += 10 }
    timer.measure(PrintPhase.SEND) { now += 5 }

    assertEquals(15, timer.millis(PrintPhase.SEND))
  }

  @Test
  fun measureExcluding_leavesOutTheRenderTimedInsideTheBlock() {
    timer.measureExcluding(PrintPhase.SEND, PrintPhase.RENDER) {
      now += 100 // render
      timer.add(PrintPhase.RENDER, 100)
      now += 30 // writing
    }

    assertEquals(100, timer.millis(PrintPhase.RENDER))
    assertEquals(30, timer.millis(PrintPhase.SEND))
  }

  @Test
  fun measureExcluding_forATsplJobHasZeroRender() {
    timer.measureExcluding(PrintPhase.SEND, PrintPhase.RENDER) { now += 30 }

    assertEquals(0, timer.millis(PrintPhase.RENDER))
    assertEquals(30, timer.millis(PrintPhase.SEND))
  }
}
