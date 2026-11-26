package com.urbanconnections.printer_flutter

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The 200-row `BITMAP` bands: same bytes as one `BITMAP`, exact counts, offsets and last band. */
class BitmapBandsTest {
  /** Width in bytes of a page of [paperWidthMm] at 203 dpi, as `PdfPrintHelper.printPdf` computes it. */
  private fun widthBytesOf(paperWidthMm: Double): Int {
    val pixelWidth = (paperWidthMm * (203 / 25.4)).roundToInt()
    return (pixelWidth / 16) * 16 / 8
  }

  private fun page(widthBytes: Int, height: Int) =
    Random(widthBytes * 31 + height).nextBytes(widthBytes * height)

  /** The page as it was sent before the bands: one `BITMAP` for the whole page. */
  private fun singleBitmap(mono: ByteArray, widthBytes: Int, height: Int): ByteArray {
    val out = ByteArrayOutputStream()
    out.write("BITMAP 0,0,$widthBytes,$height,0,".toByteArray(Charsets.US_ASCII))
    out.write(mono)
    out.write("\r\n".toByteArray(Charsets.US_ASCII))
    return out.toByteArray()
  }

  @Test
  fun bandsReassembleToTheDataOfOneBitmap_forEveryPaperWidth() {
    for (mm in listOf(58.0, 72.0, 80.0, 100.0)) {
      val widthBytes = widthBytesOf(mm)
      for (height in listOf(1, 199, 200, 201, 400, 1234, 1920)) {
        val mono = page(widthBytes, height)

        val reassembled = ByteArrayOutputStream()
        for (band in BitmapBands.split(widthBytes, height)) {
          val block = band.toBlock(mono)
          val skip = band.header.size
          reassembled.write(block, skip, band.dataLength)
        }

        assertContentEquals(mono, reassembled.toByteArray(), "$mm mm, $height rows")
      }
    }
  }

  @Test
  fun bandsOfAPage_haveExactCountsOffsetsAndLastBand() {
    val widthBytes = widthBytesOf(72.0)

    val bands = BitmapBands.split(widthBytes, 1234)

    assertEquals(7, bands.size)
    assertEquals(listOf(0, 200, 400, 600, 800, 1000, 1200), bands.map { it.y })
    assertEquals(listOf(200, 200, 200, 200, 200, 200, 34), bands.map { it.rows })
    assertEquals(bands.map { it.y * widthBytes }, bands.map { it.dataOffset })
    assertEquals(1234, bands.sumOf { it.rows })
  }

  @Test
  fun anExactMultipleHasNoShortBand() {
    val bands = BitmapBands.split(widthBytesOf(58.0), 600)

    assertEquals(listOf(0, 200, 400), bands.map { it.y })
    assertEquals(listOf(200, 200, 200), bands.map { it.rows })
  }

  @Test
  fun aPageOfAtMost200Rows_isOneBitmap() {
    val widthBytes = widthBytesOf(80.0)

    for (height in listOf(1, 150, 199, 200)) {
      val bands = BitmapBands.split(widthBytes, height)
      assertEquals(1, bands.size, "$height rows")
      assertEquals(0, bands[0].y)
      assertEquals(height, bands[0].rows)
    }
  }

  @Test
  fun aPageOfOneRowOver200_hasAOneRowLastBand() {
    val bands = BitmapBands.split(widthBytesOf(72.0), 201)

    assertEquals(listOf(200, 1), bands.map { it.rows })
    assertEquals(listOf(0, 200), bands.map { it.y })
  }

  @Test
  fun theHeaderIsTheBitmapCommandWithTheYOffset() {
    val widthBytes = widthBytesOf(72.0)

    val bands = BitmapBands.split(widthBytes, 250)

    assertEquals("BITMAP 0,0,$widthBytes,200,0,", String(bands[0].header, Charsets.US_ASCII))
    assertEquals("BITMAP 0,200,$widthBytes,50,0,", String(bands[1].header, Charsets.US_ASCII))
  }

  @Test
  fun aBlockIsHeaderDataAndLineEnd_andASingleBandIsTheOldOneBitmapStream() {
    for (mm in listOf(58.0, 72.0, 80.0, 100.0)) {
      val widthBytes = widthBytesOf(mm)
      val height = 150
      val mono = page(widthBytes, height)

      val block = BitmapBands.split(widthBytes, height).single().toBlock(mono)

      assertContentEquals(singleBitmap(mono, widthBytes, height), block, "$mm mm")
    }
  }

  @Test
  fun everyBandBlockEndsWithALineEnd() {
    val widthBytes = widthBytesOf(72.0)
    val mono = page(widthBytes, 450)

    for (band in BitmapBands.split(widthBytes, 450)) {
      val block = band.toBlock(mono)
      assertEquals("\r\n", String(block, block.size - 2, 2, Charsets.US_ASCII))
    }
  }

  @Test
  fun aBlockCopiesOnlyItsOwnRows() {
    val widthBytes = 4
    val mono = ByteArray(widthBytes * 5) { (it / widthBytes).toByte() }

    val block = BitmapBands.split(widthBytes, 5, maxRows = 2)[1].toBlock(mono)

    val header = "BITMAP 0,2,4,2,0,"
    assertEquals(header, String(block, 0, header.length, Charsets.US_ASCII))
    assertContentEquals(
      byteArrayOf(2, 2, 2, 2, 3, 3, 3, 3),
      block.copyOfRange(header.length, header.length + 8),
    )
  }

  @Test
  fun invalidSizesAreRejected() {
    assertFailsWith<IllegalArgumentException> { BitmapBands.split(0, 10) }
    assertFailsWith<IllegalArgumentException> { BitmapBands.split(10, -1) }
    assertFailsWith<IllegalArgumentException> { BitmapBands.split(10, 10, maxRows = 0) }
  }
}
