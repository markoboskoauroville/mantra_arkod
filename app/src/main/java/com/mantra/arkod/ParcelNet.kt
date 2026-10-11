package com.mantra.arkod

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * THE CADASTRE OVER THE WIRE (27.9.2026): the three questions of [Parcels], asked with java.net,
 * the stack that fetches every other thing in this app, and each answer or a sentence saying why
 * there is none. The arithmetic and the parsing are in Parcels.kt, where Test 1 reaches them.
 */
object ParcelNet {

    class Refused(message: String) : Exception(message)

    private fun get(url: String, readMs: Int = 25_000): String {
        val open = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = readMs
            setRequestProperty("User-Agent", Layers.USER_AGENT)
        }
        try {
            val code = open.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // The state's own reason, when it gives one: "ORA-01000: maximum open cursors
                // exceeded" was its database failing for every request (30.9.2026, 03:10).
                val said = runCatching { open.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                val why = Parcels.stateReason(said)
                // A 404 is an answer (the land registry has no such folio), not a service down.
                if (code == HttpURLConnection.HTTP_NOT_FOUND) Services.ok(url) else Services.failed(url, why ?: "answered $code")
                throw Refused("katastar je odgovorio $code" + (why?.let { " ($it)" } ?: ""))
            }
            return open.inputStream.bufferedReader().use { it.readText() }.also { Services.ok(url) }
        } catch (e: java.io.IOException) {
            // No connection, a timeout: the service did not answer (v14, its light goes red).
            Services.failed(url, e.message ?: e.javaClass.simpleName)
            throw e
        } finally {
            open.disconnect()
        }
    }

    /**
     * AN ANSWER THAT IS KEPT (29.9.2026): a record or a folio, read from the state when there is a
     * signal and kept, and read off the phone when there is none, so the sheets of his parcels
     * open on a hillside. [kept] says when a kept one was read, for the sheet to show.
     */
    @Volatile
    var kept: Long? = null
        private set

    /** The record shown came from the app's server (Fetch when available), not from the phone. */
    @Volatile
    var keptFar: Boolean = false
        private set

    private fun getKept(url: String): String = try {
        get(url).also {
            ArkodCache.keepAnswer(url, it)
            kept = null
            keptFar = false
        }
    } catch (e: Exception) {
        // A parcel cache keeps the state's answers too (v5): a sheet from it opens with no signal
        // even after the ARKOD tiles on the phone were cleared.
        val old = ArkodCache.answer(url) ?: ParcelCaches.answer(url)
        if (old != null) {
            kept = old.second
            keptFar = false
            old.first
        } else {
            // FETCH WHEN AVAILABLE (row 38): what the server fetched later, for anyone; then kept here too
            val far = later(url) ?: throw e
            ArkodCache.keepAnswer(url, far.first)
            kept = far.second
            keptFar = true
            far.first
        }
    }

    /** The server's answer to one of the state's addresses, with when it was fetched; null when it has none. */
    private fun later(url: String): Pair<String, Long>? = runCatching {
        val c = (URL(Later.answerUrl(url)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", Layers.USER_AGENT)
        }
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) null
            else c.inputStream.bufferedReader().use { it.readText() } to Later.fetchedAt(c.getHeaderField("X-Fetched-At"))
        } finally { c.disconnect() }
    }.getOrNull()

    /** Hand an address the state did not answer to the server; the line the button shows. */
    fun want(url: String): String = runCatching {
        val c = (URL(Later.WANT).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("User-Agent", Layers.USER_AGENT)
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            c.outputStream.use { it.write(Later.wantBody(url).toByteArray()) }
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            Later.said(code, body)
        } finally { c.disconnect() }
    }.getOrElse { "the server could not take it: no signal" }

    /**
     * CACHE KING (v11): searches, suggestions and the land books are kept too, and read off the
     * phone when the state does not answer. Quiet: it does not touch [kept], which a sheet shows.
     */
    private fun getKeptQuiet(url: String, readMs: Int = 25_000): String = try {
        get(url, readMs).also { ArkodCache.keepAnswer(url, it) }
    } catch (e: Exception) {
        ArkodCache.answer(url)?.first ?: ParcelCaches.answer(url)?.first ?: throw e
    }

    /**
     * THE LIGHT CHECK (v14): one small question to a service that has been quiet, so its light says
     * how it is now. Kukljica's own parcel 1358/3 is the question. Never Google: every request there
     * is on his key. The answer is not kept; [get] reports it to [Services].
     */
    suspend fun check(service: Services.Service) = withContext(Dispatchers.IO) {
        runCatching {
            when (service) {
                Services.Service.WMS -> get(Parcels.infoUrl(44.01732, 15.24945), readMs = 15_000)
                Services.Service.OSS -> get(Parcels.recordUrl(6436001L), readMs = 15_000)
                Services.Service.ZK -> get(Parcels.booksUrl("KUKLJICA"), readMs = 15_000)
                Services.Service.WFS -> get(Parcels.byReferenceUrl(listOf("334723-1358/3")), readMs = 45_000)
                Services.Service.OSM -> get("https://tile.openstreetmap.org/0/0/0.png", readMs = 15_000)
                // Ljubljana's middle, Kongresni trg: one parcel, one small answer (Slovenia, v23).
                Services.Service.GURS -> get(Slovenia.atUrl(46.0505, 14.5036), readMs = 20_000)
                Services.Service.GOOGLE -> Unit
            }
        }
        Unit
    }

    /**
     * CHECK NOW, UNTIL IT ANSWERS (v19): up to three tries ([Services.CHECK_WAITS]), each said through
     * [onTry]; true as soon as the service answers.
     */
    suspend fun checkUntilBack(service: Services.Service, onTry: (Int) -> Unit): Boolean {
        Services.CHECK_WAITS.forEachIndexed { i, wait ->
            if (wait > 0) kotlinx.coroutines.delay(wait)
            onTry(i + 1)
            check(service)
            if (Services.light(Services.health.value[service]) == Services.Light.GREEN) return true
        }
        return false
    }

    /**
     * FOR THE SNIFFER (v12): the state's answer, read but NOT kept; the sniffer keeps it only when
     * it fits his cache criteria. Kept already: from the phone, nothing asked.
     */
    fun peek(url: String): String = ArkodCache.answer(url)?.first ?: get(url)

    fun keep(url: String, text: String) = ArkodCache.keepAnswer(url, text)

    /** The state's answer to [url], kept as every sheet is, for the parcel caches to store (v5). */
    fun keptText(url: String): String = getKept(url)

    /**
     * ONE PAGE OF EVERY PARCEL IN A BOX (30.9.2026, v5), from the state's WFS: [count] from [start],
     * given a minute and a half because a page of five hundred took fifteen seconds.
     */
    fun boxPage(box: ParcelCache.Box, start: Int, count: Int): String =
        get(
            Parcels.WFS + "?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&OUTPUTFORMAT=application/json" +
                "&SRSNAME=urn:ogc:def:crs:EPSG::4326&TYPENAMES=cp:CadastralParcel&COUNT=$count&STARTINDEX=$start" +
                "&BBOX=" + ParcelCache.wfsBox(box),
            readMs = 90_000,
        )

    /** The parcel under a finger, in a fifth of a second; null in the sea or on an unnumbered road. */
    suspend fun at(lat: Double, lon: Double): Parcels.Parcel? = withContext(Dispatchers.IO) {
        // SLOVENIA (v23): over the border GURS is asked, and its answer carries the outline too.
        // On the border strip Croatia is asked first; where it has nothing, Slovenia.
        if (!Slovenia.inside(lat, lon)) return@withContext Parcels.parcelFromInfo(get(Parcels.infoUrl(lat, lon)))
        val croatian = if (Slovenia.mayBeCroatia(lat, lon)) runCatching { Parcels.parcelFromInfo(get(Parcels.infoUrl(lat, lon))) } else null
        croatian?.getOrNull()?.let { return@withContext it }
        val slovenian = runCatching { slovenianAt(lat, lon) }
        // Both refused: Croatia's reason, which the WMS light already shows; else Slovenia's answer.
        if (slovenian.isFailure && croatian?.isFailure == true) throw croatian.exceptionOrNull()!!
        slovenian.getOrThrow()
    }

    /** The Slovenian parcel under a point, with its outline, which is kept like any other. */
    private fun slovenianAt(lat: Double, lon: Double): Parcels.Parcel? =
        Slovenia.parcelAt(get(Slovenia.atUrl(lat, lon)), lat, lon)?.also { keepShapes(listOf(it)) }

    suspend fun record(parcelId: Long): Parcels.Record = withContext(Dispatchers.IO) {
        Parcels.parseRecord(getKept(Parcels.recordUrl(parcelId)))
    }

    /** The sheet of any parcel: Croatia's OSS by id, Slovenia's GURS by reference (v23). */
    suspend fun record(parcel: Parcels.Parcel): Parcels.Record = withContext(Dispatchers.IO) {
        if (!Slovenia.isReference(parcel.reference)) return@withContext Parcels.parseRecord(getKept(Parcels.recordUrl(parcel.id)))
        val text = getKept(Slovenia.recordUrl(parcel.reference))
        val ko = Slovenia.koId(parcel.municipality)
        val name = runCatching { Slovenia.parseMunicipality(getKeptQuiet(Slovenia.municipalityUrl(ko), readMs = 15_000))?.second }.getOrNull()
        val record = Slovenia.parseRecord(text, parcel.reference, name) ?: throw Refused("GURS ne zna česticu ${parcel.number} u k.o. $ko")
        slovenianMore(record, text, parcel)
    }

    /**
     * THE REST OF WHAT GURS PUBLISHES (the phone's S4, 11.10.2026): land use, planned use, soil rating,
     * the buildings on the parcel with their parts and address, and a public owner. Each asked on its own
     * and kept like any answer; one that fails leaves its rows out and the sheet still opens.
     */
    private fun slovenianMore(record: Parcels.Record, text: String, parcel: Parcels.Parcel): Parcels.Record {
        val eid = Slovenia.eidOf(text, record.number) ?: parcel.id.takeIf { it < 0 }?.let { (-it).toString() }
        val rings = parcel.rings.ifEmpty { Slovenia.parseParcels(text).firstOrNull { it.number == record.number }?.rings.orEmpty() }
        fun quiet(url: String) = runCatching { getKeptQuiet(url, readMs = 20_000) }.getOrNull()
        val rows = ArrayList<Parcels.SheetRow>()
        if (eid != null) {
            quiet(Slovenia.byParcelUrl(Slovenia.LAND_USE, eid))?.let { rows += Slovenia.landUseRows(it) }
            quiet(Slovenia.byParcelUrl(Slovenia.PLANNED_USE, eid))?.let { rows += Slovenia.plannedUseRows(it) }
            quiet(Slovenia.byParcelUrl(Slovenia.RATING, eid))?.let { rows += Slovenia.ratingRows(it) }
        }
        var address: String? = null
        val ring = rings.firstOrNull().orEmpty()
        if (ring.isNotEmpty()) {
            val box = doubleArrayOf(ring.minOf { it.first }, ring.minOf { it.second }, ring.maxOf { it.first }, ring.maxOf { it.second })
            quiet(Slovenia.buildingsUrl(box))?.let { Slovenia.buildings(it, rings) }.orEmpty().forEach { b ->
                rows += b.row
                if (b.eid.isNotBlank()) quiet(Slovenia.partsUrl(b.eid))?.let { Slovenia.partRows(it, b.number) }?.let { (parts, at) ->
                    rows += parts
                    if (address == null) address = at
                }
            }
            val (lat, lon) = parcel.copy(rings = rings).middle
            quiet(Slovenia.publicOwnersUrl(lat, lon))?.let { rows += Slovenia.publicOwnerRows(it) }
        }
        return Slovenia.withMore(record, rows, address)
    }

    /** Like [get], but a refusal is null: the land registry answers 404 for a number it lacks. */
    private fun soft(url: String): String? = runCatching { getKept(url) }.getOrNull()

    /** One land-registry folio, sheets A, B and C; null when the book has no such folio. */
    suspend fun folio(bookId: String, unit: String): Parcels.Folio? = withContext(Dispatchers.IO) {
        soft(Parcels.folioUrl(bookId, unit))?.let { Parcels.parseFolio(it) }
    }

    /** THE OWNER SHEETS THE CADASTRE LINKS TO (29.9.2026): every folio its record names, each once. */
    suspend fun ownerSheets(record: Parcels.Record): List<Parcels.Folio> = withContext(Dispatchers.IO) {
        record.landBooks.filter { it.bookId.isNotBlank() && it.unit.isNotBlank() }
            .distinctBy { it.bookId to it.unit }
            .mapNotNull { folio(it.bookId, it.unit) }
    }

    /**
     * WHERE THE STATE HAS NO LINK (29.9.2026): the land book found by the municipality's name, then
     * the number he typed, as the folio's own number or as a land-book parcel number. Every book of
     * that name is tried; the first that answers is the one.
     */
    suspend fun findOwnerSheets(municipality: String, number: String, isFolio: Boolean): List<Parcels.Folio> =
        withContext(Dispatchers.IO) {
            val books = Parcels.parseBooks(getKeptQuiet(Parcels.booksUrl(municipality)), municipality)
            if (books.isEmpty()) throw Refused("nema zemljišne knjige imena $municipality")
            for (book in books) {
                val units = if (isFolio) listOf(number)
                else soft(Parcels.foliosByParcelUrl(book.id, number))?.let { Parcels.parseFolioNumbers(it) }.orEmpty()
                val found = units.distinct().mapNotNull { folio(book.id, it) }
                if (found.isNotEmpty()) return@withContext found.map { it.copy(bookId = book.id) }
            }
            emptyList()
        }

    /** The cadastral municipality under the middle of the screen: ("334723", "KUKLJICA"). */
    suspend fun municipality(lat: Double, lon: Double): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (Slovenia.inside(lat, lon)) {
            val hr = if (Slovenia.mayBeCroatia(lat, lon)) runCatching { Parcels.zoningFromInfo(get(Parcels.infoUrl(lat, lon, "cp:CP.CadastralZoning"))) }.getOrNull() else null
            return@withContext hr ?: Slovenia.parseMunicipality(getKeptQuiet(Slovenia.municipalityAtUrl(lat, lon), readMs = 15_000))
        }
        Parcels.zoningFromInfo(get(Parcels.infoUrl(lat, lon, "cp:CP.CadastralZoning")))
    }

    /** The municipality under a point with OSS's internal id too: (reg number, name, internal id). */
    suspend fun municipalityFull(lat: Double, lon: Double): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        // SLOVENIA (v23): its municipality's "internal id" is its own "SI1722", which [ossSearch] routes.
        if (Slovenia.inside(lat, lon)) {
            val hr = if (Slovenia.mayBeCroatia(lat, lon)) runCatching {
                val t = get(Parcels.infoUrl(lat, lon, "cp:CP.CadastralZoning"))
                val (reg, name) = Parcels.zoningFromInfo(t)!!
                Triple(reg, name, Parcels.zoningIdFromInfo(t)!!)
            }.getOrNull() else null
            if (hr != null) return@withContext hr
            val (reg, name) = Slovenia.parseMunicipality(getKeptQuiet(Slovenia.municipalityAtUrl(lat, lon), readMs = 15_000)) ?: return@withContext null
            return@withContext Triple(reg, name, reg)
        }
        val text = get(Parcels.infoUrl(lat, lon, "cp:CP.CadastralZoning"))
        val (reg, name) = Parcels.zoningFromInfo(text) ?: return@withContext null
        val id = Parcels.zoningIdFromInfo(text) ?: return@withContext null
        Triple(reg, name, id)
    }

    /** OSS's own public search: a number, or a whole possession sheet, in one municipality. */
    suspend fun ossSearch(municipalityId: String, number: String? = null, sheet: String? = null): List<Finding.Hit> =
        withContext(Dispatchers.IO) {
            // SLOVENIA (v23): a number is asked of GURS; there are no possession sheets to search.
            if (Slovenia.isMunicipality(municipalityId)) {
                if (number == null) return@withContext emptyList()
                return@withContext Slovenia.parseParcels(getKeptQuiet(Slovenia.byNumbersUrl(municipalityId, listOf(number))))
                    .also { keepShapes(it) }
                    .map { p -> Finding.Hit(id = p.id.toString(), title = "${p.number} · k.o. ${Slovenia.koId(p.municipality)} (SLO)",
                        under = Parcels.areaLabel(p.areaM2), source = Finding.Source.PARCEL, ref = p.reference) }
            }
            val c = (URL(Parcels.OSS_SEARCH).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", Layers.USER_AGENT)
            }
            try {
                c.outputStream.use { it.write(Parcels.searchBody(municipalityId, number, sheet).toByteArray()) }
                if (c.responseCode != HttpURLConnection.HTTP_OK) {
                    Services.failed(Parcels.OSS_SEARCH, "answered ${c.responseCode}")
                    throw Refused("katastar je odgovorio ${c.responseCode}")
                }
                Parcels.parseSearch(c.inputStream.bufferedReader().use { it.readText() }).also { Services.ok(Parcels.OSS_SEARCH) }
            } catch (e: java.io.IOException) {
                Services.failed(Parcels.OSS_SEARCH, e.message ?: e.javaClass.simpleName)
                throw e
            } finally {
                c.disconnect()
            }
        }

    /**
     * AS HE TYPES (29.9.2026, v3): every parcel number in the municipality that begins with what
     * is in the field, a quarter of a second each (measured: 0.28 s for "245" in Kukljica).
     */
    suspend fun suggest(municipalityReg: String, municipalityName: String, prefix: String): List<Finding.Hit> =
        withContext(Dispatchers.IO) {
            if (Slovenia.isMunicipality(municipalityReg))
                return@withContext Slovenia.parseSuggestions(getKeptQuiet(Slovenia.suggestUrl(municipalityReg, prefix), readMs = 15_000), municipalityName)
            Parcels.parseSuggestions(getKeptQuiet(Parcels.searchUrl(prefix, municipalityReg), readMs = 10_000), municipalityReg, municipalityName)
        }

    /** Parcels by number in one municipality, through OSS's search: one quick request each. */
    suspend fun find(municipality: String, numbers: List<String>): List<Parcels.Parcel> =
        withContext(Dispatchers.IO) {
            if (Slovenia.isMunicipality(municipality)) {
                return@withContext Slovenia.parseParcels(getKeptQuiet(Slovenia.byNumbersUrl(municipality, numbers))).also { keepShapes(it) }
            }
            numbers.mapNotNull { n ->
                val id = Parcels.parseSearch(getKeptQuiet(Parcels.searchUrl(n, municipality)), n) ?: return@mapNotNull null
                Parcels.Parcel(id, n, "$municipality-$n", null, emptyList())
            }
        }

    /**
     * THE SHAPES, WHICH ONLY THE SLOW SERVICE HAS. One WFS request for all the references at once,
     * given a minute and a half, because it was measured at twenty-nine seconds (27.9.2026). Asked in the
     * background after a highlight and again whenever a mark is still without its shape.
     */
    suspend fun shapes(references: List<String>): List<Parcels.Parcel> = withContext(Dispatchers.IO) {
        if (references.isEmpty()) return@withContext emptyList()
        // CACHE KING (v11): an outline read once comes off the phone; only the unknown ones are
        // asked, and when the WFS fails (ORA-01000) the kept ones still come back.
        val kept = references.mapNotNull { ParcelQuery.decodeShape(ArkodCache.shape(it)) }
        val missing = references.filter { r -> kept.none { it.reference == r } }
        if (missing.isEmpty()) return@withContext kept
        val fresh = try {
            // SLOVENIA (v23): its references go to GURS, the rest to the Croatian WFS.
            val (si, hr) = missing.partition { Slovenia.isReference(it) }
            val sl = if (si.isEmpty()) emptyList() else runCatching { Slovenia.parseParcels(get(Slovenia.byReferenceUrl(si), readMs = 45_000)) }
                .getOrElse { if (hr.isEmpty()) throw it else emptyList() }
            sl + if (hr.isEmpty()) emptyList() else Parcels.parseParcels(get(Parcels.byReferenceUrl(hr), readMs = 90_000))
        } catch (e: Exception) {
            if (kept.isNotEmpty()) return@withContext kept
            throw e
        }
        keepShapes(fresh)
        kept + fresh
    }

    /** Outlines kept one by one, from wherever they came: the WFS, a parcel cache, a file. */
    fun keepShapes(parcels: List<Parcels.Parcel>) {
        parcels.filter { it.rings.isNotEmpty() && it.reference.isNotBlank() }
            .forEach { ArkodCache.keepShape(it.reference, ParcelQuery.encodeShape(it)) }
    }

    /**
     * THE OUTLINE AT ONCE (27.9.2026): the picture round the finger, a thousand pixels across,
     * read by [Outline]. Three hundred metres of ground first, which holds a house plot or a field
     * at a third of a metre a pixel; if the parcel runs off the picture, three times as far, and
     * once more. Null when even that does not hold it, and the slow WFS is left to it.
     */
    suspend fun outline(lat: Double, lon: Double): List<List<Pair<Double, Double>>>? = withContext(Dispatchers.IO) {
        for (side in listOf(300.0, 900.0, 2700.0)) {
            val box = Outline.box(lat, lon, side)
            val px = 1024
            val url = "${Parcels.WMS}?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=cp:CP.CadastralParcel&STYLES=" +
                "&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=$px&HEIGHT=$px" +
                "&BBOX=${box[0]},${box[1]},${box[2]},${box[3]}"
            val bytes = (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", Layers.USER_AGENT)
                try {
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        Services.failed(url, "answered $responseCode")
                        return@withContext null
                    }
                    inputStream.use { it.readBytes() }.also { Services.ok(url) }
                } catch (e: java.io.IOException) {
                    Services.failed(url, e.message ?: e.javaClass.simpleName)
                    return@withContext null
                } finally {
                    disconnect()
                }
            }
            val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val w = picture.width
            val h = picture.height
            val pixels = IntArray(w * h)
            picture.getPixels(pixels, 0, w, 0, 0, w, h)
            picture.recycle()
            // A wall is anything the state drew: its lines and its numbers, anti-aliased edges included.
            val wall = BooleanArray(w * h) { ((pixels[it] ushr 24) and 0xFF) >= 60 }
            Outline.parcelAt(wall, w, h, box, lat, lon)?.let { return@withContext listOf(it) }
        }
        null
    }

    /**
     * THE STATE'S OWN TILE (29.9.2026): off the phone if it is kept and fresh, else from the
     * state and kept. [refresh] is the prefetcher's: a kept tile past its age is fetched again,
     * and if the state does not answer the old one stands.
     */
    fun rawTile(z: Int, x: Int, y: Int, refresh: Boolean = false): ByteArray {
        val onPhone = ArkodCache.raw(z, x, y)
        if (onPhone != null && (!refresh || onPhone.second < ArkodCache.STALE_MS)) return onPhone.first
        return try {
            stateTile(z, x, y).also { ArkodCache.keepRaw(z, x, y, it) }
        } catch (e: Exception) {
            onPhone?.first ?: throw e
        }
    }

    /**
     * ONE TILE FROM WHICHEVER STATE HAS PARCELS UNDER IT (v23): Croatia's, Slovenia's, or both laid
     * one over the other on the border. One state refusing still gives the other's half.
     */
    private fun stateTile(z: Int, x: Int, y: Int): ByteArray {
        val (hr, si) = Slovenia.statesFor(z, x, y)
        if (!si) return fetchTile(Parcels.tileUrl(z, x, y))
        val slo = runCatching { sloveneTile(fetchTile(Slovenia.tileUrl(z, x, y))) }
        if (!hr) return slo.getOrThrow()
        val cro = runCatching { fetchTile(Parcels.tileUrl(z, x, y)) }
        if (slo.isFailure) return cro.getOrThrow()
        if (cro.isFailure) return slo.getOrThrow()
        return overlay(cro.getOrThrow(), slo.getOrThrow())
    }

    /** GURS's picture as lines only, should its style fill the parcels ([Slovenia.linesOnly]). */
    private fun sloveneTile(bytes: ByteArray): ByteArray {
        val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val w = picture.width
        val h = picture.height
        val pixels = IntArray(w * h)
        picture.getPixels(pixels, 0, w, 0, 0, w, h)
        picture.recycle()
        val lines = Slovenia.linesOnly(pixels, w, h)
        if (lines === pixels) return bytes
        val out = Bitmap.createBitmap(lines, w, h, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream(bytes.size).also { out.compress(Bitmap.CompressFormat.PNG, 100, it); out.recycle() }.toByteArray()
    }

    /** Two states' pictures of one tile, one over the other. */
    private fun overlay(under: ByteArray, over: ByteArray): ByteArray {
        val a = BitmapFactory.decodeByteArray(under, 0, under.size) ?: return over
        val b = BitmapFactory.decodeByteArray(over, 0, over.size) ?: return under
        val out = a.copy(Bitmap.Config.ARGB_8888, true)
        a.recycle()
        android.graphics.Canvas(out).drawBitmap(b, null, android.graphics.Rect(0, 0, out.width, out.height), null)
        b.recycle()
        return ByteArrayOutputStream(under.size + over.size).also { out.compress(Bitmap.CompressFormat.PNG, 100, it); out.recycle() }.toByteArray()
    }

    private fun fetchTile(url: String): ByteArray {
        val open = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", Layers.USER_AGENT)
        }
        return try {
            val code = open.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Report.tiles("ARKOD: HTTP $code")
                Services.failed(url, "answered $code")
                throw java.io.IOException("cadastre tile answered $code")
            }
            open.inputStream.use { it.readBytes() }.also { Services.ok(url) }
        } catch (e: java.io.IOException) {
            // No connection or a refusal: the ARKOD light goes red (v14). Still an IOException,
            // which is what the map's tile loader expects.
            Services.failed(url, e.message ?: e.javaClass.simpleName)
            throw e
        } finally {
            open.disconnect()
        }
    }

    /**
     * ONE TILE OF THE ARKOD LAYER (v5): the state's picture restyled as Parcel view says (colour,
     * transparency, weight; kept on the phone per style), and where a parcel cache is drawn in its
     * own lines, the state's lines inside the cache's box taken out and the cache's drawn instead.
     * With no signal and no picture kept, a cache still draws its part on an empty tile.
     */
    fun tile(z: Int, x: Int, y: Int): ByteArray {
        val lines = ParcelsShown.lines
        val ink = lines.ink(ParcelsShown.ink)
        val key = ParcelStyle.key(ink, lines)
        val here = ParcelCache.tileBox(z, x, y)
        val over = if (ParcelsShown.ownLines) ParcelsShown.caches.filter { it.info.visible && it.info.box.intersects(here) } else emptyList()
        val styled: ByteArray? = ArkodCache.inked(z, x, y, key) ?: runCatching {
            val bytes = rawTile(z, x, y)
            val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw java.io.IOException("the cadastre sent something that is not a picture")
            val w = picture.width
            val h = picture.height
            val pixels = IntArray(w * h)
            picture.getPixels(pixels, 0, w, 0, 0, w, h)
            picture.recycle()
            val out = Bitmap.createBitmap(ParcelStyle.restyle(pixels, w, h, ink, lines), w, h, Bitmap.Config.ARGB_8888)
            val png = ByteArrayOutputStream(bytes.size)
            out.compress(Bitmap.CompressFormat.PNG, 100, png)
            out.recycle()
            png.toByteArray().also { ArkodCache.keepInked(z, x, y, key, it) }
        }.getOrElse { if (over.isEmpty()) throw it else null }
        if (over.isEmpty()) return styled!!
        return CacheTiles.draw(styled, over, z, x, y, lines.opacity)
    }
}

/**
 * WHAT THE CADASTRE HAS ON THE MAP, kept apart from whichever engine is drawing (27.9.2026), the
 * way [Shown] keeps the route: the switch, the ink, the marked parcels, and what a tap does.
 */
object ParcelsShown {
    /** The state's parcels, the WMS layer: off when the parcels key hides them (v3). */
    @Volatile
    var on: Boolean = true

    /** His own parcels and the selection: off only when the parcels key hides everything (v3). */
    @Volatile
    var mineOn: Boolean = true

    @Volatile
    var ink: Long = Parcels.INK_DARK

    /** The state's lines as Parcel view set them: colour, transparency, weight (v5). */
    @Volatile
    var lines: ParcelStyle.Lines = ParcelStyle.Lines()

    /** Inside a cache's box its own lines are drawn and the state's taken out (v5). */
    @Volatile
    var ownLines: Boolean = true

    /** The parcel caches on the phone, as the map should draw them (v5). */
    @Volatile
    var caches: List<ParcelCache.Cache> = emptyList()

    /**
     * Everything that decides what a tile of the ARKOD layer looks like, in one string: when it
     * changes, the layer is made again, because a tile already drawn carries the old look.
     */
    fun look(): String = ParcelStyle.key(lines.ink(ink), lines) + "|" + ownLines + "|" +
        caches.filter { it.info.visible }.joinToString(",") { c ->
            "${c.info.id}:${c.items.size}:${c.info.colour}:${c.info.style}:${c.info.weight}"
        }

    @Volatile
    var marks: List<Parcels.Mark> = emptyList()

    /**
     * THE PARCEL UNDER THE LAST TAP (27.9.2026, his correction of v87): *"only one parcel can be
     * highlighted at a time ... this is just to mark current click, so new deletes the old click
     * highlight."* Drawn in its own colour, over the marks, and never kept.
     */
    @Volatile
    var selection: Parcels.Mark? = null

    /** Where the last search result is, pinned on whichever map is up. */
    @Volatile
    var pin: Pair<Double, Double>? = null

    /**
     * Everything the engines draw: his marks, and the selection on top. The selection is drawn even
     * with the ARKOD layer hidden (v5: "even when [they] are hidden, when user clicks ... you will
     * outline the invisible [parcel]").
     */
    /** What fly-through scanning found (v16), drawn bold over the marks, under the selection. */
    @Volatile
    var found: List<Parcels.Mark> = emptyList()

    fun drawn(): List<Parcels.Mark> = (if (mineOn) marks.filter { it.group !in hiddenGroups } else emptyList()) + found + listOfNotNull(selection)

    /** The groups of Moje čestice he hid (v8): their parcels stay kept and are not drawn. */
    @Volatile
    var hiddenGroups: Set<String> = emptySet()

    /** Set by the screen; called by either engine with where the finger landed. */
    @Volatile
    var onTap: ((Double, Double) -> Unit)? = null

    fun tap(lat: Double, lon: Double) {
        onTap?.invoke(lat, lon)
    }
}
