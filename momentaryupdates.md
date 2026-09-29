# Momentary updates: Marko's requests, word for word

Every new request lands here first. "Continue" means: read this file first.

## 29.9.2026, Mantra ARKOD out of Mantra Trail

> So we need to, out of this app which is called Mantra Trail, create Mantra Barcode app which will
> be focusing on barcode-only features, and it will be for barcode, and it will be in Croatian
> language. So the changes are, since this is barcode-only, uh, barcode layer is by default enabled.
> We don't need— and basically rest is The same. So we are removing barcode icon, translate to
> Croatian, and we can remove finding the paths. So for A, B, C, D, from A to B, I mean finding the
> roots kind of icon and code. And we need to have all 3 maps, 3 types, down in the action buttons
> available immediately, not as a toggle. And the feature when user clicks in the middle and gets in
> the full screen. That feature goes out. Clicking in the middle, it's like clicking anywhere else.
> It selects the R code parcel beneath in the same way original app does. So create new app, and the
> name is mantra_rcode in new repository. And then you need to work from that repository onward. And
> of course, because it's ArcCode, we need to optimize code for ArcCode. Mean when user loads the map
> automatically in the background, wherever he is, you need to cache ArcCode layer, all parts,
> traces, or vector drawings, and store it in the phone. So next time user comes to the same location
> It is, it is quickly drawn. And new feature is My Parcels. So user can select parcel and store it
> under My Parcel, and there is— that will be a special part in settings which will basically replace
> the part when we were saving the routes, because routes are now have been for trail purposes, but
> now My Parcels is the part where user is storing all its parcels, and then he can click on each of
> them and their owner sheet and this possession sheet are opening up there. And also they, they will
> have different color which user chooses on the, on the map itself. Or we can now just maybe make a
> different line style. This can be default. So line style can be line.line.line. And then any color
> user can choose. So this is a distinction between what the user of this app owns and other ones. So
> there should be somehow quick navigation to my parcels or my chest inside this app. So let's make
> it R-code-centric. To get all the data quickly with all these different 3 maps. And since this is
> only for Croatia, so we can also optimize downloading offline map to Croatia. So when user click on
> the, for example, for the first time in Croatian offline map, the user interface will just in the
> middle of the screen, because map is not available, offer the link download Offline map, 300
> megabytes or whatever the size is. And while it's downloading, in the middle of the screen it shows
> the speed and status, how much percentage is done. And one, once this map is cached, there is no
> more need for this part. And the same for Google, because user first time will open the Google
> Map, it will not be available because there is no API key. So on the screen there will be help text
> saying API key for Google Maps is not available. Please do these steps. Go on this, on, on that
> link, whatever it is. Create the project. Enable Map API for that project. So the whole workflow how
> to get your own key. And of course we will, we're going to add OpenStreetMap, which is online only.
> And that OpenStreetMap is actually only thing which works by default because it's free. And there
> should be the view the app lands immediately after first installation. Otherwise it remembers the
> states when it was left, and then on next run it just run into the same state

Asked, answered: the repository name is **manra_arkod** (typed), read as `mantra_arkod`. "Barcode",
"R code" and "ArcCode" are ARKOD, his name for the cadastre layer (DEVELOPMENT.md, 27.9.2026).

1. New repository `mantra_arkod`. **Status:** blocked. Neither the cloud session's GitHub
   connection (403) nor its permissions may create a repository. The app lives on the branch
   `claude/gifted-curie-nbt328` of MANTRA_TRAIL until the local Claude Code creates the repository
   and pushes it there (TESTING.md, step 0).
2. Cadastre always on, K key removed. **Status:** done in v1.
3. Croatian throughout. **Status:** done in v1.
4. Routes removed (points, BRouter, Google routes, heights). **Status:** done in v1.
5. Three maps as three keys: Offline, Google, OpenStreetMap. **Status:** done in v1.
6. No full screen; a tap in the middle selects the parcel. **Status:** done in v1.
7. The cadastre kept on the phone and fetched ahead in the background. **Status:** done in v1.
8. Moje čestice: kept from the sheet, first in the settings, one key from the map, sheets open
   from the list, any colour, line style (dashed by default). **Status:** done in v1.
9. Offline map is Croatia only, offered in the middle with percentage and speed. **Status:** done
   in v1 (176 MB, mapsforge).
10. Google: the way to one's own key in the middle of the screen. **Status:** done in v1.
11. OpenStreetMap, free, the first-run map; first run over Croatia; later runs where he left it.
    **Status:** done in v1.

## 29.9.2026, the cloud and the local Claude Code as one team

> Please in the repository right for cloud code local a guide how to test this app and then it will
> give you test results back. Or better yet, you write here in Claude code what I need to paste to
> Claude code so it can give you results. So we work as a team, cloud code in the cloud and cloud code
> local are both working together as one team, and you are asking me to send certain prompt to cloud
> code local and then he will write these results inside the repository so you will be able to see
> the test results. Also, if you haven't already read my mantra manifest and you can do tests here
> from intra manifest tests which can be done, then what you cannot do you need to ask my local cloud
> code. I'm just going to copy your prompt there and then it will write results you can read and
> based on the results you can do updates.

1. `TESTING.md`: the plan, the prompt, and where the results go (`TEST_RESULTS.md`). **Status:** done.
2. Tests the cloud can run (Test 1, verify.py) run here. **Status:** done: 194 cases, 222 checks.

## 29.9.2026, Mantra testing in the manifest

> Any new test created for certain applications which are useful, you need to add to Mantra manifest
> automatically and write in Mantra manifest that those tests need to be added automatically by
> future session of any app. If something is updated and modified, so we can have comprehensive Mantra
> tests. Maybe you can just point from Mantra manifest to Mantra tests.md and create different.md
> dedicated for testing. That's very important because we are avoiding bugs from build to build and
> that speeds up the workflow significantly. That's the very important step, so we must have high-end
> enterprise-grade testing. Mantra testing.md

1. MANTRA_MANIFEST `modules/mantra-testing.md`, pointed to from START_HERE and four-tests. **Status:**
   written on the manifest branch `claude/gifted-curie-nbt328`, to be merged by the local Claude Code.
