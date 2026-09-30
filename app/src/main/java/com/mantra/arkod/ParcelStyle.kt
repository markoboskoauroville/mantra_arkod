package com.mantra.arkod

/**
 * HOW THE STATE'S PARCEL LINES ARE DRAWN (30.9.2026, v5).
 *
 * Marko: *"different styles of lines and transparency of lines ... and thickness of lines, because
 * lines are covering the map too much ... different styles and different colors."* The state sends
 * its parcels as pictures, black lines about four pixels wide on a 512 px tile (measured 30.9.2026),
 * so the colour, the transparency and the weight are all made on the phone from the picture's own
 * alpha: nothing is asked of the state twice. A dashed or dotted line cannot be made from a
 * picture; that is what the parcel caches are for ([ParcelCache]): there the lines are ours.
 *
 * Pure: no Android, so Test 1 runs every line of it.
 */
object ParcelStyle {

    /**
     * FINE keeps the core of each line whole and fades its edges to a third, which halves the
     * width and keeps the numbers readable (hard erosion broke lines apart and erased the numbers:
     * tried on a Kukljica tile, 30.9.2026). BOLD grows each line by a pixel all round.
     */
    enum class Weight(val word: String) { FINE("Fine"), NORMAL("Normal"), BOLD("Bold") }

    /** The transparencies offered, as the percentage of the state's ink that is kept. */
    val OPACITIES = listOf(20, 35, 50, 62, 80, 100)

    /**
     * The state's lines: a colour (null follows the map: dark on pale maps, sand on photographs),
     * how much of it is kept, and the weight.
     */
    data class Lines(val colour: Long? = null, val opacity: Int = 62, val weight: Weight = Weight.NORMAL) {
        fun ink(auto: Long): Long = colour ?: auto
    }

    /** The name a restyled tile is kept under: a new style is a new set of tiles, never a stale one. */
    fun key(ink: Long, lines: Lines): String =
        java.lang.Long.toHexString(ink) + "-" + lines.opacity + "-" + lines.weight.name.lowercase()

    fun encode(lines: Lines): String =
        listOf(lines.colour?.let { java.lang.Long.toHexString(it) } ?: "auto", lines.opacity.toString(), lines.weight.name)
            .joinToString("|")

    fun decode(text: String?): Lines {
        val f = text.orEmpty().split('|')
        if (f.size < 3) return Lines()
        return Lines(
            colour = f[0].takeIf { it != "auto" }?.toLongOrNull(16),
            opacity = f[1].toIntOrNull()?.coerceIn(5, 100) ?: 62,
            weight = Weight.entries.firstOrNull { it.name == f[2] } ?: Weight.NORMAL,
        )
    }

    /**
     * ONE TILE OF THE STATE'S PICTURE, RESTYLED: every pixel's alpha thinned or grown by the weight,
     * scaled by the opacity, and put in the ink. Transparent pixels stay transparent.
     */
    fun restyle(pixels: IntArray, w: Int, h: Int, ink: Long, lines: Lines): IntArray {
        val alpha = IntArray(pixels.size) { (pixels[it] ushr 24) and 0xFF }
        val shaped = when (lines.weight) {
            Weight.NORMAL -> alpha
            Weight.FINE -> fine(alpha, w, h)
            Weight.BOLD -> bold(alpha, w, h)
        }
        val rgb = ink.toInt() and 0x00FFFFFF
        return IntArray(pixels.size) { i ->
            val a = (shaped[i] * lines.opacity / 100).coerceIn(0, 255)
            if (a == 0) 0 else (a shl 24) or rgb
        }
    }

    /** The core stays; a pixel with an empty neighbour (up, down, left, right) keeps a third. */
    fun fine(alpha: IntArray, w: Int, h: Int): IntArray = IntArray(alpha.size) { i ->
        val a = alpha[i]
        if (a == 0) return@IntArray 0
        val x = i % w
        val y = i / w
        val edge = (x > 0 && alpha[i - 1] == 0) || (x < w - 1 && alpha[i + 1] == 0) ||
            (y > 0 && alpha[i - w] == 0) || (y < h - 1 && alpha[i + w] == 0)
        if (edge) a * 35 / 100 else a
    }

    /** Each pixel takes the strongest alpha of the nine round it. */
    fun bold(alpha: IntArray, w: Int, h: Int): IntArray = IntArray(alpha.size) { i ->
        val x = i % w
        val y = i / w
        var best = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until w && yy in 0 until h) {
                val a = alpha[yy * w + xx]
                if (a > best) best = a
            }
        }
        best
    }
}
