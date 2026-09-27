# Development

Past tense. Every decision and the reason it was made, appended, never rewritten.

## 14.9.2026 — v1, the first build

**The name.** Baba asked for a clone of Outdooractive and first called the app that. Outdooractive
is a live product from a German company and the repository is public, so the app was named Mantra
Trail instead.

**Google cannot be cached, and that decided the shape of the app.** The first sketch had Google as
the main map with a cache behind it. Google's Map Tiles API policies forbid pre-fetching, indexing,
storing or caching content and name offline use as a prohibited case; the Maps SDK for Android
carries the same restriction with place IDs as the only exemption. So Google became one layer of
four, online only, labelled as such — and the offline work went to maps whose licences allow it.

**TK25 was proved before it was designed around.** The state survey's WMS was asked for a single
256 px tile in EPSG:3857 over Velebit: HTTP 200, `image/png`, 65,487 bytes, 33,562 opaque pixels
and 1,933 distinct colours. A 200 with a blank tile would have looked identical from the status
code alone, which is why the colours were counted (`silent-failure.md`: check the outcome, not the
operation).

**GPX 1.1 over FIT, TCX and KML**, because everything reads it and the schema is published. It is
written point by point rather than assembled at the end: a file assembled at the end does not exist
when the battery dies on the ridge.

**Accuracy is the state, so it carries the colour.** Sand under 10 m, amber to 50 m, red past it,
dim when the phone will not say. The ring on the map is drawn to scale in metres, because a number
in a corner is read as a score and a circle is read as the ground the fix cannot tell apart.

**The ascent threshold is 3 m.** Without it a phone lying still on a table records a climb, because
its altitude wanders by several metres a minute. Test 1 asserts exactly that case.

**Pitch and roll use atan2 against the other two axes, not asin.** The asin version is correct up
to 45 degrees and wrong past it, which is the worst way to be wrong: it looks right in every casual
check. Tilt composes the two exactly, so 3 degrees one way and 4 the other is 5 off level, not 7.

**The compass filter wraps.** Averaging 359 and 1 the ordinary way gives 180 and points the needle
exactly backwards, at the moment somebody is watching it. `Level.smoothAngle` goes the short way
round and Test 1 closes it.

**Five expectations in Test 1 were wrong before any code was.** A tile number, an epoch in
milliseconds and a tag count where `<trk` was also matching `trkseg` and `trkpt`. All five were
invented rather than measured; all five were measured and corrected. The harness has been seen to
fail, so it is not a rumour.

**mapsforge's API was read from its own sources rather than guessed**, and `MapCanvas.kt` was
typechecked against the real jars and `android.jar` with kotlinc before anything was pushed. That
is not a local build — it produces no artefact — and it removed several CI rounds.

**A local Gradle build was attempted and abandoned.** The sandbox had a JRE and no compiler, and
the attempt was the wrong instinct: Baba's answer was to write the rule into the manifest instead.
`android-app.md` §1a now says the APK is built by GitHub Actions and never on a desk, with the
three reasons — provenance, a reproducible environment, and the one signing key.

## 14.9.2026 — v1 build 1 was red, and the check that should have caught it was mine

`InternalRenderTheme` does not exist in mapsforge 0.25.0; the themes are
`org.mapsforge.map.rendertheme.internal.MapsforgeThemes`. Two lines, and CI found them in six
minutes.

**It should not have reached CI.** The file had been typechecked locally against the real jars and
reported clean — because the filter was `grep -E "^(error|warning)"` and this kotlinc writes
`Broken.kt:1:10: error: unresolved reference`, with the word in the middle of the line and not at
the start of it. **A check that cannot fail looks exactly like a check that passes**
(`checking-the-checks.md`). The filter was replaced with the compiler's own exit status, and then
proved by breaking `Geo.normaliseDeg` on purpose: exit 1 with 51 errors broken, exit 0 restored.

The restore is worth its own line. The first attempt to put the deliberately broken file back ran
after a `return` in a shell function and never executed, so the next run reported 51 errors that
were all my own sabotage. **When you sabotage something on purpose, confirm the repair before
reading the next result** (four-tests.md Test 3: exclude the error you caused yourself).

## 14.9.2026 — v2 was red on a dependency declared twice

`mapsforge-map-android` already depends on `com.caverock:androidsvg` as a plain jar. Declaring
`androidsvg-aar` alongside it put both on the path, and `checkReleaseDuplicateClasses` counted
every class in the library twice. The line came out: the library is still there, brought by the
module that needs it, which is also the module that will keep choosing the right version.

## 14.9.2026 — v3 was red on a lint check that was right

`play-services-maps` brings a fragment library old enough that `registerForActivityResult` is
unsafe against it, and lint said so three times. The temptation was to disable the check; what it
was reporting is real, and the file pickers and the permission request all go through exactly that
call. A modern `androidx.fragment` was named as a dependency so resolution raises the version.
**A blocking lint is narrowed in the session it cries wolf, never carried, and never silenced when
it is telling the truth.**

## 27.9.2026 — v82, the cadastre from the portal, with the owners

Marko asked for the parcel layer of the portal's *Your position* page (he called it "ARKOD"; the
page draws the state cadastre, `cp:CP.CadastralParcel` from api.uredjenazemlja.hr), with a tap that
shows the owners and a way to type or pick parcels and highlight them, in the trail colours.

**Three services, all public, all measured before a line was written.** The WMS draws; its
GetFeatureInfo refuses JSON, so a tap asks the **WFS** for the parcels in a box twenty metres across
and picks the one whose polygon holds the point (ray casting, in `Parcels.kt`, tested). The record
with the possessors comes from **OSS** by parcel id; OSS answers an app and refuses a browser (403
when an Origin header is present), which is why the phone can do this and the portal page cannot
without a relay on the machine. Search is one WFS request with `nationalCadastralReference IN
(...)`, the municipality taken from `cp:CadastralZoning` under the middle of the map.

**VTM asks tiles as base/z/x/y and a WMS wants a box**, so the cadastre's tiles are asked at
`WMS/z/x/y` and `Parcels.resolve` turns them into the real GetMap on the way out of `TileHttp`.
512 px at 180 dpi, so the numbers are the size the portal shows them on a phone's density.

**The state's lines are black**, invisible on a photograph or the night theme. Every tile is
recoloured on the phone into dark or sand ink at 62 %, chosen by the map underneath; the Google
engine uses the same function through a `TileProvider`.

**A highlight keeps the shape**, not only the number, so his land is drawn on a hillside with no
signal. Names in the tests are invented; no real person's record is in this repository.

## 27.9.2026 — v83, the tap reaches the cadastre

On the Pixel 7 emulator v82 drew the cadastre over Kukljica exactly as the portal does, and a tap
on parcel 2451 did nothing: a VTM layer listening for its TAP gesture was never called. The tap is
now caught by Android's own GestureDetector on the map view, which passes every touch on to the
map, and "single tap confirmed" keeps the double tap for zooming.

## 27.9.2026 — v85, a tap answers in a fifth of a second

v84's trace showed the tap arriving at once and the card appearing fourteen seconds later: the WFS
box query took 14 s, and 30 s with an Oracle "maximum open cursors" error when the state's
database was busy. Measured alternatives, all public: the WMS's own GetFeatureInfo in text/plain
(0.2 s: id, number, municipality), OSS parcel-info (0.3 s), OSS's search by number within a
municipality (0.13 s), and GetFeatureInfo on the zoning layer for the municipality (0.2 s). Only
the parcel's outline needs the WFS, so a highlight is kept at once and its outline is fetched
behind it, with a minute's patience, and asked again when the app opens and when K is pressed.
The card now also names the land-registry unit (z.k. uložak, main book, court office), where the
legal owners are written; the land book itself is not open without a separate lookup.

## 27.9.2026 — v86, the outline's patience

The WFS answered a query by reference in 29 s, twice running, and a filtered WMS picture was no
faster (a minute and still going). Neither the WFS's FEATUREID nor RESOURCEID forms are accepted.
So an outline is asked with ninety seconds of patience and three quiet tries, and only a third
failure is said aloud. Proved on the emulator: all three outlines arrived and were drawn.

## 27.9.2026 — v87, one tap highlights, the next opens the sheet; K in the key row; TXT

His words, after v86 on his phone in Zagreb: *"When I click on any parcel, I want it to be
highlighted automatically ... if parcel is highlighted, second tap is opening the sheet ... K layer
should go in the action bars down there ... instead of T, K is coming. T is going into the
settings ... add to the sheet at the top right corner something called TXT ... export to the local
file system TXT file with parcel number and all the data inside."*

**The outline had to be instant**, and the WFS was measured again at 14 and 30 s. So `Outline.kt`
reads it off the WMS picture: 1024 px round the finger over 300 m of ground (900 and 2700 m if the
parcel runs off it), a four-connected paint fill from the tap stopped by every pixel the state drew,
grown one pixel onto the middle of the line, walked round by Moore tracing, thinned by
Douglas–Peucker. Tried in Python on the live picture first: 2451 filled to 1256 m² against the
official 1412 before the grow, the rest being the line's own width; a tap in the sea escaped and
was refused. The WFS is kept only for parcels found by search, which have no point to fill from.

TXT writes the whole sheet and the outline's corners, into the tracks folder if one is chosen,
otherwise the phone's Downloads. The compass's three states moved to settings; the old check that
held it on the key row was rewritten, not deleted.

## 27.9.2026 — v88, the tap selects; CPY; the search field on Google's map

His correction of v87 (screenshot: six parcels red): *"only one parcel can be highlighted at a
time. What stays highlighted, it's only what user choose to highlight and choose the color. And
this is just to mark current click, so new deletes the old click highlight."* A tap is now a
SELECTION: one parcel, cyan (none of the five colours), never kept; a new tap replaces it; a tap on
the selection opens the sheet. The tick and the swatches on the sheet are the only way a tap keeps a
parcel; search still marks what it finds. "remove all" in the K panel clears what v87 marked.

Then: *"beside text export ... CPY, copy. It copies the parcel data to the clipboard"* — the same
text as TXT. And *"to the google map layer a search field at the top, round entry field, same as in
google maps ... can be hidden or shown in the settings"* — a white pill under the top line on the
Google views, the keyboard's search key asks Places (his key, near him, one request per press),
results drop down, a tap takes the map there; "Search bar on Google's map" in settings, shown by
default. The compass row and it share a group, "On the map".

## 27.9.2026 — v89, the sheet holds its touches

Testing v88 on the emulator: the sheet moved down a line as the note above it cleared, the press
meant for CPY landed on the sheet's own background, and went THROUGH to the map, selecting parcel
5763/1 beneath and closing the sheet. Nothing on the sheet or the K panel caught a touch except its
buttons. Both now swallow every touch on them, and the veil round the K panel closes it.
From here on every test is in PORTRAIT (his word: his phone is always portrait; only the camera is
landscape).

## 27.9.2026 — v90, the sheet is the screen; one search list, as Google Maps has it

Asked (with a sheet of fifty co-owners of 3700/11 in Drenova, and Google Maps listing ten "Stjepana
Radića 13" against the app's one): the sheet over the whole screen with a filter; a cadastral
search with a dropdown for number, owner and street whose result is a point on the map; and the
search results "as Google or even better".

**Why one result.** Text Search answers an address with its best match; Google Maps lists
AUTOCOMPLETE predictions. Measured with his key: autocomplete, five towns with distances; Text
Search, one. `PlaceSearch.find` asks autocomplete for the words and again without the house letter
("13c" → "13"), plus Text Search on a pressed search, and `Finding.merge` makes one list, nearest
first. Place Details gives the tapped prediction its coordinates, in one session token.

**Owner.** Read out of OSS's own public page (its JavaScript, 27.9.2026): its public search takes a
parcel number or a POSSESSION SHEET number and nothing else; no search by a person's name is
published. So "owner's sheet": the number on any parcel's sheet lists everything that holder has
(sheet 657 in Kukljica: 37 parcels). **Street**: the state's street register (/rpj) has names and
house numbers but no coordinates, so a street is found by Google, biased to the municipality.
**Not used**: the OSS web page's embedded access token for its own faster WFS; it is that page's,
not an open service. A parcel found by number or sheet opens its sheet at once and moves the map
when the open WFS hands over its outline (~30 s; a bounded CQL filter was tried and returns nothing).

**The filter** folds case and diacritics ("cabri" finds Čabrijan) and keeps a heading's group when
the heading matches. The cyan search pin is Google's shape, anchored at its tip, on both engines.

## 27.9.2026 — v91, a visual language: what is an action, what is a choice, what is a state

His words, with the route menu and the settings: *"everything is the same and user is confused
where to click. What is option? ... Compass on the map, off, press to change ... only one word
needs to be there ... make visual distinction and design cues ... BRouter and Google can be one
button which is one or the other ... more with symbols. There are no symbols in this map. Please
create SVG icons."*

**Look.kt** defines six kinds, and every control on the reworked screens is one of them:
ACTION solid amber with icon and verb (the only solid amber: amber means "do"); ACTION quiet,
amber icon and word; TOGGLE, a switch and one noun (the switch is the state); CHOICE, one bar with
the chosen part raised and bright, never amber; FLIP, a choice of two where a tap anywhere swaps
it; OPENS, an icon, a title and a chevron; PICK, a list's row with a check on the chosen one.

**Icons**: 30, one line style (24, stroke 1.8, round ends), written once in design/make_icons.py
as SVG path data and made twice, design/icons/*.svg and res/drawable/ic_*.xml. Three redrawn after
a contact sheet was looked at (the gear read as a sun, the satellite as a tangle, the track as a
bell curve). verify.py fails the build if the code draws an icon with no drawable or no SVG, or if
a drawable drifts from its SVG.

Reworked: the key row (icons: minus, parcels, locate, record, the map as mountain / satellite /
globe, gear, pin with the next letter, plus); settings (Tracks opens; Compass and Google search bar
are switches; offline maps are picks with a check and their style one bar; Google's four views one
bar; keys with test and delete icons); the route menu (points with trash, three quiet actions, the
router one flip, profile / ways / speed bars, ROUTE the one solid button, close the cross at the
top); the K panel; the parcel sheet (copy, text, close as icons; a filter with its funnel; a
switch for highlight); Google's field. The compass is on or off now and its ink follows the map.
Twelve old checks that pinned the old wording were rewritten to hold the same rule in the new
form, each dated; none was deleted.

Not yet in the language: the tracks face, the maps (download) face, the places face, the name box.
