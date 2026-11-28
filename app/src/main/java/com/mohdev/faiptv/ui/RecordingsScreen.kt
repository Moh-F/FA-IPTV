package com.mohdev.faiptv.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Kind
import com.mohdev.faiptv.data.PlayRequest
import com.mohdev.faiptv.record.RecordService
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun RecordingsScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val files = remember(tick, RecordService.active.size) {
        RecordService.dir(ctx).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
    }
    var del by remember { mutableStateOf<File?>(null) }
    var newSchedule by remember { mutableStateOf(false) }
    val activeFiles = RecordService.active.map { it.file.name }.toSet()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.nav_recordings), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        if (RecordService.active.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.rec_active)) }
            items(RecordService.active.toList(), key = { it.id }) { r ->
                RecRow(Icons.Default.FiberManualRecord, Brand.Live, r.title, "${fmtTime(r.started)} · ${fmtSize(r.file.length())}" + (if (r.endAt > 0) " → ${fmtTime(r.endAt)}" else "")) {
                    IconButton(onClick = { RecordService.stop(ctx, r.id) }) { Icon(Icons.Default.Stop, stringResource(R.string.action_stop)) }
                }
            }
        }
        val sched = Prefs.data.scheduled.sortedBy { it.start }
        if (sched.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.rec_scheduled)) }
            items(sched, key = { it.id }) { r ->
                RecRow(Icons.Default.Schedule, Brand.Violet, "${r.channel} · ${r.title}", "${fmtDayShort(r.start)} ${fmtTime(r.start)}–${fmtTime(r.end)}") {
                    IconButton(onClick = { RecordService.cancelScheduled(ctx, r.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_cancel)) }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.rem_title)) }
        val rems = Prefs.data.reminders.sortedBy { it.at }
        items(rems, key = { "r" + it.id }) { r ->
            RecRow(Icons.Default.Schedule, Brand.Amber, "${r.channel} · ${r.title}",
                fmtDayShort(r.at) + " " + fmtTime(r.at) + " · " + stringResource(if (r.open) R.string.rem_open_at_start else R.string.rem_kind_remind)) {
                IconButton(onClick = { com.mohdev.faiptv.record.Reminders.cancel(ctx, r.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_cancel)) }
            }
        }
        item {
            FocusCard(onClick = { newSchedule = true }, modifier = Modifier.fillMaxWidth(), zoom = 1.01f) {
                Text("+ " + stringResource(R.string.rem_new), Modifier.padding(14.dp))
            }
        }
        item { SectionTitle(stringResource(R.string.rec_files)) }
        val done = files.filter { it.name !in activeFiles }
        if (done.isEmpty()) item { Text(stringResource(R.string.rec_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        items(done, key = { it.name }) { f ->
            RecRow(Icons.Default.PlayArrow, Brand.Cyan, f.nameWithoutExtension, "${fmtDay(f.lastModified())} ${fmtTime(f.lastModified())} · ${fmtSize(f.length())}",
                onClick = {
                    Nav.play(ctx, PlayRequest(Uri.fromFile(f).toString(), f.nameWithoutExtension, Kind.MOVIE, "rec:${f.name}"), forceInternal = true)
                }) {
                IconButton(onClick = { del = f }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
            }
        }
        item { Text(stringResource(R.string.rec_folder_hint, RecordService.dir(ctx).absolutePath), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
    }
    if (newSchedule) ScheduleDialog { newSchedule = false }
    del?.let { f -> ConfirmDialog(stringResource(R.string.action_delete) + ": " + f.name, onDismiss = { del = null }) { f.delete(); tick++ } }
}

@Composable
private fun RecRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, sub: String, onClick: () -> Unit = {}, trailing: @Composable () -> Unit) {
    FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), zoom = 1.01f) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
}


/** "Open channel X on date … at …" or a reminder, entered by hand. */
@Composable
private fun ScheduleDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: run { onDismiss(); return }
    val lib = com.mohdev.faiptv.data.Repo.lib ?: run { onDismiss(); return }
    val vis = rememberVis(lib, acc.id)
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<com.mohdev.faiptv.data.Live?>(null) }
    val start = System.currentTimeMillis() + 3_600_000L
    var date by remember { mutableStateOf(java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US).format(java.util.Date(start))) }
    var time by remember { mutableStateOf(java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(start))) }
    var open by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf(false) }
    val matches = remember(q, vis) {
        val n = com.mohdev.faiptv.data.Repo.norm(q.trim())
        if (n.isEmpty()) emptyList() else vis.allLive.asSequence().filter { it.num.toString() == n || (lib.normName["l" + it.id] ?: "").contains(n) }.take(6).toList()
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rem_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(q, { q = it; pick = null }, label = { Text(stringResource(R.string.rem_channel_q)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (pick == null) matches.forEach { ch ->
                    FocusCard(onClick = { pick = ch; q = vis.liveName(ch) }, modifier = Modifier.fillMaxWidth(), zoom = 1f) {
                        Text((if (ch.num > 0) "${ch.num}  " else "") + vis.liveName(ch), Modifier.padding(10.dp))
                    }
                } else Text("✓ " + vis.liveName(pick!!), color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(date, { date = it }, label = { Text("dd.MM.yyyy") }, singleLine = true, modifier = Modifier.weight(1f))
                    androidx.compose.material3.OutlinedTextField(time, { time = it }, label = { Text("HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(open, { open = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (open) R.string.rem_open_at_start else R.string.rem_kind_remind))
                }
                if (err) Text(stringResource(R.string.rem_invalid), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val ch = pick
                val at = runCatching { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).parse("${date.trim()} ${time.trim()}")!!.time }.getOrNull()
                if (ch == null || at == null || at < System.currentTimeMillis()) { err = true; return@TextButton }
                com.mohdev.faiptv.record.Reminders.add(ctx, com.mohdev.faiptv.core.Reminder(java.util.UUID.randomUUID().toString(), acc.id, ch.id, vis.liveName(ch), "", at, open))
                Nav.toast = ctx.getString(R.string.rem_saved)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
