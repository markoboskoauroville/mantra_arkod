# ARKOD_cache: the remote cache

**Marko's private data repository for ARKOD Layer** (Android `mantra_arkod`, web `arkod_web`). The state's
four services fail in spells (the WFS's ORA-01000, OSS refusing addresses, the mornings until 9), so what
they answer is swept here, place by place, at Marko's word, and the apps read it when the state does not
answer, on a new install and for a new user too. Marko, 1.10.2026: *"So we can call this access point
remote cache."*

Nothing here is invented or edited: every file is the state's own answer, kept byte for byte, with the
question it answered computable from its path.

## The four services

| Short | What it is | What is kept |
|---|---|---|
| **WMS** | the ARKOD layer, the state's map service (DGU, INSPIRE `cp:CP.CadastralParcel`) | the app's own 512 px tiles, z14 to z18 |
| **WFS** | parcel outlines, the state's feature service | every outline, and one file of all of them |
| **KAT** | the cadastre, OSS: posjedovni list and search | every posjedovni list, every parcel's record, the number index |
| **ZK** | the land registry, OSS: vlasnički list (z.k. uložak) | every folio a record links to, current and with history |

## Layout

    ko/<reg>-<NAME>/                         one cadastral municipality, e.g. ko/334723-KUKLJICA/
      kat/possession-sheet/<n>.json          POST cad/search-parcels {cadMunicipalityId, possessionSheetNumber: n}
      kat/parcel-info/<parcelId>.json        GET  cad/parcel-info?parcelId=<parcelId>
      kat/index.json                         { "1358/3": 6436001, ... }  number → parcelId
      zk/lr-unit/<mainBookId>-<unit>.json    GET  lr/lr-unit?lrUnitNumber=<unit>&mainBookId=<mainBookId>&historicalOverview=false
      zk/history/<lrUnitId>.json             GET  lr-units/for-ldb-extract?lrUnitId=<lrUnitId>&historical=1
      wms/<z>/<x>/<y>.png                    GET  WMS GetMap, EPSG:3857, the slippy tile z/x/y, 512 px, dpi:180
      wms/zoning-grid.json                   the k.o. under points 0.002° apart (GetFeatureInfo, CadastralZoning)
      wfs/box/<lat>_<lon>.json               GET  WFS GetFeature over a 0.01° box, all pages
      wfs/outlines.geojson                   every outline of the k.o., by nationalCadastralReference
    MANIFEST.json                            what was swept, when, and how many of each

## Sweeping a place

    python3 scripts/sweep.py 334723 KUKLJICA 1354

The three arguments are the k.o.'s register number, its name, and OSS's internal municipality id (the
zoning layer's `ID`). It resumes: a file already kept is never asked again. Stages can be named
(`sheets records folios zoning tiles outlines`). It asks as the apps ask (their User-Agent, no Origin),
four at a time.

## Swept

See `MANIFEST.json`.
