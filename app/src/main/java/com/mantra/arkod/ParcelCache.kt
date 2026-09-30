package com.mantra.arkod

import org.json.JSONArray
import org.json.JSONObject

/**
 * PARCEL CACHES: EVERYTHING IN THE VIEW, KEPT UNDER A NAME (30.9.2026, v5).
 *
 * Marko: *"cache all parcels in the view, basically all its possessions and usage data, so I can
 * then search each of them locally ... I'm going to write, for example, name of the owner in whole
 * Kukljica area. I'm caching and I will find my grandmother's parcels by search by her name ... each
 * cache action should be stored as different cache and user can name it ... similar as tracks ...
 * all cached parcels should have different color or maybe different line style ... remove their
 * layer, and keep our own style for that area."*
 *
 * A cache is the box that was on the screen, every parcel the state's WFS has in it with its
 * outline and the point its number is written at, and for each parcel what its possession sheet
 * and owner sheet say: holders, owners, shares, address and land use. Inside the box the state's
 * picture is taken away and these outlines are drawn instead, in the cache's own colour and line.
 *
 * Pure: no Android, so Test 1 runs it (org.json is the real one there).
 */
object ParcelCache {

    /** South, west, north, east, in degrees. */
    data class Box(val south: Double, val west: Double, val north: Double, val east: Double) {
        fun contains(lat: Double, lon: Double): Boolean = lat in south..north && lon in west..east
        fun intersects(o: Box): Boolean = o.west < east && o.east > west && o.south < north && o.north > south
        fun holds(o: Box): Boolean = o.west >= west && o.east <= east && o.south >= south && o.north <= north
        val middle: Pair<Double, Double> get() = (south + north) / 2 to (west + east) / 2
    }

    /** One person on one of the parcel's sheets. */
    data class Holder(val name: String, val role: OwnerBook.Role, val detail: String)

    /** One parcel of a cache, with everything the search reads. */
    data class Item(
        val id: Long,
        val number: String,
        val reference: String,
        val areaM2: Int?,
        val rings: List<List<Pair<Double, Double>>>,
        val label: Pair<Double, Double>?,
        val municipalityName: String = "",
        val address: String = "",
        val uses: List<String> = emptyList(),
        val holders: List<Holder> = emptyList(),
        /** False until the sheets were read; the outline alone is still drawn and found by number. */
        val read: Boolean = false,
    ) {
        val box: Box by lazy {
            val all = rings.flatten()
            if (all.isEmpty()) Box(0.0, 0.0, 0.0, 0.0)
            else Box(all.minOf { it.first }, all.minOf { it.second }, all.maxOf { it.first }, all.maxOf { it.second })
        }

        /** Where the map goes, and where the number is written: the state's own point if it gave one. */
        val middle: Pair<Double, Double>
            get() = label ?: rings.firstOrNull()?.takeIf { it.isNotEmpty() }?.let { r ->
                r.sumOf { it.first } / r.size to r.sumOf { it.second } / r.size
            } ?: (0.0 to 0.0)

        fun parcel(): Parcels.Parcel = Parcels.Parcel(id, number, reference, areaM2, rings)
    }

    /** What the list shows of a cache, and how it is drawn. */
    data class Info(
        val id: String,
        val name: String,
        val createdMs: Long,
        val box: Box,
        val zoom: Int,
        val colour: Long = COLOURS.first(),
        val style: Parcels.LineStyle = Parcels.LineStyle.SOLID,
        val weight: ParcelStyle.Weight = ParcelStyle.Weight.NORMAL,
        val visible: Boolean = true,
        val count: Int = 0,
        val read: Int = 0,
        val places: String = "",
    )

    data class Cache(val info: Info, val items: List<Item>)

    /** The quick picks for a cache: none of them is the state's ink, his parcels' amber, or the selection's cyan. */
    val COLOURS: List<Long> = listOf(0xFFE040FBL, 0xFF34D399L, 0xFF60A5FAL, 0xFFFACC15L, 0xFFEF4444L, 0xFFF2DDB4L)

    /** "Kukljica 30.9.2026", the name offered before he types his own. */
    fun defaultName(places: String, day: String): String =
        (places.split(',').firstOrNull()?.trim()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "").let {
            if (it.isBlank()) day else "$it $day"
        }

    /** The places a cache holds, from its parcels' municipalities: "KUKLJICA, PRDKO". */
    fun places(items: List<Item>): String =
        items.map { it.municipalityName }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.joinToString(", ") { it.key }

    /** One parcel read: its sheets turned into what the search finds. */
    fun itemOf(
        parcel: Parcels.Parcel,
        label: Pair<Double, Double>?,
        record: Parcels.Record?,
        folios: List<Parcels.Folio>,
    ): Item {
        val holders = OwnerBook.entriesOf(parcel, record, folios).map { Holder(it.name, it.role, it.detail) }
            .distinctBy { Finding.fold(it.name) + "|" + it.role + "|" + it.detail }
        return Item(
            id = parcel.id,
            number = parcel.number,
            reference = parcel.reference,
            areaM2 = parcel.areaM2,
            rings = parcel.rings,
            label = label,
            municipalityName = record?.municipality.orEmpty(),
            address = record?.address.orEmpty(),
            uses = record?.uses.orEmpty().map { it.name }.filter { it.isNotBlank() }.distinct(),
            holders = holders,
            read = record != null,
        )
    }

    // --- the search ------------------------------------------------------------------------------

    /**
     * WHAT HE TYPED, OVER EVERY CACHE ON THE PHONE, with no signal at all:
     *  - a name, any words in any order, no diacritics needed: every holder and owner it fits,
     *    one line per person per parcel;
     *  - "pl 1984": that possession sheet's parcels;
     *  - a number, "245": the parcels whose number begins with it;
     *  - otherwise an address or a land use ("maslinik").
     */
    fun search(caches: List<Cache>, query: String, limit: Int = 40): List<Finding.Hit> {
        val q = Finding.fold(query).trim()
        if (q.length < 2 && !q.all { it.isDigit() }) return emptyList()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Finding.Hit>()
        val sheet = Regex("""^p\.?\s*l\.?\s*(\d+)$""").find(q)?.groupValues?.get(1)
        val number = Regex("""^\*?\d+(/\d*)?$""").matches(q)
        val words = q.split(Regex("\\s+"))
        for (cache in caches) for (item in cache.items) {
            when {
                sheet != null -> {
                    if (item.holders.any { it.role == OwnerBook.Role.POSJEDNIK && it.detail == "p.l. $sheet" }) {
                        out.add(hit(item, cache.info, item.number, "p.l. $sheet"))
                    }
                }
                number -> if (item.number.startsWith(q)) out.add(hit(item, cache.info, item.number, item.address.ifBlank { null }))
                else -> {
                    var found = false
                    item.holders.forEach { h ->
                        val n = Finding.fold(h.name)
                        if (words.all { n.contains(it) }) {
                            found = true
                            out.add(hit(item, cache.info, h.name, "${h.role.word} · ${h.detail}"))
                        }
                    }
                    if (!found) {
                        val rest = Finding.fold(item.address + " " + item.uses.joinToString(" "))
                        if (words.all { rest.contains(it) }) out.add(hit(item, cache.info, item.number, listOf(item.address, item.uses.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ")))
                    }
                }
            }
            if (out.size >= limit * 3) break
        }
        val first = words.first()
        return out.distinctBy { it.title + "|" + it.ref + "|" + it.under }
            .sortedWith(compareBy<Finding.Hit> { !Finding.fold(it.title).startsWith(first) }.thenBy { it.title })
            .take(limit)
    }

    private fun hit(item: Item, info: Info, title: String, what: String?): Finding.Hit = Finding.Hit(
        id = item.id.toString(),
        title = title,
        under = listOfNotNull(
            item.number.takeIf { it != title },
            item.municipalityName.takeIf { it.isNotBlank() }?.let { "k.o. $it" },
            what,
            info.name,
        ).joinToString(" · "),
        lat = item.middle.first,
        lon = item.middle.second,
        source = Finding.Source.PARCEL,
        ref = item.reference,
    )

    /** The cached parcel under a finger, from the caches that are drawn. */
    fun at(caches: List<Cache>, lat: Double, lon: Double): Item? =
        caches.asSequence().filter { it.info.visible && it.info.box.contains(lat, lon) }
            .flatMap { it.items.asSequence() }
            .firstOrNull { item -> item.box.contains(lat, lon) && item.rings.any { Parcels.contains(it, lat, lon) } }

    fun byReference(caches: List<Cache>, reference: String): Item? =
        caches.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.reference == reference && it.rings.isNotEmpty() }

    // --- the map ---------------------------------------------------------------------------------

    /** One slippy tile as a box in degrees. */
    fun tileBox(z: Int, x: Int, y: Int): Box = Box(
        south = Geo.tileLat(y + 1, z),
        west = Geo.tileLon(x, z),
        north = Geo.tileLat(y, z),
        east = Geo.tileLon(x + 1, z),
    )

    /** A point in the tile's pixels, [size] across, Web Mercator as the tile itself is. */
    fun pixel(lat: Double, lon: Double, z: Int, x: Int, y: Int, size: Int): Pair<Float, Float> {
        val n = (1 shl z).toDouble()
        val px = ((lon + 180.0) / 360.0 * n - x) * size
        val r = Math.toRadians(lat)
        val py = ((1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * n - y) * size
        return px.toFloat() to py.toFloat()
    }

    /** The WFS box of the view, as its BBOX parameter wants it (latitude first in EPSG:4326). */
    fun wfsBox(box: Box): String = "${box.south},${box.west},${box.north},${box.east},urn:ogc:def:crs:EPSG::4326"

    /** The WFS answer's parcels with the point each number is written at. */
    fun parseFeatures(json: String): List<Pair<Parcels.Parcel, Pair<Double, Double>?>> {
        val parcels = Parcels.parseParcels(json)
        val features = JSONObject(json).optJSONArray("features") ?: return parcels.map { it to null }
        val points = HashMap<Long, Pair<Double, Double>>()
        for (i in 0 until features.length()) {
            val p = features.optJSONObject(i)?.optJSONObject("properties") ?: continue
            val id = p.optJSONObject("inspireId")?.optString("localId")?.substringAfterLast('.')?.toLongOrNull() ?: continue
            val c = p.optJSONObject("referencePoint")?.optJSONArray("coordinates") ?: continue
            points[id] = c.optDouble(1) to c.optDouble(0)
        }
        return parcels.map { it to points[it.id] }
    }

    /** How many the WFS says there are in the box, when it says. */
    fun matched(json: String): Int? = JSONObject(json).let { if (it.has("numberMatched")) it.optInt("numberMatched") else null }

    // --- on the phone ----------------------------------------------------------------------------

    fun encodeInfo(i: Info): String = JSONObject().apply {
        put("id", i.id); put("name", i.name); put("created", i.createdMs)
        put("box", JSONArray(listOf(i.box.south, i.box.west, i.box.north, i.box.east)))
        put("zoom", i.zoom); put("colour", java.lang.Long.toHexString(i.colour))
        put("style", i.style.name); put("weight", i.weight.name); put("visible", i.visible)
        put("count", i.count); put("read", i.read); put("places", i.places)
    }.toString()

    fun decodeInfo(text: String): Info? = runCatching {
        val o = JSONObject(text)
        val b = o.getJSONArray("box")
        Info(
            id = o.getString("id"),
            name = o.optString("name"),
            createdMs = o.optLong("created"),
            box = Box(b.getDouble(0), b.getDouble(1), b.getDouble(2), b.getDouble(3)),
            zoom = o.optInt("zoom"),
            colour = o.optString("colour").toLongOrNull(16) ?: COLOURS.first(),
            style = Parcels.LineStyle.entries.firstOrNull { it.name == o.optString("style") } ?: Parcels.LineStyle.SOLID,
            weight = ParcelStyle.Weight.entries.firstOrNull { it.name == o.optString("weight") } ?: ParcelStyle.Weight.NORMAL,
            visible = o.optBoolean("visible", true),
            count = o.optInt("count"),
            read = o.optInt("read"),
            places = o.optString("places"),
        )
    }.getOrNull()

    fun encodeItems(items: List<Item>): String = JSONArray().apply {
        items.forEach { it -> put(encodeItem(it)) }
    }.toString()

    private fun encodeItem(it: Item): JSONObject = JSONObject().apply {
        put("id", it.id); put("n", it.number); put("r", it.reference)
        it.areaM2?.let { a -> put("a", a) }
        put("g", JSONArray().apply {
            it.rings.forEach { ring ->
                put(JSONArray().apply { ring.forEach { (la, lo) -> put(round6(la)); put(round6(lo)) } })
            }
        })
        it.label?.let { (la, lo) -> put("l", JSONArray(listOf(round6(la), round6(lo)))) }
        put("k", it.municipalityName); put("ad", it.address)
        put("u", JSONArray(it.uses))
        put("h", JSONArray().apply { it.holders.forEach { h -> put(JSONArray(listOf(h.name, h.role.name, h.detail))) } })
        put("ok", it.read)
    }

    private fun round6(v: Double): Double = Math.round(v * 1e6) / 1e6

    fun decodeItems(text: String): List<Item> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val g = o.optJSONArray("g") ?: JSONArray()
            val rings = (0 until g.length()).mapNotNull { k ->
                val r = g.optJSONArray(k) ?: return@mapNotNull null
                (0 until r.length() / 2).map { j -> r.getDouble(2 * j) to r.getDouble(2 * j + 1) }
            }
            val l = o.optJSONArray("l")
            val u = o.optJSONArray("u") ?: JSONArray()
            val h = o.optJSONArray("h") ?: JSONArray()
            Item(
                id = o.optLong("id"),
                number = o.optString("n"),
                reference = o.optString("r"),
                areaM2 = if (o.has("a")) o.optInt("a") else null,
                rings = rings,
                label = l?.let { it.getDouble(0) to it.getDouble(1) },
                municipalityName = o.optString("k"),
                address = o.optString("ad"),
                uses = (0 until u.length()).map { u.optString(it) },
                holders = (0 until h.length()).mapNotNull { k ->
                    val x = h.optJSONArray(k) ?: return@mapNotNull null
                    val role = OwnerBook.Role.entries.firstOrNull { it.name == x.optString(1) } ?: return@mapNotNull null
                    Holder(x.optString(0), role, x.optString(2))
                },
                read = o.optBoolean("ok"),
            )
        }
    }.getOrDefault(emptyList())
}
