package com.mantra.arkod

import org.oscim.core.Tile
import org.oscim.tiling.source.HttpEngine
import org.oscim.tiling.source.UrlTileSource
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * FETCHING A TILE WITH THE STACK THAT IS KNOWN TO WORK (17.9.2026).
 *
 * The URL this app builds for Google is right — proved on a desk, byte for byte, returning a
 * 256-pixel PNG — and still nothing drew. VTM's own client cannot do https; its OkHttp engine
 * should, and did not either. Rather than guess a third time at somebody else's networking, the
 * tiles now go through java.net.HttpURLConnection: the same thing that fetches the session, the
 * routes, the elevations and the map downloads, every one of which has worked all week.
 *
 * It also REPORTS. What came back for the last tile — the status, the size, the reason — is kept
 * where the settings can show it, because "the map is white" is not something anybody can debug
 * and "403 on tile 16/35762/23697" is.
 */
class TileHttp(private val source: UrlTileSource) : HttpEngine {

    private var connection: HttpURLConnection? = null
    private var stream: InputStream? = null
    private var cache: OutputStream? = null

    override fun sendRequest(tile: Tile?) {
        if (tile == null) throw java.io.IOException("no tile asked for")
        val url = Parcels.resolve(source.getTileUrl(tile))
        // THE CADASTRE (27.9.2026): the state's WMS, asked by box, recoloured into our ink on the
        // way in; and since 29.9.2026 kept on the phone, so a place seen once draws at once.
        if (Parcels.isCadastre(url)) {
            stream = java.io.ByteArrayInputStream(ParcelNet.tile(tile.zoomLevel.toInt(), tile.tileX, tile.tileY))
            return
        }
        val open = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", Layers.USER_AGENT)
            // The two headers an Android-restricted Google key needs, sent to Google only.
            if (url.contains("googleapis.com")) {
                GoogleTiles.context?.let { ctx ->
                    AndroidCaller.headers(ctx).forEach { (k, v) -> setRequestProperty(k, v) }
                }
            }
            instanceFollowRedirects = true
        }
        connection = open
        // A LIGHT FOR EVERY SERVICE (v14): OpenStreetMap's and Google's tiles report here.
        val code = try { open.responseCode } catch (e: java.io.IOException) {
            Services.failed(url, e.message ?: e.javaClass.simpleName)
            throw e
        }
        if (code != HttpURLConnection.HTTP_OK) {
            val said = runCatching {
                open.errorStream?.bufferedReader()?.use { it.readText().take(200) }
            }.getOrNull().orEmpty()
            Report.tiles("tile ${tile.zoomLevel}/${tile.tileX}/${tile.tileY}: HTTP $code $said")
            // A tile beyond the map's end (404) is an answer, not the service down.
            if (code == HttpURLConnection.HTTP_NOT_FOUND) Services.ok(url) else Services.failed(url, "answered $code")
            throw java.io.IOException("tile answered $code")
        }
        Services.ok(url)
        stream = open.inputStream
    }

    override fun read(): InputStream {
        val open = stream ?: throw java.io.IOException("nothing was fetched")
        return open
    }

    override fun close() {
        runCatching { stream?.close() }
        runCatching { connection?.disconnect() }
        stream = null
        connection = null
    }

    override fun setCache(os: OutputStream?) {
        cache = os
    }

    override fun requestCompleted(success: Boolean): Boolean {
        if (success) Report.tiles("the map is arriving")
        close()
        return success
    }

    class Factory : HttpEngine.Factory {
        override fun create(tileSource: UrlTileSource): HttpEngine = TileHttp(tileSource)
    }
}

/** The last thing the tile fetcher saw, for the settings to show. One line, no history. */
object Report {
    @Volatile
    private var lastTiles: String? = null

    fun tiles(line: String) {
        lastTiles = line
    }

    fun tileReport(): String = lastTiles ?: "nothing asked yet"
}
