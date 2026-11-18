package com.vetos26.galaxy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.vetos26.galaxy.PixelFont.Align
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Game menus: main menu (New game / Settings / Credits), settings, credits and the pause menu.
 * Works with touch (tap an item) and with keyboard / gamepad (up / down / fire).
 */
class Menu(
    private val assets: Assets,
    private val settings: Settings,
    private val version: String,
    private val actions: Actions,
) {
    interface Actions {
        fun newGame()
        fun resume()
        fun toMainMenu()
        fun settingsChanged()
        fun click()
    }

    enum class Screen { MAIN, SETTINGS, SCORES, CREDITS, PAUSE }

    var screen = Screen.MAIN
        private set
    private var returnTo = Screen.MAIN
    private var focus = 0
    private var screenTime = 0f
    private var confirmReset = false
    private var resetDoneTime = -1f
    private var time = 0f

    /** Usable area (virtual coordinates). */
    var top = 0f
    var bottom = 640f

    // ------------------------------------------------------------- texts
    private val mainItems = arrayOf("НОВАЯ ИГРА", "НАСТРОЙКИ", "ТАБЛИЦА ОЧКОВ", "АВТОРЫ")
    private val pauseItems = arrayOf("ПРОДОЛЖИТЬ", "НАСТРОЙКИ", "ГЛАВНОЕ МЕНЮ")
    private val settingsCount = 9

    /** Credits: first = text, second = style (0 - section title, 1 - name, 2 - big line). Edit freely. */
    private val credits = listOf(
        "ИГРА И ИДЕЯ" to 0, "VETOS26" to 1,
        "ПРОГРАММИРОВАНИЕ" to 0, "VETOS26 + ИИ-АССИСТЕНТ" to 1,
        "ГРАФИКА" to 0, "ПИКСЕЛЬ-АРТ 32X32 PNG" to 1,
        "ЗВУК" to 0, "8-БИТНЫЙ СИНТЕЗ" to 1,
        "ТЕХНОЛОГИИ" to 0, "KOTLIN + GITHUB ACTIONS" to 1,
        "ПО МОТИВАМ" to 0, "GALAXIAN (C) 1979 NAMCO" to 1, "И GALAXY ДЛЯ DENDY" to 1,
        "СПАСИБО ЗА ИГРУ!" to 2,
    )

    // ------------------------------------------------------------ colors
    private val yellow = Color.rgb(248, 216, 0)
    private val red = Color.rgb(248, 56, 0)
    private val cyan = Color.rgb(60, 188, 252)
    private val pink = Color.rgb(248, 120, 248)
    private val grey = Color.rgb(140, 140, 170)
    private val boxFill = Color.argb(225, 14, 16, 44)
    private val boxBorder = Color.rgb(48, 64, 160)

    private val spritePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint()
    private val dst = RectF()

    // ========================================================== control
    fun open(s: Screen) {
        if (s == Screen.SETTINGS && screen != Screen.SETTINGS) returnTo = screen
        screen = s
        focus = 0
        screenTime = 0f
        confirmReset = false
        resetDoneTime = -1f
    }

    /** @return true if consumed. */
    fun back(): Boolean {
        return when (screen) {
            Screen.MAIN -> false
            Screen.SETTINGS -> { actions.click(); open(returnTo); true }
            Screen.CREDITS, Screen.SCORES -> { actions.click(); open(Screen.MAIN); true }
            Screen.PAUSE -> { actions.resume(); true }
        }
    }

    fun update(dt: Float, tap: Boolean, tapX: Float, tapY: Float, up: Boolean, down: Boolean, select: Boolean) {
        time += dt
        screenTime += dt
        val n = itemCount()
        if (up) { focus = (focus - 1 + n) % n; actions.click() }
        if (down) { focus = (focus + 1) % n; actions.click() }
        if (tap && screenTime > 0.25f) {
            val i = hitTest(tapX, tapY)
            if (i >= 0) {
                focus = i
                activate(i)
                return
            }
        }
        if (select && screenTime > 0.15f) activate(focus)
    }

    private fun itemCount() = when (screen) {
        Screen.MAIN -> mainItems.size
        Screen.PAUSE -> pauseItems.size
        Screen.SETTINGS -> settingsCount
        Screen.CREDITS, Screen.SCORES -> 1
    }

    private fun activate(i: Int) {
        if (!(screen == Screen.SETTINGS && i == 7)) confirmReset = false
        when (screen) {
            Screen.MAIN -> when (i) {
                0 -> actions.newGame()
                1 -> { actions.click(); open(Screen.SETTINGS) }
                2 -> { actions.click(); open(Screen.SCORES) }
                3 -> { actions.click(); open(Screen.CREDITS) }
            }
            Screen.PAUSE -> when (i) {
                0 -> actions.resume()
                1 -> { actions.click(); open(Screen.SETTINGS) }
                2 -> { actions.click(); actions.toMainMenu() }
            }
            Screen.CREDITS, Screen.SCORES -> { actions.click(); open(Screen.MAIN) }
            Screen.SETTINGS -> {
                when (i) {
                    0 -> settings.sound = !settings.sound
                    1 -> settings.vibration = !settings.vibration
                    2 -> settings.autoFire = !settings.autoFire
                    3 -> settings.swipeMode = !settings.swipeMode
                    4 -> settings.leftHanded = !settings.leftHanded
                    5 -> settings.buttonSize = (settings.buttonSize + 1) % 3
                    6 -> settings.difficulty = (settings.difficulty + 1) % 3
                    7 -> if (confirmReset) {
                        settings.resetHiScore()
                        confirmReset = false
                        resetDoneTime = time
                    } else {
                        confirmReset = true
                    }
                    8 -> { actions.click(); open(returnTo); return }
                }
                actions.settingsChanged()
                actions.click()
            }
        }
    }

    // ---------------------------------------------------------- layout
    /** Main menu block (logo + buttons) is centred vertically on tall screens. */
    private fun mainBlockTop(): Float {
        val blockH = assets.logo.height + 46f + mainItems.size * 50f
        val free = (bottom - 30f) - (top + 52f) - blockH
        return top + 52f + max(0f, free) * 0.4f
    }

    private fun mainItemsTop() = mainBlockTop() + assets.logo.height + 46f
    private fun pauseItemsTop() = (top + bottom) / 2f - 40f
    private fun settingsTop() = top + 64f
    private fun settingsStep() = min(42f, (bottom - settingsTop() - 34f) / settingsCount)

    private fun itemCenterY(i: Int): Float = when (screen) {
        Screen.MAIN -> mainItemsTop() + i * 50f
        Screen.PAUSE -> pauseItemsTop() + i * 50f
        Screen.SETTINGS -> settingsTop() + i * settingsStep() + settingsStep() / 2f
        Screen.CREDITS, Screen.SCORES -> bottom - 40f
    }

    private fun itemWidth() = if (screen == Screen.SETTINGS) 332f else 240f
    private fun itemHeight() = if (screen == Screen.SETTINGS) settingsStep() - 6f else 40f

    private fun hitTest(x: Float, y: Float): Int {
        val w = itemWidth() / 2f
        val h = itemHeight() / 2f + 3f
        for (i in 0 until itemCount()) {
            if (abs(x - Game.VW / 2f) <= w && abs(y - itemCenterY(i)) <= h) return i
        }
        return -1
    }

    // ========================================================= drawing
    fun draw(c: Canvas) {
        when (screen) {
            Screen.MAIN -> drawMain(c)
            Screen.SETTINGS -> drawSettings(c)
            Screen.SCORES -> drawScores(c)
            Screen.CREDITS -> drawCredits(c)
            Screen.PAUSE -> drawPause(c)
        }
    }

    private fun sprite(c: Canvas, bmp: Bitmap, x: Float, y: Float, size: Float = 32f) {
        val h = size / 2f
        dst.set(x - h, y - h, x + h, y + h)
        c.drawBitmap(bmp, null, dst, spritePaint)
    }

    private fun blink(rate: Float) = ((time * rate).toInt() % 2) == 0

    private fun box(c: Canvas, cy: Float, w: Float, h: Float, selected: Boolean) {
        val x0 = Game.VW / 2f - w / 2f
        val x1 = Game.VW / 2f + w / 2f
        fill.color = if (selected) yellow else boxBorder
        c.drawRect(x0, cy - h / 2f, x1, cy + h / 2f, fill)
        fill.color = boxFill
        c.drawRect(x0 + 2f, cy - h / 2f + 2f, x1 - 2f, cy + h / 2f - 2f, fill)
        if (selected) {
            fill.color = Color.argb(60, 248, 216, 0)
            c.drawRect(x0 + 2f, cy - h / 2f + 2f, x1 - 2f, cy + h / 2f - 2f, fill)
        }
    }

    private fun drawButtons(c: Canvas, items: Array<String>) {
        val w = itemWidth()
        val h = itemHeight()
        val frame = if (blink(4f)) 1 else 2
        for (i in items.indices) {
            val cy = itemCenterY(i)
            val sel = i == focus
            box(c, cy, w, h, sel)
            PixelFont.draw(c, items[i], Game.VW / 2f, cy - 7f, 2.2f, if (sel) yellow else Color.WHITE, Align.CENTER)
            if (sel) {
                // flapping flagship cursors on both sides of the selected item
                sprite(c, assets["enemy_flagship_$frame"], Game.VW / 2f - w / 2f - 20f, cy, 28f)
                sprite(c, assets["enemy_flagship_$frame"], Game.VW / 2f + w / 2f + 20f, cy, 28f)
            }
        }
    }

    private fun drawHiScore(c: Canvas) {
        val y = top + 6f
        PixelFont.draw(c, "HI-SCORE", Game.VW / 2f, y, 1.8f, red, Align.CENTER)
        PixelFont.draw(c, settings.hiScore.toString().padStart(6, '0'), Game.VW / 2f, y + 16f, 1.8f, Color.WHITE, Align.CENTER)
    }

    private fun drawMain(c: Canvas) {
        drawHiScore(c)
        val logo = assets.logo
        val ly = mainBlockTop()
        dst.set(Game.VW / 2f - logo.width / 2f, ly, Game.VW / 2f + logo.width / 2f, ly + logo.height)
        c.drawBitmap(logo, null, dst, spritePaint)
        PixelFont.drawShadow(c, "DENDY STYLE SPACE SHOOTER", Game.VW / 2f, ly + logo.height + 10f, 1.4f, cyan, Align.CENTER)

        drawButtons(c, mainItems)

        PixelFont.draw(c, "(C) 2026 HOME STUDIO   V$version", Game.VW / 2f, bottom - 16f, 1.2f, grey, Align.CENTER)
    }

    // ------------------------------------------------------- score table
    private fun drawScores(c: Canvas) {
        PixelFont.drawShadow(c, "ТАБЛИЦА ОЧКОВ", Game.VW / 2f, top + 22f, 3.2f, yellow, Align.CENTER)

        // ---- best results
        val backTop = itemCenterY(0) - itemHeight() / 2f
        var y = top + 66f
        PixelFont.drawShadow(c, "- ЛУЧШИЕ РЕЗУЛЬТАТЫ -", Game.VW / 2f, y, 1.6f, pink, Align.CENTER)
        y += 18f
        val colRank = 44f
        val colScore = 150f
        val colStage = 204f
        val colDate = 330f
        PixelFont.draw(c, "МЕСТО", colRank - 14f, y, 1.2f, grey, Align.CENTER)
        PixelFont.draw(c, "ОЧКИ", colScore, y, 1.2f, grey, Align.RIGHT)
        PixelFont.draw(c, "ЭТАП", colStage, y, 1.2f, grey, Align.CENTER)
        PixelFont.draw(c, "ДАТА", colDate, y, 1.2f, grey, Align.RIGHT)
        y += 14f
        // alien table needs ~150 units, the rest is for the results
        val rowH = ((backTop - y - 160f) / Settings.MAX_SCORES).coerceIn(13f, 19f)
        val size = if (rowH >= 16f) 1.8f else 1.5f
        for (i in 0 until Settings.MAX_SCORES) {
            val e = settings.scores.getOrNull(i)
            val isLast = settings.lastRank == i + 1
            val col = when {
                isLast -> if (blink(4f)) yellow else Color.WHITE
                i == 0 -> yellow
                e == null -> Color.rgb(70, 70, 100)
                else -> Color.WHITE
            }
            if (isLast) {
                fill.color = Color.argb(70, 248, 216, 0)
                c.drawRect(14f, y - 2f, Game.VW - 14f, y + 7f * size + 2f, fill)
            }
            PixelFont.draw(c, "${i + 1}.", colRank, y, size, if (e == null) col else cyan, Align.RIGHT)
            if (e != null) {
                PixelFont.draw(c, e.score.toString().padStart(6, '0'), colScore, y, size, col, Align.RIGHT)
                PixelFont.draw(c, e.stage.toString(), colStage, y, size, col, Align.CENTER)
                PixelFont.draw(c, e.date, colDate, y, size, col, Align.RIGHT)
            } else {
                PixelFont.draw(c, "------", colScore, y, size, col, Align.RIGHT)
                PixelFont.draw(c, "-", colStage, y, size, col, Align.CENTER)
                PixelFont.draw(c, "--.--.--", colDate, y, size, col, Align.RIGHT)
            }
            y += rowH
        }
        if (settings.scores.isEmpty()) {
            PixelFont.draw(c, "СЫГРАЙТЕ, ЧТОБЫ ПОПАСТЬ В ТАБЛИЦУ!", Game.VW / 2f, y + 2f, 1.2f, grey, Align.CENTER)
        }

        // ---- points for aliens
        y += 20f
        PixelFont.drawShadow(c, "- ОЧКИ ЗА ПРИШЕЛЬЦЕВ -", Game.VW / 2f, y, 1.6f, pink, Align.CENTER)
        y += 16f
        PixelFont.draw(c, "В СТРОЮ", 190f, y, 1.4f, grey, Align.CENTER)
        PixelFont.draw(c, "В АТАКЕ", 290f, y, 1.4f, grey, Align.CENTER)
        val alienRow = ((backTop - 10f - y - 12f) / 4f).coerceIn(24f, 36f)
        y += 12f + alienRow / 2f
        val rows = arrayOf(
            Triple(EnemyType.FLAGSHIP, "60", "150-800"),
            Triple(EnemyType.RED, "50", "100"),
            Triple(EnemyType.PURPLE, "40", "80"),
            Triple(EnemyType.BLUE, "30", "60"),
        )
        val frame = if (blink(3f)) 1 else 2
        for ((type, convoy, charger) in rows) {
            sprite(c, assets["${type.sprite}_$frame"], 86f, y, min(32f, alienRow))
            PixelFont.draw(c, convoy, 190f, y - 6f, 1.8f, yellow, Align.CENTER)
            PixelFont.draw(c, charger, 290f, y - 6f, 1.8f, yellow, Align.CENTER)
            y += alienRow
        }

        drawButtons(c, arrayOf("НАЗАД"))
    }

    private fun onOff(v: Boolean) = if (v) "ВКЛ" else "ВЫКЛ"

    private fun drawSettings(c: Canvas) {
        PixelFont.drawShadow(c, "НАСТРОЙКИ", Game.VW / 2f, top + 22f, 3.2f, yellow, Align.CENTER)
        val rows = arrayOf(
            "ЗВУК" to onOff(settings.sound),
            "ВИБРАЦИЯ" to onOff(settings.vibration),
            "АВТООГОНЬ" to onOff(settings.autoFire),
            "УПРАВЛЕНИЕ" to (if (settings.swipeMode) "СВАЙП" else "КНОПКИ"),
            "КНОПКА ОГНЯ" to (if (settings.leftHanded) "СЛЕВА" else "СПРАВА"),
            "РАЗМЕР КНОПОК" to arrayOf("МАЛЫЕ", "СРЕДНИЕ", "БОЛЬШИЕ")[settings.buttonSize.coerceIn(0, 2)],
            "СЛОЖНОСТЬ" to arrayOf("ЛЕГКО", "НОРМА", "СЛОЖНО")[settings.difficulty.coerceIn(0, 2)],
            "СБРОС РЕКОРДОВ" to when {
                confirmReset -> if (blink(4f)) "ТОЧНО?" else ""
                resetDoneTime >= 0f && time - resetDoneTime < 1.5f -> "ГОТОВО"
                else -> settings.hiScore.toString()
            },
            "НАЗАД" to "",
        )
        val w = itemWidth()
        val h = itemHeight()
        val textSize = if (h >= 30f) 1.8f else 1.5f
        for (i in rows.indices) {
            val cy = itemCenterY(i)
            val sel = i == focus
            box(c, cy, w, h, sel)
            val (label, value) = rows[i]
            val ty = cy - 3.5f * textSize
            if (i == rows.size - 1) {
                PixelFont.draw(c, label, Game.VW / 2f, ty, textSize, if (sel) yellow else Color.WHITE, Align.CENTER)
            } else {
                PixelFont.draw(c, label, Game.VW / 2f - w / 2f + 10f, ty, textSize, if (sel) yellow else Color.WHITE)
                val valueColor = when {
                    i == 7 && confirmReset -> red
                    value == "ВЫКЛ" -> grey
                    else -> cyan
                }
                PixelFont.draw(c, value, Game.VW / 2f + w / 2f - 10f, ty, textSize, valueColor, Align.RIGHT)
            }
        }
        PixelFont.draw(c, "НАЖМИТЕ НА ПУНКТ, ЧТОБЫ ИЗМЕНИТЬ", Game.VW / 2f, bottom - 16f, 1.2f, grey, Align.CENTER)
    }

    private fun drawCredits(c: Canvas) {
        PixelFont.drawShadow(c, "АВТОРЫ", Game.VW / 2f, top + 22f, 3.2f, yellow, Align.CENTER)
        var y = top + 70f
        for ((text, style) in credits) {
            when (style) {
                0 -> {
                    y += 6f
                    PixelFont.draw(c, text, Game.VW / 2f, y, 1.5f, cyan, Align.CENTER)
                    y += 14f
                }
                1 -> {
                    PixelFont.drawShadow(c, text, Game.VW / 2f, y, 2f, Color.WHITE, Align.CENTER)
                    y += 18f
                }
                else -> {
                    y += 14f
                    PixelFont.drawShadow(c, text, Game.VW / 2f, y, 2.4f, yellow, Align.CENTER)
                    y += 22f
                }
            }
        }
        // parade of aliens chasing the ship
        val py = min(y + 34f, itemCenterY(0) - 44f)
        val frame = if (blink(6f)) 1 else 2
        val span = Game.VW + 64f
        val types = arrayOf("player", "enemy_blue_$frame", "enemy_purple_$frame", "enemy_red_$frame", "enemy_flagship_$frame")
        for (i in types.indices) {
            val x = ((time * 50f + (types.size - i) * 40f) % span) - 32f
            val wobble = if (i == 0) 0f else sin(time * 6f + i) * 4f
            sprite(c, assets[types[i]], x, py + wobble, if (i == 0) 28f else 26f)
        }
        drawButtons(c, arrayOf("НАЗАД"))
    }

    private fun drawPause(c: Canvas) {
        PixelFont.drawShadow(c, "ПАУЗА", Game.VW / 2f, pauseItemsTop() - 80f, 4f, yellow, Align.CENTER)
        drawButtons(c, pauseItems)
    }
}
