package com.vetos26.galaxy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.vetos26.galaxy.PixelFont.Align
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * GALAXY — a Galaxian-style shooter ("Galaxy" on the Dendy / NES).
 *
 * Everything is computed in a virtual coordinate system that is always [VW] units wide;
 * its height depends on the screen aspect ratio. Sprites are 32x32 PNG files.
 */
class Game(context: Context) {

    companion object {
        const val VW = 360f
        const val CONTROLS_H = 128f
        const val MIN_VH = 560f

        const val COL_SPACING = 36f
        const val ROW_SPACING = 30f

        const val PLAYER_SPEED_MIN = 60f
        const val PLAYER_SPEED_MAX = 170f
        const val PLAYER_ACCEL_TIME = 0.18f
        const val SWIPE_SENSITIVITY = 1.6f
        const val FIRE_BUFFER = 0.18f
        const val MISSILE_SPEED = 480f
        const val ARC_RADIUS = 28f
        const val ARC_SPEED = 4.2f
        const val LOADING_MIN_TIME = 2.6f

        val RED = Color.rgb(248, 56, 0)
        val YELLOW = Color.rgb(248, 216, 0)
        val CYAN = Color.rgb(60, 188, 252)
        val WHITE = Color.WHITE
        val GREY = Color.rgb(140, 140, 170)
    }

    enum class State { LOADING, MENU, READY, PLAYING, DYING, STAGE_CLEAR, GAME_OVER }

    val assets = Assets(context)
    private val sound = Sound(context)
    private val haptics = Haptics(context)
    private val settings = Settings(context)
    val controls = TouchControls()

    private val version: String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    private val menu = Menu(assets, settings, version, object : Menu.Actions {
        override fun newGame() = startGame()
        override fun resume() = resumeGame()
        override fun toMainMenu() = goToMainMenu()
        override fun settingsChanged() = applySettings()
        override fun click() {
            sound.play(sound.menu, 0.6f)
            haptics.pulse(12, 70)
        }
    })

    // ---------------------------------------------------------------- layout
    private var scale = 1f
    private var offsetX = 0f
    private var vh = 640f
    private var playTop = 0f
    private var playBottom = 512f
    private var screenBottom = 640f
    private var formTop = 60f

    private var btnSize = 84f
    private var fireSize = 96f
    private var leftCx = 54f
    private var rightCx = 150f
    private var fireCx = 290f
    private var btnCy = 576f

    // ----------------------------------------------------------------- state
    private var state = State.LOADING
    private var stateTime = 0f
    private var time = 0f
    private var paused = false
    private var firstReady = true

    private var score = 0
    private var lives = 3
    private var level = 1
    private var extraLifeGiven = false
    private var gameOverRank = 0

    private val player = Player()
    private var missile: Bullet? = null
    private val enemies = ArrayList<Enemy>()
    private val enemyBullets = ArrayList<Bullet>()
    private val explosions = ArrayList<Explosion>()
    private val popups = ArrayList<Popup>()
    private val stars = Starfield()

    private var formOffset = 0f
    private var formDir = 1f
    private var diveTimer = 2f

    // controls feel
    private var moveTime = 0f
    private var fireBuffer = 0f
    private var prevTouchL = false
    private var prevTouchR = false
    private var prevTouchF = false
    private var vibrationWasOn = true

    // ------------------------------------------------------------ rendering
    private val spritePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fillPaint = Paint()
    private val dst = RectF()

    init {
        sound.enabled = settings.sound
        haptics.enabled = settings.vibration
        vibrationWasOn = settings.vibration
    }

    // ===================================================================
    //  Layout & settings
    // ===================================================================
    fun resize(w: Int, h: Int, insetTop: Int, insetBottom: Int) {
        scale = min(w / VW, h / MIN_VH)
        offsetX = (w - VW * scale) / 2f
        vh = h / scale
        playTop = insetTop / scale
        screenBottom = vh - insetBottom / scale
        playBottom = screenBottom - CONTROLS_H
        formTop = playTop + 62f
        player.y = playBottom - 34f

        controls.scale = scale
        controls.offsetX = offsetX
        controls.panelTop = playBottom - 26f

        menu.top = playTop
        menu.bottom = screenBottom

        layoutControls()
        stars.init(VW, vh)
    }

    private fun layoutControls() {
        btnSize = when (settings.buttonSize) {
            0 -> 68f
            2 -> 100f
            else -> 84f
        }
        fireSize = btnSize + 12f
        val margin = 10f
        val gap = 6f
        if (!settings.leftHanded) {
            leftCx = margin + btnSize / 2f
            rightCx = leftCx + btnSize + gap
            fireCx = VW - margin - fireSize / 2f
        } else {
            fireCx = margin + fireSize / 2f
            rightCx = VW - margin - btnSize / 2f
            leftCx = rightCx - btnSize - gap
        }
        // a little above the very bottom edge -> fewer conflicts with system gestures
        btnCy = playBottom + CONTROLS_H / 2f - 2f
        controls.leftCx = leftCx
        controls.rightCx = rightCx
        controls.fireCx = fireCx
        controls.swipeMode = settings.swipeMode
    }

    private fun applySettings() {
        sound.enabled = settings.sound
        haptics.enabled = settings.vibration
        if (settings.vibration && !vibrationWasOn) haptics.pulse(60, 200)
        vibrationWasOn = settings.vibration
        layoutControls()
    }

    /** 0.8 (easy) / 1.0 (normal) / 1.25 (hard) */
    private fun difficultyFactor() = when (settings.difficulty) {
        0 -> 0.8f
        2 -> 1.25f
        else -> 1f
    }

    // ===================================================================
    //  Update
    // ===================================================================
    fun update(dt: Float) {
        time += dt
        stars.update(dt)

        if (state == State.LOADING) {
            updateLoading(dt)
            return
        }

        // one-shot input events of this frame
        val tap = controls.consumeTap()
        val select = controls.consumeSelect()
        val pauseKey = controls.consumePauseKey()
        val up = controls.consumeUp()
        val down = controls.consumeDown()
        val firePressed = controls.consumeFirePress()
        val drag = controls.consumeDrag()

        if (state == State.MENU) {
            stateTime += dt
            menu.update(dt, tap, controls.tapX, controls.tapY, up, down, select)
            return
        }
        if (paused) {
            if (pauseKey && menu.screen == Menu.Screen.PAUSE) resumeGame()
            else menu.update(dt, tap, controls.tapX, controls.tapY, up, down, select)
            return
        }
        if (pauseKey || (tap && isPauseButton(controls.tapX, controls.tapY))) {
            pause()
            return
        }

        updateButtonVibration()

        stateTime += dt
        when (state) {
            State.READY -> {
                updatePlayer(dt, false, drag)
                updateFormation(dt)
                updateEnemies(dt)
                updateEffects(dt)
                if (stateTime > (if (firstReady) 3.2f else 2f)) {
                    firstReady = false
                    setState(State.PLAYING)
                }
            }
            State.PLAYING -> {
                updatePlayer(dt, firePressed, drag)
                updateFormation(dt)
                updateDiveScheduler(dt)
                updateEnemies(dt)
                updateBullets(dt)
                updateEffects(dt)
                checkCollisions()
                if (state == State.PLAYING && enemies.none { it.alive }) {
                    sound.play(sound.clear)
                    setState(State.STAGE_CLEAR)
                }
            }
            State.DYING -> {
                updateFormation(dt)
                updateEnemies(dt)
                updateBullets(dt)
                updateEffects(dt)
                val divers = enemies.any { it.alive && it.state != EnemyState.FORMATION }
                if (stateTime > 2.6f && (!divers || stateTime > 6f)) {
                    lives--
                    if (lives <= 0) {
                        gameOverRank = settings.addResult(score, level)
                        settings.save()
                        setState(State.GAME_OVER)
                    } else {
                        respawn()
                        setState(State.READY)
                    }
                }
            }
            State.STAGE_CLEAR -> {
                updatePlayer(dt, false, drag)
                updateBullets(dt)
                updateEffects(dt)
                stars.speedFactor = 1f + min(stateTime, 2f) * 3f
                if (stateTime > 2.4f) {
                    stars.speedFactor = 1f
                    level++
                    spawnWave()
                    setState(State.READY)
                }
            }
            State.GAME_OVER -> {
                updateFormation(dt)
                updateEnemies(dt)
                updateEffects(dt)
                if (stateTime > 4.5f || ((tap || select) && stateTime > 1.5f)) {
                    // a result that made it into the top-10 -> show the table
                    goToMainMenu(if (gameOverRank > 0) Menu.Screen.SCORES else Menu.Screen.MAIN)
                }
            }
            State.LOADING, State.MENU -> Unit
        }
    }

    private fun setState(s: State) {
        state = s
        stateTime = 0f
    }

    private fun updateLoading(dt: Float) {
        stateTime += dt
        // load a couple of sprites per frame, the bar is also driven by time
        assets.loadNext()
        if (stateTime > 0.4f) assets.loadNext()
        controls.consumeTap()
        controls.consumeSelect()
        if (assets.done && stateTime >= LOADING_MIN_TIME) {
            menu.open(Menu.Screen.MAIN)
            setState(State.MENU)
        }
    }

    private fun loadingProgress() = min(assets.progress, stateTime / LOADING_MIN_TIME)

    private fun startGame() {
        score = 0
        lives = if (settings.difficulty == 0) 5 else 3
        level = 1
        extraLifeGiven = false
        firstReady = true
        paused = false
        gameOverRank = 0
        settings.lastRank = 0
        explosions.clear()
        popups.clear()
        spawnWave()
        respawn()
        controls.reset()
        sound.play(sound.start, 0.9f)
        setState(State.READY)
    }

    private fun respawn() {
        player.alive = true
        player.x = VW / 2f
        missile = null
        enemyBullets.clear()
        diveTimer = 2.5f
        moveTime = 0f
        fireBuffer = 0f
    }

    private fun goToMainMenu(screen: Menu.Screen = Menu.Screen.MAIN) {
        paused = false
        stars.speedFactor = 1f
        settings.save()
        sound.resumeAll()
        controls.reset()
        menu.open(screen)
        setState(State.MENU)
    }

    private fun spawnWave() {
        enemies.clear()
        for (c in intArrayOf(2, 5)) enemies.add(Enemy(EnemyType.FLAGSHIP, c, 0))
        for (c in 1..6) enemies.add(Enemy(EnemyType.RED, c, 1))
        for (c in 0..7) enemies.add(Enemy(EnemyType.PURPLE, c, 2))
        for (r in 3..4) for (c in 0..7) enemies.add(Enemy(EnemyType.BLUE, c, r))
        formOffset = 0f
        formDir = 1f
        for (e in enemies) {
            e.x = slotX(e.col)
            e.y = slotY(e.row)
        }
        enemyBullets.clear()
        missile = null
        diveTimer = 2.5f
    }

    private fun slotX(col: Int) = VW / 2f + (col - 3.5f) * COL_SPACING + formOffset
    private fun slotY(row: Int) = formTop + row * ROW_SPACING

    // ------------------------------------------------------------- player
    private fun updateButtonVibration() {
        val l = controls.touchLeftDown
        val r = controls.touchRightDown
        val f = controls.touchFireDown
        if ((l && !prevTouchL) || (r && !prevTouchR) || (f && !prevTouchF)) haptics.pulse(14, 80)
        prevTouchL = l
        prevTouchR = r
        prevTouchF = f
    }

    private fun updatePlayer(dt: Float, firePressed: Boolean, drag: Float) {
        if (!player.alive) return
        // smooth acceleration: a short tap moves the ship slowly -> precise aiming
        val dir = (if (controls.right) 1 else 0) - (if (controls.left) 1 else 0)
        moveTime = if (dir != 0) moveTime + dt else 0f
        val ramp = min(1f, moveTime / PLAYER_ACCEL_TIME)
        val speed = PLAYER_SPEED_MIN + (PLAYER_SPEED_MAX - PLAYER_SPEED_MIN) * ramp
        player.x += dir * speed * dt
        if (settings.swipeMode) player.x += drag * SWIPE_SENSITIVITY
        player.x = player.x.coerceIn(16f, VW - 16f)

        if (state != State.PLAYING) return
        // fire buffer: a press shortly before the missile is free is not lost
        if (firePressed) fireBuffer = FIRE_BUFFER
        if (fireBuffer > 0f) fireBuffer -= dt
        val wantFire = controls.fire || fireBuffer > 0f || settings.autoFire
        if (wantFire && missile == null) {
            missile = Bullet(player.x, player.y - 18f, 0f, -MISSILE_SPEED)
            fireBuffer = 0f
            sound.play(sound.shoot, 0.6f)
        }
    }

    // ----------------------------------------------------------- formation
    private fun updateFormation(dt: Float) {
        val speed = min(14f + level * 2f, 32f)
        formOffset += formDir * speed * dt
        var minC = 99
        var maxC = -1
        for (e in enemies) if (e.alive) {
            minC = min(minC, e.col)
            maxC = max(maxC, e.col)
        }
        if (maxC < 0) return
        val left = slotX(minC) - 16f
        val right = slotX(maxC) + 16f
        if (right > VW - 4f && formDir > 0) formDir = -1f
        if (left < 4f && formDir < 0) formDir = 1f
    }

    private fun updateDiveScheduler(dt: Float) {
        diveTimer -= dt
        if (diveTimer > 0f) return
        val inForm = enemies.count { it.alive && it.state == EnemyState.FORMATION }
        val interval = max(0.6f, 2.8f - level * 0.25f) / difficultyFactor()
        diveTimer = if (inForm <= 4) interval * 0.5f else interval * (0.7f + Random.nextFloat() * 0.6f)
        tryStartDive()
    }

    private fun tryStartDive() {
        val inForm = enemies.filter { it.alive && it.state == EnemyState.FORMATION }
        if (inForm.isEmpty()) return
        val diving = enemies.count { it.alive && it.state != EnemyState.FORMATION }
        val maxDivers = (2 + level / 2 + settings.difficulty - 1).coerceIn(1, 8)
        if (diving >= maxDivers) return

        val flags = inForm.filter { it.type == EnemyType.FLAGSHIP }
        if (flags.isNotEmpty() && Random.nextFloat() < 0.3f) {
            val f = flags.random()
            launch(f)
            val escorts = inForm.filter { it.type == EnemyType.RED && abs(it.col - f.col) <= 1 }.take(2)
            escorts.forEachIndexed { i, r ->
                r.state = EnemyState.ARC
                r.leader = f
                r.offX = r.x - f.x
                r.offY = r.y - f.y
                r.targetOffX = if (escorts.size == 1) (if (r.col < f.col) -26f else 26f) else (if (i == 0) -26f else 26f)
                r.targetOffY = -14f
                r.shotsLeft = 1 + min(2, level / 2)
                r.shotTimer = 0.2f + Random.nextFloat() * 0.3f
            }
            f.escortsStarted = escorts.size
            f.escortsKilled = 0
        } else {
            // aliens at the edges of the convoy peel off more often (like in the arcade)
            val minC = inForm.minOf { it.col }
            val maxC = inForm.maxOf { it.col }
            val edge = inForm.filter { it.col == minC || it.col == maxC }
            val candidates = if (edge.isNotEmpty() && Random.nextFloat() < 0.6f) edge else inForm
            val pick = candidates.filter { it.type != EnemyType.FLAGSHIP }.ifEmpty { candidates }.random()
            launch(pick)
        }
        sound.play(sound.dive, 0.5f)
    }

    private fun launch(e: Enemy) {
        e.state = EnemyState.ARC
        e.leader = null
        e.dir = if (e.x >= VW / 2f) 1f else -1f
        e.cx = e.x + e.dir * ARC_RADIUS
        e.cy = e.y
        e.angle = PI.toFloat()
        e.vx = 0f
        e.shotsLeft = 1 + min(3, level / 2) + (if (e.type == EnemyType.FLAGSHIP) 1 else 0)
        e.shotTimer = Random.nextFloat() * 0.3f
    }

    // ------------------------------------------------------------- enemies
    private fun updateEnemies(dt: Float) {
        val diveSpeed = min(120f + level * 9f, 210f) * difficultyFactor()
        val maxVx = min(90f + level * 6f, 150f)
        for (e in enemies) {
            if (!e.alive) continue
            val leader = e.leader
            if (leader != null && (!leader.alive || leader.state == EnemyState.RETURN || leader.state == EnemyState.FORMATION)) {
                // the flagship is gone -> continue alone
                e.leader = null
                e.state = EnemyState.DIVE
                e.vx = if (leader.alive) leader.vx else 0f
            }
            when (e.state) {
                EnemyState.FORMATION -> {
                    e.x = slotX(e.col)
                    e.y = slotY(e.row)
                    e.rotation = 0f
                }
                EnemyState.ARC, EnemyState.DIVE -> {
                    val l = e.leader
                    if (l != null) {
                        val k = min(1f, 3f * dt)
                        e.offX += (e.targetOffX - e.offX) * k
                        e.offY += (e.targetOffY - e.offY) * k
                        e.x = l.x + e.offX
                        e.y = l.y + e.offY
                        e.state = l.state
                        e.vx = l.vx
                        e.rotation = l.rotation
                    } else if (e.state == EnemyState.ARC) {
                        e.angle += ARC_SPEED * dt
                        if (e.angle >= 2f * PI.toFloat()) {
                            e.angle = 2f * PI.toFloat()
                            e.state = EnemyState.DIVE
                        }
                        e.x = e.cx + e.dir * ARC_RADIUS * cos(e.angle)
                        e.y = e.cy + ARC_RADIUS * sin(e.angle)
                        val hx = -e.dir * sin(e.angle)
                        val hy = cos(e.angle)
                        e.rotation = Math.toDegrees(atan2(hy, hx).toDouble()).toFloat() - 90f
                    } else {
                        val target = if (player.alive) player.x else VW / 2f
                        val accel = if (e.type == EnemyType.BLUE) 220f else 170f
                        e.vx += (if (target > e.x) 1f else -1f) * accel * dt
                        e.vx = e.vx.coerceIn(-maxVx, maxVx)
                        e.x = (e.x + e.vx * dt).coerceIn(12f, VW - 12f)
                        e.y += diveSpeed * dt
                        e.rotation = Math.toDegrees(atan2(diveSpeed, e.vx).toDouble()).toFloat() - 90f
                    }
                    if (e.state == EnemyState.DIVE) enemyShoot(e, dt)
                    if (e.y > playBottom + 24f) {
                        // fly through the bottom and come back from the top
                        e.state = EnemyState.RETURN
                        e.leader = null
                        e.y = playTop - 24f
                        e.x = slotX(e.col)
                        e.rotation = 0f
                    }
                }
                EnemyState.RETURN -> {
                    val tx = slotX(e.col)
                    val ty = slotY(e.row)
                    val dx = tx - e.x
                    val dy = ty - e.y
                    val dist = hypot(dx, dy)
                    val step = 150f * dt
                    if (dist <= step) {
                        e.x = tx
                        e.y = ty
                        e.state = EnemyState.FORMATION
                    } else {
                        e.x += dx / dist * step
                        e.y += dy / dist * step
                    }
                    e.rotation = 0f
                }
            }
        }
    }

    private fun enemyShoot(e: Enemy, dt: Float) {
        if (e.shotsLeft <= 0 || !player.alive || state != State.PLAYING) return
        if (e.y < formTop + 40f || e.y > player.y - 90f) return
        e.shotTimer -= dt
        if (e.shotTimer > 0f) return
        e.shotsLeft--
        e.shotTimer = 0.3f
        val vy = min(200f + level * 10f, 300f) * difficultyFactor()
        val t = (player.y - e.y) / vy
        val vx = ((player.x - e.x) / max(t, 0.3f) * 0.5f).coerceIn(-70f, 70f)
        enemyBullets.add(Bullet(e.x, e.y + 12f, vx, vy))
    }

    // ------------------------------------------------------------- bullets
    private fun updateBullets(dt: Float) {
        missile?.let {
            it.y += it.vy * dt
            if (it.y < playTop - 10f) missile = null
        }
        val iter = enemyBullets.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.y > playBottom + 10f) iter.remove()
        }
    }

    private fun updateEffects(dt: Float) {
        explosions.forEach { it.t += dt }
        explosions.removeAll { it.finished }
        popups.forEach { it.t += dt; it.y -= 12f * dt }
        popups.removeAll { it.t > 1.0f }
    }

    // ---------------------------------------------------------- collisions
    private fun checkCollisions() {
        val m = missile
        if (m != null) {
            for (e in enemies) {
                if (!e.alive) continue
                if (abs(m.x - e.x) < 13f && m.y - 4f < e.y + 11f && m.y + 4f > e.y - 11f) {
                    missile = null
                    killEnemy(e)
                    break
                }
            }
        }
        if (!player.alive) return
        for (b in enemyBullets) {
            if (abs(b.x - player.x) < 10f && abs(b.y - player.y) < 12f) {
                killPlayer()
                return
            }
        }
        for (e in enemies) {
            if (!e.alive || e.state == EnemyState.FORMATION || e.state == EnemyState.RETURN) continue
            if (abs(e.x - player.x) < 20f && abs(e.y - player.y) < 18f) {
                killEnemy(e)
                killPlayer()
                return
            }
        }
    }

    private fun killEnemy(e: Enemy) {
        e.alive = false
        explosions.add(Explosion(e.x, e.y, false))
        sound.play(sound.hit, 0.7f)
        val charging = e.state != EnemyState.FORMATION && e.state != EnemyState.RETURN
        val points = when {
            !charging -> e.type.convoyPoints
            e.type == EnemyType.FLAGSHIP -> {
                val escortsAlive = enemies.count { it.alive && it.leader === e }
                when {
                    e.escortsStarted == 2 && e.escortsKilled == 2 -> 800
                    escortsAlive == 2 -> 300
                    escortsAlive == 1 || e.escortsKilled == 1 -> 200
                    else -> 150
                }
            }
            else -> e.type.chargerPoints
        }
        e.leader?.let { it.escortsKilled++ }
        e.leader = null
        addScore(points)
        if (charging && points >= 100) popups.add(Popup(e.x, e.y - 6f, points.toString()))
    }

    private fun killPlayer() {
        player.alive = false
        missile = null
        explosions.add(Explosion(player.x, player.y, true))
        sound.play(sound.die, 1f)
        haptics.pulse(300, 255)
        setState(State.DYING)
    }

    private fun addScore(points: Int) {
        score += points
        if (!extraLifeGiven && score >= 7000) {
            extraLifeGiven = true
            lives++
            sound.play(sound.extra)
            haptics.pulse(80, 180)
        }
        settings.updateHiScore(score)
    }

    // ------------------------------------------------------- pause / back
    private fun pause() {
        if (state == State.LOADING || state == State.MENU || paused) return
        paused = true
        controls.reset()
        sound.stopAll()
        menu.open(Menu.Screen.PAUSE)
    }

    private fun resumeGame() {
        paused = false
        controls.reset()
        sound.resumeAll()
    }

    fun onAppPaused() {
        pause()
        settings.save()
    }

    /** @return true if the back press was consumed. */
    fun onBack(): Boolean {
        return when {
            state == State.LOADING -> false
            state == State.MENU || paused -> menu.back()
            else -> { pause(); true }
        }
    }

    fun release() {
        settings.save()
        sound.release()
    }

    private fun pauseButtonX() = VW - 20f
    private fun pauseButtonY() = playTop + 44f
    private fun isPauseButton(x: Float, y: Float) =
        abs(x - pauseButtonX()) < 26f && abs(y - pauseButtonY()) < 26f

    // ===================================================================
    //  Rendering
    // ===================================================================
    fun render(c: Canvas) {
        c.drawColor(Color.BLACK)
        c.save()
        c.translate(offsetX, 0f)
        c.scale(scale, scale)
        c.clipRect(0f, 0f, VW, vh)

        when (state) {
            State.LOADING -> drawLoading(c)
            State.MENU -> {
                stars.draw(c, 0f, vh)
                menu.draw(c)
            }
            else -> {
                stars.draw(c, playTop, playBottom)
                drawGame(c)
                drawControls(c)
                if (paused) {
                    fillPaint.color = Color.argb(185, 0, 0, 0)
                    c.drawRect(0f, 0f, VW, vh, fillPaint)
                    menu.draw(c)
                }
            }
        }
        c.restore()
    }

    private fun sprite(c: Canvas, bmp: Bitmap, x: Float, y: Float, size: Float = 32f, rotation: Float = 0f) {
        val h = size / 2f
        dst.set(x - h, y - h, x + h, y + h)
        if (rotation != 0f) {
            c.save()
            c.rotate(rotation, x, y)
            c.drawBitmap(bmp, null, dst, spritePaint)
            c.restore()
        } else {
            c.drawBitmap(bmp, null, dst, spritePaint)
        }
    }

    private fun blink(rate: Float = 2f) = ((time * rate).toInt() % 2) == 0

    // -------------------------------------------------------- loading screen
    private fun drawLoading(c: Canvas) {
        stars.draw(c, 0f, vh)
        val logo = assets.logo
        val cy = vh * 0.36f
        val lw = logo.width.toFloat()
        val lh = logo.height.toFloat()
        // logo slides in from above and fades in
        val appear = min(1f, stateTime / 0.6f)
        val ly = cy - lh / 2f - (1f - appear) * 60f
        dst.set(VW / 2f - lw / 2f, ly, VW / 2f + lw / 2f, ly + lh)
        spritePaint.alpha = (255 * appear).toInt()
        c.drawBitmap(logo, null, dst, spritePaint)
        spritePaint.alpha = 255

        PixelFont.drawShadow(c, "DENDY STYLE SPACE SHOOTER", VW / 2f, cy + lh / 2f + 14f, 1.4f, CYAN, Align.CENTER)

        // progress bar
        val p = loadingProgress()
        val bw = 220f
        val bx = VW / 2f - bw / 2f
        val by = vh * 0.62f
        fillPaint.color = Color.rgb(60, 70, 160)
        c.drawRect(bx - 3f, by - 3f, bx + bw + 3f, by + 13f, fillPaint)
        fillPaint.color = Color.BLACK
        c.drawRect(bx - 1f, by - 1f, bx + bw + 1f, by + 11f, fillPaint)
        val segs = 22
        val filled = (p * segs).toInt()
        for (i in 0 until filled) {
            fillPaint.color = if (i < segs / 2) YELLOW else if (i < segs * 5 / 6) Color.rgb(248, 120, 24) else RED
            val x = bx + i * (bw / segs)
            c.drawRect(x + 1f, by + 1f, x + bw / segs - 1f, by + 9f, fillPaint)
        }
        if (assets.progress > 0.05f) {
            // little ship flying above the bar
            sprite(c, assets["player"], bx + bw * p, by - 22f)
        }
        val dots = ".".repeat(((time * 3).toInt() % 4))
        PixelFont.drawShadow(c, "ЗАГРУЗКА$dots", bx, by + 22f, 2f, WHITE)
        PixelFont.drawShadow(c, "${(p * 100).toInt()}%", bx + bw, by + 22f, 2f, YELLOW, Align.RIGHT)

        PixelFont.draw(c, "(C) 2026 HOME STUDIO", VW / 2f, vh - 40f, 1.5f, GREY, Align.CENTER)
        PixelFont.draw(c, "MADE WITH KOTLIN", VW / 2f, vh - 24f, 1.5f, GREY, Align.CENTER)
    }

    // ------------------------------------------------------------- gameplay
    private fun drawGame(c: Canvas) {
        // enemies
        for (e in enemies) {
            if (!e.alive) continue
            val rate = if (e.state == EnemyState.FORMATION) 2.5f else 8f
            val frame = if (((time * rate).toInt() + e.col) % 2 == 0) 1 else 2
            sprite(c, assets["${e.type.sprite}_$frame"], e.x, e.y, rotation = e.rotation)
        }

        // player + the missile sitting on its nose (like in the arcade)
        if (player.alive) {
            val show = state != State.READY || stateTime > 0.8f
            if (show) {
                sprite(c, assets["player"], player.x, player.y)
                if (missile == null && (state == State.PLAYING || state == State.READY)) {
                    sprite(c, assets["bullet_player"], player.x, player.y - 15f)
                }
            }
        }
        missile?.let { sprite(c, assets["bullet_player"], it.x, it.y) }
        for (b in enemyBullets) sprite(c, assets["bullet_enemy"], b.x, b.y)

        for (ex in explosions) {
            val f = (ex.t / ex.frameTime).toInt().coerceIn(0, 3) + 1
            if (ex.big) sprite(c, assets["explosion_player_$f"], ex.x, ex.y, 56f)
            else sprite(c, assets["explosion_$f"], ex.x, ex.y)
        }
        for (p in popups) {
            PixelFont.drawShadow(c, p.text, p.x, p.y, 1.6f, CYAN, Align.CENTER)
        }

        drawHud(c)

        // reserve ships & level flags
        val iconY = playBottom - 8f
        for (i in 0 until min(lives - 1, 6)) sprite(c, assets["player"], 12f + i * 18f, iconY, 16f)
        val flags = min(level, 10)
        for (i in 0 until flags) sprite(c, assets["flag"], VW - 12f - i * 14f, iconY, 16f)

        // messages
        val my = playTop + (playBottom - playTop) * 0.55f
        when (state) {
            State.READY -> {
                if (firstReady) PixelFont.drawShadow(c, "PLAYER 1", VW / 2f, my - 26f, 2.4f, CYAN, Align.CENTER)
                else PixelFont.drawShadow(c, "STAGE $level", VW / 2f, my - 26f, 2.4f, CYAN, Align.CENTER)
                if (blink(3f)) PixelFont.drawShadow(c, "READY!", VW / 2f, my, 3f, YELLOW, Align.CENTER)
            }
            State.STAGE_CLEAR -> {
                PixelFont.drawShadow(c, "STAGE CLEAR!", VW / 2f, my - 20f, 3f, YELLOW, Align.CENTER)
                PixelFont.drawShadow(c, "WARP TO STAGE ${level + 1}", VW / 2f, my + 14f, 1.8f, CYAN, Align.CENTER)
            }
            State.GAME_OVER -> {
                PixelFont.drawShadow(c, "GAME OVER", VW / 2f, my, 3.4f, RED, Align.CENTER)
                val msg = when (gameOverRank) {
                    0 -> ""
                    1 -> "НОВЫЙ РЕКОРД!"
                    else -> "$gameOverRank МЕСТО В ТАБЛИЦЕ!"
                }
                if (msg.isNotEmpty() && blink(3f)) {
                    PixelFont.drawShadow(c, msg, VW / 2f, my + 36f, 2f, YELLOW, Align.CENTER)
                }
            }
            else -> Unit
        }
    }

    private fun drawHud(c: Canvas) {
        val y = playTop + 6f
        if (blink(2f) || state != State.PLAYING) {
            PixelFont.draw(c, "1UP", 14f, y, 1.8f, RED)
        }
        PixelFont.draw(c, score.toString().padStart(6, '0'), 14f, y + 16f, 1.8f, WHITE)
        PixelFont.draw(c, "HI-SCORE", VW / 2f, y, 1.8f, RED, Align.CENTER)
        PixelFont.draw(c, settings.hiScore.toString().padStart(6, '0'), VW / 2f, y + 16f, 1.8f, WHITE, Align.CENTER)
        sprite(c, assets["btn_pause"], pauseButtonX(), pauseButtonY(), 28f)
    }

    private fun drawControls(c: Canvas) {
        val top = playBottom
        fillPaint.color = Color.rgb(10, 10, 26)
        c.drawRect(0f, top, VW, vh, fillPaint)
        fillPaint.color = Color.rgb(48, 64, 160)
        c.drawRect(0f, top, VW, top + 2f, fillPaint)
        fillPaint.color = Color.rgb(24, 30, 80)
        c.drawRect(0f, top + 4f, VW, top + 5f, fillPaint)

        if (settings.swipeMode) {
            // swipe strip in place of the arrow buttons
            val x0 = min(leftCx, rightCx) - btnSize / 2f
            val x1 = max(leftCx, rightCx) + btnSize / 2f
            val h = btnSize * 0.42f
            val active = controls.dragging
            fillPaint.color = if (active) Color.rgb(90, 170, 255) else Color.rgb(48, 64, 160)
            c.drawRoundRect(x0, btnCy - h, x1, btnCy + h, 14f, 14f, fillPaint)
            fillPaint.color = if (active) Color.rgb(30, 50, 110) else Color.rgb(18, 22, 56)
            c.drawRoundRect(x0 + 3f, btnCy - h + 3f, x1 - 3f, btnCy + h - 3f, 12f, 12f, fillPaint)
            val col = if (active) YELLOW else WHITE
            PixelFont.draw(c, "<<", x0 + 12f, btnCy - 7f, 2f, col)
            PixelFont.draw(c, ">>", x1 - 12f, btnCy - 7f, 2f, col, Align.RIGHT)
            PixelFont.draw(c, "ВЕДИ", (x0 + x1) / 2f, btnCy - 10f, 1.4f, GREY, Align.CENTER)
            PixelFont.draw(c, "ПАЛЬЦЕМ", (x0 + x1) / 2f, btnCy + 2f, 1.4f, GREY, Align.CENTER)
        } else {
            sprite(c, assets[if (controls.left) "btn_left_on" else "btn_left"], leftCx, btnCy, btnSize)
            sprite(c, assets[if (controls.right) "btn_right_on" else "btn_right"], rightCx, btnCy, btnSize)
        }
        sprite(c, assets[if (controls.fire) "btn_fire_on" else "btn_fire"], fireCx, btnCy, fireSize)
        val label = if (settings.autoFire) "АВТО" else "ОГОНЬ"
        PixelFont.draw(c, label, fireCx, btnCy + fireSize / 2f - 4f, 1.2f, GREY, Align.CENTER)
    }
}
