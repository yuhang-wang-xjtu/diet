package cn.qingheng.app

import android.content.Context
import android.graphics.*
import android.view.View
import java.time.LocalDate

class WeightChart(context: Context, private val weights: List<Weight>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    init { contentDescription = "最近30天体重曲线，" + weights.joinToString { "${it.date}，${it.kg}千克" } }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (weights.isEmpty()) return
        val left = 42 * density; val right = width - 12 * density; val top = 24 * density; val bottom = height - 30 * density
        val low = weights.minOf { it.kg } - 0.5; val high = weights.maxOf { it.kg } + 0.5
        paint.textSize = 10 * density; paint.strokeWidth = density
        for (i in 0..2) {
            val y = top + (bottom - top) * i / 2
            paint.color = Color.rgb(231, 236, 228); canvas.drawLine(left, y, right, y, paint)
            paint.color = Color.rgb(116, 127, 117); canvas.drawText(String.format(java.util.Locale.US, "%.1f", high - (high - low) * i / 2), 0f, y + 4 * density, paint)
        }
        val first = LocalDate.parse(weights.first().date).toEpochDay()
        val span = (LocalDate.parse(weights.last().date).toEpochDay() - first).coerceAtLeast(1)
        val points = weights.map { w ->
            val x = if (weights.size == 1) (left + right) / 2 else left + (right - left) * (LocalDate.parse(w.date).toEpochDay() - first).toFloat() / span
            PointF(x, bottom - ((w.kg - low) / (high - low) * (bottom - top)).toFloat())
        }
        val path = Path(); points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        paint.color = Color.rgb(36, 122, 98); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * density; canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL; points.forEach { canvas.drawCircle(it.x, it.y, 4 * density, paint) }
        paint.color = Color.rgb(116, 127, 117); canvas.drawText(weights.first().date.substring(5), left, height - 6 * density, paint)
        if (weights.size > 1) { paint.textAlign = Paint.Align.RIGHT; canvas.drawText(weights.last().date.substring(5), right, height - 6 * density, paint); paint.textAlign = Paint.Align.LEFT }
    }
}
