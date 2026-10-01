package com.mantra.arkod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * THE REAL STATE (1.10.2026). The app's own addresses and parsers against the state's services, asked
 * as the phone asks them (the app's User-Agent, no Origin). Kukljica, k.o. 334723: 1358/3 (id 6436001)
 * and 2449/2. Runs only with ARKOD_REAL=1, where the state can be reached; in CI every case is skipped,
 * because the WFS fails in spells (ORA-01000) and a build must not go red on the state's weather.
 *
 *     ARKOD_REAL=1 ./gradlew :app:testReleaseUnitTest --tests '*RealStateTest*'
 */
class RealStateTest {

    companion object {
        @BeforeClass @JvmStatic fun onlyWhereTheStateIsReachable() {
            assumeTrue("ARKOD_REAL=1 not set: the real state is not asked", System.getenv("ARKOD_REAL") == "1")
        }

        // Inside each parcel (the state's own outlines, kept by the family site).
        const val LAT_1358 = 44.01732073
        const val LON_1358 = 15.2494459

        fun get(url: String): Pair<Int, String> {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", Layers.USER_AGENT)
            }
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            println("  $code ${body.length} B  ${url.take(110)}")
            return code to body
        }
    }

    @Test fun aTapAtTheMiddleOf1358_3IsAnsweredByTheWms() {
        val (code, text) = get(Parcels.infoUrl(LAT_1358, LON_1358))
        assertEquals(200, code)
        val p = Parcels.parcelFromInfo(text)
        assertNotNull("GetFeatureInfo found no parcel: ${text.take(200)}", p)
        assertEquals("1358/3", p!!.number)
        assertEquals(6436001L, p.id)
        assertEquals("334723-1358/3", p.reference)
    }

    @Test fun theNumberIsFoundInKukljicaBySearch() {
        val (code, json) = get(Parcels.searchUrl("1358/3", "334723"))
        assertEquals(200, code)
        assertEquals(6436001L, Parcels.parseSearch(json, "1358/3"))
        val (_, json2) = get(Parcels.searchUrl("2449/2", "334723"))
        assertEquals(6438469L, Parcels.parseSearch(json2, "2449/2"))
    }

    @Test fun thePossessionSheetOf1358_3IsReadAsTheAppShowsIt() {
        val (code, json) = get(Parcels.recordUrl(6436001))
        assertEquals(200, code)
        val r = Parcels.parseRecord(json)
        assertEquals("1358/3", r.number)
        assertEquals("KUKLJICA", r.municipality)
        assertEquals("334723", r.municipalityNumber)
        assertEquals("516", r.areaM2)
        assertTrue(r.uses.all { it.name == "ŠUMA" })
        val sheet = r.sheets.single()
        assertEquals("1225", sheet.number)
        assertEquals(5, sheet.owners.size)
        assertTrue(sheet.owners.any { it.name.startsWith("BOŠKO DENIS") && it.share == "1/2" })
        assertTrue(sheet.owners.any { it.name.contains("Marinko") && it.share == "1/8" })
        val book = r.landBooks.single()
        assertEquals("250", book.unit)
        assertEquals("21261", book.bookId)
        assertTrue(Parcels.sheetRows(r).isNotEmpty())
    }

    @Test fun theOwnerSheetFolio250IsReadAsTheAppShowsIt() {
        val (code, json) = get(Parcels.folioUrl("21261", "250"))
        assertEquals(200, code)
        val f = Parcels.parseFolio(json)
        assertNotNull(f)
        assertEquals("250", f!!.unit)
        assertTrue("1358/3 on list A", f.parcels.any { it.startsWith("1358/3") })
        val names = f.shares.flatMap { s -> s.owners.map { it.name } }
        assertTrue(names.any { it.contains("Marinko") })
        assertTrue(names.any { it.contains("Svetko") })
        assertTrue(names.any { it.contains("Tomislav") })
        assertTrue(names.any { it.contains("DENIS") || it.contains("Denis") })
        assertTrue("Ivana's shares have passed to her sons (2017)", names.none { it.contains("Ivana") })
        assertTrue(Parcels.folioText(f).contains("Suvlasnički"))
    }

    @Test fun thePossessionSheetOf2449_2IsRead() {
        val (code, json) = get(Parcels.recordUrl(6438469))
        assertEquals(200, code)
        val r = Parcels.parseRecord(json)
        assertEquals("2449/2", r.number)
        assertTrue(r.sheets.any { s -> s.number == "442" && s.owners.any { it.name.contains("GOBIĆ") } })
    }

    @Test fun theOutlinesComeFromTheWfsOrItSaysWhyNot() {
        val (code, body) = get(Parcels.byReferenceUrl(listOf("334723-1358/3", "334723-2449/2")))
        if (code != 200) {
            // The state's database out of connections is the WFS's known weather, not the app's fault:
            // the app waits it out and keeps every outline it ever read.
            val why = Parcels.stateReason(body)
            println("  WFS: $code, ${why ?: body.take(160)}")
            assertNotNull("the WFS failed without a reason the app can show", why)
            return
        }
        val found = Parcels.parseParcels(body).associateBy { it.number }
        assertEquals(setOf("1358/3", "2449/2"), found.keys)
        assertTrue(Parcels.contains(found.getValue("1358/3").rings.first(), LAT_1358, LON_1358))
    }
}
