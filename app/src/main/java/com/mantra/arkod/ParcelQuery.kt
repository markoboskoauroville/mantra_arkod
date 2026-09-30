package com.mantra.arkod

/**
 * WHAT HE TYPED INTO THE PARCEL FIELD, AND WHERE TO LOOK FOR IT (30.9.2026, v11).
 *
 * Marko, in Zagreb, typed 1358/3 (the family's parcel in Kukljica) and nothing happened: the field
 * asked only the k.o. under the middle of the map, CENTAR NOVI, and said "· 0". *"I typed the parcel
 * number expecting that the map will take me there ... fix this issue that I can find what I'm
 * searching. Simple."* So a number is looked for:
 *  1. in Moje čestice, in every k.o. (on the phone, no signal needed);
 *  2. in the k.o. he names with it, "1358/3 kukljica" or "kukljica 1358/3";
 *  3. else in the k.o. under the map, and when that has no such number, in every k.o. he already
 *     has something in (Moje čestice, caches, Imenik, and every k.o. the map has stood over).
 * Pure: no Android, tested in CoreTest.
 */
object ParcelQuery {

    /** A cadastral municipality: its register number, its name, and OSS's internal id when known. */
    data class Ko(val reg: String, val name: String, val id: String = "") {
        val label: String get() = name.ifBlank { reg }
    }

    /** A parcel number, and the words he typed with it, if any ("kukljica"). */
    data class Query(val number: String, val place: String?)

    private val NUMBER = Regex("""^\*?\d+(/\d*)?$""")

    /**
     * "1358/3" → (1358/3, none); "1358/3 kukljica", "kukljica 1358/3", "k.o. Kukljica, 1358/3" →
     * (1358/3, "kukljica"). Null when there is no parcel number in it, or more than one.
     */
    fun parse(text: String): Query? {
        val words = text.trim().split(Regex("""[\s,;]+""")).filter { it.isNotBlank() }
        val numbers = words.filter { NUMBER.matches(it) }
        if (numbers.size != 1) return null
        val rest = words.filter { it != numbers[0] }
            .filterNot { Finding.fold(it).trimEnd('.') in setOf("k.o", "ko", "k", "o") }
            .joinToString(" ").trim()
        return Query(numbers[0], rest.ifBlank { null })
    }

    /**
     * Every k.o. the phone knows by name or number, each once: the map's own (seen), then Imenik's,
     * the caches', and the register numbers of Moje čestice (a mark carries no name; another
     * source usually does). The first name found for a number wins; an id is kept from any source.
     */
    fun known(
        seen: List<Ko>,
        marks: List<Parcels.Mark>,
        caches: List<ParcelCache.Cache>,
        book: List<OwnerBook.Entry>,
    ): List<Ko> {
        val all = mutableListOf<Ko>()
        all += seen
        book.forEach { e -> e.reference.substringBefore('-', "").takeIf { it.isNotBlank() }?.let { all += Ko(it, e.municipalityName) } }
        caches.forEach { c -> c.items.forEach { i -> i.reference.substringBefore('-', "").takeIf { it.isNotBlank() }?.let { all += Ko(it, i.municipalityName) } } }
        marks.forEach { m -> m.municipality.takeIf { it.isNotBlank() }?.let { all += Ko(it, "") } }
        val out = LinkedHashMap<String, Ko>()
        for (k in all) {
            val had = out[k.reg]
            out[k.reg] = if (had == null) k else had.copy(
                name = had.name.ifBlank { k.name },
                id = had.id.ifBlank { k.id },
            )
        }
        return out.values.toList()
    }

    /**
     * The k.o. he named: its register number, or the start of its name without diacritics
     * ("kuklj" is KUKLJICA). An exact name before a start; null when none or when two fit.
     */
    fun resolve(place: String, known: List<Ko>): Ko? {
        val p = Finding.fold(place).trim()
        if (p.isEmpty()) return null
        known.firstOrNull { it.reg == p }?.let { return it }
        known.firstOrNull { Finding.fold(it.name) == p }?.let { return it }
        val starts = known.filter { it.name.isNotBlank() && Finding.fold(it.name).startsWith(p) }
        return starts.singleOrNull()
    }

    /**
     * Moje čestice whose number begins with it, in every k.o. (or only in [only]); the exact one
     * first. Each is a parcel result that opens straight from the phone.
     */
    fun mine(marks: List<Parcels.Mark>, number: String, known: List<Ko>, only: Ko? = null): List<Finding.Hit> {
        val names = known.associate { it.reg to it.label }
        return marks
            .filter { it.number.startsWith(number) && (only == null || it.municipality == only.reg) }
            .sortedBy { if (it.number == number) 0 else 1 }
            .map { m ->
                Finding.Hit(
                    id = m.id.toString(),
                    title = m.number,
                    under = listOfNotNull(
                        "Moje čestice",
                        m.name.takeIf { it.isNotBlank() },
                        "k.o. ${names[m.municipality] ?: m.municipality}",
                    ).joinToString(" · "),
                    source = Finding.Source.PARCEL,
                    ref = m.reference,
                )
            }
    }

    /**
     * What Search on the keyboard opens: the one result that is exactly that number. Two exact ones
     * in different k.o. are a choice for him (null); a single result of any kind is taken as it is.
     */
    fun best(hits: List<Finding.Hit>, number: String): Finding.Hit? {
        val exact = hits.filter { h -> h.ref?.substringAfter('-') == number }.distinctBy { it.ref }
        return when {
            exact.size == 1 -> exact[0]
            exact.isEmpty() && hits.distinctBy { it.ref }.size == 1 -> hits[0]
            else -> null
        }
    }

    /** True when the answer already holds exactly that number, so no other k.o. need be asked. */
    fun hasExact(hits: List<Finding.Hit>, number: String): Boolean = hits.any { it.ref?.substringAfter('-') == number }

    /** The k.o. the map has stood over, kept one per line: "reg|name|id", newest first, at most 30. */
    fun encodeSeen(list: List<Ko>): String = list.take(30).joinToString("\n") { "${it.reg}|${it.name}|${it.id}" }

    fun decodeSeen(text: String?): List<Ko> =
        text.orEmpty().split('\n').mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 2 || p[0].isBlank()) null else Ko(p[0], p[1], p.getOrElse(2) { "" })
        }

    fun withSeen(list: List<Ko>, k: Ko): List<Ko> = listOf(k) + list.filterNot { it.reg == k.reg }

    // --- outlines kept one by one (v11, "cache king") -------------------------------------------

    /**
     * One parcel's outline as the phone keeps it: "id|number|reference|area|lat,lon lat,lon …;…"
     * (a ";" between rings, six decimals). The state's WFS fails in spells (ORA-01000 again on
     * 30.9.2026 at 16:20), so an outline read once is never asked for again.
     */
    fun encodeShape(p: Parcels.Parcel): String =
        listOf(p.id.toString(), p.number, p.reference, p.areaM2?.toString().orEmpty(),
            p.rings.joinToString(";") { r -> r.joinToString(" ") { (la, lo) -> "%.6f,%.6f".format(java.util.Locale.ROOT, la, lo) } },
        ).joinToString("|")

    fun decodeShape(text: String?): Parcels.Parcel? {
        val f = text?.split('|') ?: return null
        if (f.size != 5 || f[2].isBlank()) return null
        val rings = f[4].split(';').filter { it.isNotBlank() }.map { r ->
            r.split(' ').mapNotNull { pt ->
                val (la, lo) = pt.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
                val a = la.toDoubleOrNull(); val b = lo.toDoubleOrNull()
                if (a == null || b == null) null else a to b
            }
        }.filter { it.size >= 3 }
        if (rings.isEmpty()) return null
        return Parcels.Parcel(f[0].toLongOrNull() ?: 0L, f[1], f[2], f[3].toIntOrNull(), rings)
    }

    /** The key an outline is kept under in the phone's store of answers. */
    fun shapeKey(reference: String): String = "shape:$reference"
}
