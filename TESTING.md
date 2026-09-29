# Testing Mantra ARKOD: the cloud and the local Claude Code as one team

Marko, 29.9.2026: *"cloud code in the cloud and cloud code local are both working together as one
team ... he will write these results inside the repository so you will be able to see the test
results."*

**Who does what.**

| | The cloud session | The local Claude Code (the Mac) |
|---|---|---|
| Test 1: the mechanism | runs it (`kotlinc`/Gradle JVM + JUnit, 194 cases) | runs it again in CI |
| `scripts/verify.py` | runs it (222 checks) | runs it again in CI |
| the APK | **never builds it** (android-app.md §1a) | **never builds it either**: GitHub Actions does |
| Test 2: in the running app | cannot (no emulator, no phone) | installs the CI release on the emulator or phone and drives it |
| Test 3: the ugly cases | cannot | offline, killed, rotated, refused |
| Test 4: the upgrade | cannot | from v2 on |
| results | reads `TEST_RESULTS.md` and fixes | writes `TEST_RESULTS.md` and pushes it |

Every test has an ID. A result names the ID, says PASS, FAIL or SKIP, says **what was seen** (not
what was expected), and points at a screenshot for anything visual. A FAIL with no screenshot
and no words is not a result.

---

## Step 0: the repository, the key, the first build (once)

0.1  **Create the repository and push the app to it.** The cloud session was refused both ways
     (the GitHub connection answered 403; its permissions forbid creating a public repository).

         gh repo create markoboskoauroville/mantra_arkod --public \
            --description "Mantra ARKOD: katastarske čestice Hrvatske, posjedovni i vlasnički list"
         git clone --branch claude/gifted-curie-nbt328 https://github.com/markoboskoauroville/MANTRA_TRAIL mantra_arkod
         cd mantra_arkod
         git remote rename origin trail
         git remote add origin https://github.com/markoboskoauroville/mantra_arkod
         git push -u origin claude/gifted-curie-nbt328:main
         git branch -M main && git branch -u origin/main

0.2  **The signing key.** Without the secret `ARKOD_KEYSTORE` the workflow still builds, signed
     with a key made for that one run, and the release says TEST BUILD: every later build needs an
     uninstall first. Ask Marko before making the permanent key; follow android-app.md §3. When he
     agrees:

         keytool -genkeypair -keystore arkod.p12 -storetype PKCS12 -alias arkod -keyalg RSA \
            -keysize 2048 -validity 36500 -dname "CN=Mantra ARKOD"
         base64 -i arkod.p12 | gh secret set ARKOD_KEYSTORE -R markoboskoauroville/mantra_arkod
         gh secret set ARKOD_KEYSTORE_PASSWORD -R markoboskoauroville/mantra_arkod
         # keep arkod.p12 and its password where Marko keeps keys; never commit it

0.3  **Watch the first build.** `gh run watch -R markoboskoauroville/mantra_arkod`. It is the first
     time the Android half has been compiled: the cloud had no Android SDK. **If it is red, that is
     the result:** put `gh run view --log-failed` (the first 200 lines are enough) into
     `TEST_RESULTS.md` under **CI**, push, and stop. Do not fix the code locally; the cloud fixes it.

0.4  **Install the release** on the Pixel emulator (Android 15) and, if Marko allows, his phone:

         gh release download -R markoboskoauroville/mantra_arkod --pattern '*.apk' --dir /tmp/arkod
         adb install -r /tmp/arkod/*.apk

     For a clean first run, `adb uninstall com.mantra.arkod` first.

Screenshots: `adb exec-out screencap -p > tests/screens/<ID>.png`, committed with the results.

---

## Test 2: inside the running app, driven the way a person drives it

**A · first run**

- A1  A fresh install opens on **OpenStreetMap over the whole of Croatia** (zoom 7), OSM key lit.
- A2  Every word on the map screen is Croatian. List any English word seen, with its screen.
- A3  Zoom into a town to z15+: the cadastre's lines and numbers appear, dark ink on OSM.

**B · the key row**

- B1  Nine keys, left to right: −, where-am-I, record, **OFF, GOO, OSM**, the parcels grid (Moje čestice), ⚙, +. No K key.
- B2  Each map key switches at once, lights itself, and the top line names the map. Pressing the
      key of the map already up, and OFF → OSM, draws at once with no pan (v2 fix).
- B3  Nothing clips at 360 dp wide (a small phone); screenshot the row.

**C · the offline map (OFF)**

- C1  With no map on the phone, the middle of the screen offers *Preuzmi offline kartu · 176 MB*
      and *Imam .map datoteku*. The key row stays usable under it.
- C2  Press it: the middle shows the **percentage, MB done of total, and the speed**, updating.
      Screenshot at some point past 10 %.
- C3  Switch to OSM during the download: the download goes on; the bottom line says
      *offline karta: NN% …*.
- C4  When it finishes, OFF draws the map of Croatia and the offer never appears again, also
      after a restart.

**D · Google (GOO)**

- D1  With no key, the middle shows *Google karta treba API ključ*, seven numbered steps, the
      package name and SHA-1, a paste field and *Ključ iz datoteke*.
- D2  Each step's link opens the right Google Cloud page in the browser.
- D3  Paste a wrong key (a real one with the last four characters changed, four-tests.md
      Test 2): Google's own refusal is shown under the key; no crash, no English from us.
- D4  (Only with Marko's own key) paste the real key: the satellite view draws, the cadastre's
      ink turns light over it. Remove the key afterwards if it is not his to keep there.

**E · the parcel under a tap**

- E1  At z15+, a tap **in the exact middle of the screen, on the crosshair**, selects the parcel
      there (cyan outline). No full screen, no bars hidden.
- E2  A second tap on the selection opens the sheet: number, k.o., area, three tabs
      *uporaba · posjedovni · vlasnički*.
- E3  The filter keeps only matching rows; TXT writes a file; CPY copies.
- E4  Below z15 a tap says *Približite kartu da odaberete česticu*.

**F · Moje čestice**

- F1  On a sheet, *Dodaj u Moje čestice*: the parcel is drawn **dashed** in the chosen colour on
      the map. Screenshot at z17.
- F2  Another swatch, then the + hue bar: the colour on the map follows. Try all three line
      styles: *isprekidana, puna, točkasta*; screenshot each.
- F3  ★ opens Moje čestice: the parcel is listed with its line sample; the pencil names it; a
      press takes the map there and opens its sheet.
- F4  The bin asks *sigurno?* before deleting; *obriši sve* asks twice.
- F5  Settings: *Moje čestice* is the first group and opens the same list.
- F6  Search inside Moje čestice by number (2450 in k.o. Kukljica), and by possession sheet.

**G · the cadastre kept on the phone**

- G1  Online, rest the map at z16 over a town for a minute: the bottom line shows
      *ARKOD: spremljeno N od M pločica*, counting up.
- G2  Settings → *ARKOD na telefonu* shows the count and MB.
- G3  Airplane mode, force-stop the app, open it, pan the same place at z16–18: the cadastre's
      lines still draw. Screenshot.
- G4  Still offline, open one of Moje čestice from ★: its sheet opens with
      *bez signala: prikazan zapis od …*.
- G5  *obriši* in that group (twice) empties it; G3 then shows no lines.

**H · it opens where it was left**

- H1  Choose GOO or OFF, zoom 17, turn the map; swipe the app away; open it: the same map, place,
      zoom and turn, **drawn at once with no pan** (v1 was black until a pan).

**J · settings**

- J1  Group order: Moje čestice, Offline karta, Google karta, API ključevi, ARKOD na telefonu,
      Tragovi, Na karti, O aplikaciji. Screenshot the whole scroll.
- J2  A tap on *Mantra ARKOD · verzija N* opens the releases page.
- J3  Paste a key in *API ključevi*: it appears masked, is tested, and says *radi* or Google's words.

**K · what stayed from Mantra Trail**

- K1  The record circle records a walk; stop; the name box says *Odbaci / OK*; the walk is in
      *Tragovi*.
- K2  The compass toggle in settings shows and hides the compass.

## Test 3: the ugly cases

- U1  Network off in the middle of the offline download, then on: *Nastavi preuzimanje · X od
      176 MB* continues from where it stopped, not from zero.
- U2  Tap a parcel with the network off, in a place never seen: a Croatian sentence, no crash.
- U3  Rotate the phone on each screen (map, sheet, Moje čestice, settings, the two middle panels).
- U4  Deny the location permission: where-am-I says so; the map still works.
- U5  Press OFF, GOO, OSM ten times fast: no crash, the last one pressed is what is drawn.
- U6  Force-stop while the prefetch is counting: the next start draws what was kept, no half tile.
- U7  Zoom to 21 over a parcel, then out to 5: no crash, no stuck tiles.

## Test 4: the upgrade

- V1  **From v2 on:** install the previous release, make Moje čestice (colour, style, name), a
      Google key and a track, leave it running, install the new release over it: every one of
      them is still there with its value, not its default. (Not applicable to v1: there is no
      previous version, and Mantra Trail is a different app, `com.mantra.trail`.)

---

## TEST_RESULTS.md: the shape of the answer

    # Test results · Mantra ARKOD vN · <date> · <device, Android version>
    commit: <sha>   release: <tag>   signed: permanent | throwaway

    ## CI
    green | red (then the failing log, first 200 lines)

    | ID | result | what was seen | screenshot |
    |----|--------|---------------|------------|
    | A1 | PASS | opened on OSM, all Croatia, z7 | tests/screens/A1.png |
    | C2 | FAIL | percentage stayed at 0 % for 40 s while MB grew | tests/screens/C2.png |

    ## English seen (A2)
    ## Anything else noticed

Commit `TEST_RESULTS.md` and `tests/screens/` to `main` of mantra_arkod and push.

---

## The tests that go to the manifest

Every test above that would catch a bug in **another** app too (a download that shows its
speed, a key help that names the steps, state restored after a kill, a cache that survives
airplane mode) is written into MANTRA_MANIFEST `modules/mantra-testing.md` by the session that
wrote it. That module is the one registry every app's session reads before it tests.
