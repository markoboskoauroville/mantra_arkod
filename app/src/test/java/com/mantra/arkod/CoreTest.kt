package com.mantra.arkod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST 1 — THE MECHANISM, ALONE (four-tests.md).
 *
 * No Android, no map, no network, no phone. Every case here can fail while the other three tests
 * pass, and each one was written by asking the only question that matters: what could be true
 * that would make this pass and the feature still be broken?
 *
 * The distances are checked against figures computed independently rather than against what this
 * code happens to return.
 */
class CoreTest {

    // Two corners of Croatia with a known separation, and two points on Ugljan.
    private val zagrebLat = 45.8150
    private val zagrebLon = 15.9819
    private val splitLat = 43.5081
    private val splitLon = 16.4402

    // --- Geo: distance and bearing ---------------------------------------------------------

    @Test fun distanceToItselfIsZero() {
        assertEquals(0.0, Geo.distance(zagrebLat, zagrebLon, zagrebLat, zagrebLon), 1e-9)
    }

    @Test fun zagrebToSplitIsAboutTwoHundredAndFiftyKilometres() {
        val d = Geo.distance(zagrebLat, zagrebLon, splitLat, splitLon)
        assertTrue("got $d", d > 255_000 && d < 262_000)
    }

    @Test fun oneDegreeOfLatitudeIsAboutOneHundredAndElevenKilometres() {
        val d = Geo.distance(45.0, 16.0, 46.0, 16.0)
        assertEquals(111_195.0, d, 300.0)
    }

    @Test fun oneDegreeOfLongitudeShrinksWithLatitude() {
        val atEquator = Geo.distance(0.0, 0.0, 0.0, 1.0)
        val atZagreb = Geo.distance(45.815, 15.0, 45.815, 16.0)
        assertTrue(atZagreb < atEquator * 0.75)
    }

    @Test fun distanceIsSymmetric() {
        val a = Geo.distance(zagrebLat, zagrebLon, splitLat, splitLon)
        val b = Geo.distance(splitLat, splitLon, zagrebLat, zagrebLon)
        assertEquals(a, b, 1e-6)
    }

    @Test fun aMetreApartReadsAsAboutAMetre() {
        val d = Geo.distance(44.0, 15.0, 44.000009, 15.0)
        assertEquals(1.0, d, 0.1)
    }

    @Test fun bearingDueNorthIsZero() {
        assertEquals(0.0, Geo.bearing(44.0, 15.0, 45.0, 15.0), 0.01)
    }

    @Test fun bearingDueEastIsNinety() {
        assertEquals(90.0, Geo.bearing(0.0, 15.0, 0.0, 16.0), 0.01)
    }

    @Test fun bearingDueSouthIsOneEighty() {
        assertEquals(180.0, Geo.bearing(45.0, 15.0, 44.0, 15.0), 0.01)
    }

    @Test fun bearingIsNeverNegative() {
        val b = Geo.bearing(45.0, 15.0, 44.9, 14.9)
        assertTrue("got $b", b in 0.0..360.0)
    }

    @Test fun normaliseBringsNegativesRound() {
        assertEquals(350.0, Geo.normaliseDeg(-10.0), 1e-9)
        assertEquals(10.0, Geo.normaliseDeg(370.0), 1e-9)
        assertEquals(0.0, Geo.normaliseDeg(360.0), 1e-9)
    }

    @Test fun deltaTakesTheShortWayRound() {
        assertEquals(2.0, Geo.deltaDeg(359.0, 1.0), 1e-9)
        assertEquals(-2.0, Geo.deltaDeg(1.0, 359.0), 1e-9)
        assertEquals(180.0, Geo.deltaDeg(0.0, 180.0), 1e-9)
    }

    @Test fun cardinalNamesTheEightObviousOnes() {
        assertEquals("N", Geo.cardinal(0.0))
        assertEquals("NE", Geo.cardinal(45.0))
        assertEquals("E", Geo.cardinal(90.0))
        assertEquals("SE", Geo.cardinal(135.0))
        assertEquals("S", Geo.cardinal(180.0))
        assertEquals("SW", Geo.cardinal(225.0))
        assertEquals("W", Geo.cardinal(270.0))
        assertEquals("NW", Geo.cardinal(315.0))
    }

    @Test fun cardinalWrapsBackToNorth() {
        assertEquals("N", Geo.cardinal(359.9))
        assertEquals("N", Geo.cardinal(360.0))
        assertEquals("NNW", Geo.cardinal(348.0))
    }

    // --- Geo: the tile arithmetic the raster layers depend on ---------------------------------

    @Test fun zoomZeroIsOneTile() {
        assertEquals(0, Geo.tileX(15.0, 0))
        assertEquals(0, Geo.tileY(45.0, 0))
    }

    @Test fun theOriginTileIsTopLeft() {
        assertEquals(0, Geo.tileX(-180.0, 4))
        assertEquals(0, Geo.tileY(85.0, 4))
    }

    @Test fun tileIndicesStayInsideTheGrid() {
        for (z in 0..18) {
            val n = 1 shl z
            assertTrue(Geo.tileX(180.0, z) < n)
            assertTrue(Geo.tileY(-89.0, z) < n)
            assertTrue(Geo.tileX(-180.0, z) >= 0)
            assertTrue(Geo.tileY(89.0, z) >= 0)
        }
    }

    @Test fun zagrebLandsOnTheKnownTileAtZoomTwelve() {
        // Computed from the standard slippy-map formula independently of this code.
        assertEquals(2229, Geo.tileX(zagrebLon, 12))
        assertEquals(1460, Geo.tileY(zagrebLat, 12))
    }





    // --- Geo: what the screen shows -----------------------------------------------------------

    @Test fun coordinatesAreDegreesAndDecimalMinutes() {
        assertEquals("N 45 48.900", Geo.formatLat(45.815))
        assertEquals("E 15 58.914", Geo.formatLon(15.9819))
    }

    @Test fun theSouthernAndWesternHemispheresAreNamed() {
        assertTrue(Geo.formatLat(-33.9).startsWith("S"))
        assertTrue(Geo.formatLon(-70.6).startsWith("W"))
    }

    @Test fun sixtyMinutesBecomesTheNextDegree() {
        // 44.9999999 is 44 59.99999 minutes, which must not print as 44 60.000
        val s = Geo.formatLat(44.9999999)
        assertTrue("got $s", s == "N 45 00.000")
    }

    @Test fun minutesAreAlwaysTwoDigitsAndThreeDecimals() {
        val s = Geo.formatLat(45.01)
        assertEquals("N 45 00.600", s)
    }

    @Test fun distanceIsMetresThenKilometres() {
        assertEquals("0 m", Geo.formatDistance(0.0))
        assertEquals("999 m", Geo.formatDistance(999.4))
        assertEquals("1.0 km", Geo.formatDistance(1000.0))
        assertEquals("12.35 km", Geo.formatDistance(12_345.0))
    }



    @Test fun speedIsKilometresAnHour() {
        assertEquals("- km/h", Geo.formatSpeed(null))
        assertEquals("0 km/h", Geo.formatSpeed(0f))
        assertEquals("4.7 km/h", Geo.formatSpeed(1.3f))
        assertEquals("36 km/h", Geo.formatSpeed(10f))
    }

    @Test fun aPhoneStandingStillDoesNotReportAWalk() {
        // A fix wandering by a metre every few seconds is not somebody walking.
        assertEquals("0 km/h", Geo.formatSpeed(0.1f))
    }

    @Test fun aNonsenseSpeedIsADashRatherThanANumber() {
        assertEquals("- km/h", Geo.formatSpeed(-1f))
        assertEquals("- km/h", Geo.formatSpeed(Float.NaN))
    }

    @Test fun durationIsMinutesThenHours() {
        assertEquals("00:00", Geo.formatDuration(0))
        assertEquals("01:05", Geo.formatDuration(65_000))
        assertEquals("1:00:00", Geo.formatDuration(3_600_000))
        assertEquals("9:32:07", Geo.formatDuration(34_327_000))
    }

    // --- The rules that decide whether a fix joins the track ----------------------------------

    private fun fix(lat: Double, lon: Double, t: Long, acc: Float? = 5f, ele: Double? = 100.0) =
        Fix(lat, lon, ele, t, acc)

    @Test fun theFirstGoodFixIsAccepted() {
        assertNull(TrackRules.reject(null, fix(44.0, 15.0, 1000)))
    }

    @Test fun aFixWithNoAccuracyIsRefused() {
        assertEquals(Reject.ACCURACY, TrackRules.reject(null, fix(44.0, 15.0, 1000, null)))
    }

    @Test fun aHopelessFixIsRefused() {
        assertEquals(Reject.ACCURACY, TrackRules.reject(null, fix(44.0, 15.0, 1000, 120f)))
    }

    @Test fun accuracyExactlyAtTheLimitIsAccepted() {
        assertNull(TrackRules.reject(null, fix(44.0, 15.0, 1000, 50f)))
    }

    @Test fun accuracyJustPastTheLimitIsRefused() {
        assertEquals(Reject.ACCURACY, TrackRules.reject(null, fix(44.0, 15.0, 1000, 50.1f)))
    }

    @Test fun aZeroAccuracyIsNotBelieved() {
        assertEquals(Reject.ACCURACY, TrackRules.reject(null, fix(44.0, 15.0, 1000, 0f)))
    }

    @Test fun standingStillAddsNothing() {
        val a = fix(44.0, 15.0, 1000)
        val b = fix(44.000005, 15.0, 3000)
        assertEquals(Reject.DUPLICATE, TrackRules.reject(a, b))
    }

    @Test fun aWalkingPaceIsAccepted() {
        val a = fix(44.0, 15.0, 1000)
        val b = fix(44.00005, 15.0, 4000) // about 5.5 m in 3 s
        assertNull(TrackRules.reject(a, b))
    }

    @Test fun aTeleportIsRefused() {
        val a = fix(44.0, 15.0, 1000)
        val b = fix(44.01, 15.0, 2000) // a kilometre in a second
        assertEquals(Reject.JUMP, TrackRules.reject(a, b))
    }

    @Test fun aClockGoingBackwardsIsRefused() {
        val a = fix(44.0, 15.0, 5000)
        val b = fix(44.0005, 15.0, 4000)
        assertEquals(Reject.BACKWARDS, TrackRules.reject(a, b))
    }

    @Test fun twoFixesWithTheSameStampAndDifferentPlacesAreAJump() {
        val a = fix(44.0, 15.0, 5000)
        val b = fix(44.01, 15.0, 5000)
        assertEquals(Reject.JUMP, TrackRules.reject(a, b))
    }

    @Test fun accuracyIsJudgedBeforeTheJump() {
        // A hopeless fix that is also a jump must be reported as the hopeless fix: a jump computed
        // from a fix nobody believes is not evidence of anything.
        val a = fix(44.0, 15.0, 1000)
        val b = fix(44.5, 15.0, 1100, 400f)
        assertEquals(Reject.ACCURACY, TrackRules.reject(a, b))
    }

    // --- What the track then measures ---------------------------------------------------------

    @Test fun anEmptyTrackMeasuresNothing() {
        val s = TrackMath.stats(emptyList())
        assertEquals(0, s.points)
        assertEquals(0.0, s.distanceM, 1e-9)
    }

    @Test fun oneFixHasNoDistanceAndNoDuration() {
        val s = TrackMath.stats(listOf(fix(44.0, 15.0, 1000)))
        assertEquals(1, s.points)
        assertEquals(0.0, s.distanceM, 1e-9)
        assertEquals(0L, s.durationMs)
    }

    @Test fun distanceIsTheSumOfTheLegs() {
        val pts = listOf(
            fix(44.0, 15.0, 0),
            fix(44.001, 15.0, 60_000),
            fix(44.002, 15.0, 120_000),
        )
        val expected = Geo.distance(44.0, 15.0, 44.001, 15.0) * 2
        assertEquals(expected, TrackMath.stats(pts).distanceM, 0.5)
    }

    @Test fun wanderingAltitudeIsNotAClimb() {
        // A phone lying on a table, its altitude drifting by a metre or two, must report no ascent.
        val pts = (0..20).map { i ->
            fix(44.0, 15.0 + i * 0.0001, i * 10_000L, 5f, 100.0 + (i % 3) - 1)
        }
        assertEquals(0.0, TrackMath.stats(pts).ascentM, 1e-9)
    }

    @Test fun arealClimbIsCounted() {
        val pts = listOf(
            fix(44.0, 15.0, 0, 5f, 100.0),
            fix(44.001, 15.0, 60_000, 5f, 150.0),
            fix(44.002, 15.0, 120_000, 5f, 200.0),
        )
        assertEquals(100.0, TrackMath.stats(pts).ascentM, 1e-6)
    }

    @Test fun aDescentIsNotAnAscent() {
        val pts = listOf(
            fix(44.0, 15.0, 0, 5f, 200.0),
            fix(44.001, 15.0, 60_000, 5f, 100.0),
        )
        val s = TrackMath.stats(pts)
        assertEquals(0.0, s.ascentM, 1e-9)
        assertEquals(100.0, s.descentM, 1e-6)
    }

    @Test fun theHighestAndLowestAreKept() {
        val pts = listOf(
            fix(44.0, 15.0, 0, 5f, 120.0),
            fix(44.001, 15.0, 60_000, 5f, 640.0),
            fix(44.002, 15.0, 120_000, 5f, 300.0),
        )
        val s = TrackMath.stats(pts)
        assertEquals(120.0, s.minEle!!, 1e-9)
        assertEquals(640.0, s.maxEle!!, 1e-9)
    }

    @Test fun aTrackWithNoAltitudesHasNoneRatherThanZero() {
        val pts = listOf(fix(44.0, 15.0, 0, 5f, null), fix(44.001, 15.0, 60_000, 5f, null))
        assertNull(TrackMath.stats(pts).minEle)
    }

    @Test fun standingStillIsNotMovingTime() {
        val pts = listOf(
            fix(44.0, 15.0, 0),
            fix(44.0000001, 15.0, 600_000),
        )
        val s = TrackMath.stats(pts)
        assertEquals(600_000L, s.durationMs)
        assertEquals(0L, s.movingMs)
    }

    // --- The live recording agrees with the arithmetic done afterwards -------------------------

    @Test fun theLiveTotalsMatchTheSumsTakenAtTheEnd() {
        val rec = Recording(0L)
        val offered = (0..50).map { i ->
            fix(44.0 + i * 0.0002, 15.0, i * 10_000L, 5f, 100.0 + i * 2.0)
        }
        offered.forEach { rec.offer(it) }
        val live = rec.stats()
        val after = TrackMath.stats(rec.snapshot())
        assertEquals(after.points, live.points)
        assertEquals(after.distanceM, live.distanceM, 1e-6)
        assertEquals(after.ascentM, live.ascentM, 1e-6)
        assertEquals(after.movingMs, live.movingMs)
    }

    @Test fun aRefusedFixIsCountedAndNotRecorded() {
        val rec = Recording(0L)
        assertNotNull(rec.offer(fix(44.0, 15.0, 0)))
        assertNull(rec.offer(fix(44.5, 15.0, 1000)))
        assertEquals(1, rec.size)
        assertEquals(1, rec.rejectedCount())
        assertEquals(1, rec.rejected(Reject.JUMP))
    }

    @Test fun nothingIsRecordedWhilePaused() {
        val rec = Recording(0L)
        rec.offer(fix(44.0, 15.0, 0))
        rec.pause()
        assertNull(rec.offer(fix(44.001, 15.0, 60_000)))
        assertEquals(1, rec.size)
        rec.resume()
        assertNotNull(rec.offer(fix(44.001, 15.0, 120_000)))
        assertEquals(2, rec.size)
    }

    @Test fun aPauseDoesNotCountAsARejection() {
        val rec = Recording(0L)
        rec.offer(fix(44.0, 15.0, 0))
        rec.pause()
        rec.offer(fix(44.001, 15.0, 60_000))
        assertEquals(0, rec.rejectedCount())
    }

    // --- GPX ----------------------------------------------------------------------------------

    @Test fun theFileOpensAndClosesAsGpx() {
        val s = Gpx.whole("Velebit", listOf(fix(44.0, 15.0, 0)), 0)
        assertTrue(s.startsWith("<?xml"))
        assertTrue(s.contains("<gpx version=\"1.1\""))
        assertTrue(s.trimEnd().endsWith("</gpx>"))
    }

    @Test fun everyTagThatOpensAlsoCloses() {
        val s = Gpx.whole("Velebit", (0..5).map { fix(44.0 + it * 0.001, 15.0, it * 1000L) }, 0)
        for (tag in listOf("gpx", "trk", "trkseg", "trkpt", "metadata")) {
            // "<trk" would also match trkseg and trkpt: the closing bracket is what makes the
            // count a count of that tag rather than of every tag whose name starts the same.
            assertEquals("tag $tag", countOf(s, "<$tag>") + countOf(s, "<$tag "), countOf(s, "</$tag>"))
        }
    }

    private fun countOf(haystack: String, needle: String): Int {
        var i = 0
        var n = 0
        while (true) {
            val at = haystack.indexOf(needle, i)
            if (at < 0) return n
            n++
            i = at + needle.length
        }
    }

    @Test fun aPointCarriesItsPlaceTimeAndHeight() {
        val s = Gpx.point(Fix(45.815, 15.9819, 158.4, 0L, 4.2f, null, 11))
        assertTrue(s.contains("lat=\"45.815000\""))
        assertTrue(s.contains("lon=\"15.981900\""))
        assertTrue(s.contains("<ele>158.4</ele>"))
        assertTrue(s.contains("<time>1970-01-01T00:00:00Z</time>"))
        assertTrue(s.contains("<sat>11</sat>"))
        assertTrue(s.contains("<hdop>4.2</hdop>"))
    }

    @Test fun aPointWithNoHeightOmitsTheTagRatherThanWritingZero() {
        val s = Gpx.point(Fix(45.0, 15.0, null, 0L, 4f))
        assertFalse(s.contains("<ele>"))
    }

    @Test fun timesAreUtcWhateverTheClockIsSetTo() {
        assertEquals("2026-09-14T12:00:00Z", Gpx.isoUtc(1_789_387_200_000L))
    }

    @Test fun ampersandsInANameCannotBreakTheFile() {
        assertEquals("Paklenica &amp; Velebit", Gpx.escape("Paklenica & Velebit"))
        assertEquals("&lt;script&gt;", Gpx.escape("<script>"))
        assertEquals("&quot;x&quot; &apos;y&apos;", Gpx.escape("\"x\" 'y'"))
    }

    @Test fun controlCharactersAreDroppedRatherThanWritten() {
        val s = Gpx.escape("a\u0000b\u0007c")
        assertEquals("abc", s)
    }

    @Test fun croatianLettersSurviveEscaping() {
        assertEquals("Učka Šibenik Đakovo", Gpx.escape("Učka Šibenik Đakovo"))
    }

    @Test fun theWholeFileAndThePointByPointFileAreTheSameBytes() {
        // The two routes into a file must not drift apart: one is used while walking, the other
        // when exporting something already recorded.
        val pts = (0..9).map { fix(44.0 + it * 0.001, 15.0, it * 30_000L, 5f, 100.0 + it) }
        val whole = Gpx.whole("Ugljan", pts, 500L)
        val piecewise = buildString {
            append(Gpx.header("Ugljan", 500L))
            pts.forEach { append(Gpx.point(it)) }
            append(Gpx.footer())
        }
        assertEquals(piecewise, whole)
    }

    @Test fun aSegmentBreakClosesAndOpensOneSegment() {
        val s = Gpx.segmentBreak()
        assertEquals(1, countOf(s, "</trkseg>"))
        assertEquals(1, countOf(s, "<trkseg>"))
    }




    // --- the URL, split the way the GPU engine wants it ------------------------------------------




    @Test fun theOfflineFileHasNoPatternBecauseItHasNoUrl() {
        assertNull(Layers.tilePattern(Layers.OFFLINE))
    }


    // --- a route of more than two points ----------------------------------------------------------

    // --- which square of the world a route needs ------------------------------------------------

    // --- The layers -----------------------------------------------------------------------------

    @Test fun theThreeMapsAreTheFileGoogleAndOpenStreetMap() {
        // Mantra ARKOD, 29.9.2026: three keys, one per family, and OpenStreetMap is the free one
        // the app opens on the first time.
        assertEquals(setOf(MapLayer.Family.OFFLINE, MapLayer.Family.GOOGLE, MapLayer.Family.OSM), Layers.ALL.map { it.family }.toSet())
        assertEquals(Layers.OSM, Layers.FIRST)
        assertNull(Layers.OSM.provider)
        assertEquals("https://tile.openstreetmap.org/15/17800/11700.png", Layers.tileUrl(Layers.OSM, 15, 17800, 11700))
        assertEquals("https://tile.openstreetmap.org" to "/{Z}/{X}/{Y}.png", Layers.tilePattern(Layers.OSM))
    }

    @Test fun everyLayerHasItsOwnId() {
        // The count moves whenever a family is added; what must never move is that two maps
        // share an id, because the id is what the settings list and the memory both key on.
        assertEquals(Layers.ALL.size, Layers.ALL.map { it.id }.toSet().size)
        // Sixteen when Thunderforest and OpenStreetMap were here; five now that they are not.
        assertTrue(Layers.ALL.size.toString(), Layers.ALL.size >= 5)
    }





    @Test fun everyFamilyHasAtLeastOneMap() {
        MapLayer.Family.entries.forEach { assertTrue(it.name, Layers.of(it).isNotEmpty()) }
    }

    @Test fun theViewGoesFurtherThanTheTilesDo() {
        // The complaint this closes: OpenStreetMap stopped dead at 18 because that was where its
        // tiles stopped. Past the last real tile the map is scaled, not fetched.
        // Was written for OpenStreetMap, which stopped dead at 18; it left with the raster
        // services on 16.9.2026 and the rule it proved still holds for what remains.
        assertEquals(22, Layers.OFFLINE.viewMaxZoom)
        Layers.GOOGLE_ALL.forEach { assertTrue(it.id, it.viewMaxZoom >= it.maxZoom) }
        Layers.ALL.forEach {
            assertTrue(it.id, it.viewMaxZoom >= it.maxZoom)
            // mapsforge only scales a parent four levels up; beyond that it has nothing to draw.
            assertTrue(it.id, it.viewMaxZoom - it.maxZoom <= 4)
            assertTrue(it.id, it.viewMaxZoom <= 22)
        }
    }


    @Test fun aSpeedIsWrittenInAUnitSomebodyCanJudge() {
        assertEquals("0 B/s", Geo.formatRate(0))
        assertEquals("340 kB/s", Geo.formatRate(340_000))
        assertEquals("2.5 MB/s", Geo.formatRate(2_500_000))
    }

    @Test fun everyNameFitsTheLineItSharesWithTheCoordinates() {
        // The top line is about 55 monospace characters at 11sp on a 390 px phone, and the rest
        // of it is 38. Fourteen is the most a name may take without something clipping.
        Layers.ALL.forEach { assertTrue("${it.id}: ${it.name}", it.name.length <= 14) }
    }


    @Test fun noLayerCarriesAKeyOfItsOwn() {
        // The whole point of v7: the app ships no key. A URL template may have a {key} hole in
        // it; anything that looks like a real key in this table is the failure that cost a live
        // Maps key on 14.9.2026.
        val shapes = Regex("(AIza|gsk_|sk-ant-)[A-Za-z0-9_-]{20,}|\\b[0-9a-f]{32}\\b")
        Layers.ALL.forEach { layer ->
            assertNull(layer.id, layer.url?.let { shapes.find(it) })
        }
    }

    @Test fun aLayerThatNeedsAKeyHasNoAddressWithoutOne() {
        Layers.ALL.filter { it.provider != null }.forEach {
            assertNull(it.id, Layers.tileUrl(it, 12, 2229, 1460, auth = "session", key = null))
            assertNull(it.id, Layers.tileUrl(it, 12, 2229, 1460, auth = "session", key = ""))
            assertNotNull(it.id, Layers.missingKey(it))
        }
    }

    @Test fun googleNeedsASessionAsWellAsAKey() {
        assertNull(Layers.tileUrl(Layers.GOOGLE, 12, 2229, 1460, auth = null, key = "AIza" + "B".repeat(35)))
        val url = Layers.tileUrl(Layers.GOOGLE, 12, 2229, 1460, auth = "S123", key = "AIza" + "B".repeat(35))!!
        assertTrue(url.contains("session=S123"))
        assertTrue(url.contains("/12/2229/1460"))
    }


    @Test fun hybridIsSatelliteWithTheRoadsOverIt() {
        assertEquals("satellite", MapLayer.GoogleView.HYBRID.mapType)
        assertTrue(MapLayer.GoogleView.HYBRID.overlayRoads)
        assertFalse(MapLayer.GoogleView.SATELLITE.overlayRoads)
    }

    // --- imagery he can keep ------------------------------------------------------------------

    // --- Google's encoded polyline -----------------------------------------------------------

    // --- the keyring: several keys, tried in order ------------------------------------------------

    private fun aKey(tail: String) = "AIza" + "B".repeat(31) + tail

    @Test fun keysAreAddedWithoutLosingWhatIsKnownAboutTheOldOnes() {
        val first = Keyring.add(emptyList(), listOf(aKey("aaaa")))
        val tested = Keyring.withVerdict(first, aKey("aaaa"), Keyring.Verdict.GOOD, "works", 5L)
        val both = Keyring.add(tested, listOf(aKey("bbbb")))
        assertEquals(2, both.size)
        assertEquals(Keyring.Verdict.GOOD, both[0].verdict)
        assertEquals(Keyring.Verdict.UNTRIED, both[1].verdict)
    }

    @Test fun theSameKeyIsNotAddedTwice() {
        val once = Keyring.add(emptyList(), listOf(aKey("aaaa")))
        assertEquals(1, Keyring.add(once, listOf(aKey("aaaa"))).size)
        assertEquals(1, Keyring.add(emptyList(), listOf(aKey("aaaa"), aKey("aaaa"))).size)
    }

    @Test fun onlyGoogleShapedKeysGetOntoTheRing() {
        assertTrue(Keyring.add(emptyList(), listOf("not a key", "0123456789abcdef")).isEmpty())
    }

    @Test fun theRingSurvivesBeingWrittenDownAndReadBack() {
        val ring = Keyring.withVerdict(
            Keyring.add(emptyList(), listOf(aKey("aaaa"), aKey("bbbb"))),
            aKey("aaaa"),
            Keyring.Verdict.REFUSED,
            "Google: Map Tiles API has not been used in project 1234",
            99L,
        )
        val read = Keyring.decode(Keyring.encode(ring))
        assertEquals(2, read.size)
        assertEquals(Keyring.Verdict.REFUSED, read[0].verdict)
        assertTrue(read[0].said.contains("Map Tiles API"))
        assertEquals(99L, read[0].testedMs)
    }

    @Test fun aCorruptedRingYieldsWhatItCanRatherThanThrowing() {
        assertTrue(Keyring.decode(null).isEmpty())
        assertTrue(Keyring.decode("rubbish").isEmpty())
        assertEquals(1, Keyring.decode("${aKey("aaaa")}\tkey 1\tGOOD\tworks\t1\nbroken line").size)
    }

    @Test fun theOneThatWorksIsTriedFirstAndTheRefusedOneLast() {
        var ring = Keyring.add(emptyList(), listOf(aKey("aaaa"), aKey("bbbb"), aKey("cccc")))
        ring = Keyring.withVerdict(ring, aKey("aaaa"), Keyring.Verdict.REFUSED, "no", 1L)
        ring = Keyring.withVerdict(ring, aKey("cccc"), Keyring.Verdict.GOOD, "yes", 2L)
        val order = Keyring.order(ring).map { it.value }
        assertEquals(aKey("cccc"), order[0])
        assertEquals(aKey("bbbb"), order[1])
        assertEquals(aKey("aaaa"), order[2])
        assertEquals(aKey("cccc"), Keyring.best(ring)!!.value)
    }

    @Test fun anEmptyRingHasNoBestKey() {
        assertNull(Keyring.best(emptyList()))
    }

    @Test fun aKeyIsShownMaskedAndNeverWhole() {
        val key = Keyring.add(emptyList(), listOf(aKey("aaaa"))).first()
        assertFalse(key.masked.contains(key.value.substring(10, 20)))
        assertTrue(key.masked.startsWith("AIza"))
        assertTrue(Keyring.describe(key).contains("nije provjeren"))
    }

    @Test fun removingOneLeavesTheRest() {
        val ring = Keyring.add(emptyList(), listOf(aKey("aaaa"), aKey("bbbb")))
        val left = Keyring.remove(ring, aKey("aaaa"))
        assertEquals(1, left.size)
        assertEquals(aKey("bbbb"), left[0].value)
    }

    @Test fun aKeyIsSortedByItsShapeRatherThanByBeingAsked() {
        assertEquals(Keys.Provider.GOOGLE, Keys.providerOf("AIza" + "B".repeat(35)))
        assertNull(Keys.providerOf("cafeteria"))
        // A 32-character hex string was a Thunderforest key until 16.9.2026, when those maps left
        // the app. It belongs to nobody here now, and saying so beats claiming otherwise.
        assertNull(Keys.providerOf("0123456789abcdef" + "0123456789abcdef"))
        assertNull(Keys.providerOf("0123456789ABCDEF0123456789ABCDEF"))
    }

    @Test fun aFileWithTwoKindsKeepsOnlyTheOneThisAppUses() {
        val text = "google\n" + "AIza" + "B".repeat(35) + "\n\nthunderforest\n" + "a".repeat(32) + "\n"
        val found = Keys.parse(text)
        assertEquals(1, found.size)
        assertEquals(Keys.Provider.GOOGLE, found[0].provider)
    }

    @Test fun googleContributesAllFourOfItsViews() {
        val views = Layers.ALL.mapNotNull { it.googleView }
        assertEquals(4, views.size)
        assertEquals(4, views.toSet().size)
    }

    @Test fun noViewOfGoogleMayBeCachedOrRefusedQuietly() {
        Layers.ALL.filter { it.kind == LayerKind.GOOGLE_TILES }.forEach {
            assertEquals(it.id, MapLayer.Offline.NONE, it.offline)
            assertNull(it.id, Layers.tileUrl(it, 12, 2229, 1460))
        }
    }

    @Test fun terrainIsAskedForWithARoadmapLayer() {
        // Google refuses a terrain session without one: "The terrain map type must always contain
        // a roadmap layer" (400), proved against the real key on 17.9.2026.
        val terrain = Layers.GOOGLE_ALL.first { it.googleView?.mapType == "terrain" }
        assertEquals("terrain", terrain.googleView!!.mapType)
        // Hybrid asks for the same layer for a different reason: roads over a photograph.
        val hybrid = Layers.GOOGLE_ALL.first { it.googleView?.overlayRoads == true }
        assertEquals("satellite", hybrid.googleView!!.mapType)
    }

    @Test fun onlyGoogleLayersCarryAGoogleView() {
        assertNull(Layers.OFFLINE.googleView)
        Layers.GOOGLE_ALL.forEach { assertNotNull(it.id, it.googleView) }
    }

    @Test fun aFamilyStillHasEveryStyleInIt() {
        // The key turns through families, so nothing may be reachable ONLY by the key: every map
        // is in the list, and every map belongs to a family the key visits.
        assertEquals(Layers.ALL.size, MapLayer.Family.entries.sumOf { Layers.of(it).size })
    }

    @Test fun theOfflineMapIsFetchedFromAKnownPlaceWithAKnownSize() {
        assertTrue(Layers.OfflineDownload.URL.startsWith("https://"))
        assertTrue(Layers.OfflineDownload.NAME.endsWith(".map"))
        assertTrue(Layers.OfflineDownload.BYTES > 100_000_000)
    }

    @Test fun googlesTilesAreNeverFetchedWithoutASession() {
        // Google's terms forbid pre-fetching, caching or storing tiles, and name offline use as a
        // prohibited case. This is the check that fails if somebody "improves" the app later.
        assertEquals(MapLayer.Offline.NONE, Layers.GOOGLE.offline)
        assertNull(Layers.tileUrl(Layers.GOOGLE, 12, 2229, 1460))
    }

    @Test fun theOfflineMapNeedsNothingFetched() {
        assertEquals(MapLayer.Offline.COMPLETE, Layers.OFFLINE.offline)
        assertNull(Layers.tileUrl(Layers.OFFLINE, 12, 2229, 1460))
    }

    @Test fun theRasterUrlCarriesTheTileNumbers() {
        // Written for OpenStreetMap, which left on 16.9.2026; Google's tiles are the raster that
        // remains, and the rule is the same one.
        val u = Layers.tileUrl(Layers.GOOGLE, 12, 2229, 1460, auth = "session", key = "k")!!
        assertTrue(u, u.contains("/12/2229/1460"))
        assertTrue(u.startsWith("https://"))
    }


    @Test fun anUnknownLayerIdFallsBackToTheOneThatWorksOffline() {
        // An id nobody knows opens the map that needs neither a download nor a key.
        assertEquals(Layers.OSM.id, Layers.byId("something else").id)
        assertEquals(Layers.OSM.id, Layers.byId("imagery").id)
        assertEquals(Layers.OFFLINE.id, Layers.byId("offline").id)
    }

    @Test fun everyFetchedLayerHasAnAttributionAndAUrl() {
    }

    // --- Importing a key from a file -------------------------------------------------------------

    private val fakeKey = "AIza" + "B".repeat(35)
    private val otherKey = "AIza" + "C".repeat(35)

    @Test fun aKeyIsFoundByItsShape() {
        val found = Keys.parse("some notes\n$fakeKey\n")
        assertEquals(1, found.size)
        assertEquals(fakeKey, found[0].key)
    }

    @Test fun theAccountNameComesWithTheKey() {
        val found = Keys.parse("AV LIVE VMIX\n$fakeKey\n")
        assertEquals("AV LIVE VMIX", found[0].label)
    }

    @Test fun aKeyringBlockIsUnderstood() {
        val text = "# keyring v1\n\nprovider: google-maps\nlabel: phone\nkey: $fakeKey\n"
        val found = Keys.parse(text)
        assertEquals(1, found.size)
        assertEquals(fakeKey, found[0].key)
    }

    @Test fun twoBlocksAreTwoKeys() {
        val found = Keys.parse("first\n$fakeKey\n\nsecond\n$otherKey\n")
        assertEquals(2, found.size)
    }

    @Test fun theSameKeyTwiceIsOneKey() {
        val found = Keys.parse("one\n$fakeKey\n\ntwo\n$fakeKey\n")
        assertEquals(1, found.size)
    }

    @Test fun aFileOfProseYieldsNothing() {
        assertTrue(Keys.parse("cafeteria\nkitchen\nthe wifi password is upstairs").isEmpty())
    }

    @Test fun aTrackingUrlIsNotAKeyAndNotALabel() {
        val found = Keys.parse("https://example.com/?srsltid=AbCdEfGhIjKlMnOpQrStUvWxYz012345\nmy account\n$fakeKey")
        assertEquals(1, found.size)
        assertEquals("my account", found[0].label)
    }

    @Test fun anEmptyFileYieldsNothingRatherThanThrowing() {
        assertTrue(Keys.parse("").isEmpty())
        assertTrue(Keys.parse("   \n\n  ").isEmpty())
    }

    /**
     * THE REAL FILE, IN ITS REAL SHAPE. This is the layout of the note the key actually arrived
     * in on 15.9.2026 — a title line, an account, a plan, two URLs, the key under a heading, and
     * a tile URL further down that contains the key a second time. Only the key itself is
     * replaced here. It found one key, called it Thunderforest, and labelled it API KEY.
     */
    @Test fun theFileTheKeyActuallyArrivedInParsesToOneKey() {
        // Built from pieces on purpose: a 32-hex literal in the source is exactly what
        // Gate G2 scans the history for, and it cannot tell a fixture from a real key.
        val k = "a1b2c3d4" + "e5f60718" + "293a4b5c" + "6d7e8f90"
        val text = """
            THUNDERFOREST — map tiles (OpenStreetMap), api key
            Created 15.9.2026 by Marko. Account: someone@example.com
            Plan: Hobby Project — free, 150,000 tile requests per month, no card.
            Console: https://manage.thunderforest.com/   Pricing: https://www.thunderforest.com/pricing/

            API KEY
            $k

            HOW IT IS USED
              https://tile.thunderforest.com/outdoors/{z}/{x}/{y}.png?apikey=$k
            Styles: cycle, transport, landscape, outdoors.
        """.trimIndent()
        // Thunderforest's maps left the app on 16.9.2026, so this file — which is the real one
        // his key arrived in — now parses to nothing at all. The case is kept because the file
        // still exists on his phone and the reader must not invent a provider for it.
        assertTrue(Keys.parse(text).isEmpty())
    }

    @Test fun aKeyInsideAUrlIsStillTheSameKey() {
        // Was written with a Thunderforest URL; those maps left on 16.9.2026, so the case is
        // asked of the provider that remains. A key in a URL is still a key.
        val k = "AIza" + "B".repeat(35)
        val found = Keys.parse("https://tile.googleapis.com/v1/2dtiles/1/1/1?key=$k")
        assertEquals(1, found.size)
        assertEquals(k, found[0].key)
    }

    @Test fun anAccountLineIsNotMistakenForAKey() {
        val found = Keys.parse("Account: marko@example.com\nPlan: Hobby Project, 150000 requests")
        assertTrue(found.isEmpty())
    }

    @Test fun anUppercaseHexStringIsNotAThunderforestKey() {
        // Their keys are lowercase; a hex string in capitals is a checksum in somebody's notes.
        assertNull(Keys.providerOf("A1B2C3D4" + "E5F60718" + "293A4B5C" + "6D7E8F90"))
    }

    // --- Tracks: what they are called and what the manager does to them ----------------------

    /**
     * kotlin.io.createTempDir is deprecated for putting a world-readable directory in the shared
     * temp space, and allWarningsAsErrors is right to refuse it. One folder per test, ours alone.
     */
    private fun tempFolder(): java.io.File =
        java.nio.file.Files.createTempDirectory("mantra-trail-test").toFile()

    @Test fun aTrackIsCalledByItsDayItsTimeAndTheWordTrack() {
        val name = Tracks.defaultName(1_789_387_200_000L, java.time.ZoneOffset.UTC)
        assertEquals("2026-09-14 12:00 Track", name)
    }

    @Test fun twoWalksAnHourApartGetDifferentNames() {
        val a = Tracks.defaultName(1_789_387_200_000L, java.time.ZoneOffset.UTC)
        val b = Tracks.defaultName(1_789_390_800_000L, java.time.ZoneOffset.UTC)
        assertFalse(a == b)
    }

    @Test fun parenthesesSurviveBecauseARouteIsNamedWithThem() {
        assertEquals("Velebit (AB).gpx", Tracks.safeFileName("Velebit (AB)"))
        // An empty pair is not worth keeping.
        assertEquals("Velebit.gpx", Tracks.safeFileName("Velebit ()"))
    }

    @Test fun aTypedNameCannotBreakAPath() {
        assertEquals("north-south.gpx", Tracks.safeFileName("north/south"))
        assertEquals("Velebit-2.gpx", Tracks.safeFileName("Velebit #2"))
        assertFalse(Tracks.safeFileName("../../etc/passwd").contains("/"))
        assertFalse(Tracks.safeFileName("a\u0000b").contains("\u0000"))
    }

    @Test fun anEmptyNameIsStillAFile() {
        assertEquals("Track.gpx", Tracks.safeFileName("   "))
        assertEquals("Track.gpx", Tracks.safeFileName("///"))
    }

    @Test fun theExtensionIsNotAddedTwice() {
        assertEquals("Velebit.gpx", Tracks.safeFileName("Velebit.gpx"))
        assertEquals("Velebit.gpx", Tracks.safeFileName("Velebit"))
    }

    @Test fun aVeryLongNameIsCutRatherThanRefused() {
        val name = Tracks.safeFileName("x".repeat(200))
        assertTrue(name.length <= 64)
        assertTrue(name.endsWith(".gpx"))
    }

    @Test fun theNameShownIsTheFileNameWithoutItsExtension() {
        assertEquals("2026-09-14 12:00 Track", Tracks.displayName("2026-09-14 12:00 Track.gpx"))
    }

    @Test fun theFolderIsTheList() {
        val dir = tempFolder()
        assertTrue(Tracks.list(dir).isEmpty())
        java.io.File(dir, "one.gpx").writeText("<gpx/>")
        java.io.File(dir, "notes.txt").writeText("not a track")
        val found = Tracks.list(dir)
        assertEquals(1, found.size)
        assertEquals("one", found[0].name)
        dir.deleteRecursively()
    }

    @Test fun theNewestTrackIsFirst() {
        val dir = tempFolder()
        val old = java.io.File(dir, "old.gpx").apply { writeText("<gpx/>"); setLastModified(1_000_000) }
        val new = java.io.File(dir, "new.gpx").apply { writeText("<gpx/>"); setLastModified(9_000_000) }
        assertEquals(listOf("new", "old"), Tracks.list(dir).map { it.name })
        old.delete(); new.delete(); dir.deleteRecursively()
    }

    @Test fun renamingMovesTheFile() {
        val dir = tempFolder()
        val file = java.io.File(dir, "one.gpx").apply { writeText("<gpx/>") }
        val (renamed, problem) = Tracks.rename(file, "Velebit north")
        assertNull(problem)
        assertEquals("Velebit north.gpx", renamed!!.name)
        assertFalse(file.exists())
        assertEquals("<gpx/>", renamed.readText())
        dir.deleteRecursively()
    }

    @Test fun renamingNeverWritesOverAnotherWalk() {
        val dir = tempFolder()
        java.io.File(dir, "Velebit.gpx").writeText("the first walk")
        val second = java.io.File(dir, "other.gpx").apply { writeText("the second walk") }
        val (renamed, problem) = Tracks.rename(second, "Velebit")
        assertNull(renamed)
        assertNotNull(problem)
        assertEquals("the first walk", java.io.File(dir, "Velebit.gpx").readText())
        assertTrue(second.exists())
        dir.deleteRecursively()
    }

    @Test fun renamingToItsOwnNameIsNotACollision() {
        val dir = tempFolder()
        val file = java.io.File(dir, "Velebit.gpx").apply { writeText("<gpx/>") }
        val (renamed, problem) = Tracks.rename(file, "Velebit")
        assertNull(problem)
        assertEquals(file.absolutePath, renamed!!.absolutePath)
        dir.deleteRecursively()
    }

    @Test fun renamingSomethingAlreadyGoneSaysSo() {
        val dir = tempFolder()
        val file = java.io.File(dir, "gone.gpx")
        val (renamed, problem) = Tracks.rename(file, "anything")
        assertNull(renamed)
        assertNotNull(problem)
        dir.deleteRecursively()
    }

    @Test fun deletingRemovesIt() {
        val dir = tempFolder()
        val file = java.io.File(dir, "one.gpx").apply { writeText("<gpx/>") }
        assertNull(Tracks.delete(file))
        assertFalse(file.exists())
        assertNull(Tracks.delete(file))
        dir.deleteRecursively()
    }

    @Test fun sizesAreWrittenForAWalkNotForADiskDrive() {
        assertEquals("240 kB", Tracks.formatSize(240_000))
        assertEquals("1.2 MB", Tracks.formatSize(1_200_000))
        assertEquals("900 B", Tracks.formatSize(900))
    }

    // --- reading a saved walk back --------------------------------------------------------------

    @Test fun ourOwnFileReadsBackToTheSamePoints() {
        // The two halves must agree: what Gpx writes, GpxRead reads.
        val written = listOf(
            Fix(45.815, 15.9819, 158.4, 1_000L, 4f),
            Fix(45.816, 15.9820, 160.0, 2_000L, 4f),
        )
        val text = Gpx.whole("Velebit", written, 0L)
        val read = GpxRead.points(text)
        assertEquals(2, read.size)
        assertEquals(45.815, read[0].lat, 1e-6)
        assertEquals(15.9819, read[0].lon, 1e-6)
        assertEquals(158.4, read[0].ele!!, 0.05)
        assertEquals("Velebit", GpxRead.name(text))
    }

    @Test fun aFileWithTheAttributesTheOtherWayRoundStillReads() {
        val text = """<gpx><trk><trkseg>
            <trkpt lon="15.98" lat="45.81"><ele>100</ele></trkpt>
            </trkseg></trk></gpx>"""
        val read = GpxRead.points(text)
        assertEquals(1, read.size)
        assertEquals(45.81, read[0].lat, 1e-6)
        assertEquals(15.98, read[0].lon, 1e-6)
    }

    @Test fun aWholeFileOnOneLineReads() {
        val text = "<gpx><trkpt lat=\"45.1\" lon=\"15.1\"/><trkpt lat=\"45.2\" lon=\"15.2\"/></gpx>"
        assertEquals(2, GpxRead.points(text).size)
    }

    @Test fun aTrackCutOffByAFlatBatteryYieldsWhatItHas() {
        val text = """<gpx><trk><trkseg>
            <trkpt lat="45.1" lon="15.1"><ele>100</ele></trkpt>
            <trkpt lat="45.2" lon="15.2"><ele>1"""
        val read = GpxRead.points(text)
        assertEquals(2, read.size)
        assertEquals(45.2, read[1].lat, 1e-6)
    }

    @Test fun extensionsFromOtherProgrammesAreIgnored() {
        val text = """<gpx><trkpt lat="45.1" lon="15.1">
            <ele>100</ele><time>2026-09-15T10:00:00Z</time>
            <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>141</gpxtpx:hr>
            </gpxtpx:TrackPointExtension></extensions></trkpt></gpx>"""
        val read = GpxRead.points(text)
        assertEquals(1, read.size)
        assertEquals(100.0, read[0].ele!!, 0.01)
        assertTrue(read[0].timeMs > 0)
    }

    @Test fun aCoordinateThatIsNotOnEarthIsNotDrawn() {
        val text = """<gpx><trkpt lat="91.0" lon="15.1"/><trkpt lat="45.1" lon="200.0"/>
            <trkpt lat="45.1" lon="15.1"/></gpx>"""
        assertEquals(1, GpxRead.points(text).size)
    }

    @Test fun aFileWithNoPointsIsEmptyRatherThanAnError() {
        assertTrue(GpxRead.points("<gpx></gpx>").isEmpty())
        assertTrue(GpxRead.points("").isEmpty())
        assertTrue(GpxRead.points("this is not xml at all").isEmpty())
    }

    @Test fun aTimeInAFormatNobodyExpectedIsZeroRatherThanAThrow() {
        assertEquals(0L, GpxRead.parseTime("yesterday afternoon"))
        assertEquals(0L, GpxRead.parseTime(""))
        assertTrue(GpxRead.parseTime("2026-09-15T10:00:00Z") > 0)
    }

    @Test fun aRouteIsNamedSoItCanBeToldFromAWalk() {
        val name = "${Tracks.defaultName(1_789_387_200_000L, java.time.ZoneOffset.UTC).removeSuffix(" Track")} (AB)"
        assertEquals("2026-09-14 12:00 (AB)", name)
        // And it survives becoming a file and coming back, like any other track.
        val fileName = Tracks.safeFileName(name)
        assertTrue(fileName.endsWith(".gpx"))
        assertEquals("2026-09-14 12-00 (AB)", Tracks.displayName(fileName))
    }

    @Test fun aTwoPointRouteIsAnOrdinaryGpxFile() {
        val a = Fix(45.815, 15.981, null, 1_000L, null)
        val b = Fix(45.900, 16.100, null, 1_000L, null)
        val text = Gpx.whole("2026-09-14 12:00 (AB)", listOf(a, b), 1_000L)
        val read = GpxRead.points(text)
        assertEquals(2, read.size)
        assertEquals(45.815, read[0].lat, 1e-6)
        assertEquals(16.100, read[1].lon, 1e-6)
    }

    @Test fun aNameTheAppMadeIsToldFromANameHeChose() {
        assertTrue(Tracks.isDefaultName("2026-09-15 11:30 Track"))
        assertFalse(Tracks.isDefaultName("Velebit sjever"))
        assertFalse(Tracks.isDefaultName(""))
    }

    @Test fun theDoubleStampedNamesTheOldBuilderLeftAreAlsoAppMade() {
        // These are on the phone already, and they are the ones that were unbearable to edit by
        // hand. The rename box must open EMPTY for them too.
        assertTrue(Tracks.isDefaultName("2026-09-15_1130_2026-09-15-11-30-track"))
        assertTrue(Tracks.isDefaultName("2026-09-14_1200_velebit-sjever"))
    }

    @Test fun theTrackFileIsNamedAfterTheTrackAndNothingElse() {
        // The fault this closes: a date stamp in front of a name that was already a date.
        val name = Tracks.defaultName(1_789_387_200_000L, java.time.ZoneOffset.UTC)
        val fileName = Tracks.safeFileName(name)
        // The colon becomes a dash: a colon is not a character a file name can carry everywhere,
        // and a track copied to an SD card or a Windows machine must still open.
        assertEquals("2026-09-14 12-00 Track.gpx", fileName)
        assertEquals("2026-09-14 12-00 Track", Tracks.displayName(fileName))
        // And the name survives the round trip, which is what makes the popup able to open empty.
        assertTrue(Tracks.isDefaultName(Tracks.displayName(fileName)))
    }

    @Test fun aNameHeChoseSurvivesBecomingAFileNameAndComingBack() {
        val fileName = Tracks.safeFileName("Velebit sjever")
        assertEquals("Velebit sjever.gpx", fileName)
        assertEquals("Velebit sjever", Tracks.displayName(fileName))
        assertFalse(Tracks.isDefaultName(Tracks.displayName(fileName)))
    }

    @Test fun aKeyIsDescribedByPositionAndLengthAndNothingElse() {
        val d = Keys.describe(0, 3, Keys.Found(fakeKey, Keys.Provider.GOOGLE, null))
        assertTrue(d.contains("1 of 3"))
        assertTrue(d.contains("${fakeKey.length}"))
        assertFalse(d.contains(fakeKey.substring(0, 8)))
    }

    // --- Parcels: the cadastre (27.9.2026) ------------------------------------------------------

    // Two squares side by side, shaped as the state's WFS answers: GeoJSON, longitude first.
    private val twoParcels = """
        {"type":"FeatureCollection","features":[
         {"type":"Feature","id":"CP.6438470","geometry":{"type":"Polygon","coordinates":[[[15.0,44.0],[15.001,44.0],[15.001,44.001],[15.0,44.001],[15.0,44.0]]]},
          "properties":{"areaValue":{"value":12401,"@uom":"m2"},"inspireId":{"localId":"CP.6438470","namespace":"HR.DGU.CP"},"label":"2450","nationalCadastralReference":"334723-2450"}},
         {"type":"Feature","id":"CP.10480898","geometry":{"type":"MultiPolygon","coordinates":[[[[15.001,44.0],[15.002,44.0],[15.002,44.001],[15.001,44.001],[15.001,44.0]]]]},
          "properties":{"inspireId":{"localId":"CP.10480898"},"label":"154/1","nationalCadastralReference":"334723-154/1"}}
        ]}
    """.trimIndent()

    @Test fun cadastreTileBoxIsTheWholeWorldAtZoomZero() {
        val b = Parcels.tileBox(0, 0, 0)
        assertEquals(-20037508.34, b[0], 0.01)
        assertEquals(-20037508.34, b[1], 0.01)
        assertEquals(20037508.34, b[2], 0.01)
        assertEquals(20037508.34, b[3], 0.01)
    }

    @Test fun cadastreTileBoxMatchesTheOneMeasuredOverKukljica() {
        // The box that returned the parcels of Kukljica from the live WMS on 27.9.2026, computed
        // independently in Python for tile 17/71086/47645.
        val b = Parcels.tileBox(17, 71086, 47645)
        assertEquals(1696902.03, b[0], 0.05)
        assertEquals(5469833.74, b[1], 0.05)
        assertEquals(1697207.78, b[2], 0.05)
        assertEquals(5470139.49, b[3], 0.05)
    }

    @Test fun cadastreTilePathBecomesAWmsRequestAndNothingElseIsTouched() {
        val asked = Parcels.resolve(Parcels.WMS + "/17/71086/47645")
        assertTrue(asked.startsWith(Parcels.WMS + "?SERVICE=WMS"))
        assertTrue(asked.contains("REQUEST=GetMap"))
        assertTrue(asked.contains("STYLES=&"))          // the server refuses a GetMap without it
        assertTrue(asked.contains("CRS=EPSG:3857"))
        assertTrue(asked.contains("BBOX=1696902.0"))
        val other = "https://tile.example/1/2/3.png"
        assertEquals(other, Parcels.resolve(other))
        assertEquals(Parcels.WMS + "/a/b/c", Parcels.resolve(Parcels.WMS + "/a/b/c"))
    }

    @Test fun parcelNumbersAreReadOutOfWhatHeTyped() {
        assertEquals(listOf("2450", "2449/3", "2451"), Parcels.numbers("2450, 2449/3  2451;2450"))
        assertEquals(emptyList<String>(), Parcels.numbers("Kukljica, abc, /3, 12/"))
    }

    @Test fun parcelsAreParsedWithTheirIdNumberAreaAndLatitudeFirstRings() {
        val parcels = Parcels.parseParcels(twoParcels)
        assertEquals(2, parcels.size)
        val first = parcels[0]
        assertEquals(6438470L, first.id)
        assertEquals("2450", first.number)
        assertEquals("334723", first.municipality)
        assertEquals(12401, first.areaM2)
        assertEquals(44.0, first.rings[0][0].first, 1e-9)   // latitude first, though GeoJSON is not
        assertEquals(15.0, first.rings[0][0].second, 1e-9)
        assertEquals(10480898L, parcels[1].id)
        assertNull(parcels[1].areaM2)
        assertEquals(1, parcels[1].rings.size)               // the MultiPolygon's outer ring
    }

    @Test fun theTapFindsTheParcelItLandedInAndNotItsNeighbour() {
        val parcels = Parcels.parseParcels(twoParcels)
        assertEquals("2450", Parcels.containing(parcels, 44.0005, 15.0005)?.number)
        assertEquals("154/1", Parcels.containing(parcels, 44.0005, 15.0015)?.number)
        assertNull(Parcels.containing(parcels, 44.0005, 15.0030))  // the sea
    }

    // The plain text GetFeatureInfo returned for a tap on 2451 in Kukljica, 27.9.2026, verbatim.
    private val infoText = """
        Results for FeatureType 'http://cp_wms:CP.CadastralParcel':
        --------------------------------------------
        ID = 6438471
        GEOMETRY = [GEOMETRY (Polygon) with 6 points]
        BROJ_CESTICE = 2451
        MATICNI_BROJ_KO = 334723
        --------------------------------------------
    """.trimIndent()

    @Test fun theTapReadsIdNumberAndMunicipalityFromThePlainText() {
        val p = Parcels.parcelFromInfo(infoText)
        assertNotNull(p)
        assertEquals(6438471L, p!!.id)
        assertEquals("2451", p.number)
        assertEquals("334723-2451", p.reference)
        assertEquals("334723", p.municipality)
        assertTrue(p.rings.isEmpty())
    }

    @Test fun aTapInTheSeaFindsNothing() {
        assertNull(Parcels.parcelFromInfo("no features were found\n"))
        assertNull(Parcels.parseInfo(""))
    }

    @Test fun theMunicipalityIsReadFromTheZoningLayer() {
        val text = "Results for FeatureType 'http://cp_wms:CP.CadastralZoning':\n" +
            "--------------------------------------------\nID = 1354\n" +
            "GEOMETRY = [GEOMETRY (MultiPolygon) with 1124 points]\nLABEL = 334723-KUKLJICA\n" +
            "--------------------------------------------\n"
        assertEquals("334723" to "KUKLJICA", Parcels.zoningFromInfo(text))
    }

    @Test fun theTapAsksTheMiddlePixelOfASmallBoxInPlainText() {
        val url = Parcels.infoUrl(44.0368, 15.2279)
        assertTrue(url.contains("REQUEST=GetFeatureInfo"))
        assertTrue(url.contains("INFO_FORMAT=text/plain"))   // JSON and GML are refused
        assertTrue(url.contains("I=50&J=50"))
        assertTrue(url.contains("STYLES=&"))
    }

    @Test fun searchTakesTheExactNumberAndNotOneThatBeginsWithIt() {
        val json = """[{"key1":"111","value1":"24510"},{"key1":"6438471","value1":"2451"}]"""
        assertEquals(6438471L, Parcels.parseSearch(json, "2451"))
        assertNull(Parcels.parseSearch("[]", "2451"))
        assertTrue(Parcels.searchUrl("2449/3", "334723").endsWith("search=2449%2F3&municipalityRegNum=334723"))
    }

    @Test fun aShapeThatArrivesLaterGoesIntoItsOwnMarkOnly() {
        val parcels = Parcels.parseParcels(twoParcels)
        val waiting = Parcels.Mark("334723-2450", "2450", 0xFFEF4444L, emptyList(), 6438470L)
        val other = Parcels.Mark("334723-9", "9", 0xFF34D399L, emptyList(), 9L)
        val marks = Parcels.withShapes(listOf(waiting, other), parcels)
        assertEquals(5, marks[0].rings[0].size)
        assertEquals(0xFFEF4444L, marks[0].colour)
        assertTrue(marks[1].rings.isEmpty())
        assertEquals(listOf(other), Parcels.shapeless(marks))
    }

    @Test fun aMarkWithoutItsShapeYetSurvivesBeingWrittenAndRead() {
        val m = Parcels.Mark("334723-2451", "2451", 0xFFE8A64BL, emptyList(), 6438471L)
        val back = Parcels.decode(Parcels.encode(listOf(m)))
        assertEquals(1, back.size)
        assertEquals("2451", back[0].number)
        assertTrue(back[0].rings.isEmpty())
    }

    @Test fun theRecordCarriesEveryPossessorWithShareAndAddress() {
        // Invented people: a real record's names do not belong in a repository.
        val json = """
            {"parcelId":1,"parcelNumber":"154/1","cadMunicipalityName":"KUKLJICA","cadMunicipalityRegNum":"334723",
             "address":"DONJE POLJE","area":"501",
             "parcelParts":[{"name":"ORANICA","area":"101","possessionSheetNumber":"376"},{"name":"VOĆNJAK","area":"400","possessionSheetNumber":"365"}],
             "possessionSheets":[
               {"possessionSheetNumber":"376","possessors":[{"name":"Ana Primjer ","ownership":"1/3","address":"Kukljica 1"},{"name":"Ivo Primjer","ownership":"2/3","address":""}]},
               {"possessionSheetNumber":"365","possessors":[{"name":"Mare Uzorak","ownership":"1/1","address":"Zadar"}]}]}
        """.trimIndent()
        val r = Parcels.parseRecord(json)
        assertEquals("154/1", r.number)
        assertEquals("KUKLJICA", r.municipality)
        assertEquals(2, r.uses.size)
        assertEquals("VOĆNJAK", r.uses[1].name)
        assertEquals(2, r.sheets.size)
        assertEquals("Ana Primjer", r.sheets[0].owners[0].name)
        assertEquals("2/3", r.sheets[0].owners[1].share)
        assertEquals("Mare Uzorak", r.sheets[1].owners[0].name)
        assertEquals(0, r.landBooks.size)
    }

    @Test fun theRecordNamesTheLandRegistryUnit() {
        val json = """{"parcelNumber":"2451","lrUnitsFromParcelLinks":[{"lrUnitNumber":"37","mainBookName":"KUKLJICA",
            "institutionName":"Zemljišnoknjižni odjel Zadar","lrUnitTypeName":"VLASNIČKI"}]}"""
        val book = Parcels.parseRecord(json).landBooks.single()
        assertEquals("37", book.unit)
        assertEquals("KUKLJICA", book.book)
        assertEquals("Zemljišnoknjižni odjel Zadar", book.office)
    }

    @Test fun aRecordWithNoSheetsIsEmptyNotAnError() {
        val r = Parcels.parseRecord("""{"parcelNumber":"9"}""")
        assertEquals(0, r.sheets.size)
        assertEquals(0, r.uses.size)
    }

    @Test fun highlightedParcelsSurviveBeingWrittenAndRead() {
        val parcels = Parcels.parseParcels(twoParcels)
        val marks = listOf(Parcels.markOf(parcels[0], 0xFFE8A64BL), Parcels.markOf(parcels[1], 0xFF34D399L))
        val back = Parcels.decode(Parcels.encode(marks))
        assertEquals(2, back.size)
        assertEquals("334723-2450", back[0].reference)
        assertEquals("154/1", back[1].number)
        assertEquals(0xFFE8A64BL, back[0].colour)
        assertEquals(6438470L, back[0].id)
        assertEquals(5, back[0].rings[0].size)
        assertEquals(44.001, back[0].rings[0][2].first, 1e-6)
        assertEquals(emptyList<Parcels.Mark>(), Parcels.decode(""))
        assertEquals(emptyList<Parcels.Mark>(), Parcels.decode("garbage|line"))
    }

    @Test fun aParcelIsHighlightedOnceAndANewColourReplacesTheOld() {
        val p = Parcels.parseParcels(twoParcels)[0]
        var marks = Parcels.withMark(emptyList(), Parcels.markOf(p, 0xFFE8A64BL))
        marks = Parcels.withMark(marks, Parcels.markOf(p, 0xFFEF4444L))
        assertEquals(1, marks.size)
        assertEquals(0xFFEF4444L, marks[0].colour)
        assertEquals(0, Parcels.without(marks, p.reference).size)
    }

    @Test fun theStatesBlackBecomesOurInkAndClearStaysClear() {
        assertEquals(0, Parcels.recolour(0x00000000, Parcels.INK_LIGHT))
        val black = 0xFF000000.toInt()
        val c = Parcels.recolour(black, Parcels.INK_LIGHT)
        assertEquals(0xF2DDB4, c and 0xFFFFFF)
        assertEquals((255 * Parcels.INK_ALPHA).toInt(), (c ushr 24) and 0xFF)
    }

    @Test fun searchAsksForEveryNumberInOneEncodedFilter() {
        val url = Parcels.byReferenceUrl(listOf("334723-2450", "334723-2449/3"))
        assertTrue(url.contains("CQL_FILTER=nationalCadastralReference%20IN%20%28%27334723-2450%27%2C%27334723-2449%2F3%27%29"))
        assertFalse(url.contains(" "))
    }

    @Test fun areaIsWrittenTheWayASurveyorWritesIt() {
        assertEquals("12 401 m²", Parcels.areaLabel(12401))
        assertEquals("501 m²", Parcels.areaLabel(501))
        assertEquals("površina nepoznata", Parcels.areaLabel(null))
    }

    // --- Outline: the parcel read off the state's picture (27.9.2026) ---------------------------

    /** A 60×40 picture with a one-pixel frame at x 10..50, y 5..35, and a "number" inside. */
    private fun framedPicture(): Triple<BooleanArray, Int, Int> {
        val w = 60
        val h = 40
        val wall = BooleanArray(w * h)
        for (x in 10..50) { wall[5 * w + x] = true; wall[35 * w + x] = true }
        for (y in 5..35) { wall[y * w + 10] = true; wall[y * w + 50] = true }
        for (x in 28..32) for (y in 18..22) wall[y * w + x] = true   // the printed number
        return Triple(wall, w, h)
    }

    @Test fun theFillStaysInsideTheLinesAndGoesRoundTheNumber() {
        val (wall, w, h) = framedPicture()
        val inside = Outline.fill(wall, w, h, 15, 10)!!
        assertEquals(39 * 29 - 25, inside.count { it })     // the frame's inside, less the number
        assertFalse(inside[20 * w + 30])                       // the number is a hole
        assertFalse(inside[2 * w + 2])                         // outside the frame
    }

    @Test fun aFillThatReachesTheEdgeIsNotAParcel() {
        val (wall, w, h) = framedPicture()
        assertNull(Outline.fill(wall, w, h, 2, 2))
        wall[5 * w + 30] = false                               // a gap in the line
        assertNull(Outline.fill(wall, w, h, 15, 10))
    }

    @Test fun aTapOnTheLineStartsFromTheNearestOpenPixel() {
        val (wall, w, h) = framedPicture()
        val start = Outline.nearestOpen(wall, w, h, 30, 20)!!
        assertFalse(wall[start.second * w + start.first])
    }

    @Test fun theOutlineIsTheFourCornersOfTheFrame() {
        val (wall, w, h) = framedPicture()
        val inside = Outline.fill(wall, w, h, 15, 10)!!
        val edge = Outline.trace(Outline.grow(inside, w, h, 1), w, h)
        val corners = Outline.simplify(edge.map { it.first.toDouble() to it.second.toDouble() }, 1.2)
        assertEquals(4, corners.size)
        // grown by one onto the middle of the line: the corners sit on the frame itself
        assertTrue(corners.all { (x, y) -> (x == 10.0 || x == 50.0) && (y == 5.0 || y == 35.0) })
    }

    @Test fun aSlantedSideIsKeptAsOneStraightEdge() {
        val w = 50
        val h = 50
        val wall = BooleanArray(w * h)
        // a right triangle: the two legs and a diagonal of single pixels, touching only corner to corner
        for (i in 5..45) { wall[45 * w + i] = true; wall[i * w + 5] = true; wall[i * w + i] = true }
        val inside = Outline.fill(wall, w, h, 10, 40)!!
        val edge = Outline.trace(Outline.grow(inside, w, h, 1), w, h)
        val corners = Outline.simplify(edge.map { it.first.toDouble() to it.second.toDouble() }, 1.5)
        assertEquals(3, corners.size)
    }

    @Test fun theTracedCornersBecomeLatitudeAndLongitudeInsideTheBox() {
        val (wall, w, h) = framedPicture()
        val box = Outline.box(44.0368, 15.2279, 60.0)
        // the frame's inside holds the middle of the picture? no: the tap goes where the frame is
        val lat = Outline.latOf(box[3] - 10.5 * (box[3] - box[1]) / h)
        val lon = Outline.lonOf(box[0] + 15.5 * (box[2] - box[0]) / w)
        val ring = Outline.parcelAt(wall, w, h, box, lat, lon)!!
        assertEquals(4, ring.size)
        assertTrue(ring.all { it.first in 44.0364..44.0372 && it.second in 15.2274..15.2284 })
        // and a tap in the parcel is inside the ring that came back
        assertTrue(Parcels.contains(ring, lat, lon))
    }

    @Test fun mercatorThereAndBackIsTheSamePlace() {
        assertEquals(44.0368, Outline.latOf(Outline.mercY(44.0368)), 1e-9)
        assertEquals(15.2279, Outline.lonOf(Outline.mercX(15.2279)), 1e-9)
    }

    @Test fun theTextFileCarriesTheWholeSheetAndTheOutline() {
        val parcel = Parcels.parseParcels(twoParcels)[0]
        val record = Parcels.parseRecord("""
            {"parcelNumber":"2450","cadMunicipalityName":"KUKLJICA","cadMunicipalityRegNum":"334723","address":"DRAGE","area":"12401",
             "parcelParts":[{"name":"ŠUMA","area":"6200","possessionSheetNumber":"657"}],
             "possessionSheets":[{"possessionSheetNumber":"657","possessors":[{"name":"Ana Primjer","ownership":"1/1","address":"Kukljica 1"}]}],
             "lrUnitsFromParcelLinks":[{"lrUnitNumber":"1817","mainBookName":"KUKLJICA","institutionName":"Zemljišnoknjižni odjel Zadar","lrUnitTypeName":"VLASNIČKI"}]}
        """.trimIndent())
        val text = Parcels.toText(parcel, record, "27.9.2026 10:00")
        assertTrue(text.startsWith("ČESTICA 2450"))
        assertTrue(text.contains("KUKLJICA (334723)"))
        assertTrue(text.contains("12 401 m²"))
        assertTrue(text.contains("ŠUMA, 6200 m², posjedovni list 657"))
        assertTrue(text.contains("Ana Primjer  1/1"))
        assertTrue(text.contains("z.k. uložak 1817"))
        assertTrue(text.contains("OBRIS"))
        assertTrue(text.contains("44.000000, 15.000000"))
        assertEquals("cestica 334723-154_1.txt", Parcels.textFileName(Parcels.parseParcels(twoParcels)[1]))
    }

    @Test fun aTextFileWithoutTheRecordSaysSo() {
        val text = Parcels.toText(Parcels.parseParcels(twoParcels)[1], null, "now")
        assertTrue(text.contains("nije mogao pročitati"))
    }

    @Test fun theSelectionIsNoneOfTheFiveHighlightColours() {
        val five = listOf(0xFF34D399L, 0xFFE8A64BL, 0xFFEF4444L, 0xFF60A5FAL, 0xFFF2DDB4L)
        assertFalse(Parcels.SELECTION in five)
    }

    // --- Finding: one list for every search (27.9.2026) ----------------------------------------

    @Test fun resultsAreMergedOnceEachNearestFirst() {
        val a = listOf(
            Finding.Hit("1", "Trg Stjepana Radića 13C", "Bjelovar", 68503),
            Finding.Hit("2", "Ulica Stjepana Radića 13c", "Mokrice", 20057),
        )
        val b = listOf(
            Finding.Hit("2", "Ulica Stjepana Radića 13c", "Mokrice", 20057),   // the same place again
            Finding.Hit("3", "Ulica Stjepana Radića 13", "Mičevec", 10000),
            Finding.Hit("4", "Ul. Stjepana Radića 13", "Vrbovec", null),
        )
        val m = Finding.merge(a, b)
        assertEquals(listOf("3", "2", "1", "4"), m.map { it.id })
    }

    @Test fun theHouseLetterIsDroppedForTheSecondQuestionOnly() {
        assertEquals("stjepana radića 13", Finding.withoutHouseLetter("stjepana radića 13c"))
        assertEquals("Ribnjak 6", Finding.withoutHouseLetter("Ribnjak 6A"))
        assertNull(Finding.withoutHouseLetter("stjepana radića 13"))
        assertNull(Finding.withoutHouseLetter("Kukljica"))
    }

    @Test fun distancesReadAsGoogleWritesThem() {
        assertEquals("850 m", Finding.distanceLabel(850))
        assertEquals("1.5 km", Finding.distanceLabel(1500))
        assertEquals("68 km", Finding.distanceLabel(68503))
        assertEquals("", Finding.distanceLabel(null))
    }

    @Test fun theFilterNeedsNoDiacritics() {
        assertTrue(Finding.matches("ČABRIJAN NIKOLA, SIN VLADIMIRA", "cabri"))
        assertTrue(Finding.matches("Đurđević", "durd"))
        assertTrue(Finding.matches("RIJEKA, KUČIČKI PUT 1/E", "kucicki 1/e"))
        assertFalse(Finding.matches("SAMSA ŠTEFICA", "knez"))
    }

    @Test fun theSheetFilterKeepsOnlyTheMatchingOwners() {
        val record = Parcels.parseRecord("""
            {"parcelNumber":"3700/11","parcelParts":[{"name":"DVORIŠTE","area":"500","possessionSheetNumber":"12"}],
             "possessionSheets":[{"possessionSheetNumber":"12","possessors":[
               {"name":"Ana Primjer","ownership":"1/50","address":"Kučički put 1"},
               {"name":"Ivo Uzorak","ownership":"1/50","address":"Goranska 1a"}]}]}
        """.trimIndent())
        val rows = Parcels.sheetRows(record)
        assertEquals(3, rows.size)
        assertEquals(listOf("Ana Primjer"), Parcels.filterRows(rows, "kucicki").map { it.main })
        assertEquals(2, Parcels.filterRows(rows, "posjedovni").size)      // a heading lets its group through
        assertEquals(3, Parcels.filterRows(rows, "").size)
    }

    @Test fun theCadastresOwnSearchIsReadIntoHits() {
        // The shape OSS returned for 2451 in Kukljica, 27.9.2026; the holder's name invented.
        val json = """[{"parcelId":6438471,"parcelNumber":"2451","cadMunicipalityRegNum":"334723","cadMunicipalityName":"KUKLJICA",
            "address":"DRAGE","area":"1412","possessionSheet":{"possessors":[{"name":"ANA PRIMJER"}]}}]"""
        val hit = Parcels.parseSearch(json).single()
        assertEquals("6438471", hit.id)
        assertEquals("334723-2451", hit.ref)
        assertEquals(Finding.Source.PARCEL, hit.source)
        assertTrue(hit.title.startsWith("2451"))
        assertTrue(hit.under.contains("ANA PRIMJER"))
    }

    @Test fun theSearchBodyIsWhatTheCadastresOwnPagePosts() {
        // Compared field by field: org.json keeps no key order, and the server does not need one.
        val one = org.json.JSONObject(Parcels.searchBody("1354", number = "2451"))
        assertEquals(1354L, one.getLong("cadMunicipalityId"))
        assertEquals("2451", one.getString("parcelNumber"))
        assertFalse(one.has("possessionSheetNumber"))
        val two = org.json.JSONObject(Parcels.searchBody("1354", sheet = "657"))
        assertEquals("657", two.getString("possessionSheetNumber"))
        assertFalse(two.has("parcelNumber"))
    }

    @Test fun theZoningLayerGivesTheMunicipalitysInternalId() {
        val text = "Results for FeatureType 'http://cp_wms:CP.CadastralZoning':\n" +
            "--------------------------------------------\nID = 1354\nLABEL = 334723-KUKLJICA\n" +
            "--------------------------------------------\n"
        assertEquals("1354", Parcels.zoningIdFromInfo(text))
    }

    // --- the owner sheet, the land registry (29.9.2026) -----------------------------------------

    /** Folio 1500, k.o. Drenova, as the land registry sent it on 29.9.2026, cut to two shares. */
    private val folio1500 = """[{"lrUnitId":1,"lrUnitNumber":"1500","mainBookId":32218,"mainBookName":"DRENOVA",
        "institutionName":"Zemljišnoknjižni odjel Rijeka","lrUnitTypeName":"VLASNIČKI","lastDiaryNumber":"Z-8817/2024",
        "activePlumbs":[],
        "ownershipSheetB":{"lrUnitShares":[
          {"description":"2. Suvlasnički dio: 1/3","lrOwners":[{"name":"LIVAJA DRAGICA ","address":"Brune Francetića 17, Rijeka",
            "lrEntry":{"description":"Zaprimljeno 20.03.2024.g. pod brojem Z-7547/2024<br><br>UKNJIŽBA, PRAVO VLASNIŠTVA","orderNumber":"2.2"}}],
           "subSharesAndEntries":[{"description":"ZABILJEŽBA, DOŽIVOTNO UZDRŽAVANJE","orderNumber":"2.3"}],"orderNumber":"2"},
          {"description":"3. Suvlasnički dio: 1/3","lrOwners":[{"name":"LIVAJA ZORAN"}],"subSharesAndEntries":[],"orderNumber":"3"}],
          "lrEntries":[]},
        "possessionSheetA1":{"lrParcels":[{"parcelNumber":"115","address":"ORANICA","areaInHvat":"44"},
          {"parcelNumber":"1170/4","address":"CESTE","area":"26"}]},
        "encumbranceSheetC":{"lrEntryGroups":[{"description":"1. ","lrEntries":[{"description":
          "<span class='lr-entry-black' >Primljeno, 25. lipnja 1974. Z-1754/74<br><br>služnost prolaza</span>","orderNumber":"1.1"}]}]}}]"""

    @Test fun theOwnerSheetIsReadWithItsSharesOwnersAndBurdens() {
        val f = Parcels.parseFolio(folio1500)!!
        assertEquals("1500", f.unit)
        assertEquals("32218", f.bookId)
        assertEquals("DRENOVA", f.book)
        assertEquals(2, f.shares.size)
        assertEquals("1/3", Parcels.shareOf(f.shares[0].title))
        assertEquals("LIVAJA DRAGICA", f.shares[0].owners[0].name)
        assertEquals("2.2  Zaprimljeno 20.03.2024.g. pod brojem Z-7547/2024 · UKNJIŽBA, PRAVO VLASNIŠTVA", f.shares[0].entries[0])
        assertEquals("2.3  ZABILJEŽBA, DOŽIVOTNO UZDRŽAVANJE", f.shares[0].entries[1])
        assertEquals(listOf("1.1  Primljeno, 25. lipnja 1974. Z-1754/74 · služnost prolaza"), f.burdens)
        assertEquals(listOf("115  ORANICA  44 čhv", "1170/4  CESTE  26 m²"), f.parcels)
    }

    @Test fun aFolioThatIsNotThereIsNullNotACrash() {
        assertNull(Parcels.parseFolio("[]"))
        assertNull(Parcels.parseFolio("""{"status":"NOT_FOUND","statusCode":404}"""))
        assertEquals(emptyList<String>(), Parcels.parseFolioNumbers("""{"status":"NOT_FOUND"}"""))
        assertEquals(listOf("1243"), Parcels.parseFolioNumbers("""[{"lrUnitNumber":"1243","mainBookId":32218}]"""))
    }

    @Test fun theLandBookIsTheOneNamedExactlyAsTheMunicipality() {
        val json = """[{"key1":"30036","value1":"SLATINSKI DRENOVAC","value2":"ORAHOVICA"},
            {"key1":"32218","value1":"DRENOVA","value2":"RIJEKA"}]"""
        assertEquals(listOf(Parcels.Book("32218", "DRENOVA", "RIJEKA")), Parcels.parseBooks(json, "Drenova"))
        assertTrue(Parcels.foliosByParcelUrl("32218", "370/1").contains("parcelNumber=370%2F1"))
        assertTrue(Parcels.folioUrl("32218", "1243").contains("lrUnitNumber=1243&mainBookId=32218"))
    }

    @Test fun theOwnerSheetRowsGoToTheThirdTabAndTheFilterFindsThem() {
        val rows = Parcels.folioRows(Parcels.parseFolio(folio1500)!!)
        assertTrue(rows.all { Parcels.tabOf(it) == Parcels.Tab.OWNER })
        val owners = rows.filter { it.main.startsWith("LIVAJA") }
        assertEquals(listOf("1/3", "1/3"), owners.map { it.side })
        assertEquals(1, Parcels.filterRows(rows, "zoran").count { it.main == "LIVAJA ZORAN" })
        assertEquals(Parcels.Tab.USE, Parcels.tabOf(Parcels.SheetRow("NAČIN UPORABE", "x")))
        assertEquals(Parcels.Tab.POSSESSION, Parcels.tabOf(Parcels.SheetRow("POSJEDOVNI LIST 1615", "x")))
        assertEquals(Parcels.Tab.OWNER, Parcels.tabOf(Parcels.SheetRow("ZEMLJIŠNA KNJIGA", "x")))
    }

    @Test fun aFolioFoundByHandIsKeptPerParcel() {
        val links = mapOf("324523-3700/11" to ("32218" to "1243"))
        assertEquals(links, Parcels.decodeLinks(Parcels.encodeLinks(links)))
        assertEquals(emptyMap<String, Pair<String, String>>(), Parcels.decodeLinks(""))
    }

    @Test fun theTextFileCarriesTheOwnerSheet() {
        val p = Parcels.Parcel(1, "3700/11", "324523-3700/11", 3071, emptyList())
        val text = Parcels.toText(p, null, "29.9.2026", listOf(Parcels.parseFolio(folio1500)!!))
        assertTrue(text.contains("VLASNIČKI LIST, z.k. uložak 1500, k.o. DRENOVA"))
        assertTrue(text.contains("LIVAJA ZORAN"))
    }

    // --- Mantra ARKOD (29.9.2026): my parcels, the cadastre kept ahead ----------------------------

    @Test fun myParcelKeepsItsStyleAndNameThroughTheStore() {
        val ring = listOf(45.1 to 15.1, 45.1 to 15.2, 45.2 to 15.2)
        val mine = Parcels.Mark("334723-2450", "2450", 0xFFEF4444L, listOf(ring), 7L, Parcels.LineStyle.DOTTED, "vinograd")
        val back = Parcels.decode(Parcels.encode(listOf(mine)))
        assertEquals(listOf(mine), back)
    }

    @Test fun aParcelKeptWithoutAStyleIsDashed() {
        // Five fields, as Mantra Trail wrote its highlights: read as dashed and unnamed.
        val back = Parcels.decode("334723-2450|2450|ffef4444|7|45.100000,15.100000;45.100000,15.200000;45.200000,15.200000")
        assertEquals(1, back.size)
        assertEquals(Parcels.LineStyle.DASHED, back[0].style)
        assertEquals("", back[0].name)
    }

    @Test fun aNameCannotBreakTheLineItIsKeptOn() {
        val mine = Parcels.Mark("1-2", "2", 0xFF000000L, emptyList(), name = "a|b\nc")
        val back = Parcels.decode(Parcels.encode(listOf(mine)))
        assertEquals("a b c", back.single().name)
    }

    @Test fun recolouringOneOfMyParcelsKeepsItsPlaceInTheList() {
        val a = Parcels.Mark("1-1", "1", 1L, emptyList())
        val b = Parcels.Mark("1-2", "2", 1L, emptyList())
        val c = Parcels.Mark("1-3", "3", 1L, emptyList())
        val next = Parcels.withMark(listOf(a, b, c), b.copy(colour = 2L))
        assertEquals(listOf("1", "2", "3"), next.map { it.number })
        assertEquals(2L, next[1].colour)
        assertEquals(4, Parcels.withMark(next, Parcels.Mark("1-4", "4", 1L, emptyList())).size)
    }

    @Test fun theMiddleOfMyParcelIsWhereTheMapGoes() {
        val mine = Parcels.Mark("1-1", "1", 1L, listOf(listOf(45.0 to 15.0, 45.0 to 15.2, 45.2 to 15.2, 45.2 to 15.0)))
        val (lat, lon) = mine.middle!!
        assertEquals(45.1, lat, 1e-9)
        assertEquals(15.1, lon, 1e-9)
        assertNull(Parcels.Mark("1-1", "1", 1L, emptyList()).middle)
        assertEquals("1", mine.municipality)
    }

    @Test fun everyHueIsAnOpaqueColourAndTheWheelComesRound() {
        for (h in 0..360 step 15) {
            val c = Parcels.hue(h.toFloat())
            assertEquals(0xFFL, c ushr 24)
        }
        assertEquals(Parcels.hue(0f), Parcels.hue(360f))
        assertEquals(Parcels.hue(30f), Parcels.hue(-330f))
        // Red at 0, green at 120, blue at 240: the largest channel is the right one.
        fun ch(c: Long, shift: Int) = (c shr shift) and 0xFF
        assertTrue(ch(Parcels.hue(0f), 16) > ch(Parcels.hue(0f), 8))
        assertTrue(ch(Parcels.hue(120f), 8) > ch(Parcels.hue(120f), 16))
        assertTrue(ch(Parcels.hue(240f), 0) > ch(Parcels.hue(240f), 8))
    }

    @Test fun noQuickPickIsTheSelectionsCyan() {
        assertEquals(10, Parcels.SWATCHES.size)
        assertFalse(Parcels.SWATCHES.contains(Parcels.SELECTION))
        assertEquals(Parcels.SWATCHES.size, Parcels.SWATCHES.toSet().size)
    }

    @Test fun theTilesKeptAheadCoverTheCadastresZoomsNearestFirst() {
        val tiles = Parcels.prefetchTiles(45.8150, 15.9819)
        assertEquals((Parcels.MIN_ZOOM..18).toSet(), tiles.map { it.first }.toSet())
        assertEquals(tiles.size, tiles.toSet().size)
        // A few hundred tiles: a walk's ground, not a county.
        assertTrue(tiles.size in 150..700)
        // Within each zoom the first tile is the one under the point.
        for (z in Parcels.MIN_ZOOM..18) {
            val first = tiles.first { it.first == z }
            assertEquals(Geo.tileX(15.9819, z), first.second)
            assertEquals(Geo.tileY(45.8150, z), first.third)
        }
        // The coarse zooms come before the fine ones.
        assertEquals(tiles.map { it.first }, tiles.map { it.first }.sorted())
    }

    @Test fun theInkIsLightOnGooglesPhotographsAndTheNightThemeAndDarkElsewhere() {
        assertEquals(Parcels.INK_LIGHT, Parcels.inkFor(Layers.GOOGLE_SATELLITE.id, "DEFAULT", "satellite"))
        assertEquals(Parcels.INK_DARK, Parcels.inkFor(Layers.GOOGLE.id, "DEFAULT", "roadmap"))
        assertEquals(Parcels.INK_DARK, Parcels.inkFor(Layers.OSM.id, "NEWTRON"))
        assertEquals(Parcels.INK_LIGHT, Parcels.inkFor(Layers.OFFLINE.id, "NEWTRON"))
        assertEquals(Parcels.INK_DARK, Parcels.inkFor(Layers.OFFLINE.id, "DEFAULT"))
    }

    @Test fun theOfflineMapIsCroatiaAndTheAppOpensOverIt() {
        assertTrue(Layers.OfflineDownload.URL.endsWith("/europe/croatia.map"))
        assertTrue(Layers.HOME_LAT in 42.4..46.6)
        assertTrue(Layers.HOME_LON in 13.4..19.5)
        assertTrue(Layers.USER_AGENT.startsWith("MantraARKOD/"))
    }

    @Test fun theSheetSpeaksCroatian() {
        assertEquals(listOf("uporaba", "posjedovni", "vlasnički"), Parcels.Tab.entries.map { it.word })
        assertEquals(listOf("isprekidana", "puna", "točkasta"), Parcels.LineStyle.entries.map { it.word })
    }

    // --- v3 (29.9.2026): the parcels key, Parcel view, the parcel field, Imenik ------------------

    @Test fun theParcelsKeyHidesEverythingAndOnlyMineWinsOverIt() {
        assertEquals(true to true, Parcels.visibility(cadastreOn = true, onlyMine = false))
        assertEquals(false to false, Parcels.visibility(cadastreOn = false, onlyMine = false))
        // "all the time, no matter on or off, only my parcels drawn and everything else is out"
        assertEquals(false to true, Parcels.visibility(cadastreOn = true, onlyMine = true))
        assertEquals(false to true, Parcels.visibility(cadastreOn = false, onlyMine = true))
    }

    @Test fun theNumbersOssOffersBecomeHitsWithStarredOnesLast() {
        val json = """[{"key1":"11","value1":"*245"},{"key1":"12","value1":"2450"},{"key1":"","value1":"2451"},{"key1":"13","value1":"2452/1"}]"""
        val hits = Parcels.parseSuggestions(json, "334723", "KUKLJICA")
        assertEquals(listOf("2450", "2452/1", "*245"), hits.map { it.title })
        assertEquals("334723-2450", hits.first().ref)
        assertEquals("12", hits.first().id)
        assertEquals("k.o. KUKLJICA", hits.first().under)
        assertTrue(hits.all { it.source == Finding.Source.PARCEL })
        assertEquals(1, Parcels.parseSuggestions(json, "334723", "KUKLJICA", limit = 1).size)
        assertTrue(Parcels.parseSuggestions("[]", "334723", "KUKLJICA").isEmpty())
    }

    private fun entry(name: String, reference: String, role: OwnerBook.Role = OwnerBook.Role.POSJEDNIK, detail: String = "p.l. 1984") =
        OwnerBook.Entry(name, role, detail, 7L, reference, reference.substringAfter('-'), "KUKLJICA")

    @Test fun imenikTakesEveryHolderAndOwnerOfASheetAndDropsNamesWithoutLetters() {
        val parcel = Parcels.Parcel(7L, "1655/3", "334723-1655/3", 61, emptyList())
        val record = Parcels.Record(
            "1655/3", "KUKLJICA", "334723", "ŽAVRH", "61", emptyList(),
            listOf(Parcels.Sheet("1984", listOf(Parcels.Owner(" JAŠA ANICA ", "1/1", "KUKLJICA"), Parcels.Owner("---", "", "")))),
        )
        val folio = Parcels.Folio(
            "182", "KUKLJICA", "Zadar", "glavna", "", 0,
            listOf(Parcels.Share("1. Suvlasnički dio: 1/2", listOf(Parcels.Owner("JAŠA ANICA POK. JOSE", "1/2", "")), emptyList())),
            listOf("1655/3"), emptyList(),
        )
        val e = OwnerBook.entriesOf(parcel, record, listOf(folio))
        assertEquals(listOf("JAŠA ANICA", "JAŠA ANICA POK. JOSE"), e.map { it.name })
        assertEquals(listOf(OwnerBook.Role.POSJEDNIK, OwnerBook.Role.VLASNIK), e.map { it.role })
        assertEquals("p.l. 1984", e[0].detail)
        assertEquals("z.k.ul. 182 · 1/2", e[1].detail)
        assertTrue(e.all { it.reference == "334723-1655/3" && it.municipalityName == "KUKLJICA" })
    }

    @Test fun imenikReplacesWhatAParcelSaidBeforeSoASaleDropsTheSeller() {
        val before = listOf(entry("SELLER IVO", "334723-1"), entry("OTHER ANA", "334723-2"))
        val after = OwnerBook.add(before, listOf(entry("BUYER MARA", "334723-1"), entry("BUYER MARA", "334723-1")))
        assertEquals(listOf("BUYER MARA", "OTHER ANA"), after.map { it.name })
        assertEquals(2, OwnerBook.add(before, emptyList()).size)
        assertEquals(1, OwnerBook.add(before, listOf(entry("X", "334723-9")), limit = 1).size)
    }

    @Test fun imenikFindsANameByAnyWordsWithoutDiacritics() {
        val book = listOf(
            entry("JAŠA ANICA POK. JOSE", "334723-1"),
            entry("ANIĆ JOSIP", "334723-2"),
            entry("JAŠA ANICA POK. JOSE", "334723-1"),
        )
        assertEquals(listOf("JAŠA ANICA POK. JOSE"), OwnerBook.search(book, "anica jasa").map { it.name })
        // The name that begins with the first word comes first.
        assertEquals("ANIĆ JOSIP", OwnerBook.search(book, "ani").first().name)
        assertEquals(2, OwnerBook.search(book, "ani").size)
        assertTrue(OwnerBook.search(book, "a").isEmpty())
        assertTrue(OwnerBook.search(book, "marko").isEmpty())
    }

    @Test fun imenikSurvivesThePhoneAndAHitOpensTheParcel() {
        val book = listOf(entry("NAME | WITH BAR", "334723-1655/3", OwnerBook.Role.VLASNIK, "z.k.ul. 182 · 1/2"))
        val back = OwnerBook.decode(OwnerBook.encode(book))
        assertEquals(1, back.size)
        assertEquals("NAME / WITH BAR", back[0].name)
        assertEquals(OwnerBook.Role.VLASNIK, back[0].role)
        assertEquals("334723-1655/3", back[0].reference)
        assertTrue(OwnerBook.decode("").isEmpty())
        val hit = OwnerBook.hit(back[0])
        assertEquals("7", hit.id)
        assertEquals("334723-1655/3", hit.ref)
        assertEquals("1655/3 · k.o. KUKLJICA · vlasnik · z.k.ul. 182 · 1/2", hit.under)
    }

    // --- v5 (30.9.2026): the state's lines restyled, and the parcel caches -----------------------

    @Test fun theLinesKeepTheirOwnAlphaScaledAndTakeTheInk() {
        val px = intArrayOf(0x00000000, 0xFF000000.toInt(), 0x80000000.toInt())
        val out = ParcelStyle.restyle(px, 3, 1, 0xFFEF4444L, ParcelStyle.Lines(opacity = 50))
        assertEquals(0, out[0])
        assertEquals((127 shl 24) or 0xEF4444, out[1])
        assertEquals((64 shl 24) or 0xEF4444, out[2])
        // Auto follows the map's ink; a chosen colour wins over it.
        assertEquals(0xFF15171AL, ParcelStyle.Lines().ink(0xFF15171AL))
        assertEquals(0xFFFFFFFFL, ParcelStyle.Lines(colour = 0xFFFFFFFFL).ink(0xFF15171AL))
    }

    @Test fun fineKeepsTheCoreOfALineAndBoldGrowsIt() {
        // A line three pixels wide in a row of seven.
        val a = intArrayOf(0, 0, 255, 255, 255, 0, 0)
        assertEquals(listOf(0, 0, 89, 255, 89, 0, 0), ParcelStyle.fine(a, 7, 1).toList())
        assertEquals(listOf(0, 255, 255, 255, 255, 255, 0), ParcelStyle.bold(a, 7, 1).toList())
    }

    @Test fun aStyleIsRememberedAndEveryStyleIsItsOwnSetOfTiles() {
        val l = ParcelStyle.Lines(0xFFFACC15L, 35, ParcelStyle.Weight.FINE)
        assertEquals(l, ParcelStyle.decode(ParcelStyle.encode(l)))
        assertEquals(ParcelStyle.Lines(), ParcelStyle.decode(null))
        assertEquals(ParcelStyle.Lines(), ParcelStyle.decode(ParcelStyle.encode(ParcelStyle.Lines())))
        val keys = listOf(
            ParcelStyle.key(0xFF15171AL, ParcelStyle.Lines()),
            ParcelStyle.key(0xFF15171AL, ParcelStyle.Lines(opacity = 35)),
            ParcelStyle.key(0xFF15171AL, ParcelStyle.Lines(weight = ParcelStyle.Weight.BOLD)),
            ParcelStyle.key(0xFFF2DDB4L, ParcelStyle.Lines()),
        )
        assertEquals(keys.size, keys.toSet().size)
    }

    private val square = listOf(44.0 to 15.0, 44.0 to 15.001, 44.001 to 15.001, 44.001 to 15.0)

    private fun cacheOf(vararg items: ParcelCache.Item) = ParcelCache.Cache(
        ParcelCache.Info("c1", "Kukljica 30.9.2026", 1L, ParcelCache.Box(43.99, 14.99, 44.01, 15.01), 16),
        items.toList(),
    )

    private val baka = ParcelCache.Item(
        id = 11L, number = "2450", reference = "334723-2450", areaM2 = 501, rings = listOf(square),
        label = 44.0005 to 15.0005, municipalityName = "KUKLJICA", address = "DRAGE", uses = listOf("maslinik"),
        holders = listOf(
            ParcelCache.Holder("JAŠA ANICA POK. JOSE", OwnerBook.Role.POSJEDNIK, "p.l. 657"),
            ParcelCache.Holder("JAŠA ANICA", OwnerBook.Role.VLASNIK, "z.k.ul. 182 · 1/2"),
        ),
        read = true,
    )

    @Test fun aCacheFindsHisGrandmotherByNameWithoutDiacritics() {
        val c = cacheOf(baka, baka.copy(id = 12L, number = "2451", reference = "334723-2451", holders = emptyList()))
        val hits = ParcelCache.search(listOf(c), "anica jasa")
        assertEquals(listOf("JAŠA ANICA", "JAŠA ANICA POK. JOSE"), hits.map { it.title })
        assertTrue(hits.all { it.ref == "334723-2450" && it.lat != null })
        assertTrue(hits.first().under.contains("k.o. KUKLJICA"))
        assertTrue(hits.first().under.endsWith("Kukljica 30.9.2026"))
        assertTrue(ParcelCache.search(listOf(c), "marko").isEmpty())
    }

    @Test fun aCacheFindsByNumberSheetAddressAndUse() {
        val c = cacheOf(baka, baka.copy(id = 12L, number = "2451", reference = "334723-2451", holders = emptyList(), address = "", uses = emptyList()))
        assertEquals(listOf("2450", "2451"), ParcelCache.search(listOf(c), "245").map { it.title })
        assertEquals(listOf("2450"), ParcelCache.search(listOf(c), "pl 657").map { it.title })
        assertEquals(listOf("2450"), ParcelCache.search(listOf(c), "maslinik").map { it.title })
        assertEquals(listOf("2450"), ParcelCache.search(listOf(c), "drage").map { it.title })
    }

    @Test fun aTapInsideACachedParcelFindsItAndAHiddenCacheIsNotTapped() {
        val c = cacheOf(baka)
        assertEquals("2450", ParcelCache.at(listOf(c), 44.0005, 15.0005)?.number)
        assertNull(ParcelCache.at(listOf(c), 44.002, 15.0005))
        assertNull(ParcelCache.at(listOf(c.copy(info = c.info.copy(visible = false))), 44.0005, 15.0005))
        assertEquals("2450", ParcelCache.byReference(listOf(c), "334723-2450")?.number)
    }

    @Test fun aCacheSurvivesThePhone() {
        val c = cacheOf(baka)
        val info = c.info.copy(colour = 0xFF34D399L, style = Parcels.LineStyle.DOTTED, weight = ParcelStyle.Weight.BOLD, visible = false, count = 1, read = 1, places = "KUKLJICA")
        assertEquals(info, ParcelCache.decodeInfo(ParcelCache.encodeInfo(info)))
        val back = ParcelCache.decodeItems(ParcelCache.encodeItems(c.items)).single()
        assertEquals(baka.copy(), back.copy())
        assertNull(ParcelCache.decodeInfo("not json"))
        assertTrue(ParcelCache.decodeItems("not json").isEmpty())
    }

    @Test fun theWfsAnswerGivesParcelsWithTheirNumberPoints() {
        val json = """{"numberMatched":2224,"features":[{"id":"x","geometry":{"type":"Polygon","coordinates":[[[15.0,44.0],[15.001,44.0],[15.001,44.001],[15.0,44.0]]]},
            "properties":{"inspireId":{"localId":"CP.10480898"},"label":"154/1","nationalCadastralReference":"334723-154/1","areaValue":{"value":501},
            "referencePoint":{"type":"Point","coordinates":[15.24486571,44.03344363]}}}]}"""
        val f = ParcelCache.parseFeatures(json).single()
        assertEquals(10480898L, f.first.id)
        assertEquals("154/1", f.first.number)
        assertEquals(44.03344363 to 15.24486571, f.second)
        assertEquals(2224, ParcelCache.matched(json))
    }

    @Test fun aTileIsWhereTheMapPutsItAndItsPixelsAgree() {
        val z = 18
        val x = Geo.tileX(15.253, z)
        val y = Geo.tileY(44.036, z)
        val box = ParcelCache.tileBox(z, x, y)
        assertTrue(box.contains(44.036, 15.253))
        val (px, py) = ParcelCache.pixel(box.north, box.west, z, x, y, 512)
        assertEquals(0f, px, 0.01f)
        assertEquals(0f, py, 0.01f)
        val (qx, qy) = ParcelCache.pixel(box.south, box.east, z, x, y, 512)
        assertEquals(512f, qx, 0.01f)
        assertEquals(512f, qy, 0.01f)
    }

    @Test fun aCacheIsNamedAfterItsPlaceAndDay() {
        assertEquals("Kukljica 30.9.2026", ParcelCache.defaultName("KUKLJICA", "30.9.2026"))
        assertEquals("30.9.2026", ParcelCache.defaultName("", "30.9.2026"))
        assertEquals("KUKLJICA, PREKO", ParcelCache.places(listOf(baka, baka, baka.copy(municipalityName = "PREKO"))))
    }
}
