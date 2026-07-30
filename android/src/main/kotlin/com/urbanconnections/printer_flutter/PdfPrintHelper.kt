package com.urbanconnections.printer_flutter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.roundToInt

object PdfPrintHelper {
    
    fun printPdf(
        filePath: String,
        options: Map<String, Any>,
        copies: Int = 1,
        sendCommand: (ByteArray) -> Unit,
        sendString: (String) -> Unit
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
        // Ensure pixel width is a multiple of 8 for byte alignment
        val alignedWidth = (pixelWidth / 8) * 8
        val widthBytes = alignedWidth / 8

        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)

        try {
            val pageCount = pdfRenderer.pageCount
            if (pageCount == 0) return

            if (strategy == "unifiedRoll") {
                printUnifiedRoll(pdfRenderer, alignedWidth, widthBytes, enableDithering, trimWhitespace, sendCommand, sendString, paperWidthMm, copies)
            } else {
                printPageByPage(pdfRenderer, alignedWidth, widthBytes, enableDithering, trimWhitespace, sendCommand, sendString, paperWidthMm, copies)
            }
        } finally {
            pdfRenderer.close()
            fileDescriptor.close()
        }
    }

    private fun printUnifiedRoll(
        pdfRenderer: PdfRenderer,
        alignedWidth: Int,
        widthBytes: Int,
        enableDithering: Boolean,
        trimWhitespace: Boolean,
        sendCommand: (ByteArray) -> Unit,
        sendString: (String) -> Unit,
        paperWidthMm: Double,
        copies: Int
    ) {
        val pageCount = pdfRenderer.pageCount
        val bitmaps = mutableListOf<Bitmap>()
        var totalHeight = 0

        for (i in 0 until pageCount) {
            val page = pdfRenderer.openPage(i)
            // scale height proportionally
            val scale = alignedWidth.toFloat() / page.width.toFloat()
            val height = (page.height * scale).roundToInt()
            
            val bitmap = Bitmap.createBitmap(alignedWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            var finalHeight = height
            if (trimWhitespace) {
                finalHeight = getCroppedHeight(bitmap)
            }

            val pageBitmap = if (finalHeight != height) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, alignedWidth, finalHeight)
                bitmap.recycle()
                cropped
            } else {
                bitmap
            }

            bitmaps.add(pageBitmap)
            totalHeight += finalHeight
        }

        // Stitch into one large bitmap
        val unifiedBitmap = Bitmap.createBitmap(alignedWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(unifiedBitmap)
        var currentY = 0f
        for (bitmap in bitmaps) {
            canvas.drawBitmap(bitmap, 0f, currentY, null)
            currentY += bitmap.height
            bitmap.recycle() // free memory
        }

        val totalHeightMm = (totalHeight / (203 / 25.4)).roundToInt() // approximate
        
        // TSPL Init for continuous roll
        sendString("SIZE $paperWidthMm mm, $totalHeightMm mm\r\n")
        sendString("GAP 0,0\r\n")
        sendString("DIRECTION 0\r\n")
        sendString("CLS\r\n")

        // Convert to monochrome and send
        val monoBytes = convertToMonochrome(unifiedBitmap, widthBytes, enableDithering)
        val header = "BITMAP 0,0,$widthBytes,$totalHeight,0,".toByteArray(Charsets.US_ASCII)
        
        sendCommand(header)
        sendCommand(monoBytes)
        sendString("\r\nPRINT $copies,1\r\n")

        unifiedBitmap.recycle()
    }

    private fun printPageByPage(
        pdfRenderer: PdfRenderer,
        alignedWidth: Int,
        widthBytes: Int,
        enableDithering: Boolean,
        trimWhitespace: Boolean,
        sendCommand: (ByteArray) -> Unit,
        sendString: (String) -> Unit,
        paperWidthMm: Double,
        copies: Int
    ) {
        val pageCount = pdfRenderer.pageCount

        if (copies <= 1) {
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                val scale = alignedWidth.toFloat() / page.width.toFloat()
                val height = (page.height * scale).roundToInt()
                
                val bitmap = Bitmap.createBitmap(alignedWidth, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                var finalHeight = height
                if (trimWhitespace) {
                    finalHeight = getCroppedHeight(bitmap)
                }

                val pageBitmap = if (finalHeight != height) {
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, alignedWidth, finalHeight)
                    bitmap.recycle()
                    cropped
                } else {
                    bitmap
                }

                val heightMm = (finalHeight / (203 / 25.4)).roundToInt()
                
                sendString("SIZE $paperWidthMm mm, $heightMm mm\r\n")
                sendString("GAP 0,0\r\n")
                sendString("DIRECTION 0\r\n")
                sendString("CLS\r\n")

                val monoBytes = convertToMonochrome(pageBitmap, widthBytes, enableDithering)
                val header = "BITMAP 0,0,$widthBytes,$finalHeight,0,".toByteArray(Charsets.US_ASCII)
                
                sendCommand(header)
                sendCommand(monoBytes)
                sendString("\r\nPRINT 1,1\r\n")

                pageBitmap.recycle()
            }
        } else {
            class CachedPage(val sizeCmd: String, val header: ByteArray, val monoBytes: ByteArray)
            val cache = mutableListOf<CachedPage>()
            
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                val scale = alignedWidth.toFloat() / page.width.toFloat()
                val height = (page.height * scale).roundToInt()
                
                val bitmap = Bitmap.createBitmap(alignedWidth, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                var finalHeight = height
                if (trimWhitespace) {
                    finalHeight = getCroppedHeight(bitmap)
                }

                val pageBitmap = if (finalHeight != height) {
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, alignedWidth, finalHeight)
                    bitmap.recycle()
                    cropped
                } else {
                    bitmap
                }

                val heightMm = (finalHeight / (203 / 25.4)).roundToInt()
                val sizeCmd = "SIZE $paperWidthMm mm, $heightMm mm\r\nGAP 0,0\r\nDIRECTION 0\r\nCLS\r\n"
                
                val monoBytes = convertToMonochrome(pageBitmap, widthBytes, enableDithering)
                val header = "BITMAP 0,0,$widthBytes,$finalHeight,0,".toByteArray(Charsets.US_ASCII)
                
                cache.add(CachedPage(sizeCmd, header, monoBytes))
                pageBitmap.recycle()
            }
            
            for (c in 0 until copies) {
                for (cached in cache) {
                    sendString(cached.sizeCmd)
                    sendCommand(cached.header)
                    sendCommand(cached.monoBytes)
                    sendString("\r\nPRINT 1,1\r\n")
                }
            }
        }
    }

    private fun convertToMonochrome(bitmap: Bitmap, widthBytes: Int, dither: Boolean): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val outBytes = ByteArray(widthBytes * height)

        if (dither) {
            // Floyd-Steinberg Dithering
            val errors = FloatArray(width * height)
            for (y in 0 until height) {
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

    private fun getCroppedHeight(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != Color.WHITE) {
                    // Add a small 20 pixel padding so it's not flush with the last printed pixel
                    return kotlin.math.min(height, y + 1 + 20)
                }
            }
        }
        return height
    }
}
