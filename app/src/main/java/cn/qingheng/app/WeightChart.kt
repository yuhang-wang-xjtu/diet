package cn.qingheng.app

import android.content.Context
import android.graphics.*
import android.view.View
import java.time.LocalDate

class WeightChart(context: Context, private val weights: List<Weight>, private val periodStart: LocalDate? = null, private val periodEnd: LocalDate? = null) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val dash = DashPathEffect(floatArrayOf(2 * density, 4 * density), 0f)
    private val line = Path()
    private val area = Path()
    private var last: PointF? = null
    init { contentDescription = "最近30天体重曲线，" + weights.joinToString { "${it.date}，${it.kg}千克" } }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        line.reset(); area.reset(); last = null
        if (weights.isEmpty()) return
        val left = 12 * density; val right = w - 11 * density; val top = 24 * density
        val bottom = h - 25 * density; val baseline = h - 5 * density
        val min = weights.minOf { it.kg }; val max = weights.maxOf { it.kg }
        val low = if (min == max) min - 0.5 else min; val high = if (min == max) max + 0.5 else max
        val first = (periodStart ?: LocalDate.parse(weights.first().date)).toEpochDay()
        val span = ((periodEnd ?: LocalDate.parse(weights.last().date)).toEpochDay() - first).coerceAtLeast(1)
        val points = weights.map { value ->
            val x = if (weights.size == 1 && periodStart == null) (left + right) / 2 else left + (right - left) * (LocalDate.parse(value.date).toEpochDay() - first).toFloat() / span
            PointF(x, bottom - ((value.kg - low) / (high - low) * (bottom - top)).toFloat())
        }
        points.forEachIndexed { index, point -> if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y) }
        area.set(line); area.lineTo(points.last().x, baseline); area.lineTo(points.first().x, baseline); area.close()
        last = points.last()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (weights.isEmpty()) return
        paint.style = Paint.Style.STROKE; paint.strokeWidth = density; paint.color = DesignUi.soft; paint.pathEffect = dash
        canvas.drawLine(12 * density, 24 * density, width - 11 * density, 24 * density, paint); paint.pathEffect = null
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(235, 242, 227); canvas.drawPath(area, paint)
        paint.color = DesignUi.green; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * density; canvas.drawPath(line, paint)
        paint.style = Paint.Style.FILL; last?.let { canvas.drawCircle(it.x, it.y, 4 * density, paint) }
    }
}
