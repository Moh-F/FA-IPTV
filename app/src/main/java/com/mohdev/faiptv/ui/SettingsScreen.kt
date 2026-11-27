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

    val lang = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')
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
    val lockedCount = acc?.let { Prefs.data.locked[it.id]?.count { k -> !k.endsWith(":open") } } ?: 0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Text(stringResource(R.string.nav_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        item { Header(R.string.set_general) }
        item {
            Row_(stringResource(R.string.set_language), langs.firstOrNull { it.first == lang }?.second ?: langs[0].second) {
                ask(ctx.getString(R.string.set_language), langs, lang) { t ->
                    AppCompatDelegate.setApplicationLocales(if (t.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(t))
                }
            }
        }
        item { Row_(stringResource(R.string.set_theme), themes.first { it.first == s.theme }.second) { ask(ctx.getString(R.string.set_theme), themes, s.theme) { v -> set { it.copy(theme = v) } } } }
        item { Row_(stringResource(R.string.set_layout), layouts.first { it.first == s.layout }.second) { ask(ctx.getString(R.string.set_layout), layouts, s.layout) { v -> set { it.copy(layout = v) } } } }

        item { Header(R.string.set_player) }
        item { Row_(stringResource(R.string.set_player_default), players.first { it.first == s.player }.second) { ask(ctx.getString(R.string.set_player_default), players, s.player) { v -> set { it.copy(player = v) } } } }
        item {
            val apps = remember { listOf("" to ctx.getString(R.string.ext_chooser)) + External.installed(ctx) }
            Row_(stringResource(R.string.set_external_app), apps.firstOrNull { it.first == s.externalPackage }?.second ?: s.externalPackage) {
                ask(ctx.getString(R.string.set_external_app), apps, s.externalPackage) { v -> set { it.copy(externalPackage = v) } }
            }
        }
        item { Row_(stringResource(R.string.set_live_format), s.liveFormat.uppercase()) { ask(ctx.getString(R.string.set_live_format), listOf("ts" to "MPEG-TS (.ts)", "m3u8" to "HLS (.m3u8)"), s.liveFormat) { v -> set { it.copy(liveFormat = v) } } } }
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
        item { Row_(stringResource(R.string.set_refresh_hours), "${s.refreshHours} h") { ask(ctx.getString(R.string.set_refresh_hours), listOf(3, 6, 12, 24, 48, 168).map { it to "$it h" }, s.refreshHours) { v -> set { it.copy(refreshHours = v) } } } }
        item {
            Row_(stringResource(R.string.set_clear_history), "") {
                confirm = ctx.getString(R.string.set_clear_history) to {
                    acc?.let { a -> Prefs.edit { d -> d.copy(resume = d.resume.filterValues { it.accountId != a.id }, recentLive = d.recentLive - a.id) } }
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
            acc?.let { a -> Prefs.edit { d -> d.copy(locked = d.locked - a.id) } }
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
