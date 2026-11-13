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
    @Test fun foodSelectionCategoriesOnlyContainNonemptyGroupsIncludingLegacyAndCustom() {
        assertTrue(FoodCategories.available(emptyList()).isEmpty())
        assertEquals(listOf("主食"), FoodCategories.available(listOf(FoodCatalog.all.single { it.id == -1L })))
        val foods = listOf(FoodCatalog.rice.copy(category = "自定义"), FoodJson.decode(legacy))
        assertEquals(listOf(FoodCategories.LEGACY, "自定义"), FoodCategories.available(foods))
        assertTrue(FoodCategories.categories(emptyList()).contains("饮品"))
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
    @Test fun noArtificialQuantityBoundsButDivisionByZeroAndOverflowAreRejected() {
        try { FoodPortion(0.0, 100.0, EntryNutrients(1.0, null, null, null, null)); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(FoodPortion(1.0, 100000.0, EntryNutrients(100.0, null, null, null, null)).values.valid())
        assertFalse(FoodPortion(1.0, 1e308, EntryNutrients(1e308, null, null, null, null)).values.valid())
    }
    @Test fun customUnitWeightIsUsedWhenChoosingFoodAndSurvivesReopening() {
        val food = QuickFood(name = "套餐", basisGrams = 250.0, values = FoodValues(500.0, 25.0, 50.0, 10.0), unitName = "份", unitGrams = 150.0)
        val draft = FoodInputDraft.fromFood(food)
        assertEquals(FoodUnit.PORTIONS, draft.unit); assertEquals(150.0, draft.basis!!, 0.0)
        val portion = FoodPortion(1.0, 2.0, draft.reference!!, draft.unit, draft.unitGrams, draft.unitName)
        assertEquals(food.portion(2.0, FoodUnit.PORTIONS).kcal, portion.values.kcal, 0.0)
        val snapshot = PortionJson.encode(portion)
        val reopened = FoodInputDraft.fromEntry(Entry(date = "2026-11-13", kind = "food", name = food.name, kcal = portion.values.kcal, portion = snapshot))
        assertEquals(FoodUnit.PORTIONS, reopened.unit); assertEquals(2.0, reopened.actual!!, 0.0)
        assertEquals(150.0, reopened.unitGrams!!, 0.0); assertEquals(draft.reference, reopened.reference)
        assertEquals(FoodUnit.GRAMS, FoodInputDraft.fromFood(food, preferPortions = false).unit)
    }
    @Test fun exportedFoodsKeepPreciseNutritionRecipesAndPictureBytesWithoutReusingIds() {
        val image = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg"
        val food = FoodJson.decode(legacy, 45).copy(image = image)
        val recipe = FoodCatalog.tomatoEgg.copy(id = 72, ingredients = listOf(FoodIngredient(food, 1.25, FoodUnit.PORTIONS)))
        val archive = FoodArchive.decode(FoodArchive.encode(listOf(food, recipe), mapOf(image to byteArrayOf(1, 2, 3))))
        assertEquals(listOf(0L, 0L), archive.foods.map { it.id })
        assertEquals(food.copy(id = 0), archive.foods[0]); assertEquals(recipe.ingredients, archive.foods[1].ingredients)
        assertArrayEquals(byteArrayOf(1, 2, 3), archive.images.getValue(image))
        val restored = FoodArchive.restoreImages(archive.foods[1], mapOf(image to "restored.jpg"))
        assertEquals("restored.jpg", restored.ingredients.single().food.image)
        assertEquals(food.values, restored.ingredients.single().food.values)
    }
}
