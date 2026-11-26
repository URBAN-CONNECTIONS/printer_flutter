package com.urbanconnections.printer_flutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** How often the conversion and crop loops look at the cancel flag. */
class CancelCheckTest {
  private var checks = 0
  private var cancelled = false

  private val cancel = CancelCheck({
    checks++
    if (cancelled) throw PrinterException(PrinterErrorCode.CANCELLED, "cancelled")
  })

  private fun loop(rows: Int) {
    for (y in 0 until rows) cancel.atRow(y)
  }

  @Test
  fun theCadenceIs128Rows() {
    assertEquals(128, CancelCheck.DEFAULT_EVERY_ROWS)
  }

  @Test
  fun aLoopChecksEvery128Rows() {
    loop(1000)

    // rows 0, 128, 256, 384, 512, 640, 768, 896
    assertEquals(8, checks)
  }

  @Test
  fun aShortLoopChecksOnce() {
    loop(100)

    assertEquals(1, checks)
  }

  @Test
  fun noMoreThan128RowsRunBetweenChecks() {
    val checkedRows = mutableListOf<Int>()
    var row = 0
    val c = CancelCheck({ checkedRows += row })
    for (y in 0 until 2000) {
      row = y
      c.atRow(y)
    }

    val gaps = checkedRows.zipWithNext { a, b -> b - a }
    assertEquals(true, gaps.all { it <= 128 })
  }

  @Test
  fun aCancelStopsTheLoopWithinAtMost128Rows() {
    var lastRow = -1
    val c = CancelCheck({ if (cancelled) throw PrinterException(PrinterErrorCode.CANCELLED, "cancelled") })

    val error = assertFailsWith<PrinterException> {
      for (y in 0 until 5000) {
        if (y == 300) cancelled = true
        lastRow = y
        c.atRow(y)
      }
    }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    // Cancelled at row 300: the next check is at row 384.
    assertEquals(384, lastRow)
    assertEquals(true, lastRow - 300 <= 128)
  }

  @Test
  fun nowChecksAtOnce() {
    cancel.now()
    assertEquals(1, checks)

    cancelled = true
    assertFailsWith<PrinterException> { cancel.now() }
  }

  @Test
  fun aCustomCadenceIsUsed() {
    val c = CancelCheck({ checks++ }, everyRows = 10)

    for (y in 0 until 35) c.atRow(y)

    assertEquals(4, checks)
  }

  @Test
  fun aCadenceOfZeroIsRejected() {
    assertFailsWith<IllegalArgumentException> { CancelCheck({}, everyRows = 0) }
  }
}
