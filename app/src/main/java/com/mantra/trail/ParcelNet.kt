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

    /** The cadastral municipality under the middle of the screen: ("334723", "KUKLJICA"). */
    suspend fun municipality(lat: Double, lon: Double): Pair<String, String>? = withContext(Dispatchers.IO) {
        Parcels.zoningFromInfo(get(Parcels.infoUrl(lat, lon, "cp:CP.CadastralZoning")))
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
     * given a whole minute, because it has been seen to take thirty seconds. Asked in the
     * background after a highlight and again whenever a mark is still without its shape.
     */
    suspend fun shapes(references: List<String>): List<Parcels.Parcel> = withContext(Dispatchers.IO) {
        if (references.isEmpty()) return@withContext emptyList()
        Parcels.parseParcels(get(Parcels.byReferenceUrl(references), readMs = 60_000))
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

    /** Set by the screen; called by either engine with where the finger landed. */
    @Volatile
    var onTap: ((Double, Double) -> Unit)? = null

    fun tap(lat: Double, lon: Double) {
        onTap?.invoke(lat, lon)
    }
}
