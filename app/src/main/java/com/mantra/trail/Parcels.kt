package com.mantra.trail

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * THE CADASTRE: EVERY PARCEL IN CROATIA, ITS NUMBER, AND WHO HOLDS IT (27.9.2026).
 *
 * Marko, 27.9.2026, of the Kukljica map on the portal: *"the same functionality with ability to
 * click on any parcel, any number in the map, and then it opens popup with all the data about that
 * parcel, especially the person who is owner ... This is all public ... user can type or select any
 * of parcels and highlight them, same as my land is highlighted."* And then: *"the popup with the
 * owner info has a simple tick mark highlight, and next to that highlight multiple colors ... we
 * already have color swatches for the trails, so the same colors."*
 *
 * Three public services of the State Geodetic Administration, no key, measured on 27.9.2026:
 *
 *   WMS  api.uredjenazemlja.hr/.../cp_wms   the parcel lines and numbers as pictures. The portal's
 *                                           layer; here asked in Web Mercator, 512 px at 180 dpi,
 *                                           so a tile is sharp on a phone and the numbers are the
 *                                           size the portal shows them.
 *   WFS  api.uredjenazemlja.hr/.../cp/wfs   the parcels as polygons with their number, area and
 *                                           cadastral reference "334723-2450" (municipality -
 *                                           number). What a tap asks, and what search asks.
 *   OSS  oss.uredjenazemlja.hr/.../parcel-info
 *                                           the record behind a parcel id: address, land use,
 *                                           possession sheets and the possessors with their share
 *                                           and address. It refuses a browser (403 with an Origin
 *                                           header) and answers an app.
 *
 * No Android here, so Test 1 can attack every line of it on a desk.
 */
object Parcels {

    const val WMS = "https://api.uredjenazemlja.hr/services/inspire/cp_wms/wms"
    const val WFS = "https://api.uredjenazemlja.hr/services/inspire/cp/wfs"
    const val OSS = "https://oss.uredjenazemlja.hr/oss/public/cad/parcel-info"

    /** Below this the lines are a grey smear and the numbers are not drawn at all. */
    const val MIN_ZOOM = 14

    /** A tap asks for a parcel only from here on, where a finger covers one parcel and not forty. */
    const val TAP_ZOOM = 15

    const val MAX_ZOOM = 21
    const val TILE_PX = 512

    /** The path VTM is given; it becomes a WMS request in [resolve]. */
    const val TILE_PATH = "/{Z}/{X}/{Y}"

    private const val HALF_WORLD = 20037508.342789244

    // --- the picture ----------------------------------------------------------------------------

    /** One slippy tile as a Web Mercator box: west, south, east, north, in metres. */
    fun tileBox(z: Int, x: Int, y: Int): DoubleArray {
        val size = 2 * HALF_WORLD / (1 shl z)
        return doubleArrayOf(
            -HALF_WORLD + x * size,
            HALF_WORLD - (y + 1) * size,
            -HALF_WORLD + (x + 1) * size,
            HALF_WORLD - y * size,
        )
    }

    fun tileUrl(z: Int, x: Int, y: Int): String {
        val b = tileBox(z, x, y)
        return "$WMS?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=cp:CP.CadastralParcel&STYLES=" +
            "&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=$TILE_PX&HEIGHT=$TILE_PX" +
            "&FORMAT_OPTIONS=dpi:180&BBOX=${b[0]},${b[1]},${b[2]},${b[3]}"
    }

    /**
     * VTM builds a tile address as base + "/z/x/y" and nothing else; a WMS wants a box. So the
     * cadastre's tiles are asked for at "WMS/z/x/y" and turned into the real request here, on the
     * way out. Any other address passes through untouched.
     */
    fun resolve(url: String): String {
        if (!url.startsWith("$WMS/")) return url
        val parts = url.removePrefix("$WMS/").split('/')
        if (parts.size != 3) return url
        val z = parts[0].toIntOrNull() ?: return url
        val x = parts[1].toIntOrNull() ?: return url
        val y = parts[2].toIntOrNull() ?: return url
        return tileUrl(z, x, y)
    }

    fun isCadastre(url: String): Boolean = url.startsWith(WMS)

    /**
     * THE INK (27.9.2026). The state draws its lines black, which is right on a pale street map and
     * invisible on a photograph of a forest or on the night theme, so each tile is recoloured on the
     * phone: dark over the light maps, sand over the dark ones. Half-transparent either way, as the
     * portal draws it, so the map reads through.
     */
    const val INK_DARK = 0xFF15171AL
    const val INK_LIGHT = 0xFFF2DDB4L
    const val INK_ALPHA = 0.62

    /** The selection's colour: cyan, which none of the five highlight colours is. */
    const val SELECTION = 0xFF22D3EEL

    fun inkFor(layerId: String, theme: String, googleView: String? = null): Long = when {
        layerId == Layers.IMAGERY.id -> INK_LIGHT
        googleView == "satellite" -> INK_LIGHT
        googleView != null -> INK_DARK
        theme == "NEWTRON" || theme == "TRONRENDER" -> INK_LIGHT
        else -> INK_DARK
    }

    /** One pixel of the state's tile, recoloured: its own alpha kept, scaled, in the ink. */
    fun recolour(argb: Int, ink: Long): Int {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha == 0) return 0
        val a = (alpha * INK_ALPHA).toInt().coerceIn(0, 255)
        return (a shl 24) or (ink.toInt() and 0x00FFFFFF)
    }

    // --- the questions ----------------------------------------------------------------------------

    private fun wfs(typeName: String) =
        "$WFS?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&OUTPUTFORMAT=application/json" +
            "&SRSNAME=urn:ogc:def:crs:EPSG::4326&TYPENAMES=$typeName"

    /**
     * WHAT IS UNDER A POINT, ASKED OF THE PICTURE ITSELF (27.9.2026). The WFS took fourteen seconds
     * for a box twenty metres across, and thirty with an Oracle "maximum open cursors" error when
     * the state's database was busy; the WMS's own GetFeatureInfo answers in a fifth of a second.
     * It will only speak text/plain or HTML (JSON and GML are "not allowed"), so [parseInfo] reads
     * the plain text. A 101-pixel box a few metres across, asked about its middle pixel.
     */
    fun infoUrl(lat: Double, lon: Double, layer: String = "cp:CP.CadastralParcel"): String {
        val d = 0.0002
        return "$WMS?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS=$layer&QUERY_LAYERS=$layer" +
            "&STYLES=&CRS=EPSG:4326&BBOX=${lat - d},${lon - d},${lat + d},${lon + d}" +
            "&WIDTH=101&HEIGHT=101&I=50&J=50&FEATURE_COUNT=1&INFO_FORMAT=text/plain"
    }

    /** The first feature of a plain-text answer as KEY to value; null when nothing was there. */
    fun parseInfo(text: String): Map<String, String>? {
        if (text.contains("no features were found")) return null
        val block = text.split(Regex("-{10,}")).drop(1).firstOrNull() ?: return null
        val pairs = block.lines().mapNotNull { line ->
            val at = line.indexOf(" = ")
            if (at <= 0) null else line.substring(0, at).trim() to line.substring(at + 3).trim()
        }.toMap()
        return pairs.ifEmpty { null }
    }

    /** The parcel a GetFeatureInfo found: id, number and municipality. No shape: that is slow. */
    fun parcelFromInfo(text: String): Parcel? {
        val info = parseInfo(text) ?: return null
        val id = info["ID"]?.toLongOrNull() ?: return null
        val number = info["BROJ_CESTICE"] ?: return null
        val ko = info["MATICNI_BROJ_KO"] ?: return null
        return Parcel(id, number, "$ko-$number", null, emptyList())
    }

    /** "334723-KUKLJICA" from the zoning layer's plain text. */
    fun zoningFromInfo(text: String): Pair<String, String>? {
        val label = parseInfo(text)?.get("LABEL") ?: return null
        if (!label.contains('-')) return null
        return label.substringBefore('-') to label.substringAfter('-')
    }

    /** OSS's own search, a number inside a municipality: a tenth of a second. */
    fun searchUrl(number: String, municipality: String): String =
        "https://oss.uredjenazemlja.hr/oss/public/search-cad-parcels/parcel-numbers?search=" +
            URLEncoder.encode(number, "UTF-8") + "&municipalityRegNum=" + URLEncoder.encode(municipality, "UTF-8")

    /** The id of exactly that number; the search also offers numbers that merely begin with it. */
    fun parseSearch(json: String, number: String): Long? {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .firstOrNull { it.optString("value1") == number }
            ?.optString("key1")?.toLongOrNull()
    }

    /** Parcels by their full references, "334723-2450", as many as are asked at once. */
    fun byReferenceUrl(references: List<String>): String {
        val quoted = references.joinToString(",") { "'" + it.replace("'", "") + "'" }
        val filter = "nationalCadastralReference IN ($quoted)"
        return wfs("cp:CadastralParcel") + "&CQL_FILTER=" + URLEncoder.encode(filter, "UTF-8").replace("+", "%20")
    }

    fun recordUrl(parcelId: Long): String = "$OSS?parcelId=$parcelId"

    /**
     * What he typed, as parcel numbers: "2450, 2449/3 2451" is three. Anything that is not a
     * number or a number with a part after the stroke is dropped rather than sent.
     */
    fun numbers(text: String): List<String> =
        text.split(',', ';', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { Regex("""\d{1,6}(/\d{1,4})?""").matches(it) }
            .distinct()

    // --- the answers ------------------------------------------------------------------------------

    /** A ring is a list of (latitude, longitude). */
    data class Parcel(
        val id: Long,
        val number: String,
        val reference: String,
        val areaM2: Int?,
        val rings: List<List<Pair<Double, Double>>>,
    ) {
        /** "334723", the cadastral municipality's register number. */
        val municipality: String get() = reference.substringBefore('-', "")

        /** The middle of the outer ring's corners, where the map goes when it is found. */
        val middle: Pair<Double, Double>
            get() {
                val ring = rings.firstOrNull().orEmpty()
                if (ring.isEmpty()) return 0.0 to 0.0
                return ring.sumOf { it.first } / ring.size to ring.sumOf { it.second } / ring.size
            }
    }

    fun parseParcels(json: String): List<Parcel> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.optJSONObject(i) ?: return@mapNotNull null
            val p = f.optJSONObject("properties") ?: return@mapNotNull null
            val localId = p.optJSONObject("inspireId")?.optString("localId").orEmpty()
                .ifEmpty { f.optString("id") }
            val id = localId.substringAfterLast('.').toLongOrNull() ?: return@mapNotNull null
            val area = p.optJSONObject("areaValue")?.let { if (it.has("value")) it.optInt("value") else null }
            Parcel(
                id = id,
                number = p.optString("label"),
                reference = p.optString("nationalCadastralReference"),
                areaM2 = area,
                rings = outerRings(f.optJSONObject("geometry")),
            )
        }
    }

    private fun outerRings(geometry: JSONObject?): List<List<Pair<Double, Double>>> {
        geometry ?: return emptyList()
        val c = geometry.optJSONArray("coordinates") ?: return emptyList()
        fun ring(a: JSONArray): List<Pair<Double, Double>> = (0 until a.length()).mapNotNull { k ->
            val pt = a.optJSONArray(k) ?: return@mapNotNull null
            pt.optDouble(1) to pt.optDouble(0)
        }
        return when (geometry.optString("type")) {
            "Polygon" -> listOfNotNull(c.optJSONArray(0)?.let(::ring))
            "MultiPolygon" -> (0 until c.length()).mapNotNull { c.optJSONArray(it)?.optJSONArray(0)?.let(::ring) }
            else -> emptyList()
        }
    }

    /** Ray casting, on the plane: at the size of a parcel the earth is flat enough. */
    fun contains(ring: List<Pair<Double, Double>>, lat: Double, lon: Double): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val (yi, xi) = ring[i]
            val (yj, xj) = ring[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** The parcel the finger landed in, if the finger landed in one and not in the sea. */
    fun containing(parcels: List<Parcel>, lat: Double, lon: Double): Parcel? =
        parcels.firstOrNull { p -> p.rings.any { contains(it, lat, lon) } }

    data class Owner(val name: String, val share: String, val address: String)

    data class Sheet(val number: String, val owners: List<Owner>)

    data class Use(val name: String, val areaM2: String, val sheet: String)

    /** A land-registry unit the parcel is entered in: where its legal owners are written. */
    data class LandBook(val unit: String, val book: String, val office: String, val kind: String)

    data class Record(
        val number: String,
        val municipality: String,
        val municipalityNumber: String,
        val address: String,
        val areaM2: String,
        val uses: List<Use>,
        val sheets: List<Sheet>,
        val landBooks: List<LandBook> = emptyList(),
    )

    fun parseRecord(json: String): Record {
        val o = JSONObject(json)
        val books = o.optJSONArray("lrUnitsFromParcelLinks") ?: JSONArray()
        val parts = o.optJSONArray("parcelParts") ?: JSONArray()
        val sheets = o.optJSONArray("possessionSheets") ?: JSONArray()
        return Record(
            number = o.optString("parcelNumber"),
            municipality = o.optString("cadMunicipalityName"),
            municipalityNumber = o.optString("cadMunicipalityRegNum"),
            address = o.optString("address"),
            areaM2 = o.optString("area"),
            uses = (0 until parts.length()).mapNotNull { i ->
                val p = parts.optJSONObject(i) ?: return@mapNotNull null
                Use(p.optString("name"), p.optString("area"), p.optString("possessionSheetNumber"))
            },
            sheets = (0 until sheets.length()).mapNotNull { i ->
                val s = sheets.optJSONObject(i) ?: return@mapNotNull null
                val people = s.optJSONArray("possessors") ?: JSONArray()
                Sheet(
                    number = s.optString("possessionSheetNumber"),
                    owners = (0 until people.length()).mapNotNull { k ->
                        val w = people.optJSONObject(k) ?: return@mapNotNull null
                        Owner(w.optString("name").trim(), w.optString("ownership"), w.optString("address").trim())
                    },
                )
            },
            landBooks = (0 until books.length()).mapNotNull { i ->
                val b = books.optJSONObject(i) ?: return@mapNotNull null
                LandBook(b.optString("lrUnitNumber"), b.optString("mainBookName"),
                    b.optString("institutionName"), b.optString("lrUnitTypeName"))
            },
        )
    }

    /**
     * THE SHEET AS A TEXT FILE (27.9.2026): *"TXT ... export to the local file system TXT file
     * with parcel number and all the data inside."* Plain lines a person can read and a program can
     * split, everything the card shows and the outline's corners.
     */
    fun toText(parcel: Parcel, record: Record?, madeAt: String): String = buildString {
        appendLine("PARCEL ${parcel.number}")
        appendLine("cadastral reference: ${parcel.reference}")
        if (record != null) {
            appendLine("cadastral municipality: ${record.municipality} (${record.municipalityNumber})")
            appendLine("area: ${areaLabel(record.areaM2.toIntOrNull() ?: parcel.areaM2)}")
            if (record.address.isNotBlank()) appendLine("address: ${record.address}")
            appendLine()
            appendLine("LAND USE")
            record.uses.forEach { appendLine("  ${it.name}, ${it.areaM2} m², possession sheet ${it.sheet}") }
            record.sheets.forEach { sheet ->
                appendLine()
                appendLine("POSSESSION SHEET ${sheet.number}")
                sheet.owners.forEach { o ->
                    appendLine("  ${o.name}  ${o.share}")
                    if (o.address.isNotBlank()) appendLine("    ${o.address}")
                }
            }
            record.landBooks.forEach { b ->
                appendLine()
                appendLine("LAND REGISTRY")
                appendLine("  z.k. uložak ${b.unit}, k.o. ${b.book}, ${b.kind}")
                appendLine("  ${b.office}")
            }
        } else {
            appendLine("area: ${areaLabel(parcel.areaM2)}")
            appendLine("(the record could not be read when this was saved)")
        }
        val ring = parcel.rings.firstOrNull().orEmpty()
        if (ring.isNotEmpty()) {
            appendLine()
            appendLine("OUTLINE (latitude, longitude)")
            ring.forEach { appendLine("  %.6f, %.6f".format(java.util.Locale.ROOT, it.first, it.second)) }
        }
        appendLine()
        appendLine("source: State Geodetic Administration, oss.uredjenazemlja.hr; saved $madeAt by Mantra Trail")
    }

    /** "parcel 334723-2449_3.txt": the reference, with the stroke a file name cannot hold. */
    fun textFileName(parcel: Parcel): String = "parcel ${parcel.reference.replace('/', '_')}.txt"

    /** "12401" as "12 401 m²", the way a surveyor writes it. */
    fun areaLabel(m2: Int?): String {
        m2 ?: return "area unknown"
        val grouped = m2.toString().reversed().chunked(3).joinToString(" ").reversed()
        return "$grouped m²"
    }

    // --- what he marked --------------------------------------------------------------------------

    /**
     * A PARCEL HE HIGHLIGHTED, WITH ITS SHAPE (27.9.2026). The shape is kept, not only the number,
     * so his land is still drawn on a mountain with no signal, where the cadastre cannot be asked.
     */
    data class Mark(
        val reference: String,
        val number: String,
        val colour: Long,
        val rings: List<List<Pair<Double, Double>>>,
        val id: Long = 0L,
    )

    /**
     * One mark a line: reference | number | colour | id | rings. Corners are "lat,lon" joined by
     * ';', rings by '#'. Six decimals is ten centimetres, finer than the cadastre draws.
     */
    fun encode(marks: List<Mark>): String = marks.joinToString("\n") { m ->
        val rings = m.rings.joinToString("#") { ring ->
            ring.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.ROOT, it.first, it.second) }
        }
        listOf(m.reference.replace("|", ""), m.number.replace("|", ""),
            java.lang.Long.toHexString(m.colour), m.id.toString(), rings).joinToString("|")
    }

    fun decode(text: String): List<Mark> = text.split("\n").mapNotNull { line ->
        val f = line.split("|")
        if (f.size != 5 || f[0].isBlank()) return@mapNotNull null
        val colour = f[2].toLongOrNull(16) ?: return@mapNotNull null
        val rings = f[4].split("#").filter { it.isNotBlank() }.map { ring ->
            ring.split(";").mapNotNull { pt ->
                val ll = pt.split(",")
                val a = ll.getOrNull(0)?.toDoubleOrNull()
                val b = ll.getOrNull(1)?.toDoubleOrNull()
                if (a != null && b != null) a to b else null
            }
        }.filter { it.size >= 3 }
        // A mark may have no shape yet: the WFS was slow when it was made, and it is asked again.
        Mark(f[0], f[1], colour, rings, f[3].toLongOrNull() ?: 0L)
    }

    /** Marked, re-marked in a new colour, or unmarked: one parcel is in the list at most once. */
    fun withMark(marks: List<Mark>, mark: Mark): List<Mark> =
        marks.filterNot { it.reference == mark.reference } + mark

    fun without(marks: List<Mark>, reference: String): List<Mark> =
        marks.filterNot { it.reference == reference }

    fun markOf(parcel: Parcel, colour: Long): Mark =
        Mark(parcel.reference, parcel.number, colour, parcel.rings, parcel.id)

    /** The marks still waiting for their shape. */
    fun shapeless(marks: List<Mark>): List<Mark> = marks.filter { it.rings.isEmpty() }

    /** Shapes that arrived, put into the marks they belong to; every other mark untouched. */
    fun withShapes(marks: List<Mark>, found: List<Parcel>): List<Mark> = marks.map { m ->
        val p = found.firstOrNull { it.reference == m.reference }
        if (m.rings.isEmpty() && p != null && p.rings.isNotEmpty()) m.copy(rings = p.rings, id = if (m.id == 0L) p.id else m.id) else m
    }
}
