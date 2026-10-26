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
class FoodLibraryUi(private val activity: Activity, private val store: Store, private val photos: FoodPhotoPicker, private val onSelect: (FoodSelection) -> Unit) {
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
    fun show() { pickFood { food -> portion(food, "填入记餐表单", onSelect) } }
    private fun pickFood(pick: (QuickFood) -> Unit) {
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
        button(c, "＋ 自定义食物 · 直接填数值") { dialog.dismiss(); editFood(null, category ?: "菜品") { pick(it) } }
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
        label(c, if (food.basisGrams == null) "以下为整锅总量，尚未填写成品熟重。" else "参考份量 ${FoodValues.f(food.basisGrams)} g 的营养：")
        label(c, food.values.summary())
        val amount = field(c, "实际份量（0.5 表示半份 / 半锅）", initial?.amount?.toString() ?: "1", true)
        val units = if (food.basisGrams == null) listOf(FoodUnit.PORTIONS) else listOf(FoodUnit.PORTIONS, FoodUnit.GRAMS)
        val spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, units.map { if (it == FoodUnit.GRAMS) "克（g）" else food.unitName })
            setSelection(units.indexOf(initial?.unit ?: FoodUnit.PORTIONS).coerceAtLeast(0)); minimumHeight = dp(48); c.addView(this)
        }
        val preview = label(c, "")
        fun selection(): FoodSelection? = try {
            Energy.number(NumberInput.raw(amount), 100000.0)?.let { FoodSelection(food, it, units[spinner.selectedItemPosition]).also { selected -> selected.values } }
        } catch (_: IllegalArgumentException) { null }
        fun update() { preview.text = selection()?.let { "本次：${food.quantity(it.amount, it.unit)}\n${it.values.summary()}" } ?: "请输入有效份量" }
        NumberInput.onFinished(amount) { update() }
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { update() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        label(c, food.details()); update()
        val dialog = AlertDialog.Builder(activity).setTitle(food.name).setView(ScrollView(activity).apply { addView(c) })
            .setNegativeButton("取消", null).setPositiveButton(action, null).create()
        button(c, if (food.ingredients.isEmpty()) "复制并修改数值 / 份量" else "修改这道菜的原材料配方") {
            dialog.dismiss()
            if (food.ingredients.isEmpty()) editFood(food.copy(id = 0)) { portion(it, action, chosen) }
            else editRecipe(food.copy(id = 0)) { portion(it, action, chosen) }
        }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val selected = selection()
            if (selected == null) amount.error = "请输入大于0、至多100000的份量"
            else { dialog.dismiss(); chosen(selected) }
        } }; dialog.show()
    }
    private fun editFood(existing: QuickFood?, defaultCategory: String = "菜品", saved: (QuickFood) -> Unit) {
        val c = column(); label(c, "填写下面参考克重对应的营养，不要把每100g数值与每份数值混用。能量单位为 kcal。")
        val name = field(c, "名称", existing?.name ?: "")
        val meta = metadata(c, existing, defaultCategory)
        val basis = field(c, "这些数值对应多少克（g）", existing?.basisGrams?.toString() ?: "100", true)
        val kcal = field(c, "热量（kcal，允许0）", existing?.values?.kcal?.toString() ?: "", true)
        val protein = field(c, "蛋白质（g，确实没有填0）", existing?.values?.protein?.toString() ?: "", true)
        val carbs = field(c, "碳水化合物（g）", existing?.values?.carbs?.toString() ?: "", true)
        val fat = field(c, "脂肪（g）", existing?.values?.fat?.toString() ?: "", true)
        val sodium = field(c, "钠（mg，不知道可留空）", existing?.values?.sodium?.toString() ?: "", true)
        val unit = field(c, "快捷单位，例如个、勺、份", existing?.unitName ?: "份")
        val unitGrams = field(c, "一个快捷单位的克重（g，留空同参考克重）", existing?.unitGrams?.toString() ?: "", true)
        val notes = field(c, "备注 / 包装或数据来源", existing?.note ?: "", multiline = true)
        form("自定义快捷食物", c, "保存快捷食物") {
            val appearance = meta()
            val g = Energy.number(NumberInput.raw(basis), 100000.0)
            val numbers = listOf(kcal to 50000.0, protein to 5000.0, carbs to 5000.0, fat to 5000.0)
            val bad = numbers.firstOrNull { FoodMath.nonNegative(NumberInput.raw(it.first), it.second) == null }
            val na = FoodMath.nonNegative(NumberInput.raw(sodium), 1000000.0)
            val ug = if (unitGrams.text.isBlank()) g else Energy.number(NumberInput.raw(unitGrams), 100000.0)
            when {
                appearance == null -> false
                name.text.isBlank() || name.text.length > 80 -> { name.error = "请输入1–80字的名称"; false }
                g == null -> { basis.error = "请输入有效参考克重"; false }
                bad != null -> { bad.first.error = "请输入0–${bad.second.toInt()}的数值"; false }
                sodium.text.isNotBlank() && na == null -> { sodium.error = "请输入0–1000000 mg或留空"; false }
                ug == null -> { unitGrams.error = "请输入有效单位克重或留空"; false }
                unit.text.isBlank() || unit.text.length > 20 -> { unit.error = "请输入1–20字的单位"; false }
                notes.text.length > 4000 -> { notes.error = "备注最多4000字"; false }
                else -> {
                    val values = numbers.map { FoodMath.nonNegative(NumberInput.raw(it.first), it.second)!! }
                    val food = QuickFood(existing?.id ?: 0, NumberInput.raw(name).trim(), g, FoodValues(values[0], values[1], values[2], values[3], na),
                        NumberInput.raw(unit).trim(), ug, existing?.source ?: "用户自定义", existing?.sourceUrl ?: "", NumberInput.raw(notes).trim(), category = appearance.category, image = appearance.image)
                    saved(store.saveQuickFood(food))
                    true
                }
            }
        }
    }
    private fun editRecipe(existing: QuickFood?, defaultCategory: String = "菜品", saved: (QuickFood) -> Unit) {
        val c = column(); val items = existing?.ingredients?.toMutableList() ?: mutableListOf()
        val name = field(c, "菜名 / 配方名称", existing?.name ?: "")
        val meta = metadata(c, existing, defaultCategory)
        val cooked = field(c, "整锅成品熟重（g，可留空）", existing?.basisGrams?.toString() ?: "", true)
        label(c, "没称熟重时可按0.5锅、1锅分摊；称了熟重后也可按吃掉的克数记录。原料按可食部分填写，不能用带壳重量代替去壳重量。")
        val total = label(c, "")
        val rows = column(); c.addView(rows)
        fun refresh() {
            total.text = "整锅估算：\n${FoodMath.sum(items).summary()}"
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
            val grams = if (cooked.text.isBlank()) null else Energy.number(NumberInput.raw(cooked), 100000.0)
            when {
                appearance == null -> false
                name.text.isBlank() || name.text.length > 80 -> { name.error = "请输入1–80字的菜名"; false }
                items.isEmpty() -> { raw.error = "请至少添加一项原料"; false }
                raw.text.isNotBlank() -> { raw.error = "这段文字还没加入配方，请先识别添加或清空"; false }
                cooked.text.isNotBlank() && grams == null -> { cooked.error = "请输入有效熟重，或留空按整锅分摊"; false }
                else -> { saved(store.saveQuickFood(FoodMath.recipe(NumberInput.raw(name).trim(), items, grams, existing?.id ?: 0).copy(category = appearance.category, image = appearance.image))); true }
            }
        }
    }
}
