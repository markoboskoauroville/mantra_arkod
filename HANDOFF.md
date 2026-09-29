# Mantra ARKOD — the finished state

    version      1
    package      com.mantra.arkod
    repository   markoboskoauroville/mantra_arkod (to be created: TESTING.md step 0);
                 until then the branch claude/gifted-curie-nbt328 of MANTRA_TRAIL
    artefact     1-mantra-arkod-v1.apk, release tag v1
    built by     GitHub Actions only (android-app.md §1a). Never on a desk.

The reasons live in [`DEVELOPMENT.md`](DEVELOPMENT.md); what was and was not proved in
[`DELIVERY_RECORD.md`](DELIVERY_RECORD.md); how it is tested, by whom, in [`TESTING.md`](TESTING.md).

## WHAT IT IS

Mantra Trail with the trail taken out and the cadastre put in the middle. Every parcel in Croatia
on one of three maps; a tap selects, a second tap opens the sheet with its three tabs (land use,
posjedovni list, vlasnički list); the parcels he keeps are Moje čestice, drawn dashed in his colour.
Everything the person reads is Croatian.

## THE SCREEN

    top        one line: coordinates, accuracy, speed, height, zoom, the map's name
    middle     the map, the crosshair (it takes no touch), and, when a map cannot draw yet, the
               panel that says what it needs: the Croatia download, or the way to a Google key
    bottom     the network / prefetch line, the note line, the walk's totals while recording,
               and nine keys: − · where · record · OFF · GOO · OSM · ★ · ⚙ · +

## THE THREE MAPS

    OFF   mapsforge croatia.map, 176 MB, downloaded by the app (MapDownload, resumable)
    GOO   Google Map Tiles API with HIS key (keyring, pasted or from a file); satellite by default
    OSM   tile.openstreetmap.org, online only, the first-run map; User-Agent names the app

One engine draws all three (VTM). The Maps SDK and its build-time key are gone.

## THE CADASTRE

Always on. Tiles come from the state's WMS through `TileHttp` → `ParcelNet.tile(z, x, y, ink)`:
the recoloured copy off the phone if kept, else the state's picture (kept or fetched) recoloured
and kept (`ArkodCache`, `filesDir/arkod`, 400 MB ceiling, oldest touched first out).
`ArkodPrefetch` fetches ahead around where the map rests and around each kilometre he moves:
z14 over 3 km, z15 2.5 km, z16 1.6 km, z17 900 m, z18 500 m, nearest first, three at a time.
Records and folios are kept as they arrive and shown with their date when the state is out of reach.

## MOJE ČESTICE

`Parcels.Mark` (reference, number, colour, rings, id, style, name), kept in `Store.parcelMarks`.
Kept from the sheet's foot (`MineControls`: switch, ten swatches, hue bar, dashed / solid /
dotted). Drawn by `VtmCanvas.lineFor`: VTM's own stipple, dash in his colour, gap transparent.
Listed in `MyParcelsFace` (★, and the first settings group), with the parcel search.

## STATE

`Store`: layer, last lat/lon/zoom/bearing (written on pause and when the view leaves), the Google
view, the theme, the keyring, the marks, the folio links, prefetch on/off. First run: OSM, 44.45 N
16.40 E, z7.

## THE DANGEROUS PARTS

- **No permanent signing key yet.** Until `ARKOD_KEYSTORE` is a secret, each CI build is signed
  with a throwaway key and says so in its release notes; upgrading needs an uninstall.
- **Google's tiles are never kept**, and ArkodCache only ever sees the state's cadastre.
- **The Android half has never been compiled by the cloud** (no Android SDK reachable). The first
  CI run is its first compile; TESTING.md 0.3 sends a red log back.

## THE FILES

| File | What it holds |
|---|---|
| `Layers.kt` | the three maps, the Croatia download, the first-run view. No Android. |
| `Parcels.kt` | the cadastre: tiles, services, parsing, sheet rows (Croatian), Mark, line styles, hues, the prefetch tile list. No Android. |
| `ParcelNet.kt` | the cadastre over the wire, kept answers, tiles kept and recoloured. |
| `ArkodCache.kt` | the tiles and answers on the phone; `ArkodPrefetch`, the fetch ahead. |
| `MapDownload.kt` | the Croatia file: resumable, with live percent and speed. |
| `VtmCanvas.kt` | the one engine: three maps, the cadastre over them, my parcels dashed. |
| `Canvases.kt` | what every key asks of the map. |
| `Screens.kt` | the map screen, the middle panels, the sheet, Moje čestice, tracks. |
| `Settings.kt` | the settings, Croatian. |
| `GoogleTiles.kt`, `Keyring.kt`, `Keys.kt` | his Google key: shape, ring, session. |
| the rest | as in Mantra Trail: Geo, Track, Gpx, Trail, TrailService, Locator, Sensors, Look. |
