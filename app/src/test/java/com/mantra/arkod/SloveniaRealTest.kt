package com.mantra.arkod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slovenia against GURS's REAL answers (11.10.2026): fetched by the local Claude Code on Marko's phone
 * (tests/slovenia/RESULTS.md, round 1), kept untouched in src/test/resources/slovenia. Petrina 2, Kostel:
 * the house on parcel *98/5, k.o. 1613 KUŽELJ.
 */
class SloveniaRealTest {

    private fun gurs(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("slovenia/$name.json")!!.bufferedReader().use { it.readText() }

    private val lat = 45.46479668
    private val lon = 14.8531182

    @Test fun aTapOnPetrina2SelectsTheHouseParcelStarNinetyEightFiveOfFive() {
        val p = Slovenia.parcelAt(gurs("petrina2_parcel"), lat, lon)
        assertNotNull(p)
        assertEquals("*98/5", p!!.number)
        assertEquals("SI1613-*98/5", p.reference)
        assertEquals(283, p.areaM2)
        assertEquals(-100100000271338102L, p.id)
        assertTrue("the point is inside the outline read", p.rings.any { Parcels.contains(it, lat, lon) })
        // five parcels touch the box; the real answer gives longitude first
        assertEquals(5, Slovenia.parseParcels(gurs("petrina2_parcel")).size)
    }

    @Test fun petrinaIsInKuzeljNotInKostel() {
        assertEquals("SI1613" to "KUŽELJ", Slovenia.parseMunicipality(gurs("petrina2_ko")))
        assertEquals("SI1725" to "AJDOVŠČINA", Slovenia.parseMunicipality(gurs("ko_ljubljana")))
    }

    @Test fun theSheetIsCleanTheMunicipalityBareAndTheNoiseGone() {
        val r = Slovenia.parseRecord(gurs("petrina2_parcel"), "SI1613-*98/5")!!
        assertEquals("*98/5", r.number)
        assertEquals("KUŽELJ", r.municipality)  // "1613 KUŽELJ" in GURS's answer
        assertEquals("283", r.areaM2)
        val labels = r.uses.map { it.name }
        assertTrue(labels.toString(), labels.contains("upravni statusi"))
        assertFalse(labels.any { it.startsWith("eid") || it == "featureid" || it.endsWith(" en") || it.endsWith(" it") || it.endsWith(" hu") || it == "e cen" })
        assertTrue("a field and its name are one row", labels.count { it.startsWith("upravni status") } <= 2)
        assertEquals("100100000271338102", Slovenia.eidOf(gurs("petrina2_parcel"), "*98/5"))
    }

    @Test fun landUsePlannedUseAndSoilRatingComeOnTheUseTab() {
        val use = Slovenia.landUseRows(gurs("petrina2_dejanske_rabe"))
        assertEquals(listOf("poseljena zemljišča, tloris stavbe" to "54.8 %", "poseljena zemljišča" to "45.2 %"), use.map { it.main to it.side })
        assertEquals("površine podeželskega naselja" to "100 %", Slovenia.plannedUseRows(gurs("petrina2_namenske_rabe")).single().let { it.main to it.side })
        assertEquals("boniteta 55" to "278 m²", Slovenia.ratingRows(gurs("petrina2_bonitete")).single().let { it.main to it.side })
        assertTrue((use + Slovenia.ratingRows(gurs("petrina2_bonitete"))).all { Parcels.tabOf(it) == Parcels.Tab.USE })
    }

    @Test fun theHouseIsBuilding221Of1910AndItsAddressIsPetrina2() {
        val parcel = Slovenia.parcelAt(gurs("petrina2_parcel"), lat, lon)!!
        val b = Slovenia.buildings(gurs("petrina2_stavbe"), parcel.rings).single()
        assertEquals("221", b.number)
        assertEquals("100200000250931504", b.eid)
        assertEquals("izgrađena 1910", b.row.side)
        assertTrue(b.row.under, b.row.under.contains("samostoječa stavba") && b.row.under.contains("krov obnovljen 1972"))
        val (parts, address) = Slovenia.partRows(gurs("petrina2_deli_stavb"), b.number)
        assertEquals("Kostel, Petrina 2", address)
        assertEquals("dio 1: stanovanje v enostanovanjski stavbi" to "118.8 m²", parts.single().let { it.main to it.side })
    }

    @Test fun theWholeSheetOfPetrina2() {
        val parcel = Slovenia.parcelAt(gurs("petrina2_parcel"), lat, lon)!!
        val b = Slovenia.buildings(gurs("petrina2_stavbe"), parcel.rings).single()
        val (parts, address) = Slovenia.partRows(gurs("petrina2_deli_stavb"), b.number)
        val r = Slovenia.withMore(
            Slovenia.parseRecord(gurs("petrina2_parcel"), parcel.reference)!!,
            Slovenia.landUseRows(gurs("petrina2_dejanske_rabe")) + listOf(b.row) + parts +
                Slovenia.publicOwnerRows(gurs("petrina2_javni_lastniki_gfi")),
            address,
        )
        assertEquals("Kostel, Petrina 2", r.address)
        val rows = Parcels.sheetRows(r)
        assertTrue(rows.first().under.contains("Kostel, Petrina 2"))
        // no public owner for Petrina 2: the owner tab says where a private owner is read
        assertEquals(listOf(Slovenia.OWNERS_NOTE), rows.filter { Parcels.tabOf(it) == Parcels.Tab.OWNER }.map { it.under })
        val text = Parcels.toText(parcel, r, "11.10.2026.")
        assertTrue(text.contains("STAVBA 221"))
        assertTrue(text.contains("Kostel, Petrina 2"))
        assertEquals("cestica SI1613-z98_5.txt", Parcels.textFileName(parcel))
    }

    @Test fun aNumberTypedWithoutTheStarFindsTheBuildingParcel() {
        assertEquals(listOf("98/5", "*98/5"), Slovenia.numberVariants("98/5"))
        assertEquals(listOf("98/5", "*98/5"), Slovenia.numberVariants("*98/5"))
        val u = Slovenia.byNumbersUrl("SI1613", listOf("98/5"))
        assertTrue(u, u.contains("ST_PARCELE%3D%2798%2F5%27") && u.contains("ST_PARCELE%3D%27*98%2F5%27"))
        val s = Slovenia.suggestUrl("SI1613", "98")
        assertTrue(s, s.contains("LIKE%20%2798%25%27") && s.contains("LIKE%20%27*98%25%27"))
        val hits = Slovenia.parseSuggestions(gurs("petrina2_suggest_star98"), "KUŽELJ")
        assertTrue(hits.map { it.title }.toString(), hits.any { it.title == "*98/5" && it.ref == "SI1613-*98/5" })
    }

    @Test fun theTilesCarryTheLinesAndTheNumbers() {
        val u = Slovenia.tileUrl(16, 35408, 23301)
        assertTrue(u.contains("LAYERS=SI.GURS.KN:PARCELE,SI.GURS.KN:PARCELE&STYLES=nep_kn_parcele,nep_kn_parcele_lbl"))
        // GURS's green: the restyle keeps the alpha and draws our ink
        val green = intArrayOf(0xFF00FF00.toInt(), 0)
        val out = ParcelStyle.restyle(green, 2, 1, Parcels.INK_DARK, ParcelStyle.Lines())
        assertEquals(Parcels.INK_DARK.toInt() and 0xFFFFFF, out[0] and 0xFFFFFF)
        assertEquals(0, out[1])
    }

    @Test fun ljubljanaKongresniTrgIs3200SlashOneInAjdovscina() {
        val p = Slovenia.parcelAt(gurs("tap_ljubljana"), 46.0505, 14.5036)!!
        assertEquals("SI1725-3200/1", p.reference)
        assertEquals(10302, p.areaM2)
    }
}
