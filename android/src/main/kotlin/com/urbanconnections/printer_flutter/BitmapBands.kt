package com.urbanconnections.printer_flutter

/**
 * Splits a monochrome page (1 bit per dot, `widthBytes` bytes per row, `height` rows) into the
 * `BITMAP` bands that are sent to the printer one block at a time. Plain Kotlin, no Android types.
 *
 * Splitting happens after the whole page was converted and dithered, so the bytes of all bands put
 * together are exactly the bytes of one `BITMAP` for the page and no seam appears between bands.
 */
object BitmapBands {
  /** The most rows a band has: about 14 KB at 72 mm, so finishing a block on cancel is quick. */
  const val MAX_BAND_ROWS = 200

  private val LINE_END = "\r\n".toByteArray(Charsets.US_ASCII)

  /** One band: rows `y` until `y + rows` of the page, found at [dataOffset] of the page buffer. */
  class Band(
    val y: Int,
    val rows: Int,
    val widthBytes: Int,
    val dataOffset: Int,
  ) {
    /** The `BITMAP` command without its data. */
    val header: ByteArray
      get() = "BITMAP 0,$y,$widthBytes,$rows,0,".toByteArray(Charsets.US_ASCII)

    val dataLength: Int
      get() = widthBytes * rows

    /**
     * The whole block of the band: header, data and the line end that closes the command. The line
     * end is part of the block so a cancel never leaves a band without it.
     */
    fun toBlock(mono: ByteArray): ByteArray {
      val head = header
      val block = ByteArray(head.size + dataLength + LINE_END.size)
      head.copyInto(block)
      mono.copyInto(block, head.size, dataOffset, dataOffset + dataLength)
      LINE_END.copyInto(block, head.size + dataLength)
      return block
    }
  }

  /**
   * The bands of a page, in order. A page of at most [maxRows] rows (also an empty one) is one band;
   * the last band of a longer page has what is left.
   */
  fun split(widthBytes: Int, height: Int, maxRows: Int = MAX_BAND_ROWS): List<Band> {
    require(widthBytes > 0) { "widthBytes must be positive" }
    require(height >= 0) { "height must not be negative" }
    require(maxRows > 0) { "maxRows must be positive" }

    val bands = ArrayList<Band>(height / maxRows + 1)
    var y = 0
    do {
      val rows = minOf(maxRows, height - y)
      bands.add(Band(y, rows, widthBytes, y * widthBytes))
      y += rows
    } while (y < height)
    return bands
  }
}
