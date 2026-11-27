package com.mohdev.faiptv

import com.mohdev.faiptv.core.FavList
import com.mohdev.faiptv.core.Profile
import com.mohdev.faiptv.core.ProfileData
import com.mohdev.faiptv.core.Resume
import com.mohdev.faiptv.core.SyncCrypto
import com.mohdev.faiptv.core.SyncDoc
import com.mohdev.faiptv.core.SyncMerge
import com.mohdev.faiptv.data.Account
import com.mohdev.faiptv.data.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncTest {
    private fun acc(id: String, name: String, t: Long) = Account(id, name, AccountType.XTREAM, updated = t)
    private fun res(k: String, pos: Long, t: Long) = Resume(k, "a", "MOVIE", k, posMs = pos, durMs = 1000, updated = t)

    @Test fun newerAccountWinsAndDeletesStick() {
        val a = SyncDoc(accounts = listOf(acc("1", "old", 1), acc("2", "keep", 1)), at = 10)
        val b = SyncDoc(accounts = listOf(acc("1", "new", 5)), tomb = mapOf("acc:2" to 3), at = 11)
        val m = SyncMerge.merge(a, b)
        assertEquals(listOf("new"), m.accounts.map { it.name })
    }

    @Test fun favouriteListsMergeById() {
        val p = Profile("p", "Me", updated = 1)
        val x = ProfileData(favLists = listOf(FavList("l1", "News", "a", listOf("live:1"), 5)), resume = mapOf("m1" to res("m1", 100, 5)), updated = 5)
        val y = ProfileData(favLists = listOf(FavList("l1", "News", "a", listOf("live:1", "live:2"), 9), FavList("l2", "Kids", "a", emptyList(), 2)),
            resume = mapOf("m1" to res("m1", 50, 2), "m2" to res("m2", 10, 3)), updated = 3)
        val m = SyncMerge.merge(SyncDoc(profiles = listOf(p), pdata = mapOf("p" to x)), SyncDoc(profiles = listOf(p), pdata = mapOf("p" to y)))
        val pd = m.pdata.getValue("p")
        assertEquals(listOf("l1", "l2"), pd.favLists.map { it.id })
        assertEquals(listOf("live:1", "live:2"), pd.favLists[0].items)
        assertEquals(100L, pd.resume.getValue("m1").posMs)
        assertEquals(setOf("m1", "m2"), pd.resume.keys)
    }

    @Test fun deletedListIsRemoved() {
        val p = Profile("p", "Me")
        val x = ProfileData(favLists = listOf(FavList("l1", "A", "a", updated = 1)))
        val m = SyncMerge.merge(SyncDoc(profiles = listOf(p), pdata = mapOf("p" to x)), SyncDoc(profiles = listOf(p), tomb = mapOf("fav:l1" to 2)))
        assertTrue(m.pdata.getValue("p").favLists.isEmpty())
    }

    @Test fun encryptRoundTripAndWrongPass() {
        val doc = SyncDoc(accounts = listOf(acc("1", "x", 1)), at = 1)
        val sealed = SyncCrypto.seal(doc, "correct horse")
        assertEquals("x", SyncCrypto.open(sealed, "correct horse").accounts[0].name)
        try { SyncCrypto.open(sealed, "wrong pass!"); fail() } catch (e: Exception) { /* expected */ }
    }

    @Test fun pbkdf2KnownVector() {
        // RFC 7914 / common PBKDF2-HMAC-SHA256 vector: "password"/"salt", 1 iteration
        val k = SyncCrypto.pbkdf2("password".toByteArray(), "salt".toByteArray(), 1, 32)
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", k.joinToString("") { "%02x".format(it) })
    }
}
