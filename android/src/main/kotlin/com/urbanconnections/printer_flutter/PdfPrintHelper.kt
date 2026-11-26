package com.urbanconnections.printer_flutter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.roundToInt

object PdfPrintHelper {

    /**
     * Renders [filePath] and sends it to the printer.
     *
     * [sendString] sends a control command; [sendBlock] sends one block (a `BITMAP` band or a
     * `DOWNLOAD` file) that a cancel must not cut; [checkCancelled] throws when the job was
     * cancelled. Nothing is sent after a cancel, and rendering stops at the next page or after at
     * most [CancelCheck.DEFAULT_EVERY_ROWS] rows.
     */
    fun printPdf(
        filePath: String,
        options: Map<String, Any>,
        copies: Int = 1,
        sendBlock: (ByteArray) -> Unit,
        sendString: (String) -> Unit,
        checkCancelled: () -> Unit,
        phases: PhaseTimer? = null
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            throw Exception("PDF file not found at path: $filePath")
        }

        val paperWidthMm = (options["paperWidthMm"] as? Double) ?: 72.0
        val dpi = (options["dpi"] as? Int) ?: 203
        val strategy = (options["strategy"] as? String) ?: "unifiedRoll"
        val enableDithering = (options["enableDithering"] as? Boolean) ?: true
        val trimWhitespace = (options["trimWhitespace"] as? Boolean) ?: false

        // Calculate dots per mm
        val dotsPerMm = dpi / 25.4
        val pixelWidth = (paperWidthMm * dotsPerMm).roundToInt()
        // Ensure pixel width is a multiple of 16 for even byte alignment (required by PCX)
        val alignedWidth = (pixelWidth / 16) * 16
        val widthBytes = alignedWidth / 8

        val sender = PrintSender(sendString, sendBlock, checkCancelled)
        val cancel = CancelCheck(checkCancelled)

        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // A corrupt, encrypted or non-PDF file makes the renderer throw: the descriptor must still close.
        val pdfRenderer = try {
            PdfRenderer(fileDescriptor)
        } catch (e: Throwable) {
            fileDescriptor.close()
            throw e
        }

        try {
            val pageCount = pdfRenderer.pageCount
            if (pageCount == 0) return

            if (strategy == "unifiedRoll") {
                printUnifiedRoll(pdfRenderer, alignedWidth, widthBytes, enableDithering, trimWhitespace, sender, cancel, paperWidthMm, copies, phases)
            } else if (strategy == "dramBatch") {
                printDramBatch(pdfRenderer, alignedWidth, widthBytes, enableDithering, trimWhitespace, sender, cancel, paperWidthMm, copies, phases)
            } else {
                printPageByPage(pdfRenderer, alignedWidth, widthBytes, enableDithering, trimWhitespace, sender, cancel, paperWidthMm, copies, phases)
            }
        } finally {
            pdfRenderer.close()
            fileDescriptor.close()
        }
    }

    /**
     * Renders page [index] at [alignedWidth] dots, cropped when [trimWhitespace]. Checks the cancel
     * first. The page is closed and every bitmap that is not returned is recycled on every path,
     * also when the render or the crop fails or is cancelled. The caller recycles the result.
     */
    private fun renderPageBitmap(
        pdfRenderer: PdfRenderer,
        index: Int,
        alignedWidth: Int,
        trimWhitespace: Boolean,
        cancel: CancelCheck,
        phases: PhaseTimer?
    ): Bitmap {
        cancel.now()
        val page = pdfRenderer.openPage(index)
        val bitmap = try {
            renderPage(page, alignedWidth, phases)
        } finally {
            page.close()
        }

        try {
            val height = bitmap.height
            var finalHeight = height
            if (trimWhitespace) {
                finalHeight = phases.measureRender { getCroppedHeight(bitmap, cancel) }
            }

            return if (finalHeight != height) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, alignedWidth, finalHeight)
                bitmap.recycle()
                cropped
            } else {
                bitmap
            }
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
    }

    /** Draws [page] on a white bitmap of [alignedWidth] dots; the bitmap is recycled if drawing fails. */
    private fun renderPage(page: PdfRenderer.Page, alignedWidth: Int, phases: PhaseTimer?): Bitmap {
        // scale height proportionally
        val scale = alignedWidth.toFloat() / page.width.toFloat()
        val height = (page.height * scale).roundToInt()

        val bitmap = Bitmap.createBitmap(alignedWidth, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            phases.measureRender { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) }
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    private fun printUnifiedRoll(
        pdfRenderer: PdfRenderer,
        alignedWidth: Int,
        widthBytes: Int,
        enableDithering: Boolean,
        trimWhitespace: Boolean,
        sender: PrintSender,
        cancel: CancelCheck,
        paperWidthMm: Double,
        copies: Int,
        phases: PhaseTimer?
    ) {
        val pageCount = pdfRenderer.pageCount
        val bitmaps = mutableListOf<Bitmap>()
        var unifiedBitmap: Bitmap? = null

        try {
            var totalHeight = 0

            for (i in 0 until pageCount) {
                val pageBitmap = renderPageBitmap(pdfRenderer, i, alignedWidth, trimWhitespace, cancel, phases)
                bitmaps.add(pageBitmap)
                totalHeight += pageBitmap.height
            }

            // Stitch into one large bitmap
            val unified = Bitmap.createBitmap(alignedWidth, totalHeight, Bitmap.Config.ARGB_8888)
            unifiedBitmap = unified
            val canvas = Canvas(unified)
            var currentY = 0f
            for (bitmap in bitmaps) {
                cancel.now()
                canvas.drawBitmap(bitmap, 0f, currentY, null)
                currentY += bitmap.height
                bitmap.recycle() // free memory
            }

            val totalHeightMm = (totalHeight / (203 / 25.4)).roundToInt() // approximate

            // Convert the whole roll to monochrome first (no seams, nothing sent while it works),
            // then send it in bands.
            val monoBytes = phases.measureRender { convertToMonochrome(unified, widthBytes, enableDithering, cancel) }

            // TSPL Init for continuous roll
            sender.command("SIZE $paperWidthMm mm, $totalHeightMm mm\r\n")
            sender.command("GAP 0,0\r\n")
            sender.command("DIRECTION 0\r\n")
            sender.command("CLS\r\n")

            sender.bitmap(monoBytes, widthBytes, totalHeight)
            sender.command("PRINT $copies,1\r\n")
        } finally {
            for (bitmap in bitmaps) bitmap.recycle()
            unifiedBitmap?.recycle()
        }
    }

    private fun printPageByPage(
        pdfRenderer: PdfRenderer,
        alignedWidth: Int,
        widthBytes: Int,
        enableDithering: Boolean,
        trimWhitespace: Boolean,
        sender: PrintSender,
        cancel: CancelCheck,
        paperWidthMm: Double,
        copies: Int,
        phases: PhaseTimer?
    ) {
        val pageCount = pdfRenderer.pageCount

        if (copies <= 1) {
            for (i in 0 until pageCount) {
                val pageBitmap = renderPageBitmap(pdfRenderer, i, alignedWidth, trimWhitespace, cancel, phases)
                try {
                    val finalHeight = pageBitmap.height
                    val heightMm = (finalHeight / (203 / 25.4)).roundToInt()

                    val monoBytes = phases.measureRender { convertToMonochrome(pageBitmap, widthBytes, enableDithering, cancel) }

                    sender.command("SIZE $paperWidthMm mm, $heightMm mm\r\n")
                    sender.command("GAP 0,0\r\n")
                    sender.command("DIRECTION 0\r\n")
                    sender.command("CLS\r\n")

                    sender.bitmap(monoBytes, widthBytes, finalHeight)
                    sender.command("PRINT 1,1\r\n")
                } finally {
                    pageBitmap.recycle()
                }
            }
        } else {
            class CachedPage(val sizeCmd: String, val height: Int, val monoBytes: ByteArray)
            val cache = mutableListOf<CachedPage>()

            for (i in 0 until pageCount) {
                val pageBitmap = renderPageBitmap(pdfRenderer, i, alignedWidth, trimWhitespace, cancel, phases)
                try {
                    val finalHeight = pageBitmap.height
                    val heightMm = (finalHeight / (203 / 25.4)).roundToInt()
                    val sizeCmd = "SIZE $paperWidthMm mm, $heightMm mm\r\nGAP 0,0\r\nDIRECTION 0\r\nCLS\r\n"

                    val monoBytes = phases.measureRender { convertToMonochrome(pageBitmap, widthBytes, enableDithering, cancel) }

                    cache.add(CachedPage(sizeCmd, finalHeight, monoBytes))
                } finally {
                    pageBitmap.recycle()
                }
            }

            for (c in 0 until copies) {
                for (cached in cache) {
                    sender.command(cached.sizeCmd)
                    sender.bitmap(cached.monoBytes, widthBytes, cached.height)
                    sender.command("PRINT 1,1\r\n")
                }
            }
        }
    }

    private fun printDramBatch(
        pdfRenderer: PdfRenderer,
        alignedWidth: Int,
        widthBytes: Int,
        enableDithering: Boolean,
        trimWhitespace: Boolean,
        sender: PrintSender,
        cancel: CancelCheck,
        paperWidthMm: Double,
        copies: Int,
        phases: PhaseTimer?
    ) {
        val pageCount = pdfRenderer.pageCount
        println("PdfPrintHelper: printDramBatch started. Pages to print: $pageCount, Copies: $copies")
        if (pageCount == 0) return

        println("PdfPrintHelper: Sending initial KILL command...")
        sender.command("KILL \"P*.PCX\"\r\n")
        pauseUnlessCancelled(200, cancel)

        val cachedPages = mutableListOf<String>()

        for (i in 0 until pageCount) {
            println("PdfPrintHelper: Processing page $i...")
            val pageBitmap = renderPageBitmap(pdfRenderer, i, alignedWidth, trimWhitespace, cancel, phases)
            try {
                val finalHeight = pageBitmap.height

                println("PdfPrintHelper: Converting page $i to 1-bit PCX...")
                val pcxBytes = phases.measureRender { convertToPcxFile(pageBitmap, enableDithering, cancel) }
                val filename = "P$i.PCX"

                println("PdfPrintHelper: Downloading $filename to DRAM (${pcxBytes.size} bytes)...")
                // DOWNLOAD "FILENAME", size, data, then a line end that cleanly terminates the
                // binary payload: one block, so a cancel never cuts the file.
                sender.download(filename, pcxBytes)

                // Give the printer filesystem a moment to save the file
                // to avoid buffer overflow or file system crashes on weak thermal printers.
                pauseUnlessCancelled(300, cancel)

                val heightMm = (finalHeight / (203 / 25.4)).roundToInt()
                val sizeCmd = "SIZE $paperWidthMm mm, $heightMm mm\r\nGAP 0,0\r\nDIRECTION 0\r\nCLS\r\n"

                cachedPages.add(sizeCmd + "PUTPCX 0,0,\"$filename\"\r\nPRINT 1,1\r\n")
            } finally {
                pageBitmap.recycle()
            }
        }

        println("PdfPrintHelper: Finished downloading all pages. Now executing batch scripts...")
        // Print copies
        for (c in 0 until copies) {
            println("PdfPrintHelper: Executing copy ${c + 1}...")
            for (cachedCmd in cachedPages) {
                sender.command(cachedCmd)
            }
        }

        println("PdfPrintHelper: Batch execution queued. Deleting files is deferred to the next print job to prevent crashing.")
    }


    private fun convertToMonochrome(bitmap: Bitmap, widthBytes: Int, dither: Boolean, cancel: CancelCheck): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val outBytes = ByteArray(widthBytes * height)

        if (dither) {
            // Floyd-Steinberg Dithering
            val errors = FloatArray(width * height)
            for (y in 0 until height) {
                cancel.atRow(y)
                for (x in 0 until width) {
                    val idx = y * width + x
                    val pixel = pixels[idx]
                    
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    
                    // Grayscale
                    var gray = (r * 0.299 + g * 0.587 + b * 0.114).toFloat()
                    gray += errors[idx]

                    // Threshold
                    val isBlack = gray < 128
                    val newGray = if (isBlack) 0f else 255f
                    val err = gray - newGray

                    if (isBlack) {
                        val byteIdx = y * widthBytes + (x / 8)
                        val bitIdx = 7 - (x % 8)
                        outBytes[byteIdx] = (outBytes[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                    }

                    // Propagate error
                    if (x + 1 < width) errors[y * width + (x + 1)] += err * 7f / 16f
                    if (y + 1 < height) {
                        if (x - 1 >= 0) errors[(y + 1) * width + (x - 1)] += err * 3f / 16f
                        errors[(y + 1) * width + x] += err * 5f / 16f
                        if (x + 1 < width) errors[(y + 1) * width + (x + 1)] += err * 1f / 16f
                    }
                }
            }
        } else {
            // Simple Thresholding
            for (y in 0 until height) {
                cancel.atRow(y)
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val gray = (r * 0.299 + g * 0.587 + b * 0.114)
                    
                    if (gray < 128) { // Black dot
                        val byteIdx = y * widthBytes + (x / 8)
                        val bitIdx = 7 - (x % 8)
                        outBytes[byteIdx] = (outBytes[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                    }
                }
            }
        }
        
        // Invert all bits: for this thermal printer, 0 means black (print) and 1 means white (background)
        for (i in outBytes.indices) {
            outBytes[i] = outBytes[i].toInt().inv().toByte()
        }

        return outBytes
    }

    private fun convertToPcxFile(bitmap: Bitmap, dither: Boolean, cancel: CancelCheck): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        
        // Ensure bytesPerLine is even for PCX
        var bytesPerLine = (width + 7) / 8
        if (bytesPerLine % 2 != 0) {
            bytesPerLine++
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val outStream = java.io.ByteArrayOutputStream()

        // 128 byte PCX header
        val header = ByteArray(128)
        val bb = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.put(10) // Manufacturer (ZSoft)
        bb.put(5) // Version (3.0 with palette)
        bb.put(1) // Encoding (RLE)
        bb.put(1) // BitsPerPixel
        bb.putShort(0) // Xmin
        bb.putShort(0) // Ymin
        bb.putShort((width - 1).toShort()) // Xmax
        bb.putShort((height - 1).toShort()) // Ymax
        bb.putShort(203) // HRes
        bb.putShort(203) // VRes

        // Colormap (16 RGB triplets = 48 bytes)
        bb.position(16)
        bb.put(0.toByte()); bb.put(0.toByte()); bb.put(0.toByte()) // Color 0: Black
        bb.put(255.toByte()); bb.put(255.toByte()); bb.put(255.toByte()) // Color 1: White
        
        bb.position(64)
        bb.put(0) // Reserved
        bb.put(1) // NPlanes
        bb.putShort(bytesPerLine.toShort()) // BytesPerLine
        bb.putShort(1) // PaletteInfo

        outStream.write(header)

        // Compute 1-bit PCX rows (1 = White, 0 = Black)
        val pcxRows = Array(height) { ByteArray(bytesPerLine) }

        if (dither) {
            val errors = FloatArray(width * height)
            for (y in 0 until height) {
                cancel.atRow(y)
                for (x in 0 until width) {
                    val idx = y * width + x
                    val pixel = pixels[idx]
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    
                    var gray = (r * 0.299 + g * 0.587 + b * 0.114).toFloat()
                    gray += errors[idx]

                    val isBlack = gray < 128
                    val newGray = if (isBlack) 0f else 255f
                    val err = gray - newGray

                    if (!isBlack) {
                        val byteIdx = x / 8
                        val bitIdx = 7 - (x % 8)
                        pcxRows[y][byteIdx] = (pcxRows[y][byteIdx].toInt() or (1 shl bitIdx)).toByte()
                    }

                    if (x + 1 < width) errors[y * width + (x + 1)] += err * 7f / 16f
                    if (y + 1 < height) {
                        if (x - 1 >= 0) errors[(y + 1) * width + (x - 1)] += err * 3f / 16f
                        errors[(y + 1) * width + x] += err * 5f / 16f
                        if (x + 1 < width) errors[(y + 1) * width + (x + 1)] += err * 1f / 16f
                    }
                }
            }
        } else {
            for (y in 0 until height) {
                cancel.atRow(y)
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val gray = (r * 0.299 + g * 0.587 + b * 0.114)
                    
                    if (gray >= 128) {
                        val byteIdx = x / 8
                        val bitIdx = 7 - (x % 8)
                        pcxRows[y][byteIdx] = (pcxRows[y][byteIdx].toInt() or (1 shl bitIdx)).toByte()
                    }
                }
            }
        }

        // RLE compress the rows
        for (y in 0 until height) {
            cancel.atRow(y)
            val row = pcxRows[y]
            
            // RLE Compression for this row
            var i = 0
            while (i < bytesPerLine) {
                var count = 1
                val current = row[i].toInt() and 0xFF
                while (i + count < bytesPerLine && count < 63 && (row[i + count].toInt() and 0xFF) == current) {
                    count++
                }
                if (count > 1 || current >= 0xC0) {
                    outStream.write(0xC0 or count)
                }
                outStream.write(current)
                i += count
            }
        }

        return outStream.toByteArray()
    }

    private fun getCroppedHeight(bitmap: Bitmap, cancel: CancelCheck): Int {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in height - 1 downTo 0) {
            cancel.atRow(height - 1 - y)
            for (x in 0 until width) {
                if (pixels[y * width + x] != Color.WHITE) {
                    // Add a small 20 pixel padding so it's not flush with the last printed pixel
                    return kotlin.math.min(height, y + 1 + 20)
                }
            }
        }
        return height
    }

    /** Pauses [totalMs] in slices of at most 50 ms, checking for a cancel between them. */
    private fun pauseUnlessCancelled(totalMs: Long, cancel: CancelCheck) {
        var waited = 0L
        while (waited < totalMs) {
            cancel.now()
            val slice = minOf(50L, totalMs - waited)
            Thread.sleep(slice)
            waited += slice
        }
        cancel.now()
    }
}
