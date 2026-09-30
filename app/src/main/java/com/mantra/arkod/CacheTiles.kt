package com.mantra.arkod

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * OUR OWN LINES WHERE A CACHE IS (30.9.2026, v5). Marko: *"when I ask you different style, you need
 * to redraw over their layer, remove their layer, and keep our own style for that area."*
 *
 * A tile of the ARKOD layer that meets a cache's box: the state's picture with everything inside
 * the box cleared, and the cache's parcels drawn there in its colour, weight and line, with their
 * numbers, from the outlines on the phone. Outside the box the state's picture stays as it was.
 */
object CacheTiles {

    private const val SIZE = Parcels.TILE_PX

    fun draw(state: ByteArray?, caches: List<ParcelCache.Cache>, z: Int, x: Int, y: Int, opacity: Int): ByteArray {
        val bitmap = state?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.copy(Bitmap.Config.ARGB_8888, true)
            ?: Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val size = bitmap.width
        val canvas = Canvas(bitmap)
        val here = ParcelCache.tileBox(z, x, y)
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        // 1. The state's lines out of every cache's box.
        caches.forEach { c ->
            val (l, t) = ParcelCache.pixel(c.info.box.north, c.info.box.west, z, x, y, size)
            val (r, b) = ParcelCache.pixel(c.info.box.south, c.info.box.east, z, x, y, size)
            canvas.drawRect(RectF(l, t, r, b), clear)
        }

        // 2. The cache's parcels, in its own look.
        val scale = size / 512f
        caches.forEach { c ->
            val alpha = (255 * opacity / 100).coerceIn(40, 255)
            val colour = (alpha shl 24) or (c.info.colour.toInt() and 0x00FFFFFF)
            val width = scale * when (c.info.weight) {
                ParcelStyle.Weight.FINE -> 2f
                ParcelStyle.Weight.NORMAL -> 3.5f
                ParcelStyle.Weight.BOLD -> 6f
            }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = width
                this.color = colour
                strokeJoin = Paint.Join.ROUND
                when (c.info.style) {
                    Parcels.LineStyle.SOLID -> Unit
                    Parcels.LineStyle.DASHED -> pathEffect = DashPathEffect(floatArrayOf(14f * scale, 8f * scale), 0f)
                    Parcels.LineStyle.DOTTED -> {
                        strokeCap = Paint.Cap.ROUND
                        pathEffect = DashPathEffect(floatArrayOf(0.1f, width * 2.2f), 0f)
                    }
                }
            }
            val words = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = colour or (0xFF shl 24)
                textSize = scale * if (z >= 18) 22f else 18f
                typeface = Typeface.SANS_SERIF
                textAlign = Paint.Align.CENTER
            }
            val inside = c.items.filter { it.rings.isNotEmpty() && it.box.intersects(here) }
            inside.forEach { item ->
                val path = Path()
                item.rings.forEach { ring ->
                    ring.forEachIndexed { k, (lat, lon) ->
                        val (px, py) = ParcelCache.pixel(lat, lon, z, x, y, size)
                        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                }
                canvas.drawPath(path, line)
            }
            // THE NUMBERS, where the state writes them, once a parcel is wide enough to hold one.
            if (z >= 16) inside.forEach { item ->
                val (lat, lon) = item.middle
                val (px, py) = ParcelCache.pixel(lat, lon, z, x, y, size)
                if (px < -40 || py < -20 || px > size + 40 || py > size + 20) return@forEach
                val (l, t) = ParcelCache.pixel(item.box.north, item.box.west, z, x, y, size)
                val (r, _) = ParcelCache.pixel(item.box.south, item.box.east, z, x, y, size)
                if (r - l < words.measureText(item.number) * 0.8f && z < 19) return@forEach
                if (t > size + 20) return@forEach
                canvas.drawText(item.number, px, py + words.textSize / 3f, words)
            }
        }

        val png = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
        bitmap.recycle()
        return png.toByteArray()
    }
}
