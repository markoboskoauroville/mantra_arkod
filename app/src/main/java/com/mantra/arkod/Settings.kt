package com.mantra.arkod

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
    compassOn: Boolean,
    onCompass: () -> Unit,
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
) {
    var theme by remember { mutableStateOf(store.themeName) }
    var googleId by remember { mutableStateOf(store.googleViewId) }
    var keysOpen by remember { mutableStateOf(store.opened("keys") || keyring.isEmpty()) }
    var prefetch by remember { mutableStateOf(store.prefetch) }
    var cacheLabel by remember { mutableStateOf("…") }
    var sureClear by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    val open = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        cacheLabel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ArkodCache.label() }
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
                Words("postavke", Paint.Sand, 17, TextAlign.Start, Modifier.padding(start = 4.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Paint.Card)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Words("✕", Paint.Sand, 18) }
            }

            Group("Moje čestice") {
                Opens("Moje čestice", R.drawable.ic_parcels, under = "$myParcelCount", onClick = onMyParcels)
            }

            Group("Offline karta") {
                val live = download
                when {
                    live?.running == true -> Action(
                        verb = "Preuzimam Hrvatsku",
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
                        under = if (MapDownload.isPresent(context)) "na telefonu" else "odabrana .map datoteka",
                        onClick = {},
                    )
                    else -> Action(
                        verb = "Preuzmi offline kartu",
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
                Action("Odaberi .map datoteku", R.drawable.ic_folder, onClick = onChooseMapFile, quiet = true, modifier = Modifier.fillMaxWidth())
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

            Group("Google karta") {
                Box(Modifier.padding(12.dp)) {
                    Choice(
                        parts = listOf(Part("karta"), Part("satelit"), Part("teren"), Part("hibrid")),
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
            Group("API ključevi") {
                Opens(
                    title = "Google ključevi",
                    icon = R.drawable.ic_key,
                    under = if (keyring.isEmpty()) "nijedan" else "${keyring.size}",
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
                            IconAction(R.drawable.ic_play, "provjeri", onClick = { onTestKey(key) })
                            IconAction(R.drawable.ic_trash, "obriši", onClick = { onRemoveKey(key) }, tint = Paint.Red)
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
                            if (pasted.isEmpty()) Words("zalijepite ključ (AIza…)", Paint.Dim, 14, TextAlign.Start)
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
                        IconAction(R.drawable.ic_check, "dodaj", onClick = {
                            if (pasted.isNotBlank()) {
                                onPasteKey(pasted)
                                pasted = ""
                            }
                        })
                    }
                    Hairline()
                    Action("Ključ iz datoteke", R.drawable.ic_folder, onClick = onImportKeys, quiet = true, modifier = Modifier.fillMaxWidth())
                    Hairline()
                    Action("Provjeri jednu pločicu", R.drawable.ic_play, onClick = onTestTiles, quiet = true, modifier = Modifier.fillMaxWidth())
                }
            }

            // THE CADASTRE KEPT ON THE PHONE (29.9.2026).
            Group("ARKOD na telefonu") {
                Toggle("Spremaj unaprijed", R.drawable.ic_save, on = prefetch, onChange = {
                    prefetch = it
                    store.prefetch = it
                })
                Hairline()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Words(cacheLabel, Paint.Dim, 12, TextAlign.Start, Modifier.weight(1f))
                    Action(
                        verb = if (sureClear) "ponovno: obriši" else "obriši",
                        icon = R.drawable.ic_trash,
                        onClick = {
                            if (sureClear) {
                                ArkodCache.clear()
                                cacheLabel = ArkodCache.label()
                                sureClear = false
                            } else {
                                sureClear = true
                            }
                        },
                        quiet = true,
                        danger = true,
                    )
                }
            }

            Group("Tragovi") {
                Opens("Tragovi", R.drawable.ic_track, under = "$trackCount", onClick = onTracks)
                Hairline()
                Opens("Mapa za tragove i izvoz", R.drawable.ic_folder, under = folderName, onClick = onChooseExportFolder)
                if (recording) {
                    Hairline()
                    Action(if (recordingPaused) "Nastavi snimanje" else "Pauziraj snimanje", R.drawable.ic_play, onClick = onPause, quiet = true, modifier = Modifier.fillMaxWidth())
                }
            }

            Group("Na karti") {
                Toggle("Kompas", R.drawable.ic_compass, on = compassOn, onChange = { onCompass() })
            }

            // THE VERSION OPENS THE LATEST BUILD (29.9.2026, the rule for every Mantra app).
            Group("O aplikaciji") {
                Row(
                    Modifier.fillMaxWidth().clickable { runCatching { open.openUri(RELEASES) } }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Glyph(R.drawable.ic_info, Paint.Dim)
                    Column {
                        Words("Mantra ARKOD · verzija $version", Paint.Sand, 15, TextAlign.Start)
                        Words(
                            "Katastar: Državna geodetska uprava (uredjenazemlja.hr) · © OpenStreetMap contributors · mapsforge · Google",
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
