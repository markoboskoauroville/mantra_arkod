package com.mantra.arkod

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/**
 * THE CADASTRE, KEPT ON THE PHONE (29.9.2026).
 *
 * His words: *"when user loads the map automatically in the background, wherever he is, you need
 * to cache ARKOD layer, all parts ... and store it in the phone. So next time user comes to the
 * same location it is quickly drawn."*
 *
 * Two things are kept, both in the app's own storage:
 *
 *   tiles    the state's WMS picture of every parcel tile that was drawn or fetched ahead, as the
 *            state sent it (black lines), so either ink can be made from it; and beside it the
 *            tile already recoloured in the ink it was last drawn in, so a revisit is one file
 *            read and no work at all.
 *   answers  the parcel records and the land-registry folios that were opened, so a sheet opens
 *            with no signal and says when it was read.
 *
 * A kept tile is used at once. One older than [STALE_MS] is fetched again when there is a
 * signal, and kept as it was when there is not: the cadastre changes by the parcel, rarely, and a
 * month-old line is better than none on a hillside.
 *
 * The whole store is held under [LIMIT_BYTES]; past it the tiles not touched longest go first.
 */
object ArkodCache {

    @Volatile
    private var root: File? = null

    const val STALE_MS = 60L * 24 * 3600 * 1000
    const val LIMIT_BYTES = 400L * 1024 * 1024

    fun init(context: Context) {
        root = File(context.filesDir, "arkod").apply { mkdirs() }
    }

    private fun tileFile(z: Int, x: Int, y: Int): File? =
        root?.let { File(it, "raw/$z/$x/$y.png") }

    /** A restyled tile, kept per style: [key] is ParcelStyle.key (ink, transparency, weight). */
    private fun inkedFile(z: Int, x: Int, y: Int, key: String): File? =
        root?.let { File(it, "ink-$key/$z/$x/$y.png") }

    private fun write(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val part = File(file.parentFile, file.name + ".part")
        part.writeBytes(bytes)
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    /** The state's own tile, if it is on the phone: the bytes and how old they are. */
    fun raw(z: Int, x: Int, y: Int): Pair<ByteArray, Long>? {
        val f = tileFile(z, x, y) ?: return null
        if (!f.exists()) return null
        return runCatching { f.readBytes() to (System.currentTimeMillis() - f.lastModified()) }.getOrNull()
    }

    fun keepRaw(z: Int, x: Int, y: Int, bytes: ByteArray) {
        val f = tileFile(z, x, y) ?: return
        runCatching { write(f, bytes) }
        // A new picture makes every recoloured copy of the old one wrong.
        root?.listFiles()?.filter { it.name.startsWith("ink-") }?.forEach { File(it, "$z/$x/$y.png").delete() }
    }

    fun inked(z: Int, x: Int, y: Int, key: String): ByteArray? {
        val f = inkedFile(z, x, y, key) ?: return null
        if (!f.exists()) return null
        // Touched, so the pruning keeps what is looked at.
        f.setLastModified(System.currentTimeMillis())
        return runCatching { f.readBytes() }.getOrNull()
    }

    fun keepInked(z: Int, x: Int, y: Int, key: String, bytes: ByteArray) {
        val f = inkedFile(z, x, y, key) ?: return
        runCatching { write(f, bytes) }
    }

    fun isFresh(z: Int, x: Int, y: Int): Boolean {
        val f = tileFile(z, x, y) ?: return false
        return f.exists() && System.currentTimeMillis() - f.lastModified() < STALE_MS
    }

    // --- the answers ---------------------------------------------------------------------------

    private fun answerFile(url: String): File? {
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return root?.let { File(it, "answers/$name.json") }
    }

    fun keepAnswer(url: String, text: String) {
        val f = answerFile(url) ?: return
        runCatching { write(f, text.toByteArray()) }
    }

    /** A kept answer and when it was read, in milliseconds since the epoch. */
    fun answer(url: String): Pair<String, Long>? {
        val f = answerFile(url) ?: return null
        if (!f.exists()) return null
        return runCatching { f.readText() to f.lastModified() }.getOrNull()
    }

    // --- outlines, one file per parcel (v11/v12) --------------------------------------------------

    private fun shapeFile(reference: String): File? {
        val name = MessageDigest.getInstance("SHA-1").digest(reference.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return root?.let { File(it, "shapes/$name.txt") }
    }

    fun keepShape(reference: String, text: String) {
        val f = shapeFile(reference) ?: return
        runCatching { write(f, text.toByteArray()) }
    }

    fun shape(reference: String): String? = shapeFile(reference)?.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    /**
     * EVERY PARCEL WHOSE SHEET IS KEPT (v13), read back from the kept answers for the settings'
     * list: a possession sheet is the answer with "possessionSheets" in it. Each parcel once.
     */
    fun keptRecords(): List<Parcels.Record> {
        val dir = root?.let { File(it, "answers") } ?: return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { f ->
                runCatching { f.readText() }.getOrNull()
                    ?.takeIf { it.contains("\"possessionSheets\"") }
                    ?.let { t -> runCatching { Parcels.parseRecord(t) }.getOrNull() }
            }
            .filter { it.number.isNotBlank() && it.municipalityNumber.isNotBlank() }
            .distinctBy { it.municipalityNumber to it.number }
            .toList()
    }

    /** What the phone keeps, counted, for the top of the settings (v12). */
    data class Inventory(val bytes: Long, val tiles: Int, val answers: Int, val shapes: Int)

    fun inventory(): Inventory {
        val r = root ?: return Inventory(0, 0, 0, 0)
        fun count(dir: String) = File(r, dir).walkTopDown().count { it.isFile && !it.name.endsWith(".part") }
        return Inventory(bytes(), tileCount(), count("answers"), count("shapes"))
    }

    // --- the size of it ------------------------------------------------------------------------

    fun bytes(): Long = root?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun tileCount(): Int = root?.let { File(it, "raw") }?.walkTopDown()?.count { it.isFile && it.name.endsWith(".png") } ?: 0

    /** Everything kept, gone: tiles and answers. My parcels themselves live in the settings store. */
    fun clear() {
        root?.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Held under the limit: the tiles not touched longest are the ones that go. */
    fun prune(limit: Long = LIMIT_BYTES) {
        val dir = root ?: return
        val answers = File(dir, "answers").path
        val files = dir.walkTopDown().filter { it.isFile && !it.path.startsWith(answers) }.toList()
        var total = files.sumOf { it.length() }
        if (total <= limit) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= limit * 9 / 10) break
            total -= f.length()
            f.delete()
        }
    }

    fun label(): String {
        val mb = bytes() / 1_000_000
        return "ARKOD čestice kept · $mb MB"
    }
}

/**
 * FETCHING AHEAD, IN THE BACKGROUND (29.9.2026). Around where the map is looking, and around
 * where he is, every cadastre tile he would see at the zooms where parcels are drawn is fetched
 * and kept, three at a time, the nearest first. A newer place replaces an older one: what he is
 * looking at now is what matters. Tiles already kept and fresh cost nothing.
 */
object ArkodPrefetch {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastAt: Pair<Double, Double>? = null
    private var lastPosition: Pair<Double, Double>? = null

    /** "fetched 120 of 310" while it runs, null when it rests. */
    private val _state = MutableStateFlow<String?>(null)
    val state: StateFlow<String?> = _state

    /**
     * The view has settled at [lat], [lon]. Nothing happens below the cadastre's zooms, nor for a
     * place within a few hundred metres of the last one asked.
     */
    fun viewSettled(lat: Double, lon: Double, zoom: Int) {
        if (zoom < Parcels.MIN_ZOOM - 1) return
        val last = lastAt
        if (last != null && Geo.distance(last.first, last.second, lat, lon) < 400.0) return
        lastAt = lat to lon
        start(lat, lon)
    }

    /** A fix arrived: his own surroundings are kept once per kilometre he moves. */
    fun positionKnown(lat: Double, lon: Double) {
        val last = lastPosition
        if (last != null && Geo.distance(last.first, last.second, lat, lon) < 1000.0) return
        lastPosition = lat to lon
        if (job?.isActive == true) return
        start(lat, lon)
    }

    private fun start(lat: Double, lon: Double) {
        job?.cancel()
        val tiles = Parcels.prefetchTiles(lat, lon).filterNot { (z, x, y) -> ArkodCache.isFresh(z, x, y) }
        if (tiles.isEmpty()) return
        job = scope.launch {
            val gate = Semaphore(3)
            var done = 0
            tiles.chunked(24).forEach { batch ->
                if (!isActive) return@launch
                batch.map { (z, x, y) ->
                    async {
                        gate.withPermit {
                            runCatching { ParcelNet.rawTile(z, x, y, refresh = true) }
                        }
                    }
                }.awaitAll()
                done += batch.size
                _state.value = "ARKOD čestice oko vas: spremljeno ${done * 100 / tiles.size.coerceAtLeast(1)} %"
            }
            _state.value = null
            ArkodCache.prune()
        }
    }
}
