package com.mohdev.faiptv.data

/**
 * Detects the language/country code of IPTV categories ("AR | MBC", "|DE| Sport", "[EN] News", "ARABIC MOVIES")
 * so the user can keep only the languages he wants. Pure, unit-tested.
 */
object LangFilter {
    private val prefix = Regex("""^\s*[\[(|★#*•]*\s*([A-Za-z]{2,3})\s*[\])|:\-–—»/]""")
    private val stop = setOf("HD", "SD", "FHD", "UHD", "VIP", "TV", "NEW", "ALL", "VOD", "PPV", "THE", "TOP", "BEST", "HQ", "LQ", "ONE", "XXX", "MIX", "KID", "KIDS", "BEIN", "OSN", "MBC", "NBA", "NFL", "UFC", "WWE", "DAZN", "SKY", "BBC", "ART", "RAW", "HEVC", "LIVE", "4K", "8K")
    private val words = linkedMapOf(
        "ARABIC" to "AR", "ARAB" to "AR", "عربي" to "AR", "العربية" to "AR",
        "GERMAN" to "DE", "DEUTSCH" to "DE", "GERMANY" to "DE",
        "ENGLISH" to "EN", "TURKISH" to "TR", "TURKEY" to "TR", "TÜRK" to "TR",
        "FRENCH" to "FR", "FRANCE" to "FR", "ITALIAN" to "IT", "ITALY" to "IT", "ITALIA" to "IT",
        "SPANISH" to "ES", "SPAIN" to "ES", "ESPAÑA" to "ES", "PORTUGAL" to "PT", "BRAZIL" to "BR",
        "KURDISH" to "KU", "KURD" to "KU", "PERSIAN" to "IR", "IRAN" to "IR", "AFGHAN" to "AF",
        "INDIA" to "IN", "HINDI" to "IN", "PAKISTAN" to "PK", "URDU" to "PK",
        "DUTCH" to "NL", "NETHERLANDS" to "NL", "POLAND" to "PL", "POLSKA" to "PL", "RUSSIA" to "RU", "RUSSIAN" to "RU",
        "GREECE" to "GR", "GREEK" to "GR", "ALBANIA" to "AL", "BALKAN" to "EXYU", "EX-YU" to "EXYU",
        "SWEDEN" to "SE", "NORWAY" to "NO", "DENMARK" to "DK", "FINLAND" to "FI",
        "UK" to "UK", "USA" to "US", "CANADA" to "CA", "AFRICA" to "AFR", "LATINO" to "LAT",
    )

    private val wordRx by lazy { words.map { (w, c) -> Regex("""(^|[^\p{L}])${Regex.escape(w.uppercase())}([^\p{L}]|$)""") to c } }

    fun code(name: String): String? {
        prefix.find(name)?.groupValues?.get(1)?.uppercase()?.let { if (it !in stop) return it }
        val up = name.uppercase()
        for ((rx, c) in wordRx) if (rx.containsMatchIn(up)) return c
        return null
    }

    /** Codes found in the library with the number of categories. */
    fun available(l: Library): List<Pair<String, Int>> =
        (l.liveCats + l.movieCats + l.seriesCats).mapNotNull { code(it.name) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun apply(l: Library, keep: Set<String>, keepUnknown: Boolean): Library {
        if (keep.isEmpty()) return l
        fun ok(c: Category): Boolean = code(c.name)?.let { it in keep } ?: keepUnknown
        val lc = l.liveCats.filter(::ok); val lcIds = lc.map { it.id }.toHashSet()
        val mc = l.movieCats.filter(::ok); val mcIds = mc.map { it.id }.toHashSet()
        val sc = l.seriesCats.filter(::ok); val scIds = sc.map { it.id }.toHashSet()
        val series = l.series.filter { it.categoryId in scIds }
        val sIds = series.map { it.id }.toHashSet()
        return l.copy(
            liveCats = lc, live = l.live.filter { it.categoryId in lcIds },
            movieCats = mc, movies = l.movies.filter { it.categoryId in mcIds },
            seriesCats = sc, series = series, m3uEpisodes = l.m3uEpisodes.filterKeys { it in sIds },
        )
    }
}
