package com.vetos26.galaxy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF

/** Tiny 5x7 retro bitmap font rendered with nearest-neighbour scaling. */
object PixelFont {
    const val GLYPH_W = 5
    const val GLYPH_H = 7

    enum class Align { LEFT, CENTER, RIGHT }

    private val glyphData: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".XXX.", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", ".XXX."),
        '1' to arrayOf("..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."),
        '2' to arrayOf(".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"),
        '3' to arrayOf("XXXXX", "...X.", "..X..", "...X.", "....X", "X...X", ".XXX."),
        '4' to arrayOf("...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."),
        '5' to arrayOf("XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."),
        '6' to arrayOf("..XX.", ".X...", "X....", "XXXX.", "X...X", "X...X", ".XXX."),
        '7' to arrayOf("XXXXX", "....X", "...X.", "..X..", ".X...", ".X...", ".X..."),
        '8' to arrayOf(".XXX.", "X...X", "X...X", ".XXX.", "X...X", "X...X", ".XXX."),
        '9' to arrayOf(".XXX.", "X...X", "X...X", ".XXXX", "....X", "...X.", ".XX.."),
        'A' to arrayOf(".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"),
        'B' to arrayOf("XXXX.", "X...X", "X...X", "XXXX.", "X...X", "X...X", "XXXX."),
        'C' to arrayOf(".XXX.", "X...X", "X....", "X....", "X....", "X...X", ".XXX."),
        'D' to arrayOf("XXXX.", "X...X", "X...X", "X...X", "X...X", "X...X", "XXXX."),
        'E' to arrayOf("XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"),
        'F' to arrayOf("XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "X...."),
        'G' to arrayOf(".XXX.", "X...X", "X....", "X.XXX", "X...X", "X...X", ".XXXX"),
        'H' to arrayOf("X...X", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"),
        'I' to arrayOf(".XXX.", "..X..", "..X..", "..X..", "..X..", "..X..", ".XXX."),
        'J' to arrayOf("..XXX", "...X.", "...X.", "...X.", "...X.", "X..X.", ".XX.."),
        'K' to arrayOf("X...X", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "X...X"),
        'L' to arrayOf("X....", "X....", "X....", "X....", "X....", "X....", "XXXXX"),
        'M' to arrayOf("X...X", "XX.XX", "X.X.X", "X.X.X", "X...X", "X...X", "X...X"),
        'N' to arrayOf("X...X", "X...X", "XX..X", "X.X.X", "X..XX", "X...X", "X...X"),
        'O' to arrayOf(".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
        'P' to arrayOf("XXXX.", "X...X", "X...X", "XXXX.", "X....", "X....", "X...."),
        'Q' to arrayOf(".XXX.", "X...X", "X...X", "X...X", "X.X.X", "X..X.", ".XX.X"),
        'R' to arrayOf("XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"),
        'S' to arrayOf(".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."),
        'T' to arrayOf("XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."),
        'U' to arrayOf("X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
        'V' to arrayOf("X...X", "X...X", "X...X", "X...X", "X...X", ".X.X.", "..X.."),
        'W' to arrayOf("X...X", "X...X", "X...X", "X.X.X", "X.X.X", "X.X.X", ".X.X."),
        'X' to arrayOf("X...X", "X...X", ".X.X.", "..X..", ".X.X.", "X...X", "X...X"),
        'Y' to arrayOf("X...X", "X...X", ".X.X.", "..X..", "..X..", "..X..", "..X.."),
        'Z' to arrayOf("XXXXX", "....X", "...X.", "..X..", ".X...", "X....", "XXXXX"),
        '-' to arrayOf(".....", ".....", ".....", "XXXXX", ".....", ".....", "....."),
        '.' to arrayOf(".....", ".....", ".....", ".....", ".....", ".XX..", ".XX.."),
        ',' to arrayOf(".....", ".....", ".....", ".....", ".XX..", "..X..", ".X..."),
        ':' to arrayOf(".....", ".XX..", ".XX..", ".....", ".XX..", ".XX..", "....."),
        '!' to arrayOf("..X..", "..X..", "..X..", "..X..", "..X..", ".....", "..X.."),
        '?' to arrayOf(".XXX.", "X...X", "....X", "...X.", "..X..", ".....", "..X.."),
        '/' to arrayOf("....X", "....X", "...X.", "..X..", ".X...", "X....", "X...."),
        '(' to arrayOf("...X.", "..X..", ".X...", ".X...", ".X...", "..X..", "...X."),
        ')' to arrayOf(".X...", "..X..", "...X.", "...X.", "...X.", "..X..", ".X..."),
        '<' to arrayOf("...X.", "..X..", ".X...", "X....", ".X...", "..X..", "...X."),
        '>' to arrayOf(".X...", "..X..", "...X.", "....X", "...X.", "..X..", ".X..."),
        '=' to arrayOf(".....", ".....", "XXXXX", ".....", "XXXXX", ".....", "....."),
        '%' to arrayOf("XX...", "XX..X", "...X.", "..X..", ".X...", "X..XX", "...XX"),
        '\'' to arrayOf("..X..", "..X..", ".X...", ".....", ".....", ".....", "....."),
    )

    private val glyphs = HashMap<Char, Bitmap>()
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val filters = HashMap<Int, PorterDuffColorFilter>()
    private val rect = RectF()

    init {
        for ((ch, rows) in glyphData) {
            val bmp = Bitmap.createBitmap(GLYPH_W, GLYPH_H, Bitmap.Config.ARGB_8888)
            for (y in 0 until GLYPH_H) for (x in 0 until GLYPH_W) {
                if (rows[y][x] == 'X') bmp.setPixel(x, y, Color.WHITE)
            }
            glyphs[ch] = bmp
        }
    }

    fun width(text: String, size: Float) = if (text.isEmpty()) 0f else (text.length * (GLYPH_W + 1) - 1) * size

    /** Draws [text]; [y] is the TOP of the text, [size] = virtual pixels per font pixel. */
    fun draw(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, align: Align = Align.LEFT, alpha: Int = 255) {
        val w = width(text, size)
        var cx = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2f
            Align.RIGHT -> x - w
        }
        paint.colorFilter = filters.getOrPut(color) { PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
        paint.alpha = alpha
        for (ch in text.uppercase()) {
            val g = glyphs[ch]
            if (g != null) {
                rect.set(cx, y, cx + GLYPH_W * size, y + GLYPH_H * size)
                c.drawBitmap(g, null, rect, paint)
            }
            cx += (GLYPH_W + 1) * size
        }
    }

    /** Text with a 1-pixel drop shadow, nicer on top of the starfield. */
    fun drawShadow(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, align: Align = Align.LEFT) {
        draw(c, text, x + size, y + size, size, Color.rgb(20, 20, 60), align)
        draw(c, text, x, y, size, color, align)
    }
}
