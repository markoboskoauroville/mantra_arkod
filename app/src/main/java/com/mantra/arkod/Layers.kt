package com.mantra.arkod

/**
 * THE THREE MAPS (Mantra ARKOD, 29.9.2026).
 *
 * His words: *"we need to have all 3 maps, 3 types, down in the action buttons available
 * immediately, not as a toggle"*, and *"OpenStreetMap, which is online only ... is actually only
 * thing which works by default because it's free."* So there are three, each a key of its own:
 *
 *   Offline        a mapsforge file of Croatia on the phone; nothing to fetch once it is there.
 *   Google         Map Tiles API with HIS key (Keys.kt): no key is in this app, and without one
 *                  the screen explains how to make one.
 *   OpenStreetMap  online only, free, and the map the app opens on the first time.
 *
 * The cadastre (ARKOD) is drawn over whichever of them is up, always.
 */
enum class LayerKind {
    /** A .map file on the phone. */
    VECTOR_FILE,

    /** z/x/y raster tiles from a free server. */
    RASTER_XYZ,

    /** Google Map Tiles: a session is made with his key, then plain tiles. Never kept. */
    GOOGLE_TILES,
}

data class MapLayer(
    val id: String,
    /** The full name, for the settings list. */
    val label: String,
    /** The name on the map screen's top line. */
    val name: String,
    val kind: LayerKind,
    val offline: Offline,
    val attribution: String,
    val url: String? = null,
    /** The last zoom this service actually has tiles for. Past it, the map is scaled, not fetched. */
    val maxZoom: Int = 18,
    val minZoom: Int = 2,
    /** The service whose key this layer needs, or null when it needs none. */
    val provider: Keys.Provider? = null,
    val family: Family = Family.OFFLINE,
    /** Which of Google's four views this is. */
    val googleView: GoogleView? = null,
) {
    enum class Offline { COMPLETE, NONE }

    /** The three keys on the map screen, one per family. */
    enum class Family { OFFLINE, GOOGLE, OSM }

    enum class GoogleView(val mapType: String, val overlayRoads: Boolean) {
        NORMAL("roadmap", false),
        SATELLITE("satellite", false),
        TERRAIN("terrain", false),
        HYBRID("satellite", true),
    }

    /**
     * How far the VIEW may go, which is not the same as how far the tiles go: past the last real
     * tile the engine draws it enlarged, coarse but readable, for three more levels.
     */
    val viewMaxZoom: Int
        get() = if (kind == LayerKind.VECTOR_FILE) 22 else minOf(22, maxZoom + 3)
}

object Layers {

    /**
     * WHO IS ASKING, for every request this app makes. OpenStreetMap's tile policy requires an
     * application to name itself, and the state's services see the same line.
     */
    const val USER_AGENT = "MantraARKOD/1 (+https://github.com/markoboskoauroville/mantra_arkod)"

    val OFFLINE = MapLayer(
        family = MapLayer.Family.OFFLINE,
        id = "offline",
        label = "Offline karta Hrvatske",
        name = "Offline",
        kind = LayerKind.VECTOR_FILE,
        offline = MapLayer.Offline.COMPLETE,
        attribution = "© OpenStreetMap contributors · mapsforge",
    )

    private fun google(id: String, name: String, view: MapLayer.GoogleView) = MapLayer(
        family = MapLayer.Family.GOOGLE,
        id = id,
        label = "Google $name",
        name = name,
        kind = LayerKind.GOOGLE_TILES,
        offline = MapLayer.Offline.NONE,
        attribution = "Google",
        url = "https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session={session}&key={key}",
        maxZoom = 21,
        provider = Keys.Provider.GOOGLE,
        googleView = view,
    )

    val GOOGLE = google("google", "karta", MapLayer.GoogleView.NORMAL)
    val GOOGLE_SATELLITE = google("google-sat", "satelit", MapLayer.GoogleView.SATELLITE)
    val GOOGLE_TERRAIN = google("google-ter", "teren", MapLayer.GoogleView.TERRAIN)
    val GOOGLE_HYBRID = google("google-hyb", "hibrid", MapLayer.GoogleView.HYBRID)

    val GOOGLE_ALL: List<MapLayer> = listOf(GOOGLE, GOOGLE_SATELLITE, GOOGLE_TERRAIN, GOOGLE_HYBRID)

    /**
     * OPENSTREETMAP, ONLINE ONLY. The standard tiles, served free by the OSM Foundation under its
     * tile usage policy: the app names itself ([USER_AGENT]) and never pre-fetches them.
     */
    val OSM = MapLayer(
        family = MapLayer.Family.OSM,
        id = "osm",
        label = "OpenStreetMap",
        name = "OSM",
        kind = LayerKind.RASTER_XYZ,
        offline = MapLayer.Offline.NONE,
        attribution = "© OpenStreetMap contributors",
        url = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        maxZoom = 19,
    )

    val ALL: List<MapLayer> = listOf(OFFLINE, OSM) + GOOGLE_ALL

    /** The map a fresh install opens on: the one that needs neither a download nor a key. */
    val FIRST = OSM

    /**
     * THE OFFLINE MAP'S VIEWS: the same file drawn by four themes. The name is what he chooses
     * between; the theme behind it is this app's business.
     */
    data class OfflineView(val label: String, val theme: String, val about: String)

    val OFFLINE_VIEWS: List<OfflineView> = listOf(
        OfflineView("Plain", "DEFAULT", "roads and places, calm"),
        OfflineView("Hiking", "MANTRA", "contours and paths"),
        OfflineView("Outline", "OSMARENDER", "thin lines, strong labels"),
        OfflineView("Night", "NEWTRON", "dark ground, light roads"),
    )

    fun offlineViewFor(theme: String): OfflineView =
        OFFLINE_VIEWS.firstOrNull { it.theme == theme } ?: OFFLINE_VIEWS.first()

    fun byId(id: String): MapLayer = ALL.firstOrNull { it.id == id } ?: FIRST

    fun of(family: MapLayer.Family): List<MapLayer> = ALL.filter { it.family == family }

    /**
     * THE SAME URL, SPLIT THE WAY THE GPU ENGINE WANTS IT: a base, and a path with {Z} {X} {Y} in
     * it. Whatever follows the numbers (a key, a session) must stay in the path, or every tile is
     * refused.
     */
    fun tilePattern(layer: MapLayer, auth: String? = null, key: String? = null): Pair<String, String>? {
        val sample = tileUrl(layer, 0, 0, 0, auth, key) ?: return null
        val pattern = sample.replace("/0/0/0", "/{Z}/{X}/{Y}")
        val cut = pattern.indexOf("/{Z}")
        if (cut < 0) return null
        return pattern.substring(0, cut) to pattern.substring(cut)
    }

    /**
     * One tile's URL; null when the layer draws nothing of ours, and null when it needs a key or
     * a session it has not been given (an empty key fetches four hundred refusals).
     */
    fun tileUrl(layer: MapLayer, zoom: Int, x: Int, y: Int, auth: String? = null, key: String? = null): String? {
        val template = layer.url ?: return null
        if (layer.kind == LayerKind.VECTOR_FILE) return null
        if (layer.provider != null && (key.isNullOrEmpty())) return null
        if (layer.kind == LayerKind.GOOGLE_TILES && auth.isNullOrEmpty()) return null
        return template
            .replace("{z}", zoom.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
            .replace("{key}", key ?: "")
            .replace("{session}", auth ?: "")
    }

    /** What to say when a layer needs a key nobody has given it yet. */
    fun missingKey(layer: MapLayer): String? = when (layer.provider) {
        Keys.Provider.GOOGLE -> "Google karta treba vaš API ključ. Upute su na sredini zaslona."
        null -> null
    }

    /**
     * THE OFFLINE MAP IS CROATIA, AND ONLY CROATIA (29.9.2026): this app is for Croatian parcels,
     * so the choice of region is made for him and the download is one press.
     */
    object OfflineDownload {
        const val NAME = "croatia.map"
        const val URL = "https://download.mapsforge.org/maps/v5/europe/croatia.map"
        const val BYTES = 175_514_764L
        const val LABEL = "Hrvatska, 176 MB"
    }

    /** Where the app looks the first time it opens: all of Croatia on the screen. */
    const val HOME_LAT = 44.50
    const val HOME_LON = 16.10
    const val HOME_ZOOM = 7
}
