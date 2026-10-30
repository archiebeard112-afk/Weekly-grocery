package com.weeklymeals

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * The round cook bubble, drawn the same way in the app and floating over other apps: a dark
 * circle with a lime ring that runs down with the next timer and the time inside. With no
 * timer it shows a lime pot; when a timer is done the whole bubble turns lime.
 */
object CookRing {
    private const val LIME = 0xFFD4F25A.toInt()
    private const val CARD = 0xFF1A1D19.toInt()
    private const val TRACK = 0xFF2E332C.toInt()
    private const val INK = 0xFF0F110F.toInt()
    private const val TEXT = 0xFFF1EFE8.toInt()

    /** [time] is null with no timer; [left] is the share of the timer still to go (0..1). */
    data class Look(val time: String?, val left: Float, val done: Boolean)

    /** How the bubble looks for the timer ending at [end] that started at [start] (null if unknown). */
    fun look(end: Long?, start: Long?, now: Long): Look {
        if (end == null) return Look(null, 0f, false)
        if (end <= now) return Look("Done", 1f, true)
        val sec = (end - now + 999) / 1000
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        val text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        val left = if (start != null && end > start) ((end - now).toFloat() / (end - start)).coerceIn(0f, 1f) else 1f
        return Look(text, left, false)
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val arc = RectF()

    /** Draws the bubble in a [size] x [size] square. [dim] fades a finished timer for the blink. */
    fun draw(c: Canvas, size: Float, look: Look, pot: Drawable?, font: Typeface?, dim: Float = 1f) {
        val r = size / 2
        if (look.done) {
            fill.color = LIME
            fill.alpha = (255 * dim).toInt()
            c.drawCircle(r, r, r, fill)
            text(c, size, look.time ?: "", INK, font, size * 0.24f)
            return
        }
        fill.color = CARD
        c.drawCircle(r, r, r, fill)
        val ringR = size * 25f / 60f
        ring.strokeWidth = size * 0.05f
        ring.color = TRACK
        c.drawCircle(r, r, ringR, ring)
        if (look.time == null) {
            pot?.setBounds(0, 0, size.toInt(), size.toInt())
            pot?.draw(c)
            return
        }
        ring.color = LIME
        arc.set(r - ringR, r - ringR, r + ringR, r + ringR)
        c.drawArc(arc, -90f, 360f * look.left, false, ring)
        text(c, size, look.time, TEXT, font, if (look.time.length > 5) size * 0.18f else size * 0.25f)
    }

    private fun text(c: Canvas, size: Float, s: String, color: Int, font: Typeface?, px: Float) {
        label.color = color
        label.typeface = font ?: Typeface.DEFAULT_BOLD
        label.textSize = px
        // Same-width digits so the time doesn't jiggle as it counts down.
        label.fontFeatureSettings = "tnum"
        c.drawText(s, size / 2, size / 2 - (label.descent() + label.ascent()) / 2, label)
    }
}
