package com.mantra.arkod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * ASK THE SERVICE FOR ONE TILE AND SAY WHAT CAME BACK.
 *
 * "Thunderforest doesn't work" is a sentence about a blank screen; it could be a wrong key, a
 * refused key, an exhausted quota, a URL built wrongly, or no network at all — and every one of
 * those looks identical on the map. This fetches a single tile over the same URL the engine uses
 * and reports the status and the size, which tells the five apart in one press (16.9.2026).
 */
object TileTest {

    suspend fun check(layer: MapLayer, session: String?, key: String?): String =
        withContext(Dispatchers.IO) {
            val url = Layers.tileUrl(layer, 12, 2231, 1479, session, key)
                ?: return@withContext "${layer.name}: nema adrese — prvo treba ključ"
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", Layers.USER_AGENT)
                val code = connection.responseCode
                val type = connection.contentType ?: "bez vrste"
                val bytes = if (code == 200) {
                    connection.inputStream.use { it.readBytes().size }
                } else {
                    connection.errorStream?.use { it.readBytes().size } ?: 0
                }
                connection.disconnect()
                when {
                    code == 200 && type.startsWith("image") && bytes > 500 ->
                        "${layer.name}: poslužitelj je poslao sliku od $bytes bajtova. Karta je u redu."
                    code == 200 ->
                        "${layer.name}: odgovor 200, ali poslano $type, $bytes bajtova — nije pločica."
                    code == 401 || code == 403 -> {
                        // Google's own sentence when there is one: it names the project and the
                        // API to switch on, which is what he actually needs (17.9.2026).
                        val said = runCatching {
                            connection.errorStream?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()
                        val googleSays = runCatching {
                            org.json.JSONObject(said ?: "").getJSONObject("error").optString("message", "")
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                        googleSays?.let { "${layer.name}: $it" }
                            ?: "${layer.name}: odbijeno ($code). Ključ je pogrešan, istekao ili nije dopušten."
                    }
                    code == 429 ->
                        "${layer.name}: previše zahtjeva (429). Kvota ključa je potrošena."
                    code == 404 ->
                        "${layer.name}: nema pločice (404). Adresa ne odgovara usluzi."
                    else -> "${layer.name}: poslužitelj je odgovorio $code."
                }
            } catch (e: Exception) {
                "${layer.name}: nedostupno — ${e.javaClass.simpleName}. Nema mreže?"
            }
        }
}
