package cn.qingheng.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*

/** Offline food library and recipe editor, shared by regular meals and snacks. */
class FoodLibraryUi(private val activity: Activity, private val store: Store, private val photos: FoodPhotoPicker, private val onSelect: (QuickFood?) -> Unit) {
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), dp(10)) }
    private fun label(c: LinearLayout, value: String): TextView = TextView(activity).apply {
        text = value; textSize = 13f; setTextColor(Color.rgb(72, 91, 78)); setPadding(0, dp(8), 0, dp(4)); c.addView(this)
    }
    private fun field(c: LinearLayout, title: String, value: String = "", numeric: Boolean = false, multiline: Boolean = false): EditText {
        label(c, title)
        return EditText(activity).apply {
            setText(value); textSize = 15f; minHeight = dp(48)
            inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            if (numeric) NumberInput.configure(this, value)
            setSingleLine(!multiline); if (multiline) minLines = 2
            c.addView(this)
        }
    }
    private fun button(c: LinearLayout, title: String, action: () -> Unit): Button = Button(activity).apply {
        text = title; textSize = 13f; isAllCaps = false; minHeight = dp(48); setOnClickListener { action() }; c.addView(this)
    }
    private fun watch(field: EditText, update: () -> Unit) { field.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = update()
        override fun afterTextChanged(s: Editable?) = Unit
    }) }
    private fun form(title: String, body: LinearLayout, saveLabel: String, save: () -> Boolean): AlertDialog {
        val dialog = AlertDialog.Builder(activity).setTitle(title).setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("取消", null).setPositiveButton(saveLabel, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (photos.busy) Toast.makeText(activity, "图片处理中，请稍候再保存", Toast.LENGTH_SHORT).show() else if (save()) dialog.dismiss()
        } }
        dialog.show(); return dialog
    }
    fun show() { pickFood(createManual = { onSelect(null) }) { onSelect(it) } }
    private fun pickFood(createManual: (() -> Unit)? = null, pick: (QuickFood) -> Unit) {
        val c = column(); val location = label(c, "先选择分类，再选择食物。离线参考值，按实际份量换算。")
        var category: String? = null
        val search = field(c, "搜索", "")
        val back = button(c, "‹ 返回分类") { category = null; search.text.clear() }
        val list = FoodTiles.grid(activity); c.addView(list, LinearLayout.LayoutParams(-1, dp(330)))
        val foods = store.quickFoods() + FoodCatalog.all
        var filtered = foods
        var categories = FoodCategories.categories(foods)
        fun refresh() {
            val q = NumberInput.raw(search).trim()
            back.visibility = if (category == null) View.GONE else View.VISIBLE
            filtered = foods.filter { (category == null || FoodCategories.of(it) == category) && (it.name.contains(q, true) || FoodCatalog.aliases.any { alias -> alias.key.contains(q) && alias.value.id == it.id }) }
            categories = FoodCategories.categories(foods).filter { q.isBlank() || it.contains(q, true) || filtered.any { f -> FoodCategories.of(f) == it } }
            location.text = category?.let { "$it · ${filtered.size}项，点击方格选择实际份量" } ?: "先选择分类，再选择食物"
            list.adapter = FoodTiles(activity, if (category == null) categories.map { name -> FoodTile(name, "${foods.count { FoodCategories.of(it) == name }}项") } else filtered.map { FoodTiles.food(it) })
        }
        back.setOnClickListener { category = null; search.text.clear(); refresh() }
        refresh(); watch(search) { refresh() }
        val dialog = AlertDialog.Builder(activity).setTitle("快捷食物与原料").setView(ScrollView(activity).apply { addView(c) }).setNegativeButton("关闭", null).create()
        list.setOnItemClickListener { _, _, i, _ -> if (category == null) { category = categories[i]; search.text.clear(); refresh() } else { dialog.dismiss(); pick(filtered[i]) } }
        button(c, if (createManual == null) "＋ 自定义食物" else "＋ 手动输入食物") {
            dialog.dismiss()
            if (createManual != null) createManual() else editFood(null, category ?: "菜品") { pick(it) }
        }
        button(c, "＋ 原材料配方 · 自动合计") { dialog.dismiss(); editRecipe(null, category ?: "菜品") { pick(it) } }
        button(c, "管理已有菜品 · 分类 / 图片 / 编辑") { dialog.dismiss(); activity.startActivity(Intent(activity, FoodManagerActivity::class.java)) }
        dialog.show()
        dialog.window?.setLayout(-1, -2)
    }
    fun edit(food: QuickFood?, recipe: Boolean = food?.ingredients?.isNotEmpty() == true, saved: (QuickFood) -> Unit) {
        if (recipe) editRecipe(food, "菜品", saved) else editFood(food, "菜品", saved)
    }
    private data class Meta(val category: String, val image: String)
    private fun metadata(c: LinearLayout, existing: QuickFood?, defaultCategory: String): () -> Meta? {
        label(c, "分类标签（可选择或输入新分类）")
        val category = AutoCompleteTextView(activity).apply {
            setSingleLine(true); minHeight = dp(48); threshold = 0
            setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, FoodCategories.categories(store.quickFoods() + FoodCatalog.all)))
            setText(existing?.let { FoodCategories.of(it) } ?: defaultCategory, false)
            setOnClickListener { showDropDown() }; c.addView(this)
        }
        var image = existing?.image ?: ""
        val preview = ImageView(activity); c.addView(preview, LinearLayout.LayoutParams(-1, dp(120))); FoodPhotos.display(preview, image)
        button(c, "选择食物图片（仅保存在手机）") { photos.choose { key -> image = key; FoodPhotos.display(preview, key) } }
        button(c, "使用默认图片") {
            if (photos.busy) Toast.makeText(activity, "图片处理中，请稍候", Toast.LENGTH_SHORT).show()
            else { image = ""; FoodPhotos.display(preview, image) }
        }
        return {
            val value = NumberInput.raw(category).trim()
            if (value.isBlank() || value.length > 30) { category.error = "请输入1–30字的分类"; null } else Meta(value, image)
        }
    }
    private fun portion(food: QuickFood, action: String, chosen: (FoodSelection) -> Unit, initial: FoodIngredient? = null) {
        val c = column()
        val actual = initial?.takeIf { food.basisGrams != null }?.let { food.factor(it.amount, it.unit) * food.basisGrams!! }
        val fields = FoodNutritionInput(activity, c, food.name, FoodInputDraft.fromFood(food, actual))
        label(c, "填写参考营养并手动输入实际克数。这里的修改只用于本次原料，不会改写已保存的快捷食物。")
        label(c, food.details())
        form(food.name, c, action) {
            val reference = fields.reference(requireMacros = true)
            val portion = fields.portion()
            when {
                fields.name.text.isBlank() -> { fields.name.error = "请输入食物名称"; false }
                reference == null || portion == null -> false
                else -> {
                    val snapshot = food.copy(name = NumberInput.raw(fields.name).trim(), basisGrams = portion.basis,
                        values = FoodValues(reference.kcal, reference.protein!!, reference.carbs!!, reference.fat!!, reference.sodium))
                    chosen(FoodSelection(snapshot, portion.actual, FoodUnit.GRAMS)); true
                }
            }
        }
    }
    private fun editFood(existing: QuickFood?, defaultCategory: String = "菜品", saved: (QuickFood) -> Unit) {
        val c = column()
        val fields = FoodNutritionInput(activity, c, existing?.name ?: "", existing?.let { FoodInputDraft.fromFood(it) }
            ?: FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null))
        label(c, "保存快捷食物时保存参考营养；实际克数仅供本次预览。三大营养素确实没有时填0。")
        val meta = metadata(c, existing, defaultCategory)
        val unit = field(c, "快捷单位，例如个、勺、份", existing?.unitName ?: "份")
        val unitGrams = field(c, "一个快捷单位的克重（g，留空同参考克重）", existing?.unitGrams?.toString() ?: "", true)
        val notes = field(c, "备注 / 包装或数据来源", existing?.note ?: "", multiline = true)
        form("自定义快捷食物", c, "保存快捷食物") {
            val appearance = meta()
            val reference = fields.reference(requireMacros = true)
            val grams = fields.referenceGrams()
            val ug = if (unitGrams.text.isBlank()) { if (existing != null && existing.unitGrams == null) null else grams } else FoodMath.number(NumberInput.raw(unitGrams))
            when {
                appearance == null || reference == null || grams == null -> false
                fields.name.text.isBlank() || fields.name.text.length > 80 -> { fields.name.error = "请输入1–80字的名称"; false }
                unitGrams.text.isNotBlank() && ug == null -> { unitGrams.error = "请输入有效数值或留空（允许负数）"; false }
                unit.text.isBlank() || unit.text.length > 20 -> { unit.error = "请输入1–20字的单位"; false }
                notes.text.length > 4000 -> { notes.error = "备注最多4000字"; false }
                else -> {
                    val unchanged = existing != null && fields.referenceUnchanged()
                    val food = QuickFood(id = existing?.id ?: 0, name = NumberInput.raw(fields.name).trim(),
                        basisGrams = if (unchanged) existing!!.basisGrams else grams,
                        values = if (unchanged) existing!!.values else FoodValues(reference.kcal, reference.protein!!, reference.carbs!!, reference.fat!!, reference.sodium),
                        unitName = NumberInput.raw(unit).trim(), unitGrams = ug, source = existing?.source ?: "用户自定义", sourceUrl = existing?.sourceUrl ?: "",
                        note = NumberInput.raw(notes).trim(), category = appearance.category, image = appearance.image)
                    saved(store.saveQuickFood(food)); true
                }
            }
        }
    }

    private fun editRecipe(existing: QuickFood?, defaultCategory: String = "菜品", saved: (QuickFood) -> Unit) {
        val c = column(); val items = existing?.ingredients?.toMutableList() ?: mutableListOf()
        val name = field(c, "菜名 / 配方名称", existing?.name ?: "")
        val meta = metadata(c, existing, defaultCategory)
        val cooked = field(c, "整锅成品熟重（g，可留空）", existing?.basisGrams?.toString() ?: "", true)
        label(c, "可暂不填熟重以保存配方；按克记餐前需补充整锅成品克重。原料按可食部分填写，不能用带壳重量代替去壳重量。")
        val total = label(c, "")
        val rows = column(); c.addView(rows)
        fun refresh() {
            total.text = runCatching { "整锅估算：\n${FoodMath.sum(items).summary()}" }.getOrElse { "合计结果不是有限数值，请检查原料计算式" }
            rows.removeAllViews()
            items.forEachIndexed { index, item ->
                button(rows, "${item.food.name} · ${item.food.quantity(item.amount, item.unit)}") {
                    AlertDialog.Builder(activity).setTitle(item.food.name).setItems(arrayOf("修改数量 / 单位", "移除原料")) { _, action ->
                        if (action == 1) { items.removeAt(index); refresh() }
                        else portion(item.food, "更新原料", { selected -> items[index] = FoodIngredient(selected.food, selected.amount, selected.unit); refresh() }, item)
                    }.setNegativeButton("取消", null).show()
                }
            }
        }
        refresh()
        button(c, "＋ 从食物与佐料列表添加") {
            if (items.size >= 100) Toast.makeText(activity, "每道配方最多100项原料", Toast.LENGTH_SHORT).show()
            else pickFood { food -> portion(food, "加入配方", { selected -> items.add(FoodIngredient(selected.food.copy(ingredients = emptyList()), selected.amount, selected.unit)); refresh() }) }
        }
        val raw = field(c, "文字快捷输入（添加前会校验）", multiline = true)
        raw.hint = "两个鸡蛋、一个西红柿、10g花生油、2g盐"
        button(c, "识别并添加上面的原料") {
            if (raw.text.length > 4000) raw.error = "每次最多4000字"
            else {
                val parsed = FoodParser.parse(NumberInput.raw(raw), store.quickFoods())
                when {
                    parsed.errors.isNotEmpty() -> raw.error = parsed.errors.joinToString("\n")
                    items.size + parsed.items.size > 100 -> raw.error = "每道配方最多100项原料"
                    else -> { items.addAll(parsed.items); raw.text.clear(); refresh() }
                }
            }
        }
        label(c, "“个、根、碗、勺”使用条目注明的估重，请逐项核对。油、糖、盐、酱油都可以作为原料。计算假设所填原料全部进入成品；残油、弃汤和烹调损失需自行调整。")
        form("原材料配方计算", c, "保存配方") {
            val appearance = meta()
            val grams = if (cooked.text.isBlank()) null else FoodMath.number(NumberInput.raw(cooked))?.takeIf { it != 0.0 }
            when {
                appearance == null -> false
                name.text.isBlank() || name.text.length > 80 -> { name.error = "请输入1–80字的菜名"; false }
                items.isEmpty() -> { raw.error = "请至少添加一项原料"; false }
                raw.text.isNotBlank() -> { raw.error = "这段文字还没加入配方，请先识别添加或清空"; false }
                cooked.text.isNotBlank() && grams == null -> { cooked.error = "请输入有效熟重，或留空按整锅分摊"; false }
                runCatching { FoodMath.sum(items) }.isFailure -> { raw.error = "原料合计不是有限数值，请检查计算式"; false }
                else -> { saved(store.saveQuickFood(FoodMath.recipe(NumberInput.raw(name).trim(), items, grams, existing?.id ?: 0).copy(category = appearance.category, image = appearance.image))); true }
            }
        }
    }
}
