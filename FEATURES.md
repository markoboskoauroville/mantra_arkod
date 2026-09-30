# FEATURES: ARKOD Layer, Android and web, in step

Marko, 30.9.2026: *"those 2 apps, android app and web app, should be in sync with features."*

This file is the one list both apps follow. **A feature is done only when it is in both**, or when the
row says why one of them cannot have it. A session that adds or changes a feature in one app writes
the row here in the same commit and opens the work in the other: in this file, and in the other
repository's `momentaryupdates.md`.

- Android: `markoboskoauroville/mantra_arkod` (this repository), Kotlin, Compose, VTM. APK by GitHub Actions.
- Web: `markoboskoauroville/arkod_web`, a PWA (Safari: Share, Add to Home Screen; Android Chrome:
  Install), Leaflet, Cloudflare Pages. For the iPhone, which cannot install an app from a page.

States: **yes** · **no** (not yet) · **n/a** (cannot, and why).

| # | Feature | Android | Web |
|---|---|---|---|
| 1 | Name "ARKOD Layer"; icon: the Show/hide ARKOD layer key's glyph (a square split into parcels, amber on #0B0D10) | yes (v5) | no |
| 2 | Three map keys: OFF (offline), GOO (Google, the user's own key, road map by default), OSM (default on first run) | yes (v10) | no. OFF on the web = map tiles already seen, kept by the service worker; the 176 MB Croatia file is Android only |
| 3 | The ARKOD layer (the state's WMS, cp:CP.CadastralParcel) over every map, from zoom 14 | yes | no |
| 4 | The Show/hide ARKOD layer key: a tap hides/shows the layer and always brings the parcels back, even from "Only Moje čestice"; a long press opens Parcel view | yes (v5) | no |
| 5 | Parcel view: ARKOD layer, Only Moje čestice, Parcel search, Lines, Parcel caches, Moje čestice, Imenik | yes (v5) | no |
| 6 | Lines: colour (auto + six), transparency 20/35/50/62/80/100 %, thickness Fine/Normal/Bold, made on the device from the state's picture (Fine: core kept, edges at 35 %; Bold: 3x3 max) | yes (v5) | no |
| 7 | A tap selects a parcel (cyan outline), a second tap opens its sheet; a tap outlines even with the layer hidden | yes (v5) | no |
| 8 | The sheet: tabs uporaba / posjedovni / vlasnički, a filter, CPY, TXT, FILE | yes (v10) | no |
| 9 | Owner sheet (vlasnički list) from the land registry; where the cadastre gives no link, the land-book number typed once and kept | yes | no |
| 10 | The folio's parcel numbers (list A) are links: a tap goes to that parcel and outlines it | yes (v10) | no |
| 11 | FILE: one parcel sent as a .arkod.json file | yes (v10) | no |
| 12 | Top line: the map middle's coordinates, zoom, map name; where-am-I snaps and shows the accuracy | yes (v3) | no |
| 13 | Google's search field on every map (with a key), answers as you type; the point on whichever map is up | yes (v3) | no |
| 14 | Parcel field: a number (answers as you type, OSS), "pl 1984", a name (Imenik and the caches) | yes (v3) | no |
| 15 | Search history in every search box, offered when the box is touched | yes (v8) | no |
| 16 | Imenik: every holder and owner of every sheet opened on the device, searchable by name | yes (v3) | no |
| 17 | Moje čestice: keep a parcel from its sheet, any colour, line solid/dashed/dotted, a name | yes | no |
| 18 | Moje čestice in groups; a group's colour, line, thickness, shown/hidden | yes (v8) | no |
| 19 | Import a .arkod.json (picker; "open with" on Android); export a group through the share sheet | yes (v8) | no |
| 20 | Parcel caches: "Cache this view", named, verbose status (stage, done/total, rate, time left, failures), the WFS waited out (8 tries) | yes (v6) | no |
| 21 | Inside a cache the state's lines are taken out and the cache's own drawn (colour, dashes, weight, numbers) | yes (v5) | no |
| 22 | Offline search over the caches: name, number, "pl N", address, land use; a result selects the parcel and opens its sheet from the device | yes (v5) | no |
| 23 | The ARKOD layer kept on the device around the user and where the map rests | yes | no (the service worker keeps what was seen) |
| 24 | Settings in English; the cadastre's words in Croatian (čestica, posjedovni list, vlasnički list, k.o.) | yes (v3) | no |
| 25 | Track recording to GPX, tracks list | yes | n/a (a browser cannot record in the background) |
| 26 | Compass | n/a (removed at his word, v3) | n/a |

## The file format (.arkod.json), shared

    { "kind": "arkod-moje-cestice", "version": 1, "app": "ARKOD Layer", "name": "Obitelj Boško",
      "colour": "ffe040fb", "style": "SOLID|DASHED|DOTTED", "weight": "FINE|NORMAL|BOLD",
      "parcels": [ { "ref": "334723-2449/2", "number": "2449/2", "id": 6434350, "name": "optional",
                     "rings": [[lat, lon, lat, lon]] } ] }

Latitude first, six decimals. The group takes the file's name (without .arkod.json), else "name".
MarkFile.kt is the reference implementation; its Test 1 cases are the reference behaviour.
