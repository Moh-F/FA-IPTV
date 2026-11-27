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

@Serializable
data class AppSettings(
    val theme: String = "system",          // system | dark | light
    val layout: String = "auto",           // auto | tv | phone
    val player: String = "internal",       // internal | external | ask
    val externalPackage: String = "",      // "" = chooser
    val liveFormat: String = "ts",         // ts | m3u8
    val buffer: String = "normal",         // small | normal | large
    val autoPip: Boolean = true,
    val autoNext: Boolean = true,
    val userAgent: String = "",
    val pinHash: String = "",
    val autoLockAdult: Boolean = true,
    val checkUpdates: Boolean = true,
    val updateMode: String = "ask",        // ask | auto
    val showRibbon: Boolean = true,
    val lanServer: Boolean = false,
    val refreshHours: Int = 12,
    val epgShiftMin: Int = 0,
    val activeAccount: String = "",
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
data class UserData(
    val accounts: List<Account> = emptyList(),
    /** accountId -> keys like "live:123", "movie:9", "series:4" */
    val favorites: Map<String, List<String>> = emptyMap(),
    /** accountId -> recently played live ids, newest first */
    val recentLive: Map<String, List<String>> = emptyMap(),
    val resume: Map<String, Resume> = emptyMap(),
    /** accountId -> "live:catId" / "movie:catId" / "series:catId" */
    val locked: Map<String, List<String>> = emptyMap(),
    val scheduled: List<ScheduledRec> = emptyList(),
)

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

    /** PIN unlocked for this session. */
    var unlocked by mutableStateOf(false)

    fun init(ctx: Context) {
        dir = ctx.filesDir
        runCatching { File(dir, "settings.json").takeIf { it.exists() }?.readText()?.let { s = json.decodeFromString(it) } }
            .onFailure { AppLog.e("prefs", "settings unreadable", it) }
        runCatching { File(dir, "userdata.json").takeIf { it.exists() }?.readText()?.let { data = json.decodeFromString(it) } }
            .onFailure { AppLog.e("prefs", "user data unreadable", it) }
    }

    fun update(f: (AppSettings) -> AppSettings) {
        s = f(s)
        saveSettingsJob?.cancel()
        saveSettingsJob = scope.launch { delay(300); write("settings.json", json.encodeToString(AppSettings.serializer(), s)) }
    }

    fun edit(f: (UserData) -> UserData) {
        synchronized(this) { data = f(data) }
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

    // ---- accounts
    val accounts get() = data.accounts
    val active: Account? get() = data.accounts.firstOrNull { it.id == s.activeAccount } ?: data.accounts.firstOrNull()

    fun saveAccount(a: Account) {
        edit { d ->
            val list = d.accounts.toMutableList()
            val i = list.indexOfFirst { it.id == a.id }
            if (i >= 0) list[i] = a else list += a
            d.copy(accounts = list)
        }
        if (s.activeAccount.isBlank() || data.accounts.size == 1) update { it.copy(activeAccount = a.id) }
    }

    fun deleteAccount(id: String) {
        edit { d ->
            d.copy(
                accounts = d.accounts.filterNot { it.id == id },
                favorites = d.favorites - id, recentLive = d.recentLive - id, locked = d.locked - id,
                resume = d.resume.filterValues { it.accountId != id },
                scheduled = d.scheduled.filterNot { it.accountId == id },
            )
        }
        if (s.activeAccount == id) update { it.copy(activeAccount = data.accounts.firstOrNull()?.id ?: "") }
    }

    // ---- favorites / recent / resume
    fun isFav(acc: String, key: String) = data.favorites[acc]?.contains(key) == true
    fun toggleFav(acc: String, key: String) = edit { d ->
        val cur = d.favorites[acc].orEmpty()
        d.copy(favorites = d.favorites + (acc to (if (key in cur) cur - key else listOf(key) + cur)))
    }

    fun pushRecent(acc: String, liveId: String) = edit { d ->
        val cur = d.recentLive[acc].orEmpty()
        d.copy(recentLive = d.recentLive + (acc to (listOf(liveId) + (cur - liveId)).take(30)))
    }

    fun saveResume(r: Resume) = edit { d ->
        val m = d.resume + (r.key to r)
        // keep the newest 300 entries
        d.copy(resume = if (m.size > 300) m.values.sortedByDescending { it.updated }.take(300).associateBy { it.key } else m)
    }

    fun clearResume(key: String) = edit { d -> d.copy(resume = d.resume - key) }

    // ---- parental lock
    fun hasPin() = s.pinHash.isNotBlank()
    fun checkPin(pin: String) = hash(pin) == s.pinHash
    fun setPin(pin: String) = update { it.copy(pinHash = if (pin.isBlank()) "" else hash(pin)) }
    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("fa-iptv:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun isLockedManually(acc: String, key: String) = data.locked[acc]?.contains(key) == true
    fun toggleLock(acc: String, key: String) = edit { d ->
        val cur = d.locked[acc].orEmpty()
        d.copy(locked = d.locked + (acc to (if (key in cur) cur - key else cur + key)))
    }

    private val adult = listOf("xxx", "adult", "18+", "+18", "porn", "للكبار", "erwachsene", "for adults", "sex")
    fun looksAdult(name: String): Boolean {
        val n = name.lowercase()
        return adult.any { n.contains(it) }
    }

    /** A category is locked if the user locked it, or it looks adult and auto-lock is on. */
    fun isLocked(acc: String, kind: String, catId: String, catName: String): Boolean =
        isLockedManually(acc, "$kind:$catId") || (s.autoLockAdult && looksAdult(catName) && !isLockedManually(acc, "$kind:$catId:open"))

    // ---- scheduled recordings
    fun addScheduled(r: ScheduledRec) = edit { it.copy(scheduled = it.scheduled + r) }
    fun removeScheduled(id: String) = edit { d -> d.copy(scheduled = d.scheduled.filterNot { it.id == id }) }
}
