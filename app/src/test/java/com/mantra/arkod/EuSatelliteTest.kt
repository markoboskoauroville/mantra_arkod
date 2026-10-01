package com.mantra.arkod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google refuses satellite in the EU (1.10.2026): the refusal is recognised, the aerial photograph addressed right. */
class EuSatelliteTest {

    @Test fun googlesEuRefusalIsRecognisedInItsOwnWords() {
        assertTrue(Layers.eeaRefusal("Google: Your request cannot be served because satellite tiles and 3D tiles are not available for your account and region. Learn more here: https://developers.google.com/maps/comms/eea/map-tiles."))
        assertFalse(Layers.eeaRefusal("Google: API key not valid. Please pass a valid API key."))
        assertFalse(Layers.eeaRefusal("Google: Map Tiles API has not been used in project 342783832558 before or it is disabled"))
        assertFalse(Layers.eeaRefusal(null))
    }

    @Test fun theAerialPhotographIsAskedRowBeforeColumnAsEsriWantsIt() {
        assertEquals(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile" to "/{Z}/{Y}/{X}",
            Layers.tilePattern(Layers.AERIAL),
        )
        assertEquals("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/16/23821/35543",
            Layers.tileUrl(Layers.AERIAL, 16, 35543, 23821))
        assertFalse("never offered as a choice", Layers.ALL.contains(Layers.AERIAL))
    }

    @Test fun googlesTilesStillCarryTheSessionAndTheKey() {
        val (base, path) = Layers.tilePattern(Layers.GOOGLE_SATELLITE, "S1", "K1")!!
        assertEquals("https://tile.googleapis.com/v1/2dtiles", base)
        assertEquals("/{Z}/{X}/{Y}?session=S1&key=K1", path)
    }
}
