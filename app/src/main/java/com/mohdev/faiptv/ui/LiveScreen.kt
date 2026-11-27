package com.mohdev.faiptv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohdev.faiptv.MainActivity
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.ScheduledRec
import com.mohdev.faiptv.data.Account
import com.mohdev.faiptv.data.Category
import com.mohdev.faiptv.data.Lib
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.Programme
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.player.External
import com.mohdev.faiptv.record.RecordService
import kotlinx.coroutines.delay
import java.util.UUID

const val CAT_ALL = "*all"
const val CAT_FAV = "*fav"
const val CAT_RECENT = "*recent"

/** Category ids of [kind] ("live" | "movie" | "series") that are locked right now. */
fun lockedCats(lib: Lib, acc: String, kind: String): Set<String> {
    if (Prefs.unlocked) return emptySet()
    val cats = when (kind) { "live" -> lib.raw.liveCats; "movie" -> lib.raw.movieCats; else -> lib.raw.seriesCats }
    return cats.filter { Prefs.isLocked(acc, kind, it.id, it.name) }.map { it.id }.toSet()
}

fun startRecording(ctx: Context, acc: Account, ch: Live) {
    (ctx as? MainActivity)?.askNotificationPermission()
    RecordService.startNow(ctx, Repo.liveUrl(acc, ch), ch.name, ch.userAgent.ifBlank { acc.userAgent }, 0L)
    Nav.toast = ctx.getString(R.string.rec_started, ch.name)
}

fun scheduleRecording(ctx: Context, acc: Account, ch: Live, p: Programme) {
    (ctx as? MainActivity)?.askNotificationPermission()
    RecordService.schedule(ctx, ScheduledRec(UUID.randomUUID().toString(), acc.id, ch.id, ch.name, p.title, p.start, p.end))
    Nav.toast = ctx.getString(R.string.rec_scheduled_ok)
}

@Composable
fun LiveScreen(wide: Boolean) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: run { if (Repo.loading) LoadingBox() else EmptyBox(stringResource(R.string.empty_list)); return }
    var cat by rememberSaveable { mutableStateOf(CAT_ALL) }
    var focused by remember { mutableStateOf<Live?>(null) }
    var menuFor by remember { mutableStateOf<Live?>(null) }
    var guideFor by remember { mutableStateOf<Live?>(null) }
    val favs = Prefs.data.favorites[acc.id].orEmpty()
    val recent = Prefs.data.recentLive[acc.id].orEmpty()
    val locked = remember(lib, Prefs.data.locked, Prefs.unlocked, Prefs.s.autoLockAdult) { lockedCats(lib, acc.id, "live") }

    val cats: List<Category> = remember(lib) {
        listOf(Category(CAT_FAV, ""), Category(CAT_RECENT, ""), Category(CAT_ALL, "")) + lib.raw.liveCats
    }
    val channels: List<Live> = remember(lib, cat, favs, recent, locked) {
        when (cat) {
            CAT_ALL -> lib.raw.live.filter { it.categoryId !in locked }
            CAT_FAV -> favs.filter { it.startsWith("live:") }.mapNotNull { lib.liveById[it.removePrefix("live:")] }
            CAT_RECENT -> recent.mapNotNull { lib.liveById[it] }
            else -> lib.liveByCat[cat].orEmpty()
        }
    }
    val catLabel: @Composable (Category) -> String = { c ->
        when (c.id) {
            CAT_ALL -> stringResource(R.string.cat_all) + " (${lib.raw.live.size})"
            CAT_FAV -> stringResource(R.string.cat_favorites)
            CAT_RECENT -> stringResource(R.string.cat_recent)
            else -> c.name + " (${lib.liveByCat[c.id]?.size ?: 0})"
        }
    }
    fun pick(c: Category) {
        Nav.guarded(c.id in locked) { cat = c.id }
    }
    fun play(ch: Live) {
        Nav.liveQueue = channels
        Nav.play(ctx, Repo.playLive(acc, ch))
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.width(250.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(cats, key = { it.id }) { c ->
                    FocusCard(
                        onClick = { pick(c) },
                        onLongClick = if (c.id.startsWith("*")) null else ({ Nav.guarded(c.id in locked) { Prefs.toggleLock(acc.id, "live:${c.id}") } }),
                        modifier = Modifier.fillMaxWidth(), selected = cat == c.id, color = Color.Transparent, zoom = 1f,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (c.id in locked || Prefs.isLockedManually(acc.id, "live:${c.id}")) {
                                Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = Brand.Amber); Spacer(Modifier.width(6.dp))
                            }
                            Text(catLabel(c), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                                color = if (cat == c.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            ChannelList(channels, Modifier.weight(1f), acc, onPlay = ::play, onMenu = { menuFor = it }, onFocus = { focused = it })
            Box(Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
                val ch = focused ?: channels.firstOrNull()
                if (ch != null) EpgPane(acc, ch, onPlayLive = { play(ch) })
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            val rowState = rememberLazyListState()
            LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats, key = { it.id }) { c -> Chip(catLabel(c), cat == c.id, { pick(c) }, locked = c.id in locked) }
            }
            ChannelList(channels, Modifier.weight(1f), acc, onPlay = ::play, onMenu = { menuFor = it }, onFocus = {})
        }
    }

    menuFor?.let { ch ->
        ChannelMenu(acc, ch, onDismiss = { menuFor = null }, onPlay = { menuFor = null; play(ch) }, onGuide = { menuFor = null; guideFor = ch })
    }
    guideFor?.let { ch ->
        Dialog(onDismissRequest = { guideFor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                EpgPane(acc, ch, onPlayLive = { guideFor = null; play(ch) })
            }
        }
    }
}

@Composable
private fun ChannelList(channels: List<Live>, modifier: Modifier, acc: Account, onPlay: (Live) -> Unit, onMenu: (Live) -> Unit, onFocus: (Live) -> Unit) {
    if (channels.isEmpty()) { Box(modifier.fillMaxHeight()) { EmptyBox(stringResource(R.string.empty_list)) }; return }
    @Suppress("UNUSED_VARIABLE") val epgTick = Repo.epgVersion
    LazyColumn(modifier.fillMaxHeight(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(channels, key = { it.id }) { ch ->
            val fav = Prefs.isFav(acc.id, "live:${ch.id}")
            FocusCard(
                onClick = { onPlay(ch) }, onLongClick = { onMenu(ch) }, onFocus = { onFocus(ch) },
                modifier = Modifier.fillMaxWidth(), zoom = 1.01f,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ch.num > 0) ch.num.toString() else "", Modifier.width(40.dp), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A2147))) {
                        NetImage(ch.logo, Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit, fallback = ch.name)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Repo.nowTitle(ch)?.let {
                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (ch.archiveDays > 0) Icon(Icons.Default.History, null, Modifier.size(18.dp), tint = Brand.Cyan)
                    if (fav) Icon(Icons.Default.Favorite, null, Modifier.padding(start = 6.dp).size(18.dp), tint = Brand.Pink)
                }
            }
        }
    }
}

/** Programme guide for one channel: now/next, catch-up for the past, record for the future. */
@Composable
fun EpgPane(acc: Account, ch: Live, onPlayLive: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember(ch.id) { mutableStateOf<List<Programme>?>(null) }
    var confirm by remember { mutableStateOf<Programme?>(null) }
    LaunchedEffect(ch.id) {
        delay(300)
        list = Repo.epg(acc, ch, full = true)
    }
    val now = System.currentTimeMillis()
    val listState = rememberLazyListState()
    LaunchedEffect(list) {
        val l = list ?: return@LaunchedEffect
        val i = l.indexOfFirst { it.isNow(now) }
        if (i > 0) listState.scrollToItem(i)
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 72.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A2147))) {
                NetImage(ch.logo, Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit, fallback = ch.name)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(ch.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (ch.archiveDays > 0) Text(stringResource(R.string.epg_archive_days, ch.archiveDays), fontSize = 12.sp, color = Brand.Cyan)
            }
        }
        Spacer(Modifier.height(10.dp))
        val l = list
        when {
            l == null -> LoadingBox(Modifier.height(120.dp))
            l.isEmpty() -> Text(stringResource(R.string.epg_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var lastDay = ""
                l.forEachIndexed { idx, p ->
                    val day = fmtDayShort(p.start)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "d$idx") { Text(day, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    }
                    item(key = "p$idx") {
                        val live = p.isNow(now)
                        val archived = p.isPast(now) && (p.hasArchive || (ch.archiveDays > 0 && p.start > now - ch.archiveDays * 86_400_000L))
                        val future = p.start > now
                        FocusCard(
                            onClick = {
                                when {
                                    live -> onPlayLive()
                                    archived -> { Nav.liveQueue = emptyList(); Nav.play(ctx, Repo.playCatchup(acc, ch, p)) }
                                    future -> confirm = p
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), selected = live, zoom = 1f,
                            color = if (p.isPast(now) && !archived) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${fmtTime(p.start)}–${fmtTime(p.end)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.weight(1f))
                                    when {
                                        live -> Badge(stringResource(R.string.epg_now), Brand.Live)
                                        archived -> Badge(stringResource(R.string.epg_catchup), Brand.Violet)
                                        future -> Badge("●REC", Color(0xFF475569))
                                    }
                                }
                                Text(p.title, fontWeight = if (live) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (live && p.desc.isNotBlank()) Text(p.desc, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { p ->
        ConfirmDialog(stringResource(R.string.action_record_program) + "\n\n" + "${fmtDayShort(p.start)} ${fmtTime(p.start)} · ${p.title}",
            onDismiss = { confirm = null }) { scheduleRecording(ctx, acc, ch, p) }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ChannelMenu(acc: Account, ch: Live, onDismiss: () -> Unit, onPlay: () -> Unit, onGuide: () -> Unit) {
    val ctx = LocalContext.current
    val fav = Prefs.isFav(acc.id, "live:${ch.id}")
    val catKey = "live:${ch.categoryId}"
    val lockedNow = Prefs.isLockedManually(acc.id, catKey)
    val entries = listOf(
        stringResource(R.string.action_play) to onPlay,
        stringResource(if (fav) R.string.action_fav_remove else R.string.action_fav_add) to { Prefs.toggleFav(acc.id, "live:${ch.id}"); onDismiss() },
        stringResource(R.string.guide) to onGuide,
        stringResource(R.string.action_record) to { startRecording(ctx, acc, ch); onDismiss() },
        stringResource(R.string.action_external) to { External.open(ctx, Repo.playLive(acc, ch)); onDismiss() },
        stringResource(if (lockedNow) R.string.action_unlock else R.string.action_lock) to {
            onDismiss(); Nav.guarded(true) { Prefs.toggleLock(acc.id, catKey) }
        },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ch.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entries.forEach { (label, action) ->
                    FocusCard(onClick = action, modifier = Modifier.fillMaxWidth(), zoom = 1f) {
                        Text(label, Modifier.padding(14.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
