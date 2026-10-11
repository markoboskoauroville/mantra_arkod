package com.mantra.arkod

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * SLOVENIA (11.10.2026): the same layer over the border. Marko: *"upgrade my Android app, ArcodLayer,
 * to support streams from Slovenia, to have Katastar from Slovenia for any address in Slovenia. So
 * the same principle apply Croatia, now Slovenia, neighboring country."*
 *
 * The Surveying and Mapping Authority of the Republic of Slovenia (GURS) publishes the land cadastre
 * (zemljiški kataster) as open data, no key, on a GeoServer at ipi.eprostor.gov.si:
 *
 *   WMS  wms-si-gurs-kn/wms   SI.GURS.KN:PARCELE, the parcel lines, asked like the Croatian WMS
 *                             (Web Mercator, 512 px) and kept and restyled the same way.
 *   WFS  wfs-si-gurs-kn/wfs   SI.GURS.KN:PARCELE, each parcel with its outline, its cadastral
 *                             municipality (KO_ID), its number (ST_PARCELE) and its area; and
 *                             SI.GURS.KN:KATASTRSKE_OBCINE, the municipalities with their names.
 *
 * One WFS answer gives the parcel AND its outline, so a tap in Slovenia is one request.
 *
 * WHAT SLOVENIA DOES NOT PUBLISH: the owners. GURS's open data leaves out who holds a parcel, and
 * the land register (zemljiška knjiga, e-ZK, sodisce.si) shows owners only after a sign-in. So a
 * Slovenian sheet has the parcel, its municipality, its area and every attribute GURS gives, and
 * says where the owners are read; Imenik, the sniffer and fly-through stay Croatian.
 *
 * A Slovenian parcel's reference is "SI1722-123/4": the prefix keeps it apart from Croatia's
 * six-digit municipalities ("334723-2450"), so every path (Moje čestice, the outlines kept, a file)
 * knows which state to ask. Its id is GURS's own, negative, so it never meets a Croatian id.
 *
 * The attribute names are read tolerantly (several spellings each), and the axis order of the
 * coordinates is read from the numbers themselves (Slovenia's latitudes, 45-47, never overlap its
 * longitudes, 13-17), because neither could be measured from the cloud: eprostor.gov.si was not
 * reachable from the session that wrote this (11.10.2026). No Android here: Test 1 reaches all of it.
 */
object Slovenia {

    const val WMS = "https://ipi.eprostor.gov.si/wms-si-gurs-kn/wms"
    const val WFS = "https://ipi.eprostor.gov.si/wfs-si-gurs-kn/wfs"
    const val PARCELS = "SI.GURS.KN:PARCELE"
    const val MUNICIPALITIES = "SI.GURS.KN:KATASTRSKE_OBCINE"

    /** "SI1722": a Slovenian cadastral municipality, as this app writes it. */
    const val PREFIX = "SI"

    fun isMunicipality(m: String): Boolean = m.length > 2 && m.startsWith(PREFIX) && m.drop(2).all { it.isDigit() }

    fun isReference(ref: String): Boolean = isMunicipality(ref.substringBefore('-', ""))

    /** "SI1722" → "1722", GURS's own KO_ID; anything else as it is. */
    fun koId(m: String): String = if (isMunicipality(m)) m.drop(2) else m

    fun municipality(koId: String): String = PREFIX + koId.trim()

    fun reference(koId: String, number: String): String = "${municipality(koId)}-${number.trim()}"

    // --- where Slovenia is ---------------------------------------------------------------------

    /**
     * Slovenia's border, drawn wide (a few kilometres out all round, the sea off Piran included), as
     * (latitude, longitude). It only decides whether GURS is worth asking: a tile or a tap just over
     * the line costs one empty answer, and a tile on the line is drawn from both states.
     */
    val AROUND: List<Pair<Double, Double>> = listOf(
        45.50 to 13.40, 45.40 to 13.50, 45.40 to 13.90, 45.42 to 14.30, 45.50 to 14.60,
        45.42 to 14.85, 45.35 to 15.10, 45.38 to 15.35, 45.62 to 15.42, 45.72 to 15.55,
        45.82 to 15.78, 46.00 to 15.76, 46.22 to 15.78, 46.28 to 16.02, 46.36 to 16.32,
        46.44 to 16.70, 46.56 to 16.70, 46.70 to 16.52, 46.92 to 16.40, 46.92 to 16.10,
        46.75 to 15.85, 46.76 to 15.50, 46.70 to 15.00, 46.57 to 14.65, 46.50 to 14.10,
        46.58 to 13.65, 46.42 to 13.38, 46.25 to 13.30, 46.10 to 13.40, 45.95 to 13.52,
        45.78 to 13.54, 45.68 to 13.66,
    )

    /**
     * Slovenia drawn narrow: at least five kilometres inside the Croatian border all along it (to the
     * west and north the border itself, where there is no Croatia). Croatia has no parcel in here, so
     * a tile wholly inside is not asked of it.
     */
    val WITHIN: List<Pair<Double, Double>> = listOf(
        45.62 to 13.80, 45.55 to 13.95, 45.58 to 14.25, 45.62 to 14.50, 45.60 to 14.80,
        45.55 to 15.05, 45.60 to 15.25, 45.75 to 15.25, 45.85 to 15.55, 46.05 to 15.55,
        46.20 to 15.60, 46.35 to 15.80, 46.45 to 16.15, 46.60 to 16.30, 46.85 to 16.30,
        46.85 to 16.05, 46.68 to 15.90, 46.70 to 15.50, 46.62 to 15.00, 46.52 to 14.60,
        46.45 to 14.10, 46.52 to 13.70, 46.38 to 13.45, 46.20 to 13.42, 46.05 to 13.55,
        45.85 to 13.62, 45.70 to 13.80,
    )

    /** Croatia's box: the Croatian state is asked only where it could have a parcel. */
    private const val HR_SOUTH = 42.30
    private const val HR_NORTH = 46.60
    private const val HR_WEST = 13.40
    private const val HR_EAST = 19.50

    fun inside(lat: Double, lon: Double): Boolean = Parcels.contains(AROUND, lat, lon)

    fun mayBeCroatia(lat: Double, lon: Double): Boolean =
        lat in HR_SOUTH..HR_NORTH && lon in HR_WEST..HR_EAST && !Parcels.contains(WITHIN, lat, lon)

    /** Whether a box (south, west, north, east) touches Slovenia: a corner, its middle, or a border point inside. */
    fun touches(south: Double, west: Double, north: Double, east: Double): Boolean {
        val probes = listOf(south to west, south to east, north to west, north to east, (south + north) / 2 to (west + east) / 2)
        if (probes.any { (la, lo) -> inside(la, lo) }) return true
        return AROUND.any { (la, lo) -> la in south..north && lo in west..east }
    }

    fun touchesCroatia(south: Double, west: Double, north: Double, east: Double): Boolean {
        if (north < HR_SOUTH || south > HR_NORTH || east < HR_WEST || west > HR_EAST) return false
        val corners = listOf(south to west, south to east, north to west, north to east)
        return !(corners.all { (la, lo) -> Parcels.contains(WITHIN, la, lo) } && WITHIN.none { (la, lo) -> la in south..north && lo in west..east })
    }

    /** Which states draw one slippy tile: (Croatia, Slovenia). */
    fun statesFor(z: Int, x: Int, y: Int): Pair<Boolean, Boolean> {
        val n = 1 shl z
        val west = x * 360.0 / n - 180.0
        val east = (x + 1) * 360.0 / n - 180.0
        fun lat(row: Int) = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2.0 * row / n))))
        val north = lat(y)
        val south = lat(y + 1)
        return touchesCroatia(south, west, north, east) to touches(south, west, north, east)
    }

    // --- the picture ---------------------------------------------------------------------------

    /**
     * The lines and, over them, the numbers (11.10.2026, measured from the phone: GURS's PARCELE has two
     * styles, nep_kn_parcele, the lines alone, and nep_kn_parcele_lbl), so a Slovenian tile carries its
     * numbers as a Croatian one does. GURS draws in green; the restyle keeps only the alpha, so the ink is ours.
     */
    const val TILE_LAYERS = "LAYERS=$PARCELS,$PARCELS&STYLES=nep_kn_parcele,nep_kn_parcele_lbl"

    fun tileUrl(z: Int, x: Int, y: Int): String {
        val b = Parcels.tileBox(z, x, y)
        return "$WMS?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&$TILE_LAYERS" +
            "&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=${Parcels.TILE_PX}&HEIGHT=${Parcels.TILE_PX}" +
            "&FORMAT_OPTIONS=dpi:180&BBOX=${b[0]},${b[1]},${b[2]},${b[3]}"
    }

    /** The picture round a point for [Outline], as the Croatian one. */
    fun pictureUrl(box: DoubleArray, px: Int): String =
        "$WMS?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=$PARCELS&STYLES=" +
            "&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=$px&HEIGHT=$px" +
            "&BBOX=${box[0]},${box[1]},${box[2]},${box[3]}"

    /**
     * WHERE A STATE PAINTS ITS PARCELS FILLED: more than half of a tile covered. The Croatian WMS draws
     * lines only; should GURS's default style fill its parcels, only the edges between colours are
     * kept, so the restyle draws lines over the map and not a curtain. Pixels as ARGB.
     */
    fun linesOnly(pixels: IntArray, w: Int, h: Int): IntArray {
        if (pixels.isEmpty()) return pixels
        val covered = pixels.count { (it ushr 24) and 0xFF >= 60 }
        if (covered * 2 < pixels.size) return pixels
        val out = IntArray(pixels.size)
        fun same(a: Int, b: Int): Boolean {
            val aa = (a ushr 24) and 0xFF
            val ba = (b ushr 24) and 0xFF
            if ((aa >= 60) != (ba >= 60)) return false
            if (aa < 60) return true
            val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
            val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return dr * dr + dg * dg + db * db < 40 * 40
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val p = pixels[i]
            val edge = (x + 1 < w && !same(p, pixels[i + 1])) || (y + 1 < h && !same(p, pixels[i + w]))
            out[i] = if (edge) (0xFF shl 24) or 0x101010 else 0
        }
        return out
    }

    // --- the questions -------------------------------------------------------------------------

    private fun wfs(typeName: String) =
        "$WFS?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&OUTPUTFORMAT=application/json" +
            "&SRSNAME=urn:ogc:def:crs:EPSG::4326&TYPENAMES=$typeName"

    private fun cql(filter: String) = "&CQL_FILTER=" + URLEncoder.encode(filter, "UTF-8").replace("+", "%20")

    /** Every parcel touching a few metres round a point: the tap's one question. */
    fun atUrl(lat: Double, lon: Double): String {
        val d = 0.00005
        return wfs(PARCELS) + "&COUNT=10&BBOX=${lat - d},${lon - d},${lat + d},${lon + d},urn:ogc:def:crs:EPSG::4326"
    }

    /** The municipality under a point, with its name. */
    fun municipalityAtUrl(lat: Double, lon: Double): String {
        val d = 0.00005
        return wfs(MUNICIPALITIES) + "&COUNT=5&BBOX=${lat - d},${lon - d},${lat + d},${lon + d},urn:ogc:def:crs:EPSG::4326"
    }

    fun municipalityUrl(koId: String): String = wfs(MUNICIPALITIES) + "&COUNT=1" + cql("KO_ID=${koId.filter { it.isDigit() }}")

    private fun quoted(s: String) = "'" + s.replace("'", "") + "'"

    /** Parcels by reference ("SI1722-123/4"), as many as are asked, in one request. */
    fun byReferenceUrl(references: List<String>): String {
        val terms = references.filter { isReference(it) }.joinToString(" OR ") { ref ->
            "(KO_ID=${koId(ref.substringBefore('-')).filter { c -> c.isDigit() }} AND ST_PARCELE=${quoted(ref.substringAfter('-'))})"
        }
        return wfs(PARCELS) + "&COUNT=${references.size.coerceAtLeast(1) * 2}" + cql(terms)
    }

    /** The sheet's question: the parcel itself, by its reference; its answer is kept like a Croatian record. */
    fun recordUrl(reference: String): String = byReferenceUrl(listOf(reference))

    /**
     * A NUMBER AS HE TYPES IT, AND AS A BUILDING PARCEL (11.10.2026, the phone's S1): Slovenia writes a
     * building parcel with a star, "*98/5" (Petrina 2's own), and nobody types the star. "98/5" asks both.
     */
    fun numberVariants(n: String): List<String> {
        val bare = n.trim().removePrefix("*")
        return if (bare.isEmpty()) emptyList() else listOf(bare, "*$bare")
    }

    /** Parcels by number in one municipality, each number with and without the star. */
    fun byNumbersUrl(municipality: String, numbers: List<String>): String =
        byReferenceUrl(numbers.flatMap { numberVariants(it) }.distinct().map { reference(koId(municipality), it) })

    /** Numbers in a municipality that begin with what he typed, building parcels ("*98/…") included. */
    fun suggestUrl(koId: String, prefix: String, limit: Int = 12): String {
        val bare = prefix.trim().removePrefix("*")
        return wfs(PARCELS) + "&COUNT=$limit" + cql("KO_ID=${koId(koId).filter { it.isDigit() }} AND " +
            "(ST_PARCELE LIKE ${quoted("$bare%")} OR ST_PARCELE LIKE ${quoted("*$bare%")})")
    }

    // --- the rest of what GURS publishes about a parcel (the phone's S4, 11.10.2026) -------------

    /** Actual land use (dejanska raba), planned use (namenska raba) or soil rating (boniteta), by the parcel's EID. */
    fun byParcelUrl(type: String, eid: String): String = wfs(type) + "&COUNT=20" + cql("EID_PARCELA=${quoted(eid)}")

    const val LAND_USE = "SI.GURS.KN:PARCELE_X_DEJANSKE_RABE"
    const val PLANNED_USE = "SI.GURS.KN:PARCELE_X_NAMENSKE_RABE"
    const val RATING = "SI.GURS.KN:PARCELE_X_BONITETE"
    const val BUILDINGS = "SI.GURS.KN:STAVBE"
    const val BUILDING_PARTS = "SI.GURS.KN:DELI_STAVB"
    const val PUBLIC_OWNERS = "SI.GURS.KN:PARCELE_JAVNI_LASTNIKI"

    /** The buildings over a parcel's box (a box, south west north east). */
    fun buildingsUrl(box: DoubleArray): String =
        wfs(BUILDINGS) + "&COUNT=20&BBOX=${box[0]},${box[1]},${box[2]},${box[3]},urn:ogc:def:crs:EPSG::4326"

    /** The parts of one building (a flat, a shop), with their address; this type has no shape, so by EID only. */
    fun partsUrl(eidBuilding: String): String = wfs(BUILDING_PARTS) + "&COUNT=50" + cql("EID_STAVBA=${quoted(eidBuilding)}")

    /** A public body owning the parcel (the state, a municipality): GURS's WMS layer, asked at one point. */
    fun publicOwnersUrl(lat: Double, lon: Double): String {
        val d = 0.0002
        return "$WMS?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS=$PUBLIC_OWNERS&QUERY_LAYERS=$PUBLIC_OWNERS" +
            "&STYLES=&CRS=EPSG:4326&BBOX=${lat - d},${lon - d},${lat + d},${lon + d}" +
            "&WIDTH=101&HEIGHT=101&I=50&J=50&FEATURE_COUNT=5&INFO_FORMAT=application/json"
    }

    // --- the answers ---------------------------------------------------------------------------

    private val KO_KEYS = listOf("KO_ID", "KO", "SIFKO", "KO_SIFKO", "SIFRA_KO")
    private val NUMBER_KEYS = listOf("ST_PARCELE", "PARCELA", "ST_PARC", "PARC_ST", "STEVILKA")
    private val ID_KEYS = listOf("EID_PARCELA", "PARCELA_ID", "ID_PARCELA", "EID")
    private val AREA_KEYS = listOf("POVRSINA", "RAC_POVRSINA", "POVRSINA_PARCELE", "URADNA_POVRSINA")
    private val NAME_KEYS = listOf("NAZIV", "KO_NAZIV", "IME_KO", "IME", "NAZIV_KO")

    private fun JSONObject.first(keys: List<String>): String? {
        val names = keys().asSequence().toList()
        for (k in keys) {
            val real = names.firstOrNull { it.equals(k, ignoreCase = true) } ?: continue
            val v = opt(real)
            if (v != null && v != JSONObject.NULL && v.toString().isNotBlank()) return v.toString().trim()
        }
        return null
    }

    /** "1722.0" as "1722": a number GeoServer wrote as a decimal. */
    private fun whole(s: String): String {
        // Digits stay digits: GURS's EIDs have 18 of them, more than a Double holds ("…102" read back as "…096").
        if (s.all { it.isDigit() }) return s
        return Regex("""^(\d+)\.0+$""").find(s)?.groupValues?.get(1) ?: s
    }

    /** One point of GeoJSON as (latitude, longitude), whichever order the server wrote it in. */
    private fun latLon(pt: JSONArray): Pair<Double, Double> {
        val a = pt.optDouble(0)
        val b = pt.optDouble(1)
        // Slovenia's latitudes (45-47) never overlap its longitudes (13-17).
        return if (a in 44.0..48.0 && b in 12.0..18.0) a to b else b to a
    }

    private fun rings(geometry: JSONObject?): List<List<Pair<Double, Double>>> {
        geometry ?: return emptyList()
        val c = geometry.optJSONArray("coordinates") ?: return emptyList()
        fun ring(a: JSONArray) = (0 until a.length()).mapNotNull { a.optJSONArray(it)?.let(::latLon) }
        return when (geometry.optString("type")) {
            "Polygon" -> listOfNotNull(c.optJSONArray(0)?.let(::ring))
            "MultiPolygon" -> (0 until c.length()).mapNotNull { c.optJSONArray(it)?.optJSONArray(0)?.let(::ring) }
            else -> emptyList()
        }
    }

    /** The parcels of a WFS answer, each with its outline and a "SI…" reference. */
    fun parseParcels(json: String): List<Parcels.Parcel> {
        val features = runCatching { JSONObject(json).optJSONArray("features") }.getOrNull() ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.optJSONObject(i) ?: return@mapNotNull null
            val p = f.optJSONObject("properties") ?: return@mapNotNull null
            val ko = p.first(KO_KEYS)?.let(::whole) ?: return@mapNotNull null
            val number = p.first(NUMBER_KEYS) ?: return@mapNotNull null
            val eid = p.first(ID_KEYS)?.let(::whole)?.toLongOrNull()
                ?: f.optString("id").substringAfterLast('.').toLongOrNull()
                ?: (ko + number).hashCode().toLong().let { kotlin.math.abs(it) + 1 }
            val area = p.first(AREA_KEYS)?.toDoubleOrNull()?.let { Math.round(it).toInt() }
            Parcels.Parcel(-kotlin.math.abs(eid), number, reference(ko, number), area, rings(f.optJSONObject("geometry")))
        }
    }

    /** The parcel the finger landed in; the nearest middle when none holds the point exactly. */
    fun parcelAt(json: String, lat: Double, lon: Double): Parcels.Parcel? {
        val all = parseParcels(json)
        return Parcels.containing(all, lat, lon)
            ?: all.minByOrNull { val (a, b) = it.middle; (a - lat) * (a - lat) + (b - lon) * (b - lon) }
    }

    /** ("SI1722", "KOPER") from the municipalities' answer. */
    fun parseMunicipality(json: String): Pair<String, String>? {
        val f = runCatching { JSONObject(json).optJSONArray("features")?.optJSONObject(0) }.getOrNull() ?: return null
        val p = f.optJSONObject("properties") ?: return null
        val ko = p.first(KO_KEYS)?.let(::whole) ?: return null
        val name = p.first(NAME_KEYS) ?: ko
        return municipality(ko) to name.uppercase()
    }

    /** The suggestions for a number being typed, as the Croatian ones. */
    fun parseSuggestions(json: String, municipalityName: String, limit: Int = 12): List<Finding.Hit> =
        parseParcels(json).sortedWith(compareBy({ it.number.length }, { it.number })).take(limit).map { p ->
            Finding.Hit(id = p.id.toString(), title = p.number, under = "k.o. $municipalityName (SLO)",
                source = Finding.Source.PARCEL, ref = p.reference)
        }

    const val HEAD = "KATASTER SLOVENIJE (GURS)"

    /** Every heading GURS's own rows carry; such a row belongs on the "uporaba" tab. */
    const val TAG = "(GURS)"

    /** Where a private owner is read: the land register, after signing in, or an extract from a court or a notary. */
    const val OWNERS_NOTE = "Vlasnike fizičke osobe Slovenija (GURS) ne objavljuje. Vidi ih slovenska zemljišna knjiga (e-ZK, uz prijavu) " +
        "ili izvadak iz zemljišne knjige na sudu ili kod javnog bilježnika; za to trebaju k.o. i broj čestice odozgo."

    /** Not for a person: the shapes, the ids and codes, the other three languages, the coordinates. */
    private val SKIP = Regex("(?i)^(geom.*|.*_geom|the_geom|shape.*|bbox|featureid|eid.*|.*_id|.*_sifra|.*_(en|it|hu)|.*_opis_sl|e_cen|n_cen|naziv|ko_id|st_parcele|povrsina)$")

    /** "1613 KUŽELJ" → "KUŽELJ": GURS writes a parcel's k.o. number first. */
    fun bareName(name: String): String = name.trim().replace(Regex("""^\d+\s+"""), "")

    /**
     * GURS's attributes worth a row: a coded field only as its Slovene name ("UPRAVNI_STATUSI_NAZIV_SL" as
     * "upravni statusi"), never its raw code beside it; nulls left out. As (label, value).
     */
    fun attributeRows(p: JSONObject): List<Pair<String, String>> {
        val keys = p.keys().asSequence().toList()
        val named = keys.filter { it.endsWith("_NAZIV_SL", ignoreCase = true) }.map { it.dropLast(9).uppercase() }.toSet()
        return keys.sorted()
            .filterNot { k -> SKIP.matches(k) && !k.endsWith("_NAZIV_SL", ignoreCase = true) }
            .filterNot { k -> k.uppercase() in named }
            .mapNotNull { k -> p.opt(k)?.takeIf { it != JSONObject.NULL && it !is JSONObject && it !is JSONArray }?.let { k to it.toString().trim() } }
            .filter { it.second.isNotBlank() && it.second != "null" }
            .map { (k, v) -> k.lowercase().removeSuffix("_naziv_sl").replace('_', ' ') to whole(v) }
    }

    private fun features(json: String): List<JSONObject> {
        val a = runCatching { JSONObject(json).optJSONArray("features") }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("properties") }
    }

    /** The parcel's EID in GURS's answer, for the questions asked by parcel. */
    fun eidOf(json: String, number: String? = null): String? =
        features(json).firstOrNull { number == null || it.first(NUMBER_KEYS) == number }?.first(ID_KEYS)?.let(::whole)

    private fun share(p: JSONObject): String = p.opt("DELEZ")?.takeIf { it != JSONObject.NULL }?.let { "${whole(it.toString())} %" }.orEmpty()

    /** Actual land use: "poseljena zemljišča, tloris stavbe  54.8 %", one row each. */
    fun landUseRows(json: String): List<Parcels.SheetRow> = features(json).mapNotNull { p ->
        val name = p.optString("DEJANSKE_RABE_NAZIV").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
        Parcels.SheetRow("DEJANSKA RABA $TAG", name, share(p))
    }

    /** Planned use, from the municipality's plan. */
    fun plannedUseRows(json: String): List<Parcels.SheetRow> = features(json).mapNotNull { p ->
        val name = p.optString("VRSTA_NAMENSKE_RABE_NAZIV_SL").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
        Parcels.SheetRow("NAMENSKA RABA $TAG", name, share(p))
    }

    /** Soil rating over its part of the parcel. */
    fun ratingRows(json: String): List<Parcels.SheetRow> = features(json).mapNotNull { p ->
        val b = p.opt("BONITETA")?.takeIf { it != JSONObject.NULL }?.toString() ?: return@mapNotNull null
        val m2 = p.optString("POVRSINA").toDoubleOrNull()?.let { "${Math.round(it)} m²" }.orEmpty()
        Parcels.SheetRow("BONITETA $TAG", "boniteta ${whole(b)}", m2)
    }

    /** A building GURS lists over the parcel: its number, EID, and where its outline's middle is. */
    data class Building(val number: String, val eid: String, val middle: Pair<Double, Double>?, val row: Parcels.SheetRow)

    /** The buildings of an answer whose outline's middle lies in the parcel ([rings] empty: every one). */
    fun buildings(json: String, rings: List<List<Pair<Double, Double>>>): List<Building> {
        val a = runCatching { JSONObject(json).optJSONArray("features") }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { f ->
            val p = f.optJSONObject("properties") ?: return@mapNotNull null
            val number = p.opt("ST_STAVBE")?.toString()?.let(::whole) ?: return@mapNotNull null
            val eid = p.first(listOf("EID_STAVBA", "EID"))?.let(::whole).orEmpty()
            val shape = listOf(f.optJSONObject("geometry"), p.optJSONObject("OBRIS_GEOM"), p.optJSONObject("NADZEMNI_GEOM")).firstOrNull { it != null }
            val ring = rings(shape).firstOrNull().orEmpty()
            val middle = if (ring.isEmpty()) null else ring.sumOf { it.first } / ring.size to ring.sumOf { it.second } / ring.size
            if (rings.isNotEmpty() && middle != null && rings.none { Parcels.contains(it, middle.first, middle.second) }) return@mapNotNull null
            fun s(k: String) = p.opt(k)?.takeIf { it != JSONObject.NULL }?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(::whole)
            val under = listOfNotNull(
                s("TIPI_STAVB_NAZIV_SL"),
                s("STEVILO_ETAZ")?.let { "etaža $it" },
                s("STEVILO_STANOVANJ")?.let { "stanova $it" },
                s("LETO_OBNOVE_STREHE")?.let { "krov obnovljen $it" },
                s("LETO_OBNOVE_FASADE")?.let { "fasada obnovljena $it" },
                listOfNotNull(s("ELEKTRIKA_NAZIV_SL")?.let { "struja $it" }, s("VODOVOD_NAZIV_SL")?.let { "voda $it" },
                    s("KANALIZACIJA_NAZIV_SL")?.let { "kanalizacija $it" }).joinToString(", ").takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Building(number, eid, middle, Parcels.SheetRow("STAVBA $number $TAG", "stavba $number", s("LETO_IZGRADNJE")?.let { "izgrađena $it" }.orEmpty(), under))
        }
    }

    /** The parts of a building (a flat, a shop) as rows, and the address they give. */
    fun partRows(json: String, building: String): Pair<List<Parcels.SheetRow>, String?> {
        val ps = features(json)
        val rows = ps.map { p ->
            fun s(k: String) = p.opt(k)?.takeIf { it != JSONObject.NULL }?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(::whole)
            Parcels.SheetRow("STAVBA $building $TAG",
                "dio ${s("ST_DELA_STAVBE") ?: "?"}: ${s("VRSTE_DEJANSKIH_RAB_DEL_ST_NAZIV_SL") ?: "dio stavbe"}",
                (s("UPORABNA_POVRSINA") ?: s("POVRSINA"))?.let { "$it m²" }.orEmpty(),
                listOfNotNull(s("NASLOV_DELA_STAVBE"), s("ETAZE_DELA_STAVBE")?.let { "etaža $it" }).joinToString(" · "))
        }
        return rows to ps.firstNotNullOfOrNull { p -> p.optString("NASLOV_DELA_STAVBE").takeIf { it.isNotBlank() && it != "null" } }
    }

    /** A public owner (the state, a municipality), from the WMS layer of public owners; empty for anyone else. */
    fun publicOwnerRows(json: String): List<Parcels.SheetRow> = features(json).mapNotNull { p ->
        val said = attributeRows(p).filterNot { (k, _) -> k.contains("datum") }
        if (said.isEmpty()) null
        else Parcels.SheetRow("${Parcels.HEAD_REGISTRY} (SLO) · javni vlasnik", said.first().second, "", said.drop(1).joinToString(" · ") { "${it.first}: ${it.second}" })
    }

    /** What [ParcelNet] adds to a Slovenian record from the other questions: the rows, and an address when a building gave one. */
    fun withMore(record: Parcels.Record, rows: List<Parcels.SheetRow>, address: String?): Parcels.Record =
        record.copy(extra = record.extra + rows, address = address ?: record.address)

    /**
     * THE SHEET OF A SLOVENIAN PARCEL: the same Record the Croatian sheet shows. Its municipality and
     * area; as "land use", every attribute GURS gave, one row each, so whatever GURS publishes is on
     * the sheet whatever it is called; no possession sheets, since GURS gives no owners.
     */
    fun parseRecord(json: String, reference: String, municipalityName: String? = null): Parcels.Record? {
        val features = runCatching { JSONObject(json).optJSONArray("features") }.getOrNull() ?: return null
        val number = reference.substringAfter('-')
        val f = (0 until features.length()).mapNotNull { features.optJSONObject(it) }.firstOrNull { o ->
            o.optJSONObject("properties")?.first(NUMBER_KEYS) == number
        } ?: features.optJSONObject(0) ?: return null
        val p = f.optJSONObject("properties") ?: return null
        val ko = p.first(KO_KEYS)?.let(::whole) ?: koId(reference.substringBefore('-'))
        val area = p.first(AREA_KEYS)?.toDoubleOrNull()?.let { Math.round(it).toString() }.orEmpty()
        val attributes = attributeRows(p)
        return Parcels.Record(
            number = p.first(NUMBER_KEYS) ?: number,
            municipality = bareName(municipalityName ?: p.first(NAME_KEYS) ?: ko).uppercase(),
            municipalityNumber = municipality(ko),
            address = "",
            areaM2 = area,
            uses = attributes.map { (k, v) -> Parcels.Use(k, v, "") },
            sheets = emptyList(),
            landBooks = emptyList(),
        )
    }

    /**
     * The Slovenian sheet as rows. "uporaba": the parcel, its land use, planned use, soil rating and its
     * buildings with their parts and address; "posjedovni" and "vlasnički": a public owner when there is
     * one, and where a private owner is read.
     */
    fun sheetRows(record: Parcels.Record): List<Parcels.SheetRow> = buildList {
        add(Parcels.SheetRow(HEAD, "k.o. ${record.municipality} (${koId(record.municipalityNumber)})", "",
            listOfNotNull("površina ${Parcels.areaLabel(record.areaM2.toIntOrNull())}", record.address.takeIf { it.isNotBlank() }).joinToString(" · ")))
        record.uses.forEach { add(Parcels.SheetRow(HEAD, it.name, it.areaM2)) }
        val owners = record.extra.filter { Parcels.tabOf(it) == Parcels.Tab.OWNER }
        addAll(record.extra - owners.toSet())
        add(Parcels.SheetRow("${Parcels.HEAD_POSSESSION} (SLO)", "posjednici", "", OWNERS_NOTE))
        addAll(owners)
        add(Parcels.SheetRow("${Parcels.HEAD_REGISTRY} (SLO)", if (owners.isEmpty()) "vlasnici" else "drugi vlasnici", "", OWNERS_NOTE))
    }
}
