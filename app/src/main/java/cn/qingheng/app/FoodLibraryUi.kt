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
            setText(value); textSize = 15f; minHeight = dp(48); hint = title
            inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            if (numeric) NumberInput.configure(this, value)
            setSingleLine(!multiline); if (multiline) minLines = 2
            c.addView(this)
        }
    }
    private fun button(c: LinearLayout, title: String, action: () -> Unit): Button = Button(activity).apply {
        text = title; textSize = 14f; isAllCaps = false; background = DesignUi.shape(activity, DesignUi.soft, 13); setTextColor(DesignUi.green); minHeight = dp(48); setOnClickListener { action() }; c.addView(this)
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
        dialog.show(); FormKeyboard(body) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }; return dialog
    }
    fun show(subtitle: String = "按分类选择食物") { pickFood(createManual = { onSelect(null) }, subtitle = subtitle) { onSelect(it) } }
    private fun pickFood(createManual: (() -> Unit)? = null, subtitle: String = "快捷食物 / 选择食物", pick: (QuickFood) -> Unit) {
        val foods = store.quickFoods() + FoodCatalog.all
        val sheet = DesignSheet(activity, "快捷食物", subtitle)
        val c = sheet.content
        DesignUi.section(c, "按分类选择", 4); DesignUi.space(c, 17)
        fun open(category: String) { sheet.dismiss(); pickCategory(category, foods, createManual, subtitle, pick) }
        val staples = foods.filter { FoodCategories.of(it) == "主食" }
        val feature = DesignUi.foodFeature(activity, "主食", staples.take(3).joinToString("、") { it.name.substringBefore('（') }, "选择主食   →", "design:rice", "design:rice", true)
        feature.setOnClickListener { open("主食") }; c.addView(feature)
        DesignUi.space(c, 16)
        val search = field(c, "搜索食物或分类")
        val grid = FoodTiles.grid(activity)
        c.addView(grid, LinearLayout.LayoutParams(-1, dp(360)))
        var categories = emptyList<String>()
        fun refresh() {
            val query = search.text.toString().trim()
            categories = FoodCategories.categories(foods).filter { it != "主食" || query.isNotBlank() }.filter { category ->
                query.isBlank() || category.contains(query, true) || foods.any { FoodCategories.of(it) == category && it.name.contains(query, true) }
            }
            grid.adapter = FoodTiles(activity, categories.map { category -> FoodTile(category, "${foods.count { FoodCategories.of(it) == category }}项", FoodIcons.categoryKey(category)) })
        }
        grid.setOnItemClickListener { _, _, position, _ -> open(categories[position]) }
        watch(search) { refresh() }; refresh()
        button(c, if (createManual == null) "＋ 自定义食物" else "＋ 手动输入食物") {
            sheet.dismiss(); if (createManual != null) createManual() else editFood(null) { pick(it) }
        }
        button(c, "＋ 原材料配方 · 自动合计") { sheet.dismiss(); editRecipe(null) { pick(it) } }
        button(c, "管理已有菜品 · 分类 / 图片 / 编辑") { sheet.dismiss(); activity.startActivity(Intent(activity, FoodManagerActivity::class.java)) }
        sheet.show()
    }
    private fun pickCategory(category: String, foods: List<QuickFood>, createManual: (() -> Unit)?, subtitle: String, pick: (QuickFood) -> Unit) {
        val candidates = foods.filter { FoodCategories.of(it) == category }
        var selected = candidates.firstOrNull { it.id == -1L } ?: candidates.firstOrNull()
        val sheet = DesignSheet(activity, category, "快捷食物 / 选择食物", "选择食物")
        val c = sheet.content
        DesignUi.section(c, "食物列表", 4); DesignUi.space(c, 16)
        val featured = column().apply { setPadding(0, 0, 0, 0) }; c.addView(featured)
        fun preview() {
            featured.removeAllViews()
            val food = selected
            if (food != null) {
                featured.addView(DesignUi.foodFeature(activity, food.name.substringBefore('（'), food.basisGrams?.let { "每 ${FoodValues.f(it)} 克" } ?: "整锅配方", "${FoodValues.f(food.values.kcal)} kcal", food.image, FoodPhotos.fallback(food)))
                sheet.saveButton?.text = "选择${food.name.substringBefore('（')}"
            } else featured.addView(DesignUi.text(activity, "这个分类还没有食物，可先创建自定义食物。", 14f, DesignUi.muted))
            sheet.saveButton?.isEnabled = food != null
        }
        preview(); DesignUi.space(c, 16)
        val search = field(c, "搜索$category")
        val grid = FoodTiles.grid(activity); c.addView(grid, LinearLayout.LayoutParams(-1, dp(320)))
        var filtered = candidates
        fun refresh() {
            val q = search.text.toString().trim(); filtered = candidates.filter { it.name.contains(q, true) || FoodCatalog.aliases.any { alias -> alias.key.contains(q) && alias.value.id == it.id } }
            grid.adapter = FoodTiles(activity, filtered.map { FoodTiles.food(it).copy(selected = it.id == selected?.id) })
        }
        grid.setOnItemClickListener { _, _, position, _ -> selected = filtered[position]; preview(); refresh() }
        watch(search) { refresh() }; refresh()
        button(c, "‹ 返回分类") { sheet.dismiss(); pickFood(createManual, subtitle, pick) }
        sheet.navigateBack = { sheet.dismiss(); pickFood(createManual, subtitle, pick) }
        sheet.setOnCancelListener { pickFood(createManual, subtitle, pick) }
        sheet.saveButton?.setOnClickListener { selected?.let { sheet.dismiss(); pick(it) } }
        sheet.show()
    }
    fun edit(food: QuickFood?, recipe: Boolean = food?.ingredients?.isNotEmpty() == true, saved: (QuickFood) -> Unit) {
        if (recipe) editRecipe(food, "菜品", saved) else editFood(food, "菜品", saved)
    }
    private data class Meta(val category: String, val image: String)
    private fun metadata(c: LinearLayout, existing: QuickFood?, defaultCategory: String): () -> Meta? {
        label(c, "分类标签（可选择或输入新分类）")
        val category = AutoCompleteTextView(activity).apply {
            setSingleLine(true); minHeight = dp(48); threshold = 0; hint = "选择或输入分类标签"
            setAdapter(FoodCategoryAdapter(activity, FoodCategories.categories(store.quickFoods() + FoodCatalog.all)))
            setText(existing?.let { FoodCategories.of(it) } ?: defaultCategory, false)
            setOnClickListener { showDropDown() }; c.addView(this)
        }
        var image = existing?.image ?: ""
        val preview = ImageView(activity); c.addView(preview, LinearLayout.LayoutParams(-1, dp(120)))
        fun refreshImage() {
            val fallback = if (existing != null && FoodCategories.of(existing) == FoodCategories.clean(category.text.toString()))
                FoodIcons.defaultFor(existing) else FoodIcons.categoryKey(category.text.toString())
            FoodPhotos.display(preview, image, fallback)
        }
        refreshImage(); watch(category) { refreshImage() }
        button(c, "从食物图标素材中选择") {
            if (photos.busy) Toast.makeText(activity, "图片处理中，请稍候", Toast.LENGTH_SHORT).show()
            else FoodIconPicker.show(activity, image, category.text.toString()) { key -> image = key; refreshImage() }
        }
        button(c, "从手机选择照片（仅保存在手机）") { photos.choose { key -> image = key; refreshImage() } }
        button(c, "使用默认图片") {
            if (photos.busy) Toast.makeText(activity, "图片处理中，请稍候", Toast.LENGTH_SHORT).show()
            else { image = ""; refreshImage() }
        }
        return {
            val value = NumberInput.raw(category).trim()
            if (value.isBlank() || value.length > 30) { category.error = "请输入1–30字的分类"; null } else Meta(value, image)
        }
    }
    private fun portion(food: QuickFood, action: String, chosen: (FoodSelection) -> Unit, initial: FoodIngredient? = null) {
        val c = column()
        val actual = initial?.takeIf { food.basisGrams != null }?.let { food.factor(it.amount, it.unit) * food.basisGrams!! }
        val fields = FoodNutritionInput(activity, c, food.name, FoodInputDraft.fromFood(food, actual), food)
        label(c, "填写参考营养，实际克数可留空按100g。这里的修改只用于本次原料，不会改写已保存的快捷食物。")
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
            ?: FoodInputDraft(FoodInputDraft.DEFAULT_GRAMS, null, null), existing)
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
