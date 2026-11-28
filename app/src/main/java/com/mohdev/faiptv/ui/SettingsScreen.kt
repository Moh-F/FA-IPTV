package com.mohdev.faiptv.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import coil.imageLoader
import com.mohdev.faiptv.BuildConfig
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.AppSettings
import com.mohdev.faiptv.core.LanServer
import com.mohdev.faiptv.core.Sync
import androidx.compose.foundation.layout.size
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.Updater
import com.mohdev.faiptv.data.Net
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.player.External
import kotlinx.coroutines.launch
import java.io.File

private class Choice<T>(val title: String, val options: List<Pair<T, String>>, val selected: T?, val pick: (T) -> Unit)

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Prefs.s
    val acc = Prefs.active
    var choice by remember { mutableStateOf<Choice<Any>?>(null) }
    var textEdit by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }
    var pinStep by remember { mutableStateOf(0) } // 1 = verify old then create, 2 = create, 3 = verify then remove, 4 = verify then unlock all
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    @Suppress("UNCHECKED_CAST")
    fun <T> ask(title: String, options: List<Pair<T, String>>, selected: T?, pick: (T) -> Unit) {
        choice = Choice(title, options, selected, pick) as Choice<Any>
    }
    fun set(f: (AppSettings) -> AppSettings) = Prefs.update(f)

    val lang = com.mohdev.faiptv.core.Locales.current()
    val langs = listOf("" to stringResource(R.string.lang_system), "ar" to "العربية", "en" to "English", "de" to "Deutsch")
    val themes = listOf("system" to stringResource(R.string.theme_system), "dark" to stringResource(R.string.theme_dark), "light" to stringResource(R.string.theme_light))
    val layouts = listOf("auto" to stringResource(R.string.layout_auto), "tv" to stringResource(R.string.layout_tv), "phone" to stringResource(R.string.layout_phone))
    val players = listOf("internal" to stringResource(R.string.player_internal), "external" to stringResource(R.string.player_external), "ask" to stringResource(R.string.player_ask))
    val buffers = listOf("small" to stringResource(R.string.buffer_small), "normal" to stringResource(R.string.buffer_normal), "large" to stringResource(R.string.buffer_large))
    val updModes = listOf("ask" to stringResource(R.string.update_ask), "auto" to stringResource(R.string.update_auto))
    val uaPresets = listOf(
        "" to "FA-IPTV (Standard)",
        "VLC/3.0.20 LibVLC/3.0.20" to "VLC",
        "IPTVSmartersPlayer" to "IPTV Smarters",
        "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36" to "Browser",
        "*custom" to stringResource(R.string.custom),
    )
    val none = stringResource(R.string.update_none)
    val lockedCount = acc?.let { Prefs.pd.locked[it.id]?.size } ?: 0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Text(stringResource(R.string.nav_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        item { Header(R.string.set_general) }
        item {
            Row_(stringResource(R.string.set_language), langs.firstOrNull { it.first == lang }?.second ?: langs[0].second) {
                ask(ctx.getString(R.string.set_language), langs, lang) { t ->
                    com.mohdev.faiptv.core.Locales.set(t)
                }
            }
        }
        item { Row_(stringResource(R.string.set_theme), themes.first { it.first == s.theme }.second) { ask(ctx.getString(R.string.set_theme), themes, s.theme) { v -> set { it.copy(theme = v) } } } }
        item {
            val pals = listOf("navy" to stringResource(R.string.palette_navy), "gold" to stringResource(R.string.palette_gold), "red" to stringResource(R.string.palette_red))
            Row_(stringResource(R.string.set_palette), pals.firstOrNull { it.first == s.palette }?.second ?: pals[0].second) {
                ask(ctx.getString(R.string.set_palette), pals, s.palette) { v -> set { it.copy(palette = v) } }
            }
        }
        item { Row_(stringResource(R.string.set_layout), layouts.first { it.first == s.layout }.second) { ask(ctx.getString(R.string.set_layout), layouts, s.layout) { v -> set { it.copy(layout = v) } } } }

        item { Header(R.string.set_profiles) }
        item { ProfilesManageRow() }
        item { Toggle(stringResource(R.string.set_ask_profile), s.askProfile) { v -> set { it.copy(askProfile = v) } } }

        item { Header(R.string.set_player) }
        item { Row_(stringResource(R.string.set_player_default), players.first { it.first == s.player }.second) { ask(ctx.getString(R.string.set_player_default), players, s.player) { v -> set { it.copy(player = v) } } } }
        item {
            val apps = remember { listOf("" to ctx.getString(R.string.ext_chooser)) + External.installed(ctx) }
            Row_(stringResource(R.string.set_external_app), apps.firstOrNull { it.first == s.externalPackage }?.second ?: s.externalPackage) {
                ask(ctx.getString(R.string.set_external_app), apps, s.externalPackage) { v -> set { it.copy(externalPackage = v) } }
            }
        }
        item { Row_(stringResource(R.string.set_live_format), s.liveFormat.uppercase()) { ask(ctx.getString(R.string.set_live_format), listOf("ts" to "MPEG-TS (.ts)", "m3u8" to "HLS (.m3u8)"), s.liveFormat) { v -> set { it.copy(liveFormat = v) } } } }
        item {
            val engines = listOf("auto" to stringResource(R.string.engine_auto), "exo" to "ExoPlayer (Media3)", "vlc" to "VLC")
            Row_(stringResource(R.string.set_engine), engines.first { it.first == s.engine }.second) { ask(ctx.getString(R.string.set_engine), engines, s.engine) { v -> set { it.copy(engine = v) } } }
        }
        item {
            val qs = listOf("auto" to stringResource(R.string.quality_auto), "max" to stringResource(R.string.quality_max), "2160" to "4K (2160p)", "1080" to "Full HD (1080p)", "720" to "HD (720p)", "480" to "SD (480p)", "360" to "360p")
            Row_(stringResource(R.string.set_quality), qs.first { it.first == s.quality }.second) { ask(ctx.getString(R.string.set_quality), qs, s.quality) { v -> set { it.copy(quality = v) } } }
        }
        item { Toggle(stringResource(R.string.set_fast_start), s.fastStart) { v -> set { it.copy(fastStart = v) } } }
        item { Toggle(stringResource(R.string.set_deinterlace), s.deinterlace) { v -> set { it.copy(deinterlace = v) } } }
        if (UiEnv.isTv) item { Toggle(stringResource(R.string.set_tunneling), s.tunneling) { v -> set { it.copy(tunneling = v) } } }
        item { Row_(stringResource(R.string.set_zap_delay), "${s.zapDelayMs} ms") { ask(ctx.getString(R.string.set_zap_delay), listOf(0, 200, 400, 700, 1000).map { it to "$it ms" }, s.zapDelayMs) { v -> set { it.copy(zapDelayMs = v) } } } }

        item { Header(R.string.set_infobar) }
        item { Row_(stringResource(R.string.set_info_seconds), if (s.infoSeconds == 0) stringResource(R.string.info_always) else "${s.infoSeconds} s") { ask(ctx.getString(R.string.set_info_seconds), listOf(3, 5, 8, 10, 15, 0).map { it to (if (it == 0) ctx.getString(R.string.info_always) else "$it s") }, s.infoSeconds) { v -> set { it.copy(infoSeconds = v) } } } }
        item { Row_(stringResource(R.string.set_info_opacity), "${s.infoOpacity}%") { ask(ctx.getString(R.string.set_info_opacity), listOf(30, 45, 60, 75, 90, 100).map { it to "$it%" }, s.infoOpacity) { v -> set { it.copy(infoOpacity = v) } } } }
        item {
            val sizes = listOf("s" to stringResource(R.string.size_s), "m" to stringResource(R.string.size_m), "l" to stringResource(R.string.size_l))
            Row_(stringResource(R.string.set_info_size), sizes.first { it.first == s.infoSize }.second) { ask(ctx.getString(R.string.set_info_size), sizes, s.infoSize) { v -> set { it.copy(infoSize = v) } } }
        }
        item { Toggle(stringResource(R.string.set_info_top), s.infoTop) { v -> set { it.copy(infoTop = v) } } }
        item { Toggle(stringResource(R.string.set_info_tech), s.infoTech) { v -> set { it.copy(infoTech = v) } } }
        item { Toggle(stringResource(R.string.set_show_speeds), s.showSpeeds) { v -> set { it.copy(showSpeeds = v) } } }
        item { Toggle(stringResource(R.string.set_show_clock), s.showClock) { v -> set { it.copy(showClock = v) } } }
        item { Toggle(stringResource(R.string.set_show_date), s.showDate) { v -> set { it.copy(showDate = v) } } }
        item { Toggle(stringResource(R.string.set_clock24), s.clock24) { v -> set { it.copy(clock24 = v) } } }
        item { Toggle(stringResource(R.string.set_clock_seconds), s.clockSeconds) { v -> set { it.copy(clockSeconds = v) } } }
        item {
            val now = System.currentTimeMillis()
            val fmts = listOf("short", "day", "long").map { f ->
                f to java.text.SimpleDateFormat(when (f) { "day" -> "EE, dd.MM."; "long" -> "EEEE, d. MMMM yyyy"; else -> "dd.MM.yyyy" }, com.mohdev.faiptv.core.Locales.num()).format(java.util.Date(now))
            }
            Row_(stringResource(R.string.set_date_format), fmts.first { it.first == s.dateFormat }.second) { ask(ctx.getString(R.string.set_date_format), fmts, s.dateFormat) { v -> set { it.copy(dateFormat = v) } } }
        }
        item { Row_(stringResource(R.string.set_overlay_opacity), "${s.overlayOpacity}%") { ask(ctx.getString(R.string.set_overlay_opacity), listOf(40, 55, 70, 85, 100).map { it to "$it%" }, s.overlayOpacity) { v -> set { it.copy(overlayOpacity = v) } } } }

        item { Header(R.string.set_player_more) }
        item { Toggle(stringResource(R.string.set_afr), s.afr) { v -> set { it.copy(afr = v) } } }
        if (s.afr) item {
            val opts = listOf(0 to stringResource(R.string.afr_none), 50 to "50 Hz", 60 to "60 Hz")
            Row_(stringResource(R.string.set_afr_fallback), opts.first { it.first == s.afrFallback }.second) { ask(ctx.getString(R.string.set_afr_fallback), opts, s.afrFallback) { v -> set { it.copy(afrFallback = v) } } }
        }
        item {
            val opts = listOf("normal" to stringResource(R.string.vodbuf_normal), "large" to stringResource(R.string.vodbuf_large), "max" to stringResource(R.string.vodbuf_max))
            Row_(stringResource(R.string.set_vod_buffer), opts.first { it.first == s.vodBuffer }.second) { ask(ctx.getString(R.string.set_vod_buffer), opts, s.vodBuffer) { v -> set { it.copy(vodBuffer = v) } } }
        }
        item { Toggle(stringResource(R.string.set_vod_minibar), s.vodMiniBar) { v -> set { it.copy(vodMiniBar = v) } } }
        item { Row_(stringResource(R.string.set_remind_min), "${s.remindMin} min") { ask(ctx.getString(R.string.set_remind_min), listOf(1, 2, 5, 10, 15, 30).map { it to "$it min" }, s.remindMin) { v -> set { it.copy(remindMin = v) } } } }
        item { Row_(stringResource(R.string.set_buffer), buffers.first { it.first == s.buffer }.second) { ask(ctx.getString(R.string.set_buffer), buffers, s.buffer) { v -> set { it.copy(buffer = v) } } } }
        if (UiEnv.pipSupported) item { Toggle(stringResource(R.string.set_auto_pip), s.autoPip) { v -> set { it.copy(autoPip = v) } } }
        item { Toggle(stringResource(R.string.set_auto_next), s.autoNext) { v -> set { it.copy(autoNext = v) } } }
        item {
            Row_(stringResource(R.string.set_user_agent), uaPresets.firstOrNull { it.first == s.userAgent }?.second ?: s.userAgent) {
                ask(ctx.getString(R.string.set_user_agent), uaPresets, s.userAgent) { v ->
                    if (v == "*custom") textEdit = Triple(ctx.getString(R.string.set_user_agent), s.userAgent.ifBlank { Net.defaultUserAgent }) { t -> set { it.copy(userAgent = t.trim()) } }
                    else set { it.copy(userAgent = v) }
                }
            }
        }
        item {
            val shifts = (-12..12).map { it * 30 }.map { it to (if (it > 0) "+$it" else "$it") + " min" }
            Row_(stringResource(R.string.set_epg_shift), "${s.epgShiftMin} min") { ask(ctx.getString(R.string.set_epg_shift), shifts, s.epgShiftMin) { v -> set { it.copy(epgShiftMin = v) } } }
        }

        item { Header(R.string.set_parental) }
        item {
            if (!Prefs.hasPin()) Row_(stringResource(R.string.pin_set), "") { pinStep = 2 }
            else Column {
                Row_(stringResource(R.string.pin_change), "••••") { pinStep = 1 }
                Row_(stringResource(R.string.pin_remove), "") { pinStep = 3 }
                if (Prefs.unlocked) Row_(stringResource(R.string.pin_lock_now), "") { Prefs.unlocked = false }
            }
        }
        item { Toggle(stringResource(R.string.set_auto_lock_adult), s.autoLockAdult) { v -> if (v) set { it.copy(autoLockAdult = true) } else Nav.guarded(true) { set { it.copy(autoLockAdult = false) } } } }
        item { Row_(stringResource(R.string.set_locked_count, lockedCount), stringResource(R.string.set_locked_hint)) { if (lockedCount > 0) pinStep = 4 } }

        item { Header(R.string.set_data) }
        item { Toggle(stringResource(R.string.set_badge), s.refreshBadge) { v -> set { it.copy(refreshBadge = v) } } }
        if (s.refreshBadge) {
            item {
                val opts = listOf("tl" to stringResource(R.string.pos_tl), "tr" to stringResource(R.string.pos_tr), "bl" to stringResource(R.string.pos_bl), "br" to stringResource(R.string.pos_br))
                Row_(stringResource(R.string.set_badge_pos), opts.first { it.first == s.badgePos }.second) { ask(ctx.getString(R.string.set_badge_pos), opts, s.badgePos) { v -> set { it.copy(badgePos = v) } } }
            }
            item {
                val opts = listOf("s" to stringResource(R.string.size_s), "m" to stringResource(R.string.size_m), "l" to stringResource(R.string.size_l))
                Row_(stringResource(R.string.set_info_size), opts.first { it.first == s.badgeSize }.second) { ask(ctx.getString(R.string.set_info_size), opts, s.badgeSize) { v -> set { it.copy(badgeSize = v) } } }
            }
            item {
                val opts = listOf("accent" to stringResource(R.string.color_accent), "white" to stringResource(R.string.color_white), "amber" to stringResource(R.string.color_amber), "green" to stringResource(R.string.color_green))
                Row_(stringResource(R.string.set_badge_color), opts.first { it.first == s.badgeColor }.second) { ask(ctx.getString(R.string.set_badge_color), opts, s.badgeColor) { v -> set { it.copy(badgeColor = v) } } }
            }
            item { Row_(stringResource(R.string.set_info_opacity), "${s.badgeOpacity}%") { ask(ctx.getString(R.string.set_info_opacity), listOf(40, 60, 85, 100).map { it to "$it%" }, s.badgeOpacity) { v -> set { it.copy(badgeOpacity = v) } } } }
        }
        item {
            val opts = listOf("auto" to stringResource(R.string.dns_auto), "system" to stringResource(R.string.dns_system), "cloudflare" to "Cloudflare (1.1.1.1)", "google" to "Google (8.8.8.8)", "quad9" to "Quad9 (9.9.9.9)")
            Row_(stringResource(R.string.set_dns), opts.first { it.first == s.dns }.second) { ask(ctx.getString(R.string.set_dns), opts, s.dns) { v -> set { it.copy(dns = v) } } }
        }
        item {
            val n = acc?.let { Prefs.custom(it.id).hidden.size } ?: 0
            Row_(stringResource(R.string.set_hidden, n), stringResource(R.string.set_unhide_all)) { if (n > 0) confirm = ctx.getString(R.string.set_unhide_all) to { acc?.let { Prefs.unhideAll(it.id) } } }
        }
        item { Row_(stringResource(R.string.set_refresh_hours), "${s.refreshHours} h") { ask(ctx.getString(R.string.set_refresh_hours), listOf(1, 3, 6, 12, 24).map { it to "$it h" }, s.refreshHours) { v -> set { it.copy(refreshHours = v) } } } }
        item {
            Row_(stringResource(R.string.set_clear_history), "") {
                confirm = ctx.getString(R.string.set_clear_history) to {
                    acc?.let { a -> Prefs.editPd { d -> d.copy(resume = d.resume.filterValues { it.accountId != a.id }, recentLive = d.recentLive - a.id) } }
                }
            }
        }
        item {
            Row_(stringResource(R.string.set_clear_cache), "") {
                confirm = ctx.getString(R.string.set_clear_cache) to {
                    File(ctx.filesDir, "cache").deleteRecursively()
                    ctx.imageLoader.memoryCache?.clear()
                    ctx.imageLoader.diskCache?.clear()
                    acc?.let { a -> appScope.launch { Repo.refresh(a) } }
                }
            }
        }

        item { Header(R.string.set_sync) }
        item { Text(stringResource(R.string.sync_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item {
            Row_(stringResource(R.string.sync_pass), if (s.syncPass.isBlank()) stringResource(R.string.sync_off) else "••••••••") {
                textEdit = Triple(ctx.getString(R.string.sync_pass_hint), s.syncPass) { t -> set { it.copy(syncPass = t.trim()) }; if (t.trim().length >= 8) Sync.start() }
            }
        }
        if (Sync.enabled) {
            item { Toggle(stringResource(R.string.sync_lan), s.syncLan) { v -> set { it.copy(syncLan = v) } } }
            if (s.syncLan) item {
                Text(
                    stringResource(R.string.sync_devices, Sync.peers.size) + Sync.peers.joinToString("") { "\n• " + it.name },
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item { Row_(stringResource(R.string.sync_gh_repo), s.ghRepo.ifBlank { "—" }) { textEdit = Triple(ctx.getString(R.string.sync_gh_repo), s.ghRepo.ifBlank { "Moh-F/FA-IPTV-sync" }) { t -> set { it.copy(ghRepo = t.trim()) } } } }
            item { Row_(stringResource(R.string.sync_gh_token), if (s.ghToken.isBlank()) "—" else "••••" + s.ghToken.takeLast(4)) { textEdit = Triple(ctx.getString(R.string.sync_gh_token), s.ghToken) { t -> set { it.copy(ghToken = t.trim()) } } } }
            item {
                val last = if (Sync.lastSync > 0) fmtDay(Sync.lastSync) + " " + fmtTime(Sync.lastSync) else "—"
                Row_(stringResource(R.string.sync_now), if (Sync.running) "…" else Sync.status.ifBlank { last }) { scope.launch { Sync.syncNow(manual = true) } }
            }
        }

        item { Header(R.string.set_updates) }
        item { Toggle(stringResource(R.string.set_check_updates), s.checkUpdates) { v -> set { it.copy(checkUpdates = v) } } }
        item { Row_(stringResource(R.string.set_update_mode), updModes.first { it.first == s.updateMode }.second) { ask(ctx.getString(R.string.set_update_mode), updModes, s.updateMode) { v -> set { it.copy(updateMode = v) } } } }
        item {
            Row_(stringResource(R.string.set_check_now), "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") {
                scope.launch { Updater.check(ctx, ignoreSnooze = true)?.let { Nav.update = it } ?: run { Nav.toast = none } }
            }
        }

        item { Header(R.string.set_advanced) }
        item { Toggle(stringResource(R.string.set_lan), s.lanServer) { v -> set { it.copy(lanServer = v) } } }
        if (s.lanServer && LanServer.url.isNotBlank()) item {
            QrCode(LanServer.url, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).size(160.dp))
        }
        if (s.lanServer && LanServer.url.isNotBlank()) item {
            Text(LanServer.url, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        item { Row_(stringResource(R.string.set_log), "") { Nav.showLog = true } }
        item {
            Row_(stringResource(R.string.set_share_log), "") {
                val text = AppLog.readAll().takeLast(200_000)
                ctx.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "FA IPTV log ${BuildConfig.VERSION_NAME}")
                            .putExtra(Intent.EXTRA_TEXT, AppLog.deviceInfo + "\n\n" + text),
                        null,
                    )
                )
            }
        }
        item { Toggle(stringResource(R.string.set_ribbon), s.showRibbon) { v -> set { it.copy(showRibbon = v) } } }

        item { Header(R.string.set_about) }
        item {
            Column(Modifier.padding(16.dp)) {
                Text("FA IPTV v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_text), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text(stringResource(R.string.privacy_text), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    choice?.let { c -> ChoiceDialog(c.title, c.options, c.selected, onDismiss = { choice = null }) { v -> choice = null; c.pick(v) } }
    textEdit?.let { (title, initial, save) ->
        var t by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { textEdit = null },
            title = { Text(title) },
            text = { OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { save(t); textEdit = null }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { textEdit = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    confirm?.let { (text, action) -> ConfirmDialog(text, onDismiss = { confirm = null }, onConfirm = action) }
    when (pinStep) {
        1 -> PinDialog(onDismiss = { pinStep = 0 }, onOk = { pinStep = 2 })
        2 -> PinDialog(onDismiss = { pinStep = 0 }, onOk = { pinStep = 0 }, forceCreate = true)
        3 -> PinDialog(onDismiss = { pinStep = 0 }, onOk = { Prefs.setPin(""); pinStep = 0 })
        4 -> PinDialog(onDismiss = { pinStep = 0 }, onOk = {
            acc?.let { a -> Prefs.editPd { d -> d.copy(locked = d.locked - a.id) } }
            pinStep = 0
        })
    }
}

@Composable
private fun Header(res: Int) {
    Text(
        stringResource(res), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun Row_(title: String, value: String, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(vertical = 2.dp), zoom = 1.01f) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            if (value.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp))
            }
        }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(onClick = { onChange(!checked) }, modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(vertical = 2.dp), zoom = 1.01f) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
