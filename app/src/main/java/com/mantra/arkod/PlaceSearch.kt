package com.mantra.arkod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * SEARCHING AS GOOGLE MAPS SEARCHES (27.9.2026).
 *
 * v88's field asked Places' Text Search, which answers an address with its one best match; he
 * showed Google Maps listing ten "Stjepana Radića 13" across Croatia against the app's one. Google
 * Maps' list is AUTOCOMPLETE: predictions, each with its distance. Measured with his key the same
 * morning: autocomplete gave Bjelovar, Hlebine, Mokrice, Križevci, Otočac for "…13c"; Text Search
 * gave Vrbovec alone. So [find] asks autocomplete for the text, autocomplete again without the
 * house letter, and Text Search for businesses, all at once, and [Finding.merge] makes one list,
 * nearest first. A prediction has no coordinates; [locate] asks Place Details for the one tapped,
 * in the same session token, which is how Google bills autocomplete as one search.
 */
object PlaceSearch {

    private const val AUTOCOMPLETE = "https://places.googleapis.com/v1/places:autocomplete"
    private const val TEXT = "https://places.googleapis.com/v1/places:searchText"
    private const val DETAILS = "https://places.googleapis.com/v1/places/"

    private fun post(url: String, key: String, body: JSONObject, fields: String? = null): JSONObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            fields?.let { setRequestProperty("X-Goog-FieldMask", it) }
            GoogleTiles.context?.let { ctx -> AndroidCaller.headers(ctx).forEach { (k, v) -> setRequestProperty(k, v) } }
        }
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code == 200) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code != 200) {
                val said = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
                throw ParcelNet.Refused("Google: ${said ?: code}")
            }
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun near(fix: Fix?): JSONObject? =
        fix?.let { JSONObject().put("latitude", it.lat).put("longitude", it.lon) }

    private fun autocomplete(text: String, key: String, from: Fix?, session: String): List<Finding.Hit> {
        val body = JSONObject()
            .put("input", text)
            .put("languageCode", "hr")
            .put("includedRegionCodes", JSONArray().put("hr"))
            .put("sessionToken", session)
        near(from)?.let {
            body.put("origin", it)
            body.put("locationBias", JSONObject().put("circle", JSONObject().put("center", it).put("radius", 50_000.0)))
        }
        val a = post(AUTOCOMPLETE, key, body).optJSONArray("suggestions") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("placePrediction") }.map { p ->
            val f = p.optJSONObject("structuredFormat")
            Finding.Hit(
                id = p.optString("placeId"),
                title = f?.optJSONObject("mainText")?.optString("text") ?: p.optJSONObject("text")?.optString("text").orEmpty(),
                under = f?.optJSONObject("secondaryText")?.optString("text").orEmpty(),
                distanceM = if (p.has("distanceMeters")) p.optInt("distanceMeters") else null,
            )
        }
    }

    private fun textSearch(text: String, key: String, from: Fix?): List<Finding.Hit> {
        val body = JSONObject().put("textQuery", text).put("pageSize", 10).put("languageCode", "hr").put("regionCode", "hr")
        near(from)?.let { body.put("locationBias", JSONObject().put("circle", JSONObject().put("center", it).put("radius", 50_000.0))) }
        val a = post(TEXT, key, body, "places.id,places.displayName,places.formattedAddress,places.location").optJSONArray("places")
            ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { p ->
            val loc = p.optJSONObject("location") ?: return@mapNotNull null
            val lat = loc.optDouble("latitude")
            val lon = loc.optDouble("longitude")
            Finding.Hit(
                id = p.optString("id"),
                title = p.optJSONObject("displayName")?.optString("text").orEmpty(),
                under = p.optString("formattedAddress"),
                distanceM = from?.let { Geo.distance(it.lat, it.lon, lat, lon).toInt() },
                lat = lat,
                lon = lon,
            )
        }
    }

    /** Everything Google has for the words, as one list, nearest first; or the reason there is none. */
    suspend fun find(text: String, from: Fix?, store: Store, session: String, full: Boolean = true): Pair<List<Finding.Hit>, String?> =
        withContext(Dispatchers.IO) {
            val key = Keyring.best(store.keyring)?.value
                ?: return@withContext emptyList<Finding.Hit>() to "Nema Google ključa. Postavke: API ključevi."
            coroutineScope {
                val a = async { runCatching { autocomplete(text, key, from, session) } }
                val b = async { Finding.withoutHouseLetter(text)?.let { runCatching { autocomplete(it, key, from, session) } } }
                // Text Search is billed per call, so only a pressed search asks it; typing asks
                // autocomplete, which Google bills once per session.
                val c = async { if (full) runCatching { textSearch(text, key, from) } else null }
                val lists = listOf(a.await(), b.await(), c.await()).filterNotNull()
                val merged = Finding.merge(*lists.mapNotNull { it.getOrNull() }.toTypedArray())
                val problem = lists.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                merged to if (merged.isEmpty()) (problem ?: "ništa nije pronađeno") else null
            }
        }

    /** Where a prediction is, asked once it is tapped. */
    suspend fun locate(hit: Finding.Hit, store: Store, session: String): Finding.Hit? = withContext(Dispatchers.IO) {
        if (hit.located) return@withContext hit
        val key = Keyring.best(store.keyring)?.value ?: return@withContext null
        runCatching {
            val c = (URL(DETAILS + hit.id + "?sessionToken=" + session).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("X-Goog-Api-Key", key)
                setRequestProperty("X-Goog-FieldMask", "location")
                GoogleTiles.context?.let { ctx -> AndroidCaller.headers(ctx).forEach { (k, v) -> setRequestProperty(k, v) } }
            }
            val loc = try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("location") } finally { c.disconnect() }
            hit.copy(lat = loc.getDouble("latitude"), lon = loc.getDouble("longitude"))
        }.getOrNull()
    }
}
