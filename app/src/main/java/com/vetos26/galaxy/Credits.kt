package com.vetos26.galaxy

import android.graphics.Canvas
import android.graphics.Color
import com.vetos26.galaxy.PixelFont.Align
import kotlin.math.min

/**
 * Credits text, shown on the loading screen and in the "Авторы" menu section.
 * Edit the lines here — both screens update automatically. Long lines are wrapped by words.
 */
object Credits {

    enum class Style { TITLE, LINE, THANKS }

    val lines = listOf(
        "Игра Galaxy" to Style.TITLE,
        "Автор: Виталий" to Style.LINE,
        "Разработка, дизайн звуки, игровая логика — Виталий" to Style.LINE,
        "Концепция и визуальный стиль — Виталий" to Style.LINE,
        "Спасибо за то, что играете!" to Style.THANKS,
    )

    private val orange = Color.rgb(248, 152, 40)
    private val yellow = Color.rgb(248, 216, 0)
    private const val MAX_WIDTH = Game.VW - 28f

    /**
     * Draws the credits block, centred horizontally, starting at [top].
     * [compact] = smaller spacing (loading screen). [alpha] 0..255 for fade-in.
     * @return y coordinate right below the block.
     */
    fun draw(c: Canvas, top: Float, compact: Boolean = false, alpha: Int = 255): Float {
        var y = top
        val cx = Game.VW / 2f
        for ((text, style) in lines) {
            when (style) {
                Style.TITLE -> {
                    val size = if (compact) 2.6f else 3f
                    shadow(c, text, cx, y, size, orange, alpha)
                    y += 7f * size + if (compact) 12f else 18f
                }
                Style.LINE -> {
                    val size = if (compact) 1.6f else 1.8f
                    for (line in PixelFont.wrap(text, size, MAX_WIDTH)) {
                        shadow(c, line, cx, y, size, Color.WHITE, alpha)
                        y += 8f * size + 3f
                    }
                    y += if (compact) 6f else 10f
                }
                Style.THANKS -> {
                    y += if (compact) 4f else 12f
                    val size = min(if (compact) 2f else 2.2f, MAX_WIDTH / PixelFont.width(text, 1f))
                    shadow(c, text, cx, y, size, yellow, alpha)
                    y += 8f * size + 4f
                }
            }
        }
        return y
    }

    private fun shadow(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, alpha: Int) {
        PixelFont.draw(c, text, x + size, y + size, size, Color.rgb(20, 20, 60), Align.CENTER, alpha)
        PixelFont.draw(c, text, x, y, size, color, Align.CENTER, alpha)
    }
}
