package com.mantra.arkod

import org.json.JSONObject
import java.net.URLEncoder

/**
 * FETCH WHEN AVAILABLE (1.10.2026, FEATURES row 38; web v8 first). A sheet the state did not answer is handed
 * to the app's server, arkod-layer.pages.dev/api/later: a Cloudflare Worker asks the state again every ten
 * minutes and keeps the answer in ARKOD_cache, for every phone and browser. When the state and the phone's
 * own cache both fail, [answerUrl] is asked. Pure: the requests themselves are in [ParcelNet].
 */
object Later {

    const val BASE = "https://arkod-layer.pages.dev/api/later"

    /** Where the server's answer to one of the state's addresses is. */
    fun answerUrl(stateUrl: String): String = "$BASE/answer?url=" + URLEncoder.encode(stateUrl, "UTF-8")

    const val WANT = "$BASE/want"

    fun wantBody(stateUrl: String): String = JSONObject().put("url", stateUrl).toString()

    /** What the button says after the server answered [code] with [json]. */
    fun said(code: Int, json: String): String {
        val o = runCatching { JSONObject(json) }.getOrNull()
        if (code != 200 || o == null) {
            val why = o?.optString("error")?.takeIf { it.isNotBlank() } ?: "the server answered $code"
            return "the server could not take it: $why"
        }
        return if (o.optString("state") == "fetched") "the server already has it: open the parcel again"
        else "wanted: the server asks the state every 10 minutes and keeps the answer; open the parcel again later"
    }

    /** The server's "fetched at" (ISO 8601) as milliseconds; now when it is missing or unreadable. */
    fun fetchedAt(header: String?, now: Long = System.currentTimeMillis()): Long =
        runCatching { java.time.Instant.parse(header).toEpochMilli() }.getOrDefault(now)
}
