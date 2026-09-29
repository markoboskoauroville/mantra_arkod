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
