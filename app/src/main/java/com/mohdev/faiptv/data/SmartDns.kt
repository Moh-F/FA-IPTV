package com.mohdev.faiptv.data

import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Name resolution that also works when the internet provider's DNS blocks or does not know an IPTV
 * server (UnknownHostException): system DNS first, then encrypted DNS over HTTPS (Cloudflare, Google, Quad9).
 */
object SmartDns : Dns {
    private val bootstrap by lazy { OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build() }
    private fun doh(url: String, vararg ips: String) = DnsOverHttps.Builder().client(bootstrap).url(url.toHttpUrl())
        .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) }).includeIPv6(false).build()

    private val cloudflare by lazy { doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1") }
    private val google by lazy { doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4") }
    private val quad9 by lazy { doh("https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112") }

    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    /** Hosts only the encrypted DNS could resolve (VLC needs their IP). */
    val viaDoh: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { (t, a) -> if (System.currentTimeMillis() - t < 10 * 60_000L) return a }
        val mode = Prefs.s.dns
        if (mode == "system") return Dns.SYSTEM.lookup(hostname)
        if (mode == "auto") {
            try { return Dns.SYSTEM.lookup(hostname) } catch (_: UnknownHostException) { }
        }
        val order = when (mode) { "google" -> listOf(google, cloudflare, quad9); "quad9" -> listOf(quad9, cloudflare, google); else -> listOf(cloudflare, google, quad9) }
        for (d in order) {
            try {
                val a = d.lookup(hostname)
                if (a.isNotEmpty()) {
                    cache[hostname] = System.currentTimeMillis() to a
                    viaDoh += hostname
                    AppLog.i("dns", "resolved via DNS over HTTPS")
                    return a
                }
            } catch (_: Exception) { }
        }
        throw UnknownHostException(hostname)
    }

    /** For engines without our resolver (VLC): put the IP into the URL when only DoH knew the host (cache only, no network). */
    fun ipUrl(url: String): String = runCatching {
        val u = url.toHttpUrl()
        if (u.host !in viaDoh) return url
        val ip = cache[u.host]?.second?.firstOrNull()?.hostAddress ?: return url
        u.newBuilder().host(ip).build().toString()
    }.getOrDefault(url)
}
