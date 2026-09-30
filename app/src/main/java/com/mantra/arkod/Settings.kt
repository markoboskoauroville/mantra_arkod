package com.mantra.arkod

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE SETTINGS (Mantra ARKOD, 29.9.2026). Read from the store when they open and written through
 * when a row is pressed, never read from disk while a frame is drawn (17.9.2026).
 *
 * His order: MY PARCELS first, where Mantra Trail kept its tracks and routes (*"that will be a
 * special part in settings which will basically replace the part when we were saving the
 * routes"*); then the three maps and what each needs; the cadastre kept on the phone; the tracks;
 * and the version, which opens the latest build.
 */
@Composable
fun SettingsFace(
    store: Store,
    current: MapLayer,
    version: String,
    myParcelCount: Int,
    onMyParcels: () -> Unit,
    onParcelView: () -> Unit,
    cacheCount: Int,
    onCaches: () -> Unit,
    hasOffline: Boolean,
    download: MapDownload.Live?,
    onFetchOfflineMap: () -> Unit,
    onChooseMapFile: () -> Unit,
    onOfflineView: (Layers.OfflineView) -> Unit,
    onGoogleView: (MapLayer) -> Unit,
    keyring: List<Keyring.Key>,
    onTestKey: (Keyring.Key) -> Unit,
    onRemoveKey: (Keyring.Key) -> Unit,
    onImportKeys: () -> Unit,
    onPasteKey: (String) -> Unit,
    onTestTiles: () -> Unit,
    onPause: () -> Unit,
    recordingPaused: Boolean,
    recording: Boolean,
    onTracks: () -> Unit,
    trackCount: Int,
    onChooseExportFolder: () -> Unit,
    folderName: String,
    onClose: () -> Unit,
    // A kept parcel tapped in the list (v13): the map goes there and its sheet opens.
    onKeptParcel: (municipality: String, number: String) -> Unit = { _, _ -> },
    // The Services group's "Check now" (v14): every service but Google asked once.
    onCheckServices: () -> Unit = {},
    // Help (v18): "en" or "hr".
    onHelp: (String) -> Unit = {},
) {
    val services by Services.health.collectAsState()
    val serviceLog by Services.log.collectAsState()
    // CHECK NOW, ONE SERVICE (v19): what each row's own check is doing.
    var checking by remember { mutableStateOf<Map<Services.Service, String>>(emptyMap()) }
    val checkScope = androidx.compose.runtime.rememberCoroutineScope()
    var logOpen by remember { mutableStateOf(false) }
    val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT) }
    var theme by remember { mutableStateOf(store.themeName) }
    var googleId by remember { mutableStateOf(store.googleViewId) }
    var keysOpen by remember { mutableStateOf(store.opened("keys") || keyring.isEmpty()) }
    var prefetch by remember { mutableStateOf(store.prefetch) }
    var cacheLabel by remember { mutableStateOf("…") }
    var keywords by remember { mutableStateOf(store.cacheKeywords) }
    var keptOpen by remember { mutableStateOf(false) }
    var kept by remember { mutableStateOf<List<Sniff.Kept>?>(null) }
    var keptFilter by remember { mutableStateOf("") }
    var sureClear by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    val open = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        cacheLabel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { inventoryLine() }
    }
    LaunchedEffect(keptOpen) {
        if (keptOpen && kept == null) kept = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Sniff.kept(ArkodCache.keptRecords()) }
    }

    Box(Modifier.fillMaxSize().background(Paint.Ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Words("Settings", Paint.Sand, 17, TextAlign.Start, Modifier.padding(start = 4.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Paint.Card)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Words("✕", Paint.Sand, 18) }
            }

            // WHAT THE PHONE KEEPS, ON TOP (v12): *"Inside the settings, always show at the top the size
            // of the cache file. I am aware because we are now sniffing, we are caching everything."*
            // The same switch as the cache key on the map, and his cache criteria.
            Group("Kept on this phone") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Words(cacheLabel, Paint.Sand, 13, TextAlign.Start, Modifier.weight(1f))
                    Action(
                        verb = if (sureClear) "again: clear" else "clear",
                        icon = R.drawable.ic_trash,
                        onClick = {
                            if (sureClear) {
                                ArkodCache.clear()
                                cacheLabel = inventoryLine()
                                kept = emptyList()
                                sureClear = false
                            } else {
                                sureClear = true
                            }
                        },
                        quiet = true,
                        danger = true,
                    )
                }
                Hairline()
                // EVERY KEPT PARCEL (v13): *"all parcels should be listed by its numbers and some data,
                // maybe in 3 words: last name of the owner/user and the place of Croatia."*
                Opens("Kept čestice", R.drawable.ic_parcels, under = kept?.let { "${it.size}" } ?: "number, surname, place", onClick = { keptOpen = !keptOpen }, open = keptOpen)
                if (keptOpen) {
                    val all = kept
                    if (all == null) Words("reading what is kept…", Paint.Dim, 12, TextAlign.Start, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    else {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Look.Outline, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            if (keptFilter.isEmpty()) Words("broj, prezime ili mjesto", Paint.Dim, 14, TextAlign.Start)
                            androidx.compose.foundation.text.BasicTextField(
                                value = keptFilter,
                                onValueChange = { keptFilter = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = Paint.Sand, fontSize = 14.sp),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Paint.AmberBright),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val shown = Sniff.filterKept(all, keptFilter)
                        shown.take(200).forEach { k ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onKeptParcel(k.municipalityReg, k.number) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(k.number, color = Paint.Sand, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(84.dp))
                                Words(k.words, Paint.Dim, 13, TextAlign.Start, Modifier.weight(1f))
                            }
                        }
                        if (shown.size > 200) Words("and ${shown.size - 200} more: type to narrow", Paint.Dim, 12, TextAlign.Start, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        if (all.isEmpty()) Words("none yet: a sheet you open, or one the cache reads, is kept", Paint.Dim, 12, TextAlign.Start, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                Hairline()
                Toggle("Cache", R.drawable.ic_sniff, on = prefetch, onChange = {
                    prefetch = it
                    store.prefetch = it
                })
                Hairline()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Words("Cache criteria (keywords)", Paint.Sand, 14, TextAlign.Start)
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Look.Outline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        if (keywords.isEmpty()) Words("surnames, first names, anything: boško, gobić, maslinik", Paint.Dim, 14, TextAlign.Start)
                        androidx.compose.foundation.text.BasicTextField(
                            value = keywords,
                            onValueChange = { keywords = it; store.cacheKeywords = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Paint.Sand, fontSize = 14.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Paint.AmberBright),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Words(
                        "One per comma. With keywords, a sheet read in the background is kept only when a name, place or land use on it fits one of them. Empty: everything is kept. What you open yourself is always kept.",
                        Paint.Dim, 12, TextAlign.Start,
                    )
                }
            }

            // THE SERVICES (v14): *"in settings we can also describe that certain service what it does
            // and it's offline and what cannot be done because of that."* The same lights as on the map.
            Group("Services") {
                val now = System.currentTimeMillis()
                Services.Service.values().forEachIndexed { i, sv ->
                    if (i > 0) Hairline()
                    val h = services[sv]
                    val light = Services.light(h)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 5.dp).size(11.dp).clip(CircleShape).background(ledColour(light)))
                        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Words("${sv.short} · ${sv.title}", Paint.Sand, 14, TextAlign.Start)
                            Words(
                                if (sv == Services.Service.GOOGLE && light == Services.Light.GREY) "not asked yet (never checked on its own: every request is on your key)"
                                else Services.said(h, now) { clock.format(java.util.Date(it)) },
                                if (light == Services.Light.RED) Color(0xFFEF4444) else Paint.Dim, 12, TextAlign.Start,
                            )
                            Words(sv.does, Paint.Dim, 12, TextAlign.Start)
                            if (light == Services.Light.RED) Words("While it is down: ${sv.whenDown}", Paint.Sand, 12, TextAlign.Start)
                            checking[sv]?.takeIf { !it.startsWith("checking") }?.let { Words(it, if (it == "back online") Color(0xFF34D399) else Paint.AmberBright, 12, TextAlign.Start) }
                            // CHECK NOW, NEXT TO A SERVICE THAT IS DOWN (v19): asked again, up to three
                            // times; green the moment it answers, and what waited for it is done then.
                            if (light != Services.Light.GREEN && sv != Services.Service.GOOGLE) {
                                val busy = checking[sv]?.startsWith("checking") == true
                                Action(
                                    verb = if (busy) checking[sv].orEmpty() else "Check now",
                                    icon = R.drawable.ic_play,
                                    onClick = {
                                        if (!busy) checkScope.launch {
                                            val back = ParcelNet.checkUntilBack(sv) { i -> checking = checking + (sv to Services.tryLine(i, Services.CHECK_WAITS.size)) }
                                            checking = checking + (sv to Services.checkedLine(back, Services.health.value[sv]))
                                        }
                                    },
                                    quiet = true,
                                )
                            }
                            if (light == Services.Light.RED && sv == Services.Service.GOOGLE) {
                                Words("Google is asked again when you open the GOO map (every request is on your key).", Paint.Dim, 12, TextAlign.Start)
                            }
                        }
                    }
                }
                Hairline()
                Action("Check now", R.drawable.ic_play, onClick = onCheckServices, quiet = true, modifier = Modifier.fillMaxWidth())
                Hairline()
                // THE SERVICE LOG (v16): *"this morning ... until 9, it was not working. And late
                // afternoon after 4:30 ... one service was out of service. So we need to understand."*
                Opens("Service log", R.drawable.ic_text, under = if (serviceLog.isEmpty()) "nothing yet" else "${serviceLog.size} changes", onClick = { logOpen = !logOpen }, open = logOpen)
                if (logOpen) {
                    val day = remember { java.text.SimpleDateFormat("d.M. HH:mm", java.util.Locale.ROOT) }
                    serviceLog.take(100).forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(ledColour(if (e.online) Services.Light.GREEN else Services.Light.RED)))
                            Words(Services.said(e) { day.format(java.util.Date(it)) }, Paint.Dim, 12, TextAlign.Start, Modifier.padding(start = 10.dp))
                        }
                    }
                }
                Hairline()
                // WHY THEY FAIL, AND WHAT THE APP DOES ABOUT IT (v16).
                Words(
                    "All four ARKOD services are the State Geodetic Administration's (DGU): the map (WMS) and the outlines (WFS) at api.uredjenazemlja.hr, the cadastre and the land registry at oss.uredjenazemlja.hr. " +
                        "They fail on their side: \"ORA-01000: maximum open cursors exceeded\" is their Oracle database running out of connections, which lasts until they restart it, and they are slow or down at some hours. Nothing on the phone can fix that. " +
                        "What the app does: everything read is kept (tiles, sheets, outlines, searches) and used when a service is red; the cache key reads ahead while they answer; a failing request is tried again; every change is in the log above, so the hours can be seen.",
                    Paint.Dim, 12, TextAlign.Start, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            // SETTINGS IN ENGLISH, THE CADASTRE IN CROATIAN (29.9.2026, v3): *"Write all the settings
            // in the English language and all terminology from arkod in Croatian."* So a row here is
            // English, and a name the cadastre gives a thing (čestica, posjedovni list, k.o.) is not.
            Group("Moje čestice") {
                Opens("Moje čestice", R.drawable.ic_parcels, under = "$myParcelCount kept", onClick = onMyParcels)
                Hairline()
                Opens("Parcel view", R.drawable.ic_layers, under = "the Show/hide ARKOD layer key: lines, caches", onClick = onParcelView)
                Hairline()
                Opens("Parcel caches", R.drawable.ic_save, under = if (cacheCount == 0) "none yet" else "$cacheCount kept", onClick = onCaches)
            }

            Group("Offline map") {
                val live = download
                when {
                    live?.running == true -> Action(
                        verb = "Downloading Croatia",
                        icon = R.drawable.ic_save,
                        onClick = {},
                        quiet = true,
                        trailing = live.progress?.let { MapDownload.line(it) } ?: "…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    hasOffline -> Pick(
                        title = Layers.OfflineDownload.LABEL,
                        icon = R.drawable.ic_mountain,
                        chosen = current.kind == LayerKind.VECTOR_FILE,
                        under = if (MapDownload.isPresent(context)) "on the phone" else "chosen .map file",
                        onClick = {},
                    )
                    else -> Action(
                        verb = "Download offline map",
                        icon = R.drawable.ic_save,
                        onClick = onFetchOfflineMap,
                        quiet = true,
                        trailing = "${(Layers.OfflineDownload.BYTES + 500_000) / 1_000_000} MB",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                live?.problem?.let {
                    Hairline()
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Words(it, Paint.Red, 12, TextAlign.Start) }
                }
                Hairline()
                Action("Choose a .map file", R.drawable.ic_folder, onClick = onChooseMapFile, quiet = true, modifier = Modifier.fillMaxWidth())
                Hairline(inset = 16.dp)
                Box(Modifier.padding(12.dp)) {
                    Choice(
                        parts = Layers.OFFLINE_VIEWS.map { Part(it.label) },
                        chosen = Layers.OFFLINE_VIEWS.indexOfFirst { it.theme == theme }.coerceAtLeast(0),
                        onChoose = { i ->
                            val view = Layers.OFFLINE_VIEWS[i]
                            theme = view.theme
                            onOfflineView(view)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Group("Google map") {
                Box(Modifier.padding(12.dp)) {
                    Choice(
                        parts = listOf(Part("map"), Part("satellite"), Part("terrain"), Part("hybrid")),
                        chosen = Layers.GOOGLE_ALL.indexOfFirst { it.id == googleId }.coerceAtLeast(0),
                        onChoose = { i ->
                            googleId = Layers.GOOGLE_ALL[i].id
                            onGoogleView(Layers.GOOGLE_ALL[i])
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // THE KEYRING: every key he added, what it last answered, a test and a delete for each.
            Group("API keys") {
                Opens(
                    title = "Google keys",
                    icon = R.drawable.ic_key,
                    under = if (keyring.isEmpty()) "none" else "${keyring.size}",
                    onClick = {
                        keysOpen = !keysOpen
                        store.setOpened("keys", keysOpen)
                    },
                    open = keysOpen,
                )
                if (keysOpen) {
                    keyring.forEach { key ->
                        Hairline()
                        Row(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Words(key.label, Paint.Sand, 15, TextAlign.Start)
                                Words(
                                    if (key.said.isNotBlank() && key.verdict != Keyring.Verdict.GOOD) "${key.masked} · ${key.said}"
                                    else Keyring.describe(key),
                                    Paint.Dim, 12, TextAlign.Start,
                                )
                            }
                            IconAction(R.drawable.ic_play, "test", onClick = { onTestKey(key) })
                            IconAction(R.drawable.ic_trash, "delete", onClick = { onRemoveKey(key) }, tint = Paint.Red)
                        }
                    }
                    Hairline()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, Look.Outline, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            if (pasted.isEmpty()) Words("paste a key (AIza…)", Paint.Dim, 14, TextAlign.Start)
                            androidx.compose.foundation.text.BasicTextField(
                                value = pasted,
                                onValueChange = { pasted = it.trim() },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Paint.Sand, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Paint.AmberBright),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconAction(R.drawable.ic_check, "add", onClick = {
                            if (pasted.isNotBlank()) {
                                onPasteKey(pasted)
                                pasted = ""
                            }
                        })
                    }
                    Hairline()
                    Action("Key from a file", R.drawable.ic_folder, onClick = onImportKeys, quiet = true, modifier = Modifier.fillMaxWidth())
                    Hairline()
                    Action("Test one tile", R.drawable.ic_play, onClick = onTestTiles, quiet = true, modifier = Modifier.fillMaxWidth())
                }
            }

            Group("Tracks") {
                Opens("Tracks", R.drawable.ic_track, under = "$trackCount", onClick = onTracks)
                Hairline()
                Opens("Folder for tracks and export", R.drawable.ic_folder, under = folderName, onClick = onChooseExportFolder)
                if (recording) {
                    Hairline()
                    Action(if (recordingPaused) "Resume recording" else "Pause recording", R.drawable.ic_play, onClick = onPause, quiet = true, modifier = Modifier.fillMaxWidth())
                }
            }

            // HELP, IN ENGLISH AND IN CROATIAN (v18): the same two pages as the web app's.
            Group("Help") {
                Opens("Help", R.drawable.ic_info, under = "how it works and how to use it", onClick = { onHelp("en") })
                Hairline()
                Opens("Pomoć", R.drawable.ic_info, under = "kako radi i kako se koristi", onClick = { onHelp("hr") })
            }

            // THE VERSION OPENS THE LATEST BUILD (29.9.2026, the rule for every Mantra app).
            Group("About") {
                Row(
                    Modifier.fillMaxWidth().clickable { runCatching { open.openUri(RELEASES) } }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Glyph(R.drawable.ic_info, Paint.Dim)
                    Column {
                        Words("ARKOD Layer · version $version", Paint.Sand, 15, TextAlign.Start)
                        Words(
                            "Cadastre: Državna geodetska uprava (uredjenazemlja.hr) · © OpenStreetMap contributors · mapsforge · Google",
                            Paint.Dim, 12, TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}

/** A titled card, as the phone's own settings draw one. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Words(
        text = title.uppercase(),
        colour = Paint.Dim,
        size = 11,
        align = TextAlign.Start,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paint.Card),
        content = content,
    )
}

@Composable
private fun Words(
    text: String,
    colour: Color,
    size: Int,
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
    )
}

/**
 * PARCEL VIEW (29.9.2026, v3), a long press on the parcels key: *"that settings dialog, we can turn
 * on or off some things. It's basically viewing settings."* What the parcels key shows, whether
 * only his own parcels are drawn whatever the key says, and whether the parcel field is on the
 * map. Settings words in English; the cadastre's words in Croatian.
 */
@Composable
fun ParcelViewFace(
    cadastreOn: Boolean,
    onCadastre: (Boolean) -> Unit,
    lines: ParcelStyle.Lines,
    onLines: (ParcelStyle.Lines) -> Unit,
    ownLines: Boolean,
    onOwnLines: (Boolean) -> Unit,
    cacheCount: Int,
    caching: Boolean,
    onCacheView: () -> Unit,
    onCaches: () -> Unit,
    onlyMine: Boolean,
    onOnlyMine: (Boolean) -> Unit,
    parcelSearchOn: Boolean,
    onParcelSearch: (Boolean) -> Unit,
    myParcelCount: Int,
    onMyParcels: () -> Unit,
    bookSize: Int,
    onClearBook: () -> Unit,
    onClose: () -> Unit,
) {
    var sureClear by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Paint.Ground)
                // A tap inside is the dialog's own; only a tap outside closes it.
                .clickable(enabled = true, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(R.drawable.ic_parcels, Paint.Sand)
                Words("Parcel view", Paint.Sand, 17, TextAlign.Start, Modifier.padding(start = 12.dp).weight(1f))
                IconAction(R.drawable.ic_close, null, onClick = onClose, tint = Paint.Sand)
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Group("On the map") {
                    Toggle("ARKOD layer", R.drawable.ic_parcels, on = cadastreOn, onChange = onCadastre)
                    Hint("The same as a tap on the Show/hide ARKOD layer key: off hides the cadastre completely. A tap on the map still outlines the čestica under it.")
                    Hairline()
                    Toggle("Only Moje čestice", R.drawable.ic_pin, on = onlyMine, onChange = onOnlyMine)
                    Hint("Your parcels are drawn and every other parcel is hidden. A tap on the Show/hide ARKOD layer key brings them all back.")
                    Hairline()
                    Toggle("Parcel search", R.drawable.ic_search, on = parcelSearchOn, onChange = onParcelSearch)
                    Hint("The čestica field under Google's search: a number, \"pl 1984\" or a name.")
                }
                // THE LINES (v5): *"different styles of lines and transparency of lines ... and thickness
                // of lines, because lines are covering the map too much."*
                Group("Lines") {
                    LinesControls(lines, onLines)
                }
                Group("Parcel caches") {
                    Action(
                        verb = if (caching) "Caching…" else "Cache this view",
                        icon = R.drawable.ic_save,
                        onClick = onCacheView,
                        enabled = !caching,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint("Every čestica on the screen with its posjedovni and vlasnički list, kept under a name, searchable with no signal. Zoom decides the area.")
                    Hairline()
                    Toggle("Own lines in caches", R.drawable.ic_layers, on = ownLines, onChange = onOwnLines)
                    Hint("Inside a cache the state's lines are taken away and the cache's own drawn: its colour, dashes and weight.")
                    Hairline()
                    Opens("Parcel caches", R.drawable.ic_save, under = if (cacheCount == 0) "none yet" else "$cacheCount kept", onClick = onCaches)
                }
                Group("Moje čestice") {
                    Opens("Moje čestice", R.drawable.ic_parcels, under = "$myParcelCount kept", onClick = onMyParcels)
                }
                Group("Imenik") {
                    Hint(
                        "$bookSize names from the posjedovni and vlasnički listovi opened on this phone. The " +
                            "state publishes no search by name, so the parcel field finds a person among these, " +
                            "and the list grows with every sheet you open."
                    )
                    if (bookSize > 0) {
                        Hairline()
                        Action(
                            verb = if (sureClear) "again: clear Imenik" else "Clear Imenik",
                            icon = R.drawable.ic_trash,
                            onClick = { if (sureClear) { onClearBook(); sureClear = false } else sureClear = true },
                            quiet = true,
                            danger = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * THE STATE'S LINES, AS HE WANTS THEM (v5): a colour (auto follows the map), how much of the ink is
 * kept, and the weight. Each choice redraws the ARKOD layer; the restyled tiles are kept per style.
 */
@Composable
private fun LinesControls(lines: ParcelStyle.Lines, onLines: (ParcelStyle.Lines) -> Unit) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1.4f).height(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (lines.colour == null) Look.Raised else Color.Transparent)
                    .border(1.dp, Look.Outline, RoundedCornerShape(8.dp))
                    .clickable { onLines(lines.copy(colour = null)) },
                contentAlignment = Alignment.Center,
            ) { Words("auto", if (lines.colour == null) Paint.Sand else Paint.Dim, 13) }
            LINE_COLOURS.forEach { option ->
                Box(
                    Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(option))
                        .border(1.dp, Look.Outline, RoundedCornerShape(8.dp))
                        .clickable { onLines(lines.copy(colour = option)) },
                    contentAlignment = Alignment.Center,
                ) { if (lines.colour == option) Glyph(R.drawable.ic_check, if (option == 0xFF111111L) Paint.Sand else Paint.Ground, size = 18.dp) }
            }
        }
        Words("transparency: how much of the line is kept", Paint.Dim, 12, TextAlign.Start)
        Choice(
            parts = ParcelStyle.OPACITIES.map { Part("$it") },
            chosen = ParcelStyle.OPACITIES.indexOf(lines.opacity).coerceAtLeast(0),
            onChoose = { onLines(lines.copy(opacity = ParcelStyle.OPACITIES[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
        Words("thickness", Paint.Dim, 12, TextAlign.Start)
        Choice(
            parts = ParcelStyle.Weight.entries.map { Part(it.word) },
            chosen = ParcelStyle.Weight.entries.indexOf(lines.weight),
            onChoose = { onLines(lines.copy(weight = ParcelStyle.Weight.entries[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
        Words("Dashed and dotted lines are drawn where a parcel cache is: the state sends its lines as pictures.", Paint.Dim, 12, TextAlign.Start)
    }
}

/** The state's lines in any of these, or auto: dark ink, sand, white, and four that no map is. */
private val LINE_COLOURS = listOf(0xFF111111L, 0xFFF2DDB4L, 0xFFFFFFFFL, 0xFFEF4444L, 0xFFFACC15L, 0xFF60A5FAL)

@Composable
private fun Hint(text: String) {
    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) { Words(text, Paint.Dim, 12, TextAlign.Start) }
}

/** "123.4 MB · 5210 ARKOD tiles · 840 sheets and answers · 310 outlines" (v12). */
private fun inventoryLine(): String {
    val i = ArkodCache.inventory()
    return "${Sniff.megabytes(i.bytes)} · ${i.tiles} ARKOD tiles · ${i.answers} sheets and answers · ${i.shapes} outlines"
}

/**
 * THE HELP (v18): *"add help section to both apps ... explaining how it works, what are the mechanisms,
 * and how to use it in both languages, croatian and english, so there should be 2 help files."*
 * assets/help/en.html and hr.html, the same pages as the web app's help/ (each links to the other).
 */
@Composable
fun HelpFace(lang: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Paint.Ground)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Words(if (lang == "hr") "Pomoć" else "Help", Paint.Sand, 17, TextAlign.Start, Modifier.padding(start = 4.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Paint.Card).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Words("✕", Paint.Sand, 18) }
            }
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        setBackgroundColor(0xFF0B0D10.toInt())
                        settings.javaScriptEnabled = false
                        loadUrl("file:///android_asset/help/$lang.html")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

