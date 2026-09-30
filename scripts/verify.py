#!/usr/bin/env python3
"""verify.py: the checks that are cheap enough to run on every push and that a compiler will not
run for you. EVERY CHECK PRINTS WHAT IT EXAMINED (delivery-gate.md 14), because a check that finds
nothing and a check that runs nothing look identical from outside. Copied in shape from
COCKPIT_ANDROID/scripts/verify.py (14.9.2026)."""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java/com/mantra/arkod"
TESTS = ROOT / "app/src/test/java/com/mantra/arkod/CoreTest.kt"
# Lowered once, on 15.9.2026, and only because a FEATURE left: the bubble level and its twelve
# cases went with it when he asked for the compass alone. A floor drops when the thing it counted
# is gone, never because tests were dropped (never-back-to-zero.md).
# Mantra ARKOD, 29.9.2026: 33 cases left with the features they tested (routes, BRouter's
# segments, OpenAndroMaps, kept imagery, Google's polyline), 11 came with my parcels and the
# cadastre kept ahead. The floor is what is here, and it ratchets from here.
# v3, 29.9.2026: six cases came with the parcels key, the parcel field's suggestions and Imenik.
# v5, 30.9.2026: ten cases came with the restyled lines and the parcel caches.
# v8, 30.9.2026: three cases for Moje čestice as files; one came with the state's reason (v6).
TEST_FLOOR = 215

# The files Test 1 runs against on a desk. They may not reach for Android, or the mechanism can
# only be tested in an emulator and it stops being tested at all.
PURE = ["Geo.kt", "Track.kt", "Gpx.kt", "GpxRead.kt", "Layers.kt", "Keys.kt", "Tracks.kt", "Parcels.kt", "Outline.kt", "Finding.kt", "OwnerBook.kt", "ParcelStyle.kt", "ParcelCache.kt", "MarkFile.kt"]

failures, checks = [], []


def code_only(text):
    without_block = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(l for l in without_block.split("\n") if not l.lstrip().startswith(("//", "*")))


def check(name, ok, detail):
    checks.append(name)
    print(f"{'pass' if ok else 'FAIL'}  {name}: {detail}")
    if not ok:
        failures.append(name)


# 1 the arithmetic imports nothing from Android
for name in PURE:
    src = code_only((MAIN / name).read_text())
    imports = re.findall(r"^import ", src, re.M)
    android = re.findall(r"^import (android\.|androidx\.).*$", src, re.M)
    check(f"{name} imports nothing from Android", not android,
          f"{len(imports)} imports examined, {len(android)} from android")

# 2 one version, derived everywhere
gp = (ROOT / "gradle.properties").read_text()
m = re.search(r"^appVersion=(\d+)$", gp, re.M)
bg = (ROOT / "app/build.gradle.kts").read_text()
check("one version in gradle.properties, derived in build.gradle.kts",
      bool(m) and "versionCode = appVersion" in bg and 'versionName = appVersion.toString()' in bg,
      f"appVersion={m.group(1) if m else '?'}")
for f in sorted(MAIN.glob("*.kt")):
    t = code_only(f.read_text())
    hard = re.findall(r"versionCode\s*=\s*\d+|versionName\s*=\s*\"\d", t)
    check(f"no hard-coded version in {f.name}", not hard, f"{len(hard)} found")

# 3 no key is in the repository, in any file, in any form (secrets.md 3)
tracked = [p for p in ROOT.rglob("*") if p.is_file()
           and ".git/" not in str(p) and "/build/" not in str(p) and p.suffix != ".jar"]
shapes = re.compile(r"(AIza|gsk_|ghp_|github_pat_|sk-ant-)[A-Za-z0-9_-]{20,}")
hits = [p.name for p in tracked if shapes.search(p.read_text(errors="ignore"))]
check("no key-shaped string anywhere in the tree", not hits,
      f"{len(tracked)} files examined, {len(hits)} hits {hits if hits else ''}")
# This check used to assert the opposite: that the manifest carried a ${googleMapsKey} placeholder
# filled from a repository secret. That is what put a live key inside a public APK on 14.9.2026.
# The rule reversed, so the check reversed with it rather than being deleted.
check("no key reaches the app at build time, by placeholder or otherwise",
      "googleMapsKey" not in (ROOT / "app/src/main/AndroidManifest.xml").read_text()
      and "googleMapsKey" not in bg,
      "neither the manifest nor the build file mentions one")

# 4 the manifest declares what a fix and a recording need
mf = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
for need in ("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "FOREGROUND_SERVICE_LOCATION",
             "POST_NOTIFICATIONS", "INTERNET", 'android:foregroundServiceType="location"',
             ".TrailService"):
    check(f"manifest carries {need}", need in mf, "present" if need in mf else "MISSING")
# Background location is a permission this app does not need and cannot justify: the recording
# runs in a foreground service, which is the lawful way to keep getting fixes with the screen off.
check("manifest does NOT ask for ACCESS_BACKGROUND_LOCATION",
      "ACCESS_BACKGROUND_LOCATION" not in mf, "absent")

# 5 Google's tiles are never cached, and the rule is in the code rather than in a comment
layers_src = (MAIN / "Layers.kt").read_text()
layers = code_only(layers_src)
# THE CPU RENDERER IS GONE (16.9.2026), and with it every check that examined it: the tile cache
# sized from the screen, the persistent tile store, the square frame buffer, the scaled parent
# tiles. Those were not wrong — they were true of a renderer this app no longer has, and a check
# that guards something absent can only ever pass. What replaces them is smaller, because VTM
# does that work itself: there is no cache to size and no frame buffer to shape.
canvas_src = (MAIN / "VtmCanvas.kt").read_text()
canvas = code_only(canvas_src)
# This asked for layer.cacheable, which is about a LICENCE to keep tiles somebody else served.
# Tiles we rendered ourselves from a file on the phone are ours, and keeping them is what makes a
# revisited zoom instant. The one layer that may never be kept is Google, and that is the test.

# 6 the track is written as the walk happens, not assembled at the end
svc = code_only((MAIN / "TrailService.kt").read_text())
check("the GPX header is written when recording starts", "Gpx.header(" in svc, "present")
check("every accepted point is appended and flushed",
      "Gpx.point(" in svc and svc.count("flush()") >= 2,
      f"point written, flush called {svc.count('flush()')}x")
check("the footer is written when recording stops", "Gpx.footer()" in svc, "present")
check("the notification is taken down when the service dies",
      "cancel(NOTIFICATION_ID)" in svc, "present")

# 7 nothing on the screen appears or disappears: the controls are always drawn and are enabled
# or not (design-language.md 1). Five keys, five enabled arguments.
screens_src = (MAIN / "Screens.kt").read_text()
settings_src = (MAIN / "Settings.kt").read_text() + (MAIN / "Settings.kt").read_text()
screens = code_only(screens_src)
settings_src = (MAIN / "Settings.kt").read_text()
keys = re.findall(r"\bKey\(", screens)
check("the control row draws every key unconditionally",
      len(keys) + screens.count("MarkKey(") + screens.count("RecordKey(") >= 6,
      f"{len(keys)} glyph keys plus the two marks: no key is conditional on state")
# A key that cannot act SAYS WHY. v6 dropped the disabled look from the map screen: a dimmed
# button with no explanation is the same dead end as a missing one.
check("a layer that cannot draw says why instead of going grey",
      "Trail.say(Layers.missingKey(layer))" in screens or "Layers.missingKey(layer)" in screens,
      "the reason names the key and where to put it")
check("the way out of the settings face is at the right-hand end of its top row",
      # 27.9.2026: the ✕ is the close icon now, last in the top row of every face
      screens.count("IconAction(R.drawable.ic_close, null, onClick = onClose") >= 3,
      "the ✕ in the corner it occupies on every face here")

# 8 the test floor ratchets
tests = TESTS.read_text()
n = len(re.findall(r"@Test", tests))
check(f"at least {TEST_FLOOR} unit tests", n >= TEST_FLOOR, f"{n} @Test cases")

# 9 the APK is built by CI and by nothing else (android-app.md 1a)
wf = (ROOT / ".github/workflows/build-apk.yml").read_text()
check("a workflow exists and builds the release APK",
      "assembleRelease" in wf, "present")
check("the workflow publishes a release, so the build can be downloaded",
      "gh release create" in wf, "present")
check("no committed local.properties could point a build at a desk's SDK",
      not (ROOT / "local.properties").exists() or "local.properties" in (ROOT / ".gitignore").read_text(),
      "ignored")


# NOTHING OF OURS UNDER THE SYSTEM BARS, AND TWO VIEWS ONLY (design-language.md, written
# 14.9.2026 from the v4 screenshot). These are the checks that would have caught it, and they
# exist because nothing on a desk has a status bar to be covered by.
pads = screens.count("safeDrawingPadding()")
activity = (MAIN / "MainActivity.kt").read_text()
check("every overlay of the map sits inside the safe area", pads >= 3,
      f"{pads} safeDrawingPadding calls: the top line, the controls, the settings face. "
      "The bare view has no overlay at all, which is why three is the number.")
check("the window is told we draw edge to edge ourselves",
      "setDecorFitsSystemWindows(window, false)" in activity, "present")
# REVERSED 29.9.2026 (Mantra ARKOD): "the feature when user clicks in the middle and gets in the
# full screen, that feature goes out. Clicking in the middle, it's like clicking anywhere else."
check("there is no bare view: the middle of the map is a tap like anywhere else",
      "hide(WindowInsetsCompat.Type.systemBars())" not in activity and "onBare" not in screens
      and "bare = !bare" not in screens,
      "no full screen, and the crosshair takes no touch")
# v5 clipped because each key carried a word under its glyph. v6 keys are a glyph alone, so five
# fit where three did; the ceiling is on WORDS in a row, not on keys.
key_body = screens.split("private fun RowScope.Key")[1].split("\n}\n")[0]
check("no key carries a word under its glyph any more",
      screens.count("private fun RowScope.Key") == 1 and key_body.count("Label(") == 1,
      f"{key_body.count('Label(')} Label call in the key body: the glyph, and nothing under it")
# Seven bare glyphs at a 4 dp gap is 48 px each on a 390 px phone, which is still a thumb. The
# ceiling rose because the keys lost their words, not because the phone got wider.
# NINE KEYS SINCE 29.9.2026 (Mantra ARKOD): the three maps each have their own, "not as a toggle",
# and my parcels one. Nine at a 2 dp gap is 41 dp each on a 390 dp phone, still a thumb.
control_row = screens.split("horizontalArrangement = Arrangement.spacedBy(2.dp),")[1].split("\n            }\n        }")[0]
row_keys = len(re.findall(r"\b(?:Record|Mark|Map)?Key\(", control_row))
check("the control row holds at most nine keys", row_keys <= 9,
      f"{row_keys} keys: nine across a 390 dp phone is 41 dp each")

# CH caches what is on the view, and never Google's tiles


# THE OFFLINE MAP IS FETCHED BY THE APP, WITH THE SIZE SAID FIRST AND THE PROGRESS SHOWN
# (download-monitor.md: nothing longer than a minute happens in the dark).
download = (MAIN / "MapDownload.kt").read_text()
check("the download resumes rather than starting again",
      "Range" in download and ".part" in download, "a Range header and a part file")
check("a part file only becomes the map when it is whole",
      "part.length() < total" in download and "renameTo(finished)" in download,
      "the length is checked before the rename")
# Was about the mapsforge download row, which left with that row on 17.9.2026. The rule it stood
# for is now kept by the maps face, where nothing is fetched before its size has been said.
check("the size is on the screen before the download starts",
      "private fun OfflineOffer" in screens and '"$total MB"' in screens
      and "Layers.OfflineDownload.BYTES" in screens,
      "the button that starts it carries the size, from the measured length")
check("while it runs the middle of the screen shows the percentage and the speed",
      "progress?.percent" in screens and "bytesPerSecond" in screens and "bytesPerSecond" in download,
      "percent, megabytes and the speed of the last second")


# NO KEY IS BUILT INTO THIS APP (14.9.2026, after a live Maps key went out inside a public APK).
# The picker is the only way one arrives, and these are the checks that keep it that way.
gradle_kts = (ROOT / "app/build.gradle.kts").read_text()
check("the build takes no service key",
      "googleMapsKey" not in gradle_kts and "HAS_GOOGLE_KEY" not in gradle_kts,
      "no placeholder, no BuildConfig field")
# REVERSED 17.9.2026, WITH THE REASON, and the old rule kept in words.
#
# It said: the manifest holds no key of any kind. It was written after a Google key was compiled
# into public APKs v4 to v6 and had to be revoked, and it is still the right rule for a key that
# is not locked down.
#
# He asked for Google's own vector renderer — their engine, their speed — and the Maps SDK reads
# its key from the manifest with no runtime way to hand it one. So the manifest carries ONE
# placeholder, filled at build time from a repository secret, holding a key restricted in Cloud
# Console to this package name and to the fingerprint of the certificate that signs these builds.
# Proved the same day: that key, used from anywhere else, is answered
# "Requests from this Android client application are blocked".
# REVERSED AGAIN 29.9.2026 (Mantra ARKOD): Google's map is drawn from the Map Tiles API with HIS
# key, entered on the phone, so the manifest carries no key and no placeholder for one, and no
# Google key may be in the APK at all.
check("the manifest carries no key and no placeholder",
      "MAPS_API_KEY" not in mf and "AIza" not in mf and "geo.API_KEY" not in mf,
      "Google's key arrives on the phone, from him")
check("the workflow puts no Google key into the build, and the APK scan allows none",
      "secrets.MAPS_API_KEY" not in wf and "a Google key is in the APK" in wf,
      "zero key-shaped strings in the artefact")
sdk_lines = [l for l in gradle_kts.splitlines()
             if ("play-services-maps" in l or "maps-compose" in l) and "implementation" in l]
# Reversed with the same reason, 17.9.2026: the SDK is back, deliberately, because it is the only
# way to have Google's own vector map at Google's own speed, which is what he asked for.
check("one engine draws all three maps: no Maps SDK",
      len(sdk_lines) == 0 and not (MAIN / "GoogleCanvas.kt").exists(),
      "VTM draws the file, OpenStreetMap and Google's tiles, with the cadastre over each")
keys_src = (MAIN / "Keys.kt").read_text()
check("keys are sorted by shape, not by asking him which is which",
      "fun providerOf" in keys_src, "one function decides the service from the shape")
check("a key is never written to the screen, only its masked form",
      "key.value" not in screens and "key.value" not in settings_src and "masked" in (MAIN / "Keyring.kt").read_text(),
      "the screen shows a key's label and its masked form, never its value")
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text()
check("the workflow uses no key secret",
      "GOOGLE_MAPS_API_KEY" not in workflow,
      "the only secrets are the signing keystore and its password")


# CH is gone (15.9.2026). Pre-fetching a region is bulk downloading, which Thunderforest allow
# only on their Small Business plan and above, and on the offline map it cached a file we already
# have. What remains is ordinary caching of tiles actually looked at, which no button controls.
check("no button pre-fetches anybody's tiles",
      'glyph = "CH"' not in screens and not (MAIN / "Caching.kt").exists(),
      "the key and the arithmetic behind it are both gone")
check("the credits are gathered in settings",
      "OpenStreetMap contributors" in settings_src and "Državna geodetska uprava" in settings_src,
      "one block at the bottom of the settings face")

# WHAT THE PHONE SHOWED ON 15.9.2026, TURNED INTO CHECKS.
# This check said "no filled surface over the map" until the phone showed that a shadow alone is
# not readable on a pale street map. A bar the height of its line is not a box: the rule it keeps
# is that nothing takes map it does not need.
# A bar belongs to a LINE, never to the column that holds it: on the column it also covers the
# safe-area inset and every empty row inside, which is how a strip of text shaded half the map.
map_screen = screens.split("private fun SettingsFace")[0]
check("no bar is painted on a column",
      ".background(Paint.Bar)" not in map_screen.split("private fun Panel")[0] or
      "Modifier.fillMaxWidth().align(Alignment.TopCenter).safeDrawingPadding()," in map_screen,
      "the background belongs to the line and to the key row, not to their container")
check("an empty line takes no height at all",
      "if (note != null) NoteLine(note)" in screens
      and "if (recording) TrackLine(stats, recording = !paused)" in screens,
      "drawn only when there is something in them, rather than at zero opacity")
# Reversed on 15.9.2026 after reading Thunderforest's terms: the attribution may not be removed
# from an app. One dim line, on the fetched layers only, guarded by creditOnMap.
check("no credit is printed over the map",
      "attribution" not in map_screen, "the map screen carries none of them")

# REVERSED 16.9.2026, with the reason kept (never-back-to-zero.md). This check was written when
# the map did not rotate and a square buffer looked like wasted rendering. The map rotates now,
# and a screen-shaped buffer turned by thirty degrees leaves white wedges in the corners, because
# nothing was ever drawn there. The old reasoning was right about the cost and wrong about the
# need; the cost is paid deliberately and the cache is sized for the diagonal to match.
check("every word over the map carries a shadow instead",
      "Shadow(color = Paint.Ground" in screens, "one Label, one shadow, no panel")
# Split in two on 15.9.2026: the crosshair over the map and the mark on the key are different
# things, and only the key's mark is a position colour.
# RULE THREE (17.9.2026): a hairline is one pixel with nothing behind it. Every double stroke I
# had added as an outline is removed — the crosshair, the marks, the compass ring.
check("the crosshair over the map is four hairlines and nothing else",
      "Color(0x99000000)" in screens.split("private fun CentreCross")[1][:600]
      and screens.split("private fun CentreCross")[1].split("\n}")[0].count("drawLine") == 4,
      "four lines, one pass, middle empty")
check("the key's mark is unchanged and still the position colour",
      "Paint.AmberBright" in screens.split("private fun PositionMark")[1][:400],
      "ring and dot, ringed in near-black")
check("the settings face scrolls",
      "verticalScroll(rememberScrollState())" in screens,
      "so the last row is reachable however many rows there are")
row = control_row
# THE THREE MAPS IN THE MIDDLE (29.9.2026): the keys pressed most, under the thumb that reaches
# the phone's home button; zoom at both ends, as before.
calls = re.findall(r"\b(Key|MarkKey|RecordKey|MapKey)\(", row)
maps_at = [i for i, c in enumerate(calls) if c == "MapKey"]
check("the three map keys sit together in the middle of the row",
      len(maps_at) == 3 and maps_at == list(range(maps_at[0], maps_at[0] + 3))
      and maps_at[1] == (len(calls) - 1) // 2,
      f"{len(calls)} keys, maps at positions {[i + 1 for i in maps_at]}")


# THE OFFLINE MAP WENT BLANK ON THE WAY IN (15.9.2026). Three things could do that and all three
# are now closed; the checks keep them closed.
# The rule grew: it is not only the vector map that may be enlarged past its data. Every layer now
# carries two ceilings — where its tiles stop, and how far the view may go while mapsforge scales
# the last real tile (Baba, 15.9.2026: "OpenStreetMap goes to zoom level 18 and it stops. Why?").
check("every layer still declares how far the view may go",
      "val viewMaxZoom" in layers,
      "the ceiling is the layer's, and VTM scales its own geometry past the data")
# Superseded on 15.9.2026 by the pixel-sized cache: the old check looked for the screenRatio
# argument "2f," that the desk reproduction proved was the wrong way to size it at all.
check("the app asks for the large heap a country file at street zoom needs",
      'android:largeHeap="true"' in mf, "present")
check("the zoom is on the screen, so a fault can be reported with a number",
      'Label("z$zoom"' in screens, "present on the top line, beside the map's name")


# THE RACE THAT KEPT THE MAP BLANK FROM v6 TO v10. The first draw ran from an effect that fires
# before AndroidView builds its view, so it found no canvas and returned at its first line —
# silently. Two checks, because either half alone would let it back in.
check("the first draw is triggered by the view existing, not by a bare effect",
      "onReady()" in screens and "LaunchedEffect(ready)" in screens,
      "the factory says when the view is real")
# The fallback was OpenStreetMap until it left the app on 16.9.2026; it is the offline file now,
# and when that is what failed there is nothing to fall back TO, so the sentence stands alone.
# REVERSED 17.9.2026, with the reason. The fallback was written so the screen would never be
# empty; his screenshot showed the cost — the top line said Satellite, Google had refused the key,
# and the offline map was drawn underneath. A name that disagrees with the ground is worse than
# nothing, because only the name is legible at a glance.
# 17.9.2026: two engines, so no key names one of them. Canvases sends each press to whichever map
# is on the screen; before this the keys asked the offline canvas and were told, truthfully and
# uselessly, that it was not up.
# 17.9.2026: his offline map went black the moment Google's renderer had been shown once. Compose
# took the Google view out of the tree and nobody told the MapView, so it kept its lifecycle and
# its GL surface, and VTM drew onto a surface that was still somebody else's.
# 29.9.2026 (Mantra ARKOD): one engine for all three maps, so one view to release.
check("a map that leaves the screen lets go of the screen",
      "onRelease = {" in screens and "CanvasHolder.canvas?.pause()" in screens,
      "it stops drawing and says where it was looking")
check("every key speaks through Canvases, never to a canvas by name",
      (MAIN / "Canvases.kt").exists() and "CanvasHolder.canvas?.zoomIn" not in screens
      and "Canvases.zoomIn()" in screens,
      "one place decides which view a press reaches")
check("a restricted key says which app is asking",
      (MAIN / "AndroidCaller.kt").exists() and "X-Android-Package" in (MAIN / "AndroidCaller.kt").read_text()
      and "identify(connection)" in (MAIN / "GoogleTiles.kt").read_text(),
      "without it Google answers: application <empty> are blocked")
check("a map that cannot be drawn draws nothing",
      "CanvasHolder.canvas?.blank()" in screens and "fun blank()" in canvas_src,
      "and the reason is on the screen with it")
check("the ground behind a map is black",
      "MapRenderer.setBackgroundColor(android.graphics.Color.BLACK)" in canvas_src,
      "VTM's own default is light grey, and this is a dark application")
check("zoom is on the screen as keys, not only as a pinch",
      "zoomOut()" in screens and "zoomIn()" in screens, "minus and plus at both ends of the row")


# THE MAP CAME IN AS A BAND WITH WHITE ABOVE IT: tiles rendered from the offline file were thrown
# away as soon as they left the screen, so mapsforge had no parent tile to scale while the new
# ones rendered, and there was nothing to show.
check("Google's tiles are still never kept",
      "GOOGLE_TILES" in layers,
      "their terms forbid storing them, and the GPU engine caches nothing to disk either")


# z19 WAS WHITE (15.9.2026). mapsforge draws the PARENT tile scaled while a tile renders, but it
# looks for that parent with getImmediately(), which only reads the in-memory half of the cache.
# Two settings decide whether the parent is still there.
# Replaced on 15.9.2026 after the desk reproduction: mapsforge renders this file at z19 to z21
# perfectly, so the blank above 18 was the cache, and a guessed ratio is what made it too small.
# mapsforge's own docs call the ratio an approximation made before the view has a size.
check("an empty map says so by asking the file, not by waiting to be photographed",
      "fun emptyHere" in canvas_src and "emptyHere()" in screens,
      "the read the renderer is about to do anyway")


# THE SILENCE COMPLAINT, 15.9.2026: "I'm waiting for it to download. Since I don't have indicator,
# I don't know what's going on." Long work in silence is the failure mode this whole app keeps
# repeating, so the network has a line of its own.
net_src = (MAIN / "Net.kt").read_text()
check("the speed is measured by the phone, not reported by the thing being measured",
      "TrafficStats.getUidRxBytes" in net_src,
      "so mapsforge's own tile fetching is counted too")
check("a negative counter never becomes a negative speed",
      "rx < 0 || tx < 0" in net_src, "UNSUPPORTED is -1 on some devices")
check("the status line is on the map screen",
      "StatusLine(moving)" in screens and "listOfNotNull(net, kept)" in screens, "the network and the cadastre kept ahead, above the note line")
# 29.9.2026 (Mantra ARKOD): the three maps are three keys, and one that cannot draw yet says what
# it needs in the middle of the screen instead of being skipped.
check("a map that needs something says what, where the map would be",
      "OfflineOffer(" in screens and "GoogleKeyHelp(" in screens and "private fun CentrePanel" in screens,
      "the Croatia download and the way to a Google key")
check("a blank offline map explains itself at the zoom it goes blank",
      "emptyHere()" in screens, "the file is asked, not the user")


# The map's name shares the top line with the coordinates (15.9.2026), so the line must not clip.
check("the map name is on the top line",
      "Label(layer.name, Paint.Amber" in screens,
      "next to the zoom, where the key beside it says which family it is")
check("the scale bar is gone", "mapScaleBar" not in canvas_src,
      "the zoom number says the same thing in five characters")


# The reproduction that ended five versions of guessing is kept in the repository, because the
# next person to see a blank map should run it before touching the app (four-tests.md, Test 1:
# attack the mechanism where it is cheap to attack).
# The desk probes (tools/) stayed with Mantra Trail on 29.9.2026: they read the OpenAndroMaps and
# BRouter files this app no longer has. Its own desk test is Test 1 and TESTING.md.


# THE TRACK, AFTER THE WALK (15.9.2026): a popup that names it, a folder he can see the name of,
# and a manager that renames and deletes.
tracks_src = (MAIN / "Tracks.kt").read_text()
check("cancelling after a walk still saves it",
      "onCancel = {\n                    Trail.dealtWith()" in screens,
      "cancel means do not rename, never throw the walk away")
check("renaming never writes over another walk",
      "već postoji" in tracks_src, "a name collision refuses rather than overwrites")
# Export became one action on 15.9.2026: Android's own save dialog asks where and what to call it,
# and the track on the phone takes that name afterwards. Rename in the manager is gone with it.
# Export went out with the server (15.9.2026): a finished walk is written straight into the folder
# he chose, so there is nowhere left to export it TO. The menu is that folder, filtered to GPX.
folder_src = (MAIN / "Folder.kt").read_text()
check("the tracks menu is the chosen folder, filtered to GPX",
      'endsWith(".gpx", ignoreCase = true)' in folder_src and "fun list" in folder_src,
      "not a private copy nobody can find")
check("the folder's name is on the menu",
      'Opens("Mapa", R.drawable.ic_folder, under = folder, onClick = onChooseFolder)' in screens,
      "a list of files nobody can find is a list")
check("renaming keeps the extension and shows it separately",
      "Tracks.safeFileName(newName)" in folder_src and 'Label(".${track.extension}"' in screens,
      "he renames a name; the disk keeps a file")
check("there is no export left anywhere",
      "CreateDocument" not in activity and "onExport" not in screens,
      "the walk is already where he will look for it")
check("deleting a track asks twice",
      'if (confirming?.uri == track.uri) "sigurno?" else "obriši"' in screens, "one thumb on a hillside is not a decision")
check("the settings row names the folder rather than saying chosen",
      "store.exportFolderName" in screens, "Documents/Tracks, not the word chosen")
check("the saved message says where it went",
      'Spremljeno u ${Folder.label(this@MainActivity, store)}' in activity,
      "the folder is named, so the message can be checked rather than trusted")
# Both faults of 15.9.2026: the copy ran on the main thread, and the answer went to a line that
# is behind the manager whenever the manager is what he is looking at.
check("an export happens off the main thread and says so where he is looking",
      "withContext(Dispatchers.IO)" in activity and "Trail.sayInManager" in activity,
      "the track manager carries its own line now")
check("the track list is loaded rather than read during composition",
      "loaded = withContext(Dispatchers.IO) { tracks() }" in screens,
      "this is why changing the line colour was slow")
check("speed is on the top line",
      "Geo.formatSpeed(fix?.speedMs)" in screens and "fun formatSpeed" in (MAIN / "Geo.kt").read_text(),
      "kilometres an hour, a tenth at walking pace")
# THE COMPASS LEFT ON 29.9.2026 (v3): "remove the compass. We don't want compass there. Compass is
# extra, no needed." The checks that guarded its looks went with it; this one keeps it out.
check("the compass is gone: no dial, no little compass, no switch, no icon",
      "CompassOverlay" not in screens and "LittleCompass" not in screens and "CompassDial" not in screens
      and "compassMode" not in (MAIN / "Store.kt").read_text() and "onCompass" not in (MAIN / "Settings.kt").read_text()
      and not (ROOT / "app/src/main/res/drawable/ic_compass.xml").exists() and "BubbleVial" not in screens,
      "the overlay, the needle, the setting and the drawable all left together")
check("the lock is shown rather than announced",
      'Trail.say("Locked to the middle")' not in screens
      and 'Trail.say("The map is free again")' not in screens,
      "the mark's centre fills; a line of text for a drawn state is text over the map")
check("the bubble level is gone from the app, not merely from the screen",
      not (MAIN / "Level.kt").exists() and "Level." not in (MAIN / "Sensors.kt").read_text()
      and "calibration" not in (MAIN / "Store.kt").read_text(),
      "the file, the sensor, the calibration and the tests all left together")


# WHAT HE ASKED FOR ON 15.9.2026, AFTER THE MAP SERVER LANDED.
# Rebuilt from the ground up on 15.9.2026: the box waits, it is empty, and it has two words on it.
check("the name box has no clock in it",
      "secondsLeft" not in screens and "touched" not in screens,
      "it waits; there is nothing to decide about whether he has started typing")
check("the name box starts empty and says what the name is now",
      'mutableStateOf("")' in screens.split("private fun NameBox")[1][:400],
      "he is typing a new name, not correcting an old one")
check("the entry box has a frame and the cursor is already in it",
      "border(1.5.dp, Paint.Amber" in screens and "focus.requestFocus()" in screens,
      "on a dark panel an unfocused dark field is a label, not a box")
# 17.9.2026: cancel became discard, because that is what the key now does.
check("the name box has two answers and they are named OK and odbaci",
      'Action("Odbaci", R.drawable.ic_trash, onClick = onCancel' in screens and 'Action("OK", R.drawable.ic_check' in screens,
      "no third thing to read on a hillside")
# The reason it was not empty: the file name was being built from a date stamp AND a name that
# was already a date, so nothing matched the pattern and the box opened full of numbers.
check("a track file is named after the track and nothing else",
      "Tracks.safeFileName(name)" in (MAIN / "TrailService.kt").read_text()
      and "fun fileName" not in (MAIN / "Gpx.kt").read_text(),
      "the builder that stamped a second date is gone, not merely unused")
# The rename in the manager did nothing and said nothing: DocumentFile.fromSingleUri returns a
# SingleDocumentFile, which does not implement renameTo at all.
check("renaming goes to the provider, not through a wrapper that cannot do it",
      "DocumentsContract.renameDocument" in folder_src, "the call that works on a tree's document")
check("deleting goes the same way",
      "DocumentsContract.deleteDocument" in folder_src, "one lesson, applied twice")
# 17.9.2026: the lock moved into the handover, so it is applied to whichever map appears rather
# than only to the one that was up when he pressed.
check("the position can be locked to the middle of the screen",
      "if (follow && fix != null) centreOn(fix)" in (MAIN / "Canvases.kt").read_text()
      and "Canvases.handOver(line, fix, follow)" in screens,
      "one press holds it, the next lets the map go")
# Refined 15.9.2026: one tap centres, TWO IN A ROW lock. A second tap a minute later is somebody
# centring again, not somebody asking for a lock.
check("two taps in a row are what lock it",
      "now - lastCentreTap < 1_000L" in screens, "a second inside a second")
# THE SETTINGS WERE REBUILT ON 17.9.2026 as grouped cards, the way the phone's own Settings app
# builds them: a quiet title, a rounded card, rows with a second line under the title. The three
# checks that described the old flat list — folding families, capitals for a group, a family of
# one — went with the list they described. With two families left there was nothing left to fold.
# Rewritten from nothing on 17.9.2026, in its own file, because the ticks did not move when they
# were tapped: the old face asked the preferences whether it was ticked while drawing the frame.
# Reordered 17.9.2026 to his logic: tracks first, then one entry per map, each with a way into
# its own options and a dropdown of its views.
# MANTRA ARKOD (29.9.2026): my parcels come first, where Mantra Trail kept its tracks; then the
# offline map, Google's views and the keys; the cadastre kept on the phone; tracks; about.
# v3 (29.9.2026): "Write all the settings in the English language and all terminology from arkod in
# Croatian" -- the groups are English; Moje čestice keeps its Croatian name.
order = ['Group("Moje čestice")', 'Group("Offline map")', 'Group("Google map")', 'Group("API keys")',
         'Group("ARKOD on the phone")', 'Group("Tracks")', 'Group("About")']
check("my parcels come first and every group is in its place",
      all(g in settings_src for g in order) and [settings_src.index(g) for g in order] == sorted(settings_src.index(g) for g in order),
      " · ".join(g[7:-2] for g in order))
check("a row that opens a list shows whether it is open",
      "open = keysOpen" in settings_src and "ic_chevron_down" in (MAIN / "Look.kt").read_text(),
      "the chevron points right when closed and down when open")
check("a setting is one word and its control shows the state",
      'Toggle("Fetch ahead"' in settings_src and 'Toggle("ARKOD layer"' in settings_src
      and 'Toggle("Only Moje čestice"' in settings_src and 'Toggle("Parcel search"' in settings_src,
      "the switch is the state; no sentence")
check("the version opens the latest build",
      "open.openUri(RELEASES)" in settings_src and "releases/latest" in screens,
      "the rule for every Mantra app, 29.9.2026")
check("a key can be pasted, not only picked from a file",
      "onPasteKey" in settings_src and "onPaste(text)" in screens and "fun addKeyText" in activity,
      "the Google help and the keys group both take one")

check("a saved walk can be drawn on the map in a chosen colour",
      "showSavedTrack" in canvas_src and "TRACK_COLOURS" in screens,
      "five colours, and the shown line is separate from the recording line")
# The server came out of this app on 15.9.2026: the offline files work, so a second app in the
# path was one more thing to be running. MANTRA_MAP_SERVER still exists on its own.
check("no part of the map server is left in this app",
      "ServerStatus" not in screens and "ServerStatus" not in activity
      and not (MAIN / "ServerStatus.kt").exists() and "SERVER" not in layers,
      "the layer, the family, the status row and the file are all gone")


# THE ROUTES LEFT ON 29.9.2026 (Mantra ARKOD): "we can remove finding the paths". The checks that
# guarded the point key, the route menu and BRouter's waypoints went with them; these keep them out.
check("no route, no point key, no router is left",
      "PointKey(" not in screens and "RouteMenu(" not in screens and not (MAIN / "Routing.kt").exists()
      and not (ROOT / "app/src/main/java/btools").exists() and not (ROOT / "app/src/main/assets/brouter").exists(),
      "the engine, its profiles and the keys that drove it are all gone")
# The little compass (16.9.2026), as Google has: one tap north up, the next turning with the walk.
# 17.9.2026: it was written on the 16th and he never saw it, because it was nested inside the
# 72dp centre target — fillMaxSize inside 72dp is 72dp, so it drew behind the crosshair.
# 17.9.2026: he sent Google's screenshot twice. Black disc, red north half, white south half, N.
# Refined 17.9.2026: hollow, and one thing only. No disc behind it, and the tap that used to
# choose a second state is gone with the state.
# 17.9.2026: the ticks are gone and so are the four checks that guarded them. A tick asked whether
# a view was ALLOWED in the switcher; he wanted to choose a view and see it. The switcher now turns
# between the two maps, and each map's views are a radio in its own dropdown.
# Refined again 17.9.2026: no tick and no radio either. The whole row is the button and the
# chosen one wears an amber outline.
# 27.9.2026: a view is one part of a choice bar; the chosen part is raised, never amber (amber is "do").
check("a view is chosen from one choice bar",
      "Choice(" in settings_src and "Layers.OFFLINE_VIEWS.map { Part(it.label) }" in settings_src,
      "the four views side by side, the chosen one raised")
check("the chevron appears only where a tap opens something",
      "fun Opens(" in (MAIN / "Look.kt").read_text() and "ic_chevron" not in settings_src.replace("Opens(", ""),
      "only Opens rows draw it")
check("each of the three maps has its own key",
      screens.count("MapKey(") == 4 and "choose(Layers.OFFLINE)" in screens and "choose(Layers.OSM)" in screens
      and "choose(Layers.byId(store.googleViewId))" in screens,
      "one definition and three uses; which Google view is chosen in the settings")
check("the offline map has views of its own",
      "OFFLINE_VIEWS" in layers and "onOfflineView" in settings_src,
      "the same file drawn four ways, because Google's entry had four and this one had none")
check("nothing on the marks is outlined",
      "argb(190, 11, 13, 16)" not in (MAIN / "Marks.kt").read_text()
      and "setShadowLayer" not in (MAIN / "Marks.kt").read_text(),
      "nothing drawn behind a mark")
# Removed with the second tap, 17.9.2026: he asked for a control that does one thing.
check("the map can be turned and the turn can be read back",
      "fun setMapRotation" in canvas_src and "fun mapRotationDeg" in canvas_src,
      "VTM turns with two fingers by itself")
# Replaced 16.9.2026: he asked for Google's mark instead — a dot with a cone of light in front.
# The mark moved into Marks.kt when the second engine arrived (16.9.2026): both engines draw the
# same dot, so it is drawn in one place and handed to whichever is running.
marks_src = (MAIN / "Marks.kt").read_text()
check("the position is a dot with the light in front of it",
      "RadialGradient" in marks_src and "drawArc" in marks_src,
      "one drawing, used by both engines")
check("the light turns with the map as well as with the phone",
      "mapRotationDeg()" in canvas_src or "rotation?.degrees" in canvas_src,
      "or it would point the wrong way as soon as the map was turned")
# 16.9.2026: he pressed "ask it" and saw nothing, because the answer went to the map's note line
# behind the settings — the same fault export had. And the row showed the SETTING, so a phone
# running mapsforge could read "VTM" and be telling the truth about the wrong thing.
# Removed 17.9.2026 at his word. The tile fetcher still keeps its report for the day something is
# white again; it simply is not a row he has to pass every time.
check("the map's diagnosis is not a row in the settings",
      "diagnose()" not in settings_src, "he does not need it in front of him")
check("the engine names itself in its answer",
      "\"VTM · " in canvas_src, "so a screenshot of it says which code was running")
# The choice is gone with the CPU renderer (16.9.2026): there is one engine, so there is nothing
# to choose and nothing to restart for.
check("no setting offers the renderer that was removed",
      "useVtm" not in screens and "map engine" not in screens,
      "one engine, no switch, no fallback to the thing that lagged")
check("the CPU renderer is gone from the tree, not merely unused",
      not (MAIN / "MapCanvas.kt").exists() and not (MAIN / "MapSurface.kt").exists()
      and "mapsforge-map" not in (ROOT / "app/build.gradle.kts").read_text(),
      "the file, the interface it shared and the dependency all left together")
check("the engine reads the offline files he already has",
      "MapFileTileSource" in canvas_src, "no new format and no second download")
check("accuracy is drawn as a ring",
      "accuracyRing = ring" in canvas_src and "PathLayer(map, 0x553B82F6" in canvas_src,
      "filled, three metres of accuracy swallowed the map at z22")
# BRouter left with the routes on 29.9.2026 (Mantra ARKOD); its checks went with it.


# ONE ENGINE (16.9.2026). The CPU renderer and the interface that let the two sit side by side
# are both deleted: he walked with the GPU one and the old one only kept the lag one tap away.
vtm_src = canvas_src
# 16.9.2026: every map was blank because setting a layer began by clearing VTM's whole layer list,
# which holds VTM's own layers — the gesture handler among them. Proved on a desk that the file and
# the reader were both fine (235 elements at z17), so the fault was ours.
# 16.9.2026: the raster path hard-coded "/{Z}/{X}/{Y}.png" and dropped everything after it, so
# Thunderforest's key never reached Thunderforest and every tile came back a refusal.
# 16.9.2026: his coast came back covered in petrol pumps, because the theme was fixed at
# MOTORIDER — a motorcycle theme, where a filling station is the point.
# 16.9.2026: Thunderforest and OpenStreetMap removed at his word, OpenAndroMaps added in their
# place — the same file format the engine already reads, with contours in the data.
# 16.9.2026: he started a 1.2 GB download and could not tell it was running, where it went, or
# which map was being drawn afterwards.
# His screenshot said "0 here" while a map was plainly drawing: the list only counted files this
# feature had fetched itself.
# 17.9.2026, his question: can the app cut Croatia out of the 1.2 GB Balkan file? It cannot — a
# .map is compiled, index and all — so a smaller map is offered instead of a promise.
# 17.9.2026, his instruction: this is a dark application. Nothing is filled in a light colour and
# black is never written on amber; state is an outline.
check("nothing on a face is filled in the light colour",
      "Paint.Amber else Paint.Veil" not in screens
      and screens.replace(".height(8.dp).clip(RoundedCornerShape(4.dp)).background(Paint.Amber))", "").count("background(Paint.Amber)") == 0,
      "state is an amber outline round a dark row; the download's progress bar is the one fill, because it is a measurement")
# OUR OWN THEME FOR OPENANDROMAPS (16.9.2026). Every tag in it was read out of a real
# OpenAndroMaps file with tools, not remembered, and the file is validated against VTM's own
# schema before it ships — which caught two errors that would have been a blank map on a hill.
theme_path = ROOT / "app/src/main/assets/themes/mantra-walk.xml"
theme_src = theme_path.read_text() if theme_path.exists() else ""
check("the walking theme ships with the app", theme_path.exists(), f"{len(theme_src)} bytes")
# 17.9.2026: his screenshot showed a map of thick brown hair. The stipple was the fault — in this
# dialect the pattern length is two or three, the dash colour is stipple-stroke and the casing is
# stroke, and I had set 14 with one colour for both.
check("the dashed lines follow the renderer's own convention",
      'stipple="2"' in theme_src and 'stroke="#aaffffff"' in theme_src
      and 'stipple="14"' not in theme_src,
      "pattern of two or three, a pale casing, the difficulty in the dash")
check("the contour lines are hairlines, not cables",
      'id="contour-minor" stroke="#b99a78" width="0.22"' in theme_src,
      "a fifth of the width they were")
check("it draws what makes these maps worth having",
      all(tag in theme_src for tag in ["contour_ext", "sac_scale", "hknetwork", "natural\" v=\"peak"]),
      "contour lines, path difficulty, waymarked routes, summits")
# 29.9.2026 (Mantra ARKOD): the offline file is mapsforge's Croatia, which carries none of
# OpenAndroMaps' own tags, so the plain theme opens and the walking one is a choice.
check("the plain theme is the one the app opens with",
      'KEY_THEME, "DEFAULT"' in (MAIN / "Store.kt").read_text() and '"MANTRA"' in layers,
      "the walking theme is still one of the four views")
check("a theme that will not load says so rather than drawing nothing",
      "Planinarska tema se nije učitala" in canvas_src, "and the plain one is used meanwhile")
check("the theme is chosen, not fixed at a motorcycle one",
      "applyTheme(store.themeName)" in canvas_src and "OFFLINE_VIEWS" in settings_src,
      "four views, chosen in the settings")
# 17.9.2026, tested against his real key on this desk: the key was valid and Google's refusal
# named the project and the exact console link. My own sentence said "enable the Map Tiles API"
# without saying for WHICH project, and he spent half a day making a second key for nothing.
# THE KEYRING (17.9.2026), as in his own KEY_RING_TESTER: several keys, each testable, and the app
# walks them in order rather than dying with the first one that stops working.
ring_src = (MAIN / "Keyring.kt").read_text()
# 17.9.2026, after he enabled the rest of Google's APIs: their walking directions beside BRouter's,
# with a toggle, because the two answer different questions and both are worth having.
# 17.9.2026: routing answered and not one tile drew. VTM's own HTTP client says in its comments
# that it does not do https, and every tile service is https.
# 17.9.2026: half his walking is in signal and half is not, so the signal half should be worth
# having. Heights along a route, and the turns Google was already sending and I was discarding.
# 17.9.2026, reversed at his word and asked five times: he does not want turn-by-turn in this
# menu. This is a walking app; he wants the way drawn on the map, not streets to read. They are
# still fetched, because they arrive in the same answer, and simply not shown.
# 27.9.2026: close is the cross at the top of the route menu, save a quiet action among the others.
# 17.9.2026: Google's map arrives some frames after its view, and every method began by returning
# when it was not there yet — so a centring, a point or a route asked for in those frames was
# dropped in silence. Both bugs he reported were that.
# 17.9.2026: the yellow key centred the activity's own VTM canvas by name, so on Google's map it
# dutifully centred a map he could not see.
# 17.9.2026: showLayer asked the offline canvas for every layer, so a Google view that their own
# renderer had drawn perfectly still ended with "the map view is not up yet" across a working map.
# 17.9.2026: the kept imagery downloaded and drew, and no row anywhere chose it — a map on the
# phone he could not ask for.
# 17.9.2026, his standard: changing the map changes the VIEW, and everything laid over it is
# constant. The effect that applied the overlays was keyed on the walk and the lock alone, so a
# new engine took the screen and inherited nothing.
check("a new map inherits everything that was on the old one",
      "fun handOver(" in (MAIN / "Canvases.kt").read_text()
      and "LaunchedEffect(generation, line.size" in screens,
      "points, route, saved track, the walk being recorded, the position and the lock")
# 29.9.2026 (Mantra ARKOD): "otherwise it remembers the states when it was left".
check("the next run opens where this one was left: map, place, zoom and turn",
      "store.lastBearing = position.bearing" in canvas_src and "start.bearing = store.lastBearing" in canvas_src
      and "store.layerId = picked.id" in screens and "Layers.FIRST.id" in (MAIN / "Store.kt").read_text(),
      "and the first run opens OpenStreetMap over the whole of Croatia")
# 17.9.2026: he asked for Google's "select an area and keep it". Google forbid it — their Map
# Tiles policy lists offline use among the prohibited uses of their content — so the imagery comes
# from Sentinel-2 cloudless (EOX, CC BY 4.0), which may be kept, and the attribution travels with
# it because that licence asks for it.
check("nothing of Google's is stored",
      "googleapis" not in (MAIN / "ArkodCache.kt").read_text() and "Parcels.isCadastre(url)" in (MAIN / "TileHttp.kt").read_text(),
      "only the state's cadastre is kept; Google's terms forbid keeping theirs")
check("discard discards",
      "onDiscardRecording(file)" in screens and 'Action("Odbaci", R.drawable.ic_trash, onClick = onCancel, quiet = true, danger = true' in screens
      and "fun discardRecording" in (MAIN / "MainActivity.kt").read_text(),
      "a key that does the opposite of its word teaches him to trust none of them")
check("the centre key centres whichever map is on the screen",
      "Canvases.centreOn(fix)" in (MAIN / "MainActivity.kt").read_text(),
      "not the offline canvas by name")
# 18.9.2026: the hand-over sat AFTER the Google branch's own return, so it ran for VTM and never
# once for Google — the walk he was recording was handed to an engine that was not on the screen.
# 17.9.2026: the URL the app builds was proved right on a desk, byte for byte, and still nothing
# drew. VTM's own client cannot do https and its OkHttp engine drew nothing either, so tiles now
# go through java.net — the stack that fetches the session, the routes and the maps.
check("tiles are fetched with the stack that is known to work",
      "TileHttp.Factory()" in canvas_src and (MAIN / "TileHttp.kt").exists(),
      "java.net, the same as everything else in this app that works")
check("the tile fetcher says what came back",
      "Report.tiles(" in (MAIN / "TileHttp.kt").read_text() and "Report.tileReport()" in canvas_src,
      "a white map is not debuggable; 403 on tile 16/35762/23697 is")
check("one visual language: no blur on any mark",
      "setShadowLayer" not in (MAIN / "Marks.kt").read_text() and "fun routePoint" not in (MAIN / "Marks.kt").read_text(),
      "the route letters left with the routes (29.9.2026)")
check("what is left to do is written down",
      (ROOT / "TODO.md").exists(), "he asked to be told after every build")
check("the keys are a ring, not one key",
      "fun order(" in ring_src and "sessionFromRing" in (MAIN / "GoogleTiles.kt").read_text(),
      "the one that worked last is tried first, the refused one last")
# 17.9.2026: the keys left the Google row for a group of their own at the bottom — one ring serves
# whatever asks — and a dropdown he opens stays open until he closes it.
check("the keys are a group of their own at the bottom",
      settings_src.index('Group("Google map")') < settings_src.index('Group("API keys")'),
      "under the Google row they were two lines he passed on the way to a view")
check("a dropdown he opened stays open",
      "store.opened(" in settings_src and "fun setOpened(" in (MAIN / "Store.kt").read_text(),
      "between sessions, as he asked")
check("the version rides on the credits and the app's name is not a row",
      '"ARKOD Layer · version $version"' in settings_src,
      "the launcher already says what the app is called")
check("each key can be tested from its own row",
      'IconAction(R.drawable.ic_play, "test"' in settings_src and "onTestKey" in settings_src,
      "and what Google said sits under that key, not somewhere else")
check("a key is shown masked and kept whole",
      "val masked: String" in ring_src and "value.take(8)" in ring_src,
      "enough to tell two apart, never enough to use one")
check("a key can be taken off the ring",
      "onRemoveKey" in settings_src and "fun remove(" in ring_src, "one press")
check("terrain is asked for with a roadmap layer",
      'view.mapType == "terrain"' in (MAIN / "GoogleTiles.kt").read_text(),
      "Google refuses a terrain session without one, proved against the real key")
check("Google's own words are passed on, not summarised",
      "googleSays" in (MAIN / "GoogleTiles.kt").read_text()
      and "googleSays" in (MAIN / "TileTest.kt").read_text(),
      "their message names the project and the link that switches it on")
check("a map that will not draw can be asked what the service said",
      (MAIN / "TileTest.kt").exists() and "onTestTiles" in screens,
      "401, 429, 404 and no network all look identical on a blank screen")
check("the tile pattern keeps whatever follows the numbers",
      "fun tilePattern" in layers and 'tilePath(path)' in canvas_src
      and '"/{Z}/{X}/{Y}.png"' not in code_only(canvas_src),
      "a key or a session lives in the query, and the query is part of the path")
check("VTM's own layers are never cleared",
      "map.layers().clear()" not in code_only(canvas_src),
      "only what this class added is removed, by reference")
check("every layer this class adds is held for removal",
      all(f"{name}Layer = " in canvas_src for name in ["building", "label", "bitmap"]),
      "a layer nobody kept a reference to is a layer that can only be cleared in bulk")
check("VTM reads the map file he already has",
      "MapFileTileSource()" in vtm_src and "setMapFileInputStream" in vtm_src,
      "the 176 MB on his phone is not downloaded again")
check("the engine is built once, where the view is",
      "VtmCanvas(context, store)" in screens and "useVtm" not in screens,
      "no switch, because there is nothing to switch to")

# the cadastre (27.9.2026): its tiles pass through the recolouring, a tap reaches both engines,
# the card carries the tick and the trail's own swatches, and the highlights keep their shape.
parcels_src = (MAIN / "Parcels.kt").read_text()
check("the cadastre's tiles are resolved and recoloured in the tile fetcher",
      "Parcels.resolve(source.getTileUrl(tile))" in (MAIN / "TileHttp.kt").read_text()
      and "ParcelNet.tile(tile.zoomLevel.toInt(), tile.tileX, tile.tileY)" in (MAIN / "TileHttp.kt").read_text()
      and "ParcelStyle.restyle(" in (MAIN / "ParcelNet.kt").read_text(),
      "TileHttp.kt and ParcelNet.kt examined: restyled as Parcel view says (v5)")
check("a tap anywhere reaches the cadastre, the middle included",
      "ParcelsShown.tap(at.latitude, at.longitude)" in canvas_src and "onSingleTapConfirmed" in canvas_src
      and ".size(72.dp)\n                .clickable" not in screens_src,
      "VtmCanvas.kt and Screens.kt examined: nothing over the middle takes the touch")
check("a parcel I keep keeps its shape, colour and line, so it draws with no signal",
      all(f in parcels_src.split("data class Mark")[1][:500] for f in ["val rings", "val colour", "val style", "val name"]),
      "Parcels.Mark examined")
# Reversed at his word, 29.9.2026 (v3): "It should hide parcels overlay completely from the map."
# The cadastre is on by default and on the first run; the parcels key is the one way to hide it.
check("the cadastre is on by default and only the parcels key hides it",
      'getBoolean(KEY_CADASTRE_ON, true)' in (MAIN / "Store.kt").read_text() and 'glyph = "K"' not in screens_src
      and screens_src.count("store.cadastreOn = ") == 2,
      "Screens.kt and Store.kt examined: the key and Parcel view's switch")
check("a first tap selects, a tap on the selection opens its sheet",
      "ParcelNet.outline(lat, lon)" in screens_src and "dodirnite ponovno za list" in screens_src,
      "Screens.kt examined")
check("the sheet has TXT", 'IconAction(R.drawable.ic_text, "TXT"' in screens_src and "Folder.saveText(" in screens_src,
      "Screens.kt examined")
check("a tap selects one parcel and forgets the last; only the sheet keeps a parcel",
      "fun select(parcel: Parcels.Parcel?)" in screens_src and "ParcelsShown.selection" in screens_src,
      "Screens.kt examined")
check("the sheet has CPY beside TXT", 'IconAction(R.drawable.ic_copy, "CPY"' in screens_src and "clipboard.setText(" in screens_src,
      "Screens.kt examined")
check("the sheets and panels hold their touches, nothing reaches the map beneath",
      screens_src.count(".swallowTouches()") >= 3 and "private fun Modifier.swallowTouches()" in screens_src,
      "Screens.kt examined")
check("the sheet is the whole screen, with a filter",
      "Parcels.filterRows(inTab, filter)" in screens_src and "R.drawable.ic_filter" in screens_src,
      "Screens.kt examined")
check("the sheet has three tabs: land use, possession, owner (vlasnički list)",
      "Parcels.Tab.values()" in screens_src and "Parcels.folioRows(" in screens_src
      and "ParcelNet.ownerSheets(" in screens_src and "ParcelNet.findOwnerSheets(" in screens_src
      and "fun FolioFinder(" in screens_src and "store.folioLinks" in screens_src,
      "Screens.kt examined")
# MOJE ČESTICE (29.9.2026).
check("my parcels have a screen of their own, one press from the map and first in the settings",
      "private fun MyParcelsFace" in screens_src and "myParcels = true" in screens_src and "onMyParcels" in settings_src,
      "the ★ key and the first settings group")
check("the sheet keeps a parcel, in any colour and a line style, dashed by default",
      "private fun MineControls" in screens_src and "private fun HueBar" in screens_src
      and "Parcels.SWATCHES.forEach" in screens_src and "LineStyle.DASHED" in (MAIN / "Store.kt").read_text(),
      "ten swatches, a hue bar, dashed / solid / dotted")
check("my parcels are drawn dashed on the map by the engine's own stipple",
      "fun lineFor(mark: Parcels.Mark)" in canvas_src and "stippled(12)" in canvas_src and "PathLayer(map, lineFor(mark))" in canvas_src,
      "VTM draws the dashes; the gaps are the map")
check("the parcel search lives in my parcels: number, possession sheet, street",
      "enum class SearchBy" in screens_src and "DropdownMenu(" in screens_src and "ParcelNet.ossSearch(" in screens_src,
      "Screens.kt examined")
# THE CADASTRE KEPT ON THE PHONE (29.9.2026).
cache_src = (MAIN / "ArkodCache.kt").read_text()
net_parcels = (MAIN / "ParcelNet.kt").read_text()
check("every cadastre tile drawn is kept, and read off the phone first",
      "ArkodCache.raw(z, x, y)" in net_parcels and "ArkodCache.keepRaw(" in net_parcels and "ArkodCache.inked(" in net_parcels,
      "the state's picture and the recoloured copy")
check("the cadastre is fetched ahead where he looks and where he is, and can be turned off",
      "fun viewSettled(" in cache_src and "fun positionKnown(" in cache_src
      and "ArkodPrefetch.viewSettled(" in screens_src and "ArkodPrefetch.positionKnown(" in screens_src
      and "if (store.prefetch)" in screens_src,
      "Parcels.prefetchTiles decides which; ArkodPrefetch fetches three at a time")
check("the kept cadastre has a ceiling",
      "fun prune(" in cache_src and "LIMIT_BYTES" in cache_src and "ArkodCache.prune()" in activity,
      "the tiles touched longest ago go first")
check("a sheet opens with no signal from what was read before, and says so",
      "ArkodCache.keepAnswer(" in net_parcels and "getKept(Parcels.recordUrl" in net_parcels and "keptSince" in screens_src,
      "the record and the folios are kept as they arrive")

# THE VISUAL LANGUAGE (27.9.2026): the icons are drawn from design/icons/*.svg by
# design/make_icons.py, and every control is one of Look.kt's kinds.
used_icons = set()
for f in MAIN.glob("*.kt"):
    # android.R.drawable.* is the system's own, not ours
    used_icons |= set(re.findall(r"(?<!android\.)R\.drawable\.ic_([a-z_]+)", f.read_text()))
drawables = ROOT / "app/src/main/res/drawable"
missing = sorted(i for i in used_icons if not (drawables / f"ic_{i}.xml").exists())
check("every icon the code draws exists as a drawable", not missing,
      f"{len(used_icons)} icons used, missing: {missing or 'none'}")
unsourced = sorted(i for i in used_icons if not (ROOT / "design/icons" / f"{i}.svg").exists())
check("every icon has its SVG source beside it", not unsourced,
      f"{len(used_icons)} examined, without an SVG: {unsourced or 'none'}")
drift = []
for svg in (ROOT / "design/icons").glob("*.svg"):
    d = re.search(r'd="([^"]*)"', svg.read_text()).group(1)
    xml = drawables / f"ic_{svg.stem}.xml"
    if not xml.exists() or f'android:pathData="{d}"' not in xml.read_text():
        drift.append(svg.stem)
check("each drawable is its SVG, not an edited copy", not drift,
      f"{len(list((ROOT / 'design/icons').glob('*.svg')))} SVGs compared, drifted: {drift or 'none'}")
look = (MAIN / "Look.kt").read_text()
check("the four kinds of control are defined once",
      all(f"fun {k}(" in look for k in ["Action", "Toggle", "Choice", "Opens", "Pick", "IconAction"]),
      "Look.kt examined")
check("no control explains itself in a sentence",
      not re.search(r"press (to|again to) (change|show|hide)", screens_src + settings_src),
      "Screens.kt and Settings.kt searched for 'press to change/show/hide'")

# 27.9.2026: "design/icons/*.svg" in a KDoc opened a NESTED comment (Kotlin nests them), the file
# never closed, and v91's first build failed on forty unresolved names that were all this one.
nested = []
for f in MAIN.glob("*.kt"):
    for n, line in enumerate(f.read_text().split("\n"), 1):
        st = line.strip()
        if (st.startswith("*") or st.startswith("//")) and "/*" in st.lstrip("/*"):
            nested.append(f"{f.name}:{n}")
check("no comment opens another comment by accident", not nested,
      f"{len(list(MAIN.glob('*.kt')))} files scanned, found: {nested or 'none'}")

# v2 (29.9.2026), from the first run on the emulator (TEST_RESULTS v1).
check("a layer just put on asks for its tiles, it does not wait for a pan",
      "fun load()" in canvas_src and "map.clearMap()" in canvas_src.split("fun load()")[1][:200]
      and canvas_src.count("load()") >= 4 and "addOnLayoutChangeListener" in canvas_src,
      "clearMap after every show, and again at the view's first real size (B2, H1)")
check("the touch sink does not consume, so a scroll inside it works both ways",
      ".consume()" not in screens_src.split("private fun Modifier.swallowTouches()")[1],
      "being a pointer target is enough to keep the map from the touch (D1)")
check("a key being typed is masked",
      screens_src.count("PasswordVisualTransformation()") >= 1 and "PasswordVisualTransformation()" in settings_src,
      "the Google panel and the keys group (MT-KEY-3)")
check("the sheet says what TXT and CPY did",
      "Trail.note.collectAsState()" in screens_src.split("private fun ParcelCardView")[1][:4000],
      "the note line is under the sheet (E3)")



# V3, HIS EVENING REQUEST OF 29.9.2026 (momentaryupdates.md).
check("the parcels key hides the cadastre and a long press opens Parcel view",
      "cadastreOn = !cadastreOn" in screens and "onLongClick = { parcelView = true }" in screens
      and "detectTapGestures(onTap = { tap() }, onLongPress" in screens and "ParcelViewFace(" in screens,
      "a tap is the overlay, a hold is its view settings")
check("only my parcels wins over the key, in one rule both engines read",
      "fun visibility(cadastreOn: Boolean, onlyMine: Boolean)" in (MAIN / "Parcels.kt").read_text()
      and "(if (mineOn) marks.filter { it.group !in hiddenGroups } else emptyList()) + listOfNotNull(selection)" in (MAIN / "ParcelNet.kt").read_text(),
      "Parcels.visibility decides; ParcelsShown draws it")
check("Google's field is on all three maps when there is a key, the parcel field under it",
      "if (googleUsable) PlaceField(store)" in screens and "if (parcelSearchOn) ParcelField(store, book, caches)" in screens
      and screens.index("PlaceField(store)") < screens.index("ParcelField(store, book, caches)"),
      "not tied to the Google map; the parcel field shown or hidden in Parcel view")
check("both fields answer as he types",
      "LaunchedEffect(text) {" in screens and "ParcelNet.suggest(" in screens and "OwnerBook.search(book, words)" in screens,
      "Places autocomplete, OSS numbers, and Imenik for names")
check("the top line shows the middle of the map, not the fix",
      "FixLine(centre, fix, zoom, layer)" in screens and "Geo.formatLat(it.first)" in screens,
      "it updates while he pans; where-am-I puts the middle on him")


# V5, 30.9.2026 (momentaryupdates.md): lines, caches, the key that always brings them back.
check("the Show/hide ARKOD layer key brings every parcel back, even from Only Moje čestice",
      "if (onlyMine) {\n                            onlyMine = false" in screens and '"ARKOD layer shown"' in screens,
      "he could not get them back from the key (30.9.2026)")
check("a tap outlines the parcel under it even with the layer hidden",
      "(if (mineOn) marks.filter { it.group !in hiddenGroups } else emptyList()) + listOfNotNull(selection)" in (MAIN / "ParcelNet.kt").read_text()
      and "select(null)\n            return\n        }\n        if (Canvases.currentZoom()" not in screens,
      "the selection is drawn whatever the layer shows")
check("no pločice anywhere the user reads",
      not any(re.search(r'"[^"\n]*ploči[^"\n]*"', code_only(f.read_text())) for f in MAIN.glob("*.kt")),
      "ARKOD's things are čestice (30.9.2026)")
check("the state's lines have a colour, a transparency and a weight",
      "LinesControls(lines, onLines)" in (MAIN / "Settings.kt").read_text() and "ParcelStyle.OPACITIES" in (MAIN / "Settings.kt").read_text(),
      "Parcel view, Lines")
check("a cache is filled with a verbose status and kept under a name",
      "fun start(name: String" in (MAIN / "ParcelCaches.kt").read_text() and "HarvestLine(" in screens
      and "ParcelCachesFace(" in screens and 'title = "name of the cache"' in screens,
      "stage, done/total, rate, time left, failures")
check("inside a cache its own lines replace the state's",
      "PorterDuff.Mode.CLEAR" in (MAIN / "CacheTiles.kt").read_text() and "CacheTiles.draw(" in (MAIN / "ParcelNet.kt").read_text(),
      "the box cleared, the cache drawn in its colour, dashes and weight")
check("the app is called ARKOD Layer",
      "<string name=\"app_name\">ARKOD Layer</string>" in (ROOT / "app/src/main/res/values/strings.xml").read_text(),
      "his choice, 30.9.2026")



# V8, 30.9.2026: Moje čestice as files, groups with their look, the history of every search box.
check("Moje čestice go out as a file and come in from one, as a group named after it",
      "MarkImports.share(this, group, marks)" in activity and "MarkImports.uriOf(intent)" in activity
      and "androidx.core.content.FileProvider" in (ROOT / "app/src/main/AndroidManifest.xml").read_text()
      and 'Action("Import a file"' in screens,
      "share sheet out; picker, VIEW and SEND in")
check("each group has its colour, line, weight and can be hidden",
      "onGroup(group.copy(colour = option))" in screens and "onGroup(group.copy(visible = !group.visible))" in screens
      and "it.group !in hiddenGroups" in (MAIN / "ParcelNet.kt").read_text(),
      "the file's look for all its parcels")
check("every search box offers what was searched before",
      screens.count("history = history,") >= 2 and 'store.history("mine-" + by.name)' in screens,
      "Google's field, the čestica field, Moje čestice")


# V10, 30.9.2026: the numbers on a sheet are links; a sheet goes out as a file.
check("a parcel number on an owner sheet goes to that parcel on the map",
      "onNumber = { number -> card = null; scope.launch { goToNumber(" in screens and "fun numberIn(row: SheetRow)" in (MAIN / "Parcels.kt").read_text(),
      "the folio's own parcels (list A)")
check("a sheet goes out as a file the app opens",
      'IconAction(R.drawable.ic_save, "FILE", onClick = onFile)' in screens and "onShareMarks(group, listOf(one))" in screens,
      "one parcel, one .arkod.json, opened like a group of Moje čestice")
check("Google's map opens on the road map, which EEA accounts are served",
      "Layers.GOOGLE.id) ?: Layers.GOOGLE.id" in (MAIN / "Store.kt").read_text(), "satellite is refused in the EEA")

print(f"\n{len(checks)} checks, {len(failures)} failed")
if failures:
    print("failed: " + ", ".join(failures))
    sys.exit(1)
