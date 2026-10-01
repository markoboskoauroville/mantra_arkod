# Test results · ARKOD Layer v19 · 1.10.2026 · Pixel 7 emulator (Pixel_7_API_35), Android 15, portrait
commit: c57541d (build 20)   release: v19   signed: permanent (CN=Mantra ARKOD, SHA-1 49:4A:CC:39:…:2B:6B, the same as v18)

Run by the local Claude Code on Marko's Mac (M1 Pro, arm64), from Croatia, at his word of 1.10.2026 (LOCAL_TASKS.md §4).
The APK is the CI release, downloaded with `gh release download`; nothing was built on the Mac. Screenshots are
in `tests/screens/emulator/` (v19-*.png, halved), monkey excerpts in `tests/monkey/`.

**Note on TESTING.md:** it still describes v1 (the grid key opening Moje čestice, Croatian settings group names,
zoom 21). v19 has moved on (the grid key hides the ARKOD layer, long press = parcel view; Moje čestice is under
Settings; settings are English by the rule; maximum zoom is 19). Each row below is tested against what the row
means, and the difference is named. TESTING.md should be brought up to v19.

## CI
green (the v19 release run).

## The monkey (20,000 events, throttle 80 ms, no system keys)

| seed | events injected | crashes in ARKOD | ANRs in ARKOD | other |
|------|-----------------|------------------|---------------|-------|
| 1001 | ~13,900, then stopped | 0 | **1** | — |
| 2002 | 20,000 ("Monkey finished") | 0 | 0 | 5 ANRs of **system** processes (screenshot gesture monitor, Bluetooth, Messages, Play services): the emulator overloaded |
| 3003 | 20,000 ("Monkey finished") | 0 | 0 | — |
| 1001, replayed on a quiet emulator | 5,829, then "System appears to have crashed" | 0 | 1 ("Application does not have a focused window") | the crash buffer's 48 lines are the emulator's **Bluetooth stack** aborting ("Can't start stack"), not ARKOD |

**The ANR of seed 1001, 06:28:29** (`tests/monkey/v19-anr-1001-main-thread.txt`): "Input dispatching timed out …
Waited 5293ms for KeyEvent … DPAD_UP". The main thread was **Runnable, busy in the app's own code**
(`C.D0.i ← M.n.f ← M.n.a ← o0.q0.m ← S1.a.t ← j2.H.run ← o0.Y.l`, posted to the main Handler), not waiting on a
lock or the network. Obfuscated: the CI run keeps no R8 `mapping.txt`, so the names cannot be turned back.
**Suggested:** upload `app/build/outputs/mapping/release/mapping.txt` as an artefact (or attach it to the
release) on every build. The load on the emulator was high (CPU some avg10 45 %), so a real phone may not meet
it; the stack says the work is ours, though: something heavy runs on the main thread when keys arrive fast.

## Test 2 · Test 3 · Test 4

| ID | result | what was seen | screenshot |
|----|--------|---------------|------------|
| A1 | PASS | Fresh install: the notification question, then OSM over all of Croatia at z7, OSM lit. | v19-A1_v19_firstrun.png, v19-A1.png |
| A2 | FAIL (by TESTING.md) | The map screen mixes: search field, sheets and many bottom lines Croatian; the OFF and GOO middle panels are **English** ("Offline map of Croatia", "Google map needs an API key"), and so are several bottom lines ("ARKOD layer hidden · long press: Parcel view", "WMS back online · the ARKOD layer is drawn again", "WFS offline · …"). The default track name is "2026-10-01 06-20 Track". If English is now intended for the panels, TESTING.md A2 needs the rule. | v19-B2_off.png, v19-B2_goo.png |
| A3 | PASS | Kukljica at z16: dark cadastre ink and numbers over OSM. | v19-A3.png |
| B1 | PASS (changed) | Nine keys: − · where-am-I · record · OFF · GOO · OSM · grid · ⚙ · +. No K. The grid key now hides/shows the ARKOD layer (v19), not Moje čestice. | v19-A1.png |
| B2 | PASS | OSM pressed while OSM is up, and OFF→OSM, GOO→OSM: drawn at once, 0 % black (the v1 black-map bug is gone). | v19-B2_osm_on_osm.png, v19-B2_off_to_osm.png |
| B3 | PASS | Density 480 (360 dp wide): all nine keys fit, nothing clips. | v19-B3_360dp.png |
| C1 | PASS | "Download offline map · 176 MB" and "I have a .map file"; the key row stays usable. (English, see A2.) | v19-B2_off.png |
| C2–C4 | NOT RUN | The 176 MB download was not started in this run. | |
| D1 | PASS | Seven numbered steps with links, package and SHA-1; the panel now scrolls **by finger both ways** (v1 FAIL fixed) and reaches the paste field, "Add key", "Key from a file". Panel drags do not move the map behind. **Finding:** the search field lies over the panel's title, and at the top of the scroll the bottom status line lies over the panel's lower part. | v19-B2_goo.png, v19-D1_scrolled_up.png |
| D2 | NOT RUN | | |
| D3, D4, J3 | SKIP | Key entry was left out of this session (the session's guard refused to drive the key fields); for Marko, with his own key. | |
| E1 | PASS | z17, tap on the crosshair selects the parcel under it (cyan); "3879/1 · dodirnite ponovno za list". One tap first answered "Katastar nije odgovorio: timeout" while the state's WMS was down (the state, not the app). | v19-F1_mine_unselected.png (3879/1 selected), v19-E1.png |
| E2 | PASS | Second tap: 3899, k.o. KUKLJICA · 334723, 1 439 m² · ZAGLAV, tabs uporaba · posjedovni · vlasnički, posjedovni list 1085. | v19-E1.png |
| E3 | PASS | Filter "PAVLA": "1 od 1", tab counts 0/1/1. TXT: "spremljeno: cestica 334723-3899.txt u Preuzimanja" (file present). CPY: "čestica 3899 kopirana". Both now confirm on screen (v1 did not). | v19-E3_txt.png |
| E4 | PASS | z14, tap: "Približite kartu da odaberete česticu". (A tap on a parcel still selected reopens its sheet even at z14.) | v19-E4.png |
| F1 | PASS | "Dodaj u Moje čestice" → "3899 je među mojim česticama"; on the map in its colour and line once it is not the selection (the cyan selection is drawn over it). **Finding, for a look:** the outlines do not follow the state's ink: 3899's red outline runs across the label of 3905/4, and the cyan outline of 3879/1 crosses ink lines. Either the shapes or the ink are off. | v19-F2_puna_z17.png, v19-F1_mine_unselected.png |
| F2 | PARTLY | Red + puna seen on the map; in V1 blue + točkasta kept. Isprekidana not photographed on the map; the + hue bar not tried. | v19-F1_mine_unselected.png |
| F3 | PASS (moved) | Settings → Moje čestice: the group "Moje čestice · 1 čestica · normal · shown · send", 3899 with its red line sample, pencil, bin; a press opens its sheet and takes the map there (z18). | v19-F3.png |
| F4–F6 | NOT RUN | | |
| G1 | PASS | "ARKOD čestice oko vas: spremljeno 44 %" → 88 %, counting. | v19-A3.png |
| G2 | PASS (changed) | Settings → KEPT ON THIS PHONE: "30.9 MB · 527 ARKOD tiles · 88 sheets and answers · 0 outlines". | |
| G3 | PASS | Airplane mode, force-stop, open, z17: the kept cadastre lines and Moje čestice still draw; every light red. **Finding:** with no base map the ink is dark grey on black and hard to read (MT-MAP-1). | v19-G3_offline_z17.png |
| G4 | PASS | Offline, 3899's sheet: "bez signala: prikazan zapis od 1.10.2026." | v19-G4_offline_sheet.png |
| G5 | NOT RUN | | |
| H1 | PASS | Home, force-stop, open: same place and zoom (N 44 02.219, z14), drawn at once, 0 % black. **But** a force-stop without leaving the app first lost a zoom change made 20 s earlier (z17 → reopened z14); and on v18 a force-stop a minute after first launch reopened on Croatia z7. The view seems saved on pause, not on change. | v19-H1_reopen.png |
| J1 | CHANGED | Settings are English; the first group is KEPT ON THIS PHONE, Moje čestice further down. Whole scroll not photographed. | |
| J2 | NOT RUN | | |
| K1 | PASS (on v18) | Record, stop, the name box "Odbaci / OK", named "V1 staza"; first save asks for a folder once (Documents), "Spremljeno u Documents: V1 staza"; V1 staza.gpx present. | |
| K2 | NOT RUN | | |
| U1, U2, U4, U6 | NOT RUN | | |
| U3 | FAIL (minor) | The map keeps its place through rotation; the search list and a sheet survive it. **But in landscape the sheet's owner list is squeezed to no height**: header, tabs and the Moje čestice controls fill the screen, the posjedovni rows are not visible. (Marko's phone is portrait.) | v19-U3_sheet_land.png |
| U5 | PASS | OFF, GOO, OSM thirty times fast, ending on OFF: the Offline panel shows; same process, no crash. | v19-U5_last_OFF.png |
| U7 | PASS (changed) | Zoom in stops at **z19** (TESTING says 21), out to z3 over Europe: no crash, no stuck tiles. | v19-U7_out.png |
| V1 | PASS | v18 installed fresh, Moje čestice 1358/3 in blue + točkasta, a track "V1 staza"; v19 installed **over** it (no uninstall: both carry the permanent key): 1358/3 still mine, blue, točkasta, on; the track file still there; the app reopened at the same place and zoom. The Google key was not part of it (see D3). | |

## The manifest's registry (MANTRA_MANIFEST modules/mantra-testing.md, branch claude/gifted-curie-nbt328)

| row | result | from |
|-----|--------|------|
| MT-KEY-4 | PASS | every byte of the v19 APK searched for AIza, AQ., ghp_, github_pat_, gsk_, sk-ant-, sk_ shapes: **0** |
| MT-ST-1, MT-ST-2, MT-ST-3 | PASS, PASS (with the H1 note), PASS | A1, H1, V1 |
| MT-OFF-1, MT-OFF-2 | PASS, PASS | G3, G4 |
| MT-UI-1, MT-UI-2, MT-UI-3, MT-UI-4, MT-UI-6, MT-UI-7 | PASS, FAIL, PASS, FAIL (minor), PASS, PASS | B3, A2, E1, U3, U5, D1 |
| MT-MAP-1 | PARTLY | OSM: readable; offline with no base map: ink barely readable (G3) |
| MT-MAP-2 | PASS | red solid mine vs dark ink vs cyan selection (F1) |
| MT-MAP-3, MT-MAP-4 | PASS, PASS | U7; B2 and H1 (cold start drawn at once) |
| MT-DL-1 | PASS | the size on the button (C1); MT-DL-2..5 not run |
| MT-KEY-1 | PASS (partly) | D1; MT-KEY-2, MT-KEY-3 skipped (D3) |
| MT-UI-5, MT-OFF-3, MT-WEB-* | NOT RUN / not this app | |

MANIFEST_INTRO names no test beyond the Four Tests, which TESTING.md already is.

## English seen (A2)
"Offline map of Croatia" panel; the whole Google key panel; bottom lines "ARKOD layer hidden · long press: Parcel
view", "WMS back online · the ARKOD layer is drawn again", "07:14 WFS offline · ORA-01000: maximum open cursors
exceeded" (the last is the state's own Oracle text); "Import a file" in Moje čestice; the default track name "… Track".

## Anything else noticed
- **Fresh install, search by k.o.:** "1358/3 kukljica" answers *k.o. "kukljica" još nije poznata ovom telefonu:
  pomaknite kartu iznad nje jednom*; a new user who has never panned there cannot search by place name. After a pan
  over Kukljica the same search works.
- **Taps through the sheet:** three taps on the bottom-left corner of an open sheet (no control there) reached the
  map's − key beneath and zoomed the map 17 → 14.
- **A black rectangle** where tiles were not drawn, once, after a force-stop at z16 on v18 (v1_kuk3, not kept).
- **The launcher icon is ARKOD's own** (the orange grid on black), not Mantra Trail's: the open-list item looks done.
- The emulator's GPS ignored `geo fix` after boot (an emulator fault), so where-am-I was not tested; places were
  reached by panning.
- WFS was red the whole morning (ORA-01000); WMS went offline and back twice (07:15, 07:27–07:28).

---

# Test results · Mantra ARKOD v1 · 29.9.2026 · Pixel 7 emulator, Android 15 (API 35), portrait
commit: 5bbe9db   release: v1   signed: permanent (ARKOD_KEYSTORE made 29.9.2026, SHA-1 49:4A:CC:39:…:2B:6B, matches the one shown in the Google panel)

**PARTIAL RUN: stopped at E4 because the local session hit its usage limit. The IDs marked NOT RUN still need a run.**

## CI
green (run 36581215068). The first run published v1 signed with a throwaway key. After Marko approved the permanent key, the throwaway v1 release was deleted and the same run was re-run, so v1 is now signed with the permanent key. No code was changed.

| ID | result | what was seen | screenshot |
|----|--------|---------------|------------|
| A1 | PASS | Fresh install opened on OSM at z7, OSM key lit. Centred a little east (Istria is cut at the left edge; Dubrovnik is visible). No location permission was asked; only the notification one was. | tests/screens/A1.png |
| A2 | PASS | Every word from the app on the map screen is Croatian. English was seen only in the system notification dialog. | tests/screens/A1.png |
| A3 | PASS | Kukljica at z16: dark cadastre lines and parcel numbers over OSM. | tests/screens/A3.png |
| B1 | FAIL (minor) | Nine keys: − · where-am-I · record · OFF · GOO · OSM · **a grid icon ⊞, not ★** · ⚙ · +. There is no K key. The grid key opens Moje čestice. | tests/screens/A1.png |
| B2 | FAIL | Each key lights itself and the top line names the map (OSM / Offline / satelit). **But pressing OSM while OSM is already active turns the map black**, and OFF→OSM stayed black; GOO→OSM drew. A pan brings the map back. | tests/screens/B2.png, B2_afterpan.png |
| B3 | PASS | At 360 dp (density 480) all nine keys fit; nothing clips. The map behind went black after that configuration change (see H1 note). | tests/screens/B3.png |
| C1 | PASS | "Preuzmi offline kartu · 175 MB" (TESTING says 176) and "Imam .map datoteku"; the key row stays usable. | tests/screens/C1.png |
| D1 | FAIL | The seven steps, the package name and the SHA-1 are shown. **The paste field, "Dodaj ključ" and "Ključ iz datoteke" are below the fold, and an upward finger drag does not scroll the panel** (tried five times). Only a mouse-wheel scroll moved it; a downward drag did scroll. The key is shown **in clear text while typing** (MT-KEY-3). | tests/screens/D1.png, D1_bottom.png |
| D2 | PASS (partly seen) | Each link sends ACTION_VIEW to Chrome with https://console.cloud.google.com/… (logcat). The six URLs in Screens.kt:948-953 are right. The emulator's Chrome showed a blank page, so the Google pages themselves were not seen. | tests/screens/D2.png |
| D3 | PASS | A key-shaped fake key gave "ključ 1: Google: API key not valid. Please pass a valid API key." in red at the top of the panel, plus a "ponovno" button, and the same text on the bottom line. No crash. It is Google's text, in English, as intended. The fake key is still stored as ključ 1. | tests/screens/D3.png |
| D4 | SKIP | No key from Marko. | |
| E1 | PASS | z17, a tap on the crosshair selected 1655/3 (cyan outline); bottom line "1655/3 · dodirnite ponovno za list". | tests/screens/E1.png |
| E2 | PASS | The sheet shows 1655/3, k.o. KUKLJICA · 334723, 61 m² · ŽAVRH, and the tabs uporaba / posjedovni (p.l. 1984) / vlasnički (z.k. uložak 182, owners with shares, auto-linked). | tests/screens/E2.png, E2_vlasnicki.png, E2_uporaba.png |
| E3 | PASS | The filter "ANICA" gives "4 od 53" with tab counts 0/0/4. CPY puts the sheet on the clipboard (system preview). TXT wrote Download/cestica 334723-1655_3.txt with the correct content, but **no confirmation was seen on screen**. | tests/screens/E3_filter.png, E3_cpy.png |
| E4 | NOT RUN | The run was stopped here. | |
| F1–F6, G1–G5, H1, J1–J3, K1–K2, U1–U7 | NOT RUN | These are still to do. | |

## English seen (A2)
- The Google key panel quotes Google's own UI: "Billing", "Map Tiles API", "Enable", "Places API (New)", "APIs & Services → Credentials → Create credentials → API key". This is intentional.
- Google's refusal: "API key not valid. Please pass a valid API key." (D3, intentional).

## Anything else noticed
- **The map is black after a cold start into a saved z16 view** until the first pan (reproduced: force-stop at z16 → relaunch → black; pan → drawn). The same happens after the 360 dp density change. The host-side emulator screenshot was black too, so this is not a screencap artefact. This belongs to H1 and U3. Screenshot: tests/screens/H1_coldstart_black.png.
- Where-am-I centres on the position but keeps the zoom (z7); it does not zoom in.
- At z7 the ARKOD prefetch already counted "spremljeno 192 od 325 pločica".
