package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class FoodTest {
    @Test fun scalesCookedRiceByGramsAndEstimatedBowlWeight() {
        assertEquals(FoodCatalog.rice.values.kcal * 2, FoodCatalog.rice.portion(200.0, FoodUnit.GRAMS).kcal, 0.0001)
        assertEquals(FoodCatalog.rice.values.kcal * 1.5, FoodCatalog.rice.portion(1.0, FoodUnit.PORTIONS).kcal, 0.0001)
    }
    @Test fun convertsEggCountUsingExplicitEdibleWeight() {
        val two = FoodCatalog.egg.portion(2.0, FoodUnit.PORTIONS)
        assertEquals(13.3, two.protein, 0.0001)
        assertEquals(8.8, two.fat, 0.0001)
    }
    @Test fun parsesUsersIngredientExampleAndIncludesSaltAndOil() {
        val parsed = FoodParser.parse("两个鸡蛋、一个西红柿、10g盐、10g花生油")
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals(4, parsed.items.size)
        val total = FoodMath.sum(parsed.items)
        assertEquals(599.0 / 4.184 + 27.0 + 88.4, total.kcal, 0.0001)
        assertEquals(14.62, total.protein, 0.0001)
        assertEquals(8.635, total.carbs, 0.0001)
        assertEquals(19.1, total.fat, 0.0001)
        assertEquals(4014.8, total.sodium!!, 0.0001)
    }
    @Test fun recipesCanBePortionedByBatchWithoutInventingCookedWeight() {
        val recipe = FoodCatalog.tomatoEgg
        assertNull(recipe.basisGrams)
        assertEquals(recipe.values.kcal / 2, recipe.portion(0.5, FoodUnit.PORTIONS).kcal, 0.0001)
        assertThrows(IllegalArgumentException::class.java) { recipe.portion(100.0, FoodUnit.GRAMS) }
    }
    @Test fun cookedWeightChangesDensityButNotTotalNutrients() {
        val items = FoodCatalog.tomatoEgg.ingredients
        val cooked = FoodMath.recipe("炒蛋", items, 200.0)
        assertEquals(FoodCatalog.tomatoEgg.values.kcal, cooked.values.kcal, 0.0001)
        assertEquals(cooked.values.kcal / 2, cooked.portion(100.0, FoodUnit.GRAMS).kcal, 0.0001)
    }
    @Test fun unknownSodiumIsNotConvertedToZero() {
        val custom = QuickFood(name = "未知钠食品", basisGrams = 100.0, values = FoodValues(100.0, 1.0, 20.0, 2.0))
        val sum = FoodMath.sum(listOf(FoodIngredient(custom, 100.0, FoodUnit.GRAMS), FoodIngredient(FoodCatalog.salt, 2.0, FoodUnit.GRAMS)))
        assertNull(sum.sodium)
        assertEquals(100.0, sum.kcal, 0.0001)
    }
    @Test fun zeroEnergySeasoningsRemainValidAndHaveSodium() {
        assertEquals(0.0, FoodMath.nonNegative("0", 50000.0)!!, 0.0001)
        assertEquals(3875.8, FoodCatalog.salt.portion(10.0, FoodUnit.GRAMS).sodium!!, 0.0001)
        listOf("NaN", "Infinity", "-1", "50001", "").forEach { assertNull(FoodMath.nonNegative(it, 50000.0)) }
    }
    @Test fun cannotSilentlyIgnoreUnknownIngredientsOrGuessUnits() {
        val unknown = FoodParser.parse("鸡蛋2个、神秘调料10g")
        assertTrue(unknown.errors.isNotEmpty()); assertTrue(unknown.items.isEmpty())
        assertTrue(FoodParser.parse("10ml花生油").errors.isNotEmpty())
        assertTrue(FoodParser.parse("2碗鸡蛋").errors.isNotEmpty())
    }
    @Test fun supportsSuffixUnitsChineseNumbersAndKilograms() {
        val p = FoodParser.parse("鸡蛋十二个；0.5kg米饭；半个西红柿")
        assertTrue(p.errors.toString(), p.errors.isEmpty())
        assertEquals(listOf(12.0, 500.0, 0.5), p.items.map { it.amount })
        assertEquals(FoodUnit.GRAMS, p.items[1].unit)
    }
    @Test fun customFoodsCanBeIngredientsAndAmbiguousNamesAreRejected() {
        val custom = QuickFood(7, "我的酱", 100.0, FoodValues(100.0, 1.0, 20.0, 2.0), "勺", 10.0)
        assertTrue(FoodParser.parse("我的酱10g", listOf(custom)).errors.isEmpty())
        assertTrue(FoodParser.parse("米饭100g", listOf(custom.copy(name = "米饭"))).errors.isNotEmpty())
    }
    @Test fun changingTemplateCannotRewriteAlreadyChosenNutrients() {
        val original = QuickFood(name = "我的早餐", basisGrams = 100.0, values = FoodValues(200.0, 10.0, 20.0, 9.0))
        val logged = FoodSelection(original, 100.0, FoodUnit.GRAMS).values
        val changed = original.copy(values = original.values * 2.0)
        assertEquals(200.0, logged.kcal, 0.0001)
        assertEquals(400.0, changed.values.kcal, 0.0001)
    }
    @Test fun rejectsInvalidMassAndNonfiniteValues() {
        assertThrows(IllegalArgumentException::class.java) { QuickFood(name="bad", basisGrams=0.0, values=FoodValues.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { FoodCatalog.egg.portion(Double.NaN, FoodUnit.PORTIONS) }
        assertThrows(IllegalArgumentException::class.java) { FoodValues(Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0) }
    }
    @Test fun catalogHasUniqueIdsAndDocumentedSources() {
        assertEquals(FoodCatalog.all.size, FoodCatalog.all.map { it.id }.toSet().size)
        assertTrue(FoodCatalog.all.all { it.source.isNotBlank() })
        assertEquals(11, FoodCatalog.all.size)
    }
}
