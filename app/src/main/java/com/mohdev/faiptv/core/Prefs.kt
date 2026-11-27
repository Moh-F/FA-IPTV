package com.mohdev.faiptv.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mohdev.faiptv.data.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Device-local settings (not synced). */
@Serializable
data class AppSettings(
    val theme: String = "system",          // system | dark | light
    val palette: String = "navy",          // navy | gold | red
    val layout: String = "auto",           // auto | tv | phone
    val player: String = "internal",       // internal | external | ask
    val engine: String = "auto",           // auto (Exo, VLC on failure) | exo | vlc
    val deinterlace: Boolean = true,       // VLC
    val quality: String = "auto",          // auto | max | 2160 | 1080 | 720 | 480 | 360
    val fastStart: Boolean = true,
    val tunneling: Boolean = false,
    val externalPackage: String = "",
    val liveFormat: String = "ts",         // ts | m3u8
    val buffer: String = "normal",         // small | normal | large
    val autoPip: Boolean = true,
    val autoNext: Boolean = true,
    val userAgent: String = "",
    val pinHash: String = "",              // parental PIN (device)
    val autoLockAdult: Boolean = true,
    val checkUpdates: Boolean = true,
    val updateMode: String = "ask",        // ask | auto
    val showRibbon: Boolean = true,
    val lanServer: Boolean = false,
    val refreshHours: Int = 12,
    val epgShiftMin: Int = 0,
    val activeAccount: String = "",
    // info bar in the player
    val infoSeconds: Int = 5,              // 0 = stay visible
    val infoOpacity: Int = 75,             // 30..100 %
    val infoSize: String = "m",            // s | m | l
    val infoTop: Boolean = false,
    val infoTech: Boolean = true,
    val overlayOpacity: Int = 70,
    val zapDelayMs: Int = 400,
    /** channels that only play well with VLC (engine "auto" remembers them) */
    val vlcKeys: List<String> = emptyList(),
    // profiles
    val lastProfile: String = "",
    val askProfile: Boolean = true,
    // sync
    val syncPass: String = "",
    val syncLan: Boolean = true,
    val ghRepo: String = "",
    val ghToken: String = "",
    val deviceId: String = "",
)

@Serializable
data class Resume(
    val key: String,
    val accountId: String,
    val kind: String,
    val title: String,
    val poster: String = "",
    val posMs: Long,
    val durMs: Long,
    val updated: Long,
    val seriesId: String = "",
    val episodeId: String = "",
    val itemId: String = "",
) {
    val fraction get() = if (durMs > 0) posMs.toFloat() / durMs else 0f
    val finished get() = durMs > 0 && posMs > durMs * 0.95
}

@Serializable
data class ScheduledRec(
    val id: String,
    val accountId: String,
    val liveId: String,
    val channel: String,
    val title: String,
    val start: Long,
    val end: Long,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val color: Int = 0,
    val kids: Boolean = false,
    val pinHash: String = "",
    val updated: Long = 0,
)

/** A named favourites list ("live:<id>", "movie:<id>", "series:<id>" items, in user order). */
@Serializable
data class FavList(val id: String, val name: String, val accountId: String, val items: List<String> = emptyList(), val updated: Long = 0)

/** Per-account personalisation: hidden items, own names, own order. Keys: "live:<id>", "livecat:<id>", "moviecat:<id>"… */
@Serializable
data class AccountCustom(
    val hidden: List<String> = emptyList(),
    val renamed: Map<String, String> = emptyMap(),
    /** scope ("live:<catId>", "cats:live", "fav:<listId>"…) -> ordered keys */
    val order: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class ProfileData(
    val favLists: List<FavList> = emptyList(),
    val recentLive: Map<String, List<String>> = emptyMap(),
    val resume: Map<String, Resume> = emptyMap(),
    val locked: Map<String, List<String>> = emptyMap(),
    val custom: Map<String, AccountCustom> = emptyMap(),
    /** kids profile whitelist per account: "live:cat:<id>", "live:ch:<id>", "movie:cat:<id>", "series:cat:<id>" */
    val allowed: Map<String, List<String>> = emptyMap(),
    val updated: Long = 0,
)

@Serializable
data class UserData(
    val accounts: List<Account> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val pdata: Map<String, ProfileData> = emptyMap(),
    val scheduled: List<ScheduledRec> = emptyList(),
    /** deletions for sync: "acc:<id>", "prof:<id>", "fav:<id>" -> time */
    val tomb: Map<String, Long> = emptyMap(),
    // v1.0 fields, migrated into the first profile
    val favorites: Map<String, List<String>> = emptyMap(),
    val recentLive: Map<String, List<String>> = emptyMap(),
    val resume: Map<String, Resume> = emptyMap(),
    val locked: Map<String, List<String>> = emptyMap(),
)

val profileColors = listOf(0xFF2DD4BF, 0xFF38BDF8, 0xFFF472B6, 0xFFF59E0B, 0xFFA78BFA, 0xFF34D399, 0xFFF87171, 0xFF60A5FA)

/** Settings + user data, observable from Compose, persisted as JSON in app-private storage. */
object Prefs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private var saveSettingsJob: Job? = null
    private var saveDataJob: Job? = null

    var s by mutableStateOf(AppSettings())
        private set
    var data by mutableStateOf(UserData())
        private set

    /** Profile chosen for this session. */
    var profileId by mutableStateOf("")
        private set

    /** PIN unlocked for this session. */
    var unlocked by mutableStateOf(false)

    /** Called after every synced-data change (used to trigger sync). */
    var onChange: (() -> Unit)? = null

    fun now() = System.currentTimeMillis()

    fun init(ctx: Context) {
        dir = ctx.filesDir
        runCatching { File(dir, "settings.json").takeIf { it.exists() }?.readText()?.let { s = json.decodeFromString(it) } }
            .onFailure { AppLog.e("prefs", "settings unreadable", it) }
        runCatching { File(dir, "userdata.json").takeIf { it.exists() }?.readText()?.let { data = json.decodeFromString(it) } }
            .onFailure { AppLog.e("prefs", "user data unreadable", it) }
        if (s.deviceId.isBlank()) update { it.copy(deviceId = UUID.randomUUID().toString().take(8)) }
        migrate()
        profileId = data.profiles.firstOrNull { it.id == s.lastProfile }?.id ?: data.profiles.first().id
    }

    /** v1.0 -> v1.1: create the first profile and move favourites/history into it. */
    private fun migrate() {
        if (data.profiles.isNotEmpty()) return
        val p = Profile(UUID.randomUUID().toString(), "", profileColors[0].toInt(), updated = now())
        val lists = data.favorites.filterValues { it.isNotEmpty() }.map { (acc, items) ->
            FavList(UUID.randomUUID().toString(), "", acc, items, now())
        }
        val pd = ProfileData(favLists = lists, recentLive = data.recentLive, resume = data.resume, locked = data.locked, updated = now())
        data = data.copy(
            profiles = listOf(p), pdata = mapOf(p.id to pd),
            favorites = emptyMap(), recentLive = emptyMap(), resume = emptyMap(), locked = emptyMap(),
        )
        saveData()
    }

    fun update(f: (AppSettings) -> AppSettings) {
        s = f(s)
        saveSettingsJob?.cancel()
        saveSettingsJob = scope.launch { delay(300); write("settings.json", json.encodeToString(AppSettings.serializer(), s)) }
    }

    /** Changes user data; [sync] = this change should be shared with other devices. */
    fun edit(sync: Boolean = true, f: (UserData) -> UserData) {
        synchronized(this) { data = f(data) }
        saveData()
        if (sync) onChange?.invoke()
    }

    /** Replaces synced parts after a merge (no change event, avoids sync loops). */
    fun applySynced(accounts: List<Account>, profiles: List<Profile>, pdata: Map<String, ProfileData>, tomb: Map<String, Long>) {
        synchronized(this) { data = data.copy(accounts = accounts, profiles = profiles, pdata = pdata, tomb = tomb) }
        saveData()
        if (data.profiles.isEmpty()) migrate()
        if (data.profiles.none { it.id == profileId }) profileId = data.profiles.first().id
    }

    private fun saveData() {
        saveDataJob?.cancel()
        saveDataJob = scope.launch { delay(500); write("userdata.json", json.encodeToString(UserData.serializer(), data)) }
    }

    fun flush() {
        runCatching {
            write("settings.json", json.encodeToString(AppSettings.serializer(), s))
            write("userdata.json", json.encodeToString(UserData.serializer(), data))
        }
    }

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }

    // ---------------------------------------------------------------- accounts
    val accounts get() = data.accounts
    val active: Account? get() = data.accounts.firstOrNull { it.id == s.activeAccount } ?: data.accounts.firstOrNull()

    fun saveAccount(a: Account) {
        val stamped = a.copy(updated = now())
        edit { d ->
            val list = d.accounts.toMutableList()
            val i = list.indexOfFirst { it.id == a.id }
            if (i >= 0) list[i] = stamped else list += stamped
            d.copy(accounts = list, tomb = d.tomb - "acc:${a.id}")
        }
        if (s.activeAccount.isBlank() || data.accounts.size == 1) update { it.copy(activeAccount = a.id) }
    }

    fun deleteAccount(id: String) {
        edit { d ->
            d.copy(
                accounts = d.accounts.filterNot { it.id == id },
                pdata = d.pdata.mapValues { (_, p) ->
                    p.copy(
                        favLists = p.favLists.filterNot { it.accountId == id }, recentLive = p.recentLive - id,
                        locked = p.locked - id, custom = p.custom - id, allowed = p.allowed - id,
                        resume = p.resume.filterValues { it.accountId != id }, updated = now(),
                    )
                },
                scheduled = d.scheduled.filterNot { it.accountId == id },
                tomb = d.tomb + ("acc:$id" to now()),
            )
        }
        if (s.activeAccount == id) update { it.copy(activeAccount = data.accounts.firstOrNull()?.id ?: "") }
    }

    // ---------------------------------------------------------------- profiles
    val profiles get() = data.profiles
    val profile: Profile get() = data.profiles.firstOrNull { it.id == profileId } ?: data.profiles.first()
    val isKids get() = profile.kids
    val pd: ProfileData get() = data.pdata[profileId] ?: ProfileData()

    fun selectProfile(id: String) {
        profileId = id
        unlocked = false
        update { it.copy(lastProfile = id) }
    }

    fun saveProfile(p: Profile) = edit { d ->
        val list = d.profiles.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        val stamped = p.copy(updated = now())
        if (i >= 0) list[i] = stamped else list += stamped
        d.copy(profiles = list, tomb = d.tomb - "prof:${p.id}")
    }

    fun deleteProfile(id: String) {
        if (data.profiles.size <= 1) return
        edit { d -> d.copy(profiles = d.profiles.filterNot { it.id == id }, pdata = d.pdata - id, tomb = d.tomb + ("prof:$id" to now())) }
        if (profileId == id) selectProfile(data.profiles.first().id)
    }

    fun editPd(f: (ProfileData) -> ProfileData) = edit { d ->
        d.copy(pdata = d.pdata + (profileId to f(d.pdata[profileId] ?: ProfileData()).copy(updated = now())))
    }

    // ---------------------------------------------------------------- favourites lists
    fun favLists(acc: String) = pd.favLists.filter { it.accountId == acc }
    fun favKeys(acc: String): List<String> = favLists(acc).flatMap { it.items }.distinct()
    fun isFav(acc: String, key: String) = favLists(acc).any { key in it.items }

    /** Adds to / removes from the first list (created on demand). */
    fun toggleFav(acc: String, key: String) {
        val lists = favLists(acc)
        if (lists.any { key in it.items }) {
            editPd { p -> p.copy(favLists = p.favLists.map { if (it.accountId == acc) it.copy(items = it.items - key, updated = now()) else it }) }
        } else {
            val first = lists.firstOrNull()
            if (first == null) newFavList(acc, "", listOf(key)) else addToList(first.id, key)
        }
    }

    fun newFavList(acc: String, name: String, items: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        editPd { p -> p.copy(favLists = p.favLists + FavList(id, name, acc, items, now())) }
        return id
    }

    private fun editList(id: String, f: (FavList) -> FavList) =
        editPd { p -> p.copy(favLists = p.favLists.map { if (it.id == id) f(it).copy(updated = now()) else it }) }

    fun renameList(id: String, name: String) = editList(id) { it.copy(name = name) }
    fun addToList(id: String, key: String) = editList(id) { if (key in it.items) it else it.copy(items = it.items + key) }
    fun removeFromList(id: String, key: String) = editList(id) { it.copy(items = it.items - key) }
    fun moveInList(id: String, key: String, delta: Int) = editList(id) { it.copy(items = moved(it.items, key, delta)) }

    fun deleteList(id: String) = edit { d ->
        d.copy(
            pdata = d.pdata + (profileId to pd.copy(favLists = pd.favLists.filterNot { it.id == id }, updated = now())),
            tomb = d.tomb + ("fav:$id" to now()),
        )
    }

    fun moveList(id: String, delta: Int) = editPd { p ->
        val ids = moved(p.favLists.map { it.id }, id, delta)
        p.copy(favLists = ids.mapNotNull { i -> p.favLists.firstOrNull { it.id == i } })
    }

    fun <T> moved(list: List<T>, item: T, delta: Int): List<T> {
        val i = list.indexOf(item)
        if (i < 0) return list
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i == j) return list
        val m = list.toMutableList()
        m.removeAt(i)
        m.add(j, item)
        return m
    }

    // ---------------------------------------------------------------- personalisation (hide / rename / order)
    fun custom(acc: String) = pd.custom[acc] ?: AccountCustom()
    private fun editCustom(acc: String, f: (AccountCustom) -> AccountCustom) = editPd { p -> p.copy(custom = p.custom + (acc to f(p.custom[acc] ?: AccountCustom()))) }

    fun isHidden(acc: String, key: String) = key in custom(acc).hidden
    fun toggleHidden(acc: String, key: String) = editCustom(acc) { c -> c.copy(hidden = if (key in c.hidden) c.hidden - key else c.hidden + key) }
    fun unhideAll(acc: String) = editCustom(acc) { it.copy(hidden = emptyList()) }
    fun nameOf(acc: String, key: String, default: String) = custom(acc).renamed[key]?.takeIf { it.isNotBlank() } ?: default
    fun rename(acc: String, key: String, name: String) = editCustom(acc) { c ->
        c.copy(renamed = if (name.isBlank()) c.renamed - key else c.renamed + (key to name.trim()))
    }

    /** Applies the user's order for [scope] to [items] (unknown items keep their place after the ordered ones). */
    fun <T> ordered(acc: String, scope: String, items: List<T>, key: (T) -> String): List<T> {
        val order = custom(acc).order[scope] ?: return items
        if (order.isEmpty()) return items
        val pos = order.withIndex().associate { it.value to it.index }
        return items.withIndex().sortedWith(compareBy({ pos[key(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
    }

    /** Moves [k] by [delta] inside the currently shown order [current]. */
    fun move(acc: String, scope: String, current: List<String>, k: String, delta: Int) =
        editCustom(acc) { c -> c.copy(order = c.order + (scope to moved(current, k, delta))) }

    fun resetOrder(acc: String, scope: String) = editCustom(acc) { c -> c.copy(order = c.order - scope) }

    // ---------------------------------------------------------------- kids whitelist
    fun allowed(acc: String): Set<String> = pd.allowed[acc].orEmpty().toSet()
    fun toggleAllowed(profile: String, acc: String, key: String) = edit { d ->
        val p = d.pdata[profile] ?: ProfileData()
        val cur = p.allowed[acc].orEmpty()
        d.copy(pdata = d.pdata + (profile to p.copy(allowed = p.allowed + (acc to (if (key in cur) cur - key else cur + key)), updated = now())))
    }
    fun allowedOf(profile: String, acc: String): Set<String> = data.pdata[profile]?.allowed?.get(acc).orEmpty().toSet()

    // ---------------------------------------------------------------- recent / resume
    fun pushRecent(acc: String, liveId: String) = editPd { p ->
        val cur = p.recentLive[acc].orEmpty()
        p.copy(recentLive = p.recentLive + (acc to (listOf(liveId) + (cur - liveId)).take(30)))
    }

    fun saveResume(r: Resume) = editPd { p ->
        val m = p.resume + (r.key to r)
        p.copy(resume = if (m.size > 300) m.values.sortedByDescending { it.updated }.take(300).associateBy { it.key } else m)
    }

    fun clearResume(key: String) = editPd { p -> p.copy(resume = p.resume - key) }

    // ---------------------------------------------------------------- parental lock
    fun hasPin() = s.pinHash.isNotBlank()
    fun checkPin(pin: String) = hash(pin) == s.pinHash
    fun setPin(pin: String) = update { it.copy(pinHash = if (pin.isBlank()) "" else hash(pin)) }
    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("fa-iptv:$pin".toByteArray()).joinToString("") { "%02x".format(java.util.Locale.US, it) }

    fun isLockedManually(acc: String, key: String) = pd.locked[acc]?.contains(key) == true
    fun toggleLock(acc: String, key: String) = editPd { p ->
        val cur = p.locked[acc].orEmpty()
        p.copy(locked = p.locked + (acc to (if (key in cur) cur - key else cur + key)))
    }

    private val adult = listOf("xxx", "adult", "18+", "+18", "porn", "للكبار", "erwachsene", "for adults", "sex")
    fun looksAdult(name: String): Boolean {
        val n = name.lowercase()
        return adult.any { n.contains(it) }
    }

    fun isLocked(acc: String, kind: String, catId: String, catName: String): Boolean =
        isLockedManually(acc, "$kind:$catId") || (s.autoLockAdult && looksAdult(catName))

    // ---------------------------------------------------------------- scheduled recordings (device-local)
    fun addScheduled(r: ScheduledRec) = edit(sync = false) { it.copy(scheduled = it.scheduled + r) }
    fun removeScheduled(id: String) = edit(sync = false) { d -> d.copy(scheduled = d.scheduled.filterNot { it.id == id }) }
}
