package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class UnifiedFoodInputTest {
    @Test fun defaultsTo100gAndLeavesActualGramsForManualInput() {
        val food = QuickFood(name = "食物", basisGrams = 250.0, values = FoodValues(500.0, 25.0, 50.0, 10.0))
        val draft = FoodInputDraft.fromFood(food)
        assertEquals(100.0, draft.basis!!, 0.0); assertNull(draft.actual)
        assertEquals(200.0, draft.reference!!.kcal, 0.0)
        assertEquals(10.0, draft.reference.protein!!, 0.0)
        assertEquals(4.0, draft.reference.fat!!, 0.0)
        assertEquals(20.0, draft.reference.carbs!!, 0.0)
        assertEquals(250.0, food.basisGrams!!, 0.0); assertEquals(500.0, food.values.kcal, 0.0)
    }
    @Test fun inputOrderAndSummariesAreProteinFatCarbs() {
        assertEquals(listOf("protein", "fat", "carbs"), FoodInputDraft.MACRO_ORDER)
        val summary = FoodValues(10.0, 1.0, 3.0, 2.0).summary()
        assertTrue(summary.indexOf("蛋白") < summary.indexOf("脂肪"))
        assertTrue(summary.indexOf("脂肪") < summary.indexOf("碳水"))
    }
    @Test fun negativeValuesSurviveJsonWithoutClamping() {
        val food = QuickFood(name = "修正", basisGrams = -100.0, values = FoodValues(-50001.0, -6000.0, -20000.0, -8000.0, -1000001.0))
        assertEquals(food, FoodJson.decode(FoodJson.encode(food)))
        assertEquals(-50001.0, FoodMath.number("-50000-1")!!, 0.0)
    }
    @Test fun negativeActualGramsSubtractConsumptionAndZeroIsAccepted() {
        val n = EntryNutrients(200.0, 10.0, 30.0, 5.0, null)
        val p = FoodPortion(100.0, -50.0, n)
        assertEquals(-100.0, p.values.kcal, 0.0)
        assertEquals(-5.0, p.values.protein!!, 0.0); assertNull(p.values.sodium)
        assertEquals(p, PortionJson.decode(PortionJson.encode(p)))
        assertEquals(0.0, FoodPortion(100.0, 0.0, n).values.kcal, 0.0)
    }
    @Test fun negativeReferenceAndAmountsFollowOrdinaryArithmetic() {
        val p = FoodPortion(-100.0, -50.0, EntryNutrients(-200.0, -10.0, null, -5.0, -20.0))
        assertTrue(p.values.valid()); assertEquals(-100.0, p.values.kcal, 0.0)
        assertEquals(-5.0, p.values.protein!!, 0.0)
    }
    @Test fun noFoodMagnitudeLimitsButNonFiniteAndZeroDivisorAreInvalid() {
        assertTrue(FoodPortion(1.0, 100000000.0, EntryNutrients(1000000.0, null, null, null, null)).values.valid())
        assertFalse(FoodPortion(1.0, 1e308, EntryNutrients(1e308, null, null, null, null)).values.valid())
        assertThrows(IllegalArgumentException::class.java) { FoodPortion(0.0, -1.0, EntryNutrients(1.0, null, null, null, null)) }
    }
    @Test fun negativeAndZeroIngredientsAreAllowedAndSummed() {
        val parsed = FoodParser.parse("-10g花生油、0g盐、花生油5g")
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals(listOf(-10.0, 0.0, 5.0), parsed.items.map { it.amount })
        assertEquals(-44.2, FoodMath.sum(parsed.items).kcal, 1e-10)
    }
    @Test fun existingGramRecordNormalizesPresentationWithoutChangingIntake() {
        val p = FoodPortion(250.0, 150.0, EntryNutrients(500.0, 25.0, null, 10.0, null))
        val n = p.values
        val entry = Entry(date = "2026-11-04", kind = "food", name = "原记录", kcal = n.kcal, protein = n.protein, fat = n.fat, portion = PortionJson.encode(p))
        val draft = FoodInputDraft.fromEntry(entry)
        assertEquals(100.0, draft.basis!!, 0.0); assertEquals(150.0, draft.actual!!, 0.0)
        assertEquals(n, FoodPortion(draft.basis, draft.actual, draft.reference!!).values)
        assertEquals(250.0, PortionJson.decode(entry.portion)!!.basis, 0.0)
    }
    @Test fun legacyTotalRecordPreservesUnknownAndNegativeValues() {
        val entry = Entry(date = "2026-11-04", kind = "food", name = "旧记录", kcal = -100.123456789, protein = null, fat = -3.3333333)
        val d = FoodInputDraft.fromEntry(entry)
        val n = FoodPortion(d.basis!!, d.actual!!, d.reference!!).values
        assertEquals(entry.kcal, n.kcal, 0.0); assertNull(n.protein); assertEquals(entry.fat, n.fat)
        assertEquals("", entry.portion)
    }
    @Test fun legacyUnknownRecipeWeightIsNotInvented() {
        val food = FoodCatalog.tomatoEgg
        val d = FoodInputDraft.fromFood(food)
        assertNull(d.basis); assertNull(d.actual)
        assertEquals(food.values.kcal, d.reference!!.kcal, 0.0)
    }
}
