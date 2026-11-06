package cn.qingheng.app

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.widget.*

/** The same reference/actual gram form is used for meals, shortcuts and recipe ingredients. */
class FoodNutritionInput(private val activity: Activity, private val parent: LinearLayout, name: String = "",
    initial: FoodInputDraft = FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null), food: QuickFood? = null) {
    private fun dp(n: Int) = DesignUi.dp(activity, n)
    private fun raw(value: Double?) = value?.toString() ?: ""
    private var original = initial
    private var selectedFood = food
    private val header = DesignUi.surface(activity, DesignUi.food, 18, 18).also { parent.addView(it) }
    private val top = DesignUi.row(activity).also { header.addView(it) }
    private val identity = DesignUi.column(activity).also { top.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
    val name = EditText(activity).apply {
        hint = "食物 / 菜品名称"; setSingleLine(true); setText(name); textSize = 23f; setTextColor(DesignUi.ink)
        setTypeface(typeface, android.graphics.Typeface.BOLD); background = null; setPadding(0, 0, dp(6), 0); minHeight = dp(36); includeFontPadding = false
        identity.addView(this)
    }
    private val referenceLabel = DesignUi.text(activity, "参考营养 · 点击编辑", 13f, DesignUi.muted).also { DesignUi.space(identity, 10); identity.addView(it) }
    private val picture = ImageView(activity).also { top.addView(it, LinearLayout.LayoutParams(dp(105), dp(78))) }
    private val editReference = referenceLabel
    private val referenceFields = DesignUi.column(activity).also { parent.addView(it) }
    private fun field(label: String, value: String = "", placeholder: String = label, container: LinearLayout = referenceFields): EditText {
        container.addView(DesignUi.text(activity, label, 13f, DesignUi.muted).apply { setPadding(0, dp(8), 0, dp(6)) })
        return EditText(activity).apply {
            setSingleLine(true); minHeight = dp(48); textSize = 16f; hint = placeholder; setTextColor(DesignUi.ink); setHintTextColor(DesignUi.muted)
            background = DesignUi.shape(activity, Color.WHITE, 12); setPadding(dp(14), dp(10), dp(14), dp(10)); NumberInput.configure(this, value)
            container.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    val basis = field("参考克数（g，可留空）", raw(initial.basis?.takeUnless { it == FoodInputDraft.DEFAULT_GRAMS }),
        if (initial.basis == null) "请填写整锅成品克重" else "留空默认为100g")
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
    private val amountTitle = DesignUi.text(activity, "实际食用量（g，可输入算式）", 19f, bold = true, line = 26).also { DesignUi.space(parent, 29); parent.addView(it) }
    private val presets = DesignUi.row(activity).also { DesignUi.space(parent, 18); parent.addView(it) }
    val actual = EditText(activity).apply {
        setSingleLine(true); hint = "100 克"; textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = false; gravity = android.view.Gravity.CENTER; minHeight = dp(48); setPadding(dp(12), dp(8), dp(12), dp(8))
        contentDescription = "实际食用克数，可输入计算式，留空按100克"
        NumberInput.configure(this, raw(initial.actual ?: FoodInputDraft.DEFAULT_GRAMS)); presets.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(14) })
    }
    private val preview = DesignUi.column(activity).also { parent.addView(it) }
    private val help = DesignUi.text(activity, "", 12f, DesignUi.muted).also { DesignUi.space(parent, 12); parent.addView(it) }
    init {
        referenceFields.visibility = if (initial.reference == null || initial.basis == null) android.view.View.VISIBLE else android.view.View.GONE
        fun toggle() {
            val expanded = referenceFields.visibility == android.view.View.VISIBLE
            if (expanded && referenceFields.hasFocus()) actual.requestFocus()
            referenceFields.visibility = if (expanded) android.view.View.GONE else android.view.View.VISIBLE
            (actual.getTag(R.id.form_keyboard) as? FormKeyboard)?.refresh()
        }
        editReference.setOnClickListener { toggle() }; referenceLabel.setOnClickListener { toggle() }
        presets.addView(DesignUi.button(activity, "150 克", false) { NumberInput.setValue(actual, 150.0); update() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        listOf(basis, kcal, protein, fat, carbs, sodium, actual).forEach { NumberInput.onFinished(it) { update() } }
        update()
    }
    private fun reveal(field: EditText) {
        referenceFields.visibility = android.view.View.VISIBLE
        (actual.getTag(R.id.form_keyboard) as? FormKeyboard)?.refresh()
        field.requestFocus()
    }
    fun load(food: QuickFood?, actualGrams: Double? = null) {
        name.setText(food?.name ?: ""); selectedFood = food
        val draft = food?.let { FoodInputDraft.fromFood(it, actualGrams) } ?: FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null); original = draft
        NumberInput.setValue(basis, draft.basis?.takeUnless { it == FoodInputDraft.DEFAULT_GRAMS }); NumberInput.setValue(actual, draft.actual ?: FoodInputDraft.DEFAULT_GRAMS)
        basis.hint = if (draft.basis == null) "请填写这份配方的整锅成品克重" else "留空默认为100g"
        val n = draft.reference
        NumberInput.setValue(kcal, n?.kcal); NumberInput.setValue(protein, n?.protein); NumberInput.setValue(fat, n?.fat); NumberInput.setValue(carbs, n?.carbs); NumberInput.setValue(sodium, n?.sodium)
        referenceFields.visibility = if (draft.reference == null || draft.basis == null) android.view.View.VISIBLE else android.view.View.GONE
        (actual.getTag(R.id.form_keyboard) as? FormKeyboard)?.refresh(); update()
    }
    fun reference(validate: Boolean = true, requireMacros: Boolean = false): EntryNutrients? {
        fun parse(field: EditText, required: Boolean): Double? {
            val value = FoodMath.number(NumberInput.raw(field))
            if (validate && (required || field.text.isNotBlank()) && value == null) { field.error = "请输入有效数值或计算式（允许负数）"; reveal(field) }
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
        if (validate && (grams == null || grams == 0.0)) reveal(basis)
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
        val reference = reference(false)
        val grams = referenceGrams(false)
        referenceLabel.text = if (reference != null && grams != null) "每 ${FoodValues.f(grams)} g 含 ${FoodValues.f(reference.kcal)} kcal · 编辑" else "填写参考热量与营养数值   ›"
        val food = selectedFood
        FoodPhotos.display(picture, food?.image.orEmpty(), food?.let { FoodPhotos.fallback(it) } ?: FoodIcons.categoryKey(FoodCategories.LEGACY))
        val portion = portion(false)
        val amount = FoodInputDraft.grams(NumberInput.raw(actual))
        val manual = amount == 100.0
        actual.background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), DesignUi.shape(activity, Color.WHITE, 12))
            addState(intArrayOf(), DesignUi.shape(activity, if (manual) DesignUi.forest else Color.WHITE, 12))
        }
        val colors = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(DesignUi.ink, if (manual) Color.WHITE else DesignUi.ink))
        actual.setTextColor(colors); actual.setHintTextColor(colors)
        val preset = presets.getChildAt(1) as Button
        preset.background = DesignUi.shape(activity, if (amount == 150.0) DesignUi.forest else Color.WHITE, 12)
        preset.setTextColor(if (amount == 150.0) Color.WHITE else DesignUi.ink)
        preview.removeAllViews()
        DesignUi.section(preview, "本次摄入", 33); DesignUi.space(preview, 14)
        DesignUi.item(preview, "热量", portion?.let { "${FoodValues.f(it.values.kcal)} kcal" } ?: "待填写")
        DesignUi.space(preview, 8)
        DesignUi.macros(preview, listOf(DesignUi.Macro("蛋白质", portion?.values?.protein, null),
            DesignUi.Macro("脂肪", portion?.values?.fat, null), DesignUi.Macro("碳水化合物", portion?.values?.carbs, null)), targets = false)
        if (original.basis == null && basis.text.isBlank()) help.text = "此旧配方未保存整锅克重，请先填写真实成品克重，再换算本次摄入。"
    }
}
