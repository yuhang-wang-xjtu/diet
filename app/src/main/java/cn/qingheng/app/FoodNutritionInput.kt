package cn.qingheng.app

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.widget.*

/** The same reference/actual gram form is used for meals, shortcuts and recipe ingredients. */
class FoodNutritionInput(private val activity: Activity, private val parent: LinearLayout, name: String = "",
    initial: FoodInputDraft = FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null)) {
    private fun field(label: String, initial: String = "", numeric: Boolean = true): EditText {
        parent.addView(TextView(activity).apply { text = label; textSize = 13f; setTextColor(Color.rgb(72, 91, 78)); setPadding(0, dp(8), 0, dp(4)) })
        return EditText(activity).apply {
            setSingleLine(true); minHeight = dp(48); textSize = 16f
            if (numeric) NumberInput.configure(this, initial) else { inputType = InputType.TYPE_CLASS_TEXT; setText(initial) }
            this@FoodNutritionInput.parent.addView(this)
        }
    }
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun raw(value: Double?) = value?.toString() ?: ""
    val name = field("食物 / 菜品名称", name, false)
    val basis = field("参考克数（g，默认100）", raw(initial.basis))
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
    val actual = field("实际食用克数（g，手动输入）", raw(initial.actual))
    private val preview = TextView(activity).apply { textSize = 13f; setTextColor(Color.rgb(36, 122, 98)); this@FoodNutritionInput.parent.addView(this) }
    private var original = initial
    init {
        parent.addView(TextView(activity).apply { text = "按参考克数填写营养，再手动输入实际克数。支持负数及计算式；留空表示未知，填0表示确实为0。"; textSize = 12f })
        listOf(basis, kcal, protein, fat, carbs, sodium, actual).forEach { NumberInput.onFinished(it) { update() } }
        update()
    }
    fun load(food: QuickFood?, actualGrams: Double? = null) {
        name.setText(food?.name ?: "")
        val draft = food?.let { FoodInputDraft.fromFood(it, actualGrams) } ?: FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null); original = draft
        NumberInput.setValue(basis, draft.basis); NumberInput.setValue(actual, draft.actual)
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
        val grams = FoodMath.number(NumberInput.raw(basis))
        if (validate && (grams == null || grams == 0.0)) basis.error = "参考克数需为有效非零数值（可为负数）；无法除以0"
        return grams?.takeIf { it != 0.0 }
    }
    fun portion(validate: Boolean = true): FoodPortion? {
        val n = reference(validate)
        val g = referenceGrams(validate)
        val amount = FoodMath.number(NumberInput.raw(actual))
        if (validate && amount == null) actual.error = "请手动输入实际克数或计算式（允许负数和0）"
        if (n == null || g == null || amount == null) return null
        val p = FoodPortion(g, amount, n)
        if (!p.values.valid()) { if (validate) actual.error = "计算结果不是有限数值，请检查计算式"; return null }
        return p
    }
    fun referenceUnchanged() = reference(false) == original.reference && referenceGrams(false) == original.basis
    fun actualUnchanged() = FoodMath.number(NumberInput.raw(actual)) == original.actual
    private fun update() {
        preview.text = if (original.basis == null && basis.text.isBlank()) "此旧配方只保存整锅营养，请先把整锅成品克重填为参考克数，再输入实际食用克数。不会猜测原配方重量。"
        else portion(false)?.let { "本次摄入：${it.values.summary()}" } ?: "完成参考数值并手动填入实际克数后显示本次摄入。"
    }
}
