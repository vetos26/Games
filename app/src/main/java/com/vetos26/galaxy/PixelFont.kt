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
        '+' to arrayOf(".....", "..X..", "..X..", "XXXXX", "..X..", "..X..", "....."),
        '*' to arrayOf(".....", "X.X.X", ".XXX.", "XXXXX", ".XXX.", "X.X.X", "....."),
        // ---- Cyrillic (letters that look like Latin ones are aliased below)
        'Б' to arrayOf("XXXXX", "X....", "X....", "XXXX.", "X...X", "X...X", "XXXX."),
        'Г' to arrayOf("XXXXX", "X....", "X....", "X....", "X....", "X....", "X...."),
        'Д' to arrayOf("..XX.", ".X.X.", ".X.X.", ".X.X.", ".X.X.", "XXXXX", "X...X"),
        'Ж' to arrayOf("X.X.X", "X.X.X", ".XXX.", "..X..", ".XXX.", "X.X.X", "X.X.X"),
        'З' to arrayOf(".XXX.", "X...X", "....X", "..XX.", "....X", "X...X", ".XXX."),
        'И' to arrayOf("X...X", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", "X...X"),
        'Й' to arrayOf(".X.X.", "..X..", "X...X", "X..XX", "X.X.X", "XX..X", "X...X"),
        'Л' to arrayOf("..XXX", ".X..X", ".X..X", ".X..X", ".X..X", ".X..X", "X...X"),
        'П' to arrayOf("XXXXX", "X...X", "X...X", "X...X", "X...X", "X...X", "X...X"),
        'У' to arrayOf("X...X", "X...X", "X...X", ".XXXX", "....X", "X...X", ".XXX."),
        'Ф' to arrayOf("..X..", ".XXX.", "X.X.X", "X.X.X", "X.X.X", ".XXX.", "..X.."),
        'Ц' to arrayOf("X..X.", "X..X.", "X..X.", "X..X.", "X..X.", "XXXXX", "....X"),
        'Ч' to arrayOf("X...X", "X...X", "X...X", ".XXXX", "....X", "....X", "....X"),
        'Ш' to arrayOf("X.X.X", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "XXXXX"),
        'Щ' to arrayOf("X.X.X", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "XXXXX", "....X"),
        'Ъ' to arrayOf("XX...", ".X...", ".X...", ".XXX.", ".X..X", ".X..X", ".XXX."),
        'Ы' to arrayOf("X...X", "X...X", "X...X", "XXX.X", "X.X.X", "X.X.X", "XXX.X"),
        'Ь' to arrayOf("X....", "X....", "X....", "XXXX.", "X...X", "X...X", "XXXX."),
        'Э' to arrayOf(".XXX.", "X...X", "....X", "..XXX", "....X", "X...X", ".XXX."),
        'Ю' to arrayOf("X..X.", "X.X.X", "X.X.X", "XXX.X", "X.X.X", "X.X.X", "X..X."),
        'Я' to arrayOf(".XXXX", "X...X", "X...X", ".XXXX", "..X.X", ".X..X", "X...X"),
        // ---- lowercase (8 rows: the last one is for descenders)
        'a' to arrayOf(".....", ".....", ".XXX.", "....X", ".XXXX", "X...X", ".XXXX", "....."),
        'b' to arrayOf("X....", "X....", "XXXX.", "X...X", "X...X", "X...X", "XXXX.", "....."),
        'c' to arrayOf(".....", ".....", ".XXX.", "X....", "X....", "X....", ".XXX.", "....."),
        'd' to arrayOf("....X", "....X", ".XXXX", "X...X", "X...X", "X...X", ".XXXX", "....."),
        'e' to arrayOf(".....", ".....", ".XXX.", "X...X", "XXXXX", "X....", ".XXX.", "....."),
        'f' to arrayOf("..XX.", ".X...", "XXX..", ".X...", ".X...", ".X...", ".X...", "....."),
        'g' to arrayOf(".....", ".....", ".XXXX", "X...X", "X...X", ".XXXX", "....X", ".XXX."),
        'h' to arrayOf("X....", "X....", "XXXX.", "X...X", "X...X", "X...X", "X...X", "....."),
        'i' to arrayOf("..X..", ".....", ".XX..", "..X..", "..X..", "..X..", ".XXX.", "....."),
        'j' to arrayOf("...X.", ".....", "..XX.", "...X.", "...X.", "...X.", "X..X.", ".XX.."),
        'k' to arrayOf("X....", "X....", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "....."),
        'l' to arrayOf(".XX..", "..X..", "..X..", "..X..", "..X..", "..X..", ".XXX.", "....."),
        'm' to arrayOf(".....", ".....", "XX.X.", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "....."),
        'n' to arrayOf(".....", ".....", "XXXX.", "X...X", "X...X", "X...X", "X...X", "....."),
        'o' to arrayOf(".....", ".....", ".XXX.", "X...X", "X...X", "X...X", ".XXX.", "....."),
        'p' to arrayOf(".....", ".....", "XXXX.", "X...X", "X...X", "XXXX.", "X....", "X...."),
        'q' to arrayOf(".....", ".....", ".XXXX", "X...X", "X...X", ".XXXX", "....X", "....X"),
        'r' to arrayOf(".....", ".....", "X.XX.", "XX..X", "X....", "X....", "X....", "....."),
        's' to arrayOf(".....", ".....", ".XXXX", "X....", ".XXX.", "....X", "XXXX.", "....."),
        't' to arrayOf(".X...", ".X...", "XXX..", ".X...", ".X...", ".X..X", "..XX.", "....."),
        'u' to arrayOf(".....", ".....", "X...X", "X...X", "X...X", "X..XX", ".XX.X", "....."),
        'v' to arrayOf(".....", ".....", "X...X", "X...X", "X...X", ".X.X.", "..X..", "....."),
        'w' to arrayOf(".....", ".....", "X...X", "X.X.X", "X.X.X", "X.X.X", ".X.X.", "....."),
        'x' to arrayOf(".....", ".....", "X...X", ".X.X.", "..X..", ".X.X.", "X...X", "....."),
        'y' to arrayOf(".....", ".....", "X...X", "X...X", "X...X", ".XXXX", "....X", ".XXX."),
        'z' to arrayOf(".....", ".....", "XXXXX", "...X.", "..X..", ".X...", "XXXXX", "....."),
        'б' to arrayOf("...XX", ".XX..", "X....", "XXXX.", "X...X", "X...X", ".XXX.", "....."),
        'в' to arrayOf(".....", ".....", "XXXX.", "X...X", "XXXX.", "X...X", "XXXX.", "....."),
        'г' to arrayOf(".....", ".....", "XXXXX", "X....", "X....", "X....", "X....", "....."),
        'д' to arrayOf(".....", ".....", "..XX.", ".X.X.", ".X.X.", ".X.X.", "XXXXX", "X...X"),
        'ё' to arrayOf(".X.X.", ".....", ".XXX.", "X...X", "XXXXX", "X....", ".XXX.", "....."),
        'ж' to arrayOf(".....", ".....", "X.X.X", ".XXX.", "..X..", ".XXX.", "X.X.X", "....."),
        'з' to arrayOf(".....", ".....", "XXXX.", "....X", "..XX.", "....X", "XXXX.", "....."),
        'и' to arrayOf(".....", ".....", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", "....."),
        'й' to arrayOf(".X.X.", "..X..", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", "....."),
        'к' to arrayOf(".....", ".....", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "....."),
        'л' to arrayOf(".....", ".....", "..XXX", ".X..X", ".X..X", ".X..X", "X...X", "....."),
        'м' to arrayOf(".....", ".....", "X...X", "XX.XX", "X.X.X", "X...X", "X...X", "....."),
        'н' to arrayOf(".....", ".....", "X...X", "X...X", "XXXXX", "X...X", "X...X", "....."),
        'п' to arrayOf(".....", ".....", "XXXXX", "X...X", "X...X", "X...X", "X...X", "....."),
        'т' to arrayOf(".....", ".....", "XXXXX", "..X..", "..X..", "..X..", "..X..", "....."),
        'ф' to arrayOf(".....", "..X..", ".XXX.", "X.X.X", "X.X.X", ".XXX.", "..X..", "..X.."),
        'ц' to arrayOf(".....", ".....", "X..X.", "X..X.", "X..X.", "X..X.", "XXXXX", "....X"),
        'ч' to arrayOf(".....", ".....", "X...X", "X...X", ".XXXX", "....X", "....X", "....."),
        'ш' to arrayOf(".....", ".....", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "XXXXX", "....."),
        'щ' to arrayOf(".....", ".....", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "XXXXX", "....X"),
        'ъ' to arrayOf(".....", ".....", "XX...", ".X...", ".XXX.", ".X..X", ".XXX.", "....."),
        'ы' to arrayOf(".....", ".....", "X...X", "X...X", "XXX.X", "X.X.X", "XXX.X", "....."),
        'ь' to arrayOf(".....", ".....", "X....", "X....", "XXXX.", "X...X", "XXXX.", "....."),
        'э' to arrayOf(".....", ".....", ".XXX.", "....X", "..XXX", "....X", ".XXX.", "....."),
        'ю' to arrayOf(".....", ".....", "X..X.", "X.X.X", "XXX.X", "X.X.X", "X..X.", "....."),
        'я' to arrayOf(".....", ".....", ".XXXX", "X...X", ".XXXX", "..X.X", "X...X", "....."),
        '—' to arrayOf(".....", ".....", ".....", "XXXXX", ".....", ".....", "....."),
    )

    /** Cyrillic letters drawn with the identical Latin glyph. */
    private val aliases = mapOf(
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'Ё' to 'E', 'К' to 'K', 'М' to 'M', 'Н' to 'H',
        'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X', '–' to '-',
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x',
    )

    private val glyphs = HashMap<Char, Bitmap>()
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val filters = HashMap<Int, PorterDuffColorFilter>()
    private val rect = RectF()

    init {
        for ((ch, rows) in glyphData) {
            val bmp = Bitmap.createBitmap(GLYPH_W, rows.size, Bitmap.Config.ARGB_8888)
            for (y in rows.indices) for (x in 0 until GLYPH_W) {
                if (rows[y][x] == 'X') bmp.setPixel(x, y, Color.WHITE)
            }
            glyphs[ch] = bmp
        }
    }

    /** Exact glyph, Latin look-alike, or the uppercase variant if a lowercase glyph is missing. */
    private fun glyphFor(ch: Char): Bitmap? {
        glyphs[ch]?.let { return it }
        aliases[ch]?.let { a -> glyphs[a]?.let { return it } }
        val up = ch.uppercaseChar()
        if (up != ch) {
            glyphs[up]?.let { return it }
            aliases[up]?.let { a -> glyphs[a]?.let { return it } }
        }
        return null
    }

    /** Splits [text] into lines (by words) that fit into [maxWidth] at the given [size]. */
    fun wrap(text: String, size: Float, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        for (word in text.split(' ')) {
            if (word.isEmpty()) continue
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isNotEmpty() && width(candidate, size) > maxWidth) {
                out.add(cur)
                cur = word
            } else {
                cur = candidate
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
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
        for (ch in text) {
            val g = glyphFor(ch)
            if (g != null) {
                rect.set(cx, y, cx + GLYPH_W * size, y + g.height * size)
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
