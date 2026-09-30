package com.mantra.arkod

/**
 * THE SNIFFER (30.9.2026, v12): *"make this app a cache king. So every time it can cache something,
 * it's just caching in the background ... it must be very intelligent to use the cache to get the
 * same data again and again."* And: *"caching can be through filter. If user types the search
 * terms, so all the last names, first names, what he is interested in, then you don't cache if
 * something is don't fit the criteria of caching."*
 *
 * While the cache key is on, what is round the map's middle and what a sheet he opened points to
 * (its folio's parcels) is read in the background. With cache criteria (keywords), a sheet read
 * that way is kept only when a name, place or land use on it fits one of them; what he opens
 * himself is always kept. Pure: the decisions and the words; ParcelNet and Screens do the asking.
 */
object Sniff {

    /**
     * His keywords as he wrote them: one criterion per comma or line, each of one or more words that
     * must all be there ("boško ivana" is Ivana Boško, "gobić" is every Gobić). Folded, no diacritics.
     */
    fun criteria(text: String): List<List<String>> =
        text.split(',', ';', '\n')
            .map { c -> Finding.fold(c).split(Regex("""[^a-z0-9*/]+""")).filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }

    /** Every word a sheet says: holders and owners, the place, the land uses. */
    fun wordsOf(record: Parcels.Record?, folios: List<Parcels.Folio> = emptyList()): List<String> =
        buildList {
            record?.let { r ->
                r.sheets.forEach { s -> s.owners.forEach { add(it.name) } }
                add(r.address)
                r.uses.forEach { add(it.name) }
                add(r.municipality)
            }
            folios.forEach { f -> f.shares.forEach { s -> s.owners.forEach { add(it.name) } } }
        }.filter { it.isNotBlank() }

    /**
     * Does what a sheet says fit the criteria? No criteria: everything fits (keep everything). A
     * criterion fits when each of its words begins a word of one of the texts: "bosk" finds BOŠKO,
     * "ivan" finds IVANA and IVAN, but "ana" does not find IVANA.
     */
    fun fits(texts: List<String>, criteria: List<List<String>>): Boolean {
        if (criteria.isEmpty()) return true
        val lines = texts.map { t -> Finding.fold(t).split(Regex("""[^a-z0-9*/]+""")).filter { it.isNotBlank() } }
        return criteria.any { words -> lines.any { line -> words.all { w -> line.any { it.startsWith(w) } } } }
    }

    /**
     * Where to ask the state what is under the map: an n x n grid over the box, each point in the
     * middle of its cell, the middle of the box first and the rest by distance from it.
     */
    fun grid(south: Double, west: Double, north: Double, east: Double, n: Int = 5): List<Pair<Double, Double>> {
        val mid = (south + north) / 2 to (west + east) / 2
        return (0 until n).flatMap { i -> (0 until n).map { j ->
            south + (north - south) * (i + 0.5) / n to west + (east - west) * (j + 0.5) / n
        } }.sortedBy { (la, lo) -> (la - mid.first) * (la - mid.first) + (lo - mid.second) * (lo - mid.second) }
    }

    /** What the sniffer has done, for the line over the keys. */
    data class Tally(val read: Int = 0, val kept: Int = 0, val skipped: Int = 0, val failed: Int = 0, val busy: Boolean = false)

    fun line(t: Tally, criteria: List<List<String>>): String =
        listOfNotNull(
            if (t.busy) "cache: reading" else "cache",
            "${t.read} read",
            "${t.kept} kept",
            t.skipped.takeIf { it > 0 }?.let { "$it not fitting" },
            t.failed.takeIf { it > 0 }?.let { "$it failed" },
            criteria.takeIf { it.isNotEmpty() }?.let { c -> "keywords: " + c.joinToString(", ") { it.joinToString(" ") } },
        ).joinToString(" · ")

    /** The size of what the phone keeps, for the top of the settings: "123 MB". */
    fun megabytes(bytes: Long): String = when {
        bytes < 1_000_000 -> "${(bytes + 999) / 1000} kB"
        bytes < 10_000_000_000 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_000_000.0).replace(".0 MB", " MB")
        else -> "${bytes / 1_000_000_000} GB"
    }

    // --- every kept parcel, in three words (v13) -------------------------------------------------

    /**
     * One kept parcel as the settings list it: *"all parcels should be listed by its numbers and some
     * data, maybe in 3 words: last name of the owner/user and the place of Croatia."*
     */
    data class Kept(val number: String, val municipalityReg: String, val municipality: String, val place: String, val surname: String) {
        val words: String get() = listOf(surname, place.lowercase().replaceFirstChar { it.uppercase() }, municipality)
            .filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Words on a name that are not a person's: "pok.", "ud.", "r.", "zv.", "p.", "sin", "kći", "ž". */
    private val NOT_NAMES = setOf("pok", "ud", "r", "rod", "zv", "p", "z", "sin", "kci", "zena", "i", "dr")

    private fun nameWords(name: String): List<String> =
        Finding.fold(name).split(Regex("""[^a-z]+""")).filter { it.length > 1 && it !in NOT_NAMES }

    /**
     * THE SURNAME. The state writes "BOŠKO DENIS" and "Marinko Boško" on the same sheet, so the order
     * says nothing. A surname repeats: across every kept sheet the word used most is the family's
     * name. Each parcel takes, of its first holder's words, the one used most everywhere.
     */
    fun kept(records: List<Parcels.Record>): List<Kept> {
        val counts = HashMap<String, Int>()
        records.forEach { r -> r.sheets.flatMap { it.owners }.forEach { o -> nameWords(o.name).distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 } } }
        return records.map { r ->
            val first = r.sheets.flatMap { it.owners }.firstOrNull()
            val word = first?.let { o -> nameWords(o.name).maxByOrNull { counts[it] ?: 0 } }
            val surname = word?.let { w ->
                first.name.split(Regex("""[\s,.]+""")).firstOrNull { Finding.fold(it) == w }
                    ?.lowercase()?.replaceFirstChar { it.uppercase() } ?: w
            }.orEmpty()
            Kept(r.number, r.municipalityNumber, r.municipality, r.address, surname)
        }.sortedWith(compareBy<Kept>({ it.municipality }, { numberKey(it.number) }))
    }

    /** "2449/2" before "2449/10" before "2450"; "*27" with the numbers. */
    private fun numberKey(n: String): String =
        n.trimStart('*').split('/').joinToString("/") { it.padStart(6, '0') }

    /** The list narrowed by what he typed: number, surname, place or k.o., without diacritics. */
    fun filterKept(list: List<Kept>, text: String): List<Kept> {
        val q = Finding.fold(text).trim()
        if (q.isEmpty()) return list
        return list.filter { k -> Finding.fold("${k.number} ${k.surname} ${k.place} ${k.municipality}").contains(q) }
    }
}

/**
 * FLY-THROUGH SCANNING (30.9.2026, v16): *"user can write anything, and if some of this text is
 * mentioned in the parcels I see in my view, they will auto-select ... there will be small airplane
 * ... it will just give me status scanning, scanning, scanning. Found selecting."*
 *
 * The words are matched as the cache criteria are ([Sniff.criteria], [Sniff.fits]): "jaša" finds
 * JAŠA ANICA, "bosko ivana" finds Ivana Boško. Pure; [Flyer] asks the state.
 */
object Fly {

    /** Where the flight is, for the line over the keys. */
    data class State(
        val query: String = "",
        val on: Boolean = false,
        val stage: Stage = Stage.WAITING,
        val asked: Int = 0,
        val total: Int = 0,
        val read: Int = 0,
        /** Found so far: the number and what on its sheet fitted. */
        val found: List<Pair<String, String>> = emptyList(),
        val selecting: String? = null,
        val problem: String? = null,
    )

    enum class Stage { WAITING, ZOOM, SCANNING, DONE }

    /** The text on a sheet that fits what he wrote, or null: a holder, an owner, the place, a land use. */
    fun why(record: Parcels.Record?, folios: List<Parcels.Folio>, criteria: List<List<String>>): String? =
        if (criteria.isEmpty()) null
        else Sniff.wordsOf(record, folios).firstOrNull { Sniff.fits(listOf(it), criteria) }?.trim()

    fun line(s: State): String {
        val q = "✈ \"${s.query}\""
        val found = s.found.size.takeIf { it > 0 }?.let { " · found $it" }.orEmpty()
        val trouble = s.problem?.let { " · $it" }.orEmpty()
        return when (s.stage) {
            Stage.WAITING -> "$q: fly over the map, it scans where it rests$found$trouble"
            Stage.ZOOM -> "$q: zoom to 16 or closer to scan$found$trouble"
            Stage.SCANNING -> s.selecting?.let { "$q: found $it · selecting$found$trouble" }
                ?: "$q: scanning ${s.asked}/${s.total} · ${s.read} sheets read$found$trouble"
            Stage.DONE -> "$q: this view scanned · ${s.read} sheets read$found · move on$trouble"
        }
    }
}

