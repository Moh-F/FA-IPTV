package com.mohdev.faiptv

import android.Manifest
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.player.PlayerState
import com.mohdev.faiptv.player.enterPip
import com.mohdev.faiptv.ui.FaRoot
import com.mohdev.faiptv.ui.Nav
import com.mohdev.faiptv.ui.Route
import com.mohdev.faiptv.ui.UiEnv

class MainActivity : AppCompatActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mohdev.faiptv.core.Locales.repair()
        val pm = packageManager
        val ui = getSystemService(UiModeManager::class.java)
        UiEnv.isTv = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature("amazon.hardware.fire_tv")
        UiEnv.pipSupported = Build.VERSION.SDK_INT >= 26 && pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (UiEnv.isTv) setBarsHidden(true)
        handlePlayIntent(intent)
        setContent { FaRoot() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handlePlayIntent(intent)
    }

    /** Reminder / automatic start: play a channel. */
    private fun handlePlayIntent(i: android.content.Intent?) {
        val live = i?.getStringExtra(com.mohdev.faiptv.record.Reminders.EXTRA_LIVE) ?: return
        val acc = i.getStringExtra(com.mohdev.faiptv.record.Reminders.EXTRA_ACCOUNT) ?: return
        Nav.pendingPlay = acc to live
        i.removeExtra(com.mohdev.faiptv.record.Reminders.EXTRA_LIVE)
    }

    override fun onStart() {
        super.onStart()
        UiEnv.foreground = true
    }

    /** Hides status/navigation bars (TV always, phones while the player is open). */
    fun setBarsHidden(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else if (!UiEnv.isTv) c.show(WindowInsetsCompat.Type.systemBars())
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PlayerState.playing && Prefs.s.autoPip && UiEnv.pipSupported && !UiEnv.isTv) enterPip(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        UiEnv.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        UiEnv.foreground = false
        Prefs.flush()
        // PiP window closed by the user -> leave the player
        if (UiEnv.inPip || (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode)) {
            if (Nav.top is Route.Player) Nav.pop()
        }
    }
}
