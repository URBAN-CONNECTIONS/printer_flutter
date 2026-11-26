package com.urbanconnections.printer_flutter

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The order of what reaches the printer, over a fake sender: nothing follows a cancel. */
class PrintSenderTest {
  private val widthBytes = 70
  private val height = 450 // 3 bands: 200, 200, 50

  private val mono = ByteArray(widthBytes * height) { (it % 251).toByte() }

  /** What the fake printer link received, in order. A block is recorded only once it is complete. */
  private val events = mutableListOf<String>()
  private val wire = ByteArrayOutputStream()

  private var cancelled = false
  private var cancelWhileSendingBlock = -1
  private var blocksSent = 0

  private fun cancelledError() = PrinterException(PrinterErrorCode.CANCELLED, "cancelled")

  /**
   * Like `PrinterConnection.writeBlock`: a cancel that arrives while the block is written does not
   * cut it; the block ends and then the call ends with the cancel.
   */
  private val sendBlock: (ByteArray) -> Unit = { bytes ->
    if (cancelled) throw cancelledError()
    blocksSent++
    if (blocksSent == cancelWhileSendingBlock) cancelled = true
    wire.write(bytes)
    events += "block:" + String(bytes, 0, bytes.indexOfFirst { it == ','.code.toByte() }, Charsets.US_ASCII)
    if (cancelled) throw cancelledError()
  }

  private val sendText: (String) -> Unit = { text ->
    if (cancelled) throw cancelledError()
    wire.write(text.toByteArray(Charsets.US_ASCII))
    events += "text:" + text.trim()
  }

  private val sender = PrintSender(
    sendText = sendText,
    sendBlock = sendBlock,
    checkCancelled = { if (cancelled) throw cancelledError() },
  )

  /** One page, as `printPageByPage` sends it. */
  private fun printOnePage() {
    sender.command("SIZE 72.0 mm, 57 mm\r\n")
    sender.command("CLS\r\n")
    sender.bitmap(mono, widthBytes, height)
    sender.command("PRINT 1,1\r\n")
  }

  @Test
  fun withoutCancel_thePageIsSentAsBandsThenPrint() {
    printOnePage()

    assertEquals(
      listOf(
        "text:SIZE 72.0 mm, 57 mm",
        "text:CLS",
        "block:BITMAP 0",
        "block:BITMAP 0",
        "block:BITMAP 0",
        "text:PRINT 1,1",
      ),
      events,
    )
    assertEquals(3, blocksSent)
  }

  @Test
  fun theBytesSentAreTheOldSingleBitmapStreamWhenThePageIsOneBand() {
    val short = ByteArray(widthBytes * 100) { (it % 7).toByte() }

    sender.bitmap(short, widthBytes, 100)
    sender.command("PRINT 1,1\r\n")

    val expected = ByteArrayOutputStream()
    expected.write("BITMAP 0,0,$widthBytes,100,0,".toByteArray(Charsets.US_ASCII))
    expected.write(short)
    expected.write("\r\nPRINT 1,1\r\n".toByteArray(Charsets.US_ASCII))
    assertContentEquals(expected.toByteArray(), wire.toByteArray())
  }

  @Test
  fun cancelBeforeTheJob_sendsNothing() {
    cancelled = true

    assertFailsWith<PrinterException> { printOnePage() }

    assertTrue(events.isEmpty())
    assertEquals(0, wire.size())
  }

  @Test
  fun cancelBetweenBands_sendsNoLaterBlockAndNoPrint() {
    // The check before the third band sees the cancel; the second band was already complete.
    var checks = 0
    val s = PrintSender(
      sendText = sendText,
      sendBlock = sendBlock,
      checkCancelled = {
        checks++
        if (checks == 3) cancelled = true
        if (cancelled) throw cancelledError()
      },
    )

    val error = assertFailsWith<PrinterException> {
      s.bitmap(mono, widthBytes, height)
      s.command("PRINT 1,1\r\n")
    }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    assertEquals(listOf("block:BITMAP 0", "block:BITMAP 0"), events)
    assertFalse(events.any { it.startsWith("text:PRINT") })
  }

  @Test
  fun cancelWhileABlockIsSent_letsThatBlockCompleteAndSendsNothingAfter() {
    cancelWhileSendingBlock = 2

    val error = assertFailsWith<PrinterException> { printOnePage() }

    assertEquals(PrinterErrorCode.CANCELLED, error.code)
    // The second band is on the wire complete; the third band and the PRINT never go out.
    assertEquals(
      listOf("text:SIZE 72.0 mm, 57 mm", "text:CLS", "block:BITMAP 0", "block:BITMAP 0"),
      events,
    )
    assertEquals(2, blocksSent)
    val bands = BitmapBands.split(widthBytes, height)
    val firstTwo = ByteArrayOutputStream()
    firstTwo.write("SIZE 72.0 mm, 57 mm\r\nCLS\r\n".toByteArray(Charsets.US_ASCII))
    firstTwo.write(bands[0].toBlock(mono))
    firstTwo.write(bands[1].toBlock(mono))
    assertContentEquals(firstTwo.toByteArray(), wire.toByteArray())
  }

  @Test
  fun cancelWhileTheLastBlockIsSent_sendsNoPrint() {
    cancelWhileSendingBlock = 3

    assertFailsWith<PrinterException> { printOnePage() }

    assertEquals(3, blocksSent)
    assertFalse(events.any { it.startsWith("text:PRINT") })
  }

  @Test
  fun cancelBeforePrint_sendsNoPrint() {
    sender.command("CLS\r\n")
    sender.bitmap(mono, widthBytes, height)
    cancelled = true

    assertFailsWith<PrinterException> { sender.command("PRINT 1,1\r\n") }

    assertFalse(events.any { it.startsWith("text:PRINT") })
  }

  @Test
  fun aDownloadIsOneBlockWithItsLineEnd_andIsChecked() {
    val pcx = byteArrayOf(1, 2, 3, 4, 5)

    sender.download("P0.PCX", pcx)

    val expected = "DOWNLOAD \"P0.PCX\",5,".toByteArray(Charsets.US_ASCII) + pcx + "\r\n".toByteArray(Charsets.US_ASCII)
    assertContentEquals(expected, wire.toByteArray())
    assertEquals(1, blocksSent)

    cancelled = true
    assertFailsWith<PrinterException> { sender.download("P1.PCX", pcx) }
    assertEquals(1, blocksSent)
  }

  @Test
  fun cancelWhileADownloadIsSent_finishesTheFileAndSendsNothingAfter() {
    val pcx = ByteArray(1000) { it.toByte() }
    cancelWhileSendingBlock = 1

    assertFailsWith<PrinterException> {
      sender.download("P0.PCX", pcx)
      sender.command("PUTPCX 0,0,\"P0.PCX\"\r\nPRINT 1,1\r\n")
    }

    val expected = "DOWNLOAD \"P0.PCX\",1000,".toByteArray(Charsets.US_ASCII) + pcx + "\r\n".toByteArray(Charsets.US_ASCII)
    assertContentEquals(expected, wire.toByteArray())
  }

  @Test
  fun aBitmapWhoseSizeDoesNotMatchIsRejectedBeforeAnythingIsSent() {
    assertFailsWith<IllegalArgumentException> { sender.bitmap(ByteArray(10), widthBytes, height) }

    assertEquals(0, wire.size())
  }
}
