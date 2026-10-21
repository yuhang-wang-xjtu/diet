package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExerciseTest {
    private val day = StepDay("2026-10-20", 6000, "health_connect", 1L)
    @Test fun convertsHourlyRateAndMultipliesDuration() {
        val rate = ExerciseMath.perMinute(330.0, true)
        assertEquals(5.5, rate, 0.0001)
        assertEquals(165.0, ExerciseMath.calories(rate, ExerciseRateKind.ACTIVE, 30, null)!!, 0.0001)
        assertEquals(5.5, ExerciseMath.perMinute(5.5, false), 0.0001)
    }
    @Test fun totalConsumptionDeductsRestingPartOnce() {
        assertEquals(360.0, ExerciseMath.calories(7.0, ExerciseRateKind.TOTAL, 60, 60.0)!!, 0.0001)
        assertEquals(420.0, ExerciseMath.calories(7.0, ExerciseRateKind.ACTIVE, 60, 60.0)!!, 0.0001)
        assertEquals(0.0, ExerciseMath.netRate(0.5, ExerciseRateKind.TOTAL, 60.0)!!, 0.0001)
        assertNull(ExerciseMath.netRate(7.0, ExerciseRateKind.TOTAL, null))
    }
    @Test fun walkingUsesWeightIntensityAndEstimatedCadence() {
        val walk = ExerciseMath.walking(day, emptyList(), 70.0, WalkingConfig())
        assertEquals(60.0, walk.minutes, 0.0001)
        assertEquals(196.0, walk.kcal!!, 0.0001)
        val brisk = ExerciseMath.walking(day, emptyList(), 70.0, WalkingConfig(4.8, 100.0))
        assertTrue(brisk.kcal!! > walk.kcal)
    }
    @Test fun excludesStepsAlreadyLoggedInManualWorkouts() {
        val exercise = Entry(date = day.date, kind = "exercise", name = "慢跑", kcal = 200.0, overlapSteps = 2000)
        val walk = ExerciseMath.walking(day, listOf(exercise), 70.0, WalkingConfig())
        assertEquals(4000L, walk.steps); assertEquals(2000L, walk.excluded)
        assertEquals(40.0, walk.minutes, 0.0001)
        assertEquals(196.0 * 2 / 3, walk.kcal!!, 0.0001)
    }
    @Test fun neverSubtractsOtherDaysOrFoodRecords() {
        val entries = listOf(Entry(date="2026-10-19", kind="exercise", name="跑步", kcal=100.0, overlapSteps=9999),
            Entry(date=day.date, kind="food", name="食物", kcal=100.0, overlapSteps=9999))
        assertEquals(6000L, ExerciseMath.walking(day, entries, 70.0, WalkingConfig()).steps)
    }
    @Test fun overlapCannotMakeWalkingNegative() {
        val exercise = Entry(date=day.date, kind="exercise", name="跑步", kcal=100.0, overlapSteps=9000)
        val walk = ExerciseMath.walking(day, listOf(exercise), 70.0, WalkingConfig())
        assertEquals(0L, walk.steps); assertEquals(6000L, walk.excluded); assertEquals(0.0, walk.kcal!!, 0.0001)
    }
    @Test fun dailySwitchCanExcludeWalkingAlreadyPresentInBaseline() {
        val walk = ExerciseMath.walking(day.copy(included=false), emptyList(), null, WalkingConfig())
        assertEquals(0.0, walk.kcal!!, 0.0001)
        assertNull(ExerciseMath.walking(null, emptyList(), 70.0, WalkingConfig()).kcal)
        assertNull(ExerciseMath.walking(day, emptyList(), null, WalkingConfig()).kcal)
    }
    @Test fun rejectsInconsistentDurationAndParameters() {
        assertNull(ExerciseMath.walking(day.copy(steps=300000), emptyList(), 70.0, WalkingConfig(3.8,40.0)).kcal)
        assertNull(ExerciseMath.walking(day, emptyList(), 70.0, WalkingConfig(3.8,0.0)).kcal)
        assertNull(ExerciseMath.walking(day, emptyList(), 70.0, WalkingConfig(Double.NaN,100.0)).kcal)
        assertThrows(IllegalArgumentException::class.java) { ExerciseMath.calories(5.0, ExerciseRateKind.ACTIVE, 0, 70.0) }
        assertThrows(IllegalArgumentException::class.java) { ExerciseMath.perMinute(Double.POSITIVE_INFINITY, true) }
    }
    @Test fun knownZeroStepsNeedsNoWeight() {
        assertEquals(0.0, ExerciseMath.walking(day.copy(steps=0), emptyList(), null, WalkingConfig()).kcal!!, 0.0001)
    }
    @Test fun looksUpHistoricalWeightWithoutFutureLeak() {
        val weights = listOf(Weight("2026-10-21", 90.0), Weight("2026-10-18", 70.0), Weight("2026-10-20", 68.0))
        assertEquals(68.0, ExerciseMath.weightAt(weights, day.date)!!, 0.0001)
        assertNull(ExerciseMath.weightAt(weights, "2026-10-17"))
    }
    @Test fun stepQueryUsesLocalMidnightNotRolling24Hours() {
        val range = ExerciseMath.dayRange(LocalDate.parse(day.date), Instant.parse("2026-10-20T06:00:00Z"), ZoneId.of("Asia/Shanghai"))!!
        assertEquals(Instant.parse("2026-10-19T16:00:00Z"), range.first)
        assertEquals(Instant.parse("2026-10-20T06:00:00Z"), range.second)
        assertNull(ExerciseMath.dayRange(LocalDate.parse("2026-10-21"), Instant.parse("2026-10-20T06:00:00Z"), ZoneId.of("Asia/Shanghai")))
    }
    @Test fun historicalStepDayRespectsDaylightSaving() {
        val range = ExerciseMath.dayRange(LocalDate.parse("2026-03-08"), Instant.parse("2026-03-10T00:00:00Z"), ZoneId.of("America/New_York"))!!
        assertEquals(23L * 3600, range.second.epochSecond - range.first.epochSecond)
    }
    @Test fun calorieTotalsIncludeComputedWalkingExactlyOnce() {
        val entries = listOf(Entry(date=day.date, kind="food", name="午饭", kcal=1800.0), Entry(date=day.date, kind="exercise", name="骑行", kcal=200.0))
        val total = Energy.totals(entries, 2000.0, 196.0)
        assertEquals(396.0, total.exercise, 0.0001); assertEquals(596.0, total.deficit!!, 0.0001)
        assertEquals(1, entries.count { it.kind == "exercise" })
    }
}
