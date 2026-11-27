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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
const val CAT_NEWLIST = "*new"
const val LIST_PREFIX = "*list:"

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

/** One entry of the category column: real category, favourites list or a virtual group. */
data class Group(val id: String, val label: String, val count: Int, val locked: Boolean = false, val isList: Boolean = false)

/** Channels of a group for the current profile, in the user's order. */
fun channelsOf(vis: Vis, group: String): List<Live> {
    val lib = vis.lib
    val acc = vis.acc
    return when {
        group == CAT_ALL -> vis.allLive
        group == CAT_RECENT -> Prefs.pd.recentLive[acc].orEmpty().mapNotNull { lib.liveById[it] }.filter(vis::liveOk)
        group.startsWith(LIST_PREFIX) -> Prefs.favLists(acc).firstOrNull { it.id == group.removePrefix(LIST_PREFIX) }
            ?.items.orEmpty().filter { it.startsWith("live:") }.mapNotNull { lib.liveById[it.removePrefix("live:")] }.filter(vis::liveOk)
        else -> vis.liveIn(group)
    }
}

/** Groups (lists, recent, all, categories) for the current profile. */
@Composable
fun liveGroups(vis: Vis, withNew: Boolean): List<Group> {
    val acc = vis.acc
    val defaultList = stringResource(R.string.cat_favorites)
    val newLabel = "+ " + stringResource(R.string.fav_new_list)
    val recent = stringResource(R.string.cat_recent)
    val all = stringResource(R.string.cat_all)
    return buildList {
        Prefs.favLists(acc).forEach { l -> add(Group(LIST_PREFIX + l.id, "★ " + l.name.ifBlank { defaultList }, l.items.count { it.startsWith("live:") }, isList = true)) }
        if (withNew && !vis.kids) add(Group(CAT_NEWLIST, newLabel, 0))
        add(Group(CAT_RECENT, recent, Prefs.pd.recentLive[acc]?.size ?: 0))
        add(Group(CAT_ALL, all, vis.allLive.size))
        vis.liveCats.forEach { c -> add(Group(c.id, vis.catName("live", c), vis.lib.liveByCat[c.id]?.size ?: 0, locked = c.id in vis.lockedLive)) }
    }
}

/** Move mode: the item [key] moves with up/down inside [scope]. */
private data class Moving(val key: String, val scope: String)

@Composable
fun LiveScreen(wide: Boolean) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: run { if (Repo.loading) LoadingBox() else EmptyBox(stringResource(R.string.empty_list)); return }
    val vis = rememberVis(lib, acc.id)
    val lists = Prefs.favLists(acc.id)
    val defaultList = stringResource(R.string.cat_favorites)
    var cat by rememberSaveable(acc.id) {
        mutableStateOf(lists.firstOrNull { l -> l.items.any { it.startsWith("live:") } }?.let { LIST_PREFIX + it.id } ?: CAT_ALL)
    }
    val focused = remember { mutableStateOf<Live?>(null) }   // read only by the EPG pane (no list recomposition)
    var menuFor by remember { mutableStateOf<Live?>(null) }
    var catMenu by remember { mutableStateOf<Group?>(null) }
    var guideFor by remember { mutableStateOf<Live?>(null) }
    var moving by remember { mutableStateOf<Moving?>(null) }
    var rename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameList by remember { mutableStateOf<String?>(null) }
    var newList by remember { mutableStateOf<String?>(null) }
    var addTo by remember { mutableStateOf<String?>(null) }

    val groups = liveGroups(vis, withNew = true)
    val channels = remember(vis, cat, Prefs.pd) { channelsOf(vis, cat) }
    val channelScope = when {
        cat.startsWith(LIST_PREFIX) -> "fav:" + cat.removePrefix(LIST_PREFIX)
        cat.startsWith("*") -> null
        else -> "live:$cat"
    }

    fun pick(g: Group) {
        if (g.id == CAT_NEWLIST) newList = "" else Nav.guarded(g.locked) { cat = g.id }
    }
    fun play(ch: Live) {
        Nav.liveQueue = channels
        Nav.liveGroup = cat
        Nav.play(ctx, Repo.playLive(acc, ch))
    }
    fun moveChannel(ch: Live, delta: Int) {
        val scope = channelScope ?: return
        if (scope.startsWith("fav:")) Prefs.moveInList(scope.removePrefix("fav:"), "live:${ch.id}", delta)
        else Prefs.move(acc.id, scope, channels.map { it.id }, ch.id, delta)
    }
    fun moveGroup(g: Group, delta: Int) {
        if (g.isList) Prefs.moveList(g.id.removePrefix(LIST_PREFIX), delta)
        else if (!g.id.startsWith("*")) Prefs.move(acc.id, "cats:live", vis.liveCats.map { it.id }, g.id, delta)
    }

    val catColumn: @Composable (Modifier) -> Unit = { mod ->
        val state = rememberLazyListState()
        val itemFocus = remember { ItemFocus() }
        val movingCat = moving?.takeIf { it.scope == "cats" }
        LazyColumn(
            mod.enterAtFirstVisible(state, itemFocus).moveKeys(movingCat) { d ->
                groups.firstOrNull { it.id == movingCat?.key }?.let { moveGroup(it, d) }
            },
            state = state, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(groups, key = { it.id }) { g ->
                val isMoving = movingCat?.key == g.id
                FocusCard(
                    onClick = { if (moving != null) moving = null else pick(g) },
                    onLongClick = if ((g.id.startsWith("*") && !g.isList) || vis.kids) null else ({ catMenu = g }),
                    modifier = Modifier.fillMaxWidth(), selected = cat == g.id || isMoving, color = Color.Transparent, zoom = 1f,
                    focusRequester = itemFocus.req(g.id),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isMoving) { Icon(Icons.Default.SwapVert, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
                        if (g.locked || Prefs.isLockedManually(acc.id, "live:${g.id}")) {
                            Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = Brand.Amber); Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            g.label + if (g.count > 0) "  ${g.count}" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                            color = if (cat == g.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    val channelList: @Composable (Modifier) -> Unit = { mod ->
        ChannelList(
            channels, mod, acc, vis,
            moving = moving?.takeIf { it.scope == "ch" }?.key,
            onPlay = { ch -> if (moving != null) moving = null else play(ch) },
            onMenu = { menuFor = it },
            onFocus = { focused.value = it },
            onMove = { ch, d -> moveChannel(ch, d) },
            onMoveDone = { moving = null },
        )
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            catColumn(Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer))
            channelList(Modifier.weight(1f))
            Box(Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
                EpgHolder(acc, focused, channels.firstOrNull(), ::play)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it.id }) { g -> Chip(g.label, cat == g.id, { pick(g) }, locked = g.locked) }
            }
            channelList(Modifier.weight(1f))
        }
    }

    // ---- menus & dialogs
    menuFor?.let { ch ->
        val key = "live:${ch.id}"
        val inList = cat.startsWith(LIST_PREFIX)
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += stringResource(R.string.action_play) to { play(ch) }
        actions += stringResource(R.string.guide) to { guideFor = ch }
        if (!vis.kids) {
            actions += stringResource(R.string.fav_add_to_list) to { addTo = key }
            if (inList) actions += stringResource(R.string.fav_remove_from_list) to { Prefs.removeFromList(cat.removePrefix(LIST_PREFIX), key) }
            if (channelScope != null) actions += stringResource(R.string.action_move) to { moving = Moving(ch.id, "ch") }
            actions += stringResource(R.string.action_rename) to { rename = key to vis.liveName(ch) }
            actions += stringResource(R.string.action_hide) to { Prefs.toggleHidden(acc.id, key) }
            actions += stringResource(R.string.action_record) to { startRecording(ctx, acc, ch) }
        }
        actions += stringResource(R.string.action_external) to { External.open(ctx, Repo.playLive(acc, ch)) }
        ActionMenu(vis.liveName(ch), actions) { menuFor = null }
    }
    catMenu?.let { g ->
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (g.isList) {
            val id = g.id.removePrefix(LIST_PREFIX)
            actions += stringResource(R.string.action_rename) to { renameList = id }
            actions += stringResource(R.string.action_move) to { moving = Moving(g.id, "cats") }
            actions += stringResource(R.string.fav_delete_list) to { Prefs.deleteList(id); if (cat == g.id) cat = CAT_ALL }
        } else {
            val ck = "livecat:${g.id}"
            val lk = "live:${g.id}"
            actions += stringResource(R.string.action_move) to { moving = Moving(g.id, "cats") }
            actions += stringResource(R.string.action_rename) to { rename = ck to g.label }
            actions += stringResource(R.string.action_hide) to { Prefs.toggleHidden(acc.id, ck) }
            actions += stringResource(if (Prefs.isLockedManually(acc.id, lk)) R.string.action_unlock else R.string.action_lock) to {
                Nav.guarded(true) { Prefs.toggleLock(acc.id, lk) }
            }
            actions += stringResource(R.string.action_reset_order) to { Prefs.resetOrder(acc.id, "live:${g.id}") }
        }
        ActionMenu(g.label, actions) { catMenu = null }
    }
    rename?.let { (key, current) ->
        TextInputDialog(stringResource(R.string.action_rename), current, onDismiss = { rename = null }) { Prefs.rename(acc.id, key, it) }
    }
    renameList?.let { id ->
        TextInputDialog(stringResource(R.string.action_rename), lists.firstOrNull { it.id == id }?.name ?: "", onDismiss = { renameList = null }) { Prefs.renameList(id, it) }
    }
    newList?.let { pending ->
        TextInputDialog(stringResource(R.string.fav_new_list), "", onDismiss = { newList = null }) { name ->
            val id = Prefs.newFavList(acc.id, name, if (pending.isNotBlank()) listOf(pending) else emptyList())
            if (pending.isBlank()) cat = LIST_PREFIX + id
        }
    }
    addTo?.let { key ->
        val opts = lists.map { it.id to it.name.ifBlank { defaultList } } + ("*new" to "+ " + stringResource(R.string.fav_new_list))
        ChoiceDialog(stringResource(R.string.fav_add_to_list), opts, null, onDismiss = { addTo = null }) { id ->
            addTo = null
            if (id == "*new") newList = key else Prefs.addToList(id, key)
        }
    }
    guideFor?.let { ch ->
        Dialog(onDismissRequest = { guideFor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                EpgPane(acc, ch, vis.liveName(ch), onPlayLive = { guideFor = null; play(ch) })
            }
        }
    }
}

/** Up/Down move the item in move mode. */
private fun Modifier.moveKeys(moving: Any?, onMove: (Int) -> Unit): Modifier =
    if (moving == null) this else this.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionUp -> { onMove(-1); true }
            Key.DirectionDown -> { onMove(1); true }
            else -> false
        }
    }

@Composable
private fun EpgHolder(acc: Account, focused: MutableState<Live?>, first: Live?, play: (Live) -> Unit) {
    val ch = focused.value ?: first ?: return
    EpgPane(acc, ch, Prefs.nameOf(acc.id, "live:${ch.id}", ch.name), onPlayLive = { play(ch) })
}

@Composable
private fun ChannelList(
    channels: List<Live>, modifier: Modifier, acc: Account, vis: Vis, moving: String?,
    onPlay: (Live) -> Unit, onMenu: (Live) -> Unit, onFocus: (Live) -> Unit,
    onMove: (Live, Int) -> Unit, onMoveDone: () -> Unit,
) {
    if (channels.isEmpty()) { Box(modifier.fillMaxHeight()) { EmptyBox(stringResource(R.string.empty_list)) }; return }
    @Suppress("UNUSED_VARIABLE") val epgTick = Repo.epgVersion
    val state = rememberLazyListState()
    val itemFocus = remember { ItemFocus() }
    // TV: put the focus back on the channel that was played last
    val lastId = Prefs.pd.recentLive[acc.id]?.firstOrNull()?.takeIf { id -> channels.any { it.id == id } }
    LaunchedEffect(Unit) {
        if (UiEnv.isTv && lastId != null) {
            val idx = channels.indexOfFirst { it.id == lastId }
            if (idx > 3) state.scrollToItem(idx - 3)
            delay(150)
            runCatching { itemFocus.req(lastId).requestFocus() }
        }
    }
    LazyColumn(
        modifier.fillMaxHeight().enterAtFirstVisible(state, itemFocus).moveKeys(moving) { d ->
            channels.firstOrNull { it.id == moving }?.let { onMove(it, d) }
        },
        state = state, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(channels, key = { it.id }) { ch ->
            val fav = Prefs.isFav(acc.id, "live:${ch.id}")
            val isMoving = moving == ch.id
            FocusCard(
                onClick = { if (isMoving) onMoveDone() else onPlay(ch) }, onLongClick = { onMenu(ch) }, onFocus = { onFocus(ch) },
                modifier = Modifier.fillMaxWidth(), zoom = 1.01f, selected = isMoving,
                focusRequester = itemFocus.req(ch.id),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ch.num > 0) ch.num.toString() else "", Modifier.width(40.dp), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        NetImage(ch.logo, Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit, fallback = ch.name)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vis.liveName(ch), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Repo.nowTitle(ch)?.let {
                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (isMoving) {
                        IconButton(onClick = { onMove(ch, -1) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                        IconButton(onClick = { onMove(ch, 1) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                    } else {
                        if (ch.archiveDays > 0) Icon(Icons.Default.History, null, Modifier.size(18.dp), tint = Brand.Cyan)
                        if (fav) Icon(Icons.Default.Favorite, null, Modifier.padding(start = 6.dp).size(18.dp), tint = Brand.Pink)
                    }
                }
            }
        }
    }
}

/** Programme guide for one channel: now/next, catch-up for the past, record for the future. */
@Composable
fun EpgPane(acc: Account, ch: Live, title: String, onPlayLive: () -> Unit) {
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
            Box(Modifier.size(width = 72.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                NetImage(ch.logo, Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit, fallback = ch.name)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (ch.archiveDays > 0) Text(stringResource(R.string.epg_archive_days, ch.archiveDays), fontSize = 12.sp, color = Brand.Cyan)
            }
        }
        Spacer(Modifier.height(10.dp))
        val l = list
        when {
            l == null -> LoadingBox(Modifier.height(120.dp))
            l.isEmpty() -> Text(stringResource(R.string.epg_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val catchupColor = MaterialTheme.colorScheme.secondary
                val recColor = MaterialTheme.colorScheme.outline
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                            archived -> Badge(stringResource(R.string.epg_catchup), catchupColor)
                                            future -> Badge("●REC", recColor)
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
