package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class FoodCompatibilityTest {
    private val legacy = """{"name":"已经保存的菜品","basis":150.123456789,"unit":"碗","unitGrams":200.7654321,"kcal":87.654321987,"protein":4.123456789,"carbs":6.765432109,"fat":2.333333333,"sodium":null,"source":"原有来源","url":"https://example.com/source","note":"保留我的备注","ingredients":[]}"""
    @Test fun oldPayloadGetsCategoryAndDefaultImageWithoutChangingAnyNumbers() {
        val food = FoodJson.decode(legacy, 45)
        assertEquals(45L, food.id); assertEquals(FoodCategories.LEGACY, food.category); assertEquals("", food.image)
        assertEquals(87.654321987, food.values.kcal, 0.0)
        assertEquals(150.123456789, food.basisGrams!!, 0.0)
        assertEquals(200.7654321, food.unitGrams!!, 0.0)
        assertNull(food.values.sodium)
        assertEquals("保留我的备注", food.note); assertEquals("原有来源", food.source)
        assertEquals("https://example.com/source", food.sourceUrl)
    }
    @Test fun metadataEditAndRoundTripKeepLegacyNutritionAndId() {
        val original = FoodJson.decode(legacy, 45)
        val changed = original.copy(category = "饮品", image = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg")
        val restored = FoodJson.decode(FoodJson.encode(changed), 45)
        assertEquals(changed, restored); assertEquals(original.values, restored.values)
        assertEquals(original.basisGrams, restored.basisGrams); assertEquals(original.note, restored.note)
    }
    @Test fun recipeIngredientsAndNutritionSnapshotsRoundTrip() {
        val recipe = FoodCatalog.tomatoEgg.copy(id = 34, category = "午餐", image = "photo.jpg")
        assertEquals(recipe, FoodJson.decode(FoodJson.encode(recipe), 34))
        val modified = recipe.copy(category = "晚餐")
        assertEquals(recipe.values, modified.values); assertEquals(recipe.ingredients, modified.ingredients)
    }
    @Test fun builtinsAreGroupedAndMilkIsUnderDrinks() {
        assertEquals("饮品", FoodCategories.of(FoodCatalog.all.single { it.id == -9L }))
        assertEquals("主食", FoodCategories.of(FoodCatalog.all.single { it.id == -1L }))
        val old = FoodJson.decode(legacy)
        assertTrue(FoodCategories.categories(listOf(old)).contains(FoodCategories.LEGACY))
        assertEquals(1, FoodCategories.categories(listOf(old, old)).count { it == FoodCategories.LEGACY })
    }
    @Test fun directGramsScaleKnownNutrientsAndPreserveUnknowns() {
        val portion = FoodPortion(100.0, 150.0, EntryNutrients(200.0, 10.0, null, 0.0, null))
        assertEquals(300.0, portion.values.kcal, 0.0)
        assertEquals(15.0, portion.values.protein!!, 0.0)
        assertNull(portion.values.carbs); assertNull(portion.values.sodium); assertEquals(0.0, portion.values.fat!!, 0.0)
        assertEquals(portion, PortionJson.decode(PortionJson.encode(portion)))
    }
    @Test fun editAmountsUsesReferenceRatherThanScalingPreviousActualAgain() {
        val portion = FoodPortion(100.0, 150.0, EntryNutrients(200.0, 10.0, 15.0, 3.0, 20.0))
        val reopened = PortionJson.decode(PortionJson.encode(portion))!!.copy(actual = 200.0)
        assertEquals(400.0, reopened.values.kcal, 0.0)
        assertEquals(20.0, reopened.values.protein!!, 0.0)
        assertNull(PortionJson.decode(""))
    }
    @Test fun quickPortionsConvertBowlsToActualGramsAndUnknownCookedWeightStaysBatch() {
        val selection = FoodSelection(FoodCatalog.rice, 2.0, FoodUnit.PORTIONS)
        assertEquals(300.0, selection.asPortion()!!.actual, 0.0)
        assertEquals(selection.values.kcal, selection.asPortion()!!.values.kcal, 1e-10)
        assertNull(FoodSelection(FoodCatalog.tomatoEgg, 0.5, FoodUnit.PORTIONS).asPortion())
    }
    @Test fun quantityBoundsAndExcessiveScaledIntakeAreRejected() {
        try { FoodPortion(0.0, 100.0, EntryNutrients(1.0, null, null, null, null)); fail() } catch (_: IllegalArgumentException) { }
        assertFalse(FoodPortion(1.0, 100000.0, EntryNutrients(100.0, null, null, null, null)).values.valid())
    }
}
