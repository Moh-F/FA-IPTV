package com.mohdev.faiptv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.Repo
import kotlinx.coroutines.launch

@Composable
fun PosterCard(
    title: String, image: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    width: Dp = 120.dp, progress: Float? = null, onLongClick: (() -> Unit)? = null, badge: String? = null,
) {
    Column(modifier.width(width)) {
        FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f), zoom = 1.06f) {
            NetImage(image, Modifier.fillMaxSize(), fallback = title)
            badge?.let {
                Text(
                    it, fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Brand.Amber).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = Brand.Cyan, trackColor = Color(0x66000000),
                )
            }
        }
        Text(
            title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
fun ChannelTile(ch: Live, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    Column(modifier.width(150.dp)) {
        FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f), zoom = 1.06f) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A2147), Color(0xFF0E1430)))))
            NetImage(ch.logo, Modifier.fillMaxSize().padding(14.dp), ContentScale.Fit, fallback = ch.name)
        }
        Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun <T> Shelf(title: String, items: List<T>, item: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    SectionTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { item(it) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun HomeScreen(wide: Boolean) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: return
    val lib = Repo.lib
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!wide) {
                Image(painterResource(R.drawable.app_logo_plain), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("FA IPTV", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(acc.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
            if (!wide) IconButton(onClick = { Nav.push(Route.Sub(Tab.SEARCH)) }) { Icon(Icons.Default.Search, stringResource(R.string.nav_search)) }
            IconButton(onClick = { appScope.launch { Repo.refresh(acc) } }, enabled = !Repo.loading) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            }
        }
        if (Repo.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            lib == null && Repo.loading -> LoadingBox()
            lib == null -> EmptyBox(
                Repo.error?.let { stringResource(R.string.error_load, it) } ?: stringResource(R.string.empty_list),
                action = { Button(onClick = { appScope.launch { Repo.refresh(acc) } }) { Text(stringResource(R.string.retry)) } },
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                val resume = Prefs.data.resume.values.filter { it.accountId == acc.id && !it.finished && it.posMs > 30_000 && (it.key.startsWith("movie:") || it.key.startsWith("ep:")) }
                    .sortedByDescending { it.updated }.take(20)
                item {
                    Shelf(stringResource(R.string.home_continue), resume) { r ->
                        PosterCard(r.title, r.poster, onClick = {
                            if (r.kind == "MOVIE") lib.movieById[r.itemId]?.let { Nav.play(ctx, Repo.playMovie(acc, it)) }
                            else lib.seriesById[r.seriesId]?.let { Nav.push(Route.SeriesDetail(it)) }
                        }, progress = r.fraction)
                    }
                }
                val favs = Prefs.data.favorites[acc.id].orEmpty()
                item {
                    Shelf(stringResource(R.string.home_fav_channels), favs.filter { it.startsWith("live:") }.mapNotNull { lib.liveById[it.removePrefix("live:")] }) { ch ->
                        ChannelTile(ch, onClick = { Nav.liveQueue = favs.mapNotNull { k -> lib.liveById[k.removePrefix("live:")] }; Nav.play(ctx, Repo.playLive(acc, ch)) })
                    }
                }
                item {
                    val recent = Prefs.data.recentLive[acc.id].orEmpty().mapNotNull { lib.liveById[it] }
                    Shelf(stringResource(R.string.home_recent_channels), recent) { ch ->
                        ChannelTile(ch, onClick = { Nav.liveQueue = recent; Nav.play(ctx, Repo.playLive(acc, ch)) })
                    }
                }
                item {
                    val vodFav = favs.mapNotNull { k ->
                        when {
                            k.startsWith("movie:") -> lib.movieById[k.removePrefix("movie:")]?.let { Triple(it.name, it.poster) { Nav.push(Route.MovieDetail(it)) } }
                            k.startsWith("series:") -> lib.seriesById[k.removePrefix("series:")]?.let { Triple(it.name, it.cover) { Nav.push(Route.SeriesDetail(it)) } }
                            else -> null
                        }
                    }
                    Shelf(stringResource(R.string.home_fav_vod), vodFav) { (t, img, go) -> PosterCard(t, img, onClick = go) }
                }
                item {
                    Shelf(stringResource(R.string.home_new_movies), lockedCats(lib, acc.id, "movie").let { l -> lib.recentMovies.filter { it.categoryId !in l } }) { m ->
                        PosterCard(m.name, m.poster, onClick = { Nav.push(Route.MovieDetail(m)) })
                    }
                }
                item {
                    Shelf(stringResource(R.string.home_new_series), lockedCats(lib, acc.id, "series").let { l -> lib.recentSeries.filter { it.categoryId !in l } }) { s ->
                        PosterCard(s.name, s.cover, onClick = { Nav.push(Route.SeriesDetail(s)) })
                    }
                }
                if (lib.raw.live.isEmpty() && lib.raw.movies.isEmpty() && lib.raw.series.isEmpty()) {
                    item { Text(stringResource(R.string.acc_err_empty), Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center) }
                }
            }
        }
    }
}
