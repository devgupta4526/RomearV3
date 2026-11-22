package app.romear.ui

import android.content.Context
import android.graphics.*
import android.view.View

/** Drawn as an Android View so the passport belongs to the current profile, not a screenshot. */
class PassportCoverView(context: Context, private val owner: String, private val level: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = width / 360f
        canvas.save(); canvas.scale(scale, scale)
        val height = height / scale
        val bounds = RectF(2f, 2f, 358f, height - 2)
        paint.shader = LinearGradient(0f, 0f, 360f, height, intArrayOf(Color.rgb(29, 79, 57), Color.rgb(12, 50, 36)), null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL; canvas.drawRoundRect(bounds, 22f, 22f, paint); paint.shader = null
        paint.color = Color.rgb(216, 181, 105); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(5f, 5f, 355f, height - 5), 19f, 19f, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("serif", Typeface.BOLD); paint.textSize = 33f
        canvas.drawText("ROMEAR", 180f, 53f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL); paint.textSize = 13f
        canvas.drawText("C I T Y   P A S S P O R T", 180f, 80f, paint)
        // Architectural colonnade, a scalable visual echo of Rome's amphitheatre.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(93f, 111f, 267f, 202f), 9f, 9f, paint)
        canvas.drawLine(93f, 142f, 267f, 142f, paint); canvas.drawLine(93f, 173f, 267f, 173f, paint)
        for (row in 0..2) for (col in 0..6) {
            val x = 102f + col * 23f; val y = 117f + row * 30f
            canvas.drawRoundRect(RectF(x, y, x + 14f, y + 20f), 6f, 6f, paint)
        }
        canvas.drawArc(RectF(58f, 100f, 302f, 220f), 15f, 150f, false, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(255, 245, 216)
        paint.typeface = Typeface.create("serif", Typeface.BOLD); paint.textSize = 25f
        while (paint.measureText(owner) > 310f) paint.textSize -= 1f
        canvas.drawText(owner, 180f, height - 54f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL); paint.textSize = 13f
        canvas.drawText("City Explorer · Level $level", 180f, height - 30f, paint)
        canvas.restore()
    }
}
