package com.mantra.arkod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slovenia (11.10.2026, v23): where GURS is asked, how its answers are read, and how a Slovenian parcel
 * is kept apart from a Croatian one. The answers below are GeoServer's GeoJSON in the shape GURS's WFS
 * documents (KO_ID, ST_PARCELE, POVRSINA); eprostor.gov.si could not be reached from the cloud, so the
 * first real answer is TESTING.md's SLO checks on a phone.
 */
class SloveniaTest {

    private val koper = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","id":"PARCELE.123","geometry":{"type":"Polygon","coordinates":[[
            [13.7290,45.5480],[13.7300,45.5480],[13.7300,45.5490],[13.7290,45.5490],[13.7290,45.5480]]]},
           "properties":{"EID_PARCELA":23001234567,"KO_ID":2605,"ST_PARCELE":"1234/5","POVRSINA":812.0,"BONITETA":34}},
          {"type":"Feature","id":"PARCELE.124","geometry":{"type":"Polygon","coordinates":[[
            [13.7300,45.5480],[13.7310,45.5480],[13.7310,45.5490],[13.7300,45.5490],[13.7300,45.5480]]]},
           "properties":{"EID_PARCELA":23001234568,"KO_ID":2605,"ST_PARCELE":"1234/6","POVRSINA":400}}
        ]}
    """.trimIndent()

    @Test fun ljubljanaKoperAndMariborAreSloveniaZagrebRijekaAndSplitAreNot() {
        assertTrue(Slovenia.inside(46.0505, 14.5036))  // Ljubljana
        assertTrue(Slovenia.inside(45.5480, 13.7300))  // Koper
        assertTrue(Slovenia.inside(46.5547, 15.6459))  // Maribor
        assertTrue(Slovenia.inside(45.5280, 13.5680))  // Piran
        assertTrue(Slovenia.inside(46.6600, 16.1600))  // Murska Sobota
        assertFalse(Slovenia.inside(45.8150, 15.9819)) // Zagreb
        assertFalse(Slovenia.inside(45.3271, 14.4422)) // Rijeka
        assertFalse(Slovenia.inside(43.5081, 16.4402)) // Split
        assertFalse(Slovenia.inside(44.0173, 15.2494)) // Kukljica
        assertFalse(Slovenia.inside(46.6249, 14.3050)) // Klagenfurt
    }

    @Test fun aTileIsAskedOfTheStatesWhoseGroundItCovers() {
        fun states(lat: Double, lon: Double, z: Int) = Slovenia.statesFor(z, Geo.tileX(lon, z), Geo.tileY(lat, z))
        assertEquals(false to true, states(46.0505, 14.5036, 16))  // Ljubljana: Slovenia only
        assertEquals(true to false, states(44.0173, 15.2494, 16))  // Kukljica: Croatia only
        assertEquals(true to true, states(45.4800, 13.6000, 16))   // Dragonja, on the border: both
        assertEquals(true to false, states(45.8150, 15.9819, 16))  // Zagreb: Croatia only
    }

    /**
     * Marko, 11.10.2026: *"a place in slovenia, in slovenia called petrina, and the street is petrina 2"*.
     * Petrina (Kostel) lies on the Kolpa, across the bridge from Brod na Kupi in Croatia: a tap there asks
     * Croatia first and then Slovenia, and its tiles are drawn from both states.
     */
    @Test fun petrinaOnTheKolpaIsSloveniaAndIsDrawnFromBothStates() {
        val lat = 45.4650
        val lon = 14.8529
        assertTrue(Slovenia.inside(lat, lon))
        assertTrue("Croatia is just across the river, so it is asked too", Slovenia.mayBeCroatia(lat, lon))
        for (z in 14..18) assertEquals(true to true, Slovenia.statesFor(z, Geo.tileX(lon, z), Geo.tileY(lat, z)))
        // a house there, as GURS would answer for it: its sheet is GURS's attributes, and no owner
        val house = koper.replace("2605", "1621")
        val r = Slovenia.parseRecord(house, "SI1621-1234/5", "Kostel")!!
        assertEquals("SI1621", r.municipalityNumber)
        assertTrue(r.sheets.isEmpty())
        assertTrue(Parcels.sheetRows(r).filter { Parcels.tabOf(it) == Parcels.Tab.OWNER }.all { it.under == Slovenia.OWNERS_NOTE })
    }

    @Test fun aSlovenianReferenceIsKeptApartFromACroatianOne() {
        assertEquals("SI2605-1234/5", Slovenia.reference("2605", "1234/5"))
        assertTrue(Slovenia.isReference("SI2605-1234/5"))
        assertFalse(Slovenia.isReference("334723-2449/2"))
        assertFalse(Slovenia.isReference("SIX-1"))
        assertEquals("2605", Slovenia.koId("SI2605"))
        assertEquals("334723", Slovenia.koId("334723"))
        assertEquals("SI2605", Parcels.Parcel(-1, "1234/5", "SI2605-1234/5", null, emptyList()).municipality)
    }

    @Test fun aTapReadsTheParcelUnderTheFingerWithItsOutline() {
        val p = Slovenia.parcelAt(koper, 45.5485, 13.7305)
        assertNotNull(p)
        assertEquals("1234/6", p!!.number)
        assertEquals("SI2605-1234/6", p.reference)
        assertEquals(-23001234568L, p.id)
        assertEquals(400, p.areaM2)
        // longitude first in GeoJSON; read as (latitude, longitude)
        assertEquals(45.5480 to 13.7300, p.rings[0][0])
        assertNull(Slovenia.parcelAt("""{"type":"FeatureCollection","features":[]}""", 45.5, 13.7))
        assertNull(Slovenia.parcelAt("not json", 45.5, 13.7))
    }

    @Test fun theAxisOrderIsReadFromTheNumbersThemselves() {
        val latFirst = koper.replace("[13.7290,45.5480]", "[45.5480,13.7290]")
        val p = Slovenia.parseParcels(latFirst).first()
        assertEquals(45.5480 to 13.7290, p.rings[0][0])
        assertEquals(45.5480 to 13.7300, p.rings[0][1])
    }

    @Test fun otherSpellingsOfTheAttributesAreReadToo() {
        val other = """{"features":[{"id":"x.77","geometry":null,"properties":{"ko_id":"1722.0","st_parcele":"55","povrsina":"1200.4"}}]}"""
        val p = Slovenia.parseParcels(other).single()
        assertEquals("SI1722-55", p.reference)
        assertEquals(-77L, p.id)
        assertEquals(1200, p.areaM2)
    }

    @Test fun theSheetShowsWhatGursGivesAndSaysWhereTheOwnersAre() {
        val r = Slovenia.parseRecord(koper, "SI2605-1234/5", "Koper")!!
        assertEquals("1234/5", r.number)
        assertEquals("KOPER", r.municipality)
        assertEquals("SI2605", r.municipalityNumber)
        assertEquals("812", r.areaM2)
        assertTrue(r.sheets.isEmpty())
        val rows = Parcels.sheetRows(r)
        assertTrue(rows.any { it.main == "boniteta" && it.side == "34" })
        assertEquals(Parcels.Tab.USE, Parcels.tabOf(rows.first()))
        assertTrue(rows.any { Parcels.tabOf(it) == Parcels.Tab.POSSESSION && it.under == Slovenia.OWNERS_NOTE })
        assertTrue(rows.any { Parcels.tabOf(it) == Parcels.Tab.OWNER && it.under == Slovenia.OWNERS_NOTE })
        val text = Parcels.toText(Parcels.Parcel(-1, "1234/5", "SI2605-1234/5", 812, emptyList()), r, "11.10.2026.")
        assertTrue(text.contains("GURS"))
        assertFalse(text.contains("posjedovni list "))
    }

    @Test fun theQuestionsGoToGursAndItsLightIsItsOwn() {
        val u = Slovenia.byReferenceUrl(listOf("SI2605-1234/5", "334723-1", "SI1722-55"))
        assertTrue(u.startsWith(Slovenia.WFS))
        assertTrue(u.contains("TYPENAMES=SI.GURS.KN:PARCELE"))
        assertTrue(u.contains("KO_ID%3D2605%20AND%20ST_PARCELE%3D%271234%2F5%27"))
        assertFalse("a Croatian reference is never sent to Slovenia", u.contains("334723"))
        assertTrue(Slovenia.tileUrl(16, 35000, 23000).contains("LAYERS=SI.GURS.KN:PARCELE"))
        assertEquals(Services.Service.GURS, Services.of(Slovenia.tileUrl(16, 1, 1)))
        assertEquals(Services.Service.GURS, Services.of(Slovenia.atUrl(46.05, 14.50)))
        assertEquals(Services.Service.WMS, Services.of(Parcels.tileUrl(16, 1, 1)))
    }

    @Test fun aFilledPictureBecomesLinesAndALinePictureIsLeftAlone() {
        val w = 4
        val h = 4
        val lines = IntArray(w * h) { if (it % w == 0) 0xFF000000.toInt() else 0 }
        assertTrue(Slovenia.linesOnly(lines, w, h) === lines)
        // left half one fill, right half another: only the seam between them is kept
        val filled = IntArray(w * h) { if (it % w < 2) 0xFFCCCCCC.toInt() else 0xFF336699.toInt() }
        val out = Slovenia.linesOnly(filled, w, h)
        assertTrue((0 until h).all { y -> (out[y * w + 1] ushr 24) == 0xFF })
        assertTrue((0 until h).all { y -> out[y * w + 0] == 0 && out[y * w + 3] == 0 })
    }

    /** Marko, 11.10.2026: "In the settings, please switch between Slovenia and Croatia. Make a switch." (v25) */
    @Test fun theCountrySwitchDecidesWhoseServicesAreAskedAndCroatiaIsTheDefault() {
        assertEquals(Slovenia.Country.CROATIA, Slovenia.Country.of(null))
        assertEquals(Slovenia.Country.CROATIA, Slovenia.Country.of("nonsense"))
        assertEquals(Slovenia.Country.SLOVENIA, Slovenia.Country.of("SLOVENIA"))
        val hr = Slovenia.Country.CROATIA
        val si = Slovenia.Country.SLOVENIA
        assertTrue(Slovenia.serves(Services.Service.WMS, hr) && Slovenia.serves(Services.Service.ZK, hr))
        assertFalse(Slovenia.serves(Services.Service.GURS, hr))
        assertTrue(Slovenia.serves(Services.Service.GURS, si))
        assertFalse(Slovenia.serves(Services.Service.WMS, si) || Slovenia.serves(Services.Service.OSS, si))
        assertTrue(Slovenia.serves(Services.Service.OSM, si) && Slovenia.serves(Services.Service.GOOGLE, hr))
        assertTrue("the map goes into the country chosen", Slovenia.inside(si.lat, si.lon))
        assertFalse(Slovenia.inside(hr.lat, hr.lon))
    }

    /** Marko, 11.10.2026, a screenshot: "This is slovenia mode and search results coming from Croatia" (v26). */
    @Test fun thePlaceSearchIsHeldToTheChosenCountry() {
        val si = Slovenia.Country.SLOVENIA
        val hr = Slovenia.Country.CROATIA
        assertEquals("si" to "sl", si.region to si.language)
        assertEquals("hr" to "hr", hr.region to hr.language)
        assertFalse(Slovenia.inCountry("ul. Mire Petrina 2, 10090, Zagreb, Hrvatska", si))
        assertFalse(Slovenia.inCountry("ul. Mire Petrina 2, 10090 Zagreb, Croatia", si))
        assertTrue(Slovenia.inCountry("Petrina 2, 1336 Kostel, Slovenija", si))
        assertTrue(Slovenia.inCountry("Petrina 2, 1336 Vas", si))
        assertFalse(Slovenia.inCountry("Petrina 2, 1336 Kostel, Slovenia", hr))
        assertTrue(Slovenia.inCountry("ul. Mire Petrina 2, 10090, Zagreb, Hrvatska", hr))
        assertFalse(Slovenia.inCountry("Via Roma 1, Trieste, Italia", si))
        // Petrina is inside Slovenia's search box; Zagreb is too (a box is a box), so its places go by their address
        val b = si.box
        assertTrue(45.4648 in b[0]..b[2] && 14.8531 in b[1]..b[3])
        assertFalse(45.4648 in hr.box[0]..hr.box[2] && 19.6 in hr.box[1]..hr.box[3])
    }
}
