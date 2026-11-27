package com.mohdev.faiptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.Movie
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.data.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Results(val live: List<Live> = emptyList(), val movies: List<Movie> = emptyList(), val series: List<Series> = emptyList())

@Composable
fun SearchScreen(wide: Boolean) {
    val ctx = LocalContext.current
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: run { LoadingBox(); return }
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf(Results()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!UiEnv.isTv) runCatching { fr.requestFocus() } }
    LaunchedEffect(q, lib, Prefs.unlocked) {
        delay(300)
        val n = Repo.norm(q.trim())
        if (n.length < 2) { res = Results(); return@LaunchedEffect }
        res = withContext(Dispatchers.Default) {
            val ll = lockedCats(lib, acc.id, "live")
            val lm = lockedCats(lib, acc.id, "movie")
            val ls = lockedCats(lib, acc.id, "series")
            Results(
                lib.raw.live.asSequence().filter { it.categoryId !in ll && Repo.norm(it.name).contains(n) }.take(60).toList(),
                lib.raw.movies.asSequence().filter { it.categoryId !in lm && Repo.norm(it.name).contains(n) }.take(60).toList(),
                lib.raw.series.asSequence().filter { it.categoryId !in ls && Repo.norm(it.name).contains(n) }.take(60).toList(),
            )
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(fr),
        )
        val empty = res.live.isEmpty() && res.movies.isEmpty() && res.series.isEmpty()
        if (empty && q.trim().length >= 2) { EmptyBox(stringResource(R.string.empty_list)); return@Column }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (res.live.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.nav_live) + " (${res.live.size})") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(res.live, key = { it.id }) { ch ->
                            ChannelTile(ch, onClick = { Nav.liveQueue = res.live; Nav.play(ctx, Repo.playLive(acc, ch)) })
                        }
                    }
                }
            }
            if (res.movies.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.nav_movies) + " (${res.movies.size})") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(res.movies, key = { it.id }) { m -> PosterCard(m.name, m.poster, onClick = { Nav.push(Route.MovieDetail(m)) }) }
                    }
                }
            }
            if (res.series.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.nav_series) + " (${res.series.size})") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(res.series, key = { it.id }) { s -> PosterCard(s.name, s.cover, onClick = { Nav.push(Route.SeriesDetail(s)) }) }
                    }
                }
            }
        }
    }
}
