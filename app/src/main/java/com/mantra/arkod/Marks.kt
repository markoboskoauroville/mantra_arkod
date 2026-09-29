package com.mantra.arkod

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * THE MARKS THEMSELVES, drawn once and used by both engines.
 *
 * When the second renderer arrived (16.9.2026) the position dot and the route letters existed
 * only inside the mapsforge canvas, and the quickest thing would have been to copy them. Two
 * copies of a drawing is two drawings that drift, and the day they drift is the day one map shows
 * a different mark from the other for the same place. So they live here, in Android's own
 * graphics, and each engine wraps the result in whatever bitmap it wants.
 */
object Marks {

    /** Where he is, and the cone of light showing where the phone is pointed. */
    fun position(context: Context, heading: Double, mapTurn: Double): Bitmap {
        val scale = context.resources.displayMetrics.density
        val side = (72 * scale).toInt()
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = side / 2f
        val dot = 7f * scale

        if (!heading.isNaN()) {
            val reach = c - 1f
            val sweep = 62f
            val start = (heading + mapTurn - 90.0 - sweep / 2).toFloat()
            val cone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    c,
                    c,
                    reach,
                    intArrayOf(
                        Color.argb(150, 59, 130, 246),
                        Color.argb(70, 59, 130, 246),
                        Color.argb(0, 59, 130, 246),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawArc(RectF(c - reach, c - reach, c + reach, c + reach), start, sweep, true, cone)
        }

        canvas.drawCircle(
            c,
            c + 0.5f * scale,
            dot + 2.5f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) },
        )
        canvas.drawCircle(
            c,
            c,
            dot + 2f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
        )
        canvas.drawCircle(
            c,
            c,
            dot,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 59, 130, 246) },
        )
        return bitmap
    }

    /**
     * WHERE A SEARCH LANDED (27.9.2026): Google's own shape, a round head on a point, in cyan, the
     * colour of the parcel selection, so a result reads as "this, here" and never as a route point.
     * The tip is the bottom centre of the picture; the canvases anchor it there.
     */
    fun searchPin(context: Context): Bitmap {
        val scale = context.resources.displayMetrics.density
        val w = (30 * scale).toInt()
        val h = (42 * scale).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = w / 2f - 1.5f * scale
        val cx = w / 2f
        val cy = r + 1.5f * scale
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 34, 211, 238) }
        val path = android.graphics.Path().apply {
            addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
            moveTo(cx - r * 0.62f, cy + r * 0.78f)
            lineTo(cx, h.toFloat() - 1f)
            lineTo(cx + r * 0.62f, cy + r * 0.78f)
            close()
        }
        canvas.drawPath(path, body)
        canvas.drawCircle(cx, cy, r * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 11, 13, 16) })
        return bitmap
    }

    /** The one red, used by every mark that is his rather than the map's. */
    private val RED = Color.argb(255, 229, 57, 53)
}
