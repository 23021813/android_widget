package com.carlauncher.bridge.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.util.Size
import androidx.core.graphics.scale
import androidx.core.graphics.toColor
import kotlin.experimental.or
import kotlin.math.sqrt

class BitmapHelper {
    fun toBlackAndWhiteBuffer(source: Bitmap): ByteArray {
        val width = source.width
        val height = source.height
        val byteWidth = (width + 7) / 8
        val buffer = ByteArray(byteWidth * height) { 0 }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = if (source.getPixel(x, y) == Color.BLACK) 1 else 0
                val byteIndex = y * byteWidth + x / 8
                buffer[byteIndex] = buffer[byteIndex] or (pixel shl (7 - x % 8)).toByte()
            }
        }

        return buffer
    }

    fun compressBitmap(source: Bitmap?, size: Size): Bitmap {
        if (source == null) {
            return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        }
        val scaledSource = source.scale(size.width, size.height, false)
        return ditherImage(scaledSource)
    }

    private fun ditherImage(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = false }
        canvas.drawFilter = PaintFlagsDrawFilter(Paint.FILTER_BITMAP_FLAG, 0)
        canvas.drawColor(Color.WHITE)
        paint.color = Color.BLACK

        val palette = getPalette(source)
        if (palette.size < 2) return source

        fun isValidPixel(x: Int, y: Int): Boolean {
            return x in 0 until output.width && y in 0 until output.height
        }

        for (x in 0 until output.width) {
            for (y in 0 until output.height) {
                val pixel = roundColor(source.getPixel(x, y).toColor())
                if (pixel == palette.last()) {
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                } else if (pixel != palette.first()) {
                    var greyNeighbor = false
                    var foregroundNeighbors = 0
                    for (i in -1..1) {
                        for (j in -1..1) {
                            if (!isValidPixel(x + i, y + j)) continue
                            val near = roundColor(source.getPixel(x + i, y + j).toColor())
                            if (near != palette.first() && near != palette.last()) {
                                greyNeighbor = true
                            }
                            if (palette.indexOf(pixel) <= palette.size / 2 && near == palette.last()) {
                                foregroundNeighbors++
                            }
                        }
                    }
                    if (foregroundNeighbors >= 2 || (x % 3 == 0 && y % 2 == 0 && greyNeighbor)) {
                        canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                    }
                }
            }
        }

        return output
    }

    private fun colorAvg(color: android.graphics.Color): Double {
        val r = color.red().toDouble()
        val g = color.green().toDouble()
        val b = color.blue().toDouble()
        val a = color.alpha().toDouble()
        return sqrt(r * r + g * g + b * b + a * a) / sqrt(4.0)
    }

    private fun roundColor(color: android.graphics.Color): android.graphics.Color {
        fun roundTo(value: Float): Float = when {
            value < 0.25f -> 0f
            value < 0.5f -> 0.4f
            value < 0.75f -> 0.7f
            else -> 1f
        }

        val rounded = android.graphics.Color.valueOf(
            roundTo(color.red()),
            roundTo(color.green()),
            roundTo(color.blue()),
            roundTo(color.alpha())
        )

        return if (rounded.alpha() < 0.1f) {
            Color.BLACK.toColor()
        } else {
            rounded
        }
    }

    private fun getPalette(bitmap: Bitmap): List<android.graphics.Color> {
        val colors = mutableListOf<android.graphics.Color>()
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                colors.add(roundColor(bitmap.getPixel(x, y).toColor()))
            }
        }
        return colors.distinct().sortedBy(::colorAvg)
    }
}
