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
            Text("FA IPTV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Brand.Cyan, trackColor = Color(0x26FFFFFF),
            )
            Spacer(Modifier.height(10.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            if (label != 0) Text(stringResource(label), color = Color(0xFF94A3B8), fontSize = 13.sp, maxLines = 1)
        }
        Text(
            "v${BuildConfig.VERSION_NAME}", color = Color(0x88FFFFFF), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
fun Main() {
    val ctx = LocalContext.current
    val lan = Prefs.s.lanServer
    LaunchedEffect(lan) { if (lan) LanServer.start(ctx) else if (LanServer.running) LanServer.stop() }

    // switch library when the active account changes
    val activeId = Prefs.active?.id
    LaunchedEffect(activeId) {
        val a = Prefs.active ?: run { Repo.clear(); return@LaunchedEffect }
        if (Repo.lib?.raw?.accountId != a.id && !Repo.loading) Repo.activate(a)
    }
    // periodic refresh while the app is open
    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60_000L)
            val a = Prefs.active
            if (a != null && Repo.isStale() && !Repo.loading) Repo.refresh(a)
        }
    }
    Nav.toast?.let { t ->
        LaunchedEffect(t) { Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show(); Nav.toast = null }
    }

    val top = Nav.top
    BackHandler(enabled = top !is com.mohdev.faiptv.ui.Route.Player && (Nav.stack.isNotEmpty() || Nav.tab != Tab.HOME)) {
        if (!Nav.pop()) Nav.tab = Tab.HOME
    }

    when {
        top is Route.Player -> PlayerScreen(top.req)
        Prefs.accounts.isEmpty() && top !is Route.AccountEdit -> WelcomeScreen()
        else -> Shell(top)
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
    if (Nav.showLog) LogDialog()
}

@Composable
private fun Shell(top: Route?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = UiEnv.isTv || Prefs.s.layout == "tv" || (Prefs.s.layout == "auto" && maxWidth >= 720.dp)
        val content: @Composable () -> Unit = {
            when (top) {
                is Route.MovieDetail -> MovieDetailScreen(top.movie, wide)
                is Route.SeriesDetail -> SeriesDetailScreen(top.series, wide)
                is Route.AccountEdit -> AccountEditScreen(top.account, top.type)
                is Route.Sub -> TabContent(top.tab, wide)
                else -> TabContent(Nav.tab, wide)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().then(if (!UiEnv.isTv) Modifier.systemBarsPadding() else Modifier)) {
                Rail()
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
private fun Rail() {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (UiEnv.isTv) runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxHeight().width(96.dp).background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(painterResource(R.drawable.app_logo_plain), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(10.dp))
        railItems.forEachIndexed { i, it ->
            val sel = Nav.tab == it.tab && Nav.stack.isEmpty()
            FocusCard(
                onClick = { Nav.go(it.tab) },
                modifier = Modifier.width(84.dp),
                selected = sel,
                color = Color.Transparent,
                focusRequester = if (i == 0) first else null,
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(it.icon, null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(it.label), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                Image(painterResource(R.drawable.app_logo), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("FA IPTV", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Prefs.active?.let { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        items(entries) { e ->
            FocusCard(onClick = { Nav.push(Route.Sub(e.tab)) }, Modifier.fillMaxWidth()) {
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
