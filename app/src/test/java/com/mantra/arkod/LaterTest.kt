package com.mantra.arkod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fetch when available (row 38): the addresses and the button's words. Pure; no network. */
class LaterTest {

    private val record = "https://oss.uredjenazemlja.hr/oss/public/cad/parcel-info?parcelId=6436001"

    @Test fun theAnswerIsAskedOnTheSitesOwnAddressWithTheStatesAddressEncoded() {
        assertEquals(
            "https://arkod-layer.pages.dev/api/later/answer?url=https%3A%2F%2Foss.uredjenazemlja.hr%2Foss%2Fpublic%2Fcad%2Fparcel-info%3FparcelId%3D6436001",
            Later.answerUrl(record),
        )
        assertEquals("https://arkod-layer.pages.dev/api/later/want", Later.WANT)
    }

    @Test fun theWishIsTheStatesAddressAsJson() {
        // org.json on a phone writes "/" as "\/", on the JVM as "/": the meaning is what is compared
        assertEquals(record, org.json.JSONObject(Later.wantBody(record)).getString("url"))
    }

    @Test fun theButtonSaysWhatTheServerSaid() {
        assertTrue(Later.said(200, """{"id":"x","state":"wanted","tries":0}""").startsWith("wanted: the server asks the state every 10 minutes"))
        assertEquals("the server already has it: open the parcel again", Later.said(200, """{"state":"fetched"}"""))
        assertEquals("the server could not take it: GITHUB_TOKEN is not set on the Worker yet",
            Later.said(503, """{"error":"GITHUB_TOKEN is not set on the Worker yet"}"""))
        assertEquals("the server could not take it: the server answered 502", Later.said(502, "<html>"))
    }

    @Test fun whenItWasFetchedIsReadFromTheHeaderOrIsNow() {
        assertEquals(1790845800000L, Later.fetchedAt("2026-10-01T09:10:00.000Z"))
        assertEquals(42L, Later.fetchedAt(null, now = 42L))
        assertEquals(42L, Later.fetchedAt("not a date", now = 42L))
    }
}
