package com.mohdev.faiptv.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import com.mohdev.faiptv.data.Account
import com.mohdev.faiptv.data.AccountType
import com.mohdev.faiptv.data.Xtream
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * LAN-only page: add accounts from the phone (typing on a TV remote is painful) + live error log.
 * Formerly: LAN-only control + log page. OFF by default. Reachable only from private IP ranges and only with the
 * one-time token shown on the TV. Plain HTTP on the home network; never expose it to the internet.
 */
object LanServer {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "lan").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private var token = ""
    private var page = ""

    /** Shown in the settings panel while running. */
    var url by mutableStateOf("")
        private set

    val running get() = server != null
    val port get() = server?.localPort ?: 0

    fun start(ctx: Context) {
        if (server != null) return
        runCatching {
            val rnd = SecureRandom()
            token = (1..20).map { "abcdefghijklmnopqrstuvwxyz0123456789"[rnd.nextInt(36)] }.joinToString("")
            page = ctx.assets.open("panel.html").bufferedReader().use { it.readText() }
            val s = ServerSocket(0)
            server = s
            url = "http://${localIp() ?: "?"}:${s.localPort}/?token=$token"
            pool.execute { acceptLoop(s) }
            AppLog.i("lan", "started on port ${s.localPort}")
        }.onFailure { AppLog.e("lan", "start failed", it); stop() }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        url = ""
        AppLog.i("lan", "stopped")
    }

    /** http://ip:port of this device in the home network, or null. */
    fun base(): String? {
        val p = port
        val ip = localIp() ?: return null
        return if (p > 0) "http://$ip:$p" else null
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = runCatching { s.accept() }.getOrNull() ?: break
            pool.execute { runCatching { handle(c) }.onFailure { AppLog.d("lan", "conn: ${it.message}") }; runCatching { c.close() } }
        }
    }

    private fun readLine(i: BufferedInputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = i.read()
            if (c < 0) return if (b.size() == 0) null else b.toString("ISO-8859-1")
            if (c == '\n'.code) return b.toString("ISO-8859-1").trimEnd('\r')
            if (b.size() > 8192) throw IllegalStateException("line too long")
            b.write(c)
        }
    }

    private fun send(o: OutputStream, code: Int, type: String, body: ByteArray, extra: String = "") {
        val head = "HTTP/1.1 $code OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n$extra" + "Connection: close\r\n\r\n"
        o.write(head.toByteArray(Charsets.ISO_8859_1)); o.write(body); o.flush()
    }

    private fun tokenOk(given: String?): Boolean =
        given != null && MessageDigest.isEqual(given.toByteArray(), token.toByteArray())

    private fun handle(sock: Socket) {
        sock.soTimeout = 15_000
        val addr: InetAddress = sock.inetAddress
        val out = sock.getOutputStream()
        if (!(addr.isLoopbackAddress || addr.isSiteLocalAddress)) {
            send(out, 403, "text/plain", "Forbidden".toByteArray()); return
        }
        val input = BufferedInputStream(sock.getInputStream())
        val reqLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val parts = reqLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split("&").filter { it.contains("=") }
            .associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > (if (path == "/api/sync") 8_000_000 else 65_536)) { send(out, 413, "text/plain", "Too large".toByteArray()); return }
        val body = ByteArray(len)
        var read = 0
        while (read < len) { val n = input.read(body, read, len - read); if (n < 0) break; read += n }

        // device-to-device sync: authorised by the shared, encrypted pass phrase instead of the page token
        if (method == "POST" && path == "/api/sync") {
            val reply = Sync.handleIncoming(headers["x-sync-key"], body)
            if (reply == null) send(out, 403, "text/plain", "Forbidden".toByteArray())
            else send(out, 200, "application/octet-stream", reply)
            return
        }
        // text from the phone for one open field: authorised by the field's own random code
        if (path == "/in") {
            val k = query["k"].orEmpty()
            val html = "text/html; charset=utf-8"
            if (method == "POST") {
                val v = String(body, Charsets.UTF_8).split("&").firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                if (v != null && RemoteInput.submit(k, v)) send(out, 200, html, "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><body style='font-family:sans-serif;background:#080D18;color:#2EE6CF;font-size:22px;padding:30px'>✓".toByteArray())
                else send(out, 410, "text/plain", "Closed".toByteArray())
            } else {
                val p = RemoteInput.page(k)
                if (p != null) send(out, 200, html, p.toByteArray()) else send(out, 410, "text/plain", "Closed".toByteArray())
            }
            return
        }
        if (!tokenOk(query["token"] ?: headers["x-token"])) {
            send(out, 403, "text/plain", "Forbidden".toByteArray()); return
        }
        val json = "application/json; charset=utf-8"
        when {
            method == "GET" && path == "/" -> send(out, 200, "text/html; charset=utf-8", page.toByteArray())
            method == "GET" && path == "/api/accounts" -> {
                val arr = org.json.JSONArray()
                Prefs.accounts.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name).put("active", it.id == Prefs.active?.id)) }
                send(out, 200, json, arr.toString().toByteArray())
            }
            method == "POST" && path == "/api/account" -> {
                val o = runCatching { JSONObject(String(body, Charsets.UTF_8)) }.getOrNull()
                if (o == null) { send(out, 400, "text/plain", "Bad JSON".toByteArray()); return }
                val acc = accountFrom(o)
                if (acc == null) { send(out, 400, "text/plain", "Missing fields".toByteArray()); return }
                val latch = java.util.concurrent.CountDownLatch(1)
                main.post {
                    Prefs.saveAccount(acc.copy(owner = Prefs.profileId))
                    Prefs.update { it.copy(activeAccount = acc.id) }
                    com.mohdev.faiptv.ui.Nav.go(com.mohdev.faiptv.ui.Tab.HOME)
                    com.mohdev.faiptv.ui.appScope.launch { com.mohdev.faiptv.data.Repo.activate(acc, forceRefresh = true) }
                    latch.countDown()
                }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                AppLog.i("lan", "account added from phone (${acc.type})")
                send(out, 200, json, JSONObject().put("ok", true).put("id", acc.id).toString().toByteArray())
            }
            method == "GET" && path == "/api/log" ->
                send(out, 200, json, org.json.JSONArray(AppLog.recent()).toString().toByteArray())
            method == "GET" && path == "/download" ->
                send(out, 200, "text/plain; charset=utf-8", AppLog.readAll().toByteArray(), "Content-Disposition: attachment; filename=\"fa-iptv.log\"\r\n")
            method == "GET" && path == "/stream" -> stream(sock, out)
            else -> send(out, 404, "text/plain", "Not found".toByteArray())
        }
    }

    private fun accountFrom(o: JSONObject): Account? {
        val type = if (o.optString("type").equals("m3u", true)) AccountType.M3U else AccountType.XTREAM
        var server = o.optString("server").trim()
        var user = o.optString("username").trim()
        var pass = o.optString("password").trim()
        val url = o.optString("m3uUrl").trim()
        var t = type
        if (t == AccountType.M3U) Xtream.fromM3uLink(url)?.let { c -> t = AccountType.XTREAM; server = c.server; user = c.user; pass = c.pass }
        if (t == AccountType.XTREAM && (server.isBlank() || user.isBlank())) return null
        if (t == AccountType.M3U && url.isBlank()) return null
        val norm = if (t == AccountType.XTREAM) Xtream.normalizeServer(server) else ""
        return Account(
            id = UUID.randomUUID().toString(),
            name = o.optString("name").trim().ifBlank { if (t == AccountType.XTREAM) norm.substringAfter("://").substringBefore(":") else "M3U" },
            type = t, server = norm, username = user, password = pass,
            m3uUrl = if (t == AccountType.M3U) url else "", epgUrl = o.optString("epgUrl").trim(), userAgent = o.optString("userAgent").trim(),
        )
    }

    private fun stream(sock: Socket, out: OutputStream) {
        sock.soTimeout = 0
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\n\r\n").toByteArray())
        val q = java.util.concurrent.LinkedBlockingQueue<String>()
        val l: (String) -> Unit = { q.offer(it) }
        AppLog.recent().forEach { q.offer(it) }
        AppLog.addListener(l)
        try {
            while (!sock.isClosed && server != null) {
                val line = q.poll(15, java.util.concurrent.TimeUnit.SECONDS)
                out.write((if (line != null) "data: $line\n\n" else ": keep-alive\n\n").toByteArray())
                out.flush()
            }
        } finally {
            AppLog.removeListener(l)
        }
    }
}
