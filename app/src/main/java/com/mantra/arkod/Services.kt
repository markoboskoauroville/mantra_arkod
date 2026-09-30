package com.mantra.arkod

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A LIGHT FOR EVERY SERVICE (30.9.2026, v14): *"in the main display, there should be status for every
 * service if it's online or offline. So there will be a short name of that service and a red ...
 * LED if it's offline and green if it's online. And in setting ... describe that certain service what
 * it does and it's offline and what cannot be done because of that."*
 *
 * Every request the app makes reports here (ParcelNet, the tile fetcher), so the lights say what the
 * services last did for him; a light check every few minutes asks the quiet ones (never Google,
 * whose every request is on his key). Pure: the lights are decided here and tested in CoreTest.
 */
object Services {

    enum class Service(val short: String, val title: String, val does: String, val whenDown: String) {
        WMS(
            "WMS", "ARKOD layer (the state's map service)",
            "Draws the state's parcel lines on every map, and says which parcel is under a tap.",
            "No parcel lines where the phone has none kept, and a tap cannot say which parcel it is. Kept tiles, Moje čestice and parcel caches still show and still open.",
        ),
        WFS(
            "WFS", "Parcel outlines (the state's feature service)",
            "Gives each parcel's exact outline: for search results, Moje čestice and new parcel caches.",
            "A parcel found by search opens without its outline, and a new parcel cache cannot be filled. Outlines already kept still show.",
        ),
        OSS(
            "KAT", "Cadastre (OSS): posjedovni list and search",
            "Posjedovni list: holders, shares, land uses, area. And the parcel number search.",
            "Only sheets already on the phone open, and a number can be found only in a k.o. searched before.",
        ),
        ZK(
            "ZK", "Land registry (zemljišna knjiga): vlasnički list",
            "Vlasnički list: owners, shares and burdens, from the land registry.",
            "Only owner sheets already on the phone open.",
        ),
        OSM(
            "OSM", "OpenStreetMap",
            "The default map.",
            "Only the map tiles already seen show. The OFF map works without it.",
        ),
        GOOGLE(
            "GOO", "Google (your key)",
            "Google's map and the Google search field.",
            "No Google map and no place search. OSM and the OFF map work.",
        ),
    }

    /** Which service a request went to; null for anything else. */
    fun of(url: String): Service? = when {
        "cp_wms/wms" in url -> Service.WMS
        "inspire/cp/wfs" in url -> Service.WFS
        "oss.uredjenazemlja.hr" in url && ("/lr/" in url || "/lr-units/" in url || "search-lr-parcels" in url) -> Service.ZK
        "oss.uredjenazemlja.hr" in url -> Service.OSS
        "openstreetmap.org" in url -> Service.OSM
        "googleapis.com" in url -> Service.GOOGLE
        else -> null
    }

    /** The last answer and the last failure, in ms since the epoch, and the reason it gave. */
    data class Health(val okAt: Long = 0L, val failAt: Long = 0L, val reason: String = "")

    enum class Light { GREEN, RED, GREY }

    /** Green when the last word from it was an answer, red when it was a failure, grey when never asked. */
    fun light(h: Health?): Light = when {
        h == null || (h.okAt == 0L && h.failAt == 0L) -> Light.GREY
        h.okAt >= h.failAt -> Light.GREEN
        else -> Light.RED
    }

    /** One line for the settings: "answered 12 s ago", "down since 16:20 · ORA-01000 …", "not asked yet". */
    fun said(h: Health?, now: Long, clock: (Long) -> String): String = when (light(h)) {
        Light.GREY -> "not asked yet"
        Light.GREEN -> "online · answered ${ago(now - h!!.okAt)}"
        Light.RED -> "offline since ${clock(h!!.failAt)}" + (h.reason.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    }

    fun ago(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "$s s ago"
            s < 3600 -> "${s / 60} min ago"
            else -> "${s / 3600} h ago"
        }
    }

    /** Whether a service should be asked by the light check: not heard from for [everyMs]. */
    fun due(h: Health?, now: Long, everyMs: Long): Boolean = h == null || now - maxOf(h.okAt, h.failAt) >= everyMs

    // --- what every request reports ----------------------------------------------------------------

    private val _health = MutableStateFlow<Map<Service, Health>>(emptyMap())
    val health: StateFlow<Map<Service, Health>> = _health

    fun ok(url: String, now: Long = System.currentTimeMillis()) {
        val s = of(url) ?: return
        update(s) { it.copy(okAt = now) }
    }

    fun failed(url: String, reason: String?, now: Long = System.currentTimeMillis()) {
        val s = of(url) ?: return
        update(s) { it.copy(failAt = now, reason = reason.orEmpty().take(120)) }
    }

    private fun update(s: Service, change: (Health) -> Health) {
        var event: Event? = null
        synchronized(this) {
            val before = _health.value[s]
            val after = change(before ?: Health())
            _health.value = _health.value + (s to after)
            val was = light(before)
            val now = light(after)
            // THE LOG (v16): a service going down or coming back, with the time and its reason.
            // Logged: going down (a first failure too), and coming back after being down. A first
            // answer after the app opens is not news.
            if ((now == Light.RED && was != Light.RED) || (now == Light.GREEN && was == Light.RED)) {
                event = Event(maxOf(after.okAt, after.failAt), s, now == Light.GREEN, if (now == Light.RED) after.reason else "")
                _log.value = (listOf(event!!) + _log.value).take(LOG_SIZE)
            }
        }
        event?.let { e -> onChange?.invoke(e) }
    }

    // --- the service log (v16) --------------------------------------------------------------------

    /**
     * *"this morning when we tested until 9, it was not working. And late afternoon after 4:30, this
     * service, one service was out of service. So we need to understand what's going on."* Every
     * service going down or coming back is kept here, newest first, and said on the map.
     */
    data class Event(val at: Long, val service: Service, val online: Boolean, val reason: String)

    const val LOG_SIZE = 300

    private val _log = MutableStateFlow<List<Event>>(emptyList())
    val log: StateFlow<List<Event>> = _log

    /** Called on every change of a light (Screens says it on the map and keeps the log). */
    @Volatile var onChange: ((Event) -> Unit)? = null

    fun restore(events: List<Event>) { _log.value = events.take(LOG_SIZE) }

    fun encodeLog(events: List<Event>): String =
        events.take(LOG_SIZE).joinToString("\n") { "${it.at}|${it.service.name}|${if (it.online) 1 else 0}|${it.reason.replace('|', '/').replace('\n', ' ')}" }

    fun decodeLog(text: String?): List<Event> = text.orEmpty().split('\n').mapNotNull { line ->
        val f = line.split('|', limit = 4)
        if (f.size < 3) return@mapNotNull null
        val service = Service.values().firstOrNull { it.name == f[1] } ?: return@mapNotNull null
        Event(f[0].toLongOrNull() ?: return@mapNotNull null, service, f[2] == "1", f.getOrElse(3) { "" })
    }

    /** "16:31 WFS offline · ORA-01000 …" or "17:05 WFS back online". */
    fun said(e: Event, clock: (Long) -> String): String =
        "${clock(e.at)} ${e.service.short} " + if (e.online) "back online" else "offline" + (e.reason.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
}
