package cn.qingheng.app

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.widget.*

/** Shared nutrition form. Unweighed legacy foods retain their whole-portion reference. */
class FoodNutritionInput(private val activity: Activity, private val parent: LinearLayout, name: String = "",
    initial: FoodInputDraft = FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null), food: QuickFood? = null, private val referenceOnly: Boolean = false) {
    private fun dp(n: Int) = DesignUi.dp(activity, n)
    private fun raw(value: Double?) = value?.toString() ?: ""
    private var original = initial
    private var unit = initial.unit
    val usesPortions get() = unit == FoodUnit.PORTIONS
    private val defaultAmount get() = if (usesPortions) 1.0 else FoodInputDraft.DEFAULT_GRAMS
    private var selectedFood = food
    private val unitName get() = original.unitName
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
    private val amountSection = DesignUi.column(activity).also { parent.addView(it); if (referenceOnly) it.visibility = android.view.View.GONE }
    private val amountTitle = DesignUi.text(activity, "实际食用量（g，可输入算式）", 19f, bold = true, line = 26).also { DesignUi.space(amountSection, 24); amountSection.addView(it) }
    private val presets = DesignUi.row(activity).also { DesignUi.space(amountSection, 12); amountSection.addView(it) }
    val actual = EditText(activity).apply {
        setSingleLine(true); hint = "100 克"; textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = false; gravity = android.view.Gravity.CENTER_VERTICAL; minHeight = dp(48); setPadding(dp(14), dp(8), dp(14), dp(8))
        contentDescription = "实际食用克数，可输入计算式，留空按100克"
        NumberInput.configure(this, raw(initial.actual ?: defaultAmount)); presets.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(14) })
    }
    private val unitToggle = DesignUi.button(activity, "", false) {
        val amount = FoodInputDraft.grams(NumberInput.raw(actual), defaultAmount)
        val grams = FoodInputDraft.grams(NumberInput.raw(basis), original.basis)
        val unitGrams = if (usesPortions) grams else original.unitGrams ?: grams
        val converted = if (usesPortions) amount?.let { n -> unitGrams?.let { n * it } } else amount?.let { n -> unitGrams?.takeIf { it != 0.0 }?.let { n / it } }
        if (!usesPortions && grams != null && grams != 0.0 && unitGrams != null) {
            reference(false)?.scale(unitGrams / grams)?.let { n ->
                NumberInput.setValue(kcal, n.kcal); NumberInput.setValue(protein, n.protein); NumberInput.setValue(fat, n.fat)
                NumberInput.setValue(carbs, n.carbs); NumberInput.setValue(sodium, n.sodium); NumberInput.setValue(basis, unitGrams)
            }
        }
        unit = if (usesPortions) FoodUnit.GRAMS else FoodUnit.PORTIONS
        NumberInput.setValue(actual, converted ?: defaultAmount)
        if (!usesPortions && original.basis == null) referenceFields.visibility = android.view.View.VISIBLE
        update(); (actual.getTag(R.id.form_keyboard) as? FormKeyboard)?.refresh()
    }
    private val preview = DesignUi.column(activity).also { amountSection.addView(it) }
    private val help = DesignUi.text(activity, "", 12f, DesignUi.muted).also { DesignUi.space(amountSection, 12); amountSection.addView(it) }
    init {
        amountSection.addView(unitToggle)
        referenceFields.visibility = if (referenceOnly || initial.reference == null) android.view.View.VISIBLE else android.view.View.GONE
        fun toggle() {
            if (referenceOnly) return
            val expanded = referenceFields.visibility == android.view.View.VISIBLE
            if (expanded && referenceFields.hasFocus()) actual.requestFocus()
            referenceFields.visibility = if (expanded) android.view.View.GONE else android.view.View.VISIBLE
            (actual.getTag(R.id.form_keyboard) as? FormKeyboard)?.refresh()
        }
        editReference.setOnClickListener { toggle() }; referenceLabel.setOnClickListener { toggle() }
        presets.addView(DesignUi.button(activity, "150 克", false) { NumberInput.setValue(actual, if (usesPortions) 0.5 else 150.0); update() }, LinearLayout.LayoutParams(0, dp(48), 1f))
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
        val draft = food?.let { FoodInputDraft.fromFood(it, actualGrams) } ?: FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null); original = draft; unit = draft.unit
        NumberInput.setValue(basis, draft.basis?.takeUnless { it == FoodInputDraft.DEFAULT_GRAMS }); NumberInput.setValue(actual, draft.actual ?: defaultAmount)
        val n = draft.reference
        NumberInput.setValue(kcal, n?.kcal); NumberInput.setValue(protein, n?.protein); NumberInput.setValue(fat, n?.fat); NumberInput.setValue(carbs, n?.carbs); NumberInput.setValue(sodium, n?.sodium)
        referenceFields.visibility = if (draft.reference == null) android.view.View.VISIBLE else android.view.View.GONE
        update(); (actual.getTag(R.id.form_keyboard) as? FormKeyboard)?.refresh()
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
        if (usesPortions && !referenceOnly || referenceOnly && original.basis == null && basis.text.isBlank()) return null
        val grams = FoodInputDraft.grams(NumberInput.raw(basis), if (original.basis == null) null else FoodInputDraft.DEFAULT_GRAMS)
        if (validate && (grams == null || grams == 0.0)) basis.error = if (original.basis == null && basis.text.isBlank())
            "按克记录需填写成品克重，也可切回按整份记录" else "请输入有效非零克数或留空按100g；无法除以0"
        if (validate && (grams == null || grams == 0.0)) reveal(basis)
        return grams?.takeIf { it != 0.0 }
    }
    fun portion(validate: Boolean = true): FoodPortion? {
        val n = reference(validate)
        val g = if (usesPortions) 1.0 else referenceGrams(validate)
        val amount = FoodInputDraft.grams(NumberInput.raw(actual), defaultAmount)
        if (validate && amount == null) actual.error = if (usesPortions) "请输入有效份数或计算式，或留空按1份（允许负数和0）" else "请输入有效克数或计算式，或留空按100g（允许负数和0）"
        if (n == null || g == null || amount == null) return null
        val p = FoodPortion(g, amount, n, unit, if (usesPortions) FoodInputDraft.grams(NumberInput.raw(basis), original.basis) else null, unitName)
        if (!p.values.valid()) { if (validate) actual.error = "计算结果不是有限数值，请检查计算式"; return null }
        return p
    }
    fun referenceUnchanged() = unit == original.unit && reference(false) == original.reference &&
        (if (usesPortions) FoodInputDraft.grams(NumberInput.raw(basis), original.basis) else referenceGrams(false)) == original.basis
    fun actualUnchanged() = unit == original.unit && FoodInputDraft.grams(NumberInput.raw(actual), defaultAmount) == (original.actual ?: original.defaultAmount)
    private fun update() {
        val basisVisibility = if (usesPortions && !referenceOnly) android.view.View.GONE else android.view.View.VISIBLE
        referenceFields.getChildAt(referenceFields.indexOfChild(basis) - 1).visibility = basisVisibility
        basis.visibility = basisVisibility
        basis.hint = if (original.basis == null) "按克记录时填写成品克重" else "留空默认为100g"
        amountTitle.text = if (usesPortions) "食用数量（$unitName，可留空按1）" else "实际食用量（g，可输入算式）"
        actual.hint = if (usesPortions) "留空按1$unitName" else "留空按100克"
        actual.contentDescription = if (usesPortions) "食用数量，单位$unitName，可输入计算式，留空按1" else "实际食用克数，可输入计算式，留空按100克"
        unitToggle.visibility = if (original.unit == FoodUnit.PORTIONS) android.view.View.VISIBLE else android.view.View.GONE
        val unitGrams = FoodInputDraft.grams(NumberInput.raw(basis), original.basis)
        unitToggle.text = if (usesPortions) "改按克记录${unitGrams?.let { "（1$unitName = ${FoodValues.f(it)}g）" } ?: "（需填写成品克重）"}" else "改按$unitName 记录"
        if (usesPortions && unitGrams != null) help.text = "1$unitName = ${FoodValues.f(unitGrams)}g，数量留空按1；支持负数及计算式。"
        else help.setText(if (usesPortions) R.string.food_portion_help else if (original.basis == null) R.string.food_unknown_weight_help else R.string.food_gram_help)
        val reference = reference(false)
        val grams = referenceGrams(false)
        referenceLabel.text = when {
            reference != null && usesPortions -> "每1$unitName${unitGrams?.let { "（${FoodValues.f(it)}g）" } ?: ""} 含 ${FoodValues.f(reference.kcal)} kcal · 编辑"
            reference != null && grams != null -> "每 ${FoodValues.f(grams)} g 含 ${FoodValues.f(reference.kcal)} kcal · 编辑"
            else -> "填写参考热量与营养数值   ›"
        }
        val food = selectedFood
        FoodPhotos.display(picture, food?.image.orEmpty(), food?.let { FoodPhotos.fallback(it) } ?: FoodIcons.categoryKey(FoodCategories.LEGACY))
        if (referenceOnly) return
        val portion = portion(false)
        val amount = FoodInputDraft.grams(NumberInput.raw(actual), defaultAmount)
        val manual = amount == defaultAmount
        actual.background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), DesignUi.shape(activity, Color.WHITE, 12))
            addState(intArrayOf(), DesignUi.shape(activity, if (manual) DesignUi.forest else Color.WHITE, 12))
        }
        val colors = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(DesignUi.ink, if (manual) Color.WHITE else DesignUi.ink))
        actual.setTextColor(colors); actual.setHintTextColor(colors)
        val preset = presets.getChildAt(1) as Button
        preset.text = if (usesPortions) "0.5$unitName" else "150 克"
        val presetSelected = amount == if (usesPortions) 0.5 else 150.0
        preset.background = DesignUi.shape(activity, if (presetSelected) DesignUi.forest else Color.WHITE, 12)
        preset.setTextColor(if (presetSelected) Color.WHITE else DesignUi.ink)
        preview.removeAllViews()
        DesignUi.section(preview, "本次摄入", 33); DesignUi.space(preview, 14)
        DesignUi.item(preview, "热量", portion?.let { "${FoodValues.f(it.values.kcal)} kcal" } ?: "待填写")
        DesignUi.space(preview, 8)
        DesignUi.macros(preview, listOf(DesignUi.Macro("蛋白质", portion?.values?.protein, null),
            DesignUi.Macro("脂肪", portion?.values?.fat, null), DesignUi.Macro("碳水化合物", portion?.values?.carbs, null)), targets = false)
    }
}
