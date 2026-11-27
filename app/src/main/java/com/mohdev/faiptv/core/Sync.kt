package com.mohdev.faiptv.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mohdev.faiptv.data.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class Peer(val name: String, val host: String, val port: Int)

/**
 * Keeps profiles, favourites lists, order/names, history and accounts the same on all devices:
 * - home network: devices find each other (mDNS) and exchange the encrypted document directly
 * - GitHub: the encrypted document is stored in a private repository of the user
 * Everything is end-to-end encrypted with the user's sync pass phrase.
 */
object Sync {
    private const val TYPE = "_faiptv._tcp."
    private const val GH_PATH = "fa-iptv-sync.bin"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private lateinit var app: Context
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private var debounce: Job? = null
    private var myServiceName = ""
    private var started = false
    private var lastGitHub = 0L

    val peers = mutableStateListOf<Peer>()
    var status by mutableStateOf("")
        private set
    var lastSync by mutableLongStateOf(0L)
        private set
    var running by mutableStateOf(false)
        private set

    val enabled get() = Prefs.s.syncPass.length >= 8

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Prefs.onChange = { schedule() }
    }

    fun localDoc() = Prefs.data.let { SyncDoc(1, it.accounts, it.profiles, it.pdata, it.tomb, System.currentTimeMillis()) }

    private suspend fun apply(merged: SyncDoc) = withContext(Dispatchers.Main) {
        Prefs.applySynced(merged.accounts, merged.profiles, merged.pdata, merged.tomb)
    }

    /** Called on the server side (LanServer) for POST /api/sync. Returns the encrypted merged document. */
    fun handleIncoming(keyId: String?, body: ByteArray): ByteArray? {
        if (!enabled || !Prefs.s.syncLan) return null
        val pass = Prefs.s.syncPass
        if (keyId != SyncCrypto.keyId(pass)) return null
        val remote = runCatching { SyncCrypto.open(body, pass) }.getOrNull() ?: return null
        val merged = SyncMerge.merge(localDoc(), remote)
        kotlinx.coroutines.runBlocking { apply(merged) }
        AppLog.i("sync", "merged from a device in the home network")
        lastSync = System.currentTimeMillis()
        return SyncCrypto.seal(merged, pass)
    }

    /** Debounced sync after local changes. */
    fun schedule() {
        if (!enabled) return
        debounce?.cancel()
        debounce = scope.launch { delay(15_000); syncNow() }
    }

    fun start() {
        if (!enabled || started) return
        started = true
        if (Prefs.s.syncLan) startLan()
        scope.launch {
            delay(3000)
            syncNow()
            while (true) { delay(20 * 60_000L); syncNow() }
        }
    }

    suspend fun syncNow(manual: Boolean = false): Boolean = mutex.withLock {
        if (!enabled) return false
        running = true
        var ok = false
        try {
            val pass = Prefs.s.syncPass
            if (Prefs.s.syncLan) {
                for (p in peers.toList()) {
                    runCatching { syncPeer(p, pass) }.onSuccess { ok = true }
                        .onFailure { AppLog.w("sync", "peer ${p.name}: ${it.message}") }
                }
            }
            // GitHub: at most every 10 minutes (each change is a commit), always when asked
            val ghDue = manual || System.currentTimeMillis() - lastGitHub > 10 * 60_000L
            if (ghDue && Prefs.s.ghRepo.isNotBlank() && Prefs.s.ghToken.isNotBlank()) {
                lastGitHub = System.currentTimeMillis()
                runCatching { syncGitHub(pass) }.onSuccess { ok = true }
                    .onFailure { AppLog.w("sync", "github: ${it.message}"); status = "GitHub: ${it.message}" }
            }
            if (ok) { lastSync = System.currentTimeMillis(); status = "" }
        } finally {
            running = false
        }
        ok
    }

    private suspend fun syncPeer(p: Peer, pass: String) {
        val body = SyncCrypto.seal(localDoc(), pass)
        val req = Request.Builder().url("http://${p.host}:${p.port}/api/sync")
            .header("X-Sync-Key", SyncCrypto.keyId(pass))
            .post(body.toRequestBody("application/octet-stream".toMediaType())).build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Net.HttpError(r.code)
            val merged = SyncCrypto.open(r.body!!.bytes(), pass)
            apply(SyncMerge.merge(localDoc(), merged))
        }
        AppLog.i("sync", "synced with ${p.name}")
    }

    // ------------------------------------------------------------------ GitHub (private repo of the user)
    private fun gh(path: String) = Request.Builder().url("https://api.github.com/repos/${Prefs.s.ghRepo.trim()}/contents/$path")
        .header("Authorization", "Bearer ${Prefs.s.ghToken.trim()}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun syncGitHub(pass: String) {
        var sha: String? = null
        var remote: SyncDoc? = null
        Net.client.newCall(gh(GH_PATH).get().build()).execute().use { r ->
            when (r.code) {
                200 -> {
                    val o = JSONObject(r.body!!.string())
                    sha = o.getString("sha")
                    val bytes = Base64.decode(o.getString("content").replace("\n", ""), Base64.DEFAULT)
                    remote = SyncCrypto.open(bytes, pass)
                }
                404 -> Unit
                401, 403 -> throw IllegalStateException("token/permission (HTTP ${r.code})")
                else -> throw Net.HttpError(r.code)
            }
        }
        val merged = remote?.let { SyncMerge.merge(localDoc(), it) } ?: localDoc()
        apply(merged)
        if (remote != null && same(remote!!, merged)) { AppLog.i("sync", "GitHub already up to date"); return }
        val body = JSONObject()
            .put("message", "FA IPTV sync ${Build.MODEL}")
            .put("content", Base64.encodeToString(SyncCrypto.seal(merged, pass), Base64.NO_WRAP))
        sha?.let { body.put("sha", it) }
        Net.client.newCall(gh(GH_PATH).put(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
            if (!r.isSuccessful) throw Net.HttpError(r.code)
        }
        AppLog.i("sync", "synced with GitHub")
    }

    private fun same(a: SyncDoc, b: SyncDoc) = a.copy(at = 0) == b.copy(at = 0)

    // ------------------------------------------------------------------ home network discovery
    fun startLan() {
        if (!LanServer.running) LanServer.start(app)
        val port = LanServer.port
        if (port <= 0) return
        val mgr = nsd ?: (app.getSystemService(Context.NSD_SERVICE) as NsdManager).also { nsd = it }
        if (regListener == null) {
            val info = NsdServiceInfo().apply {
                serviceName = "FA-IPTV-${Build.MODEL.take(20)}-${Prefs.s.deviceId}"
                serviceType = TYPE
                setPort(port)
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) { myServiceName = i.serviceName }
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { AppLog.w("sync", "nsd register failed $e") }
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            runCatching { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, l); regListener = l }
        }
        if (discListener == null) {
            val d = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) {}
                override fun onDiscoveryStopped(t: String) {}
                override fun onStartDiscoveryFailed(t: String, e: Int) { AppLog.w("sync", "discovery failed $e") }
                override fun onStopDiscoveryFailed(t: String, e: Int) {}
                override fun onServiceFound(i: NsdServiceInfo) {
                    if (i.serviceName == myServiceName || i.serviceName.endsWith(Prefs.s.deviceId)) return
                    @Suppress("DEPRECATION")
                    runCatching {
                        mgr.resolveService(i, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                            override fun onServiceResolved(s: NsdServiceInfo) {
                                val host = s.host?.hostAddress ?: return
                                val peer = Peer(s.serviceName.removePrefix("FA-IPTV-").substringBeforeLast('-'), host, s.port)
                                scope.launch(Dispatchers.Main) {
                                    peers.removeAll { it.name == peer.name || it.host == peer.host }
                                    peers.add(peer)
                                }
                                schedule()
                            }
                        })
                    }
                }
                override fun onServiceLost(i: NsdServiceInfo) {
                    val n = i.serviceName.removePrefix("FA-IPTV-").substringBeforeLast('-')
                    scope.launch(Dispatchers.Main) { peers.removeAll { it.name == n } }
                }
            }
            runCatching { mgr.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, d); discListener = d }
        }
    }

    fun stopLan() {
        runCatching { regListener?.let { nsd?.unregisterService(it) } }
        runCatching { discListener?.let { nsd?.stopServiceDiscovery(it) } }
        regListener = null
        discListener = null
        peers.clear()
    }
}
