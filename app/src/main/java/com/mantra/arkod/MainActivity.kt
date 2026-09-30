package com.mantra.arkod

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile

/**
 * ONE ACTIVITY. The map is the app, the cadastre is on it, and his parcels are one press away.
 *
 * Mantra ARKOD (29.9.2026) is Mantra Trail with the trail taken out: no routes, no points, no
 * bare view. What is left is the map, the parcels over it, and the sheets behind them.
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var locator: Locator
    private lateinit var sensors: Sensors
    private var canvas: VtmCanvas? = null

    private var pendingRecord by mutableStateOf(false)
    private var pendingSave: Pair<java.io.File, String>? = null
    private var tileAnswer: String? = null

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val fine = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fine) {
            locator.start()
            if (pendingRecord) startRecording()
        } else {
            Trail.say("Bez dopuštenja za lokaciju nema položaja na karti ni traga")
        }
        pendingRecord = false
    }

    private val pickMapFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        store.mapFileUri = uri.toString()
        UiTick.bump()
        Trail.say(
            canvas?.show(Layers.OFFLINE)
                ?: "Datoteka je odabrana. Odaberite offline kartu da je vidite."
        )
    }

    /**
     * A key arrives as a FILE and is read by shape, never by eye (secrets.md 2). What comes back
     * to the screen is a count; the value itself never reaches the interface or a log.
     */
    private val pickKeyFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                Trail.say("The file could not be read")
                return@registerForActivityResult
            }
            addKeyText(text)
        } catch (e: Exception) {
            Trail.say("Import failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * A KEY ARRIVES AS TEXT, from a file or pasted into the Google help (29.9.2026), and is read
     * BY SHAPE, never by eye. What comes back to the screen is a count and Google's verdict; the
     * value itself never reaches the interface or a log.
     */
    private fun addKeyText(text: String) {
        val found = Keys.parse(text)
        if (found.isEmpty()) {
            Trail.say("There is no Google key in that text (it starts with AIza…)")
            return
        }
        store.keys = store.keys + found.associate { it.provider to it.key }
        GoogleTiles.forget()
        addKeysFrom(found.filter { it.provider == Keys.Provider.GOOGLE }.map { it.key })
    }

    // MOJE ČESTICE FROM A FILE (v8).
    private val pickMarksFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) MarkImports.offer(this, uri)
    }

    private val pickExportFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        store.exportTreeUri = uri.toString()
        // THE FOLDER'S OWN NAME IS KEPT, not just its address. "chosen" told him nothing; a
        // settings row that says Documents/Tracks is a row he can act on (15.9.2026).
        store.exportFolderName = DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment
        UiTick.bump()
        // Whatever was waiting for a folder goes now, which may be a track from the manager
        // rather than the last one recorded.
        val waiting = pendingSave
        pendingSave = null
        if (waiting != null) saveRecording(waiting.first, waiting.second)
    }

    /**
     * THE WALK IS PUT IN THE FOLDER HE CHOSE, under whatever he called it in the popup. There is
     * no export any more (15.9.2026): a track that is already in the folder has nowhere to go.
     */
    /**
     * THROW THE WALK AWAY, because he pressed the key that says so (17.9.2026).
     *
     * This used to save it under its own name: a walk is hard-won and losing one to a stray press
     * seemed the worse mistake. He pressed cancel meaning to discard and got a saved track, which
     * is the worse mistake — a key that does the opposite of its word teaches him not to trust any
     * of them. The recording is the app's own working file, not yet in his folder, so deleting it
     * takes nothing else with it.
     */
    private fun discardRecording(file: java.io.File) {
        val gone = runCatching { file.delete() }.getOrDefault(false)
        Trail.say(if (gone) "Trag je odbačen" else "Trag se nije mogao obrisati")
        UiTick.bump()
    }

    private fun saveRecording(file: java.io.File, name: String) {
        if (store.exportTreeUri == null) {
            pendingSave = file to name
            report("Odaberite mapu za tragove")
            pickExportFolder.launch(null)
            return
        }
        report("Spremam ${name}…")
        lifecycleScope.launch {
            val problem = withContext(Dispatchers.IO) { Folder.save(this@MainActivity, store, file, name) }
            report(problem ?: "Spremljeno u ${Folder.label(this@MainActivity, store)}: $name")
            UiTick.bump()
        }
    }


    private fun deleteTrack(entry: Folder.Entry) {
        report(Folder.delete(this, entry) ?: "Obrisano: ${entry.name}")
        UiTick.bump()
    }

    /** He renames a NAME; the extension is the disk's business and is kept (15.9.2026). */
    private fun renameTrack(entry: Folder.Entry, newName: String) {
        report(Folder.rename(this, entry, newName) ?: "Preimenovano u $newName")
        UiTick.bump()
    }


    /** Read a saved walk back out of the folder and draw it over the map. */
    private fun showTrack(entry: Folder.Entry) {
        lifecycleScope.launch {
            val points = withContext(Dispatchers.IO) {
                Folder.read(this@MainActivity, entry)?.let { GpxRead.points(it) } ?: emptyList()
            }
            if (points.isEmpty()) {
                report("Iz ${entry.name} se nije mogla pročitati nijedna točka")
                return@launch
            }
            Canvases.drawSavedTrack(points, store.trackColour)
            Trail.say(
                "${entry.name}: ${points.size} točaka, " +
                    Geo.formatDistance(TrackMath.stats(points).distanceM)
            )
        }
    }

    /**
     * THE OFFLINE MAP OF CROATIA, ONE PRESS (29.9.2026). The progress goes to MapDownload.live,
     * which the middle of the screen shows while the offline map is chosen; the download goes on
     * whatever map he looks at meanwhile, and the offline map draws itself when it is whole.
     */
    private fun fetchOfflineMap() {
        lifecycleScope.launch {
            val problem = MapDownload.fetch(this@MainActivity) { }
            if (problem == null) {
                Trail.say("The offline map of Croatia is on the phone")
                if (Layers.byId(store.layerId).kind == LayerKind.VECTOR_FILE) canvas?.show(Layers.OFFLINE)
            }
            UiTick.bump()
        }
    }

    /** Put every Google key in the file on the ring, then ask Google about each of them. */
    private fun addKeysFrom(found: List<String>) {
        val before = store.keyring
        store.keyring = Keyring.add(before, found)
        val added = store.keyring.size - before.size
        Trail.say(
            when {
                added == 0 && found.isEmpty() -> "There is no Google key in it"
                added == 0 -> "That key is already added"
                else -> "Added: $added. Testing…"
            }
        )
        UiTick.bump()
        store.keyring.filter { it.verdict == Keyring.Verdict.UNTRIED }.forEach { testKey(it) }
    }

    /** Ask Google about one key and write what it said beside it. */
    private fun testKey(key: Keyring.Key) {
        lifecycleScope.launch {
            val answer = GoogleTiles.session(MapLayer.GoogleView.NORMAL, key.value)
            val verdict = when {
                answer.token != null -> Keyring.Verdict.GOOD
                answer.problem?.startsWith(GoogleTiles.UNREACHABLE) == true -> Keyring.Verdict.UNREACHABLE
                else -> Keyring.Verdict.REFUSED
            }
            store.keyring = Keyring.withVerdict(
                store.keyring,
                key.value,
                verdict,
                answer.problem ?: "works",
                System.currentTimeMillis(),
            )
            Trail.say("${key.label}: ${answer.problem ?: "works"}")
            // A key that works puts Google's map up at once, if Google is what he is looking at.
            UiTick.bump()
        }
    }

    private fun removeKey(key: Keyring.Key) {
        store.keyring = Keyring.remove(store.keyring, key.value)
        Trail.say("${key.label} removed")
        UiTick.bump()
    }

    private fun testTiles() {
        val layer = Layers.byId(store.layerId)
        Trail.say("Asking ${layer.name} for one map tile…")
        lifecycleScope.launch {
            val googleKey = store.key(Keys.Provider.GOOGLE)
            val session = if (layer.kind == LayerKind.GOOGLE_TILES && googleKey != null) {
                GoogleTiles.session(layer.googleView ?: MapLayer.GoogleView.NORMAL, googleKey).token
            } else {
                null
            }
            tileAnswer = TileTest.check(layer, session, layer.provider?.let { store.key(it) })
            Trail.say(tileAnswer)
            UiTick.bump()
        }
    }

    private fun trackFolder(): java.io.File = java.io.File(filesDir, "tracks").apply { mkdirs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // WHO IS ASKING, told once (17.9.2026). The key is restricted to this package and this
        // signing certificate; a web call that does not say so is refused.
        GoogleTiles.context = applicationContext
        // THE CADASTRE KEPT ON THE PHONE (29.9.2026), held under its limit from the first moment.
        ArkodCache.init(applicationContext)
        ParcelCaches.init(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) { ArkodCache.prune() }
        // From targetSdk 35 Android draws every app edge to edge and insets nothing for us, so
        // the window is the whole glass and the bars are painted over whatever is under them.
        // The screen applies safeDrawingPadding; this line is the other half of the same fact.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        locator = Locator(this)
        sensors = Sensors(this)

        // A file sent to the app, when it opens with one (v8).
        MarkImports.uriOf(intent)?.let { MarkImports.offer(this, it) }

        setContent {
            ArkodApp(
                store = store,
                sensors = sensors,
                version = BuildConfig.VERSION_NAME,
                onCanvas = { canvas = it },
                onWhereAmI = ::whereAmI,
                onRecord = ::toggleRecording,
                onPause = ::togglePause,
                onChooseMapFile = { pickMapFile.launch(arrayOf("*/*")) },
                onChooseExportFolder = { pickExportFolder.launch(null) },
                onImportKeys = { pickKeyFile.launch(arrayOf("*/*")) },
                onPasteKey = ::addKeyText,
                onFetchOfflineMap = ::fetchOfflineMap,
                tracks = { Folder.list(this, store) },
                folderLabel = Folder.label(this, store),
                onRenameJustFinished = ::saveRecording,
                onDiscardRecording = ::discardRecording,
                onDeleteTrack = ::deleteTrack,
                onRenameTrack = ::renameTrack,
                onShowTrack = ::showTrack,
                onTestTiles = ::testTiles,
                onTestKey = ::testKey,
                onRemoveKey = ::removeKey,
                onImportMarks = { pickMarksFile.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                onShareMarks = { group, marks -> MarkImports.share(this, group, marks) },
            )
        }

        if (locator.hasPermission()) locator.start()
        askForNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        sensors.start()
        if (locator.hasPermission()) locator.start()
        canvas?.resume()
    }

    override fun onPause() {
        sensors.stop()
        canvas?.remember()
        canvas?.pause()
        // The fixes keep coming while a recording is running, because the service is asking for
        // them too. With nothing recording there is no reason to keep the receiver warm.
        if (!Trail.isRecording()) locator.stop()
        super.onPause()
    }

    override fun onDestroy() {
        canvas?.destroy()
        super.onDestroy()
    }

    private fun whereAmI() {
        if (!locator.hasPermission()) {
            askLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        val fix = Trail.fix.value
        if (fix == null) {
            Trail.say("Još nema položaja. Pod krovom to može potrajati minutu.")
            return
        }
        // WHICHEVER MAP HE IS LOOKING AT (17.9.2026). This centred the activity's own VTM canvas
        // by name, so on Google's map the yellow key did nothing at all — the offline canvas was
        // dutifully centred somewhere he could not see.
        Canvases.centreOn(fix)
        sensors.updateDeclination(fix)
    }

    private fun toggleRecording() {
        if (Trail.isRecording()) {
            TrailService.send(this, TrailService.ACTION_STOP)
            return
        }
        if (!locator.hasPermission()) {
            pendingRecord = true
            askLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        startRecording()
    }

    private fun startRecording() {
        TrailService.start(this, TrailService.defaultName())
    }

    private fun togglePause() {
        if (!Trail.isRecording()) return
        TrailService.send(
            this,
            if (Trail.paused.value) TrailService.ACTION_RESUME else TrailService.ACTION_PAUSE,
        )
    }

    /**
     * The finished track, copied out of the app's own folder into one the person chose — so it is
     * still there after an uninstall, and so it can be opened by anything else on the phone.
     */


    /** Say it on the map's note line AND in the track manager, since either may be in front. */
    private fun report(message: String?) {
        Trail.say(message)
        Trail.sayInManager(message)
    }


    private fun askForNotificationPermission() {
        // POST_NOTIFICATIONS exists from 33. Guarded with >=, never with a negated <, because
        // Lint reads the negated form as an inlined API use and fails the build (android-app.md 5).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) askLocation.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    /** A file sent while the app is already open (singleTask): it arrives here (v8). */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        MarkImports.uriOf(intent)?.let { MarkImports.offer(this, it) }
    }

}
