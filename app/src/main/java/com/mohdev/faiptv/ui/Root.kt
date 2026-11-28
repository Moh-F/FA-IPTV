package com.mohdev.faiptv.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.mohdev.faiptv.BuildConfig
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.LanServer
import com.mohdev.faiptv.core.Sync
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.UpdateInfo
import com.mohdev.faiptv.core.Updater
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.player.External
import com.mohdev.faiptv.player.PlayerScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

@Composable
fun FaRoot() {
    var ready by remember { mutableStateOf(false) }
    FaTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!ready) Splash { ready = true } else Main()
        }
    }
}

private class Step(val label: Int, val run: suspend () -> Unit)

/** App icon (with the version sash) + real progress bar + numeric percent. */
@Composable
fun Splash(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var label by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        val acc = Prefs.active
        val steps = listOf(
            Step(R.string.splash_accounts) { if (acc != null) Repo.loadCache(acc) },
            Step(R.string.splash_update) {
                if (Prefs.s.checkUpdates) withTimeoutOrNull(3500) { Updater.check(ctx) }?.let { Nav.update = it }
            },
            Step(R.string.splash_library) {
                if (acc != null) {
                    when {
                        Repo.lib == null -> withTimeoutOrNull(40_000) { Repo.refresh(acc) }
                        Repo.isStale() -> appScope.launch { Repo.refresh(acc) }
                        else -> Repo.startXmltv(acc)
                    }
                }
            },
            Step(R.string.splash_ready) { },
        )
        steps.forEachIndexed { i, s ->
            label = s.label
            runCatching { s.run() }.onFailure { AppLog.e("init", "splash step failed", it) }
            progress = (i + 1f) / steps.size
        }
        val left = 900 - (System.currentTimeMillis() - started)
        if (left > 0) delay(left)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Brand.Navy, Brand.Navy2))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.app_logo), null, Modifier.size(150.dp).clip(RoundedCornerShape(34.dp)))
            Spacer(Modifier.height(18.dp))
            Text("FA IPTV", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Brand.Cyan, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            if (label != 0) Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
        }
        Text(
            "v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
fun Main() {
    val ctx = LocalContext.current
    val syncLan = Sync.enabled && Prefs.s.syncLan
    val lan = Prefs.s.lanServer || syncLan
    LaunchedEffect(lan, syncLan) {
        if (lan) LanServer.start(ctx) else if (LanServer.running) LanServer.stop()
        if (syncLan) Sync.startLan() else Sync.stopLan()
    }
    LaunchedEffect(Unit) {
        if (Prefs.profiles.size > 1 && Prefs.s.askProfile) Nav.pickProfile = true
        Sync.start()
    }

    // switch library when the active account changes
    val activeId = Prefs.active?.id
    LaunchedEffect(activeId) {
        val a = Prefs.active ?: run { Repo.clear(); return@LaunchedEffect }
        if (Repo.lib?.raw?.accountId != a.id && !Repo.loading) Repo.activate(a)
    }
    // periodic refresh while the app is open
    LaunchedEffect(Unit) {
        while (true) {
            delay(10 * 60_000L)
            val a = Prefs.active
            if (a != null && Repo.isStale() && !Repo.loading) Repo.refresh(a)
        }
    }
    Nav.toast?.let { t ->
        LaunchedEffect(t) { Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show(); Nav.toast = null }
    }

    val top = Nav.top
    val shellState = rememberSaveableStateHolder()
    BackHandler(enabled = top !is com.mohdev.faiptv.ui.Route.Player && (Nav.stack.isNotEmpty() || Nav.tab != Tab.HOME)) {
        if (!Nav.pop()) Nav.tab = Tab.HOME
    }

    when {
        top is Route.Player -> PlayerScreen(top.req)
        Nav.pickProfile -> {
            BackHandler { Nav.pickProfile = false }
            ProfilePicker { Nav.pickProfile = false; Nav.go(Tab.HOME) }
        }
        Prefs.accounts.isEmpty() && top !is Route.AccountEdit -> WelcomeScreen()
        // keeps category, scroll position etc. while the player is open
        else -> shellState.SaveableStateProvider("shell") { Shell(top) }
    }

    Nav.pinThen?.let { then -> PinDialog(onDismiss = { Nav.pinThen = null }, onOk = { Nav.pinThen = null; then() }) }
    Nav.askPlayer?.let { req ->
        ChoiceDialog(
            stringResource(R.string.set_player_default),
            listOf("internal" to stringResource(R.string.player_internal), "external" to stringResource(R.string.player_external)),
            null, onDismiss = { Nav.askPlayer = null },
        ) { v ->
            Nav.askPlayer = null
            if (v == "internal") Nav.openInternal(req) else External.open(ctx, req)
        }
    }
    Nav.update?.let { UpdateDialog(it) }
    // reminder fired while the app is open
    Nav.reminder?.let { r ->
        AlertDialog(
            onDismissRequest = { Nav.reminder = null },
            title = { Text(r.channel) },
            text = { Text(stringResource(R.string.rem_starts, r.title, fmtTime(r.at))) },
            confirmButton = { Button(onClick = { Nav.reminder = null; Nav.pendingPlay = r.accountId to r.liveId }) { Text(stringResource(R.string.rem_watch)) } },
            dismissButton = { TextButton(onClick = { Nav.reminder = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    // reminder / automatic start: switch account if needed, then play the channel
    val pending = Nav.pendingPlay
    LaunchedEffect(pending, Repo.lib) {
        val (accId, liveId) = pending ?: return@LaunchedEffect
        if (Prefs.active?.id != accId) { Prefs.update { it.copy(activeAccount = accId) }; return@LaunchedEffect }
        val lib = Repo.lib ?: return@LaunchedEffect
        if (lib.raw.accountId != accId) return@LaunchedEffect
        val ch = lib.liveById[liveId]
        val a = Prefs.active
        Nav.pendingPlay = null
        if (ch != null && a != null) { Nav.pickProfile = false; Nav.liveQueue = emptyList(); Nav.play(ctx, Repo.playLive(a, ch), forceInternal = true) }
    }
    if (Nav.showLog) LogDialog()
}

@Composable
private fun Shell(top: Route?) {
    var hasFocus by remember { mutableStateOf(false) }
    val railFocus = remember { FocusRequester() }
    // TV: if nothing is focused after a screen change (e.g. back from the player), focus the menu
    LaunchedEffect(top, Nav.tab) {
        if (UiEnv.isTv) {
            delay(450)
            if (!hasFocus) runCatching { railFocus.requestFocus() }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        val wide = UiEnv.isTv || Prefs.s.layout == "tv" || (Prefs.s.layout == "auto" && maxWidth >= 720.dp)
        val content: @Composable () -> Unit = {
            when (top) {
                is Route.MovieDetail -> MovieDetailScreen(top.movie, wide)
                is Route.SeriesDetail -> SeriesDetailScreen(top.series, wide)
                is Route.AccountEdit -> AccountEditScreen(top.account, top.type)
                is Route.KidsAllowed -> KidsAllowedScreen(top.profileId)
                is Route.Guide -> GuideScreen(top.group, wide)
                is Route.Sub -> TabContent(top.tab, wide)
                else -> TabContent(Nav.tab, wide)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().then(if (!UiEnv.isTv) Modifier.systemBarsPadding() else Modifier)) {
                Rail(railFocus)
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) { content() }
                BottomBar()
            }
        }
        if (Prefs.s.showRibbon) {
            VersionRibbon("v${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.BottomEnd))
        }
        val pos = Prefs.s.badgePos
        val align = when (pos) { "tl" -> Alignment.TopStart; "tr" -> Alignment.TopEnd; "bl" -> Alignment.BottomStart; else -> Alignment.BottomEnd }
        val bottomPad = if (pos.startsWith("b")) (if (wide) 0.dp else 80.dp) + (if (pos == "br" && Prefs.s.showRibbon) 36.dp else 0.dp) else 0.dp
        RefreshBadge(Modifier.align(align).padding(bottom = bottomPad).then(if (!UiEnv.isTv) Modifier.systemBarsPadding() else Modifier))
    }
}

@Composable
fun TabContent(tab: Tab, wide: Boolean) {
    when (tab) {
        Tab.HOME -> HomeScreen(wide)
        Tab.LIVE -> LiveScreen(wide)
        Tab.MOVIES -> MoviesScreen(wide)
        Tab.SERIES -> SeriesScreen(wide)
        Tab.SEARCH -> SearchScreen(wide)
        Tab.RECORDINGS -> RecordingsScreen()
        Tab.ACCOUNTS -> AccountsScreen()
        Tab.SETTINGS -> SettingsScreen()
        Tab.MORE -> MoreScreen()
    }
}

private data class NavItem(val tab: Tab, val icon: ImageVector, val label: Int)

private val railItems = listOf(
    NavItem(Tab.HOME, Icons.Default.Home, R.string.nav_home),
    NavItem(Tab.SEARCH, Icons.Default.Search, R.string.nav_search),
    NavItem(Tab.LIVE, Icons.Default.LiveTv, R.string.nav_live),
    NavItem(Tab.MOVIES, Icons.Default.Movie, R.string.nav_movies),
    NavItem(Tab.SERIES, Icons.Default.VideoLibrary, R.string.nav_series),
    NavItem(Tab.RECORDINGS, Icons.Default.FiberManualRecord, R.string.nav_recordings),
    NavItem(Tab.ACCOUNTS, Icons.Default.AccountCircle, R.string.nav_accounts),
    NavItem(Tab.SETTINGS, Icons.Default.Settings, R.string.nav_settings),
)

@Composable
private fun Rail(focus: FocusRequester) {
    val selIdx = railItems.indexOfFirst { it.tab == Nav.tab }.coerceAtLeast(0)
    Column(
        Modifier.fillMaxHeight().width(96.dp).background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FocusCard(onClick = { Nav.pickProfile = true }, shape = androidx.compose.foundation.shape.CircleShape, color = Color.Transparent) {
            Avatar(Prefs.profile, 44.dp)
        }
        Text(profileName(Prefs.profile), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        railItems.forEachIndexed { i, item ->
            val sel = Nav.tab == item.tab && Nav.stack.isEmpty()
            FocusCard(
                onClick = { openTab(item.tab) },
                modifier = Modifier.width(84.dp),
                selected = sel,
                color = Color.Transparent,
                focusRequester = if (i == selIdx) focus else null,
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(item.icon, null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(item.label), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Prefs.active?.let {
            Text(
                it.name, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Kids profile: settings and accounts need the parental PIN (if one is set). */
fun openTab(t: Tab, push: Boolean = false) {
    val go = { if (push) Nav.push(Route.Sub(t)) else Nav.go(t) }
    if (Prefs.isKids && Prefs.hasPin() && t in setOf(Tab.SETTINGS, Tab.ACCOUNTS, Tab.RECORDINGS)) Nav.guarded(true, go) else go()
}

@Composable
private fun BottomBar() {
    val items = listOf(
        NavItem(Tab.HOME, Icons.Default.Home, R.string.nav_home),
        NavItem(Tab.LIVE, Icons.Default.LiveTv, R.string.nav_live),
        NavItem(Tab.MOVIES, Icons.Default.Movie, R.string.nav_movies),
        NavItem(Tab.SERIES, Icons.Default.VideoLibrary, R.string.nav_series),
        NavItem(Tab.MORE, Icons.Default.MoreHoriz, R.string.nav_more),
    )
    NavigationBar(Modifier.navigationBarsPadding()) {
        items.forEach {
            NavigationBarItem(
                selected = Nav.tab == it.tab,
                onClick = { Nav.go(it.tab) },
                icon = { Icon(it.icon, null) },
                label = { Text(stringResource(it.label), maxLines = 1) },
            )
        }
    }
}

@Composable
fun MoreScreen() {
    val entries = listOf(
        NavItem(Tab.SEARCH, Icons.Default.Search, R.string.nav_search),
        NavItem(Tab.RECORDINGS, Icons.Default.FiberManualRecord, R.string.nav_recordings),
        NavItem(Tab.ACCOUNTS, Icons.Default.AccountCircle, R.string.nav_accounts),
        NavItem(Tab.SETTINGS, Icons.Default.Settings, R.string.nav_settings),
    )
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FocusCard(onClick = { Nav.pickProfile = true }, shape = androidx.compose.foundation.shape.CircleShape, color = Color.Transparent) {
                    Avatar(Prefs.profile, 56.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(profileName(Prefs.profile), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Prefs.active?.let { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        items(entries) { e ->
            FocusCard(onClick = { openTab(e.tab, push = true) }, Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(e.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(e.label), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = ctx.resources.configuration.locales[0].language
    val forced = info.forced(BuildConfig.VERSION_CODE)
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<File?>(null) }

    fun start() {
        scope.launch {
            try {
                progress = 0f
                val f = Updater.download(ctx, info) { progress = it }
                file = f
                Updater.install(ctx, f)
            } catch (e: Exception) {
                AppLog.e("update", "download failed", e)
                error = e.message
                progress = null
            }
        }
    }
    LaunchedEffect(Unit) { if (Prefs.s.updateMode == "auto" && !forced) start() }

    AlertDialog(
        onDismissRequest = { if (!forced) { Updater.snooze(ctx, info); Nav.update = null } },
        properties = DialogProperties(dismissOnBackPress = !forced, dismissOnClickOutside = false),
        icon = { Image(painterResource(R.drawable.app_logo_plain), null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) },
        title = { Text(stringResource(R.string.update_title, info.version)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(info.notes[lang] ?: info.notes["en"] ?: "")
                progress?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.update_downloading), fontSize = 13.sp)
                    LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { file?.let { Updater.install(ctx, it) } ?: start() }, enabled = progress == null || file != null) {
                Text(stringResource(R.string.update_install))
            }
        },
        dismissButton = {
            if (!forced) Row {
                TextButton(onClick = { Updater.skip(ctx, info); Nav.update = null }) { Text(stringResource(R.string.update_skip)) }
                TextButton(onClick = { Updater.snooze(ctx, info); Nav.update = null }) { Text(stringResource(R.string.update_later)) }
            }
        },
    )
}

@Composable
fun LogDialog() {
    val lines = remember {
        AppLog.recent().takeLast(150).map {
            runCatching {
                val o = JSONObject(it)
                "${o.getString("t").substring(11, 19)} [${o.getString("l")}] ${o.getString("tag")}: ${o.getString("m")}"
            }.getOrDefault(it)
        }.reversed()
    }
    AlertDialog(
        onDismissRequest = { Nav.showLog = false },
        title = { Text(stringResource(R.string.set_log)) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(lines) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { Nav.showLog = false }) { Text(stringResource(R.string.action_close)) } },
    )
}
