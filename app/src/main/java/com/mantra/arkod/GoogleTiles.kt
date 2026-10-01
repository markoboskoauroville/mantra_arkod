package com.mantra.arkod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GOOGLE'S MAP, WITH HIS KEY AND NOT WITH THE APP'S.
 *
 * The Maps SDK reads its key from the app when Android installs it, which is why v4 to v6 had a
 * live key inside a public APK. This does not: the Map Tiles API takes the key at run time, so it
 * can come from the file he picked and nothing is ever compiled in.
 *
 * HOW IT WORKS. One POST creates a session for a map type; the answer is a token, and the tiles
 * are then ordinary z/x/y requests carrying the token and the key. The token has an expiry, so it
 * is kept per view and made again when it runs out.
 *
 * WHAT IT COSTS. Google bills per tile against his account. So the session is made once per view
 * and only when that view is actually chosen — never to "check whether it works".
 *
 * AND ITS TILES ARE NEVER KEPT. Google's terms forbid caching and name offline use as a
 * prohibited case, so every Google layer is cacheable = false and CH refuses them with the reason.
 */
object GoogleTiles {

    /**
     * WHO IS ASKING. Without this an Android-restricted key is refused with
     * "Requests from this Android client application <empty> are blocked" (17.9.2026).
     */
    @Volatile
    var context: android.content.Context? = null

    private fun identify(connection: java.net.HttpURLConnection) {
        val ctx = context ?: return
        AndroidCaller.headers(ctx).forEach { (k, v) -> connection.setRequestProperty(k, v) }
    }

    /**
     * TRY THE KEYS IN TURN (17.9.2026). One key that stops working used to mean no Google map at
     * all; with a ring, the app walks it in order — the one that worked last first — and the
     * verdict of each attempt is written back, so the next walk starts with the one that answered.
     */
    suspend fun sessionFromRing(
        view: MapLayer.GoogleView,
        store: Store,
    ): Result {
        val ring = Keyring.order(store.keyring)
        if (ring.isEmpty()) return Result(null, "Još nema Google ključa.")
        var last: Result? = null
        ring.forEach { key ->
            val answer = session(view, key.value)
            val verdict = when {
                answer.token != null -> Keyring.Verdict.GOOD
                answer.problem?.startsWith(UNREACHABLE) == true -> Keyring.Verdict.UNREACHABLE
                else -> Keyring.Verdict.REFUSED
            }
            store.keyring = Keyring.withVerdict(
                store.keyring,
                key.value,
                verdict,
                answer.problem ?: "works",
                System.currentTimeMillis(),
            )
            if (answer.token != null) return answer
            last = answer
        }
        return last ?: Result(null, "Google nije prihvatio nijedan ključ")
    }

    private const val CREATE = "https://tile.googleapis.com/v1/createSession"

    private data class Session(val token: String, val madeMs: Long)

    private val sessions = HashMap<String, Session>()

    /** Sessions are good for a while; this is well inside it and costs one call a day at most. */
    private const val LIFETIME_MS = 6L * 3600 * 1000

    /**
     * The token for a view, made if there is none or the last one is old. Returns the token, or
     * null with the reason in [problem].
     */
    suspend fun session(view: MapLayer.GoogleView, key: String): Result = withContext(Dispatchers.IO) {
        val cached = sessions[view.name]
        if (cached != null && System.currentTimeMillis() - cached.madeMs < LIFETIME_MS) {
            return@withContext Result(cached.token, null)
        }
        try {
            val body = JSONObject()
                .put("mapType", view.mapType)
                .put("language", "hr")
                .put("region", "HR")
            // TERRAIN IS REFUSED WITHOUT A ROADMAP LAYER (17.9.2026, tested against his key
            // once the API was switched on): "The terrain map type must always contain a roadmap
            // layer", 400. Hybrid asks for the same layer to draw roads over the photograph, so
            // the two cases are one line.
            if (view.overlayRoads || view.mapType == "terrain") {
                body.put("layerTypes", JSONArray().put("layerRoadmap"))
            }
            val connection = URL("$CREATE?key=$key").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            identify(connection)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            // The GOO light (v14): the session is Google's first answer to the key.
            if (code == HttpURLConnection.HTTP_OK) Services.ok(CREATE)
            if (code != HttpURLConnection.HTTP_OK) {
                // GOOGLE'S OWN WORDS, NOT MINE (17.9.2026).
                //
                // Tested against his real key on a desk: the key was valid and the refusal was
                // "Map Tiles API has not been used in project 342783832558 before or it is
                // disabled", with the exact console link to switch it on. My sentence said to
                // enable the API but not WHICH PROJECT, and he spent half a day making a second
                // key that was refused for the same reason. Google says it better; pass it on.
                //
                // The key is never in this text: the message quotes the project, not the key, and
                // the URL that carries the key is never shown.
                val said = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                connection.disconnect()
                val googleSays = runCatching {
                    JSONObject(said ?: "").getJSONObject("error").optString("message", "")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                // Google's rule in the EU (satellite refused) is an answer, not an outage: the light stays green.
                if (Layers.eeaRefusal(googleSays)) Services.ok(CREATE) else Services.failed(CREATE, googleSays ?: "answered $code")
                return@withContext Result(
                    null,
                    when {
                        googleSays != null -> "Google: $googleSays"
                        code == 429 -> "Google ograničava ovaj ključ. Pokušajte malo kasnije."
                        code == 401 || code == 403 ->
                            "Google je odbio ključ ($code). U Cloud Consoleu uključite Map Tiles API za njegov projekt."
                        else -> "Google je odgovorio $code"
                    },
                )
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val token = JSONObject(text).optString("session", "")
            if (token.isEmpty()) {
                return@withContext Result(null, "Google nije dao sesiju")
            }
            sessions[view.name] = Session(token, System.currentTimeMillis())
            Result(token, null)
        } catch (e: Exception) {
            Result(null, "$UNREACHABLE: ${e.javaClass.simpleName}")
        }
    }

    data class Result(val token: String?, val problem: String?)

    /** How every "no network" answer begins, so a key is not marked refused for a dead signal. */
    const val UNREACHABLE = "Google nije dostupan"

    /** Forgotten when the key changes, so a new key never rides on an old session. */
    fun forget() {
        sessions.clear()
    }
}
