package com.mantra.arkod

import android.content.Context
import android.content.SharedPreferences

/**
 * WHAT THE APP REMEMBERS (Mantra ARKOD, 29.9.2026).
 *
 * His words: *"there should be the view the app lands immediately after first installation.
 * Otherwise it remembers the states when it was left, and then on next run it just run into the
 * same state."* So the first run opens on OpenStreetMap over the whole of Croatia, and every run
 * after that opens on the map, the place, the zoom and the turn he left it on.
 *
 * My parcels (Moje čestice) live here too, each with its shape, so they draw with no signal.
 */
class Store(context: Context) {

    private val app: Context = context.applicationContext

    private val prefs: SharedPreferences =
        app.getSharedPreferences("mantra-arkod", Context.MODE_PRIVATE)

    /** Which of the three maps is up. OpenStreetMap on a first run: it needs no file and no key. */
    var layerId: String
        get() = prefs.getString(KEY_LAYER, Layers.FIRST.id) ?: Layers.FIRST.id
        set(v) = prefs.edit().putString(KEY_LAYER, v).apply()

    /** A .map file he chose with the file picker, instead of the downloaded one. */
    var mapFileUri: String?
        get() = prefs.getString(KEY_MAP_FILE, null)
        set(v) = prefs.edit().putString(KEY_MAP_FILE, v).apply()

    /** The folder finished tracks and exported sheets are copied into, so they outlive the app. */
    var exportFolderName: String?
        get() = prefs.getString(KEY_EXPORT_NAME, null)
        set(v) = prefs.edit().putString(KEY_EXPORT_NAME, v).apply()

    var exportTreeUri: String?
        get() = prefs.getString(KEY_EXPORT_TREE, null)
        set(v) = prefs.edit().putString(KEY_EXPORT_TREE, v).apply()

    /**
     * The keys he gave the app. They live in its own private storage and nowhere else: not in the
     * repository, not in a log line, not on the screen.
     */
    var keys: Map<Keys.Provider, String>
        get() = (prefs.getString(KEY_KEYS, "") ?: "").split("\n")
            .filter { it.contains('|') }
            .mapNotNull { line ->
                val provider = runCatching { Keys.Provider.valueOf(line.substringBefore('|')) }.getOrNull()
                val value = line.substringAfter('|')
                if (provider != null && value.isNotBlank()) provider to value else null
            }
            .toMap()
        set(v) = prefs.edit()
            .putString(KEY_KEYS, v.entries.joinToString("\n") { "${it.key.name}|${it.value}" })
            .apply()

    fun key(provider: Keys.Provider): String? = keys[provider]

    /** True when there is an offline map to draw, from either of its two places. */
    val hasOfflineMap: Boolean
        get() = MapDownload.isPresent(app) || mapFileUri != null

    /** Whether a settings section is folded open. Remembered between sessions. */
    fun opened(name: String): Boolean = prefs.getBoolean("open-$name", false)

    fun setOpened(name: String, value: Boolean) =
        prefs.edit().putBoolean("open-$name", value).apply()

    /**
     * THE PARCELS KEY (29.9.2026, v3): *"It should hide parcels overlay completely from the map."*
     * On: the state's parcels and his own are drawn. Off: nothing of the cadastre is on the map.
     */
    var cadastreOn: Boolean
        get() = prefs.getBoolean(KEY_CADASTRE_ON, true)
        set(v) = prefs.edit().putBoolean(KEY_CADASTRE_ON, v).apply()

    /**
     * ONLY MY PARCELS (29.9.2026, v3): *"all the time, no matter on or off, only my parcels drawn
     * and everything else is out."* Stronger than the key: while it is on, the state's parcels are
     * never drawn and his are always drawn.
     */
    var onlyMine: Boolean
        get() = prefs.getBoolean(KEY_ONLY_MINE, false)
        set(v) = prefs.edit().putBoolean(KEY_ONLY_MINE, v).apply()

    /** The parcel field on the map, under Google's; shown unless he hides it in Parcel view. */
    var parcelSearchOn: Boolean
        get() = prefs.getBoolean(KEY_PARCEL_SEARCH, true)
        set(v) = prefs.edit().putBoolean(KEY_PARCEL_SEARCH, v).apply()

    /** Imenik: every holder and owner the sheets opened on this phone named ([OwnerBook]). */
    var ownerBook: List<OwnerBook.Entry>
        get() = OwnerBook.decode(prefs.getString(KEY_OWNER_BOOK, "") ?: "")
        set(v) = prefs.edit().putString(KEY_OWNER_BOOK, OwnerBook.encode(v)).apply()

    /** Which of VTM's render themes draws the offline file. The plain one suits mapsforge's Croatia. */
    var themeName: String
        get() = prefs.getString(KEY_THEME, "DEFAULT") ?: "DEFAULT"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    /**
     * WHICH GOOGLE VIEW THE GOOGLE KEY SHOWS. Satellite by default: a parcel is read against the
     * ground, the hedges and the walls, better than against a road map.
     */
    var googleViewId: String
        get() = prefs.getString(KEY_GOOGLE_VIEW, Layers.GOOGLE_SATELLITE.id) ?: Layers.GOOGLE_SATELLITE.id
        set(v) = prefs.edit().putString(KEY_GOOGLE_VIEW, v).apply()

    /** The keyring, as one line per key. */
    var keyring: List<Keyring.Key>
        get() = Keyring.decode(prefs.getString(KEY_RING, null))
        set(v) = prefs.edit().putString(KEY_RING, Keyring.encode(v)).apply()

    /** The colour a loaded track is drawn in, as an ARGB value. */
    var trackColour: Long
        get() = prefs.getLong(KEY_TRACK_COLOUR, 0xFF34D399)
        set(v) = prefs.edit().putLong(KEY_TRACK_COLOUR, v).apply()

    /** The last place the map was looking. A first run starts over the whole of Croatia. */
    var lastLat: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LAT, DEFAULT_LAT_BITS))
        set(v) = prefs.edit().putLong(KEY_LAST_LAT, java.lang.Double.doubleToRawLongBits(v)).apply()

    var lastLon: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LON, DEFAULT_LON_BITS))
        set(v) = prefs.edit().putLong(KEY_LAST_LON, java.lang.Double.doubleToRawLongBits(v)).apply()

    var lastZoom: Int
        get() = prefs.getInt(KEY_LAST_ZOOM, Layers.HOME_ZOOM)
        set(v) = prefs.edit().putInt(KEY_LAST_ZOOM, v.coerceIn(2, 21)).apply()

    /** How far the map was turned, so it opens turned the same way. */
    var lastBearing: Float
        get() = prefs.getFloat(KEY_LAST_BEARING, 0f)
        set(v) = prefs.edit().putFloat(KEY_LAST_BEARING, v).apply()

    /**
     * MY PARCELS (Moje čestice, 29.9.2026): the parcels he keeps, each with its colour, its line
     * style and its shape. The preference key is the one Mantra Trail's highlights used, so the
     * format stays one.
     */
    var parcelMarks: List<Parcels.Mark>
        get() = Parcels.decode(prefs.getString(KEY_PARCEL_MARKS, "") ?: "")
        set(v) = prefs.edit().putString(KEY_PARCEL_MARKS, Parcels.encode(v)).apply()

    /** The colour the next parcel he keeps takes: the last one he chose. */
    var parcelColour: Long
        get() = prefs.getLong(KEY_PARCEL_COLOUR, 0xFFE8A64BL)
        set(v) = prefs.edit().putLong(KEY_PARCEL_COLOUR, v).apply()

    /** The line style the next parcel he keeps takes: dashed until he chooses another. */
    var parcelStyle: Parcels.LineStyle
        get() = prefs.getString(KEY_PARCEL_STYLE, null)
            ?.let { s -> Parcels.LineStyle.entries.firstOrNull { it.name == s } } ?: Parcels.LineStyle.DASHED
        set(v) = prefs.edit().putString(KEY_PARCEL_STYLE, v.name).apply()

    /**
     * THE OWNER SHEETS HE FOUND BY HAND: where the state links no folio to a parcel, the one he
     * found is kept, cadastral reference to (land book id, folio), and opens by itself.
     */
    var folioLinks: Map<String, Pair<String, String>>
        get() = Parcels.decodeLinks(prefs.getString(KEY_FOLIO_LINKS, "") ?: "")
        set(v) = prefs.edit().putString(KEY_FOLIO_LINKS, Parcels.encodeLinks(v)).apply()

    /** Whether cadastre tiles are fetched ahead in the background. On unless he turns it off. */
    var prefetch: Boolean
        get() = prefs.getBoolean(KEY_PREFETCH, true)
        set(v) = prefs.edit().putBoolean(KEY_PREFETCH, v).apply()

    companion object {
        private const val KEY_FOLIO_LINKS = "folioLinks"
        private const val KEY_PARCEL_MARKS = "parcelMarks"
        private const val KEY_PARCEL_COLOUR = "parcelColour"
        private const val KEY_PARCEL_STYLE = "parcelStyle"
        private const val KEY_PREFETCH = "prefetch"
        private const val KEY_LAYER = "layer"
        private const val KEY_KEYS = "keys"
        private const val KEY_TRACK_COLOUR = "trackColour"
        private const val KEY_CADASTRE_ON = "cadastreOn"
        private const val KEY_ONLY_MINE = "onlyMine"
        private const val KEY_PARCEL_SEARCH = "parcelSearchOn"
        private const val KEY_OWNER_BOOK = "ownerBook"
        private const val KEY_THEME = "themeName"
        private const val KEY_GOOGLE_VIEW = "googleView"
        private const val KEY_RING = "keyring"
        private const val KEY_MAP_FILE = "mapFile"
        private const val KEY_EXPORT_TREE = "exportTree"
        private const val KEY_EXPORT_NAME = "exportName"
        private const val KEY_LAST_LAT = "lastLat"
        private const val KEY_LAST_LON = "lastLon"
        private const val KEY_LAST_ZOOM = "lastZoom"
        private const val KEY_LAST_BEARING = "lastBearing"

        private val DEFAULT_LAT_BITS = java.lang.Double.doubleToRawLongBits(Layers.HOME_LAT)
        private val DEFAULT_LON_BITS = java.lang.Double.doubleToRawLongBits(Layers.HOME_LON)
    }
}
