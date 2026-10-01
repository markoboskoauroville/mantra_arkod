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
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    // v8: Moje čestice as files, in and out.
    onImportMarks: () -> Unit,
    onShareMarks: (MarkFile.Group, List<Parcels.Mark>) -> Unit,
) {
    var layer by remember { mutableStateOf(Layers.byId(store.layerId)) }
    var settings by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(store.lastZoom) }
    // A counter, not a flag: every canvas that appears is told to draw (17.9.2026).
    var ready by remember { mutableIntStateOf(0) }
    // LOCKED TO THE MIDDLE (15.9.2026). One press centres; two in a second hold it there.
    var follow by remember { mutableStateOf(false) }
    var lastCentreTap by remember { mutableLongStateOf(0L) }
    // THE PARCELS KEY AND ITS VIEW (29.9.2026, v3): a tap hides or shows the cadastre, a long
    // press opens Parcel view, where "only my parcels" and the parcel field are chosen.
    var cadastreOn by remember { mutableStateOf(store.cadastreOn) }
    var onlyMine by remember { mutableStateOf(store.onlyMine) }
    var parcelSearchOn by remember { mutableStateOf(store.parcelSearchOn) }
    var parcelView by remember { mutableStateOf(false) }
    // GOO's LONG PRESS (1.10.2026, row 40): Google's four views, chosen in one tap
    var googleViews by remember { mutableStateOf(false) }
    // THE MIDDLE OF THE MAP, which the top line shows (v3): where he looks, not where he is.
    var centre by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // IMENIK (v3): the holders and owners of every sheet opened on this phone.
    var book by remember { mutableStateOf(store.ownerBook) }
    // v5 (30.9.2026): the state's lines as he styled them, and the parcel caches with their own.
    var lines by remember { mutableStateOf(store.lines) }
    var ownLines by remember { mutableStateOf(store.ownLines) }
    val caches by ParcelCaches.all.collectAsState()
    val harvest by ParcelCaches.progress.collectAsState()
    var showCaches by remember { mutableStateOf(false) }
    var namingCache by remember { mutableStateOf<ParcelCache.Box?>(null) }

    val fix by Trail.fix.collectAsState()
    val stats by Trail.stats.collectAsState()
    val recordingSince by Trail.recordingSince.collectAsState()
    val paused by Trail.paused.collectAsState()
    val note by Trail.note.collectAsState()
    val net by Net.line.collectAsState()
    val kept by ArkodPrefetch.state.collectAsState()
    // THE CACHE KEY (v12): *"app should actually have cache action button so i can enable or disable
    // it while i'm going through the map."* On: tiles fetched ahead (as before) and the sniffer reading
    // the sheets round the map. One switch, the same as "Cache in the background" in the settings.
    var cacheOn by remember { mutableStateOf(store.prefetch) }
    val sniffed by Sniffer.tally.collectAsState()
    val services by Services.health.collectAsState()
    // FLY-THROUGH SCANNING (v16): the airplane, what it looks for, and what it found.
    val flying by Flyer.state.collectAsState()
    var askFly by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<Parcels.Parcel>>(emptyList()) }
    // FULL SCREEN (v15, FEATURES row 27, from the web app): the map alone with one key to come back.
    // Not the Trail's tap in the middle, which v1 removed at his word: a key.
    var full by remember { mutableStateOf(false) }
    val hostView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(full) {
        var c: android.content.Context? = hostView.context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.window?.let { w ->
            val bars = androidx.core.view.WindowCompat.getInsetsController(w, hostView)
            if (full) {
                bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = full) { full = false }
    // THE HELP (v18), over everything; Back closes it.
    var help by remember { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = help != null) { help = null }
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
    // THE GROUPS OF MOJE ČESTICE (v8): his own, and one for every file opened, each in its look.
    var groups by remember { mutableStateOf(store.markGroups) }
    val incoming by MarkImports.incoming.collectAsState()
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

    /** Every name on a sheet that opened goes into Imenik, replacing what that parcel said before. */
    fun enterInBook(parcel: Parcels.Parcel, record: Parcels.Record?, folios: List<Parcels.Folio>) {
        val entries = OwnerBook.entriesOf(parcel, record, folios)
        if (entries.isEmpty()) return
        book = OwnerBook.add(book, entries)
        store.ownerBook = book
    }

    LaunchedEffect(cacheOn) {
        Sniffer.criteria = Sniff.criteria(store.cacheKeywords)
        Sniffer.onSheet = { p, r, f -> scope.launch { enterInBook(p, r, f) } }
        if (cacheOn) Sniffer.start() else Sniffer.stop()
    }

    suspend fun openCard(parcel: Parcels.Parcel) {
        card = ParcelCard(parcel)
        val answer = runCatching { ParcelNet.record(parcel.id) }
        val keptSince = ParcelNet.kept
        val keptFar = ParcelNet.keptFar
        if (card?.parcel?.id != parcel.id) return
        card = ParcelCard(
            parcel,
            record = answer.getOrNull(),
            problem = answer.exceptionOrNull()?.let { "vlasnici se nisu mogli pročitati: ${it.message ?: it.javaClass.simpleName}" },
            keptSince = keptSince,
            keptFar = keptFar && keptSince != null,
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
        enterInBook(parcel, record, folios.getOrNull().orEmpty())
        // THE SNIFFER FOLLOWS (v12): the other parcels of this owner sheet, read in the background.
        if (cacheOn) Sniffer.follow(record, folios.getOrNull().orEmpty())
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
        if (found.isNotEmpty()) enterInBook(parcel, card?.record, found)
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

    fun showFound(next: List<Parcels.Parcel>) {
        found = next
        ParcelsShown.found = next.filter { it.rings.isNotEmpty() }
            .map { Parcels.markOf(it, FOUND_COLOUR, Parcels.LineStyle.SOLID).copy(weight = ParcelStyle.Weight.BOLD) }
        Canvases.refreshParcels()
    }

    LaunchedEffect(Unit) {
        Flyer.onFound = { p, why ->
            scope.launch {
                showFound(found.filterNot { it.id == p.id } + p)
                if (p.rings.isNotEmpty()) select(p)
                Trail.say("✈ found ${p.number}: $why · selected${if (p.rings.isEmpty()) " (outline not yet)" else ""}")
            }
        }
        // THE SERVICE LOG (v16): kept across restarts, and every change said on the map at once.
        Services.restore(Services.decodeLog(store.serviceLog))
        val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)
        Services.onChange = { e ->
            scope.launch {
                store.serviceLog = Services.encodeLog(Services.log.value)
                Trail.say(Services.said(e) { clock.format(java.util.Date(it)) })
                // MAKE IT WORK (v19): a service back, what waited for it is done at once.
                if (e.online) when (e.service) {
                    Services.Service.WMS -> { Canvases.reloadCadastre(); Trail.say("${clock.format(java.util.Date(e.at))} WMS back online · the ARKOD layer is drawn again") }
                    Services.Service.WFS -> { Trail.say("${clock.format(java.util.Date(e.at))} WFS back online · missing outlines asked"); fillShapes() }
                    Services.Service.OSS, Services.Service.ZK -> card?.takeIf { it.problem != null || it.folioProblem != null }?.let { c ->
                        Trail.say("${clock.format(java.util.Date(e.at))} ${e.service.short} back online · the sheet is read again")
                        openCard(c.parcel)
                    }
                    else -> Unit
                }
            }
        }
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
        // FROM MOJE ČESTICE (v11): the outline is on the phone, so the map goes there at once.
        marks.firstOrNull { it.reference == ref && it.rings.isNotEmpty() }?.let { m ->
            val p = Parcels.Parcel(m.id, m.number, m.reference, null, m.rings)
            Trail.say(null)
            select(p)
            val (lat, lon) = p.middle
            ParcelsShown.pin = lat to lon
            Canvases.refreshParcels()
            Canvases.goTo(lat, lon, 18)
            openCard(p)
            return
        }
        // FROM A CACHE (v5): the outline is on the phone, so the map goes there at once.
        ParcelCache.byReference(caches, ref)?.let { item ->
            val p = item.parcel()
            Trail.say(null)
            select(p)
            val (lat, lon) = item.middle
            ParcelsShown.pin = lat to lon
            Canvases.refreshParcels()
            Canvases.goTo(lat, lon, 18)
            openCard(p)
            return
        }
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

    /**
     * A NUMBER ON A SHEET, TAPPED (v10): *"When I click it, it just jumps to the map and outlines
     * that parcel."* Its outline from the phone when a cache or Moje čestice has it, else its id from
     * OSS (a quarter of a second) and its outline from the state's WFS (slow, and sometimes down).
     */
    suspend fun goToNumber(municipality: String, number: String) {
        val ref = "$municipality-$number"
        val known = ParcelCache.byReference(caches, ref)?.parcel()
            ?: marks.firstOrNull { it.reference == ref && it.rings.isNotEmpty() }?.let { Parcels.Parcel(it.id, it.number, it.reference, null, it.rings) }
        if (known != null) {
            select(known)
            val (lat, lon) = known.middle
            Canvases.goTo(lat, lon, 18)
            Trail.say("$number · dodirnite ponovno za list")
            return
        }
        Trail.say("tražim $number na karti…")
        val found = runCatching { ParcelNet.find(municipality, listOf(number)) }.getOrNull()?.firstOrNull()
        if (found == null) {
            Trail.say("$number: katastar ne zna tu česticu u k.o. $municipality")
            return
        }
        val shape = runCatching { ParcelNet.shapes(listOf(ref)) }.getOrNull()?.firstOrNull { it.reference == ref }
        if (shape == null || shape.rings.isEmpty()) {
            Trail.say("$number: državna usluga karte ne odgovara; obris kasnije")
            return
        }
        val p = found.copy(rings = shape.rings, areaM2 = shape.areaM2)
        select(p)
        val (lat, lon) = p.middle
        Canvases.goTo(lat, lon, 18)
        Trail.say("$number · dodirnite ponovno za list")
    }

    /**
     * A KEPT PARCEL FROM THE SETTINGS' LIST (v13): its outline and id are on the phone, so the map
     * goes there and the sheet opens from the phone, with no signal. Else as a number on a sheet.
     */
    suspend fun openKept(municipality: String, number: String) {
        val ref = "$municipality-$number"
        val p = ParcelQuery.decodeShape(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ArkodCache.shape(ref) })
            ?: marks.firstOrNull { it.reference == ref && it.rings.isNotEmpty() }?.let { Parcels.Parcel(it.id, it.number, it.reference, null, it.rings) }
        if (p == null || p.id <= 0L) { goToNumber(municipality, number); return }
        select(p)
        val (lat, lon) = p.middle
        ParcelsShown.pin = lat to lon
        Canvases.refreshParcels()
        Canvases.goTo(lat, lon, 18)
        openCard(p)
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
        val (stateShown, mineShown) = Parcels.visibility(cadastreOn, onlyMine)
        // EVEN WITH THE LAYER HIDDEN A TAP OUTLINES THE PARCEL UNDER IT (v5): only the outline is
        // drawn, and the line says the layer is hidden.
        val hidden = if (stateShown) "" else " · ARKOD layer hidden"
        val current = selected
        if (current != null && current.rings.any { Parcels.contains(it, lat, lon) }) {
            Trail.say(null)
            openCard(current)
            return
        }
        marks.takeIf { mineShown }?.firstOrNull { m -> m.rings.any { Parcels.contains(it, lat, lon) } }?.let { m ->
            card = null
            select(Parcels.Parcel(m.id, m.number, m.reference, null, m.rings))
            Trail.say("${m.number}${if (m.name.isNotBlank()) " · ${m.name}" else ""} · dodirnite ponovno za list")
            return
        }
        // A PARCEL FLY-THROUGH FOUND (v16): selected at once, its sheet on the next tap.
        found.firstOrNull { p -> p.rings.any { Parcels.contains(it, lat, lon) } }?.let { p ->
            card = null
            select(p)
            Trail.say("${p.number} · found by ✈ · dodirnite ponovno za list")
            return
        }
        // A CACHED PARCEL IS FOUND ON THE PHONE (v5): at once, and with no signal.
        ParcelCache.at(caches, lat, lon)?.let { item ->
            card = null
            select(item.parcel())
            Trail.say("${item.number}$hidden · dodirnite ponovno za list")
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
        Trail.say("${parcel.number}$hidden · dodirnite ponovno za list")
    }

    /** One of the three maps, chosen by its key. The choice is remembered for the next run. */
    fun choose(picked: MapLayer) {
        // The key of the map already up asks it to load again, and changes nothing else (B2, v1).
        if (picked.id == layer.id) {
            CanvasHolder.canvas?.load()
            return
        }
        layer = picked
        store.layerId = picked.id
        scope.launch { showLayer(store, picked) }
    }

    LaunchedEffect(Unit) {
        Parcels.visibility(store.cadastreOn, store.onlyMine).let { (state, mine) ->
            ParcelsShown.on = state
            ParcelsShown.mineOn = mine
        }
        ParcelsShown.marks = store.parcelMarks
        ParcelsShown.onTap = { lat, lon -> scope.launch { tapped(lat, lon) } }
        fillShapes()
    }

    LaunchedEffect(myParcels) { if (myParcels) fillShapes() }

    fun setGroups(next: List<MarkFile.Group>) {
        groups = next
        store.markGroups = next
        ParcelsShown.hiddenGroups = next.filter { !it.visible }.map { it.name }.toSet()
        Canvases.refreshParcels()
    }

    LaunchedEffect(Unit) { ParcelsShown.hiddenGroups = groups.filter { !it.visible }.map { it.name }.toSet() }

    // A FILE OPENED (v8), from the picker or sent to the app: its parcels become a group.
    LaunchedEffect(incoming) {
        val read = incoming ?: return@LaunchedEffect
        val (m, g) = MarkFile.importInto(marks, groups, read)
        setMarks(m)
        setGroups(g)
        MarkImports.done()
        Trail.say("Uvezeno: ${read.group.name} · ${read.marks.size} čestica")
        myParcels = true
        fillShapes()
    }

    // WHAT THE PARCELS KEY AND PARCEL VIEW SAY IS DRAWN, told to the map whenever either changes.
    LaunchedEffect(cadastreOn, onlyMine, ready) {
        val (state, mine) = Parcels.visibility(cadastreOn, onlyMine)
        ParcelsShown.on = state
        ParcelsShown.mineOn = mine
        Canvases.refreshParcels()
    }

    // THE LOOK OF THE ARKOD LAYER (v5): his lines and the caches, told to the map when either changes.
    LaunchedEffect(lines, ownLines, caches, ready) {
        ParcelsShown.lines = lines
        ParcelsShown.ownLines = ownLines
        ParcelsShown.caches = caches
        Canvases.refreshParcels()
    }

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

    // THE ZOOM, THE MIDDLE, AND WHERE THE MAP HAS SETTLED, four times a second. A place where the map rests is
    // where the cadastre is fetched ahead (29.9.2026). Bounded by the composition.
    // THE LIGHT CHECK (v14): every minute, a service not heard from for three minutes is asked once.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            Services.Service.values().filter { it != Services.Service.GOOGLE }
                .filter { Services.due(Services.health.value[it], now, 180_000L) }
                .forEach { s -> scope.launch { ParcelNet.check(s) } }
            delay(60_000)
        }
    }

    LaunchedEffect(Unit) {
        var restingAt: Pair<Double, Double>? = null
        var restingFor = 0
        while (true) {
            val now = Canvases.currentZoom()
            if (now != zoom && now > 0) zoom = now
            val here = Canvases.centre()
            if (here != null && here != centre) centre = here
            if (here != null && here == restingAt) {
                restingFor += 1
                if (restingFor == 4 && store.prefetch) ArkodPrefetch.viewSettled(here.first, here.second, now)
                if (restingFor == 4 && store.prefetch) Sniffer.viewSettled(here.first, here.second, now)
                if (restingFor == 3) Flyer.viewSettled(here.first, here.second, now)
            } else {
                restingAt = here
                restingFor = 0
            }
            delay(250)
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

        if (!full) Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).safeDrawingPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FixLine(centre, fix, zoom, layer)
            // A LIGHT FOR EVERY SERVICE (v14), under the line that says where the map is; a tap
            // opens the settings, where each is described.
            ServiceLights(services, googleKey = keyring.isNotEmpty()) { settings = true }
            // TWO FIELDS ON THE MAP (29.9.2026, v3): Google's on all three maps when there is a
            // key, the answer pinned on whichever map is up; the parcel field under it.
            if (googleUsable) PlaceField(store) { hit -> showPlace(hit) }
            if (parcelSearchOn) ParcelField(store, book, caches, marks) { hit -> scope.launch { showFoundParcel(hit) } }
        }

        // IN FULL SCREEN, ONLY THE KEY THAT BRINGS EVERYTHING BACK (v15), where the full-screen key was.
        if (full) Box(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(8.dp)) {
            RoundKey(R.drawable.ic_fullscreen_exit, on = false, onClick = { full = false })
        }

        if (!full) Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeDrawingPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // A line with nothing in it takes no room at all.
            val moving = listOfNotNull(net, kept).joinToString(" · ").ifBlank { null }
            if (moving != null) StatusLine(moving)
            if (cacheOn && sniffed.busy) StatusLine(Sniff.line(sniffed, Sniffer.criteria))
            // FLY-THROUGH, SAID AT EVERY STEP (v16): "scanning 14/25 · 9 sheets read · found 1".
            if (flying.on) StatusLine(Fly.line(flying))
            // THE CACHE KEY (v12): *"app should actually have cache action button so i can enable or
            // disable it while i'm going through the map."* Round, on its own at the right, over the
            // key row (which holds nine at most); lit while the app caches in the background. A long
            // press opens the settings, where the size and the cache criteria are, at the top.
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                // FLY-THROUGH SCANNING (v16): the small airplane. Off: asks what to look for, then
                // flies. On: lands (stops) and the found ones stay drawn until the next flight.
                // A long press asks for other words.
                RoundKey(R.drawable.ic_plane, on = flying.on, onClick = {
                    if (flying.on) { Flyer.stop(); Trail.say("✈ landed · found ${found.size}") } else askFly = true
                }, onLongClick = { askFly = true })
                // FULL SCREEN (v15): the map alone; the same place brings everything back.
                RoundKey(R.drawable.ic_fullscreen, on = false, onClick = { full = true; Trail.say(null) })
                CacheKey(
                    on = cacheOn,
                    onClick = {
                        cacheOn = !cacheOn
                        store.prefetch = cacheOn
                        Trail.say(if (cacheOn) "cache on: reading ahead in the background" else "cache off: only what you open is kept")
                    },
                    onLongClick = { settings = true },
                )
            }
            // The download goes on while he looks at another map; its line stays with him.
            if (download?.running == true && layer.kind != LayerKind.VECTOR_FILE) {
                StatusLine("offline map: " + (download?.progress?.let { MapDownload.line(it) } ?: "starting…"))
            }
            // THE CACHE BEING FILLED, said as it goes (v5: "with verbose status showing what's going on").
            harvest?.let { h ->
                HarvestLine(h, onStop = { ParcelCaches.cancel() }, onClose = { ParcelCaches.dismiss() })
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
                    icon = R.drawable.ic_google,
                    up = layer.family == MapLayer.Family.GOOGLE,
                    onClick = { choose(Layers.byId(store.googleViewId)) },
                    // Marko, 1.10.2026: "Long press on the action bar for Google Maps ... open different
                    // options for different views ... and then when I choose the view it just closes."
                    onLongClick = { googleViews = true },
                )
                MapKey(
                    word = "OSM",
                    icon = R.drawable.ic_globe,
                    up = layer.family == MapLayer.Family.OSM,
                    onClick = { choose(Layers.OSM) },
                )
                // THE SHOW/HIDE ARKOD LAYER KEY (v3; named so at his word, 30.9.2026): *"It should
                // hide parcels overlay completely from the map. And long press on it ... open the
                // settings dialog."* Lit while the state's parcels are drawn. v5: with "Only Moje
                // čestice" on, a tap brings every parcel back and turns that off: he could not get
                // them back from the key (30.9.2026).
                Key(
                    glyph = "▦",
                    lit = Parcels.visibility(cadastreOn, onlyMine).first,
                    onClick = {
                        if (onlyMine) {
                            onlyMine = false
                            store.onlyMine = false
                            cadastreOn = true
                        } else {
                            cadastreOn = !cadastreOn
                        }
                        store.cadastreOn = cadastreOn
                        Trail.say(if (cadastreOn) "ARKOD layer shown" else "ARKOD layer hidden · long press: Parcel view")
                    },
                    onLongClick = { parcelView = true },
                    icon = R.drawable.ic_parcels,
                )
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
                onNumber = { number -> card = null; scope.launch { goToNumber(shown.parcel.municipality, number) } },
                onFile = {
                    // ONE PARCEL AS A FILE (v10): sent like a group of Moje čestice, opened the same way.
                    val rings = mark?.rings?.takeIf { it.isNotEmpty() } ?: shown.parcel.rings.takeIf { it.isNotEmpty() }
                        ?: selected?.takeIf { it.reference == shown.parcel.reference }?.rings
                        ?: ParcelCache.byReference(caches, shown.parcel.reference)?.rings.orEmpty()
                    val name = "Čestica ${shown.parcel.number.replace('/', '-')} k.o. ${shown.record?.municipality ?: shown.parcel.municipality}"
                    val group = MarkFile.Group(name, parcelColour, parcelStyle)
                    val one = Parcels.markOf(shown.parcel.copy(rings = rings), parcelColour, parcelStyle).copy(group = name, name = mark?.name.orEmpty())
                    onShareMarks(group, listOf(one))
                },
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
                groups = groups,
                onGroup = { g ->
                    setGroups(groups.filterNot { it.name == g.name } + g)
                    setMarks(MarkFile.restyle(marks, g))
                },
                onDeleteGroup = { name ->
                    setMarks(marks.filterNot { it.group == name })
                    setGroups(groups.filterNot { it.name == name })
                },
                onShareGroup = { name -> onShareMarks(MarkFile.groupOf(name, groups, marks), marks.filter { it.group == name }) },
                onImport = onImportMarks,
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
        // composition). The compass is gone (v3: "Compass is extra, no needed").
        LaunchedEffect(ready) {
            while (true) {
                if (ready > 0) Canvases.setHeading(sensors.heading())
                delay(200)
            }
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

        if (googleViews) {
            GoogleViews(
                chosen = Layers.GOOGLE_ALL.indexOfFirst { it.id == store.googleViewId }.coerceAtLeast(0),
                onChoose = { i ->
                    val picked = Layers.GOOGLE_ALL[i]
                    store.googleViewId = picked.id
                    googleViews = false
                    choose(picked)
                },
                onDismiss = { googleViews = false },
            )
        }

        if (parcelView) {
            ParcelViewFace(
                cadastreOn = cadastreOn,
                // A switch here makes the key's last line stale ("ARKOD layer hidden"): it goes.
                onCadastre = { cadastreOn = it; store.cadastreOn = it; Trail.say(null) },
                lines = lines,
                onLines = { lines = it; store.lines = it },
                ownLines = ownLines,
                onOwnLines = { ownLines = it; store.ownLines = it },
                cacheCount = caches.size,
                caching = harvest?.finished == false,
                onCacheView = {
                    val box = CanvasHolder.canvas?.viewBox()
                    when {
                        box == null -> Trail.say("The map is not ready yet")
                        Canvases.currentZoom() < 13 -> Trail.say("Zoom in: at this zoom the view is a county, not a place")
                        else -> { parcelView = false; namingCache = box }
                    }
                },
                onCaches = { parcelView = false; showCaches = true },
                onlyMine = onlyMine,
                onOnlyMine = { onlyMine = it; store.onlyMine = it; Trail.say(null) },
                parcelSearchOn = parcelSearchOn,
                onParcelSearch = { parcelSearchOn = it; store.parcelSearchOn = it },
                myParcelCount = marks.size,
                onMyParcels = { parcelView = false; myParcels = true },
                bookSize = book.size,
                onClearBook = { book = emptyList(); store.ownerBook = emptyList() },
                onClose = { parcelView = false },
            )
        }

        if (askFly) {
            NameBox(
                current = store.flyQuery.ifBlank { "jaša" },
                title = "✈ fly-through: what to look for (a name, a place, a land use; one per comma)",
                fallbackToCurrent = true,
                onCancel = { askFly = false },
                onOk = { q ->
                    askFly = false
                    store.flyQuery = q
                    showFound(emptyList())
                    Flyer.start(q)
                    // Where the map is now is scanned at once, not only after the next move.
                    Canvases.centre()?.let { (la, lo) -> Flyer.viewSettled(la, lo, Canvases.currentZoom()) }
                    Trail.say("✈ flying: \"$q\" · scanning where the map rests")
                },
            )
        }

        namingCache?.let { box ->
            // The k.o. under the middle names the cache until he types his own.
            var place by remember(box) { mutableStateOf("") }
            LaunchedEffect(box) {
                place = runCatching { ParcelNet.municipality(box.middle.first, box.middle.second)?.second }.getOrNull().orEmpty()
            }
            NameBox(
                current = ParcelCache.defaultName(place, java.text.SimpleDateFormat("d.M.yyyy", java.util.Locale.ROOT).format(java.util.Date())),
                title = "name of the cache",
                fallbackToCurrent = true,
                onCancel = { namingCache = null },
                onOk = { name ->
                    namingCache = null
                    ParcelCaches.start(
                        name = name,
                        box = box,
                        zoom = Canvases.currentZoom(),
                        colour = ParcelCache.COLOURS[caches.size % ParcelCache.COLOURS.size],
                        owners = store.cacheOwners,
                    )
                },
            )
        }

        if (showCaches) {
            ParcelCachesFace(
                caches = caches,
                store = store,
                onGo = { c ->
                    showCaches = false
                    val (lat, lon) = c.info.box.middle
                    Canvases.goTo(lat, lon, c.info.zoom.coerceIn(14, 18))
                },
                onClose = { showCaches = false },
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
                onParcelView = {
                    settings = false
                    parcelView = true
                },
                cacheCount = caches.size,
                onCaches = {
                    settings = false
                    showCaches = true
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
                folderName = store.exportFolderName ?: "not chosen yet",
                onKeptParcel = { m, n -> settings = false; scope.launch { openKept(m, n) } },
                onHelp = { lang -> settings = false; help = lang },
                onCheckServices = { Services.Service.values().filter { it != Services.Service.GOOGLE }.forEach { s -> scope.launch { ParcelNet.check(s) } } },
                onClose = {
                    settings = false
                    // The switch and the keywords may have changed there.
                    cacheOn = store.prefetch
                    Sniffer.criteria = Sniff.criteria(store.cacheKeywords)
                },
            )
        }

        help?.let { lang -> HelpFace(lang) { help = null } }
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
        if (result.token == null && Layers.eeaRefusal(result.problem) &&
            (view == MapLayer.GoogleView.SATELLITE || view == MapLayer.GoogleView.HYBRID)
        ) {
            // GOOGLE REFUSES SATELLITE IN THE EU (1.10.2026): the aerial photograph in its place, said once
            Trail.say("Google ne daje satelit u EU (njegovo pravilo, ne kvar): prikazana je zračna snimka (Esri)")
            return canvas.show(Layers.AERIAL)
        }
        val used = Keyring.best(store.keyring)?.value
        return result.token?.let { canvas.show(layer, session = it, key = used) } ?: result.problem
    }
    return canvas.show(layer)
}

/**
 * THE SERVICE LIGHTS (v14): *"a short name of that service and a red ... LED if it's offline and green
 * if it's online."* Grey until it has been asked. GOO only when there is a Google key.
 */
@Composable
private fun ServiceLights(health: Map<Services.Service, Services.Health>, googleKey: Boolean, onClick: () -> Unit) {
    Panel {
        Row(
            Modifier.fillMaxWidth().background(Paint.Bar).clickable(onClick = onClick).padding(horizontal = GAP, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Services.Service.values().filter { it != Services.Service.GOOGLE || googleKey }.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(ledColour(Services.light(health[s]))))
                    Box(Modifier.width(4.dp))
                    Label(s.short, Paint.Sand, size = 11)
                }
            }
        }
    }
}

/** What fly-through found is drawn in this, bold (v16): magenta, apart from every other line. */
const val FOUND_COLOUR = 0xFFE040FBL

/** Green answers, red does not, grey not asked yet. */
fun ledColour(light: Services.Light): Color = when (light) {
    Services.Light.GREEN -> Color(0xFF34D399)
    Services.Light.RED -> Color(0xFFEF4444)
    Services.Light.GREY -> Color(0xFF6B7280)
}

/** THE CACHE KEY (v12): a round key over the map, lit amber while the app caches in the background. */
@Composable
private fun CacheKey(on: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) =
    RoundKey(R.drawable.ic_sniff, on, onClick, onLongClick)

/** A round key over the map (v12, v15): the cache key and the full-screen key. */
@Composable
private fun RoundKey(@androidx.annotation.DrawableRes icon: Int, on: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val tap by androidx.compose.runtime.rememberUpdatedState(onClick)
    val hold by androidx.compose.runtime.rememberUpdatedState(onLongClick)
    Box(
        // A dark base first, so a lit key's amber reads on a light map too (v16).
        Modifier.size(46.dp).clip(CircleShape)
            .background(Paint.Ground.copy(alpha = if (on) 0.96f else 0.86f))
            .background(if (on) Paint.Amber.copy(alpha = 0.30f) else Color.Transparent)
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }, onLongPress = { hold?.invoke() }) },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, if (on) Paint.AmberBright else Paint.Sand, size = 24.dp)
    }
}

/**
 * A KEY FOR ONE OF THE THREE MAPS (29.9.2026): its picture, and its three letters under it so the
 * three are told apart at a glance; lit amber while it is the map on the screen.
 */
@Composable
private fun RowScope.MapKey(
    word: String,
    @androidx.annotation.DrawableRes icon: Int,
    up: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val ink = if (up) Paint.AmberBright else Paint.Sand
    val tap by androidx.compose.runtime.rememberUpdatedState(onClick)
    val hold by androidx.compose.runtime.rememberUpdatedState(onLongClick)
    Column(
        Modifier
            .weight(1f)
            .height(KEY)
            .clip(RoundedCornerShape(10.dp))
            .background(if (up) Paint.Amber.copy(alpha = 0.22f) else Color.Transparent)
            .then(
                if (onLongClick == null) Modifier.clickable(onClick = onClick)
                else Modifier.pointerInput(Unit) { detectTapGestures(onTap = { tap() }, onLongPress = { hold?.invoke() }) }
            ),
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
    val total = (Layers.OfflineDownload.BYTES + 500_000) / 1_000_000
    CentrePanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_mountain, Paint.Sand)
            Spacer(Modifier.width(10.dp))
            Label("Offline map of Croatia", Paint.Sand, size = 17, align = TextAlign.Start)
        }
        val progress = live?.progress
        if (live?.running == true) {
            val percent = progress?.percent ?: 0
            Label("$percent %", Paint.AmberBright, size = 40)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Paint.Card)) {
                Box(Modifier.fillMaxWidth(percent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Paint.Amber))
            }
            Label(
                progress?.let { "${it.done / 1_000_000} of ${it.total / 1_000_000} MB · ${Geo.formatRate(it.bytesPerSecond)}" } ?: "connecting…",
                Paint.Sand, size = 14,
            )
            Label("The download goes on while you look at another map.", Paint.Dim, size = 12, lines = 3)
        } else {
            Label(
                "The whole of Croatia for working with no signal: roads, places, paths. Downloaded " +
                    "once, it stays on the phone. Best over Wi-Fi.",
                Paint.Sand, size = 13, align = TextAlign.Start, lines = 6,
            )
            live?.problem?.let { Label(it, Paint.Red, size = 13, align = TextAlign.Start, lines = 4) }
            Action(
                verb = if (partMb > 0) "Resume download" else "Download offline map",
                icon = R.drawable.ic_save,
                onClick = onFetch,
                trailing = if (partMb > 0) "$partMb of $total MB" else "$total MB",
                modifier = Modifier.fillMaxWidth(),
            )
            Action("I have a .map file", R.drawable.ic_folder, onClick = onChooseFile, quiet = true, modifier = Modifier.fillMaxWidth())
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
            Label("Google map needs an API key", Paint.Sand, size = 16, align = TextAlign.Start, modifier = Modifier.weight(1f))
        }
        keyring.forEach { key ->
            // A key he gave that Google refused: Google's own words, and a way to try again.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("${key.label}: ${key.said.ifBlank { Keyring.describe(key) }}", Paint.Red, size = 12, align = TextAlign.Start, lines = 5, modifier = Modifier.weight(1f))
                IconAction(R.drawable.ic_play, "again", onClick = { onTest(key) })
            }
        }
        Label(
            "No key is built into the app: everyone uses their own. Google gives a free monthly " +
                "quota that is enough for personal use. The steps:",
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
                "Optional restriction: Android apps, package ${caller["X-Android-Package"]}, " +
                    "SHA-1 ${caller["X-Android-Cert"]?.chunked(2)?.joinToString(":")}",
                Paint.Dim, size = 11, align = TextAlign.Start, lines = 5,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Paint.Card).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Label("paste the key (AIza…)", Paint.Dim, size = 14, align = TextAlign.Start)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    singleLine = true,
                    // A KEY IS NEVER ON THE SCREEN WHOLE, not even while it is typed (MT-KEY-3).
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    textStyle = TextStyle(color = Paint.Sand, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Paint.AmberBright),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Action(
            "Add key", R.drawable.ic_check,
            onClick = {
                onPaste(text)
                text = ""
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Action("Key from a file", R.drawable.ic_folder, onClick = onImport, quiet = true, modifier = Modifier.fillMaxWidth())
    }
}

/** The way to a Google key, a step a line, each with the page it happens on. */
private val GOOGLE_STEPS: List<Pair<String, String?>> = listOf(
    "Open Google Cloud Console and sign in with your Google account." to "https://console.cloud.google.com/",
    "Create a new project (top left: project picker → New project), e.g. \"mantra-arkod\"." to "https://console.cloud.google.com/projectcreate",
    "Turn on Billing for that project. Google asks for a card but charges nothing within the free monthly quota." to "https://console.cloud.google.com/billing",
    "In that project, enable \"Map Tiles API\" (the Enable button)." to "https://console.cloud.google.com/apis/library/tile.googleapis.com",
    "Also enable \"Places API (New)\" for the search field on the map (streets, house numbers, places)." to "https://console.cloud.google.com/apis/library/places.googleapis.com",
    "APIs & Services → Credentials → Create credentials → API key. Copy the key." to "https://console.cloud.google.com/apis/credentials",
    "Paste the key here and press Add key. The app tests it at once." to null,
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

/**
 * One line of numbers, and only the ones that decide something. THE COORDINATES ARE THE MIDDLE OF
 * THE MAP (29.9.2026, v3): *"showing always ... what is the center of the map, not where I am
 * now ... when I'm scrolling through the map, it's updating."* Where-am-I puts the middle on him,
 * and then they are his. The accuracy is shown only while the middle is on his fix.
 */
@Composable
private fun FixLine(centre: Pair<Double, Double>?, fix: Fix?, zoom: Int, layer: MapLayer) {
    val onHim = fix != null && centre != null && Geo.distance(fix.lat, fix.lon, centre.first, centre.second) <= maxOf(fix.accuracyM ?: 0f, 5f)
    Panel {
        Row(
            Modifier.fillMaxWidth().background(Paint.Bar).padding(horizontal = GAP, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ONE LINE, AND THE MAP'S NAME IS ON IT (15.9.2026). Everything here went to 11sp to
            // make the room rather than anything being dropped: about 55 monospace characters fit
            // across a 390 px phone, and this is fifty. The name carries no family — the key just
            // below says THU or GOO — so "Thunderforest Landscape" reads "Landscape" here.
            Label(centre?.let { Geo.formatLat(it.first) } ?: "N -- --.---", ink(centre != null), size = 11)
            Label(centre?.let { Geo.formatLon(it.second) } ?: "E -- --.---", ink(centre != null), size = 11)
            Label(
                if (onHim) fix?.accuracyM?.let { "±${it.toInt()}m" } ?: "±-" else "±-",
                if (onHim) accuracyInk(fix?.accuracyM) else Paint.Dim,
                size = 11,
            )
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
    // A LONG PRESS (v3), for the parcels key: its view settings.
    onLongClick: (() -> Unit)? = null,
) {
    val tap by androidx.compose.runtime.rememberUpdatedState(onClick)
    val hold by androidx.compose.runtime.rememberUpdatedState(onLongClick)
    Box(
        Modifier.weight(1f).height(KEY).then(
            if (onLongClick == null) Modifier.clickable(onClick = onClick)
            else Modifier.pointerInput(Unit) { detectTapGestures(onTap = { tap() }, onLongPress = { hold?.invoke() }) }
        ),
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
private fun NameBox(
    current: String,
    onCancel: () -> Unit,
    onOk: (String) -> Unit,
    title: String = "novo ime",
    // v5, a cache's name: OK with the box empty takes the name offered.
    fallbackToCurrent: Boolean = false,
) {
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
            Label(title, Paint.Amber, size = 11, align = TextAlign.Start)
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
                val usable = text.isNotBlank() || (fallbackToCurrent && current.isNotBlank())
                Action("OK", R.drawable.ic_check, onClick = { if (usable) onOk(text.trim().ifBlank { current }) }, enabled = usable, modifier = Modifier.weight(1f))
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
 * THE CACHE BEING FILLED, ON THE MAP (30.9.2026, v5): *"with verbose status showing what's going
 * on"*. What stage, how many of how many, how fast, how long is left, what failed; the cross
 * stops it (what was read is kept) and, once it is finished, puts the line away.
 */
@Composable
private fun HarvestLine(h: ParcelCaches.Progress, onStop: () -> Unit, onClose: () -> Unit) {
    Panel {
        Row(
            Modifier.fillMaxWidth().background(Paint.Bar).padding(start = GAP, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Label(h.line, if (h.problem != null) Paint.Red else Paint.Sand, size = 11, align = TextAlign.Start, lines = 3)
            }
            IconAction(R.drawable.ic_close, if (h.finished) null else "stop", onClick = if (h.finished) onClose else onStop, tint = Paint.Sand)
        }
    }
}

/**
 * THE PARCEL CACHES (30.9.2026, v5), kept like the tracks: *"we have tracks to store where we were
 * walking and we have parcel cache to store under different name what we have cached."* Each one
 * with its name, what it holds, whether it is drawn, its colour, line and weight, and a way to go
 * there, rename it or delete it (asking once).
 */
@Composable
private fun ParcelCachesFace(
    caches: List<ParcelCache.Cache>,
    store: Store,
    onGo: (ParcelCache.Cache) -> Unit,
    onClose: () -> Unit,
) {
    var owners by remember { mutableStateOf(store.cacheOwners) }
    var open by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ParcelCache.Info?>(null) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val day = remember { java.text.SimpleDateFormat("d.M.yyyy", java.util.Locale.ROOT) }

    Box(Modifier.fillMaxSize().background(Paint.Ground)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_save, Paint.Sand)
                Spacer(Modifier.width(12.dp))
                Label("Parcel caches", Paint.Sand, size = 17, align = TextAlign.Start)
                Spacer(Modifier.width(10.dp))
                Label("${caches.size}", Paint.Dim, size = 13, align = TextAlign.Start, modifier = Modifier.weight(1f))
                IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
            }
            Label(
                "A new cache takes everything on the screen: long press the Show/hide ARKOD layer key, then " +
                    "Cache this view. Search the caches by name, number, \"pl 1984\" or address in the čestica field.",
                Paint.Dim, size = 12, align = TextAlign.Start, lines = 4,
            )
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Paint.Card)) {
                Toggle("Also vlasnički listovi", R.drawable.ic_text, on = owners, onChange = { owners = it; store.cacheOwners = it })
            }

            caches.forEach { cache ->
                val info = cache.info
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paint.Card)
                        .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(info.colour)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).clickable { open = if (open == info.id) null else info.id }) {
                            Label(info.name, Paint.Sand, size = 15, align = TextAlign.Start)
                            Label(
                                "${info.count} čestica · ${cache.items.count { it.read }} listova · " +
                                    "${cache.items.sumOf { it.holders.size }} imena · ${day.format(java.util.Date(info.createdMs))}",
                                Paint.Dim, size = 11, align = TextAlign.Start, lines = 2,
                            )
                            if (info.places.isNotBlank()) Label("k.o. ${info.places}", Paint.Dim, size = 11, align = TextAlign.Start, lines = 2)
                        }
                        IconAction(
                            if (info.visible) R.drawable.ic_eye else R.drawable.ic_eye_off,
                            if (info.visible) "shown" else "hidden",
                            onClick = { ParcelCaches.update(info.copy(visible = !info.visible)) },
                        )
                        IconAction(R.drawable.ic_pin, "go", onClick = { onGo(cache) })
                    }
                    if (open == info.id) {
                        Row(Modifier.fillMaxWidth().padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ParcelCache.COLOURS.forEach { option ->
                                Box(
                                    Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(option))
                                        .clickable { ParcelCaches.update(info.copy(colour = option)) },
                                    contentAlignment = Alignment.Center,
                                ) { if (option == info.colour) Glyph(R.drawable.ic_check, Paint.Ground, size = 18.dp) }
                            }
                        }
                        Choice(
                            parts = Parcels.LineStyle.entries.map { Part(it.word) },
                            chosen = Parcels.LineStyle.entries.indexOf(info.style),
                            onChoose = { ParcelCaches.update(info.copy(style = Parcels.LineStyle.entries[it])) },
                            modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
                        )
                        Choice(
                            parts = ParcelStyle.Weight.entries.map { Part(it.word) },
                            chosen = ParcelStyle.Weight.entries.indexOf(info.weight),
                            onChoose = { ParcelCaches.update(info.copy(weight = ParcelStyle.Weight.entries[it])) },
                            modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            IconAction(R.drawable.ic_edit, "name", onClick = { renaming = info })
                            IconAction(
                                R.drawable.ic_trash,
                                if (confirming == info.id) "sure?" else "delete",
                                onClick = {
                                    if (confirming == info.id) {
                                        ParcelCaches.delete(info.id)
                                        confirming = null
                                    } else {
                                        confirming = info.id
                                    }
                                },
                                tint = Paint.Red,
                            )
                        }
                    }
                }
            }
            if (caches.isEmpty()) Label("No caches yet.", Paint.Dim, size = 13, align = TextAlign.Start)
        }

        renaming?.let { info ->
            NameBox(
                current = info.name,
                title = "new name",
                onCancel = { renaming = null },
                onOk = { name ->
                    renaming = null
                    ParcelCaches.update(info.copy(name = name))
                },
            )
        }
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
    /** That record came from the app's server, fetched later (Fetch when available, row 38). */
    val keptFar: Boolean = false,
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
    // v10: a parcel number on the sheet goes to that parcel; FILE sends this parcel as a file.
    onNumber: (String) -> Unit,
    onFile: () -> Unit,
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
            IconAction(R.drawable.ic_save, "FILE", onClick = onFile)
            IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
        }
        val area = record?.areaM2?.toIntOrNull() ?: card.parcel.areaM2
        Label(
            text = Parcels.areaLabel(area) + (record?.address?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            colour = Paint.Sand,
            size = 14,
            align = TextAlign.Start,
        )
        // WHAT TXT AND CPY DID, on the sheet itself (TEST_RESULTS E3, v1): the note line is under
        // the sheet, so its answer was never seen.
        val said by Trail.note.collectAsState()
        said?.let { Label(it, Paint.AmberBright, size = 12, align = TextAlign.Start, lines = 3) }
        // READ OFF THE PHONE (29.9.2026): the state did not answer, so this is what it said last.
        card.keptSince?.let { since ->
            val day = java.text.SimpleDateFormat("d.M.yyyy.", java.util.Locale.ROOT).format(java.util.Date(since))
            if (card.keptFar) {
                val at = java.text.SimpleDateFormat("d.M.yyyy. HH:mm", java.util.Locale.ROOT).format(java.util.Date(since))
                Label("država nije odgovorila: zapis koji je poslužitelj dohvatio kasnije, $at", Paint.Amber, size = 12, align = TextAlign.Start, lines = 2)
            } else Label("bez signala: prikazan zapis od $day", Paint.Amber, size = 12, align = TextAlign.Start)
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
                        // THE NUMBERS ARE LINKS (v10): a parcel the folio lists goes to the map.
                        val number = Parcels.numberIn(r)
                        Row(Modifier.fillMaxWidth().then(if (number != null) Modifier.clickable { onNumber(number) } else Modifier)) {
                            Label(r.main, if (number != null) Paint.AmberBright else Paint.Sand, size = 15, align = TextAlign.Start, modifier = Modifier.weight(1f), lines = lines)
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
                card.problem != null -> {
                    Label(card.problem, Paint.Red, size = 13, align = TextAlign.Start)
                    LaterButton(Parcels.recordUrl(card.parcel.id))
                }
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
    groups: List<MarkFile.Group>,
    onGroup: (MarkFile.Group) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onShareGroup: (String) -> Unit,
    onImport: () -> Unit,
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
    var styling by remember { mutableStateOf<String?>(null) }
    var sureGroup by remember { mutableStateOf<String?>(null) }
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
        store.remember("mine-" + by.name, words)
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
            // A FILE IN, AND EACH GROUP OUT (v8): a group is one file, with one look for its parcels.
            Action("Import a file", R.drawable.ic_folder, onClick = onImport, quiet = true, modifier = Modifier.fillMaxWidth())
            MarkFile.groupsIn(marks, groups).forEach { group ->
                val members = marks.filter { it.group == group.name }
                val title = group.name.ifBlank { "Moje čestice" }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paint.Card)
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineSample(group.colour, group.style)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable { styling = if (styling == group.name) null else group.name }) {
                            Label(title, if (group.visible) Paint.Sand else Paint.Dim, size = 15, align = TextAlign.Start)
                            Label("${members.size} čestica · ${group.weight.word.lowercase()}", Paint.Dim, size = 11, align = TextAlign.Start)
                        }
                        IconAction(
                            if (group.visible) R.drawable.ic_eye else R.drawable.ic_eye_off,
                            if (group.visible) "shown" else "hidden",
                            onClick = { onGroup(group.copy(visible = !group.visible)) },
                        )
                        IconAction(R.drawable.ic_copy, "send", onClick = { onShareGroup(group.name) })
                    }
                    if (styling == group.name) {
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Parcels.SWATCHES.forEach { option ->
                                Box(
                                    Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(7.dp)).background(Color(option))
                                        .border(1.dp, Look.Outline, RoundedCornerShape(7.dp))
                                        .clickable { onGroup(group.copy(colour = option)) },
                                    contentAlignment = Alignment.Center,
                                ) { if (option == group.colour) Glyph(R.drawable.ic_check, if (option == 0xFF111111L) Paint.Sand else Paint.Ground, size = 16.dp) }
                            }
                        }
                        Choice(
                            parts = Parcels.LineStyle.entries.map { Part(it.word) },
                            chosen = Parcels.LineStyle.entries.indexOf(group.style),
                            onChoose = { onGroup(group.copy(style = Parcels.LineStyle.entries[it])) },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                        Choice(
                            parts = ParcelStyle.Weight.entries.map { Part(it.word) },
                            chosen = ParcelStyle.Weight.entries.indexOf(group.weight),
                            onChoose = { onGroup(group.copy(weight = ParcelStyle.Weight.entries[it])) },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                        if (group.name.isNotBlank()) {
                            Action(
                                verb = if (sureGroup == group.name) "again: delete this file's parcels" else "Delete this file's parcels",
                                icon = R.drawable.ic_trash,
                                onClick = { if (sureGroup == group.name) { onDeleteGroup(group.name); sureGroup = null } else sureGroup = group.name },
                                quiet = true,
                                danger = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                members.forEach { mark ->
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
            // THE HISTORY OF THIS BOX (v8): a tap searches it again.
            if (text.isEmpty()) {
                store.history("mine-" + by.name).take(6).forEach { past ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { text = past; find() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(R.drawable.ic_track, Paint.Dim, size = 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Label(past, Paint.Sand, size = 14, align = TextAlign.Start)
                    }
                }
            }
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

/**
 * A FIELD ON THE MAP (29.9.2026, v3): Google's is white as Google's own is; the parcel field is the
 * app's dark card. A clear cross while there is text; the search key on the keyboard asks at once.
 */
@Composable
private fun MapField(
    text: String,
    onText: (String) -> Unit,
    hint: String,
    @androidx.annotation.DrawableRes icon: Int,
    light: Boolean,
    onSearch: () -> Unit,
    // v8: what was searched here before, offered when the box is touched and empty.
    history: List<String> = emptyList(),
    onForget: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val ink = if (light) Color(0xFF202124) else Paint.Sand
    val dim = if (light) Color(0xFF70757A) else Paint.Dim
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = GAP)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (light) Color.White else Paint.Card.copy(alpha = 0.96f))
            .border(1.dp, if (light) Color(0x33000000) else Look.Outline, RoundedCornerShape(23.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, dim, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(hint, color = dim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 15.sp),
                cursorBrush = SolidColor(if (light) Color(0xFF1A73E8) else Paint.AmberBright),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (text.isNotEmpty()) IconAction(R.drawable.ic_close, null, onClick = { onText("") }, tint = dim)
    }
    // THE HISTORY (v8): a tap puts the words back and asks again; nothing is typed twice.
    if (focused && text.isEmpty() && history.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = GAP).padding(top = 4.dp).clip(RoundedCornerShape(16.dp))
                .background(if (light) Color.White else Paint.Card),
        ) {
            history.forEach { past ->
                Row(
                    Modifier.fillMaxWidth().clickable { onText(past) }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(R.drawable.ic_track, dim, size = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(past, color = ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { onForget() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("clear history", color = dim, fontSize = 13.sp)
            }
        }
    }
}

/**
 * GOOGLE'S SEARCH ON EVERY MAP (29.9.2026, v3): *"Google search field is not present, should be
 * present. When there is a Google key. And the same search field should be on all 3 maps. So if I
 * search some street, Google give result, but this map, current map which is not Google, can show
 * result, show the point."* Answers come as he types, as in Google Maps (autocomplete, billed
 * once per session); the search key also asks Text Search. A tap pins the place on the map up.
 */
@Composable
private fun PlaceField(store: Store, onPlace: (Finding.Hit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Finding.Hit>>(emptyList()) }
    var line by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var history by remember { mutableStateOf(store.history("google")) }

    suspend fun ask(full: Boolean) {
        val words = text.trim()
        if (words.length < 3) { hits = emptyList(); line = null; return }
        val near = Canvases.centre()?.let { Fix(it.first, it.second, null, 0L, null) }
        val (found, problem) = PlaceSearch.find(words, near, store, session, full = full)
        if (text.trim() != words) return
        hits = found
        line = problem
    }

    // AS HE TYPES: a third of a second after the last letter, so a word is one question.
    LaunchedEffect(text) {
        delay(350)
        ask(full = false)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MapField(
            text = text,
            onText = { text = it; if (it.isEmpty()) { hits = emptyList(); line = null } },
            hint = "Search Google Maps",
            icon = R.drawable.ic_search,
            light = true,
            onSearch = { store.remember("google", text); history = store.history("google"); scope.launch { ask(full = true) } },
            history = history,
            onForget = { store.forget("google"); history = emptyList() },
        )
        line?.let { Box(Modifier.padding(horizontal = GAP)) { NoteLine(it) } }
        if (hits.isNotEmpty()) {
            Box(Modifier.padding(horizontal = GAP)) {
                ResultsList(hits, light = true) { hit ->
                    hits = emptyList()
                    store.remember("google", text)
                    history = store.history("google")
                    focus.clearFocus()
                    scope.launch {
                        val placed = PlaceSearch.locate(hit, store, session)
                        // One session is one search: the tap closes it, the next word opens another.
                        session = java.util.UUID.randomUUID().toString()
                        if (placed == null) line = "Google did not say where ${hit.title} is" else onPlace(placed)
                    }
                }
            }
        }
    }
}

/**
 * THE PARCEL FIELD (29.9.2026, v3), under Google's, hidden or shown in Parcel view. What he types
 * decides what it asks, and it answers as he types:
 *  - a number, "245", "2449/3": every parcel number that begins with it in the k.o. under the
 *    middle of the map (OSS, a quarter of a second);
 *  - "pl 1984": that possession sheet's parcels in the same k.o.;
 *  - a name, "jaša anica": Imenik, the holders and owners of every sheet opened on this phone.
 *
 * WHEREVER THE MAP IS (30.9.2026, v11): *"I typed the parcel number expecting that the map will take
 * me there ... nothing is happening."* He was in Zagreb and typed 1358/3, a Kukljica number. Now a
 * number is found in Moje čestice in every k.o., in the k.o. he names with it ("1358/3 kukljica"),
 * and, when the k.o. under the map has no such number, in every k.o. he already has something in
 * (ParcelQuery). Search on the keyboard opens the one exact result: the map goes there, the sheet
 * opens.
 */
@Composable
private fun ParcelField(store: Store, book: List<OwnerBook.Entry>, caches: List<ParcelCache.Cache>, marks: List<Parcels.Mark>, onParcel: (Finding.Hit) -> Unit) {
    var history by remember { mutableStateOf(store.history("parcel")) }
    var text by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Finding.Hit>>(emptyList()) }
    var line by remember { mutableStateOf<String?>(null) }
    // The k.o. under the middle, asked again only when the middle has moved (a tenth of a degree).
    var ko by remember { mutableStateOf<Pair<Pair<Double, Double>, Triple<String, String, String>>?>(null) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // SEARCH ASKS AGAIN (v11): the answer was kept while the text stayed the same, so after he had
    // moved from Zagreb to Kukljica the line still said "k.o. CENTAR NOVI · 0". Search now asks again
    // from where the map is, and opens the one exact result when the answer comes.
    var asked by remember { mutableStateOf(0) }
    var openWhenAnswered by remember { mutableStateOf(false) }

    suspend fun municipality(): Triple<String, String, String>? {
        val (lat, lon) = Canvases.centre() ?: return null
        val known = ko
        if (known != null && Geo.distance(known.first.first, known.first.second, lat, lon) < 1_000) return known.second
        val found = runCatching { ParcelNet.municipalityFull(lat, lon) }.getOrNull() ?: return null
        ko = (lat to lon) to found
        store.seenMunicipalities = ParcelQuery.withSeen(store.seenMunicipalities, ParcelQuery.Ko(found.first, found.second, found.third))
        return found
    }

    LaunchedEffect(text, book.size, caches, asked) {
        val words = text.trim()
        if (words.isEmpty()) { hits = emptyList(); line = null; openWhenAnswered = false; return@LaunchedEffect }
        if (!openWhenAnswered) delay(250)
        // THE CACHES FIRST (v5): on the phone, every name, number and sheet, with no signal.
        val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ParcelCache.search(caches, words) }
        val cachedLine = if (caches.isEmpty()) null else "caches: ${cached.size}"
        val sheet = Regex("""^p\.?\s*l\.?\s*(\d+)$""", RegexOption.IGNORE_CASE).find(words)?.groupValues?.get(1)
        val query = if (sheet == null) ParcelQuery.parse(words) else null
        var mine = emptyList<Finding.Hit>()
        val answer = runCatching {
            when {
                sheet != null -> {
                    val k = municipality() ?: error("pomaknite kartu iznad katastarske općine")
                    ParcelNet.ossSearch(k.third, sheet = sheet).also { line = listOfNotNull(cachedLine, "k.o. ${k.second} · posjedovni list $sheet: ${it.size}").joinToString(" · ") }
                }
                query != null -> {
                    val known = ParcelQuery.known(store.seenMunicipalities, marks, caches, book)
                    val named = query.place?.let { ParcelQuery.resolve(it, known) }
                    if (query.place != null && named == null) {
                        mine = ParcelQuery.mine(marks, query.number, known)
                        error("k.o. \"${query.place}\" još nije poznata ovom telefonu: pomaknite kartu iznad nje jednom")
                    }
                    mine = ParcelQuery.mine(marks, query.number, known, only = named)
                    val here = named ?: municipality()?.let { ParcelQuery.Ko(it.first, it.second, it.third) }
                    val first = here?.let { ParcelNet.suggest(it.reg, it.label, query.number) }.orEmpty()
                    // Not in the k.o. under the map: every k.o. he already has something in.
                    val others = if (named != null || ParcelQuery.hasExact(mine + first, query.number)) emptyList()
                        else known.filter { it.reg != here?.reg }.take(8).map { k ->
                            async { runCatching { ParcelNet.suggest(k.reg, k.label, query.number) }.getOrDefault(emptyList()) }
                        }.awaitAll().flatten()
                    val elsewhere = others.mapNotNull { h -> known.firstOrNull { it.reg == h.ref?.substringBefore('-') }?.label }.distinct()
                    line = listOfNotNull(
                        cachedLine,
                        mine.size.takeIf { it > 0 }?.let { "Moje čestice: $it" },
                        here?.let { "k.o. ${it.label} · ${first.size}" } ?: "k.o. pod kartom nije poznata",
                        elsewhere.takeIf { it.isNotEmpty() }?.let { "i u k.o. ${it.joinToString(", ")}" },
                    ).joinToString(" · ")
                    first + others
                }
                words.any { it.isLetter() } -> {
                    OwnerBook.search(book, words).map { OwnerBook.hit(it) }.also {
                        line = listOfNotNull(cachedLine, "Imenik: ${it.size} · iz ${book.size} imena s listova otvorenih na ovom telefonu").joinToString(" · ")
                    }
                }
                else -> emptyList()
            }
        }
        if (text.trim() != words) return@LaunchedEffect
        // Moje čestice first, the caches' next, the state's after them, each parcel once.
        val local = mine + cached.filter { h -> mine.none { it.ref == h.ref } }
        hits = (local + answer.getOrNull().orEmpty().filter { h -> local.none { it.ref == h.ref } }).take(60)
        answer.exceptionOrNull()?.let { e ->
            line = listOfNotNull(cachedLine, e.message ?: "katastar nije odgovorio").joinToString(" · ")
        }
        if (openWhenAnswered) {
            openWhenAnswered = false
            query?.let { q -> ParcelQuery.best(hits, q.number) }?.let { hit ->
                hits = emptyList()
                line = null
                onParcel(hit)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MapField(
            text = text,
            onText = { text = it },
            hint = "broj čestice (i k.o.), pl 1984 ili ime",
            icon = R.drawable.ic_parcels,
            light = false,
            onSearch = {
                store.remember("parcel", text)
                history = store.history("parcel")
                focus.clearFocus()
                // SEARCH OPENS IT (v11): asked again from where the map is now; the one exact number
                // goes to the map and its sheet as soon as the answer is in.
                ko = null
                openWhenAnswered = true
                asked++
            },
            history = history,
            onForget = { store.forget("parcel"); history = emptyList() },
        )
        line?.let { Box(Modifier.padding(horizontal = GAP)) { NoteLine(it) } }
        if (hits.isNotEmpty()) {
            Box(Modifier.padding(horizontal = GAP)) {
                ResultsList(hits, light = false) { hit ->
                    store.remember("parcel", text)
                    history = store.history("parcel")
                    hits = emptyList()
                    line = null
                    focus.clearFocus()
                    onParcel(hit)
                }
            }
        }
    }
}

/**
 * EVERY TOUCH ON THIS IS ITS OWN: nothing reaches the map beneath. Being a pointer target is
 * enough for that, because Compose gives a touch to the topmost node that takes it and not to the
 * map under it. It used to CONSUME every event as well, and a scroll inside cancels itself when a
 * parent consumes (TEST_RESULTS D1, v1: the Google panel would not scroll up by finger).
 */
private fun Modifier.swallowTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
        }
    }
}

/**
 * FETCH WHEN AVAILABLE (row 38): the record the state did not answer, handed to the app's server, which asks
 * again every ten minutes and keeps the answer for every phone. The button says what the server said.
 */
@Composable
fun LaterButton(stateUrl: String) {
    val scope = rememberCoroutineScope()
    var said by remember(stateUrl) { mutableStateOf<String?>(null) }
    var busy by remember(stateUrl) { mutableStateOf(false) }
    Action("Fetch when available", R.drawable.ic_save, onClick = {
        if (!busy) {
            busy = true
            said = "handing it to the server…"
            scope.launch {
                said = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ParcelNet.want(stateUrl) }
                busy = false
            }
        }
    }, quiet = true, enabled = !busy)
    said?.let { Label(it, Paint.Dim, size = 12, align = TextAlign.Start, lines = 3) }
}

/**
 * GOOGLE'S VIEWS, ON A LONG PRESS OF GOO (1.10.2026, FEATURES row 40): the same four as Settings → Google map,
 * just above the key row; a choice shows Google's map in that view and closes; a tap anywhere else closes.
 */
@Composable
private fun GoogleViews(chosen: Int, onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = KEY + 40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Paint.Card)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Choice(
                parts = listOf(Part("map"), Part("satellite"), Part("terrain"), Part("hybrid")),
                chosen = chosen,
                onChoose = onChoose,
            )
        }
    }
}
