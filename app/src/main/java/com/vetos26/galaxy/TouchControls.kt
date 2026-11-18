package com.vetos26.galaxy

import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Multi-touch on-screen controls in the bottom panel.
 *
 * Two modes:
 *  - BUTTONS: LEFT / RIGHT arrow buttons + FIRE button;
 *  - SWIPE:   a strip where the ship follows the finger movement + FIRE button.
 * A pointer is assigned to the nearest button (by x), so the layout can be mirrored
 * for left-handed players and resized freely. Keyboard / gamepad also supported.
 */
class TouchControls {
    // layout in virtual coordinates (set by Game.layoutControls)
    var scale = 1f
    var offsetX = 0f
    var panelTop = 500f
    var leftCx = 54f
    var rightCx = 150f
    var fireCx = 290f
    var swipeMode = false

    private var touchLeft = false
    private var touchRight = false
    private var touchFire = false
    private var keyLeft = false
    private var keyRight = false
    private var keyFire = false

    val left get() = touchLeft || keyLeft
    val right get() = touchRight || keyRight
    val fire get() = touchFire || keyFire

    /** Touch-only states, used for vibration feedback. */
    val touchLeftDown get() = touchLeft
    val touchRightDown get() = touchRight
    val touchFireDown get() = touchFire

    // swipe strip
    private var dragId = -1
    private var dragLastX = 0f
    private var dragAccum = 0f
    val dragging get() = dragId != -1

    // one-shot events
    private var tapPending = false
    private var selectPending = false
    private var pausePending = false
    private var upPending = false
    private var downPending = false
    private var firePressPending = false

    var tapX = 0f
        private set
    var tapY = 0f
        private set

    private fun vx(px: Float) = (px - offsetX) / scale
    private fun vy(py: Float) = py / scale

    private enum class Zone { LEFT, RIGHT, FIRE }

    private fun zoneOf(x: Float): Zone {
        val dl = abs(x - leftCx)
        val dr = abs(x - rightCx)
        val df = abs(x - fireCx)
        return when {
            df <= dl && df <= dr -> Zone.FIRE
            dl <= dr -> Zone.LEFT
            else -> Zone.RIGHT
        }
    }

    fun onTouch(e: MotionEvent) {
        val action = e.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            val x = vx(e.getX(i))
            val y = vy(e.getY(i))
            tapX = x
            tapY = y
            tapPending = true
            if (swipeMode && y >= panelTop && zoneOf(x) != Zone.FIRE) {
                dragId = e.getPointerId(i)
                dragLastX = x
            }
        }
        if (action == MotionEvent.ACTION_MOVE && dragId != -1) {
            val idx = e.findPointerIndex(dragId)
            if (idx >= 0) {
                val x = vx(e.getX(idx))
                dragAccum += x - dragLastX
                dragLastX = x
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragId = -1
        } else if (action == MotionEvent.ACTION_POINTER_UP && e.getPointerId(e.actionIndex) == dragId) {
            dragId = -1
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
                when (zoneOf(x)) {
                    Zone.FIRE -> f = true
                    Zone.LEFT -> if (!swipeMode) l = true
                    Zone.RIGHT -> if (!swipeMode) r = true
                }
            }
        }
        if (f && !touchFire) firePressPending = true
        touchLeft = l
        touchRight = r
        touchFire = f
    }

    fun onKey(code: Int, down: Boolean): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> keyLeft = down
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keyRight = down
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> if (down) upPending = true
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> if (down) downPending = true
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (down && !keyFire) {
                    selectPending = true
                    firePressPending = true
                }
                keyFire = down
            }
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE -> {
                if (down) pausePending = true
            }
            else -> return false
        }
        return true
    }

    fun consumeTap(): Boolean = tapPending.also { tapPending = false }
    fun consumeSelect(): Boolean = selectPending.also { selectPending = false }
    fun consumePauseKey(): Boolean = pausePending.also { pausePending = false }
    fun consumeUp(): Boolean = upPending.also { upPending = false }
    fun consumeDown(): Boolean = downPending.also { downPending = false }
    fun consumeFirePress(): Boolean = firePressPending.also { firePressPending = false }
    fun consumeDrag(): Float = dragAccum.also { dragAccum = 0f }

    fun reset() {
        touchLeft = false; touchRight = false; touchFire = false
        keyLeft = false; keyRight = false; keyFire = false
        tapPending = false; selectPending = false; pausePending = false
        upPending = false; downPending = false; firePressPending = false
        dragId = -1; dragAccum = 0f
    }
}
