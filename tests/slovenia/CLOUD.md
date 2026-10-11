# Slovenia (v23): what the cloud session did, round by round

The local session reads this after each `git pull`. Every fix is marked `FIX n` and says what to test again.

## Round 0, 11.10.2026

v23 pushed (Slovenia.kt and the routing in ParcelNet). The release build was started from this branch
(workflow_dispatch, "Build ARKOD Layer"). Nothing has been measured against GURS yet: start with
LOCAL_PROMPT.md, Round 1.

## FIX 1, 11.10.2026 (from ROUND 1A/1B): release v24

Thank you: every finding was right, and the real answers caught one more bug (below).

- **S1** A number typed without the star now also asks the building parcel: "98/5" asks `'98/5'` and `'*98/5'`.
  Suggestions ask `LIKE '98%' OR LIKE '*98%'`. Search by number does the same.
- **S2** TXT's file name turns `*` into `z` (`cestica SI1613-z98_5.txt`). FILE already strips it (MarkFile.fileName).
- **S3** The sheet is clean. IDs, codes, EN/IT/HU/OPIS, E_CEN/N_CEN and nulls are gone, and a coded field shows only
  its `_NAZIV_SL` ("upravni statusi: ni urejena"). The k.o. shows as "KUŽELJ", not "1613 KUŽELJ".
- **S4** The sheet's "uporaba" tab now also has the actual land use (with %), the planned use, the soil rating, and
  every building on the parcel (number, year built, type, floors, flats, roof/facade renewal, utilities) with its parts
  (use, m²). **The address comes from the building part: "Kostel, Petrina 2"**, under the area at the top.
- **S5** "vlasnički" shows a **public owner** when GURS's `PARCELE_JAVNI_LASTNIKI` has one (WMS GetFeatureInfo,
  application/json). The note for private owners is rewritten: e-ZK with a sign-in, or an extract from a court or a
  notary, using the k.o. and number on the sheet. No URL is claimed any more.
- **S6** No change needed: the restyle keeps only the alpha, so GURS's green becomes our ink (now a unit case).
- **Bug the real answers caught:** GURS's 18-digit EIDs went through a Double and came back wrong
  (…338102 read as …338096), so the land-use questions would have asked for the wrong parcel. Digits now stay digits.
- **Numbers on the map:** tiles now ask `LAYERS=SI.GURS.KN:PARCELE,SI.GURS.KN:PARCELE&STYLES=nep_kn_parcele,nep_kn_parcele_lbl`.
- Test 1: 261 cases, 0 failures. SloveniaRealTest (9 cases) runs on your files, copied untouched to
  `app/src/test/resources/slovenia/`. verify 269. The app's Kotlin compiles.

### Round 2 for you

- **G9** `tile_ljubljana_lbl.png`: the G4 tile with the new layers/styles above (copy the URL from `Slovenia.tileUrl`:
  `...&REQUEST=GetMap&LAYERS=SI.GURS.KN:PARCELE,SI.GURS.KN:PARCELE&STYLES=nep_kn_parcele,nep_kn_parcele_lbl&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=512&HEIGHT=512&FORMAT_OPTIONS=dpi:180&BBOX=<same as G4>`).
  Is it a PNG (not an XML error)? Are the numbers drawn next to the lines? Count opaque pixels again.
- **G10** `pravne_osebe_schema.xml`: DescribeFeatureType for `SI.GURS.KN:LASTNISTVO_PRAVNIH_OSEB`. Then one GetFeature
  for Ljubljana's 3200/1 (`EID_PARCELA='100100000748099824'`, or whatever key the schema has) → `pravne_osebe_3200_1.json`.
  This is open data about companies and public bodies only. Do not query private persons' types.
- **G11** `javni_lastniki_3200_1.json`: `Slovenia.publicOwnersUrl(46.0505, 14.5036)` (WMS GetFeatureInfo,
  INFO_FORMAT=application/json, LAYERS=QUERY_LAYERS=SI.GURS.KN:PARCELE_JAVNI_LASTNIKI). Kongresni trg is likely public.
- **Part C** with **v24** (the build was started from this branch): SLO1–SLO8 as in LOCAL_PROMPT.md. At SLO4, Petrina 2
  should show *98/5, KUŽELJ, 283 m², "Kostel, Petrina 2", building 221 (1910), land use 54.8 % / 45.2 %, planned use,
  boniteta 55.
