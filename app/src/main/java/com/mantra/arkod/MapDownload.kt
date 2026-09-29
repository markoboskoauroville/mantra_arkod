package com.mantra.arkod

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * FETCHING THE OFFLINE MAP, WITH THE PERSON WATCHING IT HAPPEN.
 *
 * 176 MB is minutes on a phone signal, and download-monitor.md's rule is that nothing longer than
 * a minute happens in the dark: the size is said before it starts, the progress is a number while
 * it runs, and a failure says what failed rather than leaving a button that did nothing.
 *
 * IT RESUMES. A half-finished file is kept as `.part` and the next attempt asks the server for
 * the rest with a Range header. Somebody on a mountain road loses the signal at 80% once, and
 * starting again from zero is how an app gets deleted.
 *
 * THE FILE IS ONLY RENAMED WHEN IT IS WHOLE. A `.map` of the right name and the wrong length is
 * the worst outcome: mapsforge opens it, draws part of a country, and nothing says why.
 */
object MapDownload {

    data class Progress(val done: Long, val total: Long, val bytesPerSecond: Long = 0L) {
        val percent: Int get() = if (total > 0) ((done * 100) / total).toInt() else 0
    }

    /**
     * WHAT THE DOWNLOAD IS DOING, FOR THE MIDDLE OF THE SCREEN (29.9.2026): *"while it's
     * downloading, in the middle of the screen it shows the speed and status, how much percentage
     * is done."* Null before anything was asked; [problem] set when it stopped short.
     */
    data class Live(val progress: Progress?, val running: Boolean, val problem: String? = null)

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live

    /** How much of the file is on the phone already, for the offer before it starts. */
    fun partBytes(context: Context): Long = File(target(context).parentFile, "${Layers.OfflineDownload.NAME}.part")
        .let { if (it.exists()) it.length() else 0L }

    /** One line of the progress, as the middle of the screen writes it. */
    fun line(p: Progress): String =
        "${p.percent}% · ${p.done / 1_000_000} od ${p.total / 1_000_000} MB · ${Geo.formatRate(p.bytesPerSecond)}"

    fun target(context: Context): File = File(File(context.filesDir, "maps").apply { mkdirs() }, Layers.OfflineDownload.NAME)

    fun isPresent(context: Context): Boolean = target(context).let { it.exists() && it.length() > 1_000_000 }

    fun sizeOnDisk(context: Context): Long = target(context).let { if (it.exists()) it.length() else 0L }

    /**
     * Fetch it, resuming if there is a part file. Returns null when the file is whole, or a
     * sentence saying what went wrong.
     */
    suspend fun fetch(
        context: Context,
        onProgress: (Progress) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        if (_live.value?.running == true) return@withContext "Preuzimanje je već u tijeku"
        _live.value = Live(null, running = true)
        val problem = fetchInner(context) { p ->
            _live.value = Live(p, running = true)
            onProgress(p)
        }
        _live.value = Live(_live.value?.progress, running = false, problem = problem)
        problem
    }

    private fun fetchInner(context: Context, onProgress: (Progress) -> Unit): String? {
        val finished = target(context)
        val part = File(finished.parentFile, "${finished.name}.part")
        return try {
            val already = if (part.exists()) part.length() else 0L
            val connection = URL(Layers.OfflineDownload.URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", Layers.USER_AGENT)
            if (already > 0) connection.setRequestProperty("Range", "bytes=$already-")
            connection.connect()

            val code = connection.responseCode
            // 206 means the server honoured the Range and we keep what we have; 200 means it did
            // not, and keeping the old bytes would splice two different files together.
            val resuming = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resuming) {
                return "Poslužitelj karte je odgovorio $code"
            }
            val total = (if (resuming) already else 0L) + connection.contentLengthLong.coerceAtLeast(0L)

            val out = java.io.FileOutputStream(part, resuming)
            var done = if (resuming) already else 0L
            if (!resuming && part.exists()) done = 0L
            connection.inputStream.use { input ->
                out.use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    // THE SPEED is the bytes of the last second or so, not the average since the
                    // start, so a signal that drops shows at once (29.9.2026).
                    var windowStart = System.currentTimeMillis()
                    var windowBytes = 0L
                    var rate = 0L
                    var lastReportMs = 0L
                    onProgress(Progress(done, total, 0L))
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        done += read
                        windowBytes += read
                        val now = System.currentTimeMillis()
                        if (now - windowStart >= 1_000L) {
                            rate = windowBytes * 1000L / (now - windowStart)
                            windowStart = now
                            windowBytes = 0L
                        }
                        if (now - lastReportMs >= 400L) {
                            lastReportMs = now
                            onProgress(Progress(done, total, rate))
                        }
                    }
                }
            }
            connection.disconnect()

            // Only a file of the length the server promised becomes the map.
            if (total > 0 && part.length() < total) {
                return "Preuzimanje je stalo na ${part.length() * 100 / total}%. Pritisnite ponovno za nastavak."
            }
            if (finished.exists()) finished.delete()
            if (!part.renameTo(finished)) return "Datoteka se nije mogla spremiti"
            onProgress(Progress(finished.length(), finished.length()))
            null
        } catch (e: Exception) {
            "Preuzimanje je prekinuto (${e.javaClass.simpleName}). Pritisnite ponovno za nastavak."
        }
    }
}
