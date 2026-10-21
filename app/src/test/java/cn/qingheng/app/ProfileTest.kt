package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class ProfileTest {
    private val day = "2026-10-20"
    private val profile = Profile(day, 30, FormulaSex.MALE, 175.0, ActivityLevel.LOW)
    private val weights = listOf(Weight(day, 70.0))
    @Test fun maleFormulaAndMaintenanceNutrition() {
        val e = ProfileMath.estimate(profile, weights, day)!!
        assertEquals(1648.75, e.resting, 0.001)
        assertEquals(2308.25, e.expenditure, 0.001)
        assertEquals(22.85714, e.bmi, 0.0001)
        assertEquals(e.expenditure, e.protein * 4 + e.carbs * 4 + e.fat * 9, 0.001)
        assertEquals(115.4125, e.protein, 0.001)
        assertEquals(288.53125, e.carbs, 0.001)
        assertEquals(76.94167, e.fat, 0.001)
    }
    @Test fun femaleFormulaAndActivity() {
        val e = ProfileMath.estimate(profile.copy(sex = FormulaSex.FEMALE, activity = ActivityLevel.HIGH), weights, day)!!
        assertEquals(1482.75, e.resting, 0.001)
        assertEquals(2965.5, e.expenditure, 0.001)
    }
    @Test fun profileAndWeightNeverLeakIntoPast() {
        assertNull(ProfileMath.at(listOf(profile), "2026-10-19"))
        assertNull(ProfileMath.estimate(profile, weights, "2026-10-19"))
        assertNull(ProfileMath.estimate(profile.copy(date = "2026-10-01"), weights, "2026-10-19"))
        val old = profile.copy(date = "2026-09-01", heightCm = 170.0)
        assertEquals(old, ProfileMath.at(listOf(profile, old), "2026-10-19"))
    }
    @Test fun latestWeightUpdatesEstimateOnlyFromItsDate() {
        val ws = weights + Weight("2026-10-21", 69.0)
        assertEquals(1648.75, ProfileMath.estimate(profile, ws, day)!!.resting, 0.001)
        assertEquals(1638.75, ProfileMath.estimate(profile, ws, "2026-10-21")!!.resting, 0.001)
    }
    @Test fun unsupportedAndInvalidInputsHaveNoAutomaticTarget() {
        for (age in listOf(18, 79)) assertNull(ProfileMath.estimate(profile.copy(age = age), weights, day))
        for (height in listOf(Double.NaN, Double.POSITIVE_INFINITY, 99.0, 251.0)) assertNull(ProfileMath.estimate(profile.copy(heightCm = height), weights, day))
        assertNull(ProfileMath.estimate(profile, listOf(Weight(day, Double.NaN)), day))
        assertNull(ProfileMath.estimate(null, weights, day))
        assertNull(ProfileMath.estimate(profile, emptyList(), day))
    }
    @Test fun ageUsesProfileAnniversaryAndStopsAtUpperLimit() {
        assertEquals(30, ProfileMath.ageAt(profile, "2027-10-19"))
        assertEquals(31, ProfileMath.ageAt(profile, "2027-10-20"))
        assertNull(ProfileMath.estimate(profile.copy(age = 78), weights, "2027-10-20"))
    }
    @Test fun automaticTotalsDoNotAddRecordedExerciseOrWalkingAgain() {
        val entries = listOf(Entry(date = day, kind = "food", name = "餐", kcal = 1800.0), Entry(date = day, kind = "exercise", name = "跑步", kcal = 300.0))
        val auto = Energy.totals(entries, 2300.0, 100.0, addExercise = false)
        assertEquals(500.0, auto.deficit!!, 0.001)
        assertEquals(400.0, auto.exercise, 0.001)
        assertEquals(900.0, Energy.totals(entries, 2300.0, 100.0).deficit!!, 0.001)
        assertNull(Energy.totals(entries, null, 100.0, false).deficit)
    }
}
