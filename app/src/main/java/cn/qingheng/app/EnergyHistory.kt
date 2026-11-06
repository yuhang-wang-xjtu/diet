package cn.qingheng.app

import java.time.LocalDate

data class EnergyPeriod(val start: LocalDate, val end: LocalDate, val days: Int, val deficit: Double?,
    val countedDays: Int, val missingFoodDays: Int, val missingBaselineDays: Int)

object EnergyHistory {
    /** Blank days are unknown intake, rather than days of fasting. Include signed surpluses. */
    fun summarize(end: LocalDate, days: Int, entries: List<Entry>, skipped: List<SkippedMeal>,
        totals: (String, List<Entry>) -> Totals): EnergyPeriod {
        require(days > 0)
        val start = end.minusDays(days.toLong() - 1)
        val byDate = entries.groupBy { it.date }
        val skippedByDate = skipped.groupBy { it.date }
        var count = 0; var missingFood = 0; var missingBaseline = 0; var sum = 0.0
        repeat(days) { offset ->
            val date = start.plusDays(offset.toLong()).toString()
            val records = byDate[date].orEmpty()
            val fasting = Meal.regular.all { meal -> skippedByDate[date].orEmpty().any { it.meal == meal.key } }
            if (records.none { it.kind == "food" } && !fasting) missingFood++
            else {
                val deficit = totals(date, records).deficit
                if (deficit == null || !deficit.isFinite()) missingBaseline++
                else { sum += deficit; count++ }
            }
        }
        return EnergyPeriod(start, end, days, sum.takeIf { count > 0 && it.isFinite() }, count, missingFood, missingBaseline)
    }
}
