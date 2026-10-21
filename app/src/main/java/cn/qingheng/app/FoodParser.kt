package cn.qingheng.app

data class IngredientParse(val items: List<FoodIngredient>, val errors: List<String>)
object FoodParser {
    private const val NUM = "([0-9]+(?:\\.[0-9]+)?|[一二两三四五六七八九十半]+)"
    private const val UNIT = "(kg|公斤|千克|g|克|个|根|碗|杯|勺|份|小勺)"
    private val prefix = Regex("^$NUM\\s*$UNIT\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val suffix = Regex("^(.+?)\\s*$NUM\\s*$UNIT$", RegexOption.IGNORE_CASE)
    private fun number(raw: String): Double? {
        raw.toDoubleOrNull()?.let { return it }
        if (raw == "半") return 0.5
        val digits = mapOf('一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (raw.length == 1) return if (raw == "十") 10.0 else digits[raw[0]]?.toDouble()
        val parts = raw.split('十')
        if (parts.size != 2 || parts.any { it.length > 1 }) return null
        val tens = if (parts[0].isEmpty()) 1 else digits[parts[0][0]] ?: return null
        val ones = if (parts[1].isEmpty()) 0 else digits[parts[1][0]] ?: return null
        return (tens * 10 + ones).toDouble()
    }
    fun parse(raw: String, custom: List<QuickFood> = emptyList()): IngredientParse {
        val items = mutableListOf<FoodIngredient>(); val errors = mutableListOf<String>()
        val tokens = raw.split(Regex("[、,，;；\\n+]+" )).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return IngredientParse(emptyList(), listOf("请输入原材料与数量"))
        tokens.forEach { token ->
            val p = prefix.matchEntire(token); val s = suffix.matchEntire(token)
            val amountText = p?.groupValues?.get(1) ?: s?.groupValues?.get(2)
            val unitText = (p?.groupValues?.get(2) ?: s?.groupValues?.get(3))?.lowercase()
            val name = (p?.groupValues?.get(3) ?: s?.groupValues?.get(1))?.trim()
            val amount = amountText?.let { number(it) }
            val candidates = (custom.filter { it.name == name } + listOfNotNull(FoodCatalog.aliases[name])).distinctBy { it.id to it.name }
            val food = candidates.singleOrNull()
            when {
                name == null || amount == null || amount <= 0 || !amount.isFinite() -> errors.add("无法识别“$token”，请用“鸡蛋2个”或“10g花生油”")
                food == null -> errors.add("“$name”不存在或有同名条目，请先建自定义食物，或从列表选择")
                else -> {
                    val grams = unitText in listOf("g", "克", "kg", "公斤", "千克")
                    val value = amount * if (unitText in listOf("kg", "公斤", "千克")) 1000 else 1
                    val validUnit = grams || food.unitName.startsWith(unitText!!) || unitText == "份"
                    if (!validUnit || grams && food.basisGrams == null || value > 100000) errors.add("“$token”的单位/数量不适用，请从列表确认份量")
                    else items.add(FoodIngredient(food.copy(ingredients = emptyList()), value, if (grams) FoodUnit.GRAMS else FoodUnit.PORTIONS))
                }
            }
        }
        // A partially understood ingredient list must never silently undercount a recipe.
        return IngredientParse(if (errors.isEmpty()) items else emptyList(), errors)
    }
}
