package com.mohdev.faiptv.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.UpdateInfo
import com.mohdev.faiptv.data.Account
import com.mohdev.faiptv.data.AccountType
import com.mohdev.faiptv.data.Episode
import com.mohdev.faiptv.data.Kind
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.Movie
import com.mohdev.faiptv.data.PlayRequest
import com.mohdev.faiptv.data.Series
import com.mohdev.faiptv.player.External

enum class Tab { HOME, LIVE, MOVIES, SERIES, SEARCH, RECORDINGS, ACCOUNTS, SETTINGS, MORE }

sealed interface Route {
    data class MovieDetail(val movie: Movie) : Route
    data class SeriesDetail(val series: Series) : Route
    data class Player(val req: PlayRequest) : Route
    data class AccountEdit(val account: Account?, val type: AccountType) : Route
    data class Sub(val tab: Tab) : Route
    data class KidsAllowed(val profileId: String) : Route
    data class Guide(val group: String) : Route
    data class Multi(val liveIds: List<String>) : Route
}

/** App-wide UI state: tab, back stack, dialogs. */
object Nav {
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Route>()
    val top: Route? get() = stack.lastOrNull()

    var askPlayer by mutableStateOf<PlayRequest?>(null)
    var pinThen by mutableStateOf<(() -> Unit)?>(null)
    var update by mutableStateOf<UpdateInfo?>(null)
    var showLog by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    /** account id to channel id that should start now (reminder / automatic start) */
    var pendingPlay by mutableStateOf<Pair<String, String>?>(null)
    var reminder by mutableStateOf<com.mohdev.faiptv.core.Reminder?>(null)
    /** "Who's watching?" screen visible */
    var pickProfile by mutableStateOf(false)

    /** Channels to zap through with up/down in the player. */
    var liveQueue: List<Live> = emptyList()
    /** Group (category / favourites list) the queue came from, for the in-player channel list. */
    var liveGroup: String = "*all"
    /** Episodes after the current one, for "next episode". */
    var episodeQueue: List<Pair<Series, Episode>> = emptyList()

    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun replaceTop(r: Route) { if (stack.isNotEmpty()) stack[stack.lastIndex] = r else stack.add(r) }

    fun go(t: Tab) { stack.clear(); tab = t }

    /** Runs [then] directly, or after the PIN when [locked] and not yet unlocked this session. */
    fun guarded(locked: Boolean, then: () -> Unit) {
        if (!locked || Prefs.unlocked) then() else pinThen = then
    }

    fun play(ctx: Context, req: PlayRequest, forceInternal: Boolean = false) {
        val acc = Prefs.active
        if (req.kind == Kind.LIVE && req.liveId != null && acc != null && !req.isCatchup) Prefs.pushRecent(acc.id, req.liveId)
        AppLog.i("play", "${req.kind} ${req.key}")
        when {
            forceInternal -> openInternal(req)
            Prefs.s.player == "external" -> External.open(ctx, req)
            Prefs.s.player == "ask" -> askPlayer = req
            else -> openInternal(req)
        }
    }

    fun openInternal(req: PlayRequest) {
        if (top is Route.Player) replaceTop(Route.Player(req)) else push(Route.Player(req))
    }
}
