package com.vetos26.galaxy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView with its own render thread.
 * Logic runs with a fixed 60 Hz time step, drawing happens as fast as vsync allows.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val game = Game(context)
    private val lock = Any()

    private var thread: Thread? = null
    @Volatile private var running = false
    private var surfaceReady = false
    private var resumed = false

    private var surfaceW = 0
    private var surfaceH = 0
    private var insetTop = 0
    private var insetBottom = 0

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        setOnApplyWindowInsetsListener { _, insets ->
            var top = 0
            var bottom = 0
            if (Build.VERSION.SDK_INT >= 28) {
                insets.displayCutout?.let {
                    top = it.safeInsetTop
                    bottom = it.safeInsetBottom
                }
            }
            if (top != insetTop || bottom != insetBottom) {
                insetTop = top
                insetBottom = bottom
                applySize()
            }
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
        requestFocus()
    }

    private fun applySize() {
        if (surfaceW == 0 || surfaceH == 0) return
        synchronized(lock) { game.resize(surfaceW, surfaceH, insetTop, insetBottom) }
    }

    // ------------------------------------------------------------ lifecycle
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceW = width
        surfaceH = height
        applySize()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopThread()
    }

    fun resume() {
        resumed = true
        startThread()
    }

    fun pause() {
        resumed = false
        synchronized(lock) { game.onAppPaused() }
        stopThread()
    }

    fun release() {
        game.release()
    }

    fun onBack(): Boolean = synchronized(lock) { game.onBack() }

    private fun startThread() {
        if (running || !surfaceReady || !resumed) return
        running = true
        thread = Thread(this, "GalaxyLoop").also { it.start() }
    }

    private fun stopThread() {
        running = false
        val t = thread ?: return
        try {
            t.join(1000)
        } catch (ignored: InterruptedException) {
        }
        thread = null
    }

    // ------------------------------------------------------------ game loop
    override fun run() {
        val step = 1.0 / 60.0
        var last = System.nanoTime()
        var acc = 0.0
        while (running) {
            val now = System.nanoTime()
            var frame = (now - last) / 1_000_000_000.0
            last = now
            if (frame > 0.25) frame = 0.25
            acc += frame
            synchronized(lock) {
                while (acc >= step) {
                    game.update(step.toFloat())
                    acc -= step
                }
            }

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (canvas != null) {
                    synchronized(lock) { game.render(canvas) }
                }
            } catch (ignored: Exception) {
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (ignored: Exception) {
                    }
                }
            }
            if (canvas == null) {
                try {
                    Thread.sleep(16)
                } catch (ignored: InterruptedException) {
                }
            }
        }
    }

    // --------------------------------------------------------------- input
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(lock) { game.controls.onTouch(event) }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        val handled = synchronized(lock) { game.controls.onKey(keyCode, true) }
        return handled || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyUp(keyCode, event)
        val handled = synchronized(lock) { game.controls.onKey(keyCode, false) }
        return handled || super.onKeyUp(keyCode, event)
    }
}
