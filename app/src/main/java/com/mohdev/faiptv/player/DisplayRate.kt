package com.mohdev.faiptv.player

import android.app.Activity
import android.os.Build
import android.view.Display
import com.mohdev.faiptv.core.AppLog
import kotlin.math.abs
import kotlin.math.roundToInt

/** Switches the TV to a refresh rate that matches the video (e.g. 50 Hz for European channels) for smooth motion. */
object DisplayRate {
    fun match(a: Activity, fps: Float) {
        if (fps <= 1f) return
        runCatching {
            @Suppress("DEPRECATION")
            val d: Display = if (Build.VERSION.SDK_INT >= 30) a.display ?: return else a.windowManager.defaultDisplay
            val cur = d.mode
            val same = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            val best = same.minByOrNull { m -> err(m.refreshRate, fps) } ?: return
            if (err(best.refreshRate, fps) > 0.3f || best.modeId == cur.modeId) return
            val lp = a.window.attributes
            lp.preferredDisplayModeId = best.modeId
            a.window.attributes = lp
            AppLog.i("afr", "display ${cur.refreshRate} -> ${best.refreshRate} Hz for $fps fps")
        }.onFailure { AppLog.w("afr", "failed: ${it.message}") }
    }

    /** Distance from a whole multiple of the frame rate (prefers the lowest multiple). */
    private fun err(refresh: Float, fps: Float): Float {
        val k = (refresh / fps).roundToInt().coerceAtLeast(1)
        return abs(refresh - k * fps) + (k - 1) * 0.01f
    }

    fun reset(a: Activity) {
        runCatching {
            val lp = a.window.attributes
            if (lp.preferredDisplayModeId != 0) { lp.preferredDisplayModeId = 0; a.window.attributes = lp }
        }
    }
}
