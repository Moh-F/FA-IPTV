package com.mohdev.faiptv.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Typing with the phone: every text field can show a QR code; the phone opens a small page in the home network,
 * the text typed there goes straight into the field on the TV. Each code is random and only valid while the
 * dialog is open.
 */
object RemoteInput {
    class Req(val label: String, val value: String, val secret: Boolean, val multi: Boolean, val onValue: (String) -> Unit)

    private val open = ConcurrentHashMap<String, Req>()
    private val main = Handler(Looper.getMainLooper())

    /** Returns the address for the QR code, or null without home network. */
    fun start(ctx: Context, label: String, value: String, secret: Boolean, multi: Boolean, onValue: (String) -> Unit): Pair<String, String>? {
        if (!LanServer.running) LanServer.start(ctx)
        val base = LanServer.base() ?: return null
        val rnd = SecureRandom()
        val id = (1..16).map { "abcdefghijkmnpqrstuvwxyz23456789"[rnd.nextInt(32)] }.joinToString("")
        open[id] = Req(label, value, secret, multi, onValue)
        return id to "$base/in?k=$id"
    }

    fun stop(id: String) { open.remove(id) }

    fun page(id: String, sent: Boolean = false): String? {
        val r = open[id] ?: return null
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val field = if (r.multi) """<textarea name="v" rows="5" autofocus>${esc(if (r.secret) "" else r.value)}</textarea>"""
        else """<input name="v" type="${if (r.secret) "password" else "text"}" value="${esc(if (r.secret) "" else r.value)}" autofocus autocomplete="off" autocapitalize="off" spellcheck="false">"""
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>FA IPTV</title><style>
body{font-family:system-ui,sans-serif;background:#080D18;color:#F8FAFC;margin:0;padding:24px}
h1{font-size:18px;color:#2EE6CF}label{display:block;margin:16px 0 8px;font-size:15px;color:#CBD5E1}
input,textarea{box-sizing:border-box;width:100%;font-size:18px;padding:14px;border-radius:10px;border:2px solid #3B4F73;background:#152036;color:#F8FAFC}
button{margin-top:18px;width:100%;font-size:18px;padding:14px;border:0;border-radius:10px;background:#2EE6CF;color:#00211C;font-weight:700}
.ok{margin-top:14px;color:#2EE6CF;font-size:16px}</style></head><body dir="auto">
<h1>FA IPTV</h1><form method="post" action="/in?k=$id"><label>${esc(r.label)}</label>$field
<button type="submit">✓ Send · إرسال · Senden</button></form>
${if (sent) "<div class=\"ok\">✓ OK</div>" else ""}</body></html>"""
    }

    fun submit(id: String, value: String): Boolean {
        val r = open.remove(id) ?: return false
        main.post { r.onValue(value) }
        AppLog.i("lan", "text received from phone")
        return true
    }
}
