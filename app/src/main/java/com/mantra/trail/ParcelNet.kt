package com.mantra.trail

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
            setRequestProperty("User-Agent", "MantraTrail/1")
        }
        try {
            val code = open.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw Refused("the cadastre answered $code")
            return open.inputStream.bufferedReader().use { it.readText() }
        } finally {
            open.disconnect()
        }
    }

    /** The parcel under a finger, in a fifth of a second; null in the sea or on an unnumbered road. */
    suspend fun at(lat: Double, lon: Double): Parcels.Parcel? = withContext(Dispatchers.IO) {
        Parcels.parcelFromInfo(get(Parcels.infoUrl(lat, lon)))
    }

    suspend fun record(parcelId: Long): Parcels.Record = withContext(Dispatchers.IO) {
        Parcels.parseRecord(get(Parcels.recordUrl(parcelId)))
    }

    /** Like [get], but a refusal is null: the land registry answers 404 for a number it lacks. */
    private fun soft(url: String): String? = runCatching { get(url) }.getOrNull()

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
            val books = Parcels.parseBooks(get(Parcels.booksUrl(municipality)), municipality)
            if (books.isEmpty()) throw Refused("no land book is named $municipality")
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
        Parcels.zoningFromInfo(get(Parcels.infoUrl(lat, lon, "cp:CP.CadastralZoning")))
    }

    /** The municipality under a point with OSS's internal id too: (reg number, name, internal id). */
    suspend fun municipalityFull(lat: Double, lon: Double): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        val text = get(Parcels.infoUrl(lat, lon, "cp:CP.CadastralZoning"))
        val (reg, name) = Parcels.zoningFromInfo(text) ?: return@withContext null
        val id = Parcels.zoningIdFromInfo(text) ?: return@withContext null
        Triple(reg, name, id)
    }

    /** OSS's own public search: a number, or a whole possession sheet, in one municipality. */
    suspend fun ossSearch(municipalityId: String, number: String? = null, sheet: String? = null): List<Finding.Hit> =
        withContext(Dispatchers.IO) {
            val c = (URL(Parcels.OSS_SEARCH).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "MantraTrail/1")
            }
            try {
                c.outputStream.use { it.write(Parcels.searchBody(municipalityId, number, sheet).toByteArray()) }
                if (c.responseCode != HttpURLConnection.HTTP_OK) throw Refused("the cadastre answered ${c.responseCode}")
                Parcels.parseSearch(c.inputStream.bufferedReader().use { it.readText() })
            } finally {
                c.disconnect()
            }
        }

    /** Parcels by number in one municipality, through OSS's search: one quick request each. */
    suspend fun find(municipality: String, numbers: List<String>): List<Parcels.Parcel> =
        withContext(Dispatchers.IO) {
            numbers.mapNotNull { n ->
                val id = Parcels.parseSearch(get(Parcels.searchUrl(n, municipality)), n) ?: return@mapNotNull null
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
        Parcels.parseParcels(get(Parcels.byReferenceUrl(references), readMs = 90_000))
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
                setRequestProperty("User-Agent", "MantraTrail/1")
                try {
                    if (responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    inputStream.use { it.readBytes() }
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
     * ONE TILE OF THE STATE'S PICTURE, IN OUR INK: fetched, every pixel recoloured, written back as
     * a PNG. Both engines use it, so the lines look the same on the file and on Google's map.
     */
    fun tile(url: String, ink: Long): ByteArray {
        val open = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "MantraTrail/1")
        }
        val bytes = try {
            val code = open.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Report.tiles("cadastre tile: HTTP $code")
                throw java.io.IOException("cadastre tile answered $code")
            }
            open.inputStream.use { it.readBytes() }
        } finally {
            open.disconnect()
        }
        val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw java.io.IOException("the cadastre sent something that is not a picture")
        val w = picture.width
        val h = picture.height
        val pixels = IntArray(w * h)
        picture.getPixels(pixels, 0, w, 0, 0, w, h)
        picture.recycle()
        for (i in pixels.indices) pixels[i] = Parcels.recolour(pixels[i], ink)
        val out = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream(bytes.size)
        out.compress(Bitmap.CompressFormat.PNG, 100, png)
        out.recycle()
        return png.toByteArray()
    }
}

/**
 * WHAT THE CADASTRE HAS ON THE MAP, kept apart from whichever engine is drawing (27.9.2026), the
 * way [Shown] keeps the route: the switch, the ink, the marked parcels, and what a tap does.
 */
object ParcelsShown {
    @Volatile
    var on: Boolean = true

    @Volatile
    var ink: Long = Parcels.INK_DARK

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

    /** Everything the engines draw: his marks, and the selection on top. */
    fun drawn(): List<Parcels.Mark> = marks + listOfNotNull(selection)

    /** Set by the screen; called by either engine with where the finger landed. */
    @Volatile
    var onTap: ((Double, Double) -> Unit)? = null

    fun tap(lat: Double, lon: Double) {
        onTap?.invoke(lat, lon)
    }
}
