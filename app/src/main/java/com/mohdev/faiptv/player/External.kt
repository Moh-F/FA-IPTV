package com.mohdev.faiptv.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Net
import com.mohdev.faiptv.data.PlayRequest
import com.mohdev.faiptv.ui.Nav

/** Hands a stream to VLC, MX Player, Just Player or any installed video app. */
object External {
    val known = listOf(
        "org.videolan.vlc" to "VLC",
        "com.mxtech.videoplayer.ad" to "MX Player",
        "com.mxtech.videoplayer.pro" to "MX Player Pro",
        "com.brouken.player" to "Just Player",
        "com.instantbits.cast.webvideo" to "Web Video Cast",
    )

    fun installed(ctx: Context): List<Pair<String, String>> {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://x/y.ts"), "video/*")
        val pm = ctx.packageManager
        val found = runCatching {
            if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.queryIntentActivities(probe, 0)
        }.getOrDefault(emptyList())
        return found.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }.distinctBy { it.first }
    }

    fun open(ctx: Context, req: PlayRequest) {
        val ua = Net.userAgent(req.userAgent)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(req.url), "video/*")
            .putExtra("title", listOf(req.title, req.subtitle).filter { it.isNotBlank() }.joinToString(" · "))
            .putExtra("headers", arrayOf("User-Agent", ua))          // MX Player
            .putExtra("http-user-agent", ua)                          // VLC
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (req.startAtMs > 0) i.putExtra("position", req.startAtMs.toInt()).putExtra("from_start", false)
        val pkg = Prefs.s.externalPackage
        try {
            if (pkg.isNotBlank()) ctx.startActivity(i.setPackage(pkg))
            else ctx.startActivity(Intent.createChooser(i, req.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            AppLog.w("external", "no external player: ${e.message}")
            Toast.makeText(ctx, ctx.getString(R.string.player_no_external), Toast.LENGTH_LONG).show()
            Nav.openInternal(req)
        }
    }
}
