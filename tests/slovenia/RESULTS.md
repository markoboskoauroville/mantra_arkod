# Slovenia (v23): what the local session saw

Measured from Marko's phone (Termux, proot Ubuntu, the phone's own internet), 11.10.2026.
Every raw answer is in `gurs/`, untouched. Requests used `curl -sS -A "ARKOD Layer"`. Status lines are
`http_code size content_type`.

    W=https://ipi.eprostor.gov.si/wms-si-gurs-kn/wms
    F=https://ipi.eprostor.gov.si/wfs-si-gurs-kn/wfs
    Q=$F?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&OUTPUTFORMAT=application/json&SRSNAME=urn:ogc:def:crs:EPSG::4326

## Round 1, part A: GURS's real answers

### G1 wms_caps.xml: PASS
`$W?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities` → `200 68738 text/xml`

- `SI.GURS.KN:PARCELE` exists under exactly that name. queryable=1. Styles: `nep_kn_parcele`, `nep_kn_parcele_lbl`.
- Layers with PARC in the name: `GRADBENE_PARCELE`, `OSNOVNI_PARCELE`, `OSNOVNI_PARCELE_CENTROID`, `PARCELE`,
  `PARCELE_JAVNI_LASTNIKI`, `STAVBE_PARCELE` (all with the `SI.GURS.KN:` prefix, all queryable=1).

### G2 wfs_caps.xml: PASS
`$F?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetCapabilities` → `200 134687 application/xml`

- Parcels: `SI.GURS.KN:PARCELE`. Municipalities: `SI.GURS.KN:KATASTRSKE_OBCINE`. Default CRS of every type is EPSG:3794.
- Addresses: `SI.GURS.KN:NASLOVI_HS` (full address with settlement, street and house number, plus KO_ID and
  building number), `SI.GURS.KN:HISNE_STEVILKE` (house number with its point, IDs only), `SI.GURS.KN:NASLOVI_STAVBE`.
  There is no `RPE` type.
- Search types: `SI.GURS.KN:ISCI_PARCELE`, `SI.GURS.KN:ISCI_STAVBE`.
- Ownership-related WFS types: `LASTNISTVO_PRAVNIH_OSEB`, `OSEBE_PRAVNE_OS`, `IMETNIKI_LASTNISTVA_H`,
  `PRAVICE_LASTNISTVA_H`, `UPRAVLJAVCI_X_PARCELE`. **I did not query them.** `PARCELE_JAVNI_LASTNIKI` is a WMS
  layer only (see P4).
- GetFeature output formats include `application/json` (also `json` and `application/geo+json`).

### G3 parcele_schema.xml, ko_schema.xml: PASS
`$F?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType&TYPENAMES=SI.GURS.KN:PARCELE` → `200 6193`
and `...TYPENAMES=SI.GURS.KN:KATASTRSKE_OBCINE` → `200 1590`

- PARCELE: FEATUREID, EID, EID_PARCELA, PARCELA_ID, **KO_ID** (long), **NAZIV** (k.o. as "1725 AJDOVŠČINA",
  number first), **ST_PARCELE** (string), **POVRSINA** (long), DOLOCITEV_POVRSINE, METODE_DOLOCITVE_POVRSINE_*,
  UPRAVNI_STATUS*, E_CEN, N_CEN, GEOM, DATUM_SYS, BONITETA, ZNESEK_KD, SKUPNI_DEL_ETAZNA_LASTNINA*,
  GRAD_PARC, OMEJITEV. Every coded field comes in four languages: `_NAZIV_SL/_EN/_IT/_HU` plus `_OPIS_SL/_EN/_IT/_HU`.
- KATASTRSKE_OBCINE: FEATUREID, EID_KATASTRSKA_OBCINA, KO_ID, **NAZIV** (name only, "AJDOVŠČINA"), SIFKO, GEOM, DATUM_SYS.
- The app's first keys match: KO_ID, ST_PARCELE, EID_PARCELA, POVRSINA, NAZIV.

### G4 tile_ljubljana.png: PASS (with a note)
The app's exact tile URL (z16 35408/23301) → `200 144726 image/png`. `file`: PNG image data, 512 x 512, 8-bit RGBA.

- Opaque pixels (alpha ≥ 60): **51046 / 262144 = 19.5 %**. Counted with a small zlib PNG decoder, because
  PIL is not installed here.
- **Lines only, nothing filled.** Across rows 128, 256 and 384 the opaque pixels fall into 30, 27 and 22 short runs.
  I looked at the image: thin parcel outlines with no labels.
- Colour: pure green `#00FF00`, alpha 255 (Croatia is not green). `linesOnly()` will not trigger (19.5 % < 50 %),
  so the picture reaches the restyle unchanged. If the restyle does not recolour, Slovenia will be bright green.
  SLO1 will show which.

### G5 tap_ljubljana.json: PASS
`$Q&TYPENAMES=SI.GURS.KN:PARCELE&COUNT=10&BBOX=46.05045,14.50355,46.05055,14.50365,urn:ogc:def:crs:EPSG::4326`
→ `200 3583 application/json;charset=UTF-8`

- One feature: KO_ID 1725, NAZIV "1725 AJDOVŠČINA", ST_PARCELE "3200/1", POVRSINA 10302, EID_PARCELA
  "100100000748099824", UPRAVNI_STATUSI_NAZIV_SL "ni urejena", GRAD_PARC "Ne", OMEJITEV "Ne", BONITETA null.
- **Coordinates come longitude first** (`[14.50401357, 46.04983783]`), even though SRSNAME is the urn form of EPSG:4326.
  The app's `latLon()` reads them correctly, because it tells latitude from longitude by range.

### G6 ko_ljubljana.json: PASS
Same BBOX, `TYPENAMES=SI.GURS.KN:KATASTRSKE_OBCINE` → `200 17014`.
KO_ID 1725, **NAZIV "AJDOVŠČINA"** holds the name, SIFKO 1725. `ko_by_id.json` (`CQL_FILTER=KO_ID=1725`) returns the same feature.

### G7 by_reference.json: PASS
`$Q&TYPENAMES=SI.GURS.KN:PARCELE&COUNT=2&CQL_FILTER=KO_ID%3D1725%20AND%20ST_PARCELE%3D%273200%2F1%27` → `200 3583`, 1 feature (3200/1).
The property names are as the app expects. The app's OR form also works (`by_reference_or.json`).
The building parcel `*98/5` found by reference too: `petrina2_by_reference.json`, `KO_ID=1613 AND ST_PARCELE='*98/5'` → 1.

### G8 suggest.json: PASS (LIKE works)
`CQL_FILTER=KO_ID=1725 AND ST_PARCELE LIKE '12%'` → `200 147`, 0 features. **That 0 is correct.** K.o. 1725 has 1543 parcels
(`suggest_12_hits.json`, RESULTTYPE=hits), and `LIKE '1%'` gives only `142/16` (`suggest_1.json`).
`LIKE '3200%'` → 3200/1, 3200/2, 3200/3 (`suggest_3200.json`).

**But see FINDING S1:** building parcels start with `*`, and a typed prefix does not reach them.

No request needed a fallback (WFS 1.1.0, outputFormat=json, and so on).

## Round 1, part B: Petrina 2

### P1 petrina2_address.json: PASS
GURS's own address register (URL in `gurs/petrina2_address.url`):
`$Q&TYPENAMES=SI.GURS.KN:NASLOVI_HS&COUNT=5&CQL_FILTER=NASELJE_NAZIV='Petrina' AND HS_STEVILKA=2` → `200 2500`, 1 feature.
Občina Kostel, naselje Petrina, no street, house number 2, KO_ID 1613, building ST_STAVBE 221.

**Petrina 2 = 45.46479668 N, 14.8531182 E** (D96/TM E 488514, N 36107). Nominatim was not needed.

### P2 petrina2_parcel.json: PASS
The G5 tap query, BBOX ±0.00005° round that point → `200 12221`. **Five parcels** touch the box: 2781/4, 2757/8, *98/5,
2757/7, *98/4. A point-in-polygon test picks one:

**`*98/5`, k.o. 1613 KUŽELJ, 283 m²** (EID_PARCELA 100100000271338102, PARCELA_ID 27133810).
UPRAVNI_STATUSI_NAZIV_SL "ni urejena", GRAD_PARC "Ne", OMEJITEV "Ne", BONITETA null, ZNESEK_KD null,
METODE_DOLOCITVE_POVRSINE_NAZIV_SL "ni podatka", DATUM_SYS 2022-05-29. All attributes are in the file.
WMS GetFeatureInfo at the same point also gives `*98/5` (`petrina2_parcel_gfi.json`).

### P3 petrina2_ko.json: PASS
KO_ID 1613, NAZIV **"KUŽELJ"**, SIFKO 1613. Petrina lies in k.o. Kuželj, not in a k.o. named Kostel or Petrina.

### P4 other public sheets: what I could read without a login
| What | Request | Result |
|---|---|---|
| Building at the point | `$Q&TYPENAMES=SI.GURS.KN:STAVBE` + BBOX | `petrina2_stavbe.json`: building 221, built 1910, roof 1972, 2 floors, 1 flat, electricity, water and sewer yes, freestanding, with its outline |
| Parts of the building | `TYPENAMES=SI.GURS.KN:DELI_STAVB&CQL_FILTER=EID_STAVBA='100200000250931504'` | `petrina2_deli_stavb.json`: 1 part, "stanovanje v enostanovanjski stavbi", 118.8 m², address "Kostel, Petrina 2". **BBOX fails here** (400 "Illegal property name": the type has no geometry) |
| Actual land use (dejanska raba) | `TYPENAMES=SI.GURS.KN:PARCELE_X_DEJANSKE_RABE&CQL_FILTER=EID_PARCELA='100100000271338102'` | `petrina2_dejanske_rabe.json`: 54.8 % "poseljena zemljišča, tloris stavbe", 45.2 % "poseljena zemljišča". BBOX → 400 for the same reason |
| Planned use (namenska raba) | `PARCELE_X_NAMENSKE_RABE`, same filter | 100 % "površine podeželskega naselja" |
| Soil rating (boniteta) | `PARCELE_X_BONITETE`, same filter | boniteta 55 over 277.97 m² |
| Public owners | WMS GetFeatureInfo `PARCELE_JAVNI_LASTNIKI` (WFS → "Feature type unknown") | empty: no public body owns this parcel |
| GURS public view | https://ipi.eprostor.gov.si/jv/ (https://eprostor.gov.si/javni-vpogled/ redirects there) | 200, "Javni vpogled v podatke o nepremičninah". It is a JavaScript app, so curl cannot read its data |
| GURS metadata catalogue | https://eprostor.gov.si/imps/ | 200, a GeoNetwork catalogue with no parcel data |
| Land register e-ZK | evlog.sodisce.si, evlog.zk.gov.si, www.zk.gov.si | **DNS does not resolve** from this phone. www.sodisce.si/zemljiska_knjiga/ → 404. **I could not verify the address in `OWNERS_NOTE`** |

I did not try to get private owners' names.

### P5 Croatia at Petrina 2: PASS
URL in `gurs/petrina2_croatia.url` → `200 23 text/plain`: **"no features were found"**, as expected.
Across the Kolpa (BBOX 45.4612,14.8528,45.4616,14.8532) the Croatian WMS answers: parcel **75/6, MATICNI_BROJ_KO 304506**
(`croatia_across_kolpa.txt`). Both states have parcels at the border, so SLO3 can test both banks.

## Findings for the cloud session (no code under app/ changed)

**S1 Suggestions miss building parcels (`*` numbers).** Slovenia writes building parcels as `*98/5`. When Marko types
"98/5", `suggestUrl` sends `ST_PARCELE LIKE '98/5%'` and misses `*98/5`. Measured at Petrina: `LIKE '98%'` gives 15
plain parcels (98, 980, 981/1…) and none of the `*98/…` ones; `LIKE '*98%'` gives `*98/4, *98/5, *98/6`. Suggested fix:

```diff
-        wfs(PARCELS) + "&COUNT=$limit" + cql("KO_ID=${koId(koId).filter { it.isDigit() }} AND ST_PARCELE LIKE ${quoted(prefix.trim() + "%")}")
+        wfs(PARCELS) + "&COUNT=$limit" + cql("KO_ID=${koId(koId).filter { it.isDigit() }} AND " +
+            "(ST_PARCELE LIKE ${quoted(prefix.trim().removePrefix("*") + "%")} OR ST_PARCELE LIKE ${quoted("*" + prefix.trim().removePrefix("*") + "%")})")
```
The same applies to Search by number: a typed "98/5" should try `'98/5'` and then `'*98/5'`.

**S2 `*` in the reference.** `SI1613-*98/5` will reach FILE and TXT names. Android shared storage (FAT/sdcardfs) refuses
`*` in file names. Check where a reference becomes a file name, and replace `*` (for example with `z`) there only.
SLO2 and SLO4 will test FILE.

**S3 The sheet will be noisy.** `parseRecord` turns every attribute into a row. For one parcel that means about 45 rows,
including the EN/IT/HU/OPIS copies, the IDs (FEATUREID, EID, EID_PARCELA, PARCELA_ID), E_CEN/N_CEN and nulls. Suggested filter:
```diff
-    private val SKIP = Regex("(?i)^(geom.*|the_geom|shape.*|bbox)$")
+    private val SKIP = Regex("(?i)^(geom.*|the_geom|shape.*|bbox|featureid|eid.*|parcela_id|.*_id|.*_sifra|.*_(en|it|hu)|.*_opis_sl|e_cen|n_cen)$")
```
GURS's NAZIV on a parcel is "1613 KUŽELJ" (number first). Where it is used as the municipality name, strip the leading
digits, or use KATASTRSKE_OBCINE.NAZIV ("KUŽELJ"), which the app already asks for.

**S4 What else the sheet could show (all open, no key).** These can be read by `EID_PARCELA='<id>'` with no BBOX:
actual land use (`PARCELE_X_DEJANSKE_RABE`, DEJANSKE_RABE_NAZIV + DELEZ %), planned use (`PARCELE_X_NAMENSKE_RABE`) and
soil rating (`PARCELE_X_BONITETE`). Buildings on the parcel come from `STAVBE` by BBOX (year built, floors, flats) and
building parts from `DELI_STAVB` by `EID_STAVBA`. Actual land use is the closest Slovenian match for Croatia's
"uporaba" rows.

**S5 `OWNERS_NOTE`.** The claim that GURS publishes no owners holds for private people, but GURS shows public owners
(`PARCELE_JAVNI_LASTNIKI`, WMS GetFeatureInfo) and has WFS types for legal-person ownership (`LASTNISTVO_PRAVNIH_OSEB`).
I have not queried that type yet. The e-ZK address could not be reached from here (DNS), so please check the URL
shown to Marko.

**S6 Tile colour.** GURS draws `#00FF00` lines with alpha 255. Make sure the restyle recolours them to the Croatian look.

ROUND 1A/1B DONE 11.10.2026 12:40 CEST. Part C (the app on the phone) follows.
