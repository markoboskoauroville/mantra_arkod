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

    private fun get(url: String): String {
        val open = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", "MantraTrail/1")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = open.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw Refused("the cadastre answered $code")
            return open.inputStream.bufferedReader().use { it.readText() }
        } finally {
            open.disconnect()
        }
    }

    /** The parcel under a finger, or null when the finger is in the sea or on a road with no number. */
    suspend fun at(lat: Double, lon: Double): Parcels.Parcel? = withContext(Dispatchers.IO) {
        val round = Parcels.parseParcels(get(Parcels.aroundUrl(lat, lon)))
        Parcels.containing(round, lat, lon)
    }

    suspend fun record(parcelId: Long): Parcels.Record = withContext(Dispatchers.IO) {
        Parcels.parseRecord(get(Parcels.recordUrl(parcelId)))
    }

    /** The cadastral municipality under the middle of the screen: ("334723", "KUKLJICA"). */
    suspend fun municipality(lat: Double, lon: Double): Pair<String, String>? = withContext(Dispatchers.IO) {
        Parcels.parseZoning(get(Parcels.zoningUrl(lat, lon)))
    }

    /** Parcels by number, in one municipality, in one request. */
    suspend fun find(municipality: String, numbers: List<String>): List<Parcels.Parcel> =
        withContext(Dispatchers.IO) {
            if (numbers.isEmpty()) return@withContext emptyList()
            val found = Parcels.parseParcels(get(Parcels.byReferenceUrl(numbers.map { "$municipality-$it" })))
            // In the order he typed them, so the first he asked for is the one the map goes to.
            numbers.mapNotNull { n -> found.firstOrNull { it.number == n } }
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
