package cn.qingheng.app

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.widget.*

/** The same reference/actual gram form is used for meals, shortcuts and recipe ingredients. */
class FoodNutritionInput(private val activity: Activity, private val parent: LinearLayout, name: String = "",
    initial: FoodInputDraft = FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null)) {
    private fun field(label: String, initial: String = "", numeric: Boolean = true, placeholder: String = label): EditText {
        parent.addView(TextView(activity).apply { text = label; textSize = 13f; setTextColor(Color.rgb(72, 91, 78)); setPadding(0, dp(8), 0, dp(4)) })
        return EditText(activity).apply {
            setSingleLine(true); minHeight = dp(48); textSize = 16f; hint = placeholder
            if (numeric) NumberInput.configure(this, initial) else { inputType = InputType.TYPE_CLASS_TEXT; setText(initial) }
            this@FoodNutritionInput.parent.addView(this)
        }
    }
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun raw(value: Double?) = value?.toString() ?: ""
    val name = field("食物 / 菜品名称", name, false)
    val basis = field("参考克数（g，可留空）", raw(initial.basis?.takeUnless { it == FoodInputDraft.DEFAULT_GRAMS }),
        placeholder = if (initial.basis == null) "请填写这份配方的整锅成品克重" else "留空默认为100g")
    val kcal = field("参考热量（kcal）", raw(initial.reference?.kcal))
    private val macros = FoodInputDraft.MACRO_ORDER.associateWith { key -> when (key) {
        "protein" -> field("蛋白质（g）", raw(initial.reference?.protein))
        "fat" -> field("脂肪（g）", raw(initial.reference?.fat))
        else -> field("碳水化合物（g）", raw(initial.reference?.carbs))
    } }
    val protein = macros.getValue("protein")
    val fat = macros.getValue("fat")
    val carbs = macros.getValue("carbs")
    val sodium = field("钠（mg，未知可留空）", raw(initial.reference?.sodium))
    val actual = field("实际食用克数（g，可留空）", raw(initial.actual), placeholder = "留空默认为100g，可手动修改")
    private val preview = TextView(activity).apply { textSize = 13f; setTextColor(Color.rgb(36, 122, 98)); this@FoodNutritionInput.parent.addView(this) }
    private val help = TextView(activity).apply { textSize = 12f; this@FoodNutritionInput.parent.addView(this) }
    private var original = initial
    init {
        listOf(basis, kcal, protein, fat, carbs, sodium, actual).forEach { NumberInput.onFinished(it) { update() } }
        update()
    }
    fun load(food: QuickFood?, actualGrams: Double? = null) {
        name.setText(food?.name ?: "")
        val draft = food?.let { FoodInputDraft.fromFood(it, actualGrams) } ?: FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null); original = draft
        NumberInput.setValue(basis, draft.basis?.takeUnless { it == FoodInputDraft.DEFAULT_GRAMS }); NumberInput.setValue(actual, draft.actual)
        basis.hint = if (draft.basis == null) "请填写这份配方的整锅成品克重" else "留空默认为100g"
        val n = draft.reference
        NumberInput.setValue(kcal, n?.kcal); NumberInput.setValue(protein, n?.protein); NumberInput.setValue(fat, n?.fat); NumberInput.setValue(carbs, n?.carbs); NumberInput.setValue(sodium, n?.sodium)
        update()
    }
    fun reference(validate: Boolean = true, requireMacros: Boolean = false): EntryNutrients? {
        fun parse(field: EditText, required: Boolean): Double? {
            val value = FoodMath.number(NumberInput.raw(field))
            if (validate && (required || field.text.isNotBlank()) && value == null) field.error = "请输入有效数值或计算式（允许负数）"
            return value
        }
        val energy = parse(kcal, true)
        val p = parse(protein, requireMacros); val f = parse(fat, requireMacros); val c = parse(carbs, requireMacros); val na = parse(sodium, false)
        if (energy == null || listOf(protein to p, fat to f, carbs to c).any { (field, n) -> (requireMacros || field.text.isNotBlank()) && n == null } || sodium.text.isNotBlank() && na == null) return null
        return EntryNutrients(energy, p, c, f, na)
    }
    fun referenceGrams(validate: Boolean = true): Double? {
        val grams = FoodInputDraft.grams(NumberInput.raw(basis), if (original.basis == null) null else FoodInputDraft.DEFAULT_GRAMS)
        if (validate && (grams == null || grams == 0.0)) basis.error = if (original.basis == null && basis.text.isBlank())
            "旧配方未保存重量，请填写整锅成品克重" else "请输入有效非零克数或留空按100g；无法除以0"
        return grams?.takeIf { it != 0.0 }
    }
    fun portion(validate: Boolean = true): FoodPortion? {
        val n = reference(validate)
        val g = referenceGrams(validate)
        val amount = FoodInputDraft.grams(NumberInput.raw(actual))
        if (validate && amount == null) actual.error = "请输入有效克数或计算式，或留空按100g（允许负数和0）"
        if (n == null || g == null || amount == null) return null
        val p = FoodPortion(g, amount, n)
        if (!p.values.valid()) { if (validate) actual.error = "计算结果不是有限数值，请检查计算式"; return null }
        return p
    }
    fun referenceUnchanged() = reference(false) == original.reference && referenceGrams(false) == original.basis
    fun actualUnchanged() = FoodInputDraft.grams(NumberInput.raw(actual)) == (original.actual ?: FoodInputDraft.DEFAULT_GRAMS)
    private fun update() {
        help.setText(if (original.basis == null) R.string.food_unknown_weight_help else R.string.food_gram_help)
        preview.text = if (original.basis == null && basis.text.isBlank()) "此旧配方只保存整锅营养，请先把整锅成品克重填为参考克数，再输入实际食用克数。不会猜测原配方重量。"
        else portion(false)?.let { "本次摄入：${it.values.summary()}" } ?: "填写参考热量后显示本次摄入；克数留空按100g。"
    }
}
