package com.mohdev.faiptv.core

import com.mohdev.faiptv.data.Account
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Everything that is shared between devices. Pure data, merged without a server. */
@Serializable
data class SyncDoc(
    val v: Int = 1,
    val accounts: List<Account> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val pdata: Map<String, ProfileData> = emptyMap(),
    val tomb: Map<String, Long> = emptyMap(),
    val at: Long = 0,
)

/** Last-writer-wins merge per account, per profile, per favourites list and per resume point. Pure, unit-tested. */
object SyncMerge {
    fun merge(a: SyncDoc, b: SyncDoc): SyncDoc {
        val tomb = (a.tomb.keys + b.tomb.keys).associateWith { maxOf(a.tomb[it] ?: 0, b.tomb[it] ?: 0) }
        fun dead(key: String, updated: Long) = (tomb[key] ?: -1) >= updated

        val accounts = lww(a.accounts, b.accounts, { it.id }, { it.updated }).filterNot { dead("acc:${it.id}", it.updated) }
        val profiles = lww(a.profiles, b.profiles, { it.id }, { it.updated }).filterNot { dead("prof:${it.id}", it.updated) }
        val ids = profiles.map { it.id }.toSet()
        val pdata = ids.associateWith { id ->
            val x = a.pdata[id]
            val y = b.pdata[id]
            when {
                x == null && y == null -> ProfileData()
                x == null -> y!!
                y == null -> x
                else -> mergeProfile(x, y)
            }.let { p -> p.copy(favLists = p.favLists.filterNot { dead("fav:${it.id}", it.updated) }) }
        }
        // drop tombstones older than 90 days
        val cutoff = maxOf(a.at, b.at) - 90L * 86_400_000L
        return SyncDoc(1, accounts, profiles, pdata, tomb.filterValues { it > cutoff }, maxOf(a.at, b.at))
    }

    fun mergeProfile(x: ProfileData, y: ProfileData): ProfileData {
        val newer = if (y.updated > x.updated) y else x
        val lists = lww(x.favLists, y.favLists, { it.id }, { it.updated })
        // keep the newer doc's list order, then lists only the other one has
        val order = newer.favLists.map { it.id } + lists.map { it.id }
        val sorted = lists.sortedBy { l -> order.indexOf(l.id) }
        val resume = (x.resume.keys + y.resume.keys).associateWith { k ->
            val r1 = x.resume[k]; val r2 = y.resume[k]
            if (r1 == null) r2!! else if (r2 == null) r1 else if (r2.updated > r1.updated) r2 else r1
        }
        return newer.copy(favLists = sorted, resume = resume, updated = maxOf(x.updated, y.updated))
    }

    private fun <T> lww(a: List<T>, b: List<T>, id: (T) -> String, t: (T) -> Long): List<T> {
        val out = LinkedHashMap<String, T>()
        for (e in a) out[id(e)] = e
        for (e in b) { val cur = out[id(e)]; if (cur == null || t(e) > t(cur)) out[id(e)] = e }
        return out.values.toList()
    }
}

/** AES-256-GCM with a key derived from the sync pass phrase (PBKDF2-HMAC-SHA256, works on Android 7). */
object SyncCrypto {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val salt = "fa-iptv-sync-v1".toByteArray()
    private const val ITER = 60_000
    private var cache: Pair<String, ByteArray>? = null

    fun key(pass: String): ByteArray {
        cache?.let { (p, k) -> if (p == pass) return k }
        val k = pbkdf2(pass.toByteArray(Charsets.UTF_8), salt, ITER, 32)
        cache = pass to k
        return k
    }

    /** Short id so devices with a different pass phrase are rejected before decrypting. */
    fun keyId(pass: String): String =
        MessageDigest.getInstance("SHA-256").digest(key(pass)).take(8).joinToString("") { "%02x".format(java.util.Locale.US, it) }

    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val out = ByteArray(length)
        var block = 1
        var pos = 0
        while (pos < length) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            block++
        }
        return out
    }

    fun encrypt(plain: ByteArray, pass: String): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(pass), "AES"), GCMParameterSpec(128, iv))
        return byteArrayOf(1) + iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, pass: String): ByteArray {
        require(data.size > 13 && data[0].toInt() == 1) { "unknown format" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(pass), "AES"), GCMParameterSpec(128, data, 1, 12))
        return c.doFinal(data, 13, data.size - 13)
    }

    fun seal(doc: SyncDoc, pass: String): ByteArray = encrypt(json.encodeToString(SyncDoc.serializer(), doc).toByteArray(), pass)
    fun open(data: ByteArray, pass: String): SyncDoc = json.decodeFromString(SyncDoc.serializer(), String(decrypt(data, pass)))
}
