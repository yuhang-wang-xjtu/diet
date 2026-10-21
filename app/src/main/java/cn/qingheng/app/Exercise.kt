package cn.qingheng.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ExerciseRateKind { ACTIVE, TOTAL }
data class ExerciseTemplate(val id: Long = 0, val name: String, val kcalPerMinute: Double, val kind: ExerciseRateKind = ExerciseRateKind.ACTIVE)
data class StepDay(val date: String, val steps: Long, val source: String, val updatedAt: Long, val included: Boolean = true)
data class WalkingConfig(val met: Double = 3.8, val cadence: Double = 100.0)
data class WalkingEstimate(val steps: Long, val excluded: Long, val minutes: Double, val kcal: Double?, val message: String)

object ExerciseMath {
    fun perMinute(value: Double, perHour: Boolean): Double {
        require(value.isFinite() && value >= 0)
        return value / if (perHour) 60.0 else 1.0
    }
    fun netRate(rate: Double, kind: ExerciseRateKind, weightKg: Double?): Double? {
        require(rate.isFinite() && rate >= 0)
        if (kind == ExerciseRateKind.ACTIVE) return rate
        if (weightKg == null || !weightKg.isFinite() || weightKg <= 0) return null
        // Compendium convention: resting expenditure is approximately 1 kcal/kg/hour.
        return (rate - weightKg / 60.0).coerceAtLeast(0.0)
    }
    fun calories(rate: Double, kind: ExerciseRateKind, minutes: Int, weightKg: Double?): Double? {
        require(minutes in 1..1440)
        return netRate(rate, kind, weightKg)?.times(minutes)
    }
    fun weightAt(weights: List<Weight>, date: String): Double? = weights.filter { it.date <= date }.maxByOrNull { it.date }?.kg
    fun walking(day: StepDay?, entries: List<Entry>, weightKg: Double?, config: WalkingConfig): WalkingEstimate {
        if (day == null) return WalkingEstimate(0, 0, 0.0, null, "尚无该日步数")
        val overlap = entries.filter { it.kind == "exercise" && it.date == day.date }.sumOf { it.overlapSteps.coerceAtLeast(0) }
        val excluded = overlap.coerceAtMost(day.steps.coerceAtLeast(0))
        val steps = (day.steps - excluded).coerceAtLeast(0)
        if (!day.included) return WalkingEstimate(steps, excluded, 0.0, 0.0, "本日步行不计入热量")
        if (!config.met.isFinite() || config.met < 1 || !config.cadence.isFinite() || config.cadence <= 0)
            return WalkingEstimate(steps, excluded, 0.0, null, "请核对步行强度与步频")
        val minutes = steps / config.cadence
        if (minutes > 1440) return WalkingEstimate(steps, excluded, minutes, null, "步数与步频超过一天时长，请核对参数")
        if (steps == 0L) return WalkingEstimate(0, excluded, 0.0, 0.0, "没有可计入步行的步数")
        if (weightKg == null || !weightKg.isFinite() || weightKg <= 0)
            return WalkingEstimate(steps, excluded, minutes, null, "请先记录当天或之前的体重")
        val kcal = (config.met - 1).coerceAtLeast(0.0) * weightKg * minutes / 60.0
        return WalkingEstimate(steps, excluded, minutes, kcal, "已扣除约1 MET静息消耗")
    }
    fun dayRange(date: LocalDate, now: Instant, zone: ZoneId): Pair<Instant, Instant>? {
        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
        return if (end > start) start to end else null
    }
}
