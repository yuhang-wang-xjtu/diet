package cn.qingheng.app

import java.util.Locale

data class FoodValues(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val sodium: Double? = null) {
    init {
        require(listOf(kcal, protein, carbs, fat).all { it.isFinite() && it >= 0 })
        require(sodium == null || sodium.isFinite() && sodium >= 0)
    }
    operator fun times(factor: Double): FoodValues {
        require(factor.isFinite() && factor >= 0)
        return FoodValues(kcal * factor, protein * factor, carbs * factor, fat * factor, sodium?.times(factor))
    }
    operator fun plus(other: FoodValues) = FoodValues(kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat,
        if (sodium == null || other.sodium == null) null else sodium + other.sodium)
    fun summary() = "${f(kcal)} kcal · 蛋白 ${f(protein)} g · 碳水 ${f(carbs)} g · 脂肪 ${f(fat)} g · 钠 ${sodium?.let { f(it) + " mg" } ?: "未知"}"
    companion object {
        fun f(value: Double) = String.format(Locale.US, "%.1f", value)
        val ZERO = FoodValues(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

enum class FoodUnit { GRAMS, PORTIONS }

/** Values refer to basisGrams, or to one whole batch if cooked weight is unknown. */
data class QuickFood(
    val id: Long = 0, val name: String, val basisGrams: Double?, val values: FoodValues,
    val unitName: String = "份", val unitGrams: Double? = basisGrams,
    val source: String = "用户自定义", val sourceUrl: String = "", val note: String = "",
    val ingredients: List<FoodIngredient> = emptyList()
) {
    init {
        require(basisGrams == null || basisGrams.isFinite() && basisGrams > 0)
        require(unitGrams == null || unitGrams.isFinite() && unitGrams > 0)
    }
    fun factor(amount: Double, unit: FoodUnit): Double {
        require(amount.isFinite() && amount > 0)
        return when (unit) {
            FoodUnit.GRAMS -> amount / requireNotNull(basisGrams) { "未填写熟重，不能按克分摊整锅配方" }
            FoodUnit.PORTIONS -> if (unitGrams != null && basisGrams != null) amount * unitGrams / basisGrams else amount
        }
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
            append(if (basisGrams == null) "\n未填写成品熟重，只能按整锅比例记录。" else "\n整锅成品熟重：${FoodValues.f(basisGrams)} g。")
        }
    }
}
data class FoodIngredient(val food: QuickFood, val amount: Double, val unit: FoodUnit) {
    val values get() = food.portion(amount, unit)
}
data class FoodSelection(val food: QuickFood, val amount: Double, val unit: FoodUnit) {
    val values get() = food.portion(amount, unit)
    val details get() = "${food.name} · ${food.quantity(amount, unit)}\n${food.details()}"
}
object FoodMath {
    fun nonNegative(raw: String, max: Double): Double? = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it <= max }
    fun sum(items: List<FoodIngredient>): FoodValues = items.fold(FoodValues.ZERO) { total, item -> total + item.values }
    fun recipe(name: String, items: List<FoodIngredient>, cookedGrams: Double?, id: Long = 0) = QuickFood(
        id, name, cookedGrams, sum(items), "整锅", cookedGrams,
        "按原材料相加估算", note = "假设所填原料均进入成品；不自动估计残油、弃汤和烹调营养损失。按实际吃掉的整锅比例记录。",
        ingredients = items.map { it.copy(food = it.food.copy(ingredients = emptyList())) })
}
