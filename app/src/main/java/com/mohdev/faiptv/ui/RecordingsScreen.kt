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
