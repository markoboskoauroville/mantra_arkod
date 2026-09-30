package com.mantra.arkod

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File

/**
 * THE PARCEL CACHES ON THE PHONE, AND THE JOB THAT FILLS ONE (30.9.2026, v5). The model and the
 * search are in [ParcelCache], where Test 1 reaches them; this is the disk and the network.
 *
 * One folder a cache: info.json (its name, box, colour, line), items.json (every parcel, its outline
 * and what its sheets say), answers.json (the state's own answers, so a sheet opens from the cache
 * with no signal even after the ARKOD tiles on the phone were cleared).
 */
object ParcelCaches {

    @Volatile
    private var root: File? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _all = MutableStateFlow<List<ParcelCache.Cache>>(emptyList())

    /** Every cache on the phone, loaded; the map and the search read this. */
    val all: StateFlow<List<ParcelCache.Cache>> = _all

    /** The answers of each cache, read from disk the first time a sheet asks for one. */
    private val answers = HashMap<String, Map<String, String>>()

    fun init(context: Context) {
        root = File(context.filesDir, "caches").apply { mkdirs() }
        scope.launch { _all.value = load() }
    }

    private fun dir(id: String): File? = root?.let { File(it, id) }

    private fun load(): List<ParcelCache.Cache> =
        root?.listFiles()?.filter { it.isDirectory }?.mapNotNull { d ->
            val info = runCatching { ParcelCache.decodeInfo(File(d, "info.json").readText()) }.getOrNull() ?: return@mapNotNull null
            val items = runCatching { ParcelCache.decodeItems(File(d, "items.json").readText()) }.getOrDefault(emptyList())
            ParcelCache.Cache(info, items)
        }?.sortedByDescending { it.info.createdMs }.orEmpty()

    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val part = File(file.parentFile, file.name + ".part")
        part.writeText(text)
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    private fun save(cache: ParcelCache.Cache, answersNow: Map<String, String>? = null) {
        val d = dir(cache.info.id) ?: return
        write(File(d, "items.json"), ParcelCache.encodeItems(cache.items))
        write(File(d, "info.json"), ParcelCache.encodeInfo(cache.info))
        if (answersNow != null) {
            write(File(d, "answers.json"), JSONObject(answersNow).toString())
            synchronized(answers) { answers[cache.info.id] = answersNow }
        }
        _all.value = (_all.value.filterNot { it.info.id == cache.info.id } + cache).sortedByDescending { it.info.createdMs }
    }

    /** Colour, line, name, shown: only the info changes, the parcels stay as they are. */
    fun update(info: ParcelCache.Info) {
        val old = _all.value.firstOrNull { it.info.id == info.id } ?: return
        val next = old.copy(info = info)
        _all.value = _all.value.map { if (it.info.id == info.id) next else it }
        scope.launch { dir(info.id)?.let { write(File(it, "info.json"), ParcelCache.encodeInfo(info)) } }
    }

    fun delete(id: String) {
        _all.value = _all.value.filterNot { it.info.id == id }
        synchronized(answers) { answers.remove(id) }
        scope.launch { dir(id)?.deleteRecursively() }
    }

    /** The state's answer to [url] as some cache kept it, and when that cache was made. */
    fun answer(url: String): Pair<String, Long>? {
        for (c in _all.value) {
            val map = synchronized(answers) {
                answers.getOrPut(c.info.id) {
                    val f = dir(c.info.id)?.let { File(it, "answers.json") }
                    runCatching {
                        val o = JSONObject(f?.readText() ?: "{}")
                        o.keys().asSequence().associateWith { o.getString(it) }
                    }.getOrDefault(emptyMap())
                }
            }
            map[url]?.let { return it to c.info.createdMs }
        }
        return null
    }

    fun bytes(): Long = root?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    // --- the job -----------------------------------------------------------------------------------

    /** What the job is doing, in one line for the map and the numbers behind it. */
    data class Progress(
        val name: String,
        val stage: String,
        val done: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
        val perSecond: Double = 0.0,
        val finished: Boolean = false,
        val problem: String? = null,
    ) {
        val line: String
            get() = buildString {
                append("cache \"").append(name).append("\": ").append(stage)
                if (total > 0) append(" ").append(done).append("/").append(total)
                if (perSecond > 0.05 && !finished) append(" · ").append("%.1f".format(java.util.Locale.ROOT, perSecond)).append("/s")
                if (perSecond > 0.05 && total > done && !finished) {
                    val s = ((total - done) / perSecond).toInt()
                    append(" · ~").append(if (s >= 90) "${(s + 30) / 60} min" else "$s s").append(" left")
                }
                if (failed > 0) append(" · ").append(failed).append(" failed")
                problem?.let { append(" · ").append(it) }
            }
    }

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    fun cancel() {
        job?.cancel()
        _progress.value = _progress.value?.copy(stage = "stopped; what was read is kept", finished = true, perSecond = 0.0)
    }

    fun dismiss() {
        if (!running) _progress.value = null
    }

    /**
     * FILL A CACHE WITH EVERYTHING IN [box]: the outlines from the state's WFS, five hundred to a
     * page (fifteen seconds a page, measured 30.9.2026); then each parcel's possession sheet from
     * OSS, six at once (seventeen a second); then, if [owners], every owner sheet those name, each
     * folio once. Saved after the outlines and every three hundred sheets, so a stop keeps what was
     * read and the parcels are drawn from the first minute.
     */
    fun start(name: String, box: ParcelCache.Box, zoom: Int, colour: Long, owners: Boolean) {
        if (running) return
        val id = "c" + System.currentTimeMillis()
        job = scope.launch {
            var info = ParcelCache.Info(id = id, name = name, createdMs = System.currentTimeMillis(), box = box, zoom = zoom, colour = colour)
            val kept = java.util.concurrent.ConcurrentHashMap<String, String>()
            fun say(p: Progress) { _progress.value = p }
            try {
                // 1. THE OUTLINES
                val found = ArrayList<Pair<Parcels.Parcel, Pair<Double, Double>?>>()
                val seen = HashSet<Long>()
                var total = 0
                var page = 0
                val began = System.currentTimeMillis()
                while (isActive) {
                    say(Progress(name, if (page == 0) "asking the state for the čestice in the view (≈15 s)" else "outlines of čestice", found.size, total))
                    var text: String? = null
                    var lastProblem: Exception? = null
                    // THE STATE'S WFS FAILS IN SPELLS (30.9.2026: every request refused for its
                    // database's "maximum open cursors" an hour after it answered in fifteen
                    // seconds), so the job waits it out, about five minutes, and says so.
                    for (attempt in 1..TRIES) {
                        try {
                            text = ParcelNet.boxPage(box, found.size, PAGE)
                            break
                        } catch (e: Exception) {
                            lastProblem = e
                            if (attempt == TRIES) break
                            val wait = WAITS[(attempt - 1).coerceAtMost(WAITS.size - 1)]
                            say(Progress(name, "the state's čestice service is failing; again in $wait s (try ${attempt + 1} of $TRIES)", found.size, total, problem = e.message))
                            delay(wait * 1000L)
                        }
                    }
                    val answer = text ?: throw lastProblem ?: IllegalStateException("the state did not answer")
                    val got = ParcelCache.parseFeatures(answer)
                    // Every outline a cache reads is kept one by one too (v11): found from anywhere.
                    ParcelNet.keepShapes(got.map { it.first })
                    if (page == 0) total = ParcelCache.matched(answer) ?: got.size
                    found.addAll(got.filter { seen.add(it.first.id) })
                    page += 1
                    if (got.size < PAGE || found.size >= total || found.size >= MAX_PARCELS) break
                }
                total = found.size
                if (total == 0) {
                    say(Progress(name, "the state has no čestice in this view", finished = true))
                    return@launch
                }
                var items = found.map { (p, label) -> ParcelCache.Item(p.id, p.number, p.reference, p.areaM2, p.rings, label) }
                info = info.copy(count = items.size)
                save(ParcelCache.Cache(info, items))
                val outlineSeconds = (System.currentTimeMillis() - began) / 1000

                // 2. THE POSSESSION SHEETS AND LAND USE
                val records = java.util.concurrent.ConcurrentHashMap<Long, Parcels.Record>()
                val gate = Semaphore(PARALLEL)
                var done = 0
                var failed = 0
                val t0 = System.currentTimeMillis()
                fun rate(): Double = done / ((System.currentTimeMillis() - t0) / 1000.0).coerceAtLeast(0.5)
                say(Progress(name, "posjedovni listovi (outlines took $outlineSeconds s)", 0, total))
                items.chunked(CHECKPOINT).forEach { chunk ->
                    if (!isActive) return@forEach
                    chunk.map { item ->
                        async {
                            gate.withPermit {
                                val url = Parcels.recordUrl(item.id)
                                val text = runCatching { ParcelNet.keptText(url) }.getOrNull()
                                    ?: runCatching { delay(1_500); ParcelNet.keptText(url) }.getOrNull()
                                synchronized(this@ParcelCaches) {
                                    if (text != null) {
                                        kept[url] = text
                                        runCatching { Parcels.parseRecord(text) }.getOrNull()?.let { records[item.id] = it }
                                    } else failed += 1
                                    done += 1
                                }
                                say(Progress(name, "posjedovni listovi", done, total, failed, rate()))
                            }
                        }
                    }.awaitAll()
                    items = items.map { i -> records[i.id]?.let { r -> ParcelCache.itemOf(i.parcel(), i.label, r, emptyList()) } ?: i }
                    info = info.copy(read = items.count { it.read }, places = ParcelCache.places(items))
                    save(ParcelCache.Cache(info, items), HashMap(kept))
                }

                // 3. THE OWNER SHEETS, each folio once
                if (owners && isActive) {
                    val wanted = records.values.flatMap { r -> r.landBooks.filter { it.bookId.isNotBlank() && it.unit.isNotBlank() } }
                        .map { it.bookId to it.unit }.distinct()
                    val folios = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, Parcels.Folio>()
                    done = 0
                    failed = 0
                    val t1 = System.currentTimeMillis()
                    fun rate1(): Double = done / ((System.currentTimeMillis() - t1) / 1000.0).coerceAtLeast(0.5)
                    wanted.chunked(CHECKPOINT).forEach { chunk ->
                        if (!isActive) return@forEach
                        chunk.map { key ->
                            async {
                                gate.withPermit {
                                    val url = Parcels.folioUrl(key.first, key.second)
                                    val text = runCatching { ParcelNet.keptText(url) }.getOrNull()
                                    synchronized(this@ParcelCaches) {
                                        if (text != null) {
                                            kept[url] = text
                                            runCatching { Parcels.parseFolio(text) }.getOrNull()?.let { folios[key] = it }
                                        } else failed += 1
                                        done += 1
                                    }
                                    say(Progress(name, "vlasnički listovi", done, wanted.size, failed, rate1()))
                                }
                            }
                        }.awaitAll()
                    }
                    items = items.map { i ->
                        val r = records[i.id] ?: return@map i
                        val f = r.landBooks.mapNotNull { folios[it.bookId to it.unit] }
                        ParcelCache.itemOf(i.parcel(), i.label, r, f)
                    }
                    save(ParcelCache.Cache(info.copy(places = ParcelCache.places(items)), items), HashMap(kept))
                }
                val names = items.sumOf { it.holders.size }
                say(Progress(name, "done: ${items.size} čestica · ${items.count { it.read }} listova · $names names", finished = true))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                say(Progress(name, "stopped", finished = true, problem = e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private const val PAGE = 500
    private const val TRIES = 8
    private val WAITS = listOf(5, 10, 20, 30, 60, 60, 90)
    private const val PARALLEL = 6
    private const val CHECKPOINT = 300

    /** A view bigger than this is a county, not a place: the job stops at it and says so. */
    const val MAX_PARCELS = 15_000
}
