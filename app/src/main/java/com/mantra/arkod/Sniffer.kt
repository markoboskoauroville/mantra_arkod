package com.mantra.arkod

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * THE SNIFFER, RUNNING (30.9.2026, v12). The decisions are in [Sniff]; this asks the state.
 *
 * While the cache key is on:
 *  - when the map rests at z16 or closer, the parcels under a 4 x 4 grid over the screen are found
 *    (GetFeatureInfo, a fifth of a second each) and their sheets read;
 *  - when he opens a sheet, the other parcels of its owner sheet (the folio's list A) are read.
 * A sheet read so is kept, with its owner sheets and its outline, only when it fits his cache
 * criteria (no criteria: always). Two at a time, never the same parcel twice, a kept one never
 * asked again. Off: everything stops at once.
 */
object Sniffer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queue = Channel<Parcels.Parcel>(Channel.UNLIMITED)
    private var workers: Job? = null
    private var looking: Job? = null
    private val seen = HashSet<Long>()
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)
    private var lastAt: Pair<Double, Double>? = null

    private val _tally = MutableStateFlow(Sniff.Tally())
    val tally: StateFlow<Sniff.Tally> = _tally

    /** His keywords, read when each sheet is judged, so a change counts at once. */
    @Volatile var criteria: List<List<String>> = emptyList()

    /** Every sheet kept goes into Imenik too; Screens sets this. */
    @Volatile var onSheet: ((Parcels.Parcel, Parcels.Record, List<Parcels.Folio>) -> Unit)? = null

    fun start() {
        if (workers?.isActive == true) return
        queue = Channel(Channel.UNLIMITED)
        val q = queue
        workers = scope.launch {
            (1..2).map {
                launch {
                    for (p in q) {
                        if (!isActive) break
                        try { sniff(p) } finally { pending.decrementAndGet(); idle() }
                    }
                }
            }
        }
    }

    fun stop() {
        looking?.cancel()
        workers?.cancel()
        queue.close()
        pending.set(0)
        synchronized(seen) { seen.clear() }
        lastAt = null
        _tally.value = _tally.value.copy(busy = false)
    }

    private fun offer(p: Parcels.Parcel) {
        if (p.id <= 0L) return
        synchronized(seen) { if (!seen.add(p.id)) return }
        _tally.value = _tally.value.copy(busy = true)
        pending.incrementAndGet()
        if (queue.trySend(p).isFailure) pending.decrementAndGet()
    }

    /** The map rests at [lat], [lon], [zoom]: what is under the screen, once per 150 m moved. */
    fun viewSettled(lat: Double, lon: Double, zoom: Int) {
        if (zoom < 16 || workers?.isActive != true) return
        val last = lastAt
        if (last != null && Geo.distance(last.first, last.second, lat, lon) < 150.0) return
        lastAt = lat to lon
        // About the screen of a phone held upright: 1.6 tiles across, 3 down, at this zoom.
        val tile = 360.0 / (1 shl zoom)
        val halfLon = tile * 0.8
        val halfLat = tile * 1.5 * Math.cos(Math.toRadians(lat))
        looking?.cancel()
        looking = scope.launch {
            val gate = Semaphore(3)
            Sniff.grid(lat - halfLat, lon - halfLon, lat + halfLat, lon + halfLon, 4).map { (la, lo) ->
                async { gate.withPermit { runCatching { ParcelNet.at(la, lo) }.getOrNull() } }
            }.awaitAll().filterNotNull().distinctBy { it.id }.forEach { offer(it) }
            // Nothing new under the screen: the line goes (idle() would still see this job running).
            if (pending.get() <= 0) _tally.value = _tally.value.copy(busy = false)
        }
    }

    /** A sheet he opened: the other parcels of its owner sheets, found by number in its k.o. */
    fun follow(record: Parcels.Record, folios: List<Parcels.Folio>) {
        if (workers?.isActive != true || record.municipalityNumber.isBlank()) return
        val numbers = folios.flatMap { f -> f.parcels.mapNotNull { it.trim().split(Regex("""\s+""")).firstOrNull() } }
            .filter { it != record.number && Regex("""^\*?\d+(/\d+)?$""").matches(it) }
            .distinct().take(40)
        if (numbers.isEmpty()) return
        scope.launch {
            runCatching { ParcelNet.find(record.municipalityNumber, numbers) }.getOrNull().orEmpty().forEach { offer(it) }
        }
    }

    private suspend fun sniff(p: Parcels.Parcel) {
        val recordUrl = Parcels.recordUrl(p.id)
        if (ArkodCache.answer(recordUrl) != null) return // kept already: nothing to ask
        val text = runCatching { ParcelNet.peek(recordUrl) }.getOrNull()
        if (text == null) { _tally.value = _tally.value.let { it.copy(read = it.read + 1, failed = it.failed + 1) }; return }
        val record = runCatching { Parcels.parseRecord(text) }.getOrNull() ?: run { done(0, 0); return }
        val folioTexts = record.landBooks.filter { it.bookId.isNotBlank() && it.unit.isNotBlank() }
            .distinctBy { it.bookId to it.unit }
            .mapNotNull { b -> val u = Parcels.folioUrl(b.bookId, b.unit); runCatching { u to ParcelNet.peek(u) }.getOrNull() }
        val folios = folioTexts.mapNotNull { (_, t) -> runCatching { Parcels.parseFolio(t) }.getOrNull() }
        if (!Sniff.fits(Sniff.wordsOf(record, folios), criteria)) { done(kept = 0, skipped = 1); return }
        ParcelNet.keep(recordUrl, text)
        folioTexts.forEach { (u, t) -> ParcelNet.keep(u, t) }
        onSheet?.invoke(p, record, folios)
        runCatching { ParcelNet.shapes(listOf(p.reference)) }
        done(kept = 1, skipped = 0)
    }

    private fun done(kept: Int, skipped: Int) {
        _tally.value = _tally.value.let { it.copy(read = it.read + 1, kept = it.kept + kept, skipped = it.skipped + skipped) }
    }

    private fun idle() {
        if (pending.get() <= 0 && looking?.isActive != true) _tally.value = _tally.value.copy(busy = false)
    }
}
