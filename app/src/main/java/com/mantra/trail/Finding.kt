package com.mantra.trail

import java.text.Normalizer

/**
 * ONE LIST FOR EVERY SEARCH (27.9.2026).
 *
 * Marko, with two screenshots, his app showing one result for "stjepana radića 13c" and Google
 * Maps ten: *"Search results on the map for both should be the same ... I want that my app be as
 * Google or even better."* Google's own list is autocomplete predictions across the country, each
 * with its distance, and a tap puts the place on the map. So every search here (Google's field,
 * the cadastre's number, a holder's possession sheet, a street) answers in the same shape: a
 * [Hit] with a title, a line under it, how far it is, and where it is when that is known. Pure.
 */
object Finding {

    data class Hit(
        /** Unique within its source: a Google place id, or a cadastral parcel id. */
        val id: String,
        val title: String,
        val under: String,
        val distanceM: Int? = null,
        val lat: Double? = null,
        val lon: Double? = null,
        val source: Source = Source.GOOGLE,
        /** A parcel's cadastral reference, "334723-2451"; its number is what follows the dash. */
        val ref: String? = null,
    ) {
        val located: Boolean get() = lat != null && lon != null
    }

    enum class Source { GOOGLE, PARCEL }

    /**
     * Several answers into one list: each place once (by id, then by what it says), nearest first,
     * the ones with no distance after them in the order they came.
     */
    fun merge(vararg lists: List<Hit>, limit: Int = 12): List<Hit> {
        val seen = HashSet<String>()
        val out = ArrayList<Hit>()
        for (list in lists) for (h in list) {
            val same = fold(h.title) + "|" + fold(h.under)
            if (seen.add("id:" + h.id) && seen.add("as:" + same)) out.add(h)
        }
        return out.sortedWith(compareBy<Hit> { it.distanceM == null }.thenBy { it.distanceM ?: 0 }).take(limit)
    }

    /**
     * "stjepana radića 13c" asked a second time as "stjepana radića 13": Google predicts five per
     * question, and the plain number finds the same street in the towns that have no 13c, which is
     * most of the list Google Maps shows. Null when there is no letter to drop.
     */
    fun withoutHouseLetter(text: String): String? {
        val m = Regex("""^(.*\d+)\s*[a-zA-Z]$""").find(text.trim()) ?: return null
        return m.groupValues[1]
    }

    /** "850 m", "10 km", as Google's list says it. */
    fun distanceLabel(metres: Int?): String = when {
        metres == null -> ""
        metres < 1000 -> "$metres m"
        metres < 10_000 -> "%.1f km".format(java.util.Locale.ROOT, metres / 1000.0)
        else -> "${metres / 1000} km"
    }

    /** Lower case, diacritics off: "Čabrijan" is found by "cabri", as a person types it. */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('đ', 'd')

    fun matches(text: String, query: String): Boolean {
        val q = fold(query).trim()
        if (q.isEmpty()) return true
        val t = fold(text)
        return q.split(Regex("\\s+")).all { t.contains(it) }
    }
}
