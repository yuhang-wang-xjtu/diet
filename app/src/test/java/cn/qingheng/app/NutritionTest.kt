package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class NutritionTest {
    @Test fun sumsFoodOnlyAndPreservesMissingVersusZero() {
        val entries = listOf(
            Entry(date = "2026-10-20", kind = "food", name = "早餐", kcal = 300.0, protein = 12.5, carbs = 25.0),
            Entry(date = "2026-10-20", kind = "food", name = "午餐", kcal = 600.0, protein = 0.0, fat = 10.0),
            Entry(date = "2026-10-20", kind = "food", name = "旧记录", kcal = 500.0),
            Entry(date = "2026-10-20", kind = "exercise", name = "跑步", kcal = 200.0, protein = 999.0))
        assertEquals(NutrientTotal(12.5, 2, 3), Nutrition.total(entries) { it.protein })
        assertEquals(NutrientTotal(25.0, 1, 3), Nutrition.total(entries) { it.carbs })
        assertEquals(NutrientTotal(10.0, 1, 3), Nutrition.total(entries) { it.fat })
        assertEquals(NutrientTotal(0.0, 0, 0), Nutrition.total(emptyList()) { it.protein })
    }
    @Test fun usesLatestWeightOnOrBeforeSelectedDate() {
        val weights = listOf(Weight("2026-10-21", 90.0), Weight("2026-10-18", 70.0), Weight("2026-10-20", 68.0))
        assertEquals(68.0 * 0.83, Nutrition.proteinTarget(weights, "2026-10-20", null)!!, 0.001)
        assertEquals(70.0 * 0.83, Nutrition.proteinTarget(weights, "2026-10-19", null)!!, 0.001)
        assertNull(Nutrition.proteinTarget(weights, "2026-10-17", null))
        assertNull(Nutrition.proteinTarget(emptyList(), "2026-10-20", null))
    }
    @Test fun customProteinTargetOverridesAutomaticReference() {
        assertEquals(100.0, Nutrition.proteinTarget(listOf(Weight("2026-10-20", 70.0)), "2026-10-20", 100.0)!!, 0.001)
        assertEquals(80.0, Nutrition.proteinTarget(emptyList(), "2026-10-20", 80.0)!!, 0.001)
    }
    @Test fun acceptsZeroButRejectsInvalidGrams() {
        assertEquals(0.0, Nutrition.grams("0")!!, 0.001)
        assertEquals(23.4, Nutrition.grams(" 23.4 ")!!, 0.001)
        listOf("", "-1", "NaN", "Infinity", "5000.1", "hello").forEach { assertNull(Nutrition.grams(it)) }
    }
}
