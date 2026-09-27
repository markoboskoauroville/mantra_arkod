package com.mantra.trail

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
 * THE MAP IS THE APP, AND EVERYTHING ELSE GETS OUT OF ITS WAY.
 *
 * Baba, 14.9.2026, on v5: *"When I tap in the middle of the screen, all interface is gone. I just
 * see the map. I'm following the map. That's only what I need... one button settings, not too
 * many buttons and too many numbers on the screen."*
 *
 * So: one line of numbers at the top, four small keys at the bottom, everything over the map at
 * half transparency, and a tap in the middle takes all of it away and brings it back. The map
 * choice is one key that turns through the three, and everything else lives in settings.
 *
 * THE SNIPER IS GONE. It was a target drawn over the middle of the map, and the middle of the map
 * is where the person is looking. What is left is a small ring the size of a fingernail: enough
 * to say where the centre is when panning, not enough to be in the way.
 */
private val GAP = 10.dp
private val KEY = 46.dp

/** The one size for the marks: the centre of the map, the where key and the record circle. */
private val MARK = 22.dp

/** The crosshair over the map: bigger than the key's mark, and far quieter. */
private val CROSS = 34.dp

/** The compass's three states, in the order T turns through them. */
private const val COMPASS_DARK = 0
private const val COMPASS_NIGHT = 1
private const val COMPASS_OFF = 2

/** The five a line can be drawn in: green, amber, red, blue, white. */
/** VTM's own themes, in the order they are offered. The plain one leads because it is plainest. */
private val THEMES = listOf("MANTRA", "DEFAULT", "OSMARENDER", "NEWTRON", "BIKER", "TRONRENDER")

private val TRACK_COLOURS = listOf(0xFF34D399L, 0xFFE8A64BL, 0xFFEF4444L, 0xFF60A5FAL, 0xFFF2DDB4L)

/** Bumped when a picker or a download changes something a row shows. */
object UiTick {
    var n by mutableIntStateOf(0)
    fun bump() {
        n += 1
    }
}

@Composable
fun TrailApp(
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
    onBare: (Boolean) -> Unit,
    tracks: () -> List<Folder.Entry>,
    folderLabel: String,
    onRenameJustFinished: (java.io.File, String) -> Unit,
    onDiscardRecording: (java.io.File) -> Unit,
    onDeleteTrack: (Folder.Entry) -> Unit,
    onRenameTrack: (Folder.Entry, String) -> Unit,
    onShowTrack: (Folder.Entry) -> Unit,
    onTestTiles: () -> Unit,
    onFetchRegion: (OamIndex.Entry) -> Unit,
    onFetchImagery: (Int) -> Unit,
    onTestKey: (Keyring.Key) -> Unit,
    onRemoveKey: (Keyring.Key) -> Unit,
    onSaveRoute: (List<Pair<Double, Double>>) -> Unit,
    onFindWays: (List<Pair<Double, Double>>, String, Int) -> Unit,
    onSaveOption: (Routing.Option) -> Unit,
    onHeights: (Int) -> Unit,
    routeOptions: List<Routing.Option>,
) {
    var layer by remember { mutableStateOf(Layers.byId(store.layerId)) }
    var settings by remember { mutableStateOf(false) }
    var compass by remember { mutableIntStateOf(store.compassMode) }
    var points by remember { mutableStateOf(store.routePoints) }
    var routeMenu by remember { mutableStateOf(false) }
    var showPlaces by remember { mutableStateOf(false) }
    var bare by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(13) }
    // EVERY CANVAS GETS TOLD TO DRAW (17.9.2026). This was a one-shot flag: true the first time a
    // map view existed, and true for ever after. Switching to Google's renderer and back builds a
    // NEW VTM canvas, and nothing told it anything, because the flag was already true — a black
    // screen with his position dot on it, which is exactly what he photographed. A counter goes
    // up with each canvas, so each one is drawn on.
    var ready by remember { mutableIntStateOf(0) }
    // LOCKED TO THE MIDDLE (15.9.2026). One press centres and holds; the next lets the map go.
    var follow by remember { mutableStateOf(false) }
    var lastCentreTap by remember { mutableLongStateOf(0L) }
    // 0 free, 1 north up, 2 turning with the walk (16.9.2026, as Google's little compass does).
    var mapTurn by remember { mutableFloatStateOf(0f) }

    val fix by Trail.fix.collectAsState()
    val stats by Trail.stats.collectAsState()
    val recordingSince by Trail.recordingSince.collectAsState()
    val paused by Trail.paused.collectAsState()
    val note by Trail.note.collectAsState()
    val net by Net.line.collectAsState()
    val recording = recordingSince != null
    val justFinished by Trail.justFinished.collectAsState()
    var showTracks by remember { mutableStateOf(false) }
    var showMaps by remember { mutableStateOf(false) }
    // How deep the kept imagery goes, and what the area on the screen would cost at that depth.
    var imageryDepth by remember { mutableIntStateOf(15) }
    val imageryCost = remember(UiTick.n, imageryDepth, showMaps) {
        val box = CanvasHolder.canvas?.visibleBox() ?: GoogleHolder.canvas?.visibleBox()
        if (box == null) {
            "move the map to the ground you want"
        } else {
            val tiles = Imagery.countFor(
                box[0], box[1], box[2], box[3],
                Imagery.MIN_ZOOM.coerceAtMost(imageryDepth),
                imageryDepth,
            )
            "$tiles tiles · ${Imagery.sizeLabel(tiles)}"
        }
    }
    var listing by remember { mutableStateOf<List<OamIndex.Entry>>(emptyList()) }
    var listingOf by remember { mutableStateOf<String?>(null) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val installedMaps = remember(UiTick.n, showMaps, settings) { OamDownload.installed(appContext) }
    val imageryKept = remember(UiTick.n, showMaps, settings) { ImageryStore.label(appContext) }
    val unfinishedMaps = remember(UiTick.n, showMaps, settings) { OamDownload.unfinished(appContext) }
    val scope = rememberCoroutineScope()

    // THE CADASTRE (27.9.2026). The parcels are on the map unless he turned them off; a tap on one
    // opens its record with the owners; the ones he highlighted are kept with their colour.
    var cadastreOn by remember { mutableStateOf(store.cadastreOn) }
    var marks by remember { mutableStateOf(store.parcelMarks) }
    var parcelColour by remember { mutableLongStateOf(store.parcelColour) }
    var card by remember { mutableStateOf<ParcelCard?>(null) }
    var parcelPanel by remember { mutableStateOf(false) }
    var searchBar by remember { mutableStateOf(store.googleSearchBar) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    fun setMarks(next: List<Parcels.Mark>) {
        marks = next
        store.parcelMarks = next
        ParcelsShown.marks = next
        Canvases.refreshParcels()
    }

    // THE SHAPES COME LATER (27.9.2026). Only the state's slow WFS has them, so a highlight is
    // kept at once with its number and colour, and its outline is fetched behind it; whatever is
    // still missing is asked again when the app opens and when the K panel does.
    var fetchingShapes by remember { mutableStateOf(false) }

    suspend fun fillShapes() {
        if (fetchingShapes) return
        val missing = Parcels.shapeless(marks)
        if (missing.isEmpty()) return
        fetchingShapes = true
        // Three quiet tries: the state's WFS answers in half a minute when it answers, and now
        // and then refuses with a busy database, which the next try usually gets past.
        var found: List<Parcels.Parcel>? = null
        for (attempt in 1..3) {
            found = runCatching { ParcelNet.shapes(missing.map { it.reference }) }.getOrNull()
            if (!found.isNullOrEmpty()) break
            delay(5_000)
        }
        fetchingShapes = false
        if (found.isNullOrEmpty()) {
            Trail.say("The outline of ${missing.joinToString(", ") { it.number }} did not come yet; K asks again")
            return
        }
        setMarks(Parcels.withShapes(marks, found))
    }

    suspend fun openCard(parcel: Parcels.Parcel) {
        card = ParcelCard(parcel)
        val answer = runCatching { ParcelNet.record(parcel.id) }
        if (card?.parcel?.id != parcel.id) return
        card = ParcelCard(
            parcel,
            record = answer.getOrNull(),
            problem = answer.exceptionOrNull()?.let { "the owners could not be read: ${it.message ?: it.javaClass.simpleName}" },
        )
    }

    /**
     * ONE TAP SELECTS, A TAP ON THE SELECTION OPENS THE SHEET (27.9.2026). v87 kept every tapped
     * parcel highlighted; his correction: *"only one parcel can be highlighted at a time. What
     * stays highlighted, it's only what user choose to highlight and choose the color. And this is
     * just to mark current click, so new deletes the old click highlight."* So a tap outlines one
     * parcel in cyan and forgets the one before; the tick and the colours on the sheet are what
     * keep a parcel. A parcel he highlighted is selected from its own outline on the phone, so the
     * two taps work on it with no signal.
     */
    var selected by remember { mutableStateOf<Parcels.Parcel?>(null) }

    fun select(parcel: Parcels.Parcel?) {
        selected = parcel
        ParcelsShown.selection = parcel?.takeIf { it.rings.isNotEmpty() }?.let { Parcels.markOf(it, Parcels.SELECTION) }
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
        Trail.say("finding ${parcel.number} on the map (the state's map service takes about half a minute)…")
        val shape = runCatching { ParcelNet.shapes(listOf(ref)) }.getOrNull()?.firstOrNull { it.reference == ref }
        if (shape == null || shape.rings.isEmpty()) {
            Trail.say("${parcel.number}: the state's map service did not answer; its sheet is open")
            return
        }
        val found = parcel.copy(rings = shape.rings)
        select(found)
        val (lat, lon) = found.middle
        ParcelsShown.pin = lat to lon
        Canvases.refreshParcels()
        Canvases.goTo(lat, lon, 18)
        Trail.say("${parcel.number} is on the map")
    }

    suspend fun tapped(lat: Double, lon: Double) {
        if (!ParcelsShown.on) return
        val current = selected
        if (current != null && current.rings.any { Parcels.contains(it, lat, lon) }) {
            Trail.say(null)
            openCard(current)
            return
        }
        marks.firstOrNull { m -> m.rings.any { Parcels.contains(it, lat, lon) } }?.let { m ->
            card = null
            select(Parcels.Parcel(m.id, m.number, m.reference, null, m.rings))
            Trail.say("${m.number} · tap it again for its sheet")
            return
        }
        if (Canvases.currentZoom() < Parcels.TAP_ZOOM) {
            Trail.say("Zoom in to pick a parcel")
            return
        }
        val answer = runCatching { ParcelNet.at(lat, lon) }
        val parcel = answer.getOrNull()
        if (parcel == null) {
            Trail.say(if (answer.isFailure) "The cadastre did not answer: ${answer.exceptionOrNull()?.message}" else "No parcel there")
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
        Trail.say("${parcel.number} · tap it again for its sheet")
    }

    LaunchedEffect(Unit) {
        ParcelsShown.on = store.cadastreOn
        ParcelsShown.marks = store.parcelMarks
        ParcelsShown.onTap = { lat, lon -> scope.launch { tapped(lat, lon) } }
        fillShapes()
    }

    LaunchedEffect(parcelPanel) { if (parcelPanel) fillShapes() }

    // The ink follows the ground: dark on the pale maps, sand on the photographs and the night theme.
    LaunchedEffect(ready, layer.id, settings, UiTick.n) {
        ParcelsShown.ink = Parcels.inkFor(layer.id, store.themeName, layer.googleView?.mapType)
        Canvases.refreshParcels()
    }

    // The map the app opened on, drawn as soon as the view is real and not a moment before.
    LaunchedEffect(ready, layer.id) {
        if (ready > 0) {
            showLayer(store, layer)
            // and whatever was on the old map goes onto the new one
            Canvases.setRoutePoints(store.routePoints)
            Shown.route?.let { Canvases.drawRoute(it.first, it.second) }
            Shown.track?.let { Canvases.drawSavedTrack(it.first, it.second) }
        }
    }

    // The network line: sampled from the phone's own byte counters once a second, so it reports
    // mapsforge's tile fetching too, which happens inside the library. Bounded by the composition.
    LaunchedEffect(Unit) {
        while (true) {
            Net.sample(System.currentTimeMillis())
            delay(1_000)
        }
    }

    // The zoom on the screen follows the map rather than the other way round. Twice a second is
    // enough for a number that changes when a thumb moves, and it stops with the composition.
    LaunchedEffect(Unit) {
        while (true) {
            Canvases.currentZoom()?.let {
                if (it != zoom) {
                    zoom = it
                    // A blank offline map at a zoom explains itself now, rather than waiting to
                    // be photographed: the file is asked what it holds under the crosshair.
                    if (layer.kind == LayerKind.VECTOR_FILE) {
                        Trail.say(Canvases.emptyHere())
                    }
                }
            }
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Paint.Ground)) {

        MapSurface(
            store = store,
            layer = layer,
            generation = ready,
            points = points,
            fix = fix,
            line = Trail.line.collectAsState().value,
            follow = follow,
            onCanvas = onCanvas,
            onReady = { ready += 1 },
        )


        // THE LITTLE COMPASS, at the top right where Google keeps it.
        //
        // It was written on 16.9.2026 and he never saw it, because it was nested INSIDE the 72dp
        // target in the middle of the screen: fillMaxSize inside a 72dp box is 72dp, so the thing
        // was drawn behind the crosshair, thumb-sized, in the centre. It sits on the screen now.
        //
        // One tap puts north at the top and centres him; the next turns the map so the way he is
        // walking is up, and it wears an amber ring while it does.
        if (!bare) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 52.dp, end = 10.dp)) {
                LittleCompass(
                    turn = mapTurn,
                    modifier = Modifier.align(Alignment.TopEnd),
                    onTap = { Canvases.setMapRotation(0f) },
                )
            }
        }

        // THE TAP IN THE MIDDLE. A small target, so panning the map anywhere else is untouched,
        // and the mark that says where the centre is sits inside it.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    bare = !bare
                    onBare(bare)
                },
            contentAlignment = Alignment.Center,
        ) {
            CentreCross()
        }

        if (!bare) {
            // A BAR BEHIND THE WORDS. Baba, 15.9.2026: *"80% transparent bar behind the letters
            // and the symbols... because I don't see them in the map."* The shadow alone was not
            // enough on a pale street map. The bar runs the full width and only as tall as the
            // line it carries, so it costs a strip rather than a panel.
            // THE BAR IS THE HEIGHT OF ITS LINE AND NOTHING MORE. Putting the background on the
            // column meant it also covered the safe-area inset and every invisible row inside it,
            // which is how a strip of text ended up shading half the map.
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).safeDrawingPadding(),
            ) {
                FixLine(fix, zoom, layer)
                // THE ROUND SEARCH FIELD, as Google Maps has it (27.9.2026), on Google's map only
                // and only while the settings show it.
                if (searchBar && layer.family == MapLayer.Family.GOOGLE) {
                    GoogleSearchBar(store = store, near = fix, onPlace = { showPlace(it) })
                }
            }

            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeDrawingPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // A line with nothing in it takes no room at all now. It used to be drawn at zero
                // opacity, which is invisible but still occupies its height — and with a
                // background behind the column, that height was a shaded band over the map.
                if (net != null) StatusLine(net)
                if (note != null) NoteLine(note)
                if (recording) TrackLine(stats, recording = !paused)
                // THE ORDER IS THE THUMB'S, NOT THE LIST'S. Baba, 15.9.2026: the record circle sits
                // in the middle, straight above the phone's own home button, with the centre key
                // beside it; the three that are pressed rarely spread out from there.
                // SEVEN KEYS, AND THE RED ONE IS STILL THE MIDDLE OF THEM. Zoom sits at both ends
                // where either thumb reaches it: Baba, 15.9.2026, *"give me plus and minus so I
                // don't need to zoom with my pinching. It hurts."*
                Row(
                    Modifier.fillMaxWidth().background(Paint.Bar).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Key(glyph = "−", lit = false, onClick = { Canvases.zoomOut() }, icon = R.drawable.ic_minus)
                    // K, THE CADASTRE, WHERE T WAS (27.9.2026, his order): the compass's
                    // three states went into the settings, and this key opens the parcels panel,
                    // lit while the parcels are drawn.
                    Key(
                        glyph = "K",
                        lit = cadastreOn,
                        onClick = { parcelPanel = true },
                        icon = R.drawable.ic_parcels,
                    )
                    // ONE TAP CENTRES, TWO IN A ROW LOCK (15.9.2026). A second tap inside a
                    // second is somebody saying "and keep it there"; a second tap later is just
                    // somebody centring again. While it is locked, one tap lets the map go.
                    MarkKey(
                        lit = follow,
                        onClick = {
                            val now = System.currentTimeMillis()
                            when {
                                // No sentence for either: the mark's own centre fills when it is
                                // locked, and a line of text for a state that is already drawn is
                                // a line of text over the map (15.9.2026).
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
                    // ONE BUTTON FOR THE MAP. It says which one is on and turns to the next.
                    Key(
                        glyph = layer.short,
                        lit = false,
                        onClick = {
                            // ONLY THROUGH WHAT CAN ACTUALLY DRAW. Baba, 15.9.2026: *"I want to
                            // show only map which is selected in the settings, not going through
                            // all these different ways."* A family with no key and a family with
                            // no file are presses that do nothing, so the key skips them and the
                            // settings list still holds every map.
                            val picked = nextUsable(store, layer)
                            layer = picked
                            store.layerId = picked.id
                            scope.launch { showLayer(store, picked) }
                        },
                        // Which map is up, as a picture: the mountain for the file on the phone,
                        // the satellite for kept imagery, the globe for Google's.
                        icon = when {
                            layer.family == MapLayer.Family.GOOGLE -> R.drawable.ic_globe
                            layer.id == Layers.IMAGERY.id -> R.drawable.ic_satellite
                            else -> R.drawable.ic_mountain
                        },
                    )
                    Key("⚙", lit = false, onClick = { settings = true }, icon = R.drawable.ic_settings)
                    // ONE KEY FOR POINTS (16.9.2026). Pressing it drops the next one where the
                    // crosshair is — A, then B, then C — and it shows which letter is next. A
                    // long press opens the manager, where they are removed and the ways found.
                    // Two keys for this were two ways of doing one thing.
                    PointKey(
                        letter = Route.letterFor(points.size),
                        placed = points.isNotEmpty(),
                        onTap = {
                            val at = Canvases.centre() ?: return@PointKey
                            if (points.size >= Route.MAX_POINTS) {
                                Trail.say("That is as many points as one route holds")
                                return@PointKey
                            }
                            points = points + at
                            store.routePoints = points
                            Canvases.setRoutePoints(points)
                        },
                        onLongPress = { routeMenu = true },
                    )
                    Key(glyph = "+", lit = false, onClick = { Canvases.zoomIn() }, icon = R.drawable.ic_plus)
                }
            }
        }

        // THE POPUP THAT ASKS WHAT THE WALK WAS CALLED. It opens when a recording is written and
        // takes itself away after three seconds, so a walk can be ended with one press and no
        // second thought — but the countdown STOPS the moment he touches the field, because a
        // box that closes while somebody is typing in it is worse than no box at all.
        // THE SHEET, OVER EVERYTHING (27.9.2026): the whole screen, his word.
        card?.let { shown ->
            val mark = marks.firstOrNull { it.reference == shown.parcel.reference }
            ParcelCardView(
                card = shown,
                markedColour = mark?.colour,
                onClose = { card = null },
                onCopy = {
                    val stamp = java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale.ROOT)
                        .format(java.util.Date())
                    val parcel = shown.parcel.copy(rings = mark?.rings ?: shown.parcel.rings)
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(Parcels.toText(parcel, shown.record, stamp)))
                    Trail.say("parcel ${parcel.number} copied")
                },
                onText = {
                    val stamp = java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale.ROOT)
                        .format(java.util.Date())
                    val parcel = shown.parcel.copy(rings = mark?.rings ?: shown.parcel.rings)
                    val text = Parcels.toText(parcel, shown.record, stamp)
                    scope.launch {
                        val (_, said) = withContext(Dispatchers.IO) {
                            Folder.saveText(appContext, store, Parcels.textFileName(parcel), text)
                        }
                        Trail.say(said)
                    }
                },
                onHighlight = { colour ->
                    if (colour == null) {
                        setMarks(Parcels.without(marks, shown.parcel.reference))
                    } else {
                        parcelColour = colour
                        store.parcelColour = colour
                        // A colour changed on a mark that has its shape keeps the shape.
                        val had = marks.firstOrNull { it.reference == shown.parcel.reference }
                        val mark = Parcels.markOf(shown.parcel, colour)
                        setMarks(Parcels.withMark(marks, if (mark.rings.isEmpty() && had != null) mark.copy(rings = had.rings) else mark))
                        scope.launch { fillShapes() }
                    }
                },
                defaultColour = parcelColour,
            )
        }
        if (parcelPanel) {
            ParcelsPanel(
                store = store,
                on = cadastreOn,
                marks = marks,
                colour = parcelColour,
                onSwitch = { on ->
                    cadastreOn = on
                    store.cadastreOn = on
                    ParcelsShown.on = on
                    if (!on) card = null
                    Canvases.refreshParcels()
                },
                onColour = { c ->
                    parcelColour = c
                    store.parcelColour = c
                },
                onParcel = { hit -> parcelPanel = false; scope.launch { showFoundParcel(hit) } },
                onPlace = { hit -> parcelPanel = false; showPlace(hit) },
                onGo = { mark ->
                    val p = Parcels.Parcel(mark.id, mark.number, mark.reference, null, mark.rings)
                    if (mark.rings.isNotEmpty()) {
                        val (lat, lon) = p.middle
                        Canvases.goTo(lat, lon, 18)
                    }
                    parcelPanel = false
                    if (mark.id != 0L) scope.launch { openCard(p) }
                },
                onRemove = { mark -> setMarks(Parcels.without(marks, mark.reference)) },
                onClearAll = { setMarks(emptyList()) },
                onClose = { parcelPanel = false },
            )
        }

        justFinished?.let { file ->
            // EITHER ANSWER SAVES THE WALK. Cancel means "do not rename it", not "throw it
            // away": the track goes into the folder under the date it already has.
            NameBox(
                current = Tracks.displayName(file.name),
                // CANCEL MEANS CANCELLED (17.9.2026, his word). It used to save the walk under
                // its own name, on the reasoning that a walk is hard-won and should never be lost
                // by a stray press. But a key called cancel that keeps the thing is a key that
                // lies, and he pressed it meaning to throw the walk away. It throws it away.
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

        // WHAT IS DRAWN BELONGS TO HIM, NOT TO AN ENGINE (17.9.2026). The lettered points and the
        // way found between them are put back on every new canvas, so switching between the
        // offline file and Google's map keeps the line he is walking rather than losing it.
        LaunchedEffect(ready, points) {
            if (ready > 0) Canvases.setRoutePoints(points)
        }

        // THE LIGHT IN FRONT OF THE DOT NEEDS THE HEADING, whether or not the compass overlay is
        // on. Five times a second is enough for a light that only moves when the phone turns, and
        // the canvas redraws only when the heading has really moved (bounded by the composition).
        LaunchedEffect(ready) {
            while (true) {
                if (ready > 0) {
                    Canvases.setHeading(sensors.heading())
                    // The compass reports the map's own angle, so it is read where the heading is.
                    mapTurn = Canvases.mapRotationDeg()
                }
                delay(200)
            }
        }

        if (showPlaces) {
            PlacesFace(
                fix = fix,
                store = store,
                onAdd = { place ->
                    if (points.size < Route.MAX_POINTS) {
                        points = points + (place.lat to place.lon)
                        store.routePoints = points
                        Canvases.setRoutePoints(points)
                        Canvases.centreOn(Fix(place.lat, place.lon, null, 0L, null))
                    }
                    showPlaces = false
                    Trail.say("${place.name} added as ${Route.letterFor(points.size - 1)}")
                },
                onClose = { showPlaces = false },
            )
        }

        if (routeMenu) {
            RouteMenu(
                store = store,
                points = points,
                found = routeOptions,
                onFind = {
                    routeMenu = false
                    showPlaces = true
                },
                onAdd = {
                    val at = Canvases.centre()
                    if (at != null && points.size < Route.MAX_POINTS) {
                        points = points + at
                        store.routePoints = points
                        Canvases.setRoutePoints(points)
                    }
                },
                onRemove = { index ->
                    points = points.filterIndexed { i, _ -> i != index }
                    store.routePoints = points
                    Canvases.setRoutePoints(points)
                },
                onRoute = { profile, wanted -> onFindWays(points, profile, wanted) },
                onSaveOption = { option -> onSaveOption(option) },
                onHeights = onHeights,
                onSave = { onSaveRoute(points) },
                onClose = { routeMenu = false },
            )
        }

        if (compass != COMPASS_OFF) {
            CompassOverlay(
                sensors = sensors,
                night = Parcels.inkFor(layer.id, store.themeName, layer.googleView?.mapType) == Parcels.INK_LIGHT,
            )
        }

        if (showMaps) {
            MapsFace(
                store = store,
                installed = installedMaps,
                imageryDepth = imageryDepth,
                imageryCost = imageryCost,
                imageryHeld = imageryKept,
                onImageryDepth = { imageryDepth = it },
                onFetchImagery = { onFetchImagery(imageryDepth) },
                onForgetImagery = {
                    ImageryStore.forget(appContext)
                    Trail.say("The kept imagery was deleted")
                    UiTick.bump()
                },
                folder = OamDownload.folderLabel(appContext),
                onUse = { file ->
                    store.offlineMapName = file.name
                    UiTick.bump()
                    scope.launch { showLayer(store, Layers.OFFLINE) }
                },
                onRemove = { file ->
                    OamDownload.remove(file)
                    if (store.offlineMapName == file.name) store.offlineMapName = ""
                    UiTick.bump()
                },
                onBrowse = { continent ->
                    listingOf = continent
                    listing = emptyList()
                    scope.launch {
                        OamDownload.say("Reading the list for $continent…")
                        val (entries, problem) = OamDownload.index(continent)
                        listing = entries
                        OamDownload.say(problem)
                    }
                },
                listing = listing,
                listingOf = listingOf,
                onFetch = onFetchRegion,
                onClose = { showMaps = false },
            )
        }

        if (showTracks) {
            // THE LIST IS LOADED, NOT COMPUTED. It used to be read from the folder inside the
            // composition, so every tap — a colour, a note, anything — listed the directory again
            // on the main thread. That is why changing the line colour was slow (15.9.2026).
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
                compassMode = compass,
                // ON OR OFF (27.9.2026, his word): "when it's unselected, there is no compass". Its
                // ink is no longer his to choose: it follows the map, light over the dark ones.
                onCompass = {
                    compass = if (compass == COMPASS_OFF) COMPASS_DARK else COMPASS_OFF
                    store.compassMode = compass
                },
                searchBar = searchBar,
                onSearchBar = {
                    searchBar = !searchBar
                    store.googleSearchBar = searchBar
                },
                version = version,
                installedMaps = installedMaps,
                unfinishedMaps = unfinishedMaps,
                keyring = remember(UiTick.n, settings) { store.keyring },
                onTestKey = onTestKey,
                onRemoveKey = onRemoveKey,
                hasImagery = imageryKept != "none yet",
                imageryHeld = imageryKept,
                onFetchOffer = { entry ->
                    settings = false
                    showMaps = true
                    onFetchRegion(entry)
                },
                drawingMapName = store.offlineMapName.ifBlank {
                    installedMaps.firstOrNull()?.name ?: ""
                },
                offlineUnder = if (installedMaps.isEmpty()) {
                    if (unfinishedMaps.isEmpty()) "none yet" else "one unfinished — open to carry on"
                } else {
                    val drawing = store.offlineMapName.ifBlank { installedMaps.first().name }
                    drawing.removePrefix("oam-").removeSuffix(".map") +
                        " · " + (if (store.themeName == "MANTRA") "walking" else store.themeName.lowercase())
                },
                onUseMap = { file ->
                    store.offlineMapName = file.name
                    settings = false
                    scope.launch { showLayer(store, Layers.OFFLINE) }
                },
                onOfflineView = { view ->
                    store.themeName = view.theme
                    CanvasHolder.canvas?.setTheme(view.theme)
                    settings = false
                    scope.launch { showLayer(store, Layers.OFFLINE) }
                },
                chosenGoogleId = store.googleViewId,

                onPick = { picked ->
                    if (picked.family == MapLayer.Family.GOOGLE) store.googleViewId = picked.id
                    settings = false
                    layer = picked
                    store.layerId = picked.id
                    store.rememberStyle(picked)
                    // CHOOSING A MAP CLOSES THE SETTINGS AND SHOWS IT (15.9.2026). Picking one and
                    // then having to find the way out is two decisions where there was one.
                    settings = false
                    scope.launch { showLayer(store, picked) }
                },
                onChooseMapFile = onChooseMapFile,
                onChooseExportFolder = onChooseExportFolder,
                onImportKeys = onImportKeys,
                onPause = onPause,
                recordingPaused = paused,
                onTracks = {
                    settings = false
                    showTracks = true
                },
                trackCount = tracks().size,
                onTestTiles = onTestTiles,
                onMaps = {
                    settings = false
                    showMaps = true
                },
                installedCount = installedMaps.size,
                folderName = store.exportFolderName ?: "none chosen yet",
                hasGoogleKey = store.key(Keys.Provider.GOOGLE) != null,
                onClose = { settings = false },
            )
        }
    }
}

@Composable
private fun MapSurface(
    store: Store,
    layer: MapLayer,
    generation: Int,
    points: List<Pair<Double, Double>>,
    fix: Fix?,
    line: List<Fix>,
    follow: Boolean,
    onCanvas: (VtmCanvas) -> Unit,
    onReady: () -> Unit,
) {
    // THE HAND-OVER, BEFORE THE BRANCH (18.9.2026).
    //
    // It sat after the Google branch's own `return`, so it ran for VTM and never once for Google:
    // the walk he was recording was handed to an engine that was not on the screen. Saving the
    // track and recalling it worked, because that is a different path — which is exactly why it
    // looked like the recording was being lost rather than misdelivered.
    //
    // Above the branch it runs for whichever engine is up, every time the line grows, a fix
    // arrives, the lock changes or a canvas appears.
    LaunchedEffect(generation, line.size, fix?.timeMs, follow) {
        Canvases.handOver(points, line, fix, follow)
    }

    // TWO ENGINES, ONE SCREEN (17.9.2026), which reverses the note that stood here — that Google's
    // SDK was gone with the key that was compiled in. It is back with a key that is locked to this
    // package and this signing certificate, because he asked for their vector map at their speed
    // and the Map Tiles API serves pictures of a map rather than the map.
    //
    // Google's renderer draws the Google views; VTM draws the file on the phone. Whichever is not
    // in use is not in the tree at all, so a walk with no signal carries nothing waiting to fail.
    Canvases.googleIsUp = layer.family == MapLayer.Family.GOOGLE
    if (layer.family == MapLayer.Family.GOOGLE) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val made = GoogleCanvas(context, store)
                GoogleHolder.canvas = made
                // AS THE OTHER ENGINE DOES (17.9.2026): say when the map is real, and the screen
                // puts his points and his route on it. Before this, whatever was drawn while
                // Google's map was still arriving went nowhere.
                made.onReady = { onReady() }
                made.onCreate()
                made.onResume()
                made.show(layer)
                made.showPosition(true)
                made.setRoutePoints(points)
                made.view
            },
            update = { GoogleHolder.canvas?.show(layer) },
            // A VIEW THAT LEAVES THE SCREEN MUST LET GO OF THE SCREEN (17.9.2026). Compose took
            // the Google map out of the tree when he switched back to the offline file, but
            // nobody told the MapView, so it kept its lifecycle and its GL surface — and VTM,
            // handed a surface that was still somebody else's, drew black. His offline map went
            // dark the moment Google's renderer had been shown once.
            onRelease = {
                // Where he was looking goes with him to the other engine.
                Canvases.rememberCamera(store)
                GoogleHolder.canvas?.onPause()
                GoogleHolder.canvas?.onDestroy()
                GoogleHolder.canvas = null
            },
        )
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // ONE ENGINE (16.9.2026). The CPU renderer is gone from this app: it was the reason
            // the map lagged, and keeping it as a fallback only kept the lag one setting away.
            val made = VtmCanvas(context, store)
            CanvasHolder.canvas = made
            onCanvas(made)
            // THE VIEW EXISTS NOW, AND NOT BEFORE. This is the whole bug of v6 to v10: the first
            // showLayer ran from a LaunchedEffect, which fires after composition but BEFORE
            // AndroidView builds its view, so CanvasHolder.canvas was still null, showLayer
            // returned at its first line, and no layer was ever put on the map. A blank white
            // screen at every zoom, with nothing said — and zooming could not fix what was never
            // there. The map is now told to draw itself from here, where the view is real.
            onReady()
            made.view
        },
        onRelease = {
            // The same courtesy in the other direction: VTM stops drawing when it leaves, and
            // says where it was looking first.
            Canvases.rememberCamera(store)
            CanvasHolder.canvas?.pause()
            CanvasHolder.canvas = null
        },
    )

    // NOTHING IS LEFT BEHIND WHEN THE VIEW CHANGES (17.9.2026).
    //
    // His words: changing the map is changing the VIEW, and everything laid over it is constant.
    // This effect was keyed on the walk and the lock alone, so when a new engine took the screen
    // it ran nothing — the track he was recording vanished, the lock stopped holding, the route
    // and the points went with them. It is keyed on the canvas too now, so every map that
    // appears inherits the whole state rather than an empty screen.

}

/**
 * THE NEXT MAP THE KEY TURNS TO: the next TICKED one, wherever it lives.
 *
 * This used to walk the FAMILIES and show each one's remembered style, which meant that ticking
 * three Thunderforest maps got him one of them and the other two were unreachable from the map
 * screen (15.9.2026). The list is the order, the ticks are the filter, and a family unticked
 * takes all of its maps out at once while remembering which were ticked inside it.
 *
 * A map that cannot draw — no key, no file — is stepped over too, and if nothing else can be
 * shown the key stays where it is rather than moving to a blank screen.
 */
private fun nextUsable(store: Store, current: MapLayer): MapLayer {
    // TWO MAPS, AND THE KEY TURNS BETWEEN THEM (17.9.2026). It used to walk every ticked view,
    // which is eight presses to come back to where he started. Which Google view it shows is
    // chosen in the settings; the key only decides offline or Google.
    if (current.family == MapLayer.Family.OFFLINE) {
        val google = Layers.byId(store.googleViewId)
        val hasKey = google.provider?.let { !store.key(it).isNullOrEmpty() } ?: false
        return if (hasKey) google else current
    }
    return Layers.OFFLINE
}

/**
 * PUT A LAYER ON THE MAP, fetching whatever it needs first.
 *
 * A layer that needs a key and has none says which key and where to put it, rather than drawing
 * an empty grid and leaving somebody to guess. Google needs a session as well, and the session is
 * made here — once, when the view is actually chosen, because Google bills per tile.
 */
suspend fun showLayer(store: Store, layer: MapLayer) {
    // GOOGLE'S MAPS ARE GOOGLE'S TO DRAW (17.9.2026). This routine asked the offline canvas for
    // every layer, so choosing a Google view — which their own renderer had already drawn
    // perfectly — ended with "the map view is not up yet" written across a working map. The
    // sentence was true of the canvas it asked and false about everything he could see.
    if (layer.family == MapLayer.Family.GOOGLE) {
        val google = GoogleHolder.canvas
        if (google == null) {
            // Not an error: the view is a frame or two behind the choice, and the canvas applies
            // whatever it was asked for as soon as it arrives.
            return
        }
        Trail.say(google.show(layer))
        return
    }

    val canvas = CanvasHolder.canvas
    if (canvas == null) {
        // This return used to be silent, which is how a blank screen kept its secret for five
        // versions (silent-failure.md). It cannot be silent again.
        Trail.say("The map view is not up yet")
        return
    }
    val problem = attempt(canvas, store, layer)
    if (problem == null) {
        // A layer that reports success and still shows nothing is the failure that cost five
        // versions of guessing. For the offline map the file can be asked directly, so it is:
        // if there is no data under the crosshair at this zoom, that is said now rather than
        // waiting for somebody to photograph a white screen.
        Trail.say(if (layer.kind == LayerKind.VECTOR_FILE) canvas.emptyHere() else null)
        return
    }
    // A MAP THAT CANNOT BE DRAWN DRAWS NOTHING (17.9.2026).
    //
    // It used to fall back to the offline map, and his screenshot showed why that was wrong: the
    // top line said Satellite, Google had refused the key, and the offline map was underneath. The
    // name and the ground disagreed, and only the name was legible at a glance — which is worse
    // than an empty screen, because an empty screen with a sentence on it is not ambiguous.
    CanvasHolder.canvas?.blank()
    Trail.say(problem)
}

/** One attempt at one layer. Returns null when it drew, or the reason it did not. */
private suspend fun attempt(canvas: VtmCanvas, store: Store, layer: MapLayer): String? {
    if (layer.kind == LayerKind.GOOGLE_TILES) {
        // THE RING, IN ORDER (17.9.2026): the key that worked last is tried first, and if it has
        // stopped working the next is tried before anybody is told the map is unavailable.
        if (store.keyring.isEmpty()) return Layers.missingKey(layer)
        val view = layer.googleView ?: MapLayer.GoogleView.NORMAL
        Trail.say("Asking Google for a session…")
        val result = GoogleTiles.sessionFromRing(view, store)
        val used = Keyring.best(store.keyring)?.value
        return result.token?.let { canvas.show(layer, session = it, key = used) } ?: result.problem
    }
    val key = layer.provider?.let { store.key(it) }
    if (layer.provider != null && key.isNullOrEmpty()) return Layers.missingKey(layer)
    return canvas.show(layer, key = key)
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
            Label("${stats.points} pts", ink, size = 13)
        }
    }
}

/**
 * A TICK, and it is a tick rather than two words. Dimmed when the group it belongs to is out of
 * the switcher: the map is still ticked, and it is still not in the toggle, and both of those
 * are true at once.
 */
@Composable
private fun Tick(checked: Boolean, onChange: (Boolean) -> Unit, dimmed: Boolean = false) {
    Box(
        Modifier
            .width(56.dp)
            .height(42.dp)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val side = size.minDimension
            val stroke = 1.5.dp.toPx()
            val ink = if (dimmed) Paint.Dim else Paint.Amber
            drawRoundRect(
                color = if (checked) ink else Paint.Dim,
                size = androidx.compose.ui.geometry.Size(side, side),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                style = if (checked) androidx.compose.ui.graphics.drawscope.Fill else Stroke(stroke),
            )
            if (checked) {
                // The mark inside, drawn rather than typed, so it cannot be a font that is missing.
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(side * 0.24f, side * 0.53f)
                    lineTo(side * 0.43f, side * 0.72f)
                    lineTo(side * 0.78f, side * 0.29f)
                }
                drawPath(path, Paint.Ground, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

/** The families, named as somebody would say them. */
private fun familyLabel(family: MapLayer.Family): String = when (family) {
    MapLayer.Family.OFFLINE -> "Offline map"
    MapLayer.Family.GOOGLE -> "Google"
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
            Label("now: $current", Paint.Dim, size = 11, align = TextAlign.Start)
            Label("new name", Paint.Amber, size = 11, align = TextAlign.Start)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paint.Veil)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) { Label("discard", Paint.Red, size = 14) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (text.isBlank()) Paint.Veil else Paint.Amber)
                        .clickable { if (text.isNotBlank()) onOk(text.trim()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Label("OK", if (text.isBlank()) Paint.Dim else Paint.Ground, size = 14)
                }
            }
        }
    }
}


/**
 * THE MAPS ON THIS PHONE, AND EVERY ONE IN THE WORLD THAT COULD BE.
 *
 * Baba, 16.9.2026: he started a 1.2 GB download and could not tell it was running, could not see
 * where it went, and could not tell afterwards which map the app was drawing. So this face says
 * all three at once — what is here, what is happening, and what can be fetched, continent by
 * continent, read from the mirror's own listing rather than from a list somebody typed.
 */
@Composable
private fun MapsFace(
    store: Store,
    installed: List<java.io.File>,
    folder: String,
    onUse: (java.io.File) -> Unit,
    onRemove: (java.io.File) -> Unit,
    onBrowse: (String) -> Unit,
    listing: List<OamIndex.Entry>,
    listingOf: String?,
    onFetch: (OamIndex.Entry) -> Unit,
    imageryDepth: Int,
    imageryCost: String,
    imageryHeld: String,
    onImageryDepth: (Int) -> Unit,
    onFetchImagery: () -> Unit,
    onForgetImagery: () -> Unit,
    onClose: () -> Unit,
) {
    val busy by OamDownload.state.collectAsState()
    val chosen = remember(UiTick.n) { store.offlineMapName }

    Box(Modifier.fillMaxSize().background(Paint.Ground)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(
                Modifier.fillMaxWidth().height(46.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("maps", Paint.Dim, size = 13)
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Paint.Veil).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Label("✕", Paint.Sand, size = 18) }
            }

            // WHAT IS HAPPENING, at the top, because a gigabyte is worth knowing about.
            // WHAT IS HAPPENING. Dark like everything else, with an amber outline round it —
            // his instruction, 17.9.2026: this is a dark application, so nothing is filled in a
            // light colour and black is never written on amber.
            if (busy != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paint.Card)
                        .border(1.5.dp, Paint.Amber, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Label(busy ?: "", Paint.Amber, size = 12, align = TextAlign.Start)
                }
            }

            Label("on this phone", Paint.Dim, size = 12, align = TextAlign.Start)
            if (installed.isEmpty()) {
                Label("None yet. Fetch one below.", Paint.Dim, size = 12, align = TextAlign.Start)
            }
            installed.forEach { file ->
                val inUse = file.name == chosen || (chosen.isBlank() && file == installed.first())
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paint.Card)
                        .then(
                            if (inUse) {
                                Modifier.border(1.5.dp, Paint.Amber, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().clickable { onUse(file) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Label(
                                text = file.name.removePrefix("oam-").removeSuffix(".map"),
                                colour = Paint.Sand,
                                size = 14,
                                align = TextAlign.Start,
                            )
                            Label(
                                text = "${file.length() / 1_000_000} MB" +
                                    if (inUse) " · drawing" else "",
                                colour = if (inUse) Paint.Amber else Paint.Dim,
                                size = 11,
                                align = TextAlign.Start,
                            )
                        }
                    }
                    Box(
                        Modifier.width(72.dp).fillMaxWidth().clickable { onRemove(file) },
                        contentAlignment = Alignment.Center,
                    ) { Label("delete", Paint.Red, size = 12) }
                }
            }
            Label("kept in $folder", Paint.Dim, size = 10, align = TextAlign.Start)

            // SMALLER MAPS, FIRST (17.9.2026). He asked whether Croatia could be cut out of the
            // Balkan file: it cannot, by this app or any other, so the answer offered instead is
            // a map that is only Croatia — a quarter of the size, and made for walking too.

            // SATELLITE HE KEEPS (17.9.2026). Google forbid storing theirs — their policy lists
            // offline use among the things their tiles may not be used for — so this is Sentinel-2
            // cloudless from EOX, CC BY 4.0, which may be kept. Ten metres to the pixel: forest
            // from clearing, ridge from valley, not cars.
            //
            // The area is what is on the screen. Drawing a rectangle with a finger on a map that
            // pans under it is a fiddle; moving the map until it shows what he wants is not.
            Label("satellite for offline use", Paint.Dim, size = 12, align = TextAlign.Start)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(14, 15, 16).forEach { depth ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Paint.Card)
                            .then(
                                if (depth == imageryDepth) {
                                    Modifier.border(1.5.dp, Paint.Amber, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onImageryDepth(depth) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(
                            text = when (depth) {
                                14 -> "coarse"
                                15 -> "closer"
                                else -> "closest"
                            },
                            colour = if (depth == imageryDepth) Paint.Amber else Paint.Sand,
                            size = 12,
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Paint.Card)
                    .clickable(onClick = onFetchImagery)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label("keep what is on the screen", Paint.Sand, size = 13, align = TextAlign.Start)
                    Label(imageryCost, Paint.Dim, size = 11, align = TextAlign.Start)
                }
                Label("fetch", Paint.Amber, size = 12)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Label("kept: $imageryHeld", Paint.Dim, size = 11, align = TextAlign.Start)
                if (imageryHeld != "none yet") {
                    Box(Modifier.clickable(onClick = onForgetImagery)) {
                        Label("delete all", Paint.Red, size = 11)
                    }
                }
            }
            Label(Imagery.ATTRIBUTION, Paint.Dim, size = 10, align = TextAlign.Start)

            Label("a smaller map, from elsewhere", Paint.Dim, size = 12, align = TextAlign.Start)
            OamIndex.ELSEWHERE.forEach { entry ->
                val here = installed.any { it.name == entry.mapName }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paint.Card)
                        .clickable { if (!here) onFetch(entry) }
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(
                        text = "Croatia, for walking — from your own GitHub",
                        colour = if (here) Paint.Dim else Paint.Sand,
                        size = 12,
                        align = TextAlign.Start,
                    )
                    Label(
                        text = if (here) "on the phone" else entry.sizeLabel,
                        colour = if (here) Paint.Green else Paint.Amber,
                        size = 11,
                    )
                }
            }

            Label("fetch a region", Paint.Dim, size = 12, align = TextAlign.Start)
            OamIndex.CONTINENTS.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (id, label) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Paint.Card)
                                .then(
                                    if (id == listingOf) {
                                        Modifier.border(1.5.dp, Paint.Amber, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onBrowse(id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Label(label, if (id == listingOf) Paint.Amber else Paint.Sand, size = 12)
                        }
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            listing.forEach { entry ->
                val here = installed.any { it.name == entry.mapName }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paint.Card)
                        .clickable { if (!here) onFetch(entry) }
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(entry.label, if (here) Paint.Dim else Paint.Sand, size = 12, align = TextAlign.Start)
                    Label(
                        text = if (here) "on the phone" else entry.sizeLabel,
                        colour = if (here) Paint.Green else Paint.Amber,
                        size = 11,
                    )
                }
            }
        }
    }
}


/**
 * SEARCHING FOR A PLACE (17.9.2026).
 *
 * He types a name, presses search, and each answer is a row: press it and that place becomes the
 * next point of the route, which is what he was panning the map to do by hand. The map goes there
 * too, so he can see what he has chosen before walking to it.
 *
 * Nothing is searched while he types. Each search is a billed request on his account, so it
 * happens when he presses and not before.
 */
@Composable
private fun PlacesFace(
    fix: Fix?,
    store: Store,
    onAdd: (Places.Place) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Places.Place>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    var looking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun go() {
        if (text.isBlank() || looking) return
        looking = true
        note = "Looking…"
        scope.launch {
            val (places, problem) = Places.search(text, fix, store)
            found = places
            note = problem ?: "${places.size} found"
            looking = false
        }
    }

    Box(Modifier.fillMaxSize().background(Paint.Ground)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(
                Modifier.fillMaxWidth().height(46.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("find a place", Paint.Dim, size = 13)
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Paint.Card).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Label("✕", Paint.Sand, size = 18) }
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Paint.Sand,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(Paint.AmberBright),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { go() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Paint.Card)
                    .border(1.5.dp, Paint.Amber, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .focusRequester(focus),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Paint.Card)
                    .border(1.5.dp, if (text.isBlank()) Paint.Dim else Paint.Amber, RoundedCornerShape(8.dp))
                    .clickable { go() },
                contentAlignment = Alignment.Center,
            ) {
                Label(
                    text = if (looking) "looking…" else "search",
                    colour = if (text.isBlank()) Paint.Dim else Paint.Amber,
                    size = 14,
                )
            }

            if (note != null) Label(note ?: "", Paint.Dim, size = 11, align = TextAlign.Start)

            found.forEach { place ->
                val away = fix?.let { Geo.distance(it.lat, it.lon, place.lat, place.lon) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Paint.Card)
                        .clickable { onAdd(place) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Label(place.name, Paint.Sand, size = 14, align = TextAlign.Start)
                        if (place.where.isNotBlank()) {
                            Label(place.where, Paint.Dim, size = 11, align = TextAlign.Start)
                        }
                    }
                    Label(
                        text = away?.let { Geo.formatDistance(it) } ?: "",
                        colour = Paint.Amber,
                        size = 11,
                    )
                }
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
            Row(
                Modifier.fillMaxWidth().height(46.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("tracks (gpx)", Paint.Dim, size = 13)
                Label("${tracks.size}", Paint.Dim, size = 11)
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Paint.Veil).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Label("✕", Paint.Sand, size = 18) }
            }

            // WHERE THEY ARE. This menu is the folder he chose, showing only GPX, so the folder's
            // own name is the first thing on it: a list of files nobody can find is a list.
            SettingRow("folder", folder, onChooseFolder)

            if (note != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paint.Veil)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Label(note ?: "", Paint.Amber, size = 12, align = TextAlign.Start)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                Label("line", Paint.Dim, size = 11)
                TRACK_COLOURS.forEach { option ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(option))
                            .clickable { onColour(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (option == colour) Label("✓", Paint.Ground, size = 12)
                    }
                }
            }
            Label(
                text = "hide the shown track",
                colour = Paint.Dim,
                size = 11,
                modifier = Modifier.clickable { onHide() },
            )

            if (tracks.isEmpty()) {
                Label(
                    text = "No GPX in that folder yet. The red circle starts a walk.",
                    colour = Paint.Dim,
                    size = 12,
                    align = TextAlign.Start,
                )
            }

            tracks.forEach { track ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paint.Veil)
                        .padding(GAP),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // The name he gave it, and the extension after it in the quieter ink: he
                    // renames a name, but what is on the disk is a file (15.9.2026).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(track.name, Paint.Sand, size = 13, align = TextAlign.Start)
                        Label(".${track.extension}", Paint.Dim, size = 11)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label(Tracks.formatSize(track.bytes), Paint.Dim, size = 11)
                        Label(
                            text = "show",
                            colour = Paint.Green,
                            size = 12,
                            modifier = Modifier.clickable { onShow(track) },
                        )
                        Label(
                            text = "rename",
                            colour = Paint.Amber,
                            size = 12,
                            modifier = Modifier.clickable { renaming = track },
                        )
                        Label(
                            text = if (confirming?.uri == track.uri) "sure? delete" else "delete",
                            colour = Paint.Red,
                            size = 12,
                            modifier = Modifier.clickable {
                                if (confirming?.uri == track.uri) {
                                    onDelete(track)
                                    confirming = null
                                } else {
                                    confirming = track
                                }
                            },
                        )
                    }
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


/**
 * A KEY FOR A ROUTE POINT. A tap drops it where the crosshair is; a long press opens the menu.
 * Lit while the point is on the map, so the row says what has been placed without a word on it.
 */
@Composable
private fun RowScope.PointKey(
    letter: String,
    placed: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .height(KEY)
            .clip(RoundedCornerShape(10.dp))
            .background(if (placed) Paint.Amber.copy(alpha = 0.3f) else Color.Transparent)
            .pointerInput(letter) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        // A pin with the next point's letter at its foot.
        Box(contentAlignment = Alignment.Center) {
            Glyph(R.drawable.ic_pin, if (placed) Paint.AmberBright else Paint.Sand, size = 26.dp)
            Label(letter, if (placed) Paint.AmberBright else Paint.Sand, size = 10, modifier = Modifier.padding(top = 36.dp))
        }
    }
}

/**
 * THE ROUTE MENU, opened by a long press on either point.
 *
 * Baba, 16.9.2026: A, B and Save in one menu; unticking a point deletes it from the map; Save
 * keeps the pair as a route in the same drawer as a recorded walk, with (AB) in its name so it
 * can be told from one that was walked.
 *
 * Route is the button for the routing that is not written yet. It is here, it says what it will
 * do, and it says plainly that it does not do it — a key that lies about being ready is worse
 * than a key that is missing (silent-failure.md).
 */
@Composable
private fun RouteMenu(
    store: Store,
    points: List<Pair<Double, Double>>,
    found: List<Routing.Option>,
    onAdd: () -> Unit,
    onFind: () -> Unit,
    onRemove: (Int) -> Unit,
    onRoute: (String, Int) -> Unit,
    onSaveOption: (Routing.Option) -> Unit,
    onHeights: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var options by remember { mutableIntStateOf(store.routeOptions) }
    var speed by remember { mutableStateOf(store.walkSpeedKmh) }
    var profile by remember { mutableStateOf(store.routeProfile) }
    var useGoogle by remember { mutableStateOf(store.useGoogleRouting) }
    val enough = points.size >= 2
    val straight = Route.straightMetres(points)

    Box(
        Modifier.fillMaxSize().background(Paint.Veil).safeDrawingPadding().padding(GAP),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Paint.Ground)
                .swallowTouches()
                .verticalScroll(rememberScrollState())
                .padding(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            // THE VISUAL LANGUAGE (27.9.2026, his screenshot of this menu: "BRouter and Google are
            // options. Route is a button, action button. In this user interface everything is the
            // same"). Now: the points; the second things as quiet actions, icon and verb; every
            // choice a bar with the chosen part raised; the router one flip; ROUTE the one solid
            // amber button. Close is the cross at the top, the far corner from save.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_route, Paint.Sand)
                Spacer(Modifier.width(10.dp))
                Label(
                    text = if (enough) "${Geo.formatDistance(straight)} straight" else "place at least two",
                    colour = if (enough) Paint.Sand else Paint.Dim,
                    size = 14,
                    align = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
                IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
            }

            points.forEachIndexed { index, at ->
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Paint.Card).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(Route.letterFor(index), Paint.Sand, size = 17)
                    Spacer(Modifier.width(14.dp))
                    Label("${Geo.formatLat(at.first)}  ${Geo.formatLon(at.second)}", Paint.Dim, size = 12, modifier = Modifier.weight(1f), align = TextAlign.Start)
                    IconAction(R.drawable.ic_trash, null, onClick = { onRemove(index) }, tint = Paint.Red)
                }
            }

            Column {
                Action("Add ${Route.letterFor(points.size)} here", R.drawable.ic_plus, onClick = onAdd, quiet = true, modifier = Modifier.fillMaxWidth())
                Action("Find a place", R.drawable.ic_search, onClick = onFind, quiet = true, modifier = Modifier.fillMaxWidth())
                if (enough) Action("Save as track", R.drawable.ic_save, onClick = onSave, quiet = true, modifier = Modifier.fillMaxWidth())
            }

            // WHICH ROUTER ANSWERS: one control, the one or the other (his words).
            val routers = listOf(false to "BRouter", true to "Google")
            Choice(
                parts = routers.map { (google, name) -> Part(name, if (google) R.drawable.ic_globe else R.drawable.ic_mountain) },
                chosen = if (useGoogle) 1 else 0,
                onChoose = { i ->
                    useGoogle = routers[i].first
                    store.useGoogleRouting = useGoogle
                },
                flip = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!useGoogle) {
                Choice(
                    parts = Routing.PROFILES.map { name ->
                        when (name) {
                            "trekking" -> Part("Trek", R.drawable.ic_walk)
                            "hiking-mountain" -> Part("Mountain", R.drawable.ic_mountain)
                            else -> Part("Shortest", R.drawable.ic_route)
                        }
                    },
                    chosen = Routing.PROFILES.indexOf(profile).coerceAtLeast(0),
                    onChoose = { i ->
                        profile = Routing.PROFILES[i]
                        store.routeProfile = profile
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Labelled("ways") {
                Choice(
                    parts = (1..5).map { Part("$it") },
                    chosen = options - 1,
                    onChoose = { i ->
                        options = i + 1
                        store.routeOptions = options
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            val speeds = listOf(3f, 4f, 5f, 6f)
            Labelled("km/h") {
                Choice(
                    parts = speeds.map { Part("${it.toInt()}") },
                    chosen = speeds.indexOf(speed).coerceAtLeast(0),
                    onChoose = { i ->
                        speed = speeds[i]
                        store.walkSpeedKmh = speed
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Action(
                verb = "Route",
                icon = R.drawable.ic_route,
                onClick = { onRoute(profile, options) },
                enabled = enough,
                trailing = if (enough) "$options" else null,
                modifier = Modifier.fillMaxWidth(),
            )

            // NO TURN-BY-TURN HERE (17.9.2026, his fifth telling): the way is drawn on the map.
            found.forEachIndexed { index, option ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paint.Card).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(option.colour)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Label(
                                "${Geo.formatDistance(option.metres.toDouble())}   ↑ ${option.climbM} m",
                                Paint.Sand, size = 15, align = TextAlign.Start,
                            )
                            Label(
                                Geo.formatDuration((option.metres / (speed * 1000.0 / 3600.0)).toLong() * 1000L),
                                Paint.Dim, size = 12, align = TextAlign.Start,
                            )
                        }
                        if (option.profile == null) IconAction(R.drawable.ic_chart, "ground", onClick = { onHeights(index) })
                        IconAction(R.drawable.ic_save, "save", onClick = { onSaveOption(option) })
                    }
                    if (option.profile != null) {
                        // THE GROUND UNDER THE ROUTE: the shape is the hill.
                        Label(option.profile.line(), Paint.Dim, size = 11, align = TextAlign.Start)
                        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(end = 10.dp)) {
                            val heights = option.profile.metres
                            if (heights.size < 2) return@Canvas
                            val low = heights.min()
                            val high = heights.max()
                            val span = (high - low).coerceAtLeast(1.0)
                            val step = size.width / (heights.size - 1)
                            val path = androidx.compose.ui.graphics.Path()
                            heights.forEachIndexed { i, metres ->
                                val x = i * step
                                val y = size.height - ((metres - low) / span * size.height).toFloat()
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, Color(option.colour), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(
            text = title.uppercase(),
            colour = Paint.Dim,
            size = 11,
            align = TextAlign.Start,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Paint.Card),
            content = content,
        )
    }
}

/** A row inside a section: what it is, and underneath, what it says. */
/** The hairline between two rows of one card, inset like the phone's own. */
@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Paint.Rule),
    )
}

@Composable
private fun Row2(
    title: String,
    value: String? = null,
    tint: Color = Paint.Amber,
    onPress: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(if (onPress != null) Modifier.clickable(onClick = onPress) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(title, Paint.Sand, size = 14, align = TextAlign.Start)
            if (value != null) Label(value, tint, size = 11, align = TextAlign.Start)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun SettingRow(title: String, state: String, onPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Paint.Veil)
            .clickable(onClick = onPress)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title, Paint.Sand, size = 12, align = TextAlign.Start)
        Label(state, Paint.Amber, size = 11)
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
) {
    Text(
        modifier = modifier,
        text = text,
        color = colour,
        fontSize = size.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = align,
        maxLines = 2,
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
    markedColour: Long?,
    defaultColour: Long,
    onClose: () -> Unit,
    onText: () -> Unit,
    onCopy: () -> Unit,
    onHighlight: (Long?) -> Unit,
) {
    val record = card.record
    var filter by remember(card.parcel.reference) { mutableStateOf("") }
    val rows = remember(record) { record?.let { Parcels.sheetRows(it) }.orEmpty() }
    val shown = remember(rows, filter) { Parcels.filterRows(rows, filter) }

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
                Label(card.parcel.number, Paint.Amber, size = 22, align = TextAlign.Start)
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
                if (filter.isEmpty()) Label("owner, address, anything", Paint.Dim, size = 14, align = TextAlign.Start)
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
                Label("${shown.size} of ${rows.size}", Paint.Dim, size = 12)
                IconAction(R.drawable.ic_close, null, onClick = { filter = "" }, tint = Paint.Dim)
            }
        }
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
                        Row(Modifier.fillMaxWidth()) {
                            Label(r.main, Paint.Sand, size = 15, align = TextAlign.Start, modifier = Modifier.weight(1f))
                            if (r.side.isNotBlank()) Label(r.side, Paint.AmberBright, size = 14)
                        }
                        if (r.under.isNotBlank()) Label(r.under, Paint.Dim, size = 12, align = TextAlign.Start)
                    }
                    if (rows.isEmpty()) Label("no possessors listed", Paint.Dim, size = 13, align = TextAlign.Start)
                    else if (shown.isEmpty()) Label("nothing on this sheet matches \"$filter\"", Paint.Dim, size = 13, align = TextAlign.Start)
                }
                card.problem != null -> Label(card.problem, Paint.Red, size = 13, align = TextAlign.Start)
                else -> Label("asking the cadastre for the owners…", Paint.Dim, size = 13, align = TextAlign.Start)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // HIGHLIGHT: a switch, and the colour it is in.
            Box(Modifier.clickable { onHighlight(if (markedColour == null) defaultColour else null) }.padding(end = 4.dp)) {
                SwitchMark(markedColour != null)
            }
            TRACK_COLOURS.forEach { option ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(option))
                        .clickable { onHighlight(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (option == markedColour) Label("✓", Paint.Ground, size = 13)
                }
            }
        }
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

/** What the K panel's search looks for (27.9.2026): *"a dropdown menu for number, owner, street."* */
private enum class SearchBy(val label: String, val hint: String) {
    NUMBER("number", "2450, 2449/3 — several at once"),
    SHEET("owner's sheet", "possession sheet number, from any parcel's sheet: every parcel that holder has"),
    STREET("street", "a street and house number; Google finds it"),
}

/**
 * THE CADASTRE'S PANEL: the switch, the search with its dropdown, the colour, and the parcels he
 * highlighted. Every search answers in the same list as Google's field and every answer becomes a
 * point on the map. By OWNER: the state publishes no search by a person's name (OSS's public
 * search takes a parcel or a possession sheet number, nothing else), so the owner's entry is his
 * possession sheet, whose number is on every parcel's sheet, and it lists everything he holds.
 */
@Composable
private fun ParcelsPanel(
    store: Store,
    on: Boolean,
    marks: List<Parcels.Mark>,
    colour: Long,
    onSwitch: (Boolean) -> Unit,
    onColour: (Long) -> Unit,
    onParcel: (Finding.Hit) -> Unit,
    onPlace: (Finding.Hit) -> Unit,
    onGo: (Parcels.Mark) -> Unit,
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
    val session = remember { java.util.UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val (lat, lon) = Canvases.centre() ?: return@LaunchedEffect
        here = runCatching { ParcelNet.municipalityFull(lat, lon) }.getOrNull()
        if (here == null) line = "the municipality under the map could not be read"
    }

    fun find() {
        val ko = here
        val words = text.trim()
        if (words.isEmpty()) { line = "type what to look for"; return }
        busy = true
        line = "searching…"
        hits = emptyList()
        scope.launch {
            val answer: Result<List<Finding.Hit>> = runCatching {
                when (by) {
                    SearchBy.NUMBER -> {
                        ko ?: error("move the map over the municipality first")
                        Parcels.numbers(words).ifEmpty { error("type a parcel number, like 2450 or 2449/3") }
                            .flatMap { ParcelNet.ossSearch(ko.third, number = it) }
                    }
                    SearchBy.SHEET -> {
                        ko ?: error("move the map over the municipality first")
                        val n = Regex("""\d+""").find(words)?.value ?: error("type the possession sheet's number")
                        ParcelNet.ossSearch(ko.third, sheet = n)
                    }
                    SearchBy.STREET -> {
                        val near = Canvases.centre()?.let { Fix(it.first, it.second, null, 0L, null) }
                        val query = ko?.let { "$words, ${it.second.lowercase().replaceFirstChar { c -> c.uppercase() }}" } ?: words
                        val (found, problem) = PlaceSearch.find(query, near, store, session)
                        if (found.isEmpty()) error(problem ?: "nothing found")
                        found
                    }
                }
            }
            busy = false
            hits = answer.getOrNull().orEmpty()
            line = answer.exceptionOrNull()?.message
                ?: if (hits.isEmpty()) "nothing found in k.o. ${ko?.second ?: ""}" else "${hits.size} found"
        }
    }

    Box(
        Modifier.fillMaxSize().background(Paint.Veil)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose)
            .safeDrawingPadding().padding(GAP),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Paint.Ground)
                .swallowTouches()
                .padding(GAP)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_parcels, Paint.Sand)
                Spacer(Modifier.width(10.dp))
                Label("Cadastre", Paint.Sand, size = 16, align = TextAlign.Start, modifier = Modifier.weight(1f))
                IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Paint.Card)) {
                Toggle("Parcels", null, on = on, onChange = onSwitch)
            }
            Label(
                text = here?.let { "k.o. ${it.second} · ${it.first}" } ?: "…",
                colour = Paint.Dim,
                size = 11,
                align = TextAlign.Start,
            )
            // THE DROPDOWN: what the field means.
            Box {
                // A SELECT: its value and the arrow that says it drops down.
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
                Action("Find", R.drawable.ic_search, onClick = { find() }, enabled = !busy)
            }
            Label(by.hint, Paint.Dim, size = 11, align = TextAlign.Start)
            line?.let { Label(it, Paint.Amber, size = 12, align = TextAlign.Start) }
            if (hits.isNotEmpty()) {
                ResultsList(hits, light = false) { hit ->
                    if (hit.source == Finding.Source.PARCEL) onParcel(hit) else onPlace(hit)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TRACK_COLOURS.forEach { option ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(option))
                            .clickable { onColour(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (option == colour) Label("✓", Paint.Ground, size = 12)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("HIGHLIGHTED  ${marks.size}", Paint.Amber, size = 12, align = TextAlign.Start, modifier = Modifier.weight(1f))
                if (marks.isNotEmpty()) {
                    var sure by remember { mutableStateOf(false) }
                    Action(
                        verb = if (sure) "again: all" else "all",
                        icon = R.drawable.ic_trash,
                        onClick = { if (sure) { onClearAll(); sure = false } else sure = true },
                        quiet = true,
                        danger = true,
                    )
                }
            }
            if (marks.isEmpty()) {
                Label("None yet. Tap a parcel, tap it again, tick highlight or pick a colour.", Paint.Dim, size = 12, align = TextAlign.Start)
            }
            marks.forEach { mark ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paint.Card)
                        .clickable { onGo(mark) }
                        .padding(horizontal = GAP, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(mark.colour)))
                    Label(mark.number, Paint.Sand, size = 15, align = TextAlign.Start)
                    Label(
                        text = "k.o. ${mark.reference.substringBefore('-')}" + if (mark.rings.isEmpty()) " · outline coming" else "",
                        colour = Paint.Dim,
                        size = 11,
                        align = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(R.drawable.ic_trash, null, onClick = { onRemove(mark) }, tint = Paint.Red)
                }
            }
        }
    }
}

/**
 * THE SEARCH FIELD ON GOOGLE'S MAP, AS GOOGLE MAPS HAS IT (27.9.2026). Suggestions while he types
 * (autocomplete, billed once per session), the full list when he presses search, nearest first
 * with the distance under each pin; a tap takes the map there and drops the cyan pin.
 */
@Composable
private fun GoogleSearchBar(store: Store, near: Fix?, onPlace: (Finding.Hit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Finding.Hit>>(emptyList()) }
    var line by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var typed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun from(): Fix? = near ?: Canvases.centre()?.let { Fix(it.first, it.second, null, 0L, null) }

    // As he types: a moment after the last key, autocomplete only.
    LaunchedEffect(typed) {
        if (typed == 0 || text.trim().length < 3) return@LaunchedEffect
        delay(400)
        val (found, _) = PlaceSearch.find(text.trim(), from(), store, session, full = false)
        hits = found
    }

    fun go() {
        if (text.isBlank()) return
        keyboard?.hide()
        line = "searching…"
        scope.launch {
            val (found, problem) = PlaceSearch.find(text.trim(), from(), store, session, full = true)
            hits = found
            line = problem
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 72.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(R.drawable.ic_search, Color(0xFF5F6368), size = 22.dp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Search here", color = Color(0xFF70757A), fontSize = 16.sp)
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        typed += 1
                        if (it.isBlank()) hits = emptyList()
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF202124), fontSize = 16.sp),
                    cursorBrush = SolidColor(Color(0xFF1A73E8)),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (text.isNotEmpty()) {
                IconAction(
                    R.drawable.ic_close, null,
                    onClick = {
                        text = ""
                        hits = emptyList()
                        line = null
                        ParcelsShown.pin = null
                        Canvases.refreshParcels()
                    },
                    tint = Color(0xFF5F6368),
                )
            }
        }
        line?.let {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(it, color = Color(0xFF3C4043), fontSize = 14.sp)
            }
        }
        if (hits.isNotEmpty()) {
            ResultsList(hits, light = true) { hit ->
                keyboard?.hide()
                scope.launch {
                    val found = PlaceSearch.locate(hit, store, session)
                    if (found == null) {
                        line = "Google did not say where that is"
                        return@launch
                    }
                    text = hit.title
                    hits = emptyList()
                    line = null
                    session = java.util.UUID.randomUUID().toString()
                    onPlace(found)
                }
            }
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
