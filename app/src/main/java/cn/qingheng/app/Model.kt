package cn.qingheng.app

data class Entry(val id: Long = 0, val date: String, val kind: String, val name: String, val kcal: Double, val minutes: Int = 0,
    val protein: Double? = null, val carbs: Double? = null, val fat: Double? = null, val meal: String = "unspecified",
    val sodium: Double? = null, val details: String = "", val exerciseRate: Double? = null,
    val exerciseRateKind: String = "ACTIVE", val overlapSteps: Long = 0)
data class Weight(val date: String, val kg: Double)
data class Totals(val intake: Double, val exercise: Double, val baseline: Double?, val addExercise: Boolean = true) {
    val deficit: Double? get() = baseline?.let { it + (if (addExercise) exercise else 0.0) - intake }
}
data class NutrientTotal(val grams: Double, val recorded: Int, val foods: Int)
object Nutrition {
    fun total(entries: List<Entry>, nutrient: (Entry) -> Double?): NutrientTotal {
        val foods = entries.filter { it.kind == "food" }
        val values = foods.mapNotNull(nutrient)
        return NutrientTotal(values.sum(), values.size, foods.size)
    }
    fun grams(raw: String): Double? = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..5000.0 }
    fun proteinTarget(weights: List<Weight>, date: String, custom: Double?): Double? = custom ?: weights
        .filter { it.date <= date }.maxByOrNull { it.date }?.let { it.kg * 0.83 }
}
object Energy {
    fun totals(entries: List<Entry>, baseline: Double?, walkingKcal: Double = 0.0, addExercise: Boolean = true) = Totals(
        entries.filter { it.kind == "food" }.sumOf { it.kcal },
        entries.filter { it.kind == "exercise" }.sumOf { it.kcal } + walkingKcal, baseline, addExercise)
    fun number(raw: String, max: Double): Double? = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= max }
    fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
