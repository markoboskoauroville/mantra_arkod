# Delivery record — v1

    artefact   1-mantra-trail-v1.apk
    built      GitHub Actions, from the commit the release names. Never on a desk.

## MEASURED

    Test 1            87 cases, 0 failures, 0.107 s (kotlinc + JUnit, no Android SDK)
    harness proved    5 cases were red first and all 5 were wrong expectations in the test
    TK25 WMS          one real GetMap in EPSG:3857: 200, image/png, 65,487 bytes,
                      33,562 opaque pixels, 1,933 distinct colours — real map, not a blank tile
    mapsforge         MapCanvas.kt typechecked against the real 0.25.0 jars and android.jar
    icon              painted extent 40 of the 72 unit mask = 0.556, inside the 0.55–0.60 band
    gates             G1 provenance, G2 history and artefact, G3 verify.py, G5 loops — in CI

## NOT TESTED

Said as plainly as what works (four-tests.md §4).

- **Nothing has run on a phone.** No fix has been taken, no track recorded, no GPX opened in
  another app, no tile drawn on a screen. Everything below the arithmetic is unproven.
- **Test 2 does not exist yet.** The one real call made was to the TK25 service with curl, not
  from the app.
- **Test 3 does not exist yet.** No sabotage: no revoked folder permission, no deleted map file,
  no network pulled mid-fetch, no recording carried through a reboot.
- **Test 4 cannot exist yet.** There is no previous version to upgrade from. It becomes real at v2
  and it is the one that matters most, because the cost of failing it is lost tracks.
- **The Google layer has never been displayed.** The key is in a repository secret and reaches
  the build; whether the SDK accepts it, and whether the key's restrictions allow this package
  name, is unknown until the app is opened.
- **The launcher icon's ratio was computed from the path geometry, not rendered and measured.**
  `app-icon.md` §3 asks for a render. Do that at v2.
- **The signing key was made in a sandbox, not on the phone** (`android-app.md` §3). Its only copy
  is the repository secret. See HANDOFF.md for the cost of changing it later.

## v86 — the cadastre (27.9.2026), Pixel 7 emulator, Android 15, the CI APK

Proved, with screenshots, at Kukljica (Rt Loparić) over openhiking-croatia.map:
- the state's parcels and numbers drawn over the offline map, as on the portal;
- a tap on 2451 opened its card in under 3 s: municipality, area, address, land use, possession
  sheet with the possessor, share and address, and the land-registry unit (z.k. uložak 37, ZK
  odjel Zadar);
- the highlight tick and the five trail colours; red chosen, the tick and the ✓ follow;
- K panel: the switch, the municipality under the map read at once, search "2450, 2449/3" found
  both in one press, highlighted them in the chosen blue and opened the first card;
- the outlines arrived through the slow WFS behind the scenes (about 30 s) and were drawn exactly
  on the state's lines, 2451 red, 2449/3 and 2450 blue;
- network off, app restarted: the lines of the cadastre are gone (never kept), the three highlights
  remain, and a tap on a highlighted parcel still opens its card, saying the owners need a signal.

NOT tested: the Google engine's cadastre overlay and its tap (no Google key on the emulator); the
K switch turning the layer off and on; a real phone and a real thumb (Test 4 is his).

## v87 — one tap highlights, the next opens the sheet; K; TXT (27.9.2026), Pixel 7 emulator

Proved, with screenshots, in Zagreb (Ribnjak) over openhiking-croatia.map:
- K in the key row where T was; the compass's three states in settings ("Compass on the map");
- first tap on 5925: highlighted in amber within 3 s, "5925 highlighted · tap it again for its
  sheet"; the traced outline encloses 323 m² against the official 330 m²;
- second tap on 5925: the sheet, tick on, amber marked, TXT beside the ✕;
- TXT: "saved parcel 335240-5925.txt in Downloads"; the file read back off the phone holds the
  municipality, area, address, land use, possessor, land registry unit and the seven corners.
NOT tested: the Google engine; a parcel larger than 300 m across (the 900 m and 2700 m pictures);
the TXT into a chosen tracks folder; his thumb.

## v89 — the tap selects, CPY, the sheet holds its touches (27.9.2026), emulator in PORTRAIT

Proved in portrait (1080×2400, his phone's way up), Zagreb, Ribnjak:
- tap 5925 then 5927: the cyan selection moved, 5925's outline gone (v88, landscape; v89 portrait
  again with 5927 → 5924);
- a tap on the selection opened the sheet with CPY and TXT; a press on the sheet's plain text did
  nothing (before v89 it went through to the map and selected the parcel beneath);
- CPY: "parcel 5927 copied", Android's clipboard preview showing "PARCEL 5927 cadastral reference…";
- red swatch kept 5927 red after the sheet closed and the selection moved on;
- settings: "Search bar on Google's map · shown · press to hide".
NOT tested: the Google search field itself (no Google key on the emulator); his thumb.
