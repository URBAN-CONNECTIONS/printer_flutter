package com.urbanconnections.printer_flutter

/**
 * The order in which a PDF print job reaches the printer. Plain Kotlin, no Android types, so the
 * order is tested on the JVM with a fake sender.
 *
 * Every step first calls `checkCancelled` (which throws [PrinterErrorCode.CANCELLED]), so after a
 * cancel nothing is sent: not the next block and not a `PRINT`. A block is sent in one piece by
 * `sendBlock` (`PrinterConnection.writeBlock`), which finishes the block that a cancel found in
 * progress and then ends the call with the cancel.
 */
class PrintSender(
  private val sendText: (String) -> Unit,
  private val sendBlock: (ByteArray) -> Unit,
  private val checkCancelled: () -> Unit,
) {
  /** Sends a control command (`SIZE`, `CLS`, `PRINT`...) unless the job was cancelled. */
  fun command(text: String) {
    checkCancelled()
    sendText(text)
  }

  /**
   * Sends a converted page as `BITMAP` bands of at most [BitmapBands.MAX_BAND_ROWS] rows, one block
   * per band, checking the cancel before each one.
   */
  fun bitmap(mono: ByteArray, widthBytes: Int, height: Int) {
    require(mono.size == widthBytes * height) {
      "mono has ${mono.size} bytes, expected ${widthBytes * height}"
    }
    for (band in BitmapBands.split(widthBytes, height)) {
      checkCancelled()
      sendBlock(band.toBlock(mono))
    }
  }

  /** Sends one file as a single `DOWNLOAD` block, with the line end that closes it. */
  fun download(filename: String, bytes: ByteArray) {
    checkCancelled()
    val header = "DOWNLOAD \"$filename\",${bytes.size},".toByteArray(Charsets.US_ASCII)
    val end = "\r\n".toByteArray(Charsets.US_ASCII)
    val block = ByteArray(header.size + bytes.size + end.size)
    header.copyInto(block)
    bytes.copyInto(block, header.size)
    end.copyInto(block, header.size + bytes.size)
    sendBlock(block)
  }
}

/**
 * Calls `check` (which throws when the job was cancelled) at least every `everyRows` rows of a
 * conversion or crop loop, and on demand at page boundaries.
 */
class CancelCheck(
  private val check: () -> Unit,
  private val everyRows: Int = DEFAULT_EVERY_ROWS,
) {
  init {
    require(everyRows > 0) { "everyRows must be positive" }
  }

  /** Checks now. */
  fun now() = check()

  /** Checks when [row] (counted from 0 in the order the loop visits rows) is a multiple of the cadence. */
  fun atRow(row: Int) {
    if (row % everyRows == 0) check()
  }

  companion object {
    /** The most rows a loop may run without a check. */
    const val DEFAULT_EVERY_ROWS = 128
  }
}
