# LOCAL TASKS: what the cloud session could not do, for the Claude Code on Marko's Mac

Written 1.10.2026 by the cloud session (Claude Code on the web), at Marko's word: *"write a prompt for
Claude Code right on my computer, local one, to do everything you cannot do ... either guide me what even
Claude Code local cannot do, or to do what it can do."*

You are the local Claude Code on Marko's MacBook Pro (Apple M1 Pro, arm64; never Intel). The cloud
session runs in a container that cannot create repositories, cannot upload files to Cloudflare Pages
(its proxy replaces wrangler's upload token), has no hardware virtualisation (so no Android emulator),
and reaches Cloudflare in Ashburn, USA, not in Croatia. You can do all four. What neither of us can do
is listed at the end, as steps for Marko.

## 0. Before anything

1. Pull MANTRA_MANIFEST and read, in order: `START_HERE.md`, `README.md`, `modules/four-tests.md`,
   `modules/secrets.md`, `modules/the-machine.md`, `modules/android-app.md`, `modules/delivery-gate.md`,
   `modules/writing-styles.md` (§0a: a message Marko sends is written in his voice) and `MEMORY.md`.
2. Pull these and read each one's `momentaryupdates.md` (Marko's requests word for word, with status),
   `TAKEOVER.md`, `LESSONS.md` where they exist:
   `~/Developer/mantra_arkod`, `~/Developer/arkod_web`, `~/Developer/markoboskopossesions`.
   `mantra_arkod/FEATURES.md` is the one feature list both apps follow.
3. "QR", "R code", "Arcode", "ArcCode" and "Mantra Barcode" always mean ARKOD. "VMS" is WMS, "VFS" is WFS.

## Rules that do not bend

- Every new request of Marko's goes word for word into the right repository's `momentaryupdates.md`, and
  is pushed, before any code.
- No key or token in a repository, a commit, a log or the chat. Read keys from where they are kept, pipe
  them straight into the tool that needs them, never echo them. Show a secret only masked.
- Settings in English; everything from the cadastre stays in Croatian.
- The APK is built by GitHub Actions only, never on the Mac (android-app.md §1a). On the Mac you install
  the CI release and test it.
- Test before saying done, say what was not tested and why. Versions are whole numbers.
- Before every push, scan the staged diff for key shapes (`AIza`, `ghp_`, `github_pat_`, `gsk_`, `sk-ant-`, `AQ.`).
- When a step is done, write its status into `mantra_arkod/momentaryupdates.md` (the 1.10.2026 sections)
  and push. The cloud session reads it there.

## 1. Create the private repository ARKOD_cache (the remote cache)

The cloud session was refused: "sessions are bound to their configured repositories".

    gh repo create markoboskoauroville/ARKOD_cache --private \
       --description "ARKOD remote cache: the state's WMS, WFS, cadastre (KAT) and land registry (ZK) answers, kept for when the state is offline"

Empty: no README, no licence, no .gitignore (the cloud session pushes the first commit). Check:
`gh repo view markoboskoauroville/ARKOD_cache --json visibility` must say PRIVATE.

Then tell Marko, in one line, to tell the cloud session "ARKOD_cache exists". The cloud session holds the
whole k.o. Kukljica sweep (9,180 parcels, their posjedovni lists, records, folios with history, the ARKOD
layer's tiles, the outlines when the WFS answers) and pushes it.

**If the cloud session says its data is gone** (its container is reclaimed when idle), run the sweep here.
From Croatia it is also the better place to ask the state from:

    git clone https://github.com/markoboskoauroville/ARKOD_cache ~/Developer/ARKOD_cache
    mkdir -p ~/Developer/ARKOD_cache/scripts
    cp ~/Developer/mantra_arkod/scripts/remote-cache/sweep.py ~/Developer/ARKOD_cache/scripts/
    cp ~/Developer/mantra_arkod/scripts/remote-cache/README.md ~/Developer/ARKOD_cache/
    cd ~/Developer/ARKOD_cache
    caffeinate -i python3 scripts/sweep.py 334723 KUKLJICA 1354 | tee sweep.log

It resumes where it stopped. It takes one to two hours, mostly the state's own slowness. The WFS stage
waits out ORA-01000 for up to about seven hours; it can be stopped and run again later with
`python3 scripts/sweep.py 334723 KUKLJICA 1354 outlines`. Commit in parts (a commit per stage) and
push; the WMS tiles are many small PNGs, so push them last.

## 2. Deploy the family site markoboskopossesions (275 parcels)

The live site still serves the deploy of 30.9.2026 (296 parcels, before the Miroslav/Miroslava fix).
Its GitHub Actions deploy fails on every push because two secrets are missing. Set them from the file
`scripts/deploy.sh` already reads, without printing anything:

    F=~/Downloads/API/Cloudflare.api.txt
    head -1 "$F" | sed -E 's#.*dash.cloudflare.com/([0-9a-f]{32}).*#\1#' | tr -d '\n' \
      | gh secret set CLOUDFLARE_ACCOUNT_ID -R markoboskoauroville/markoboskopossesions
    grep -v '^\s*$' "$F" | tail -1 | tr -d '[:space:]' \
      | gh secret set CLOUDFLARE_API_TOKEN -R markoboskoauroville/markoboskopossesions
    gh secret list -R markoboskoauroville/markoboskopossesions
    gh workflow run deploy.yml -R markoboskoauroville/markoboskopossesions
    gh run watch -R markoboskoauroville/markoboskopossesions

If the file is not there or the run is red: `bash ~/Developer/markoboskopossesions/scripts/deploy.sh`
deploys straight from the Mac. Either way, check the result as the person who uses it: open
https://markoboskopossesions.pages.dev, log in as marko, and confirm the parcel list shows **275**
(`/data/parcels.json` behind the login has `parcels` of length 275, `generated` 2026-09-30). Write the
outcome as item 21 in markoboskopossesions' `momentaryupdates.md`.

## 3. Test the web app against the real state, from Croatia

From the cloud (Cloudflare in the USA) the state's OSS refused about two connections in three with an
Apache "403 Forbidden", all or nothing per connection; the sheets did not open. Nobody knows yet whether
a connection from Croatia meets it. This is the most useful test only the Mac can make:

    cd ~/Developer/arkod_web && git pull && npm ci && npm run real

It drives Chromium at 390 x 844 against https://arkod-layer.pages.dev (version 7 or later): the ARKOD
layer at 1358/3, a tap, the second tap, the posjedovni list 1225, the vlasnički list (z.k. uložak 250),
"2449/2 kukljica", the lights. Run it three times, a few minutes apart. Also:

    for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code} " "https://arkod-layer.pages.dev/api/oss/cad/parcel-info?parcelId=6436001&n=$i"; done; echo

Ten separate connections; count the 200s. Write both results (how many checks passed, how many 200s) into
arkod_web's `momentaryupdates.md` under "1.10.2026, tested against the real state", with the time of day.
If Croatia is refused too, say so plainly; the fix then is to ask the state from another address, and
that is Marko's decision, not yours.

## 4. The Android app on a Pixel 7 emulator: the monkey and the tests

Marko asked whether the Android app can be stress-tested on a Pixel 7 emulator with the monkey and every
test in MANTRA_MANIFEST and MANIFEST_INTRO. The cloud container cannot run an emulator; the M1 can
(arm64 system images run natively). The APK still comes from CI, never from a build on the Mac.

1. The tools (Android Studio's SDK, or the command-line tools in `~/Library/Android/sdk`):

        sdkmanager "platform-tools" "emulator" "system-images;android-33;google_apis;arm64-v8a"
        avdmanager create avd -n pixel7_api33 -d pixel_7 -k "system-images;android-33;google_apis;arm64-v8a"
        emulator -avd pixel7_api33 -no-snapshot -no-boot-anim &
        adb wait-for-device && adb shell getprop sys.boot_completed   # 1 when ready

   Android 13 (API 33) is what the Pixel 7 shipped with. If Marko means Android 7 instead, ask him once.
2. The release, as Marko gets it:

        gh release download -R markoboskoauroville/mantra_arkod --pattern '*.apk' --dir /tmp/arkod --clobber
        adb uninstall com.mantra.arkod; adb install /tmp/arkod/*.apk

3. The monkey, seeded, so a crash can be replayed with the same seed:

        adb logcat -c
        adb shell monkey -p com.mantra.arkod -s 1001 --throttle 80 --pct-syskeys 0 \
           --ignore-timeouts --monitor-native-crashes -v -v 20000 > /tmp/arkod/monkey-1001.txt 2>&1
        adb logcat -d -b crash > /tmp/arkod/crash-1001.txt

   Three seeds (1001, 2002, 3003). A run passes when the monkey reports all events injected and the crash
   buffer is empty. For any crash: the seed, the event number, the first 40 lines of the stack, a
   screenshot (`adb exec-out screencap -p > shot.png`).
4. Every test in `mantra_arkod/TESTING.md` (Test 2: driven as a person drives it; Test 3: offline, killed,
   rotated, refused; Test 4: the upgrade from the previous release), each by its ID, PASS, FAIL or SKIP,
   with what was seen and a screenshot for anything visual.
5. The test registry every app inherits, `modules/mantra-testing.md` in MANTRA_MANIFEST (on the branch
   `claude/gifted-curie-nbt328` until it is merged: MT-MAP, MT-UI, MT-KEY, MT-WEB). Run every row that
   applies to an Android map app. Then read MANIFEST_INTRO (`markoboskoauroville/MANIFEST_INTRO`) and run
   any test it names.
6. Results into `mantra_arkod/TEST_RESULTS.md`, screenshots under `tests/screens/emulator/`, push.
   A FAIL with no screenshot and no words is not a result. Do not fix the app on the Mac; the cloud
   session fixes it from the results.

Optional, and only with Marko's yes: the same monkey run on GitHub Actions on every build (Linux runners
have KVM; `reactivecircus/android-emulator-runner`, pinned by commit as the build workflow pins its
actions), so every APK is stress-tested before it is published.

## 5. Only with Marko's yes: the permanent signing key

Today every build is signed with a key made for that one run, so each new APK needs an uninstall first.
`TESTING.md` 0.2 says how to make the permanent one. **Ask Marko first, in one line.** On his yes:

    keytool -genkeypair -keystore ~/.arkod-signing/arkod.p12 -storetype PKCS12 -alias arkod \
       -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Mantra ARKOD"
    base64 -i ~/.arkod-signing/arkod.p12 | gh secret set ARKOD_KEYSTORE -R markoboskoauroville/mantra_arkod
    gh secret set ARKOD_KEYSTORE_PASSWORD -R markoboskoauroville/mantra_arkod   # typed, never echoed

The password is generated or chosen by Marko and kept where he keeps keys (MANTRA_MANIFEST keyring.md and
vault.md), never in a file in a repository. Back up `arkod.p12` the same way: a lost key means no update
can ever install over the last one (updatable.md). Then `gh workflow run build-apk.yml -R
markoboskoauroville/mantra_arkod`, watch it, and check the release no longer says TEST BUILD. Tell Marko
that the first permanent build needs one last uninstall.

## 6. Only with Marko's yes: the remote cache's reader

Marko wants both apps to read ARKOD_cache when the state does not answer. A private repository needs a
credential to read, and it cannot sit in the APK or the page. The cloud session's proposal, waiting for
his word: the web app's server (arkod-layer on Cloudflare Pages) reads the repository with a read-only
token and answers at `https://arkod-layer.pages.dev/api/remote/...`; the Android app asks the same
address and needs no secret. Anyone using the web app can then read the cache through it (it is the
state's public data; the repository itself stays private). **Ask Marko.** On his yes, guide him through
the token (step for Marko, below), then put it into Cloudflare without it passing through the chat:

    pbpaste | tr -d '[:space:]' | npx --yes wrangler@3 pages secret put ARKOD_CACHE_TOKEN --project-name arkod-layer
    pbcopy < /dev/null    # clear the clipboard

(with CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID exported from `~/Downloads/API/Cloudflare.api.txt` as
`scripts/deploy.sh` does). The cloud session writes the reader (`functions/api/remote/`) and both apps'
use of it; tell it the secret is in place.

## Steps only Marko can do (guide him; you cannot do them for him)

- **The read-only token for ARKOD_cache** (only if he said yes to §6): github.com → Settings → Developer
  settings → Personal access tokens → Fine-grained tokens → Generate new token. Name "ARKOD cache reader",
  expiration as he likes (a year), Repository access: Only select repositories → ARKOD_cache, Permissions:
  Contents → Read-only, nothing else. Generate, copy it, and run the `pbpaste` line above. He never pastes
  it into a chat.
- **Opening a sheet on his phone, in Croatia**: arkod-layer.pages.dev in Safari, find "1358/3 kukljica",
  open the sheet, look at the lights (WMS, WFS, KAT, ZK). If KAT or ZK is red with "the state refused this
  site's address", tell the cloud session.
- **Installing the APK on his own phone** after §5 (one uninstall first), and saying whether the launcher
  icon is still Mantra Trail's (it is; the ARKOD icon is on the open list).
- **Deciding the order of the open list**: the ARKOD launcher icon, the family's parcels as a .arkod.json
  for Moje čestice, the signing key, merging MANTRA_MANIFEST `claude/gifted-curie-nbt328` into main, the
  emulator run on every build; then the help in two parts (usage, and technology with the story of the
  offices).

## When you finish

One short report to Marko: each section done, not done, or waiting for him, with the numbers (the
parcels on the family site, the checks that passed from Croatia, the monkey's events and crashes). Every
status written into the repositories' `momentaryupdates.md` and pushed, so the cloud session can carry on.
