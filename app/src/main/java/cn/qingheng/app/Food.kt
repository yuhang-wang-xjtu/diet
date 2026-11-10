package cn.qingheng.app


data class FoodValues(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val sodium: Double? = null) {
    init {
        require(listOf(kcal, protein, carbs, fat).all { it.isFinite() })
        require(sodium == null || sodium.isFinite())
    }
    operator fun times(factor: Double): FoodValues {
        require(factor.isFinite())
        return FoodValues(kcal * factor, protein * factor, carbs * factor, fat * factor, sodium?.times(factor))
    }
    operator fun plus(other: FoodValues) = FoodValues(kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat,
        if (sodium == null || other.sodium == null) null else sodium + other.sodium)
    fun summary() = "${f(kcal)} kcal · 蛋白 ${f(protein)} g · 脂肪 ${f(fat)} g · 碳水 ${f(carbs)} g · 钠 ${sodium?.let { f(it) + " mg" } ?: "未知"}"
    fun nutrients() = EntryNutrients(kcal, protein, carbs, fat, sodium)
    companion object {
        fun f(value: Double) = DecimalText.display(value)
        val ZERO = FoodValues(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

enum class FoodUnit { GRAMS, PORTIONS }

/** Values refer to basisGrams, or to one whole batch if cooked weight is unknown. */
data class QuickFood(
    val id: Long = 0, val name: String, val basisGrams: Double?, val values: FoodValues,
    val unitName: String = "份", val unitGrams: Double? = basisGrams,
    val source: String = "用户自定义", val sourceUrl: String = "", val note: String = "",
    val ingredients: List<FoodIngredient> = emptyList(),
    val category: String = FoodCategories.LEGACY, val image: String = ""
) {
    init {
        require(basisGrams == null || basisGrams.isFinite() && basisGrams != 0.0)
        require(unitGrams == null || unitGrams.isFinite())
    }
    fun factor(amount: Double, unit: FoodUnit): Double {
        require(amount.isFinite())
        return when (unit) {
            FoodUnit.GRAMS -> amount / requireNotNull(basisGrams) { "未填写熟重，不能按克分摊整锅配方" }
            FoodUnit.PORTIONS -> if (unitGrams != null && basisGrams != null) amount * unitGrams / basisGrams else amount
        }.also { require(it.isFinite()) }
    }
    fun portion(amount: Double, unit: FoodUnit) = values * factor(amount, unit)
    fun quantity(amount: Double, unit: FoodUnit) = if (unit == FoodUnit.GRAMS) "${FoodValues.f(amount)} g" else
        "${FoodValues.f(amount)} $unitName${unitGrams?.let { "（约 ${FoodValues.f(amount * it)} g）" } ?: ""}"
    fun details() = buildString {
        append("来源：$source"); if (sourceUrl.isNotBlank()) append("\n$sourceUrl")
        if (note.isNotBlank()) append("\n$note")
        if (ingredients.isNotEmpty()) {
            append("\n整锅原料：")
            ingredients.forEach { append("\n${it.food.name} ${it.food.quantity(it.amount, it.unit)}；${it.food.source}") }
            append(if (basisGrams == null) "\n未填写成品熟重，可直接按整份记录；按克记录时再补充成品克重。" else "\n整锅成品熟重：${FoodValues.f(basisGrams)} g。")
        }
    }
}
data class FoodIngredient(val food: QuickFood, val amount: Double, val unit: FoodUnit) {
    val values get() = food.portion(amount, unit)
}
data class FoodSelection(val food: QuickFood, val amount: Double, val unit: FoodUnit) {
    fun asPortion(): FoodPortion? = food.basisGrams?.let { basis ->
        val n = food.values
        val actual = food.factor(amount, unit) * basis
        if (!actual.isFinite()) null
        else FoodPortion(basis, actual, EntryNutrients(n.kcal, n.protein, n.carbs, n.fat, n.sodium))
    }
    val values get() = food.portion(amount, unit)
    val details get() = "${food.name} · ${food.quantity(amount, unit)}\n${food.details()}"
}
object FoodMath {
    fun number(raw: String): Double? = Expressions.evaluate(raw)
    fun nonNegative(raw: String, max: Double): Double? = Expressions.evaluate(raw)?.takeIf { it >= 0 && it <= max }
    fun sum(items: List<FoodIngredient>): FoodValues = items.fold(FoodValues.ZERO) { total, item -> total + item.values }
    fun recipe(name: String, items: List<FoodIngredient>, cookedGrams: Double?, id: Long = 0) = QuickFood(
        id, name, cookedGrams, sum(items), "整锅", cookedGrams,
        "按原材料相加估算", note = "假设所填原料均进入成品；不自动估计残油、弃汤和烹调营养损失。可按整份记录，或补充成品克重后按实际克数记录。",
        ingredients = items.map { it.copy(food = it.food.copy(ingredients = emptyList())) })
}
object FoodCategories {
    const val LEGACY = "原有菜品"
    val defaults = listOf("菜品", "主食", "饮品", "蛋奶肉类", "蔬果", "佐料", "零食", LEGACY)
    fun clean(raw: String) = raw.trim().ifBlank { LEGACY }
    fun of(food: QuickFood) = clean(food.category)
    fun builtin(id: Long) = when (id) {
        -1L, -2L -> "主食"; -9L -> "饮品"; -3L -> "蛋奶肉类"; -4L, -10L -> "蔬果"
        -5L, -6L, -7L, -8L -> "佐料"; else -> "菜品"
    }
    fun categories(foods: List<QuickFood>) = (defaults + foods.map { of(it) }).distinct()
    /** Selection lists contain existing foods; editor suggestions may still include empty defaults. */
    fun available(foods: List<QuickFood>) = categories(foods).filter { category -> foods.any { of(it) == category } }
}
data class EntryNutrients(val kcal: Double, val protein: Double?, val carbs: Double?, val fat: Double?, val sodium: Double?) {
    fun scale(factor: Double) = EntryNutrients(kcal * factor, protein?.times(factor), carbs?.times(factor), fat?.times(factor), sodium?.times(factor))
    fun valid() = listOf(kcal, protein, fat, carbs, sodium).all { it == null || it.isFinite() }
    fun summary() = "${FoodValues.f(kcal)} kcal · 蛋白 ${protein?.let { FoodValues.f(it) } ?: "未知"}g · 脂肪 ${fat?.let { FoodValues.f(it) } ?: "未知"}g · 碳水 ${carbs?.let { FoodValues.f(it) } ?: "未知"}g"
}
data class FoodPortion(val basis: Double, val actual: Double, val reference: EntryNutrients, val unit: FoodUnit = FoodUnit.GRAMS) {
    init { require(basis.isFinite() && basis != 0.0 && actual.isFinite()) }
    val values get() = reference.scale(actual / basis)
}
data class FoodInputDraft(val basis: Double?, val actual: Double?, val reference: EntryNutrients?, val unit: FoodUnit = FoodUnit.GRAMS) {
    val defaultAmount get() = if (unit == FoodUnit.PORTIONS) 1.0 else DEFAULT_GRAMS
    companion object {
        const val DEFAULT_GRAMS = 100.0
        val MACRO_ORDER = listOf("protein", "fat", "carbs")
        fun grams(raw: String, default: Double? = DEFAULT_GRAMS): Double? =
            if (raw.isBlank()) default else FoodMath.number(raw)
        fun fromFood(food: QuickFood, actual: Double? = null) = FoodInputDraft(
            food.basisGrams?.let { DEFAULT_GRAMS }, actual,
            food.values.nutrients().let { n -> food.basisGrams?.let { n.scale(DEFAULT_GRAMS / it) } ?: n },
            if (food.basisGrams == null) FoodUnit.PORTIONS else FoodUnit.GRAMS)
        fun fromEntry(entry: Entry): FoodInputDraft {
            val p = PortionJson.decode(entry.portion)
            return if (p == null) FoodInputDraft(DEFAULT_GRAMS, DEFAULT_GRAMS, EntryNutrients(entry.kcal, entry.protein, entry.carbs, entry.fat, entry.sodium))
            else if (p.unit == FoodUnit.PORTIONS) FoodInputDraft(null, p.actual, p.reference.scale(1.0 / p.basis), FoodUnit.PORTIONS)
            else FoodInputDraft(DEFAULT_GRAMS, p.actual, p.reference.scale(DEFAULT_GRAMS / p.basis))
        }
    }
}
