package com.mohdev.faiptv.data

import com.mohdev.faiptv.BuildConfig
import com.mohdev.faiptv.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    val defaultUserAgent = "FA-IPTV/${BuildConfig.VERSION_NAME} (Linux; Android) ExoPlayer"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .dns(SmartDns)
            .build()
    }

    /** Client for streams and recordings: no read timeout on long live streams. */
    val streamClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
    }

    fun userAgent(accountUa: String = ""): String =
        accountUa.ifBlank { Prefs.s.userAgent.ifBlank { defaultUserAgent } }

    class HttpError(val code: Int) : IOException("HTTP $code")

    suspend fun get(url: String, ua: String = ""): String = withContext(Dispatchers.IO) {
        open(url, ua).use { r -> r.body!!.string() }
    }

    fun open(url: String, ua: String = ""): Response {
        val req = Request.Builder().url(url).header("User-Agent", userAgent(ua)).build()
        val r = client.newCall(req).execute()
        if (!r.isSuccessful) { r.close(); throw HttpError(r.code) }
        return r
    }
}
