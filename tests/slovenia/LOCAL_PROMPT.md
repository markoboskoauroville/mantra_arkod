# Slovenia (v23): the prompt for the local Claude Code on Marko's phone (Termux)

Paste everything under the line into the local Claude Code session in Termux.

---

You are the local half of a two-session team testing ARKOD Layer v23, which adds Slovenia's cadastre (GURS) to the
Croatian one. The cloud Claude session wrote the code, but its network cannot reach `eprostor.gov.si`. You can: you
run on Marko's phone, on the phone's own internet. **Your job is to collect GURS's real answers and to test the app
on this phone, then hand everything back through git.** The cloud session reads what you push, fixes the code, pushes
again, and you test again, until Slovenia works the way Croatia does.

## How we talk

- Repository: `markoboskoauroville/mantra_arkod`, branch **`ccr-dde7958e-5nzzpy`**. Never push to `main`.
- Write everything under `tests/slovenia/`. Commit with clear messages and push to that branch after each step, so
  the cloud session sees progress early. **Raw answers go in as files, untouched** (no summaries in place of them).
- Your report goes in `tests/slovenia/RESULTS.md`. Every check has its ID and PASS / FAIL / SKIP, and says **what
  you saw**, not what was expected. Add the command or the URL you used.
- When you finish a round, end `RESULTS.md` with a line `ROUND n DONE <date time>` and push. Then tell Marko:
  "round n pushed, tell the cloud session". When the cloud session pushes a fix, it adds `FIX n` to
  `tests/slovenia/CLOUD.md`: `git pull`, read it, and run the round again.
- Do not change code under `app/`. Fixes are the cloud session's job. If you are sure of a fix, write it as a diff
  in `RESULTS.md`.

## Setup (once)

    pkg install -y git gh curl jq python   # whatever is missing
    gh auth status || gh auth login
    gh repo clone markoboskoauroville/mantra_arkod ~/mantra_arkod 2>/dev/null; cd ~/mantra_arkod
    git fetch origin ccr-dde7958e-5nzzpy && git checkout ccr-dde7958e-5nzzpy && git pull
    mkdir -p tests/slovenia/gurs

## Round 1, part A: GURS's real answers (the most important part)

The app asks these. Save each answer to `tests/slovenia/gurs/<name>` with `curl -sS -A "ARKOD Layer" -o FILE -w "%{http_code} %{size_download} %{content_type}\n" URL`,
and record the status line in RESULTS.md.

    W=https://ipi.eprostor.gov.si/wms-si-gurs-kn/wms
    F=https://ipi.eprostor.gov.si/wfs-si-gurs-kn/wfs

- **G1** `wms_caps.xml`: `$W?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities`. Is the layer `SI.GURS.KN:PARCELE` there?
  Write down its name exactly, its styles, and whether it is queryable. List every layer whose name has PARC in it.
- **G2** `wfs_caps.xml`: `$F?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetCapabilities`. Write down the exact names of the
  parcels layer and the cadastral municipalities layer (KATASTRSKE_OBCINE?), plus any house-number or address layer
  (HISNE_STEVILKE, RPE, NASLOVI …). List the GetFeature output formats (is `application/json` among them?).
- **G3** `parcele_schema.xml`: `$F?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType&TYPENAMES=SI.GURS.KN:PARCELE`.
  Same for the municipalities layer: `ko_schema.xml`.
- **G4** `tile_ljubljana.png`: one map tile over Ljubljana (z16 35408/23301), exactly as the app asks for it:
  `$W?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=SI.GURS.KN:PARCELE&STYLES=&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=512&HEIGHT=512&FORMAT_OPTIONS=dpi:180&BBOX=1614350.037382923,5788423.277979827,1614961.533609204,5789034.774206109`
  Check it is a PNG (`file`), and with python count the opaque pixels (alpha ≥ 60) out of 262144. Are they lines, or
  is everything filled?
- **G5** `tap_ljubljana.json`: the tap at Kongresni trg:
  `$F?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&OUTPUTFORMAT=application/json&SRSNAME=urn:ogc:def:crs:EPSG::4326&TYPENAMES=SI.GURS.KN:PARCELE&COUNT=10&BBOX=46.05045,14.50355,46.05055,14.50365,urn:ogc:def:crs:EPSG::4326`
  Write down the property names of the first feature, and whether the coordinates come longitude first or latitude first.
- **G6** `ko_ljubljana.json`: the same BBOX with `TYPENAMES=<municipalities layer>`. Which property holds the name?
- **G7** `by_reference.json`: one parcel from G5 asked by its number, the way the app asks for a sheet:
  `...&TYPENAMES=SI.GURS.KN:PARCELE&COUNT=2&CQL_FILTER=` + URL-encoded `KO_ID=<ko> AND ST_PARCELE='<number>'`
  (use the real property names if they differ, and say so).
- **G8** `suggest.json`: `CQL_FILTER` = `KO_ID=<ko> AND ST_PARCELE LIKE '12%'`. Does LIKE work?
- If any request fails, save the error body and try the obvious variation (WFS 1.1.0, `outputFormat=json`,
  `srsName=EPSG:4326`), and record what worked.

## Round 1, part B: Petrina 2 (Marko's use case)

Petrina is a village in the municipality of Kostel, on the Kolpa river, opposite Brod na Kupi (Croatia), about 45.465 N 14.853 E.

- **P1** Find the point of the house **Petrina 2**: from GURS's address or house-number layer (G2) by settlement
  "Petrina" and house number 2, and if that fails, from OSM Nominatim
  (`https://nominatim.openstreetmap.org/search?q=Petrina+2,+Kostel,+Slovenia&format=json`, with a User-Agent).
  Save to `petrina2_address.json`, and write down the latitude and longitude.
- **P2** `petrina2_parcel.json`: the G5 tap query at that point. Which parcel number and k.o. is it, and what area?
  Save every attribute GURS gives for it. This is what the app's sheet will show.
- **P3** `petrina2_ko.json`: its cadastral municipality (number and name).
- **P4** Which other public sheets exist for that parcel? Check without signing in: the GURS public view
  (`https://eprostor.gov.si/imps/` and the "javni vpogled" pages), buildings (stavbe) or parts of buildings over
  that point in the WFS, and land use (dejanska raba) if GURS publishes it. Record what you could read with no
  login. **Do not try to get owners' names.** Slovenia does not publish them openly, and the land register (e-ZK)
  needs a sign-in. Just say what each service required.
- **P5** Petrina is on the border, so ask the Croatian state for the same point too (the app does):
  `https://api.uredjenazemlja.hr/services/inspire/cp_wms/wms?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS=cp:CP.CadastralParcel&QUERY_LAYERS=cp:CP.CadastralParcel&STYLES=&CRS=EPSG:4326&BBOX=<lat-0.0002>,<lon-0.0002>,<lat+0.0002>,<lon+0.0002>&WIDTH=101&HEIGHT=101&I=50&J=50&FEATURE_COUNT=1&INFO_FORMAT=text/plain`
  Expected: "no features were found". Write down what came back.

Push and write `ROUND 1A/1B DONE`. Part C can follow.

## Round 1, part C: the app on this phone

The release is built by GitHub Actions from this branch (workflow "Build ARKOD Layer", release **v23**).

    gh run list -R markoboskoauroville/mantra_arkod --branch ccr-dde7958e-5nzzpy --limit 3
    gh release download v23 -R markoboskoauroville/mantra_arkod --pattern '*.apk' --dir ~/storage/downloads --clobber
    termux-open ~/storage/downloads/*v23*.apk     # Marko taps Install (termux-setup-storage first if needed)

If the run is red, put `gh run view <id> --log-failed | head -200` in RESULTS.md under **CI** and push. That is the
whole round. (A TEST BUILD signed with a throwaway key may need the old app uninstalled first: ask Marko.)

**Optional, recommended:** wireless ADB from Termux lets you drive and screenshot the app yourself.
`pkg install android-tools`. Then Developer options → Wireless debugging → Pair device with code: `adb pair localhost:<port> <code>`,
`adb connect localhost:<port>`. Then `adb shell screencap -p /sdcard/x.png`, `adb pull`, `adb shell input tap X Y`. If Marko
does not want this, give him each step below and ask him for a screenshot (put screenshots in `tests/slovenia/shots/`).

Run each check and write what was seen, with a screenshot for anything visual:

- **SLO1** OSM map, Ljubljana center, zoom 17. Are Slovenian parcel lines drawn? Is the SLO light (under the
  coordinates) green? Compare the look with Croatia (Kukljica, zoom 17): same colour, thickness and transparency?
- **SLO2** Tap a parcel in Ljubljana: does a cyan outline appear at once, with the number on the note line? Tap it
  again: does the sheet open on "uporaba" with k.o., area and the GURS rows? What do "posjedovni" and "vlasnički"
  say? Do CPY, TXT and FILE work?
- **SLO3** Petrina, zoom 18: lines on both banks of the Kolpa (Slovenia north, Croatia south)? A tap on a Croatian
  parcel across the river: does it still open the Croatian sheet with owners?
- **SLO4** **Petrina 2:** type "Petrina 2 Kostel" in the Google search field (if Marko has a Google key), else go to
  P1's point. Tap the house's parcel, then tap again. Does the sheet match P2? Keep it in Moje čestice (any colour).
- **SLO5** In the parcel field, type the parcel number from P2 while the map is at Petrina. Do suggestions appear?
  Does Search open it?
- **SLO6** Airplane mode, then force-stop and reopen the app. Is the Petrina parcel still drawn in Moje čestice, and
  does its sheet open ("bez signala")? Are the Ljubljana tiles you saw still drawn?
- **SLO7** Settings → Services: is "Slovenia's cadastre (GURS)" listed, and does Check now work?
- **SLO8** Croatia did not get worse: Kukljica 1358/3 tap, sheet, owner sheet, as before.

Push, write `ROUND 1 DONE`, and tell Marko to tell the cloud session.
