# Mantra ARKOD

**Katastarske čestice cijele Hrvatske na karti: dodir na česticu, pa njezin posjedovni i vlasnički
list. Moje čestice u boji koju sami odaberete. Tri karte, a katastar radi i bez signala tamo gdje
ste već bili.**

Every cadastral parcel in Croatia on a map. Tap one and its possession sheet (posjedovni list) and
owner sheet (vlasnički list) open. Your own parcels are kept in your colour, and the cadastre is
kept on the phone wherever you have been.

Mantra Productions · Marko Boško · derived on 29.9.2026 from
[Mantra Trail](https://github.com/markoboskoauroville/MANTRA_TRAIL) v94.

---

## Install

Every build is published as a release. Open this link on the phone and tap the `.apk`:

**https://github.com/markoboskoauroville/mantra_arkod/releases/latest**

The APK is built by GitHub Actions and never on a desk (MANTRA_MANIFEST `android-app.md` §1a).
Pushing to `main` runs the gates, Test 1 and the build, and publishes the release.

## The three maps

| Key | Map | Needs | With no signal |
|---|---|---|---|
| **OFF** | Offline map of Croatia (mapsforge, 176 MB) | one download, offered in the middle of the screen | everything |
| **GOO** | Google: satellite, map, terrain or hybrid | **your own** API key; the screen explains how to make one | nothing (Google forbids keeping its tiles) |
| **OSM** | OpenStreetMap | nothing: free, the map a fresh install opens on | nothing |

The **cadastre (ARKOD)** is drawn over every map, always. Every cadastre tile you see is kept on the
phone, and the tiles around the map and around you are fetched ahead in the background.

## The documents

| File | What it is for |
|---|---|
| `HANDOFF.md` | the finished state; a new chat picks the app up from this |
| `TESTING.md` | the test plan, and how the local Claude Code runs it and reports back |
| `TEST_RESULTS.md` | what the last local test run found (written by the local Claude Code) |
| `DEVELOPMENT.md` | every decision and why, in the past tense (Mantra Trail's history first) |
| `DELIVERY_RECORD.md` | what was measured, and what was NOT tested |
| `momentaryupdates.md` | Marko's requests, word for word, before any code |
