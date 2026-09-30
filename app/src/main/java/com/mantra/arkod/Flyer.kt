package com.mantra.arkod

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * FLY-THROUGH SCANNING, FLYING (30.9.2026, v16). The words and the line are in [Fly]; this asks.
 *
 * On: every time the map rests at z16 or closer, a 5 x 5 grid over the screen is asked which parcel
 * is under each point (GetFeatureInfo), each new parcel's posjedovni list is read (and its vlasnički
 * list when the first says nothing), and every one that mentions his words is outlined and selected
 * on its own. Three at a time; a parcel is read once per flight; every sheet read is kept (cache
 * king), so flying back over the same place asks nothing. Every step is on the line.
 */
object Flyer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var criteria: List<List<String>> = emptyList()
    private val seen = HashSet<Long>()
    private var lastAt: Pair<Double, Double>? = null

    private val _state = MutableStateFlow(Fly.State())
    val state: StateFlow<Fly.State> = _state

    /** A parcel that fitted: the screen draws it with the others found and selects it. */
    @Volatile var onFound: ((Parcels.Parcel, String) -> Unit)? = null

    fun start(query: String) {
        job?.cancel()
        criteria = Sniff.criteria(query)
        synchronized(seen) { seen.clear() }
        lastAt = null
        _state.value = Fly.State(query = query.trim(), on = true)
    }

    fun stop() {
        job?.cancel()
        _state.value = Fly.State()
    }

    private fun update(change: (Fly.State) -> Fly.State) {
        synchronized(this) { _state.value = change(_state.value) }
    }

    /** The map rests at [lat], [lon], [zoom]. */
    fun viewSettled(lat: Double, lon: Double, zoom: Int) {
        if (!_state.value.on) return
        if (zoom < 16) { update { it.copy(stage = Fly.Stage.ZOOM) }; return }
        val last = lastAt
        if (last != null && Geo.distance(last.first, last.second, lat, lon) < 60.0) return
        lastAt = lat to lon
        val tile = 360.0 / (1 shl zoom)
        val halfLon = tile * 0.8
        val halfLat = tile * 1.5 * Math.cos(Math.toRadians(lat))
        val points = Sniff.grid(lat - halfLat, lon - halfLon, lat + halfLat, lon + halfLon, 5)
        job?.cancel()
        update { it.copy(stage = Fly.Stage.SCANNING, asked = 0, total = points.size, selecting = null, problem = null) }
        job = scope.launch {
            val gate = Semaphore(3)
            points.map { (la, lo) -> async { gate.withPermit { scan(la, lo) } } }.awaitAll()
            update { it.copy(stage = Fly.Stage.DONE, selecting = null) }
        }
    }

    private suspend fun scan(la: Double, lo: Double) {
        val under = runCatching { ParcelNet.at(la, lo) }
        update { it.copy(asked = it.asked + 1) }
        val p = under.getOrElse {
            update { s -> s.copy(problem = "ARKOD (WMS) not answering") }
            return
        } ?: return
        synchronized(seen) { if (!seen.add(p.id)) return }
        val record = runCatching { Parcels.parseRecord(read(Parcels.recordUrl(p.id))) }.getOrElse {
            update { s -> s.copy(problem = "cadastre (KAT) not answering") }
            return
        }
        update { it.copy(read = it.read + 1) }
        var why = Fly.why(record, emptyList(), criteria)
        if (why == null) {
            val folios = record.landBooks.filter { it.bookId.isNotBlank() && it.unit.isNotBlank() }
                .distinctBy { it.bookId to it.unit }
                .mapNotNull { b -> runCatching { Parcels.parseFolio(read(Parcels.folioUrl(b.bookId, b.unit))) }.getOrNull() }
            why = Fly.why(null, folios, criteria)
        }
        if (why == null) return
        update { it.copy(selecting = p.number) }
        val rings = runCatching { ParcelNet.shapes(listOf(p.reference)) }.getOrNull()?.firstOrNull { it.reference == p.reference }?.rings
            ?.takeIf { it.isNotEmpty() }
            ?: runCatching { ParcelNet.outline(la, lo) }.getOrNull().orEmpty()
        update { it.copy(found = it.found + (p.number to why)) }
        onFound?.invoke(p.copy(rings = rings), why)
        delay(600) // long enough to read "selecting" on the line
        update { if (it.selecting == p.number) it.copy(selecting = null) else it }
    }

    /** A sheet: off the phone when kept, else from the state and kept (a flight is his own doing). */
    private fun read(url: String): String = ParcelNet.peek(url).also { ParcelNet.keep(url, it) }
}
