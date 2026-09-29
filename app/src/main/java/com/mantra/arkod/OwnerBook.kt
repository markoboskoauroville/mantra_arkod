package com.mantra.arkod

/**
 * IMENIK: WHO HOLDS WHAT, FROM EVERY SHEET THIS PHONE HAS READ (29.9.2026, v3).
 *
 * Marko: *"can I search by owner? Can I write first name, last name, and then to the whole Croatia,
 * anything who is in possession or is owner of the parcel, it will be listed? It's like a
 * dictionary, like a phone dictionary."* The state does not publish that. OSS answers a parcel
 * number, a possession sheet or a folio inside one cadastral municipality, and nothing by a
 * person's name (probed 29.9.2026: every name-shaped path under /oss/public is 404). So the book
 * is made here: every holder on a posjedovni list and every owner on a vlasnički list that a sheet
 * opened on this phone showed is written down with the parcel it came from, and the parcel field
 * on the map looks names up in it as he types. It grows with every sheet he opens. Pure: no
 * Android, so Test 1 runs it.
 */
object OwnerBook {

    enum class Role(val word: String) { POSJEDNIK("posjednik"), VLASNIK("vlasnik") }

    data class Entry(
        val name: String,
        val role: Role,
        /** "p.l. 1984" or "z.k.ul. 182" and the share, as the sheet says it. */
        val detail: String,
        val parcelId: Long,
        /** "334723-1655/3". */
        val reference: String,
        val parcelNumber: String,
        val municipalityName: String,
    )

    /** Every holder and owner of one parcel's sheets, as entries. Names without letters are dropped. */
    fun entriesOf(parcel: Parcels.Parcel, record: Parcels.Record?, folios: List<Parcels.Folio>): List<Entry> {
        val ko = record?.municipality.orEmpty()
        val held = record?.sheets.orEmpty().flatMap { sheet ->
            sheet.owners.map { o ->
                Entry(o.name.trim(), Role.POSJEDNIK, "p.l. ${sheet.number}", parcel.id, parcel.reference, parcel.number, ko)
            }
        }
        val owned = folios.flatMap { f ->
            f.shares.flatMap { share ->
                share.owners.map { o ->
                    val part = o.share.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    Entry(o.name.trim(), Role.VLASNIK, "z.k.ul. ${f.unit}$part", parcel.id, parcel.reference, parcel.number, ko)
                }
            }
        }
        return (held + owned).filter { e -> e.name.any { it.isLetter() } }
    }

    /**
     * The book with one parcel's entries put in: whatever that parcel said before is replaced, so
     * a sheet read again after a sale does not keep the seller.
     */
    fun add(book: List<Entry>, parcelEntries: List<Entry>, limit: Int = 20_000): List<Entry> {
        val refs = parcelEntries.map { it.reference }.toSet()
        val kept = book.filter { it.reference !in refs }
        return (parcelEntries.distinctBy { key(it) } + kept).take(limit)
    }

    private fun key(e: Entry) = Finding.fold(e.name) + "|" + e.role + "|" + e.reference + "|" + e.detail

    /**
     * Names that hold every word he typed, in any order and without diacritics: "jasa anica" finds
     * "JAŠA ANICA POK. JOSE". Each person once per parcel, the fullest match first.
     */
    fun search(book: List<Entry>, query: String, limit: Int = 30): List<Entry> {
        val q = Finding.fold(query).trim()
        if (q.length < 2) return emptyList()
        val words = q.split(Regex("\\s+"))
        return book.filter { e -> val n = Finding.fold(e.name); words.all { n.contains(it) } }
            .distinctBy { Finding.fold(it.name) + "|" + it.reference + "|" + it.role }
            .sortedWith(compareBy<Entry> { !Finding.fold(it.name).startsWith(words.first()) }.thenBy { it.name })
            .take(limit)
    }

    /** One entry as a line in the results under the field: the name, and the parcel under it. */
    fun hit(e: Entry): Finding.Hit = Finding.Hit(
        id = e.parcelId.toString(),
        title = e.name,
        under = listOfNotNull(
            e.parcelNumber,
            e.municipalityName.takeIf { it.isNotBlank() }?.let { "k.o. $it" },
            e.role.word,
            e.detail,
        ).joinToString(" · "),
        source = Finding.Source.PARCEL,
        ref = e.reference,
    )

    // --- on the phone: one line an entry; a bar cannot be in a name the state writes ------------

    fun encode(book: List<Entry>): String = book.joinToString("\n") { e ->
        listOf(e.name, e.role.name, e.detail, e.parcelId.toString(), e.reference, e.parcelNumber, e.municipalityName)
            .joinToString("|") { it.replace("|", "/").replace("\n", " ") }
    }

    fun decode(text: String): List<Entry> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|')
        if (p.size < 7) return@mapNotNull null
        val role = Role.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
        Entry(p[0], role, p[2], p[3].toLongOrNull() ?: 0L, p[4], p[5], p[6])
    }.toList()
}
