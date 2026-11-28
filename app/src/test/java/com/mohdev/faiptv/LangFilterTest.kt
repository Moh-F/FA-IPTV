package com.mohdev.faiptv

import com.mohdev.faiptv.data.Alternatives
import com.mohdev.faiptv.data.LangFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LangFilterTest {
    @Test fun prefixes() {
        assertEquals("AR", LangFilter.code("AR | MBC 1 HD"))
        assertEquals("DE", LangFilter.code("[DE] Das Erste"))
        assertEquals("EN", LangFilter.code("EN: BBC One"))
        assertEquals("AR", LangFilter.code("Arabic Movies"))
        assertNull(LangFilter.code("HD | Something"))
        assertNull(LangFilter.code("Sky Sport"))
    }

    @Test fun alternatives() {
        assertEquals(Alternatives.base("AR | beIN Sports 1 FHD"), Alternatives.base("AR: beIN Sports 1 HD"))
        assertEquals(3, Alternatives.rank("beIN Sports 1 FHD"))
        assertEquals(1, Alternatives.rank("beIN Sports 1 SD"))
    }
}
