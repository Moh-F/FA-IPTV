package com.mohdev.faiptv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.MainActivity
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.ui.Brand
import com.mohdev.faiptv.ui.FocusCard
import com.mohdev.faiptv.ui.Nav
import com.mohdev.faiptv.ui.Route
import com.mohdev.faiptv.ui.channelsOf
import com.mohdev.faiptv.ui.rememberVis

private val silent = object : EngineListener {
    override fun onState(playing: Boolean, buffering: Boolean) {}
    override fun onReady() {}
    override fun onEnded() {}
    override fun onError(code: String, unsupported: Boolean) {}
}

/** 2 or 4 channels at once (e.g. several matches). Sound comes from the focused picture; OK opens it full screen. */
@Composable
fun MultiViewScreen(ids0: List<String>) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: return
    val vis = rememberVis(lib, acc.id)
    val ids = remember { mutableStateListOf<String?>().apply { addAll(ids0.take(4)); while (size < 4) add(null) } }
    var count by remember { mutableIntStateOf(if (ids0.size > 2) 4 else 2) }
    var active by remember { mutableIntStateOf(0) }
    var pickFor by remember { mutableStateOf<Int?>(null) }
    val engines = remember(count) { List(count) { ExoEngine(ctx, silent) } }
    DisposableEffect(engines) {
        (ctx as? MainActivity)?.setBarsHidden(true)
        onDispose { engines.forEach { it.release() }; (ctx as? MainActivity)?.setBarsHidden(false) }
    }
    for (i in 0 until count) {
        val id = ids[i]
        LaunchedEffect(engines, id) {
            val ch = id?.let { lib.liveById[it] } ?: return@LaunchedEffect
            engines[i].load(Repo.playLive(acc, ch))
        }
    }
    LaunchedEffect(engines, active) { engines.forEachIndexed { i, e -> e.setVolume(if (i == active) 1f else 0f) } }
    BackHandler { Nav.pop() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            val rows = if (count == 4) listOf(0 to 1, 2 to 3) else listOf(0 to 1)
            rows.forEach { (a, b) ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    listOf(a, b).forEach { i ->
                        val ch = ids[i]?.let { lib.liveById[it] }
                        FocusCard(
                            onClick = { if (ch != null) { Nav.liveQueue = emptyList(); Nav.replaceTop(Route.Player(Repo.playLive(acc, ch))) } else pickFor = i },
                            onLongClick = { pickFor = i },
                            onFocus = { active = i },
                            modifier = Modifier.weight(1f).fillMaxSize().padding(2.dp)
                                .then(if (i == active) Modifier.border(3.dp, Brand.Cyan, RoundedCornerShape(6.dp)) else Modifier),
                            color = Color.Black, zoom = 1f, shape = RoundedCornerShape(6.dp),
                        ) {
                            if (ch != null) key(engines[i]) { engines[i].Surface(Modifier.fillMaxSize()) }
                            Text(
                                if (ch != null) vis.liveName(ch) else "+ " + stringResource(R.string.multi_pick),
                                color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(if (ch != null) Alignment.BottomStart else Alignment.Center).padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        FocusCard(
            onClick = { count = if (count == 2) 4 else 2; if (active >= count) active = 0 },
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp), color = Color(0x99000000), zoom = 1f,
        ) { Text(if (count == 2) "2 → 4" else "4 → 2", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
    }

    pickFor?.let { slot ->
        val list = channelsOf(vis, Nav.liveGroup).ifEmpty { vis.allLive }
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text(stringResource(R.string.multi_pick)) },
            text = {
                LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(list, key = { it.id }) { ch ->
                        FocusCard(onClick = { ids[slot] = ch.id; pickFor = null }, modifier = Modifier.fillMaxWidth(), zoom = 1f) {
                            Text((if (ch.num > 0) "${ch.num}  " else "") + vis.liveName(ch), Modifier.padding(10.dp), maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
