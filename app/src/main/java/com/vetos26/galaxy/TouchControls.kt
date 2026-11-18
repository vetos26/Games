package com.vetos26.galaxy

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Multi-touch on-screen buttons: LEFT / RIGHT on the left side of the bottom panel,
 * big FIRE button on the right. Several fingers are supported at once, and a finger
 * can slide from one button to another. Hardware keys / gamepads work too.
 */
class TouchControls {
    // layout in virtual coordinates (set by Game.resize)
    var scale = 1f
    var offsetX = 0f
    var panelTop = 500f
    var splitLR = 104f
    var splitFire = 200f

    private var touchLeft = false
    private var touchRight = false
    private var touchFire = false
    private var keyLeft = false
    private var keyRight = false
    private var keyFire = false

    val left get() = touchLeft || keyLeft
    val right get() = touchRight || keyRight
    val fire get() = touchFire || keyFire

    private var tapPending = false
    private var pausePending = false
    var tapX = 0f
        private set
    var tapY = 0f
        private set

    private fun vx(px: Float) = (px - offsetX) / scale
    private fun vy(py: Float) = py / scale

    fun onTouch(e: MotionEvent) {
        val action = e.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            tapX = vx(e.getX(i))
            tapY = vy(e.getY(i))
            tapPending = true
        }
        var l = false
        var r = false
        var f = false
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until e.pointerCount) {
                if (action == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
                val x = vx(e.getX(i))
                val y = vy(e.getY(i))
                if (y < panelTop) continue
                when {
                    x < splitLR -> l = true
                    x < splitFire -> r = true
                    else -> f = true
                }
            }
        }
        touchLeft = l
        touchRight = r
        touchFire = f
    }

    fun onKey(code: Int, down: Boolean): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> keyLeft = down
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keyRight = down
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_W -> {
                keyFire = down
                if (down) {
                    tapX = -1000f
                    tapY = -1000f
                    tapPending = true
                }
            }
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE -> {
                if (down) pausePending = true
            }
            else -> return false
        }
        return true
    }

    fun consumeTap(): Boolean {
        val t = tapPending
        tapPending = false
        return t
    }

    fun consumePauseKey(): Boolean {
        val p = pausePending
        pausePending = false
        return p
    }

    fun reset() {
        touchLeft = false; touchRight = false; touchFire = false
        keyLeft = false; keyRight = false; keyFire = false
        tapPending = false; pausePending = false
    }
}
