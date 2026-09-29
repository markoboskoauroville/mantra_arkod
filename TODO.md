# WHAT IS LEFT — Mantra ARKOD

## First

1. **The repository** `mantra_arkod`, created and pushed by the local Claude Code (TESTING.md 0.1).
2. **The first CI run** is the first compile of the Android half. Red → the log comes back.
3. **Tests 2 and 3** on the emulator (TESTING.md), results in TEST_RESULTS.md.
4. **The permanent signing key** `ARKOD_KEYSTORE`, made as android-app.md §3 asks.

## Then

- The dash lengths on the map, tuned from the F2 screenshots.
- The launcher icon is still Mantra Trail's; ARKOD wants its own (app-icon.md), and the version
  under it (the 29.9.2026 rule for every app).
- The place search inside Moje čestice needs Google's Places API on his key; without it the street
  search says so.
- A parcel tapped with no signal, in a place never seen, cannot be identified (the state's
  GetFeatureInfo is online). Keeping the parcel info per tile would close that.

## Open, not decided

- Whether the prefetch should wait for Wi-Fi. It is small (a few MB per place) and on by default;
  the switch is in settings.
