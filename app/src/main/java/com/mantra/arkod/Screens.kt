package com.mantra.arkod

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch


/**
 * THE MAP IS THE APP, AND THE PARCELS ARE ON IT (Mantra ARKOD, 29.9.2026).
 *
 * Mantra Trail's screen with the trail taken out, at his word: *"barcode layer is by default
 * enabled ... we are removing barcode icon ... we can remove finding the paths ... we need to have
 * all 3 maps, 3 types, down in the action buttons available immediately, not as a toggle. And the
 * feature when user clicks in the middle and gets in the full screen, that feature goes out.
 * Clicking in the middle, it's like clicking anywhere else. It selects the parcel beneath."*
 *
 * So: one line of numbers at the top; one row of keys at the bottom, with the three maps side by
 * side and his parcels one press away; the cadastre over every map, always; a tap anywhere picks
 * the parcel under it, the middle included. What a map needs before it can draw (the Croatia file,
 * his Google key) is asked for in the middle of the screen, where the map would be.
 */
private val GAP = 10.dp
private val KEY = 46.dp

/** The one size for the marks: the where key and the record circle. */
private val MARK = 22.dp

/** The crosshair over the map: bigger than the key's mark, and far quieter. */
private val CROSS = 34.dp

/** The compass's states: on (dark ink or light, chosen by the map) or off. */
private const val COMPASS_DARK = 0
private const val COMPASS_OFF = 2

private val TRACK_COLOURS = listOf(0xFF34D399L, 0xFFE8A64BL, 0xFFEF4444L, 0xFF60A5FAL, 0xFFF2DDB4L)

/** Where a build of this app can be downloaded: the version in settings opens it. */
const val RELEASES = "https://github.com/markoboskoauroville/mantra_arkod/releases/latest"

/** Bumped when a picker, a key or a download changes something a row shows. */
object UiTick {
    var n by mutableIntStateOf(0)
    fun bump() {
        n += 1
    }
}

@Composable
fun ArkodApp(
    store: Store,
    sensors: Sensors,
    version: String,
    onCanvas: (VtmCanvas) -> Unit,
    onWhereAmI: () -> Unit,
    onRecord: () -> Unit,
    onPause: () -> Unit,
    onChooseMapFile: () -> Unit,
    onChooseExportFolder: () -> Unit,
    onImportKeys: () -> Unit,
    onPasteKey: (String) -> Unit,
    onFetchOfflineMap: () -> Unit,
    tracks: () -> List<Folder.Entry>,
    folderLabel: String,
    onRenameJustFinished: (java.io.File, String) -> Unit,
    onDiscardRecording: (java.io.File) -> Unit,
    onDeleteTrack: (Folder.Entry) -> Unit,
    onRenameTrack: (Folder.Entry, String) -> Unit,
    onShowTrack: (Folder.Entry) -> Unit,
    onTestTiles: () -> Unit,
    onTestKey: (Keyring.Key) -> Unit,
    onRemoveKey: (Keyring.Key) -> Unit,
) {
    var layer by remember { mutableStateOf(Layers.byId(store.layerId)) }
    var settings by remember { mutableStateOf(false) }
    var compass by remember { mutableIntStateOf(store.compassMode) }
    var zoom by remember { mutableIntStateOf(store.lastZoom) }
    // A counter, not a flag: every canvas that appears is told to draw (17.9.2026).
    var ready by remember { mutableIntStateOf(0) }
    // LOCKED TO THE MIDDLE (15.9.2026). One press centres; two in a second hold it there.
    var follow by remember { mutableStateOf(false) }
    var lastCentreTap by remember { mutableLongStateOf(0L) }
    var mapTurn by remember { mutableFloatStateOf(0f) }

    val fix by Trail.fix.collectAsState()
    val stats by Trail.stats.collectAsState()
    val recordingSince by Trail.recordingSince.collectAsState()
    val paused by Trail.paused.collectAsState()
    val note by Trail.note.collectAsState()
    val net by Net.line.collectAsState()
    val kept by ArkodPrefetch.state.collectAsState()
    val download by MapDownload.live.collectAsState()
    val recording = recordingSince != null
    val justFinished by Trail.justFinished.collectAsState()
    var showTracks by remember { mutableStateOf(false) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // WHAT THE THREE MAPS NEED, read again whenever a download or a key changes something.
    val hasOffline = remember(UiTick.n, download?.running) { store.hasOfflineMap }
    val keyring = remember(UiTick.n, settings) { store.keyring }
    val googleUsable = keyring.any { it.verdict != Keyring.Verdict.REFUSED }

    // THE CADASTRE (27.9.2026) and MY PARCELS (29.9.2026). The parcels are always on the map; a
    // tap on one opens its record; the ones he keeps are his, each in its colour and line.
    var marks by remember { mutableStateOf(store.parcelMarks) }
    var parcelColour by remember { mutableLongStateOf(store.parcelColour) }
    var parcelStyle by remember { mutableStateOf(store.parcelStyle) }
    var card by remember { mutableStateOf<ParcelCard?>(null) }
    var myParcels by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    fun setMarks(next: List<Parcels.Mark>) {
        marks = next
        store.parcelMarks = next
        ParcelsShown.marks = next
        Canvases.refreshParcels()
    }

    // THE SHAPES COME LATER (27.9.2026). Only the state's slow WFS has them, so a parcel is kept
    // at once with its number and colour, and its outline is fetched behind it; whatever is still
    // missing is asked again when the app opens and when my parcels are opened.
    var fetchingShapes by remember { mutableStateOf(false) }

    suspend fun fillShapes() {
        if (fetchingShapes) return
        val missing = Parcels.shapeless(marks)
        if (missing.isEmpty()) return
        fetchingShapes = true
        var found: List<Parcels.Parcel>? = null
        for (attempt in 1..3) {
            found = runCatching { ParcelNet.shapes(missing.map { it.reference }) }.getOrNull()
            if (!found.isNullOrEmpty()) break
            delay(5_000)
        }
        fetchingShapes = false
        if (found.isNullOrEmpty()) {
            Trail.say("Obris čestice ${missing.joinToString(", ") { it.number }} još nije stigao; pitat ću ponovno")
            return
        }
        setMarks(Parcels.withShapes(marks, found))
    }

    suspend fun openCard(parcel: Parcels.Parcel) {
        card = ParcelCard(parcel)
        val answer = runCatching { ParcelNet.record(parcel.id) }
        val keptSince = ParcelNet.kept
        if (card?.parcel?.id != parcel.id) return
        card = ParcelCard(
            parcel,
            record = answer.getOrNull(),
            problem = answer.exceptionOrNull()?.let { "vlasnici se nisu mogli pročitati: ${it.message ?: it.javaClass.simpleName}" },
            keptSince = keptSince,
        )
        // THE OWNER SHEET (29.9.2026): the folios the cadastre links to, else the one he found by
        // hand for this parcel before; asked after the record, so the first two tabs never wait.
        val record = answer.getOrNull() ?: return
        val folios = runCatching {
            ParcelNet.ownerSheets(record).ifEmpty {
                store.folioLinks[parcel.reference]?.let { (book, unit) -> listOfNotNull(ParcelNet.folio(book, unit)) }.orEmpty()
            }
        }
        if (card?.parcel?.id != parcel.id) return
        card = card?.copy(
            folios = folios.getOrNull() ?: emptyList(),
            folioProblem = folios.exceptionOrNull()?.let { "zemljišna knjiga se nije mogla pročitati: ${it.message ?: it.javaClass.simpleName}" },
        )
    }

    /** He typed a land-book parcel number or a folio number where the state has no link. */
    suspend fun findFolio(parcel: Parcels.Parcel, municipality: String, number: String, isFolio: Boolean) {
        card = card?.copy(folios = null, folioProblem = null)
        val answer = runCatching { ParcelNet.findOwnerSheets(municipality, number, isFolio) }
        if (card?.parcel?.id != parcel.id) return
        val found = answer.getOrNull().orEmpty()
        card = card?.copy(
            folios = found,
            folioProblem = answer.exceptionOrNull()?.let { "zemljišna knjiga se nije mogla pročitati: ${it.message ?: it.javaClass.simpleName}" }
                ?: if (found.isEmpty()) "zemljišna knjiga $municipality nema ${if (isFolio) "uložak" else "česticu"} $number" else null,
        )
        found.firstOrNull()?.let { f -> store.folioLinks = store.folioLinks + (parcel.reference to (f.bookId to f.unit)) }
    }

    /**
     * ONE TAP SELECTS, A TAP ON THE SELECTION OPENS THE SHEET (27.9.2026). A tap outlines one
     * parcel in cyan and forgets the one before; keeping a parcel is done on its sheet. One of my
     * parcels is selected from its own outline on the phone, so both taps work with no signal.
     * Since 29.9.2026 the middle of the screen is like anywhere else: there is no bare view.
     */
    var selected by remember { mutableStateOf<Parcels.Parcel?>(null) }

    fun select(parcel: Parcels.Parcel?) {
        selected = parcel
        ParcelsShown.selection = parcel?.takeIf { it.rings.isNotEmpty() }
            ?.let { Parcels.markOf(it, Parcels.SELECTION, Parcels.LineStyle.SOLID) }
        Canvases.refreshParcels()
    }

    /** A place found by a search: the map goes there and the cyan pin marks it. */
    fun showPlace(hit: Finding.Hit) {
        val lat = hit.lat ?: return
        val lon = hit.lon ?: return
        ParcelsShown.pin = lat to lon
        Canvases.refreshParcels()
        Canvases.goTo(lat, lon, 18)
    }

    /**
     * A parcel found by a search: its sheet at once, and the map to it when the state's slow map
     * service hands over its outline (half a minute, measured): selected in cyan, pinned.
     */
    suspend fun showFoundParcel(hit: Finding.Hit) {
        val ref = hit.ref ?: return
        val parcel = Parcels.Parcel(hit.id.toLongOrNull() ?: 0L, ref.substringAfter('-'), ref, null, emptyList())
        scope.launch { openCard(parcel) }
        Trail.say("tražim ${parcel.number} na karti (državna usluga treba oko pola minute)…")
        val shape = runCatching { ParcelNet.shapes(listOf(ref)) }.getOrNull()?.firstOrNull { it.reference == ref }
        if (shape == null || shape.rings.isEmpty()) {
            Trail.say("${parcel.number}: državna usluga karte nije odgovorila; list je otvoren")
            return
        }
        val found = parcel.copy(rings = shape.rings)
        select(found)
        val (lat, lon) = found.middle
        ParcelsShown.pin = lat to lon
        Canvases.refreshParcels()
        Canvases.goTo(lat, lon, 18)
        Trail.say("${parcel.number} je na karti")
    }

    /** One of my parcels, chosen from the list: the map goes to it and its sheet opens. */
    fun openMine(mark: Parcels.Mark) {
        val p = Parcels.Parcel(mark.id, mark.number, mark.reference, null, mark.rings)
        mark.middle?.let { (lat, lon) -> Canvases.goTo(lat, lon, 18) }
        myParcels = false
        if (mark.rings.isNotEmpty()) select(p)
        if (mark.id != 0L) scope.launch { openCard(p) }
    }

    suspend fun tapped(lat: Double, lon: Double) {
        val current = selected
        if (current != null && current.rings.any { Parcels.contains(it, lat, lon) }) {
            Trail.say(null)
            openCard(current)
            return
        }
        marks.firstOrNull { m -> m.rings.any { Parcels.contains(it, lat, lon) } }?.let { m ->
            card = null
            select(Parcels.Parcel(m.id, m.number, m.reference, null, m.rings))
            Trail.say("${m.number}${if (m.name.isNotBlank()) " · ${m.name}" else ""} · dodirnite ponovno za list")
            return
        }
        if (Canvases.currentZoom() < Parcels.TAP_ZOOM) {
            Trail.say("Približite kartu da odaberete česticu")
            return
        }
        val answer = runCatching { ParcelNet.at(lat, lon) }
        val parcel = answer.getOrNull()
        if (parcel == null) {
            Trail.say(if (answer.isFailure) "Katastar nije odgovorio: ${answer.exceptionOrNull()?.message}" else "Tu nema čestice")
            return
        }
        // The selection whose outline could not be traced is still the selection.
        if (current != null && current.reference == parcel.reference) {
            openCard(current)
            return
        }
        card = null
        val rings = runCatching { ParcelNet.outline(lat, lon) }.getOrNull()
        select(parcel.copy(rings = rings.orEmpty()))
        Trail.say("${parcel.number} · dodirnite ponovno za list")
    }

    /** One of the three maps, chosen by its key. The choice is remembered for the next run. */
    fun choose(picked: MapLayer) {
        layer = picked
        store.layerId = picked.id
        scope.launch { showLayer(store, picked) }
    }

    LaunchedEffect(Unit) {
        ParcelsShown.on = true
        ParcelsShown.marks = store.parcelMarks
        ParcelsShown.onTap = { lat, lon -> scope.launch { tapped(lat, lon) } }
        fillShapes()
    }

    LaunchedEffect(myParcels) { if (myParcels) fillShapes() }

    // The ink follows the ground: dark on the pale maps, sand on the photographs and the night theme.
    LaunchedEffect(ready, layer.id, settings, UiTick.n) {
        ParcelsShown.ink = Parcels.inkFor(layer.id, store.themeName, layer.googleView?.mapType)
        Canvases.refreshParcels()
    }

    // The map is drawn as soon as the view is real, and again when a download or a key arrives.
    LaunchedEffect(ready, layer.id, hasOffline, googleUsable) {
        if (ready > 0) {
            showLayer(store, layer)
            Shown.track?.let { Canvases.drawSavedTrack(it.first, it.second) }
        }
    }

    // HIS SURROUNDINGS KEPT (29.9.2026): each new fix may start the background fetch round him.
    LaunchedEffect(fix?.timeMs) {
        val f = fix ?: return@LaunchedEffect
        if (store.prefetch) ArkodPrefetch.positionKnown(f.lat, f.lon)
    }

    // The network line: sampled once a second. Bounded by the composition.
    LaunchedEffect(Unit) {
        while (true) {
            Net.sample(System.currentTimeMillis())
            delay(1_000)
        }
    }

    // THE ZOOM, AND WHERE THE MAP HAS SETTLED, twice a second. A place where the map rests is
    // where the cadastre is fetched ahead (29.9.2026). Bounded by the composition.
    LaunchedEffect(Unit) {
        var restingAt: Pair<Double, Double>? = null
        var restingFor = 0
        while (true) {
            val now = Canvases.currentZoom()
            if (now != zoom && now > 0) zoom = now
            val here = Canvases.centre()
            if (here != null && here == restingAt) {
                restingFor += 1
                if (restingFor == 2 && store.prefetch) ArkodPrefetch.viewSettled(here.first, here.second, now)
            } else {
                restingAt = here
                restingFor = 0
            }
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Paint.Ground)) {

        MapSurface(
            store = store,
            generation = ready,
            fix = fix,
            line = Trail.line.collectAsState().value,
            follow = follow,
            onCanvas = onCanvas,
            onReady = { ready += 1 },
        )

        // THE LITTLE COMPASS, at the top right where Google keeps it. A tap puts north up.
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 52.dp, end = 10.dp)) {
            LittleCompass(
                turn = mapTurn,
                modifier = Modifier.align(Alignment.TopEnd),
                onTap = { Canvases.setMapRotation(0f) },
            )
        }

        // THE CROSSHAIR, drawn and nothing more: it takes no touch, so a tap in the middle of the
        // map reaches the map and picks the parcel there, like a tap anywhere else (29.9.2026).
        Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
            CentreCross()
        }

        // WHAT A MAP NEEDS BEFORE IT CAN DRAW, where the map would be (29.9.2026).
        when {
            layer.kind == LayerKind.VECTOR_FILE && !hasOffline -> OfflineOffer(
                live = download,
                partMb = remember(UiTick.n, download?.running) { MapDownload.partBytes(appContext) / 1_000_000 },
                onFetch = onFetchOfflineMap,
                onChooseFile = onChooseMapFile,
            )
            layer.family == MapLayer.Family.GOOGLE && !googleUsable -> GoogleKeyHelp(
                keyring = keyring,
                onPaste = onPasteKey,
                onImport = onImportKeys,
                onTest = onTestKey,
            )
        }

        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).safeDrawingPadding()) {
            FixLine(fix, zoom, layer)
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeDrawingPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // A line with nothing in it takes no room at all.
            val moving = listOfNotNull(net, kept).joinToString(" · ").ifBlank { null }
            if (moving != null) StatusLine(moving)
            // The download goes on while he looks at another map; its line stays with him.
            if (download?.running == true && layer.kind != LayerKind.VECTOR_FILE) {
                StatusLine("offline karta: " + (download?.progress?.let { MapDownload.line(it) } ?: "počinje…"))
            }
            if (note != null) NoteLine(note)
            if (recording) TrackLine(stats, recording = !paused)
            // NINE KEYS (29.9.2026): zoom at both ends where either thumb reaches it; where-am-I
            // and the record circle; the three maps side by side, the one that is up lit; my
            // parcels; the settings.
            Row(
                Modifier.fillMaxWidth().background(Paint.Ground.copy(alpha = 0.86f)).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Key(glyph = "−", lit = false, onClick = { Canvases.zoomOut() }, icon = R.drawable.ic_minus)
                MarkKey(
                    lit = follow,
                    onClick = {
                        val now = System.currentTimeMillis()
                        when {
                            follow -> follow = false
                            now - lastCentreTap < 1_000L -> follow = true
                            else -> onWhereAmI()
                        }
                        lastCentreTap = now
                    },
                ) { hasFix -> PositionMark(hasFix, locked = follow) }
                RecordKey(
                    recording = recording,
                    paused = paused,
                    metres = stats.distanceM,
                    onPress = onRecord,
                )
                MapKey(
                    word = "OFF",
                    icon = R.drawable.ic_mountain,
                    up = layer.family == MapLayer.Family.OFFLINE,
                    onClick = { choose(Layers.OFFLINE) },
                )
                MapKey(
                    word = "GOO",
                    icon = R.drawable.ic_satellite,
                    up = layer.family == MapLayer.Family.GOOGLE,
                    onClick = { choose(Layers.byId(store.googleViewId)) },
                )
                MapKey(
                    word = "OSM",
                    icon = R.drawable.ic_globe,
                    up = layer.family == MapLayer.Family.OSM,
                    onClick = { choose(Layers.OSM) },
                )
                // MY PARCELS, ONE PRESS AWAY (29.9.2026): *"there should be somehow quick
                // navigation to my parcels ... inside this app."* Lit while he has any.
                Key(glyph = "★", lit = marks.isNotEmpty(), onClick = { myParcels = true }, icon = R.drawable.ic_parcels)
                Key("⚙", lit = false, onClick = { settings = true }, icon = R.drawable.ic_settings)
                Key(glyph = "+", lit = false, onClick = { Canvases.zoomIn() }, icon = R.drawable.ic_plus)
            }
        }

        // THE SHEET, OVER EVERYTHING (27.9.2026): the whole screen, his word.
        card?.let { shown ->
            val mark = marks.firstOrNull { it.reference == shown.parcel.reference }
            ParcelCardView(
                card = shown,
                mine = mark,
                onClose = { card = null },
                onCopy = {
                    val stamp = java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale.ROOT)
                        .format(java.util.Date())
                    val parcel = shown.parcel.copy(rings = mark?.rings ?: shown.parcel.rings)
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(Parcels.toText(parcel, shown.record, stamp, shown.folios.orEmpty())))
                    Trail.say("čestica ${parcel.number} kopirana")
                },
                onText = {
                    val stamp = java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale.ROOT)
                        .format(java.util.Date())
                    val parcel = shown.parcel.copy(rings = mark?.rings ?: shown.parcel.rings)
                    val text = Parcels.toText(parcel, shown.record, stamp, shown.folios.orEmpty())
                    scope.launch {
                        val (_, said) = withContext(Dispatchers.IO) {
                            Folder.saveText(appContext, store, Parcels.textFileName(parcel), text)
                        }
                        Trail.say(said)
                    }
                },
                onKeep = { keep ->
                    if (!keep) {
                        setMarks(Parcels.without(marks, shown.parcel.reference))
                        Trail.say("${shown.parcel.number} više nije među mojim česticama")
                    } else {
                        // A parcel whose outline is already on the phone keeps it.
                        val made = Parcels.markOf(shown.parcel, parcelColour, parcelStyle)
                        setMarks(Parcels.withMark(marks, if (made.rings.isEmpty() && mark != null) made.copy(rings = mark.rings) else made))
                        Trail.say("${shown.parcel.number} je među mojim česticama")
                        scope.launch { fillShapes() }
                    }
                },
                onColour = { colour ->
                    parcelColour = colour
                    store.parcelColour = colour
                    if (mark != null) setMarks(Parcels.withMark(marks, mark.copy(colour = colour)))
                },
                onStyle = { style ->
                    parcelStyle = style
                    store.parcelStyle = style
                    if (mark != null) setMarks(Parcels.withMark(marks, mark.copy(style = style)))
                },
                colour = mark?.colour ?: parcelColour,
                style = mark?.style ?: parcelStyle,
                onFindFolio = { number, isFolio ->
                    val ko = shown.record?.municipality.orEmpty()
                    scope.launch { findFolio(shown.parcel, ko, number, isFolio) }
                },
                onForgetFolio = {
                    store.folioLinks = store.folioLinks - shown.parcel.reference
                    card = card?.copy(folios = emptyList(), folioProblem = null)
                },
            )
        }

        if (myParcels) {
            MyParcelsFace(
                store = store,
                marks = marks,
                onParcel = { hit -> myParcels = false; scope.launch { showFoundParcel(hit) } },
                onPlace = { hit -> myParcels = false; showPlace(hit) },
                onOpen = { mark -> openMine(mark) },
                onRename = { mark, name -> setMarks(Parcels.withMark(marks, mark.copy(name = name))) },
                onRemove = { mark -> setMarks(Parcels.without(marks, mark.reference)) },
                onClearAll = { setMarks(emptyList()) },
                onClose = { myParcels = false },
            )
        }

        justFinished?.let { file ->
            NameBox(
                current = Tracks.displayName(file.name),
                // CANCEL MEANS CANCELLED (17.9.2026): the walk is thrown away.
                onCancel = {
                    Trail.dealtWith()
                    onDiscardRecording(file)
                },
                onOk = { name ->
                    Trail.dealtWith()
                    onRenameJustFinished(file, name)
                },
            )
        }

        // THE LIGHT IN FRONT OF THE DOT NEEDS THE HEADING, five times a second (bounded by the
        // composition), and the compass reports the map's own turn.
        LaunchedEffect(ready) {
            while (true) {
                if (ready > 0) {
                    Canvases.setHeading(sensors.heading())
                    mapTurn = Canvases.mapRotationDeg()
                }
                delay(200)
            }
        }

        if (compass != COMPASS_OFF) {
            CompassOverlay(
                sensors = sensors,
                night = Parcels.inkFor(layer.id, store.themeName, layer.googleView?.mapType) == Parcels.INK_LIGHT,
            )
        }

        if (showTracks) {
            // THE LIST IS LOADED, NOT COMPUTED, off the main thread (15.9.2026).
            var loaded by remember { mutableStateOf<List<Folder.Entry>>(emptyList()) }
            var colour by remember { mutableStateOf(store.trackColour) }
            LaunchedEffect(UiTick.n, showTracks) {
                loaded = withContext(Dispatchers.IO) { tracks() }
            }
            TracksFace(
                tracks = loaded,
                folder = folderLabel,
                onChooseFolder = onChooseExportFolder,
                colour = colour,
                onColour = {
                    colour = it
                    store.trackColour = it
                },
                onShow = { entry ->
                    showTracks = false
                    onShowTrack(entry)
                },
                onRename = onRenameTrack,
                onHide = {
                    Canvases.clearSavedTrack()
                    Trail.say(null)
                },
                onDelete = onDeleteTrack,
                onClose = { showTracks = false },
            )
        }

        if (settings) {
            SettingsFace(
                store = store,
                current = layer,
                version = version,
                myParcelCount = marks.size,
                onMyParcels = {
                    settings = false
                    myParcels = true
                },
                compassOn = compass != COMPASS_OFF,
                onCompass = {
                    compass = if (compass == COMPASS_OFF) COMPASS_DARK else COMPASS_OFF
                    store.compassMode = compass
                },
                hasOffline = hasOffline,
                download = download,
                onFetchOfflineMap = onFetchOfflineMap,
                onChooseMapFile = onChooseMapFile,
                onOfflineView = { view ->
                    store.themeName = view.theme
                    CanvasHolder.canvas?.setTheme(view.theme)
                    UiTick.bump()
                },
                onGoogleView = { picked ->
                    store.googleViewId = picked.id
                    settings = false
                    choose(picked)
                },
                keyring = keyring,
                onTestKey = onTestKey,
                onRemoveKey = onRemoveKey,
                onImportKeys = onImportKeys,
                onPasteKey = onPasteKey,
                onTestTiles = onTestTiles,
                onPause = onPause,
                recordingPaused = paused,
                recording = recording,
                onTracks = {
                    settings = false
                    showTracks = true
                },
                trackCount = remember(UiTick.n, settings) { tracks().size },
                onChooseExportFolder = onChooseExportFolder,
                folderName = store.exportFolderName ?: "još nije odabrana",
                onClose = { settings = false },
            )
        }
    }
}

@Composable
private fun MapSurface(
    store: Store,
    generation: Int,
    fix: Fix?,
    line: List<Fix>,
    follow: Boolean,
    onCanvas: (VtmCanvas) -> Unit,
    onReady: () -> Unit,
) {
    // Whatever is laid over the map is handed to every canvas that appears, every time the line
    // grows, a fix arrives or the lock changes (18.9.2026).
    LaunchedEffect(generation, line.size, fix?.timeMs, follow) {
        Canvases.handOver(line, fix, follow)
    }

    // ONE ENGINE FOR ALL THREE MAPS (29.9.2026): VTM draws the file, OpenStreetMap's tiles and
    // Google's tiles, with the cadastre over each, so a change of map is a change of layer.
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val made = VtmCanvas(context, store)
            CanvasHolder.canvas = made
            onCanvas(made)
            // THE VIEW EXISTS NOW, AND NOT BEFORE (the whole bug of v6 to v10 in Mantra Trail):
            // the map is told to draw itself from here, where the view is real.
            onReady()
            made.view
        },
        onRelease = {
            CanvasHolder.canvas?.remember()
            CanvasHolder.canvas?.pause()
            CanvasHolder.canvas = null
        },
    )
}

/**
 * PUT A LAYER ON THE MAP, fetching whatever it needs first. A map that cannot draw yet (no file,
 * no key) draws nothing, and the middle of the screen says what it needs; any other failure is
 * said on the note line.
 */
suspend fun showLayer(store: Store, layer: MapLayer) {
    val canvas = CanvasHolder.canvas
    if (canvas == null) {
        // Never silent (silent-failure.md): a blank screen must say why.
        Trail.say("Karta još nije spremna")
        return
    }
    if (layer.kind == LayerKind.VECTOR_FILE && !store.hasOfflineMap) {
        canvas.blank()
        Trail.say(null)
        return
    }
    if (layer.kind == LayerKind.GOOGLE_TILES && store.keyring.isEmpty()) {
        canvas.blank()
        Trail.say(null)
        return
    }
    val problem = attempt(canvas, store, layer)
    if (problem == null) {
        Trail.say(if (layer.kind == LayerKind.VECTOR_FILE) canvas.emptyHere() else null)
        return
    }
    // A MAP THAT CANNOT BE DRAWN DRAWS NOTHING (17.9.2026): never another map under its name.
    CanvasHolder.canvas?.blank()
    Trail.say(problem)
}

/** One attempt at one layer. Returns null when it drew, or the reason it did not. */
private suspend fun attempt(canvas: VtmCanvas, store: Store, layer: MapLayer): String? {
    if (layer.kind == LayerKind.GOOGLE_TILES) {
        // THE RING, IN ORDER (17.9.2026): the key that worked last is tried first.
        if (store.keyring.isEmpty()) return Layers.missingKey(layer)
        val view = layer.googleView ?: MapLayer.GoogleView.SATELLITE
        Trail.say("Tražim sesiju od Googlea…")
        val result = GoogleTiles.sessionFromRing(view, store)
        UiTick.bump()
        val used = Keyring.best(store.keyring)?.value
        return result.token?.let { canvas.show(layer, session = it, key = used) } ?: result.problem
    }
    return canvas.show(layer)
}

/**
 * A KEY FOR ONE OF THE THREE MAPS (29.9.2026): its picture, and its three letters under it so the
 * three are told apart at a glance; lit amber while it is the map on the screen.
 */
@Composable
private fun RowScope.MapKey(word: String, @androidx.annotation.DrawableRes icon: Int, up: Boolean, onClick: () -> Unit) {
    val ink = if (up) Paint.AmberBright else Paint.Sand
    Column(
        Modifier
            .weight(1f)
            .height(KEY)
            .clip(RoundedCornerShape(10.dp))
            .background(if (up) Paint.Amber.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(icon, ink, size = 22.dp)
        Text(word, color = ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

/**
 * THE OFFLINE MAP, OFFERED WHERE IT WOULD BE (29.9.2026): *"when user click ... for the first time
 * in Croatian offline map, the user interface will just in the middle of the screen, because map is
 * not available, offer the link download offline map ... And while it's downloading, in the middle
 * of the screen it shows the speed and status, how much percentage is done. And once this map is
 * cached, there is no more need for this part."*
 */
@Composable
private fun OfflineOffer(
    live: MapDownload.Live?,
    partMb: Long,
    onFetch: () -> Unit,
    onChooseFile: () -> Unit,
) {
    val total = Layers.OfflineDownload.BYTES / 1_000_000
    CentrePanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_mountain, Paint.Sand)
            Spacer(Modifier.width(10.dp))
            Label("Offline karta Hrvatske", Paint.Sand, size = 17, align = TextAlign.Start)
        }
        val progress = live?.progress
        if (live?.running == true) {
            val percent = progress?.percent ?: 0
            Label("$percent %", Paint.AmberBright, size = 40)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Paint.Card)) {
                Box(Modifier.fillMaxWidth(percent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Paint.Amber))
            }
            Label(
                progress?.let { "${it.done / 1_000_000} od ${it.total / 1_000_000} MB · ${Geo.formatRate(it.bytesPerSecond)}" } ?: "povezujem se…",
                Paint.Sand, size = 14,
            )
            Label("Preuzimanje se nastavlja i dok gledate drugu kartu.", Paint.Dim, size = 12, lines = 3)
        } else {
            Label(
                "Karta cijele Hrvatske za rad bez signala: ceste, mjesta, staze. Preuzima se jednom " +
                    "i ostaje na telefonu. Najbolje preko Wi-Fi mreže.",
                Paint.Sand, size = 13, align = TextAlign.Start, lines = 6,
            )
            live?.problem?.let { Label(it, Paint.Red, size = 13, align = TextAlign.Start, lines = 4) }
            Action(
                verb = if (partMb > 0) "Nastavi preuzimanje" else "Preuzmi offline kartu",
                icon = R.drawable.ic_save,
                onClick = onFetch,
                trailing = if (partMb > 0) "$partMb od $total MB" else "$total MB",
                modifier = Modifier.fillMaxWidth(),
            )
            Action("Imam .map datoteku", R.drawable.ic_folder, onClick = onChooseFile, quiet = true, modifier = Modifier.fillMaxWidth())
        }
        Label("© OpenStreetMap contributors · mapsforge.org", Paint.Dim, size = 10)
    }
}

/**
 * GOOGLE'S MAP NEEDS HIS OWN KEY, AND THE WAY TO MAKE ONE IS ON THE SCREEN (29.9.2026): *"user
 * first time will open the Google Map, it will not be available because there is no API key. So
 * on the screen there will be help text ... the whole workflow how to get your own key."* No key
 * is built into this app, so every step is his, and each step is a link to the page it happens on.
 */
@Composable
private fun GoogleKeyHelp(
    keyring: List<Keyring.Key>,
    onPaste: (String) -> Unit,
    onImport: () -> Unit,
    onTest: (Keyring.Key) -> Unit,
) {
    val open = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val caller = remember { AndroidCaller.headers(context) }
    var text by remember { mutableStateOf("") }
    CentrePanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_key, Paint.Sand)
            Spacer(Modifier.width(10.dp))
            Label("Google karta treba API ključ", Paint.Sand, size = 16, align = TextAlign.Start, modifier = Modifier.weight(1f))
        }
        keyring.forEach { key ->
            // A key he gave that Google refused: Google's own words, and a way to try again.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("${key.label}: ${key.said.ifBlank { Keyring.describe(key) }}", Paint.Red, size = 12, align = TextAlign.Start, lines = 5, modifier = Modifier.weight(1f))
                IconAction(R.drawable.ic_play, "ponovno", onClick = { onTest(key) })
            }
        }
        Label(
            "Ključ nije ugrađen u aplikaciju: svatko koristi svoj. Google daje besplatnu mjesečnu " +
                "kvotu koja je dovoljna za osobnu upotrebu. Koraci:",
            Paint.Sand, size = 13, align = TextAlign.Start, lines = 5,
        )
        GOOGLE_STEPS.forEachIndexed { i, (words, link) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (link != null) Modifier.clickable { runCatching { open.openUri(link) } } else Modifier)
                    .padding(vertical = 4.dp),
            ) {
                Label("${i + 1}.", Paint.Amber, size = 13, modifier = Modifier.width(26.dp), align = TextAlign.Start)
                Column(Modifier.weight(1f)) {
                    Label(words, Paint.Sand, size = 13, align = TextAlign.Start, lines = 6)
                    if (link != null) Label(link.removePrefix("https://"), Paint.AmberBright, size = 11, align = TextAlign.Start, lines = 2)
                }
            }
        }
        if (caller.isNotEmpty()) {
            Label(
                "Ograničenje po želji: Android aplikacije, paket ${caller["X-Android-Package"]}, " +
                    "SHA-1 ${caller["X-Android-Cert"]?.chunked(2)?.joinToString(":")}",
                Paint.Dim, size = 11, align = TextAlign.Start, lines = 5,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Paint.Card).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Label("zalijepite ključ (AIza…)", Paint.Dim, size = 14, align = TextAlign.Start)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    singleLine = true,
                    textStyle = TextStyle(color = Paint.Sand, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Paint.AmberBright),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Action(
            "Dodaj ključ", R.drawable.ic_check,
            onClick = {
                onPaste(text)
                text = ""
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Action("Ključ iz datoteke", R.drawable.ic_folder, onClick = onImport, quiet = true, modifier = Modifier.fillMaxWidth())
    }
}

/** The way to a Google key, a step a line, each with the page it happens on. */
private val GOOGLE_STEPS: List<Pair<String, String?>> = listOf(
    "Otvorite Google Cloud Console i prijavite se svojim Google računom." to "https://console.cloud.google.com/",
    "Napravite novi projekt (gore lijevo: odabir projekta → Novi projekt), npr. \"mantra-arkod\"." to "https://console.cloud.google.com/projectcreate",
    "Uključite naplatu (Billing) za taj projekt. Google traži karticu, ali do mjesečne besplatne kvote ništa ne naplaćuje." to "https://console.cloud.google.com/billing",
    "U tom projektu uključite \"Map Tiles API\" (gumb Enable)." to "https://console.cloud.google.com/apis/library/tile.googleapis.com",
    "Po želji uključite i \"Places API (New)\" za traženje ulica i kućnih brojeva." to "https://console.cloud.google.com/apis/library/places.googleapis.com",
    "APIs & Services → Credentials → Create credentials → API key. Kopirajte ključ." to "https://console.cloud.google.com/apis/credentials",
    "Zalijepite ključ ovdje i pritisnite Dodaj ključ. Aplikacija ga odmah provjerava." to null,
)

/**
 * A PANEL IN THE MIDDLE OF THE MAP, above the key row and under the top line, that scrolls when it
 * is taller than the room and takes every touch on itself so nothing reaches the map beneath.
 */
@Composable
private fun CentrePanel(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().padding(start = 14.dp, end = 14.dp, top = 40.dp, bottom = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Paint.Ground.copy(alpha = 0.94f))
                .border(1.dp, Look.Outline, RoundedCornerShape(16.dp))
                .swallowTouches()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * A KEY THAT IS A MARK RATHER THAN A GLYPH.
 *
 * Baba, 14.9.2026: the where key should look like the thing in the middle of the map, and both it
 * and the record circle should be smaller. So they are: the same ring and dot that marks the
 * centre, at the same size as the red circle beside it, on nothing.
 */
@Composable
private fun RowScope.MarkKey(
    lit: Boolean = false,
    onClick: () -> Unit,
    mark: @Composable (Boolean) -> Unit,
) {
    val hasFix = Trail.fix.collectAsState().value != null
    Box(
        Modifier
            .weight(1f)
            .height(KEY)
            .clip(RoundedCornerShape(10.dp))
            .background(if (lit) Paint.Amber.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        mark(hasFix)
    }
}

/** One place the composable side can reach the view it made. */
object CanvasHolder {
    var canvas: VtmCanvas? = null
}

/**
 * THE MARK IN THE MIDDLE. A ring the width of a fingernail and a dot in it, at half strength.
 * It says where the centre of the screen is, and it is also the target that takes the interface
 * away. It is not a sight and it is not the person's position: the position is the amber dot on
 * the map, drawn where the phone actually is.
 */
/**
 * THE MARK IN THE MIDDLE, IN THE COLOUR OF THE POSITION ON THE MAP.
 *
 * Baba, 15.9.2026: *"the middle center I don't see at all. It needs to have different color, same
 * as for the center current position on the map."* It was sand on a street map, which is sand on
 * sand. It is amber now, and every stroke is laid down twice: near-black underneath, a little
 * wider, then the amber on top. That black edge is what makes it readable on snow and under
 * fir, and no single colour does that on its own.
 */

/**
 * THE LITTLE COMPASS. A needle in a dark disc: red half to the north, pale half the other way,
 * turned by however much the map has been turned. When it is following the walk it gains an amber
 * ring, so the two states are a colour and not a word.
 *
 * It is 44dp, which is a thumb, and it sits under the top bar at the right-hand edge.
 */
/**
 * THE COMPASS (17.9.2026, third attempt, to his description and no further).
 *
 * One white ring. One needle, wholly inside it, red to the north and pale to the south. Nothing
 * else: no letter, no disc, no second stroke under anything.
 *
 * What was wrong before, in his words and mine: the needle reached past the ring; the ring and
 * the needle were each drawn twice, near-black under colour, and the two passes did not meet
 * cleanly at the point — which is the "double sword" on the edges; and the N carried a shadow
 * because it too was drawn twice. Drawing a thing twice to make it readable is a trick for a
 * hairline over a map, and a filled needle is not a hairline. It needs none of it.
 *
 * The needle ends at 0.58 of the radius, so there is air between its point and the ring.
 */
@Composable
private fun LittleCompass(turn: Float, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(55.dp).clip(CircleShape).clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        // A QUARTER LARGER (17.9.2026), with a black ring OUTSIDE the white one — concentric, not
        // one drawn under the other, so there is no double edge anywhere on it.
        Canvas(Modifier.size(38.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 1.dp.toPx()

            // ONE LINE, ONE PIXEL, NO OUTLINE (17.9.2026, his rule three). The black ring around
            // this was me outlining by reflex; it is gone, and so is every other outline I added.
            drawCircle(Color.White, radius = r, center = c, style = Stroke(1.dp.toPx()))

            // The needle turns against the map: the map turned east puts north to the left.
            val along = Math.toRadians(-turn.toDouble() - 90.0)
            val across = Math.toRadians(-turn.toDouble())
            fun at(distance: Float, radians: Double) = Offset(
                c.x + (distance * Math.cos(radians)).toFloat(),
                c.y + (distance * Math.sin(radians)).toFloat(),
            )
            val reach = r * 0.58f
            val waist = r * 0.13f
            val tip = at(reach, along)
            val tail = at(-reach, along)
            val left = at(waist, across)
            val right = at(-waist, across)

            fun half(point: Offset, colour: Color) {
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(point.x, point.y)
                        lineTo(left.x, left.y)
                        lineTo(right.x, right.y)
                        close()
                    },
                    colour,
                )
            }
            half(tip, Color(0xFFE53935))
            half(tail, Color.White)
        }
    }
}

/**
 * THE CROSSHAIR IN THE MIDDLE OF THE MAP: four black hairlines, half transparent, and nothing in
 * the middle. Baba, 15.9.2026, and it is the whole specification.
 *
 * Half-transparent black rather than a colour because it has to sit over every map this app can
 * show — a white street, a satellite photograph, a dark forest — and a shadow of the ground is
 * readable on all of them without being a mark anybody looks at. The middle is empty because the
 * middle is the thing being pointed at.
 */
@Composable
private fun CentreCross() {
    Canvas(Modifier.size(CROSS)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val arm = size.minDimension / 2f
        val gap = arm * 0.36f
        // ONE HAIRLINE. A hairline is one pixel and nothing under it (17.9.2026, his rule three).
        val hair = 1.dp.toPx()
        val ink = Color(0x99000000)
        drawLine(ink, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), hair)
        drawLine(ink, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), hair)
        drawLine(ink, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), hair)
        drawLine(ink, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), hair)
    }
}

/**
 * THE MARK ON THE KEY, WHICH DOES NOT CHANGE (15.9.2026: "keep the icon on the action bar same as
 * before"). It is the ring and dot in the position colour, ringed in near-black so it reads on
 * any map, and its centre fills while the map is locked to the middle.
 */
@Composable
private fun PositionMark(hasFix: Boolean, locked: Boolean = false) {
    val ink = if (hasFix) Paint.AmberBright else Paint.Amber
    Canvas(Modifier.size(MARK)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2f
        drawCircle(Paint.Ground, radius = r, center = c, style = Stroke(3.5.dp.toPx()))
        drawCircle(ink, radius = r, center = c, style = Stroke(2.dp.toPx()))
        drawCircle(Paint.Ground, radius = 2.4.dp.toPx(), center = c)
        drawCircle(ink, radius = if (locked) 2.0.dp.toPx() else 1.6.dp.toPx(), center = c)
        if (locked) drawCircle(ink, radius = r * 0.55f, center = c, style = Stroke(1.dp.toPx()))
    }
}

/** One line of numbers, and only the ones that decide something. */
@Composable
private fun FixLine(fix: Fix?, zoom: Int, layer: MapLayer) {
    Panel {
        Row(
            Modifier.fillMaxWidth().background(Paint.Bar).padding(horizontal = GAP, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ONE LINE, AND THE MAP'S NAME IS ON IT (15.9.2026). Everything here went to 11sp to
            // make the room rather than anything being dropped: about 55 monospace characters fit
            // across a 390 px phone, and this is fifty. The name carries no family — the key just
            // below says THU or GOO — so "Thunderforest Landscape" reads "Landscape" here.
            Label(fix?.let { Geo.formatLat(it.lat) } ?: "N -- --.---", ink(fix != null), size = 11)
            Label(fix?.let { Geo.formatLon(it.lon) } ?: "E -- --.---", ink(fix != null), size = 11)
            Label(fix?.accuracyM?.let { "±${it.toInt()}m" } ?: "±-", accuracyInk(fix?.accuracyM), size = 11)
            // Speed, because a walking pace is the one number that says whether the fix is
            // moving with him or wandering on its own (15.9.2026).
            Label(Geo.formatSpeed(fix?.speedMs), ink(fix?.speedMs != null), size = 11)
            Label(fix?.ele?.let { "${it.toInt()}m" } ?: "-m", ink(fix?.ele != null), size = 11)
            Label("z$zoom", Paint.Dim, size = 11)
            Label(layer.name, Paint.Amber, size = 11)
        }
    }
}

private fun accuracyInk(metres: Float?): Color = when {
    metres == null -> Paint.Dim
    metres <= 10f -> Paint.Sand
    metres <= TrackRules.MAX_ACCURACY_M -> Paint.Amber
    else -> Paint.Red
}

private fun ink(active: Boolean): Color = if (active) Paint.Sand else Paint.Dim

/**
 * THE WALK, AND WHAT COLOUR IT IS IN (18.9.2026).
 *
 * Red while it is recording, sand when it is paused. His reasoning, and it is right: these are the
 * numbers that say the phone is writing a file, and a colour says that at arm's length where four
 * words do not. Colour is the only state channel in this app (design-language.md), and recording
 * is exactly the state worth one.
 *
 * Idle it is empty space, not a row of zeros.
 */
@Composable
private fun TrackLine(stats: TrackStats, recording: Boolean) {
    val ink = if (recording) Paint.Red else Paint.Sand
    Panel {
        Row(
            Modifier.fillMaxWidth().background(Paint.Bar).padding(horizontal = GAP, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label(Geo.formatDistance(stats.distanceM), ink, size = 13)
            Label(Geo.formatDuration(stats.durationMs), ink, size = 13)
            Label("↑ ${stats.ascentM.toInt()}", ink, size = 13)
            Label("${stats.points} tč", ink, size = 13)
        }
    }
}

/** What the network is doing. Absent when nothing is moving and nothing is being waited for. */
@Composable
private fun StatusLine(status: String?) {
    Panel {
        Box(Modifier.fillMaxWidth().background(Paint.Bar).padding(horizontal = GAP, vertical = 2.dp)) {
            Label(status ?: " ", Paint.Sand, size = 11, align = TextAlign.Start)
        }
    }
}

@Composable
private fun NoteLine(note: String?) {
    Panel {
        Box(Modifier.fillMaxWidth().background(Paint.Bar).padding(horizontal = GAP, vertical = 2.dp)) {
            Label(note ?: " ", Paint.Amber, size = 12, align = TextAlign.Start)
        }
    }
}

/**
 * A key: a glyph on half-strength ground, no box, no label under it. Four of them and the record
 * button is the fifth, and none of them is bigger than a thumb needs.
 */
@Composable
private fun RowScope.Key(
    glyph: String,
    lit: Boolean,
    onClick: () -> Unit,
    // THE ICON (27.9.2026): the key row speaks in symbols; the glyph stays as its name.
    @androidx.annotation.DrawableRes icon: Int? = null,
) {
    Box(
        Modifier.weight(1f).height(KEY).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Glyph(icon, if (lit) Paint.AmberBright else Paint.Sand, size = 26.dp)
        else Label(glyph, if (lit) Paint.AmberBright else Paint.Sand, size = 17)
    }
}

/**
 * THE RECORD KEY IS A RED CIRCLE AND NOTHING ELSE. Baba, 14.9.2026: *"the record button can be
 * just one red circle without any background."*
 *
 * Hollow to start, filled while recording, a ring with a dot while paused — the face says what
 * the next press does (design-language.md 5). Pause is the rare one and lives in settings rather
 * than taking a key on a screen that is meant to be nearly empty.
 */
@Composable
private fun RowScope.RecordKey(
    recording: Boolean,
    paused: Boolean,
    metres: Double,
    onPress: () -> Unit,
) {
    Box(
        Modifier.weight(1f).height(KEY).clickable(onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(MARK)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 2f
            // The black edge first, then the red, for the same reason as the centre mark.
            drawCircle(Paint.Ground, radius = r, center = c, style = Stroke(4.5.dp.toPx()))
            when {
                paused -> {
                    drawCircle(Paint.Red, radius = r, center = c, style = Stroke(3.dp.toPx()))
                    drawCircle(Paint.Red, radius = r * 0.35f, center = c)
                }

                // FILLED, BUT NOT SOLID, WHILE THE NUMBER IS IN IT (18.9.2026). A filled circle
                // with dark digits over it reads at arm's length; a ring with digits inside does
                // not, because the ring and the numerals fight for the same few pixels.
                recording -> drawCircle(Paint.Red, radius = r, center = c)
                else -> drawCircle(Paint.Red, radius = r, center = c, style = Stroke(3.dp.toPx()))
            }
        }
        // TWO DIGITS, AND ONLY EVER TWO (18.9.2026, at his word).
        //
        // This is not a measurement — the distance is on the line at the bottom, whole and
        // honest. It is a heartbeat: the last two metres of it, turning over as he walks, so a
        // glance at the key says the phone is still writing. Two digits also stay inside a circle
        // at every value, where 1084 would spill out of it and 108 would be tight.
        if (recording || paused) {
            Label(
                text = "%02d".format(metres.toInt().coerceAtLeast(0) % 100),
                colour = if (paused) Paint.Red else Paint.Ground,
                size = 11,
            )
        }
    }
}


/**
 * THE NAME BOX: empty, OK, cancel, and it waits.
 *
 * Baba, 15.9.2026, rebuilding it from the ground up: *"no timeout anymore, it stays forever...
 * menu just have option OK or cancel, no other confusing text there."*
 *
 * The countdown is gone, and with it the whole apparatus of deciding whether somebody had started
 * typing. A box that waits needs no such apparatus. The field starts empty whatever the track is
 * called, because he is typing a new name, not correcting an old one; the name it has now is the
 * grey text behind, so cancel is never a guess about what it will be left as.
 */
@Composable
private fun NameBox(current: String, onCancel: () -> Unit, onOk: (String) -> Unit) {
    var text by remember(current) { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    // THE CURSOR IS IN THE BOX BEFORE HE TOUCHES IT. Without this the field is focused by a tap
    // he has to know to make, and an empty unfocused field on a dark panel is indistinguishable
    // from a label (15.9.2026).
    LaunchedEffect(current) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Paint.Veil).safeDrawingPadding().padding(GAP * 2),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Paint.Ground)
                .padding(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Label("sada: $current", Paint.Dim, size = 11, align = TextAlign.Start)
            Label("novo ime", Paint.Amber, size = 11, align = TextAlign.Start)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Paint.Sand,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                // The cursor is the bright amber and it blinks, which is Compose's own doing once
                // the field has focus. What was missing was the focus and the frame.
                cursorBrush = SolidColor(Paint.AmberBright),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Paint.Ground)
                    // A FRAME, so the box is a box. On a dark panel a dark field is a label.
                    .border(1.5.dp, Paint.Amber, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .focusRequester(focus),
            )
            // discard in red and quiet, OK the one solid button; his two words kept (27.9.2026, the visual language).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP), verticalAlignment = Alignment.CenterVertically) {
                Action("Odbaci", R.drawable.ic_trash, onClick = onCancel, quiet = true, danger = true, modifier = Modifier.weight(1f))
                Action("OK", R.drawable.ic_check, onClick = { if (text.isNotBlank()) onOk(text.trim()) }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f))
            }
        }
    }
}


/**
 * THE TRACK MANAGER. Every recording on the phone, newest first, with what it weighs. Rename it,
 * send it to the chosen folder again, or delete it — and deleting asks a second time, because a
 * walk deleted by a thumb on a hillside cannot be walked again.
 */
@Composable
private fun TracksFace(
    tracks: List<Folder.Entry>,
    folder: String,
    colour: Long,
    onColour: (Long) -> Unit,
    onRename: (Folder.Entry, String) -> Unit,
    onDelete: (Folder.Entry) -> Unit,
    onShow: (Folder.Entry) -> Unit,
    onHide: () -> Unit,
    onChooseFolder: () -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { Trail.sayInManager(null) }
    var renaming by remember { mutableStateOf<Folder.Entry?>(null) }
    var confirming by remember { mutableStateOf<Folder.Entry?>(null) }
    val note by Trail.managerNote.collectAsState()

    Box(Modifier.fillMaxSize().background(Paint.Ground)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            // THE VISUAL LANGUAGE (27.9.2026): a title with its icon; the folder a row that opens
            // the picker; the line colour a choice; hide a quiet action; each track's show,
            // rename and delete an icon each, delete asking once in red.
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_track, Paint.Sand)
                Spacer(Modifier.width(12.dp))
                Label("Tragovi", Paint.Sand, size = 17, align = TextAlign.Start)
                Spacer(Modifier.width(10.dp))
                Label("${tracks.size}", Paint.Dim, size = 13, align = TextAlign.Start, modifier = Modifier.weight(1f))
                IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
            }

            // WHERE THEY ARE: the folder he chose, and a tap to choose another.
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Paint.Card)) {
                Opens("Mapa", R.drawable.ic_folder, under = folder, onClick = onChooseFolder)
            }

            if (note != null) Label(note ?: "", Paint.Amber, size = 12, align = TextAlign.Start)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_route, Paint.Dim, size = 20.dp)
                TRACK_COLOURS.forEach { option ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(option))
                            .clickable { onColour(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (option == colour) Glyph(R.drawable.ic_check, Paint.Ground, size = 18.dp)
                    }
                }
            }
            Action("Sakrij prikazani trag", R.drawable.ic_eye_off, onClick = onHide, quiet = true, modifier = Modifier.fillMaxWidth())

            if (tracks.isEmpty()) {
                Label("U toj mapi još nema GPX-a. Crveni krug pokreće snimanje.", Paint.Dim, size = 13, align = TextAlign.Start)
            }

            tracks.forEach { track ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paint.Card)
                        .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The name he gave it, the extension after it in the quieter ink (15.9.2026).
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label(track.name, Paint.Sand, size = 15, align = TextAlign.Start)
                            Label(".${track.extension}", Paint.Dim, size = 12)
                        }
                        Label(Tracks.formatSize(track.bytes), Paint.Dim, size = 12, align = TextAlign.Start)
                    }
                    IconAction(R.drawable.ic_eye, "prikaži", onClick = { onShow(track) })
                    IconAction(R.drawable.ic_edit, "ime", onClick = { renaming = track })
                    IconAction(
                        R.drawable.ic_trash,
                        if (confirming?.uri == track.uri) "sigurno?" else "obriši",
                        onClick = {
                            if (confirming?.uri == track.uri) {
                                onDelete(track)
                                confirming = null
                            } else {
                                confirming = track
                            }
                        },
                        tint = Paint.Red,
                    )
                }
            }
        }

        renaming?.let { track ->
            NameBox(
                current = track.fileName,
                onCancel = { renaming = null },
                onOk = { name ->
                    renaming = null
                    onRename(track, name)
                },
            )
        }
    }
}

/**
 * THE TOOLS, IN A WINDOW OF THEIR OWN, over the map.
 *
 * Baba, 15.9.2026: the compass and the level are not settings and should not be in the settings.
 * They are instruments somebody reaches for on a hillside, so they are one key away — T — and
 * they cover the map while they are open, because reading a level is the whole of what you are
 * doing while you are doing it.
 */
/**
 * THE COMPASS, OVER THE MAP, EDGE TO EDGE. That is the whole window.
 *
 * Baba, 15.9.2026, twice: *"overlay the compass over the map from edge to the edge... and this
 * bubble thing is going away, please. I'm persistent."* So there is no ground drawn behind it, no
 * second mode to choose between, and no bubble anywhere in the app. The map shows through the
 * dial; the heading is the one number; the way out is where it always is.
 */
@Composable
private fun CompassOverlay(sensors: Sensors, night: Boolean) {
    var heading by remember { mutableStateOf(0.0) }

    // Bounded by the composition: it dies with the overlay.
    LaunchedEffect(Unit) {
        while (true) {
            heading = sensors.heading()
            delay(50)
        }
    }

    // TWO INKS, BECAUSE THERE ARE TWO KINDS OF MAP (15.9.2026). A dark compass disappears on a
    // satellite photograph and a light one disappears on a street map, so the same dial is drawn
    // in near-black for the pale maps and in sand for the dark ones, and T turns from one to the
    // other. Both are half transparent: the map underneath is the thing being read.
    val ink = if (night) Paint.Sand.copy(alpha = 0.75f) else Color(0xB3000000)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompassDial(heading, full = true, ink = ink)
            Label("${heading.toInt()}° ${Geo.cardinal(heading)}", ink, size = 16)
        }
    }
}


@Composable
private fun CompassDial(heading: Double, full: Boolean = false, ink: Color = Paint.Sand) {
    Canvas(if (full) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(140.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 6f
        drawCircle(ink.copy(alpha = 0.45f), radius = r, center = c, style = Stroke(2f))
        for (tick in 0 until 72) {
            val angle = Math.toRadians(tick * 5.0 - heading - 90.0)
            val long = tick % 6 == 0
            val inner = r * if (long) 0.84f else 0.92f
            val colour = when {
                tick == 0 -> Paint.Red
                long -> ink
                else -> ink.copy(alpha = 0.45f)
            }
            drawLine(
                color = colour,
                start = Offset(c.x + (inner * Math.cos(angle)).toFloat(), c.y + (inner * Math.sin(angle)).toFloat()),
                end = Offset(c.x + (r * Math.cos(angle)).toFloat(), c.y + (r * Math.sin(angle)).toFloat()),
                strokeWidth = if (long) 3f else 1.5f,
            )
        }
        drawLine(ink, Offset(c.x, c.y - r * 0.8f), Offset(c.x, c.y + r * 0.2f), 4f)
        drawCircle(ink, radius = 5f, center = c)
    }
}


/**
 * A BAR THE HEIGHT OF ITS OWN TEXT. Baba, 15.9.2026: *"only height of this bar is height of the
 * text, not cover my whole map."* So the background belongs to the line, not to the column that
 * holds it, and the ink is thin enough to read the map through.
 */
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Paint.Bar)
            .padding(horizontal = GAP, vertical = 3.dp),
        content = content,
    )
}

/**
 * EVERY WORD OVER THE MAP CARRIES ITS OWN SHADOW, AND NOTHING CARRIES A BOX.
 *
 * Baba, 15.9.2026: *"don't put there these squares under the buttons... make a shadow, hard
 * shadow behind, so it's seen from the background but it doesn't take much of real estate."*
 *
 * A panel steals map. A shadow steals nothing and works on both a white street and a dark forest,
 * which is the whole difficulty: the background under this text is not one colour, it is every
 * colour, and no single ink is readable on all of them without something behind the letter itself.
 */
@Composable
private fun Label(
    text: String,
    colour: Color,
    size: Int = 14,
    align: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
    lines: Int = 2,
) {
    Text(
        modifier = modifier,
        text = text,
        color = colour,
        fontSize = size.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = align,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            shadow = Shadow(color = Paint.Ground, offset = Offset(0f, 1.5f), blurRadius = 5f),
        ),
    )
}

// --- the cadastre (27.9.2026) ---------------------------------------------------------------------

/** A parcel he tapped, and its record once the state has answered. */
data class ParcelCard(
    val parcel: Parcels.Parcel,
    val record: Parcels.Record? = null,
    val problem: String? = null,
    /** The owner sheets (vlasnički list); null while the land registry is being asked. */
    val folios: List<Parcels.Folio>? = null,
    val folioProblem: String? = null,
    /** When the record shown was read, if it came off the phone because the state did not answer. */
    val keptSince: Long? = null,
)

/**
 * THE PARCEL'S SHEET, THE WHOLE SCREEN (27.9.2026). His words: *"expand the TextView to the whole
 * screen and add a filter at the top so user can filter owners or any other text."* A sheet with
 * fifty co-owners was a strip he scrolled with a thumb over the map. Now the number, CPY, TXT and
 * the way out along the top; under them a filter that keeps only the rows whose words match (no
 * diacritics needed: "cabri" finds Čabrijan); the rows; and the highlight tick and the trail
 * colours along the bottom, where his thumb is.
 */
@Composable
private fun ParcelCardView(
    card: ParcelCard,
    mine: Parcels.Mark?,
    colour: Long,
    style: Parcels.LineStyle,
    onClose: () -> Unit,
    onText: () -> Unit,
    onCopy: () -> Unit,
    onKeep: (Boolean) -> Unit,
    onColour: (Long) -> Unit,
    onStyle: (Parcels.LineStyle) -> Unit,
    onFindFolio: (String, Boolean) -> Unit,
    onForgetFolio: () -> Unit,
) {
    val record = card.record
    var filter by remember(card.parcel.reference) { mutableStateOf("") }
    // THE THREE TABS (29.9.2026): land use, possession sheet, owner sheet. The possession sheet
    // first, as before, since it is what he opens the sheet for most.
    var tab by remember(card.parcel.reference) { mutableStateOf(Parcels.Tab.POSSESSION) }
    val folios = card.folios.orEmpty()
    val rows = remember(record, folios) {
        val base = record?.let { Parcels.sheetRows(it) }.orEmpty()
        // Once a folio is read, its own header says what the bare link said.
        (if (folios.isEmpty()) base else base.filterNot { it.heading == Parcels.HEAD_REGISTRY }) + folios.flatMap { Parcels.folioRows(it) }
    }
    val inTab = remember(rows, tab) { rows.filter { Parcels.tabOf(it) == tab } }
    val shown = remember(inTab, filter) { Parcels.filterRows(inTab, filter) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paint.Ground)
            .swallowTouches()
            .safeDrawingPadding()
            .padding(horizontal = GAP),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(card.parcel.number + (mine?.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), Paint.Amber, size = 22, align = TextAlign.Start)
                Label(
                    text = record?.let { "k.o. ${it.municipality} · ${it.municipalityNumber}" } ?: "k.o. ${card.parcel.municipality}",
                    colour = Paint.Dim,
                    size = 12,
                    align = TextAlign.Start,
                )
            }
            IconAction(R.drawable.ic_copy, "CPY", onClick = onCopy)
            IconAction(R.drawable.ic_text, "TXT", onClick = onText)
            IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
        }
        val area = record?.areaM2?.toIntOrNull() ?: card.parcel.areaM2
        Label(
            text = Parcels.areaLabel(area) + (record?.address?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            colour = Paint.Sand,
            size = 14,
            align = TextAlign.Start,
        )
        // READ OFF THE PHONE (29.9.2026): the state did not answer, so this is what it said last.
        card.keptSince?.let { since ->
            val day = java.text.SimpleDateFormat("d.M.yyyy.", java.util.Locale.ROOT).format(java.util.Date(since))
            Label("bez signala: prikazan zapis od $day", Paint.Amber, size = 12, align = TextAlign.Start)
        }
        // THE FILTER
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Paint.Card)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(R.drawable.ic_filter, Paint.Dim, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (filter.isEmpty()) Label("vlasnik, adresa, bilo što", Paint.Dim, size = 14, align = TextAlign.Start)
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Paint.Sand, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Paint.AmberBright),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filter.isNotEmpty()) {
                Label("${shown.size} od ${inTab.size}", Paint.Dim, size = 12)
                IconAction(R.drawable.ic_close, null, onClick = { filter = "" }, tint = Paint.Dim)
            }
        }
        // THE TABS: one choice bar, the language's CHOICE; a filter's matches counted on each.
        val tabs = Parcels.Tab.values()
        Choice(
            parts = tabs.map { t ->
                val n = if (filter.isEmpty()) null else Parcels.filterRows(rows.filter { Parcels.tabOf(it) == t }, filter).size
                Part(word = t.word + (n?.let { " $it" } ?: ""))
            },
            chosen = tab.ordinal,
            onChoose = { tab = tabs[it] },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            when {
                record != null -> {
                    var heading = ""
                    shown.forEach { r ->
                        if (r.heading != heading) {
                            heading = r.heading
                            Spacer(Modifier.height(6.dp))
                            Label(r.heading, Paint.Amber, size = 12, align = TextAlign.Start)
                        }
                        // The land registry's entries are legal sentences; they are read whole (29.9.2026).
                        val lines = if (tab == Parcels.Tab.OWNER) 40 else 2
                        Row(Modifier.fillMaxWidth()) {
                            Label(r.main, Paint.Sand, size = 15, align = TextAlign.Start, modifier = Modifier.weight(1f), lines = lines)
                            if (r.side.isNotBlank()) Label(r.side, Paint.AmberBright, size = 14)
                        }
                        if (r.under.isNotBlank()) Label(r.under, Paint.Dim, size = 12, align = TextAlign.Start, lines = lines)
                    }
                    if (tab == Parcels.Tab.OWNER) {
                        when {
                            card.folios == null -> Label("pitam zemljišnu knjigu za vlasnike…", Paint.Dim, size = 13, align = TextAlign.Start)
                            folios.isEmpty() -> FolioFinder(record, card.folioProblem, onFindFolio)
                            else -> card.folioProblem?.let { Label(it, Paint.Red, size = 13, align = TextAlign.Start) }
                        }
                        // A folio he found by hand can be found again, if the number was the wrong one.
                        if (folios.isNotEmpty() && record.landBooks.isEmpty()) {
                            Action("drugi broj", R.drawable.ic_search, onClick = onForgetFolio, quiet = true)
                        }
                    }
                    if (inTab.isEmpty() && tab != Parcels.Tab.OWNER) Label("ništa nije upisano", Paint.Dim, size = 13, align = TextAlign.Start)
                    else if (inTab.isNotEmpty() && shown.isEmpty()) Label("na ovom listu ništa ne odgovara \"$filter\"", Paint.Dim, size = 13, align = TextAlign.Start)
                }
                card.problem != null -> Label(card.problem, Paint.Red, size = 13, align = TextAlign.Start)
                else -> Label("pitam katastar za posjednike…", Paint.Dim, size = 13, align = TextAlign.Start)
            }
        }
        MineControls(mine = mine, colour = colour, style = style, onKeep = onKeep, onColour = onColour, onStyle = onStyle)
    }
}

/**
 * MY PARCEL, AT THE FOOT OF ITS SHEET (29.9.2026): the switch that keeps it among my parcels, the
 * colour it is drawn in (ten quick picks and a bar with every other hue), and its line: dashed by
 * default, so what is his reads apart from the state's own lines on every map.
 */
@Composable
private fun MineControls(
    mine: Parcels.Mark?,
    colour: Long,
    style: Parcels.LineStyle,
    onKeep: (Boolean) -> Unit,
    onColour: (Long) -> Unit,
    onStyle: (Parcels.LineStyle) -> Unit,
) {
    var hues by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paint.Card)
                .clickable { onKeep(mine == null) }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(R.drawable.ic_parcels, if (mine != null) Paint.AmberBright else Paint.Sand, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Label(if (mine != null) "Moja čestica" else "Dodaj u Moje čestice", Paint.Sand, size = 15, align = TextAlign.Start, modifier = Modifier.weight(1f))
            LineSample(colour, style)
            Spacer(Modifier.width(10.dp))
            SwitchMark(mine != null)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Parcels.SWATCHES.forEach { option ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(option))
                        .clickable { onColour(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (option == colour) Label("✓", if (option == 0xFF111111L) Paint.Sand else Paint.Ground, size = 12)
                }
            }
            // ANY OTHER COLOUR: the hue bar opens under the swatches.
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, Look.Outline, RoundedCornerShape(6.dp))
                    .clickable { hues = !hues },
                contentAlignment = Alignment.Center,
            ) { Label(if (hues) "−" else "+", Paint.Sand, size = 14) }
        }
        if (hues) HueBar(onColour)
        Choice(
            parts = Parcels.LineStyle.entries.map { Part(it.word) },
            chosen = style.ordinal,
            onChoose = { onStyle(Parcels.LineStyle.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Every hue in one bar: a touch anywhere on it is that colour. */
@Composable
private fun HueBar(onColour: (Long) -> Unit) {
    val stops = remember { (0..12).map { Color(Parcels.hue(it * 30f)) } }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { at -> onColour(Parcels.hue(360f * at.x / size.width.coerceAtLeast(1))) }
            },
    ) {
        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(stops))
    }
}

/** A short piece of a line in the colour and style it is drawn in on the map. */
@Composable
private fun LineSample(colour: Long, style: Parcels.LineStyle) {
    Canvas(Modifier.width(44.dp).height(12.dp)) {
        val y = size.height / 2f
        val effect = when (style) {
            Parcels.LineStyle.SOLID -> null
            Parcels.LineStyle.DASHED -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 5.dp.toPx()))
            Parcels.LineStyle.DOTTED -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 3.dp.toPx()))
        }
        drawLine(Color(colour), Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(), pathEffect = effect)
    }
}

/**
 * WHERE THE STATE LINKS NO FOLIO (29.9.2026). In Drenova, and wherever the cadastre was measured
 * anew, the land book still keeps the old parcel numbers and the record names no folio. Then he
 * types the land book's own parcel number (from an old deed or an extract) or the folio's number
 * (z.k. uložak), and the book is found by the municipality's name. What he finds is kept.
 */
@Composable
private fun FolioFinder(record: Parcels.Record, problem: String?, onFind: (String, Boolean) -> Unit) {
    var number by remember(record.number) { mutableStateOf("") }
    var byFolio by remember(record.number) { mutableStateOf(false) }
    val go = { number.trim().takeIf { it.isNotEmpty() }?.let { onFind(it, byFolio) } }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(
            "Katastar čestici ${record.number} ne povezuje nijedan zemljišnoknjižni uložak: zemljišna " +
                "knjiga k.o. ${record.municipality} vodi svoje, starije brojeve. Upišite jedan od njih.",
            Paint.Sand, size = 13, align = TextAlign.Start, lines = 6,
        )
        problem?.let { Label(it, Paint.Red, size = 13, align = TextAlign.Start) }
        Choice(
            parts = listOf(Part("zk čestica"), Part("z.k. uložak")),
            chosen = if (byFolio) 1 else 0,
            onChoose = { byFolio = it == 1 },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Paint.Card)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (number.isEmpty()) Label(if (byFolio) "1243" else "370/1", Paint.Dim, size = 15, align = TextAlign.Start)
                BasicTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() || c == '/' }.take(12) },
                    singleLine = true,
                    textStyle = TextStyle(color = Paint.Sand, fontSize = 17.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Paint.AmberBright),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Action("pronađi vlasnike", R.drawable.ic_search, onClick = { go() }, enabled = number.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}

/**
 * THE ONE LIST OF RESULTS (27.9.2026), shaped like Google Maps': a pin with the distance under it,
 * the name, the line under the name. Light under Google's white field, dark in the K panel.
 */
@Composable
private fun ResultsList(hits: List<Finding.Hit>, light: Boolean, onPick: (Finding.Hit) -> Unit) {
    val ink = if (light) Color(0xFF202124) else Paint.Sand
    val dim = if (light) Color(0xFF70757A) else Paint.Dim
    val face = if (light) Color.White else Paint.Card
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(face)
            .verticalScroll(rememberScrollState()),
    ) {
        hits.forEachIndexed { i, hit ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(dim.copy(alpha = 0.25f)))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(hit) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(R.drawable.ic_pin, dim, size = 22.dp)
                    Text(Finding.distanceLabel(hit.distanceM), color = dim, fontSize = 11.sp, maxLines = 1)
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(hit.title, color = ink, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (hit.under.isNotBlank()) Text(hit.under, color = dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** What the parcel search looks for (27.9.2026): *"a dropdown menu for number, owner, street."* */
private enum class SearchBy(val label: String, val hint: String) {
    NUMBER("broj čestice", "2450, 2449/3 — i više odjednom"),
    SHEET("posjedovni list", "broj posjedovnog lista s bilo kojeg lista čestice: sve čestice tog posjednika"),
    STREET("ulica", "ulica i kućni broj; traži Google (treba ključ)"),
}

/**
 * MOJE ČESTICE (29.9.2026): *"new feature is My Parcels. So user can select parcel and store it
 * under My Parcels ... he can click on each of them and their owner sheet and possession sheet are
 * opening up."* Every parcel he keeps, in its colour and line; a press takes the map there and
 * opens its sheet; the pencil names it; the bin forgets it, asking once. Above the list, the
 * search that was in Mantra Trail's K panel: by number or possession sheet in the cadastral
 * municipality under the map, or a street through Google.
 */
@Composable
private fun MyParcelsFace(
    store: Store,
    marks: List<Parcels.Mark>,
    onParcel: (Finding.Hit) -> Unit,
    onPlace: (Finding.Hit) -> Unit,
    onOpen: (Parcels.Mark) -> Unit,
    onRename: (Parcels.Mark, String) -> Unit,
    onRemove: (Parcels.Mark) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var by by remember { mutableStateOf(SearchBy.NUMBER) }
    var menu by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<String?>(null) }
    var here by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<Finding.Hit>>(emptyList()) }
    var renaming by remember { mutableStateOf<Parcels.Mark?>(null) }
    var confirming by remember { mutableStateOf<String?>(null) }
    var sureAll by remember { mutableStateOf(false) }
    val session = remember { java.util.UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val (lat, lon) = Canvases.centre() ?: return@LaunchedEffect
        here = runCatching { ParcelNet.municipalityFull(lat, lon) }.getOrNull()
        if (here == null) line = "katastarska općina pod kartom se nije mogla pročitati"
    }

    fun find() {
        val ko = here
        val words = text.trim()
        if (words.isEmpty()) { line = "upišite što tražite"; return }
        busy = true
        line = "tražim…"
        hits = emptyList()
        scope.launch {
            val answer: Result<List<Finding.Hit>> = runCatching {
                when (by) {
                    SearchBy.NUMBER -> {
                        ko ?: error("pomaknite kartu iznad katastarske općine")
                        Parcels.numbers(words).ifEmpty { error("upišite broj čestice, npr. 2450 ili 2449/3") }
                            .flatMap { ParcelNet.ossSearch(ko.third, number = it) }
                    }
                    SearchBy.SHEET -> {
                        ko ?: error("pomaknite kartu iznad katastarske općine")
                        val n = Regex("""\d+""").find(words)?.value ?: error("upišite broj posjedovnog lista")
                        ParcelNet.ossSearch(ko.third, sheet = n)
                    }
                    SearchBy.STREET -> {
                        val near = Canvases.centre()?.let { Fix(it.first, it.second, null, 0L, null) }
                        val query = ko?.let { "$words, ${it.second.lowercase().replaceFirstChar { c -> c.uppercase() }}" } ?: words
                        val (found, problem) = PlaceSearch.find(query, near, store, session)
                        if (found.isEmpty()) error(problem ?: "ništa nije pronađeno")
                        found
                    }
                }
            }
            busy = false
            hits = answer.getOrNull().orEmpty()
            line = answer.exceptionOrNull()?.message
                ?: if (hits.isEmpty()) "ništa nije pronađeno u k.o. ${ko?.second ?: ""}" else "pronađeno: ${hits.size}"
        }
    }

    Box(Modifier.fillMaxSize().background(Paint.Ground).swallowTouches()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_parcels, Paint.Sand)
                Spacer(Modifier.width(12.dp))
                Label("Moje čestice", Paint.Sand, size = 17, align = TextAlign.Start)
                Spacer(Modifier.width(10.dp))
                Label("${marks.size}", Paint.Dim, size = 13, align = TextAlign.Start, modifier = Modifier.weight(1f))
                IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
            }

            if (marks.isEmpty()) {
                Label(
                    "Još nijedna. Dodirnite česticu na karti, dodirnite je ponovno za list i uključite " +
                        "\"Dodaj u Moje čestice\" na dnu lista.",
                    Paint.Dim, size = 13, align = TextAlign.Start, lines = 5,
                )
            }
            marks.forEach { mark ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paint.Card)
                        .clickable { onOpen(mark) }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineSample(mark.colour, mark.style)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Label(
                            mark.number + (if (mark.name.isNotBlank()) " · ${mark.name}" else ""),
                            Paint.Sand, size = 15, align = TextAlign.Start,
                        )
                        Label(
                            "k.o. ${mark.municipality}" + if (mark.rings.isEmpty()) " · obris stiže" else "",
                            Paint.Dim, size = 11, align = TextAlign.Start,
                        )
                    }
                    IconAction(R.drawable.ic_edit, null, onClick = { renaming = mark })
                    IconAction(
                        R.drawable.ic_trash,
                        if (confirming == mark.reference) "sigurno?" else null,
                        onClick = {
                            if (confirming == mark.reference) {
                                onRemove(mark)
                                confirming = null
                            } else {
                                confirming = mark.reference
                            }
                        },
                        tint = Paint.Red,
                    )
                }
            }
            if (marks.size > 1) {
                Action(
                    verb = if (sureAll) "ponovno: sve" else "obriši sve",
                    icon = R.drawable.ic_trash,
                    onClick = { if (sureAll) { onClearAll(); sureAll = false } else sureAll = true },
                    quiet = true,
                    danger = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Label("PRONAĐI ČESTICU", Paint.Dim, size = 11, align = TextAlign.Start, modifier = Modifier.padding(top = 10.dp))
            Label(
                text = here?.let { "k.o. ${it.second} · ${it.first}" } ?: "…",
                colour = Paint.Dim,
                size = 11,
                align = TextAlign.Start,
            )
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paint.Card)
                        .border(1.dp, Look.Outline, RoundedCornerShape(12.dp))
                        .clickable { menu = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(
                        when (by) {
                            SearchBy.NUMBER -> R.drawable.ic_parcels
                            SearchBy.SHEET -> R.drawable.ic_text
                            SearchBy.STREET -> R.drawable.ic_pin
                        },
                        Paint.Sand, size = 20.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Label(by.label, Paint.Sand, size = 15)
                    Spacer(Modifier.width(10.dp))
                    Glyph(R.drawable.ic_chevron_down, Paint.Dim, size = 20.dp)
                }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    SearchBy.entries.forEach { choice ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(choice.label, fontSize = 16.sp) },
                            onClick = {
                                by = choice
                                menu = false
                                hits = emptyList()
                                line = null
                            },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Paint.Sand, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Paint.AmberBright),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (!busy) find() }),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, Paint.Amber, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
                Action("Traži", R.drawable.ic_search, onClick = { find() }, enabled = !busy)
            }
            Label(by.hint, Paint.Dim, size = 11, align = TextAlign.Start)
            line?.let { Label(it, Paint.Amber, size = 12, align = TextAlign.Start) }
            if (hits.isNotEmpty()) {
                ResultsList(hits, light = false) { hit ->
                    if (hit.source == Finding.Source.PARCEL) onParcel(hit) else onPlace(hit)
                }
            }
        }

        renaming?.let { mark ->
            NameBox(
                current = mark.name.ifBlank { mark.number },
                onCancel = { renaming = null },
                onOk = { name ->
                    renaming = null
                    onRename(mark, name)
                },
            )
        }
    }
}

/** Every touch on this is its own: nothing reaches the map beneath. */
private fun Modifier.swallowTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                .changes.forEach { it.consume() }
        }
    }
}
