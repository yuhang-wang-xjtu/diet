package cn.qingheng.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EnergyHistoryTest {
    private val end = LocalDate.of(2026, 11, 6)
    private fun food(date: LocalDate, kcal: Double = 1800.0) = Entry(date = date.toString(), kind = "food", name = "饮食", kcal = kcal)
    private val manual: (String, List<Entry>) -> Totals = { _, records -> Energy.totals(records, 2000.0) }

    @Test fun sevenDayWindowIncludesBothEndpointsAndNetsSurplusAgainstDeficit() {
        val entries = listOf(food(end, 1500.0), food(end.minusDays(6), 2300.0),
            food(end.minusDays(7), 0.0), food(end.plusDays(1), 0.0))
        val result = EnergyHistory.summarize(end, 7, entries, emptyList(), manual)
        assertEquals(end.minusDays(6), result.start); assertEquals(end, result.end)
        assertEquals(200.0, result.deficit!!, 0.0)
        assertEquals(2, result.countedDays); assertEquals(5, result.missingFoodDays)
    }
    @Test fun thirtyDayWindowUsesCalendarDatesAcrossMonthBoundary() {
        val entries = (0L..30L).map { food(end.minusDays(it)) } + food(end.plusDays(1))
        val result = EnergyHistory.summarize(end, 30, entries, emptyList(), manual)
        assertEquals(LocalDate.of(2026, 10, 8), result.start)
        assertEquals(6000.0, result.deficit!!, 0.0)
        assertEquals(30, result.countedDays); assertEquals(0, result.missingFoodDays)
    }
    @Test fun blankDaysAndExerciseOnlyDaysDoNotPretendToBeFastingDays() {
        val entries = listOf(Entry(date = end.toString(), kind = "exercise", name = "跑步", kcal = 300.0))
        val result = EnergyHistory.summarize(end, 7, entries, emptyList(), manual)
        assertNull(result.deficit); assertEquals(0, result.countedDays); assertEquals(7, result.missingFoodDays)
    }
    @Test fun onlyThreeExplicitSkippedMealsEstablishZeroIntake() {
        val skipped = Meal.regular.map { SkippedMeal(end.toString(), it.key) } +
            SkippedMeal(end.minusDays(1).toString(), Meal.BREAKFAST.key)
        val result = EnergyHistory.summarize(end, 7, emptyList(), skipped, manual)
        assertEquals(2000.0, result.deficit!!, 0.0)
        assertEquals(1, result.countedDays); assertEquals(6, result.missingFoodDays)
    }
    @Test fun missingExpenditureIsExcludedAndNotTreatedAsZero() {
        val result = EnergyHistory.summarize(end, 7, listOf(food(end)), emptyList()) { _, records -> Energy.totals(records, null) }
        assertNull(result.deficit); assertEquals(1, result.missingBaselineDays); assertEquals(6, result.missingFoodDays)
    }
    @Test fun automaticModeDoesNotAddRecordedExerciseOrWalkingAgain() {
        val entries = listOf(food(end), Entry(date = end.toString(), kind = "exercise", name = "运动", kcal = 500.0))
        val auto = EnergyHistory.summarize(end, 7, entries, emptyList()) { _, records -> Energy.totals(records, 2000.0, 100.0, addExercise = false) }
        val manualResult = EnergyHistory.summarize(end, 7, entries, emptyList()) { _, records -> Energy.totals(records, 2000.0, 100.0) }
        assertEquals(200.0, auto.deficit!!, 0.0); assertEquals(800.0, manualResult.deficit!!, 0.0)
    }
    @Test fun eachDateUsesItsEffectiveProfileAndWeight() {
        val profile = Profile(end.minusDays(2).toString(), 30, FormulaSex.MALE, 175.0, ActivityLevel.LOW)
        val weights = listOf(Weight(end.minusDays(2).toString(), 70.0), Weight(end.toString(), 69.0), Weight(end.plusDays(1).toString(), 40.0))
        val entries = (0L..3L).map { food(end.minusDays(it)) }
        val result = EnergyHistory.summarize(end, 7, entries, emptyList()) { date, records ->
            val estimate = ProfileMath.estimate(ProfileMath.at(listOf(profile), date), weights, date)
            Energy.totals(records, estimate?.expenditure, addExercise = false)
        }
        val expected = (0L..2L).sumOf { ProfileMath.estimate(profile, weights, end.minusDays(it).toString())!!.expenditure - 1800.0 }
        assertEquals(expected, result.deficit!!, 1e-9)
        assertEquals(3, result.countedDays); assertEquals(1, result.missingBaselineDays); assertEquals(3, result.missingFoodDays)
    }
}
