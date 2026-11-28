package com.mohdev.faiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.Reminder
import com.mohdev.faiptv.data.Account
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.Programme
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.record.Reminders
import java.util.UUID

private const val HALF_HOUR = 30 * 60_000L

/** What can be done with a programme: watch, replay, remind, start automatically, record. */
@Composable
fun ProgrammeActions(acc: Account, ch: Live, p: Programme, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val name = Prefs.nameOf(acc.id, "live:${ch.id}", ch.name)
    val archived = p.isPast(now) && (p.hasArchive || (ch.archiveDays > 0 && p.start > now - ch.archiveDays * 86_400_000L))
    var details by remember { mutableStateOf(false) }
    val actions = mutableListOf<Pair<String, () -> Unit>>()
    if (p.isNow(now)) actions += stringResource(R.string.action_play) to { Nav.liveQueue = emptyList(); Nav.play(ctx, Repo.playLive(acc, ch)) }
    if (archived) actions += stringResource(R.string.epg_catchup) to { Nav.liveQueue = emptyList(); Nav.play(ctx, Repo.playCatchup(acc, ch, p)) }
    if (p.start > now) {
        actions += stringResource(R.string.rem_remind, Prefs.s.remindMin) to {
            Reminders.add(ctx, Reminder(UUID.randomUUID().toString(), acc.id, ch.id, name, p.title, p.start, open = false))
            Nav.toast = ctx.getString(R.string.rem_saved)
        }
        actions += stringResource(R.string.rem_open_at_start) to {
            Reminders.add(ctx, Reminder(UUID.randomUUID().toString(), acc.id, ch.id, name, p.title, p.start, open = true))
            Nav.toast = ctx.getString(R.string.rem_saved)
        }
    }
    if (p.end > now && !Prefs.isKids) actions += stringResource(R.string.action_record_program) to { scheduleRecording(ctx, acc, ch, p) }
    if (p.desc.isNotBlank()) actions += stringResource(R.string.action_info) to { details = true }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false; onDismiss() },
            title = { Text(p.title) },
            text = { Text("${fmtDayShort(p.start)} ${fmtTime(p.start)}–${fmtTime(p.end)} · $name\n\n${p.desc}") },
            confirmButton = { TextButton(onClick = { details = false; onDismiss() }) { Text(stringResource(R.string.action_close)) } },
        )
    } else {
        ActionMenu("${fmtTime(p.start)} · ${p.title}", actions, onDismiss)
    }
}

/** Grid programme guide: channels × time, 2–3 hours visible, shift by one hour. */
@Composable
fun GuideScreen(group0: String, wide: Boolean) {
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: run { LoadingBox(); return }
    val vis = rememberVis(lib, acc.id)
    val groups = liveGroups(vis, withNew = false)
    var group by remember { mutableStateOf(group0.takeIf { g -> groups.any { it.id == g } } ?: CAT_ALL) }
    val channels = remember(vis, group) { channelsOf(vis, group).ifEmpty { vis.allLive } }
    val now = System.currentTimeMillis()
    var winStart by remember { mutableLongStateOf(now - now % HALF_HOUR - HALF_HOUR) }
    val winLen = if (wide) 3 * 2 * HALF_HOUR else 2 * 2 * HALF_HOUR
    var action by remember { mutableStateOf<Pair<Live, Programme>?>(null) }
    val chW = if (wide) 190.dp else 120.dp

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.MediaFastForward, Key.PageDown -> { winStart += 2 * HALF_HOUR; true }
                Key.MediaRewind, Key.PageUp -> { winStart -= 2 * HALF_HOUR; true }
                else -> false
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close)) }
            Text(stringResource(R.string.guide), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Text(fmtDay(winStart), color = MaterialTheme.colorScheme.onSurfaceVariant, style = tabular)
            Spacer(Modifier.weight(1f))
            FocusCard(onClick = { winStart -= 2 * HALF_HOUR }, color = Color.Transparent) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.padding(8.dp)) }
            FocusCard(onClick = { val n = System.currentTimeMillis(); winStart = n - n % HALF_HOUR - HALF_HOUR }, color = Color.Transparent) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.epg_now))
                }
            }
            FocusCard(onClick = { winStart += 2 * HALF_HOUR }, color = Color.Transparent) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.padding(8.dp)) }
            Spacer(Modifier.width(8.dp))
            ClockView(MaterialTheme.colorScheme.onBackground, 16f)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups, key = { it.id }) { g -> Chip(g.label, g.id == group, { Nav.guarded(g.locked) { group = g.id } }, locked = g.locked) }
        }
        // time ruler
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(24.dp)) {
            Spacer(Modifier.width(chW))
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val w = maxWidth
                var t = winStart
                while (t < winStart + winLen) {
                    val x = w * ((t - winStart).toFloat() / winLen)
                    Text(fmtTime(t), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = tabular, modifier = Modifier.offset(x = x + 4.dp))
                    t += HALF_HOUR
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(channels, key = { it.id }) { ch ->
                GuideRow(acc, ch, vis.liveName(ch), winStart, winLen, chW) { p -> action = ch to p }
            }
        }
    }
    action?.let { (ch, p) -> ProgrammeActions(acc, ch, p) { action = null } }
}

@Composable
private fun GuideRow(acc: Account, ch: Live, name: String, winStart: Long, winLen: Long, chW: androidx.compose.ui.unit.Dp, onPick: (Programme) -> Unit) {
    val ctx = LocalContext.current
    var list by remember(ch.id) { mutableStateOf<List<Programme>?>(null) }
    LaunchedEffect(ch.id) { list = Repo.epg(acc, ch, full = true) }
    val now = System.currentTimeMillis()
    val winEnd = winStart + winLen
    Row(Modifier.fillMaxWidth().height(62.dp).padding(vertical = 2.dp)) {
        FocusCard(
            onClick = { Nav.liveQueue = emptyList(); Nav.play(ctx, Repo.playLive(acc, ch)) },
            modifier = Modifier.width(chW).fillMaxHeight().padding(horizontal = 4.dp), zoom = 1f,
        ) {
            Row(Modifier.padding(horizontal = 6.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                if (ch.num > 0) Text(ch.num.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = tabular, modifier = Modifier.width(30.dp))
                NetImage(ch.logo, Modifier.size(width = 40.dp, height = 28.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Fit, fallback = name)
                Spacer(Modifier.width(6.dp))
                Text(name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val w = maxWidth
            val l = list
            when {
                l == null -> Unit
                l.none { it.end > winStart && it.start < winEnd } ->
                    Text(stringResource(R.string.epg_none), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
                else -> l.filter { it.end > winStart && it.start < winEnd }.forEach { p ->
                    val s = maxOf(p.start, winStart)
                    val e = minOf(p.end, winEnd)
                    val x = w * ((s - winStart).toFloat() / winLen)
                    val pw = w * ((e - s).toFloat() / winLen)
                    val live = p.isNow(now)
                    FocusCard(
                        onClick = { onPick(p) },
                        modifier = Modifier.offset(x = x).width(pw).fillMaxHeight().padding(horizontal = 1.dp),
                        selected = live, zoom = 1f, shape = RoundedCornerShape(6.dp),
                        color = if (p.isPast(now)) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text(p.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (live) FontWeight.Bold else FontWeight.Normal)
                            Text(fmtTime(p.start), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = tabular, maxLines = 1)
                        }
                    }
                }
            }
            if (now in winStart until winEnd) {
                Box(Modifier.offset(x = w * ((now - winStart).toFloat() / winLen)).width(2.dp).fillMaxHeight().background(Brand.Live))
            }
        }
    }
}
