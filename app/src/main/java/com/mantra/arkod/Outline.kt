package com.mantra.arkod

/**
 * A PARCEL'S OUTLINE, READ OFF THE STATE'S OWN PICTURE (27.9.2026).
 *
 * Marko, 27.9.2026: *"When I click on any parcel, I want it to be highlighted automatically ... if
 * parcel is highlighted, second tap is opening the sheet."* A highlight is an outline, and the only
 * service that hands out outlines, the WFS, was measured again that morning at 14 and 30 seconds.
 * The WMS draws the same lines in a fifth of a second. So the picture round the finger is fetched,
 * the parcel under the finger is filled like a paint bucket until it meets the state's lines, and
 * the edge of the fill is walked round and thinned to corners: the outline, from the lines the
 * state drew, in well under a second.
 *
 * Pixels only, no Android: Test 1 fills and traces shapes it drew itself.
 */
object Outline {

    /** The 8 neighbours, clockwise from west. y grows downwards, as in a picture. */
    private val DX = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private val DY = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

    /** An open pixel near (x, y): a tap on a line or a letter still belongs to a parcel. */
    fun nearestOpen(wall: BooleanArray, w: Int, h: Int, x: Int, y: Int, radius: Int = 8): Pair<Int, Int>? {
        for (r in 0..radius) {
            for (dy in -r..r) for (dx in -r..r) {
                if (maxOf(Math.abs(dx), Math.abs(dy)) != r) continue
                val px = x + dx
                val py = y + dy
                if (px in 0 until w && py in 0 until h && !wall[py * w + px]) return px to py
            }
        }
        return null
    }

    /**
     * The paint bucket, four-connected so a diagonal line of single pixels still holds it. Null
     * when the fill reached the edge of the picture: the parcel is bigger than the picture, or its
     * lines have a gap, and either way this is not its outline.
     */
    fun fill(wall: BooleanArray, w: Int, h: Int, sx: Int, sy: Int): BooleanArray? {
        if (sx !in 0 until w || sy !in 0 until h || wall[sy * w + sx]) return null
        val inside = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        stack[top++] = sy * w + sx
        inside[sy * w + sx] = true
        while (top > 0) {
            val i = stack[--top]
            val x = i % w
            val y = i / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) return null
            val next = intArrayOf(i - 1, i + 1, i - w, i + w)
            for (n in next) {
                if (!inside[n] && !wall[n]) {
                    inside[n] = true
                    stack[top++] = n
                }
            }
        }
        return inside
    }

    /** Grown by r pixels, so the outline sits on the middle of the state's line, not inside it. */
    fun grow(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val across = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            for (d in -r..r) { val nx = x + d; if (nx in 0 until w) across[y * w + nx] = true }
        }
        val out = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (!across[y * w + x]) continue
            for (d in -r..r) { val ny = y + d; if (ny in 0 until h) out[ny * w + x] = true }
        }
        return out
    }

    /**
     * The outer edge, walked clockwise by Moore's neighbour tracing from the topmost, leftmost
     * pixel. Holes, which are the parcel's own number printed inside it, are never visited.
     */
    fun trace(mask: BooleanArray, w: Int, h: Int): List<Pair<Int, Int>> {
        val first = mask.indexOfFirst { it }
        if (first < 0) return emptyList()
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]
        val sx = first % w
        val sy = first / w
        val path = ArrayList<Pair<Int, Int>>()
        path.add(sx to sy)
        var px = sx
        var py = sy
        var back = 0 // entered from the west, which is outside: it is the leftmost of the top row
        val limit = 4 * (w + h) * 8
        var steps = 0
        while (steps++ < limit) {
            var moved = false
            for (k in 1..8) {
                val d = (back + k) % 8
                val nx = px + DX[d]
                val ny = py + DY[d]
                if (on(nx, ny)) {
                    // the neighbour checked just before this one is outside: it is where we came from
                    val prev = (d + 7) % 8
                    val bx = px + DX[prev]
                    val by = py + DY[prev]
                    px = nx
                    py = ny
                    back = (0 until 8).first { px + DX[it] == bx && py + DY[it] == by }
                    moved = true
                    break
                }
            }
            if (!moved) return path // a single pixel
            if (px == sx && py == sy) return path
            path.add(px to py)
        }
        return path
    }

    /** Douglas–Peucker on a closed ring: the corners, and the straight runs between them dropped. */
    fun simplify(ring: List<Pair<Double, Double>>, tolerance: Double): List<Pair<Double, Double>> {
        if (ring.size < 4) return ring
        val a = ring.first()
        val far = ring.indices.maxByOrNull { dist2(ring[it], a) } ?: return ring
        val one = dp(ring.subList(0, far + 1), tolerance)
        val two = dp(ring.subList(far, ring.size) + listOf(a), tolerance)
        return one.dropLast(1) + two.dropLast(1)
    }

    private fun dp(line: List<Pair<Double, Double>>, tol: Double): List<Pair<Double, Double>> {
        if (line.size < 3) return line
        val a = line.first()
        val b = line.last()
        var worst = -1.0
        var at = 0
        for (i in 1 until line.size - 1) {
            val d = offLine(line[i], a, b)
            if (d > worst) { worst = d; at = i }
        }
        if (worst <= tol) return listOf(a, b)
        return dp(line.subList(0, at + 1), tol).dropLast(1) + dp(line.subList(at, line.size), tol)
    }

    private fun dist2(p: Pair<Double, Double>, q: Pair<Double, Double>): Double {
        val dx = p.first - q.first
        val dy = p.second - q.second
        return dx * dx + dy * dy
    }

    private fun offLine(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val len = Math.sqrt(dist2(a, b))
        if (len == 0.0) return Math.sqrt(dist2(p, a))
        return Math.abs((b.first - a.first) * (a.second - p.second) - (a.first - p.first) * (b.second - a.second)) / len
    }

    // --- the picture on the ground ----------------------------------------------------------------

    private const val HALF_WORLD = 20037508.342789244

    fun mercX(lon: Double) = lon * HALF_WORLD / 180.0

    fun mercY(lat: Double) =
        Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) * HALF_WORLD / Math.PI

    fun lonOf(x: Double) = x / HALF_WORLD * 180.0

    fun latOf(y: Double) = Math.toDegrees(2 * Math.atan(Math.exp(y / HALF_WORLD * Math.PI)) - Math.PI / 2)

    /**
     * A square picture round a point, [side] metres of ground: west, south, east, north in Web
     * Mercator. Mercator metres are stretched by 1/cos(latitude), so the ground is asked for.
     */
    fun box(lat: Double, lon: Double, side: Double): DoubleArray {
        val half = side / 2 / Math.cos(Math.toRadians(lat))
        val x = mercX(lon)
        val y = mercY(lat)
        return doubleArrayOf(x - half, y - half, x + half, y + half)
    }

    /**
     * THE WHOLE WAY, from the picture's walls to the corners of the parcel in latitude and
     * longitude; null when the fill escaped, so the caller can ask for a bigger picture.
     */
    fun parcelAt(wall: BooleanArray, w: Int, h: Int, box: DoubleArray, lat: Double, lon: Double): List<Pair<Double, Double>>? {
        val resX = (box[2] - box[0]) / w
        val resY = (box[3] - box[1]) / h
        val tx = ((mercX(lon) - box[0]) / resX).toInt()
        val ty = ((box[3] - mercY(lat)) / resY).toInt()
        val (sx, sy) = nearestOpen(wall, w, h, tx, ty) ?: return null
        val inside = fill(wall, w, h, sx, sy) ?: return null
        val edge = trace(grow(inside, w, h, 1), w, h)
        if (edge.size < 4) return null
        val corners = simplify(edge.map { (x, y) -> x.toDouble() to y.toDouble() }, 1.2)
        if (corners.size < 3) return null
        return corners.map { (x, y) ->
            latOf(box[3] - (y + 0.5) * resY) to lonOf(box[0] + (x + 0.5) * resX)
        }
    }
}
