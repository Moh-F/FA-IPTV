package com.mohdev.faiptv.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import com.mohdev.faiptv.core.AppLog

/**
 * Night mode (loud parts softer, quiet parts louder) and volume levelling between channels,
 * with Android's DynamicsProcessing (Android 9+) on the player's audio session.
 */
class AudioFx {
    private var dp: Any? = null

    fun apply(session: Int, night: Boolean, level: Boolean) {
        release()
        if (session <= 0 || (!night && !level) || Build.VERSION.SDK_INT < 28) return
        runCatching {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0, false, 0, false, 0, true,
            ).build()
            val d = DynamicsProcessing(0, session, cfg)
            // threshold dB, ratio, post gain dB
            val (threshold, ratio, gain) = if (night) Triple(-28f, 6f, 10f) else Triple(-16f, 3f, 5f)
            d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 5f, 120f, ratio, threshold, gain))
            d.enabled = true
            dp = d
        }.onFailure { AppLog.w("audiofx", "not available: ${it.message}") }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= 28) runCatching { (dp as? DynamicsProcessing)?.release() }
        dp = null
    }
}
