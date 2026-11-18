package com.vetos26.galaxy

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Player settings, stored in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("galaxy", Context.MODE_PRIVATE)

    var sound = prefs.getBoolean("sound", true)
        set(v) { field = v; save() }
    var vibration = prefs.getBoolean("vibration", true)
        set(v) { field = v; save() }
    var autoFire = prefs.getBoolean("autoFire", false)
        set(v) { field = v; save() }
    /** false = arrow buttons, true = swipe strip */
    var swipeMode = prefs.getBoolean("swipeMode", false)
        set(v) { field = v; save() }
    /** true = FIRE on the left side (left-handed layout) */
    var leftHanded = prefs.getBoolean("leftHanded", false)
        set(v) { field = v; save() }
    /** 0 = small, 1 = medium, 2 = large */
    var buttonSize = prefs.getInt("buttonSize", 1)
        set(v) { field = v; save() }
    /** 0 = easy, 1 = normal, 2 = hard */
    var difficulty = prefs.getInt("difficulty", 1)
        set(v) { field = v; save() }

    var hiScore = prefs.getInt("hi", 5000)
        private set

    fun updateHiScore(score: Int) {
        if (score > hiScore) hiScore = score
    }

    fun resetHiScore() {
        hiScore = 5000
        save()
    }

    fun save() {
        prefs.edit()
            .putBoolean("sound", sound)
            .putBoolean("vibration", vibration)
            .putBoolean("autoFire", autoFire)
            .putBoolean("swipeMode", swipeMode)
            .putBoolean("leftHanded", leftHanded)
            .putInt("buttonSize", buttonSize)
            .putInt("difficulty", difficulty)
            .putInt("hi", hiScore)
            .apply()
    }
}

/** Short vibration pulses: feedback for touch buttons, explosions, etc. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    var enabled = true

    fun pulse(ms: Long, amplitude: Int = 120) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // some devices throw without VIBRATE permission or on emulators — ignore
        }
    }
}
