package com.mantra.arkod

/**
 * THE MAP, WHICHEVER OF THE THREE IS UP (Mantra ARKOD, 29.9.2026).
 *
 * One engine draws all three: VTM reads the Croatia file, and fetches OpenStreetMap's and
 * Google's tiles, with the cadastre over each. Everything the keys do goes through here, so no
 * call site names the engine.
 */
object Canvases {

    private val vtm: VtmCanvas? get() = CanvasHolder.canvas

    fun zoomIn() {
        vtm?.zoomIn()
    }

    fun zoomOut() {
        vtm?.zoomOut()
    }

    fun centreOn(fix: Fix) {
        vtm?.centreOn(fix)
    }

    /** The middle of the screen. */
    fun centre(): Pair<Double, Double>? = vtm?.centre()

    fun currentZoom(): Int = vtm?.currentZoom() ?: 0

    fun setMapRotation(degrees: Float) {
        vtm?.setMapRotation(degrees)
    }

    fun mapRotationDeg(): Float = vtm?.mapRotationDeg() ?: 0f

    /** A saved walk, remembered here so a new canvas draws it again. */
    fun drawSavedTrack(points: List<Fix>, colour: Long) {
        Shown.track = points to colour
        vtm?.showSavedTrack(points, colour)
    }

    /** The walk being recorded. */
    fun drawTrack(points: List<Fix>) {
        vtm?.drawTrack(points)
    }

    /** Everything a new canvas must inherit: the walk, the saved track, his position. */
    fun handOver(live: List<Fix>, fix: Fix?, follow: Boolean) {
        Shown.track?.let { drawSavedTrack(it.first, it.second) }
        drawTrack(live)
        drawPosition(fix)
        if (follow && fix != null) centreOn(fix)
    }

    fun clearSavedTrack() {
        Shown.forget()
        vtm?.clearSavedTrack()
    }

    fun setHeading(heading: Double) {
        vtm?.setHeading(heading)
    }

    fun drawPosition(fix: Fix?) {
        vtm?.drawPosition(fix)
    }

    /** The cadastre re-inked, or my parcels changed: the map told (27.9.2026). */
    fun refreshParcels() {
        vtm?.refreshCadastre()
        vtm?.drawMarks(ParcelsShown.drawn())
        vtm?.drawPin(ParcelsShown.pin)
    }

    /** The WMS is back (v19): the failed ARKOD tiles are asked again. */
    fun reloadCadastre() {
        vtm?.reloadCadastre()
    }

    /** A found parcel: the map goes there. */
    fun goTo(lat: Double, lon: Double, zoom: Int) {
        vtm?.goTo(lat, lon, zoom)
    }

    fun emptyHere(): String? = vtm?.emptyHere()

    fun diagnose(): String = vtm?.diagnose() ?: "karta još nije spremna"

    fun ready(): Boolean = vtm != null
}

/** What is on the map, kept apart from the canvas drawing it. */
object Shown {
    /** A saved walk he asked to see. */
    @Volatile
    var track: Pair<List<Fix>, Long>? = null

    fun forget() {
        track = null
    }
}
