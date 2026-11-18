package com.vetos26.galaxy

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

enum class EnemyType(val sprite: String, val convoyPoints: Int, val chargerPoints: Int) {
    FLAGSHIP("enemy_flagship", 60, 150),
    RED("enemy_red", 50, 100),
    PURPLE("enemy_purple", 40, 80),
    BLUE("enemy_blue", 30, 60),
}

enum class EnemyState { FORMATION, ARC, DIVE, RETURN }

class Enemy(val type: EnemyType, val col: Int, val row: Int) {
    var x = 0f
    var y = 0f
    var alive = true
    var state = EnemyState.FORMATION

    // loop ("peel off") parameters
    var angle = 0f
    var dir = 1f
    var cx = 0f
    var cy = 0f

    // dive parameters
    var vx = 0f
    var shotsLeft = 0
    var shotTimer = 0f
    var rotation = 0f

    // escorts (red aliens following a flagship)
    var leader: Enemy? = null
    var offX = 0f
    var offY = 0f
    var targetOffX = 0f
    var targetOffY = 0f
    var escortsStarted = 0
    var escortsKilled = 0
}

class Bullet(var x: Float, var y: Float, var vx: Float, var vy: Float)

class Explosion(val x: Float, val y: Float, val big: Boolean) {
    var t = 0f
    val frameTime get() = if (big) 0.16f else 0.075f
    val finished get() = t >= frameTime * 4
}

class Popup(val x: Float, var y: Float, val text: String) {
    var t = 0f
}

class Player {
    var x = 180f
    var y = 400f
    var alive = true
}

/** Classic Galaxian multi-coloured, blinking, scrolling starfield. */
class Starfield {
    private class Star(var x: Float, var y: Float, val speed: Float, val color: Int, val phase: Float, val rate: Float)

    private val colors = intArrayOf(
        Color.WHITE, Color.rgb(60, 188, 252), Color.rgb(248, 216, 0), Color.rgb(248, 120, 248),
        Color.rgb(120, 232, 96), Color.rgb(248, 56, 0), Color.rgb(180, 236, 252)
    )
    private val stars = ArrayList<Star>()
    private val paint = Paint()
    private var height = 0f
    private var time = 0f
    var speedFactor = 1f

    fun init(width: Float, h: Float) {
        if (stars.isNotEmpty() && h == height) return
        height = h
        stars.clear()
        repeat(90) {
            stars.add(
                Star(
                    Random.nextFloat() * width, Random.nextFloat() * h,
                    18f + Random.nextFloat() * 50f, colors[Random.nextInt(colors.size)],
                    Random.nextFloat() * 6.28f, 1.5f + Random.nextFloat() * 4f
                )
            )
        }
    }

    fun update(dt: Float) {
        time += dt
        for (s in stars) {
            s.y += s.speed * speedFactor * dt
            if (s.y > height) {
                s.y -= height
                s.x = Random.nextFloat() * Game.VW
            }
        }
    }

    fun draw(c: Canvas, top: Float, bottom: Float) {
        for (s in stars) {
            if (s.y < top || s.y > bottom) continue
            if (kotlin.math.sin(time * s.rate + s.phase) < -0.35f) continue
            paint.color = s.color
            c.drawRect(s.x, s.y, s.x + 1.4f, s.y + 1.4f, paint)
        }
    }
}
