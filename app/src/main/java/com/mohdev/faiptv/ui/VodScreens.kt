package com.mohdev.faiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Category
import com.mohdev.faiptv.data.Episode
import com.mohdev.faiptv.data.Movie
import com.mohdev.faiptv.data.MovieInfo
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.data.Series
import com.mohdev.faiptv.data.SeriesInfo
import com.mohdev.faiptv.player.External

private data class VodItem(val id: String, val name: String, val image: String, val cat: String, val added: Long, val rating: String, val open: () -> Unit)

@Composable
fun MoviesScreen(wide: Boolean) {
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: run { if (Repo.loading) LoadingBox() else EmptyBox(stringResource(R.string.empty_list)); return }
    val vis = rememberVis(lib, acc.id)
    val items = remember(vis) { lib.raw.movies.filter(vis::movieOk).map { m -> VodItem(m.id, vis.name("movie:${m.id}", m.name), m.poster, m.categoryId, m.added, m.rating) { Nav.push(Route.MovieDetail(m)) } } }
    VodBrowser(wide, "movie", vis.movieCats.map { it.copy(name = vis.catName("movie", it)) }, items, acc.id, vis.lockedMovie, vis.kids)
}

@Composable
fun SeriesScreen(wide: Boolean) {
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: run { if (Repo.loading) LoadingBox() else EmptyBox(stringResource(R.string.empty_list)); return }
    val vis = rememberVis(lib, acc.id)
    val items = remember(vis) { lib.raw.series.filter(vis::seriesOk).map { s -> VodItem(s.id, vis.name("series:${s.id}", s.name), s.cover, s.categoryId, s.added, s.rating) { Nav.push(Route.SeriesDetail(s)) } } }
    VodBrowser(wide, "series", vis.seriesCats.map { it.copy(name = vis.catName("series", it)) }, items, acc.id, vis.lockedSeries, vis.kids)
}

@Composable
private fun VodBrowser(wide: Boolean, kind: String, cats: List<Category>, all: List<VodItem>, acc: String, locked: Set<String>, kids: Boolean) {
    var cat by rememberSaveable(kind) { mutableStateOf(CAT_ALL) }
    var sort by rememberSaveable(kind) { mutableIntStateOf(0) } // 0 default, 1 newest, 2 A-Z
    val favs = Prefs.favKeys(acc)
    var menuFor by remember { mutableStateOf<VodItem?>(null) }
    var catMenu by remember { mutableStateOf<Category?>(null) }
    var rename by remember { mutableStateOf<Pair<String, String>?>(null) }
    val allCats = remember(cats) { listOf(Category(CAT_FAV, ""), Category(CAT_ALL, "")) + cats }
    val counts = remember(all) { all.groupingBy { it.cat }.eachCount() }
    val shown = remember(all, cat, sort, locked, favs, Prefs.pd) {
        val base = when (cat) {
            CAT_ALL -> all.filter { it.cat !in locked }
            CAT_FAV -> all.filter { "$kind:${it.id}" in favs }
            else -> Prefs.ordered(acc, "$kind:$cat", all.filter { it.cat == cat }) { it.id }
        }
        when (sort) {
            1 -> base.sortedByDescending { it.added }
            2 -> base.sortedBy { Repo.norm(it.name) }
            else -> base
        }
    }
    val label: @Composable (Category) -> String = { c ->
        when (c.id) {
            CAT_ALL -> stringResource(R.string.cat_all) + " (${all.size})"
            CAT_FAV -> stringResource(R.string.cat_favorites)
            else -> c.name + " (${counts[c.id] ?: 0})"
        }
    }
    fun pick(c: Category) { Nav.guarded(c.id in locked) { cat = c.id } }
    val sortLabels = listOf(R.string.sort_default, R.string.sort_newest, R.string.sort_az)

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gridFocus = remember { ItemFocus() }
    val grid: @Composable (Modifier) -> Unit = { mod ->
        if (shown.isEmpty()) Box(mod) { EmptyBox(stringResource(R.string.empty_list)) }
        else LazyVerticalGrid(
            columns = GridCells.Adaptive(if (wide) 140.dp else 108.dp), modifier = mod.enterAtFirstVisible(gridState, gridFocus), state = gridState,
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${shown.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Chip("⇅ " + stringResource(sortLabels[sort]), false, { sort = (sort + 1) % 3 })
                }
            }
            items(shown, key = { it.id }) { v ->
                PosterCard(
                    v.name, v.image, onClick = v.open, width = 200.dp, modifier = Modifier.fillMaxWidth(),
                    onLongClick = if (kids) null else ({ menuFor = v }),
                    focusRequester = gridFocus.req(v.id),
                    badge = if ("$kind:${v.id}" in favs) "♥" else v.rating.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ %.1f".format(java.util.Locale.US, it) },
                )
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            val catState = androidx.compose.foundation.lazy.rememberLazyListState()
            val catFocus = remember { ItemFocus() }
            LazyColumn(
                Modifier.width(250.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer).enterAtFirstVisible(catState, catFocus),
                state = catState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(allCats, key = { it.id }) { c ->
                    FocusCard(
                        onClick = { pick(c) },
                        onLongClick = if (c.id.startsWith("*") || kids) null else ({ catMenu = c }),
                        modifier = Modifier.fillMaxWidth(), selected = cat == c.id, color = Color.Transparent, zoom = 1f,
                        focusRequester = catFocus.req(c.id),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (c.id in locked || Prefs.isLockedManually(acc, "$kind:${c.id}")) {
                                Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = Brand.Amber); Spacer(Modifier.width(6.dp))
                            }
                            Text(label(c), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                                color = if (cat == c.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            grid(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCats, key = { it.id }) { c -> Chip(label(c), cat == c.id, { pick(c) }, locked = c.id in locked) }
            }
            grid(Modifier.weight(1f).fillMaxWidth())
        }
    }

    menuFor?.let { v ->
        val key = "$kind:${v.id}"
        val fav = key in favs
        val inCat = !cat.startsWith("*") && sort == 0
        val ids = shown.map { it.id }
        val acts = mutableListOf<Pair<String, () -> Unit>>(
            stringResource(if (fav) R.string.action_fav_remove else R.string.action_fav_add) to { Prefs.toggleFav(acc, key) },
            stringResource(R.string.action_rename) to { rename = key to v.name },
            stringResource(R.string.action_hide) to { Prefs.toggleHidden(acc, key) },
        )
        if (inCat) {
            acts += stringResource(R.string.action_move_up) to { Prefs.move(acc, "$kind:$cat", ids, v.id, -1) }
            acts += stringResource(R.string.action_move_down) to { Prefs.move(acc, "$kind:$cat", ids, v.id, 1) }
            acts += stringResource(R.string.action_move_top) to { Prefs.move(acc, "$kind:$cat", ids, v.id, -100_000) }
            acts += stringResource(R.string.action_move_bottom) to { Prefs.move(acc, "$kind:$cat", ids, v.id, 100_000) }
        }
        ActionMenu(v.name, acts) { menuFor = null }
    }
    catMenu?.let { c ->
        val ck = "${kind}cat:${c.id}"
        val lk = "$kind:${c.id}"
        ActionMenu(c.name, listOf(
            stringResource(R.string.action_rename) to { rename = ck to c.name },
            stringResource(R.string.action_hide) to { Prefs.toggleHidden(acc, ck) },
            stringResource(R.string.action_move_up) to { Prefs.move(acc, "cats:$kind", cats.map { it.id }, c.id, -1) },
            stringResource(R.string.action_move_down) to { Prefs.move(acc, "cats:$kind", cats.map { it.id }, c.id, 1) },
            stringResource(R.string.action_move_top) to { Prefs.move(acc, "cats:$kind", cats.map { it.id }, c.id, -100_000) },
            stringResource(R.string.action_move_bottom) to { Prefs.move(acc, "cats:$kind", cats.map { it.id }, c.id, 100_000) },
            stringResource(R.string.action_reset_order) to { Prefs.resetOrder(acc, "$kind:${c.id}") },
            stringResource(if (Prefs.isLockedManually(acc, lk)) R.string.action_unlock else R.string.action_lock) to {
                Nav.guarded(true) { Prefs.toggleLock(acc, lk) }
            },
        )) { catMenu = null }
    }
    rename?.let { (key, current) ->
        TextInputDialog(stringResource(R.string.action_rename), current, onDismiss = { rename = null }) { Prefs.rename(acc, key, it) }
    }
}

@Composable
private fun DetailHeader(title: String, poster: String, backdrop: String, meta: List<String>, wide: Boolean, actions: @Composable () -> Unit, body: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        NetImage(backdrop.ifBlank { poster }, Modifier.fillMaxWidth().height(if (wide) 360.dp else 240.dp), fallback = "")
        Box(
            Modifier.fillMaxWidth().height(if (wide) 362.dp else 242.dp).background(
                Brush.verticalGradient(listOf(Color(0x33000000), MaterialTheme.colorScheme.background))
            )
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                IconButton(onClick = { Nav.pop() }, Modifier.padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0x66000000))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close), tint = Color.White)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = if (wide) 40.dp else 16.dp)) {
                    NetImage(poster, Modifier.width(if (wide) 180.dp else 110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)), fallback = title)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = if (wide) 30.sp else 22.sp, fontWeight = FontWeight.Bold)
                        val m = meta.filter { it.isNotBlank() }.joinToString("  •  ")
                        if (m.isNotBlank()) Text(m, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(16.dp))
                        actions()
                    }
                }
            }
            item { Column(Modifier.padding(horizontal = 20.dp)) { body() } }
        }
    }
}

@Composable
private fun InfoLine(label: Int, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(stringResource(label) + ": ", fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MovieDetailScreen(m: Movie, wide: Boolean) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: return
    var info by remember(m.id) { mutableStateOf<MovieInfo?>(null) }
    LaunchedEffect(m.id) { info = Repo.movieInfo(acc, m) }
    val resume = Prefs.pd.resume["movie:${m.id}"]?.takeIf { !it.finished && it.posMs > 30_000 }
    val fav = Prefs.isFav(acc.id, "movie:${m.id}")
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    val i = info ?: MovieInfo()
    val req = { Repo.playMovie(acc, m, i.ext) }
    DetailHeader(
        m.name, m.poster, i.backdrop,
        listOf(i.year, i.duration, i.rating.ifBlank { m.rating }.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" } ?: "", i.genre),
        wide,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { Nav.play(ctx, req()) }, Modifier.focusRequester(playFocus)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                    Text(if (resume != null) stringResource(R.string.action_resume, fmtDur(resume.posMs)) else stringResource(R.string.action_play))
                }
                if (resume != null) OutlinedButton(onClick = { Nav.play(ctx, req().copy(startAtMs = 0)) }) {
                    Icon(Icons.Default.Replay, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_restart))
                }
                IconButton(onClick = { Prefs.toggleFav(acc.id, "movie:${m.id}") }) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav) Brand.Pink else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { External.open(ctx, req()) }) { Icon(Icons.Default.OpenInNew, stringResource(R.string.action_external)) }
            }
            if (resume != null) LinearProgressIndicator(progress = { resume.fraction }, Modifier.padding(top = 10.dp).width(260.dp), color = Brand.Cyan)
        },
    ) {
        if (info == null) LoadingBox(Modifier.height(80.dp))
        if (i.plot.isNotBlank()) Text(i.plot, Modifier.padding(bottom = 12.dp), lineHeight = 22.sp)
        InfoLine(R.string.cast, i.cast)
        InfoLine(R.string.director, i.director)
        InfoLine(R.string.genre, i.genre)
    }
}

@Composable
fun SeriesDetailScreen(s: Series, wide: Boolean) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: return
    var info by remember(s.id) { mutableStateOf<SeriesInfo?>(null) }
    LaunchedEffect(s.id) { info = Repo.seriesInfo(acc, s) }
    val i = info
    val seasons = i?.seasons?.keys?.toList().orEmpty()
    var season by rememberSaveable(s.id) { mutableIntStateOf(-1) }
    val fav = Prefs.isFav(acc.id, "series:${s.id}")
    val flat: List<Episode> = remember(i) { i?.seasons?.values?.flatten().orEmpty() }
    val lastWatched = remember(flat, Prefs.pd.resume) {
        flat.mapNotNull { e -> Prefs.pd.resume["ep:${e.id}"]?.let { e to it } }.maxByOrNull { it.second.updated }
    }
    LaunchedEffect(seasons) {
        if (season == -1 && seasons.isNotEmpty()) season = lastWatched?.first?.season ?: seasons.first()
    }
    fun play(e: Episode) {
        Nav.episodeQueue = flat.map { s to it }
        Nav.play(ctx, Repo.playEpisode(acc, s, e))
    }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(i) { if (i != null) runCatching { playFocus.requestFocus() } }
    DetailHeader(
        s.name, s.cover, i?.backdrop ?: "",
        listOf(s.year, s.rating.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" } ?: "", i?.genre ?: "",
            if (seasons.isNotEmpty()) "${seasons.size} × " + stringResource(R.string.season) else ""),
        wide,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val target = lastWatched?.let { (e, r) -> if (r.finished) flat.getOrNull(flat.indexOf(e) + 1) ?: e else e } ?: flat.firstOrNull()
                if (target != null) Button(onClick = { play(target) }, Modifier.focusRequester(playFocus)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                    Text(
                        (if (lastWatched != null) stringResource(R.string.action_continue) else stringResource(R.string.action_play)) +
                            " S%02dE%02d".format(java.util.Locale.US, target.season, target.episode)
                    )
                }
                IconButton(onClick = { Prefs.toggleFav(acc.id, "series:${s.id}") }) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav) Brand.Pink else MaterialTheme.colorScheme.onSurface)
                }
            }
        },
    ) {
        if (i == null) { LoadingBox(Modifier.height(80.dp)); return@DetailHeader }
        val plot = i.plot.ifBlank { s.plot }
        if (plot.isNotBlank()) Text(plot, Modifier.padding(bottom = 12.dp), lineHeight = 22.sp)
        InfoLine(R.string.cast, i.cast)
        if (seasons.isEmpty()) { Text(stringResource(R.string.empty_list)); return@DetailHeader }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(seasons) { n -> Chip(stringResource(R.string.season_n, n), season == n, { season = n }) }
        }
        i.seasons[season].orEmpty().forEach { e ->
            val r = Prefs.pd.resume["ep:${e.id}"]
            FocusCard(onClick = { play(e) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), zoom = 1.01f) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                        NetImage(e.image.ifBlank { s.cover }, Modifier.fillMaxSize(), fallback = e.episode.toString())
                        if (r != null) LinearProgressIndicator(
                            progress = { if (r.finished) 1f else r.fraction }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                            color = Brand.Cyan, trackColor = Color(0x66000000),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${e.episode}. ${e.title}", fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (e.duration.isNotBlank()) Text(e.duration, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (e.plot.isNotBlank()) Text(e.plot, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
