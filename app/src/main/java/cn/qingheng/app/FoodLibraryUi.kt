package cn.qingheng.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*

/** Offline food library and recipe editor, shared by regular meals and snacks. */
class FoodLibraryUi(private val activity: Activity, private val store: Store, private val onSelect: (FoodSelection) -> Unit) {
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), dp(10)) }
    private fun label(c: LinearLayout, value: String): TextView = TextView(activity).apply {
        text = value; textSize = 13f; setTextColor(Color.rgb(72, 91, 78)); setPadding(0, dp(8), 0, dp(4)); c.addView(this)
    }
    private fun field(c: LinearLayout, title: String, value: String = "", numeric: Boolean = false, multiline: Boolean = false): EditText {
        label(c, title)
        return EditText(activity).apply {
            setText(value); textSize = 15f; minHeight = dp(48)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
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
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (save()) dialog.dismiss() } }
        dialog.show(); return dialog
    }
    fun show() { pickFood { food -> portion(food, "填入记餐表单", onSelect) } }
    private fun pickFood(pick: (QuickFood) -> Unit) {
        val c = column(); label(c, "离线参考值，按实际可食份量换算。可搜索食物名或佐料名。")
        val search = field(c, "搜索", "")
        val list = ListView(activity); c.addView(list, LinearLayout.LayoutParams(-1, dp(255)))
        val foods = store.quickFoods() + FoodCatalog.all
        var filtered = foods
        fun refresh() {
            val q = search.text.toString().trim()
            filtered = foods.filter { it.name.contains(q, true) || it.source.contains(q, true) || FoodCatalog.aliases.any { alias -> alias.key.contains(q) && alias.value.id == it.id } }
            list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, filtered.map { "${if (it.id > 0) "我的 · " else ""}${it.name}" })
        }
        refresh(); watch(search) { refresh() }
        val dialog = AlertDialog.Builder(activity).setTitle("快捷食物与原料").setView(c).setNegativeButton("关闭", null).create()
        list.setOnItemClickListener { _, _, i, _ -> dialog.dismiss(); pick(filtered[i]) }
        button(c, "＋ 自定义食物 · 直接填数值") { dialog.dismiss(); editFood(null) { pick(it) } }
        button(c, "＋ 原材料配方 · 自动合计") { dialog.dismiss(); editRecipe(null) { pick(it) } }
        button(c, "管理我的快捷食物 / 配方") { dialog.dismiss(); manage() }
        dialog.show()
    }
    private fun manage() {
        val foods = store.quickFoods()
        if (foods.isEmpty()) { Toast.makeText(activity, "还没有自定义食物或配方", Toast.LENGTH_SHORT).show(); show(); return }
        AlertDialog.Builder(activity).setTitle("我的快捷食物").setItems(foods.map { it.name }.toTypedArray()) { _, i ->
            val food = foods[i]
            AlertDialog.Builder(activity).setTitle(food.name).setItems(arrayOf("选择份量记餐", "编辑", "删除快捷项")) { _, action -> when (action) {
                0 -> portion(food, "填入记餐表单", onSelect)
                1 -> if (food.ingredients.isEmpty()) editFood(food) { show() } else editRecipe(food) { show() }
                else -> AlertDialog.Builder(activity).setTitle("删除这个快捷项？").setMessage("已经记下的食物与营养不会改变。").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.deleteQuickFood(food); show() }.show()
            } }.setNegativeButton("关闭", null).show()
        }.setNegativeButton("关闭", null).show()
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
        fun selection(): FoodSelection? = Energy.number(amount.text.toString(), 100000.0)?.let { FoodSelection(food, it, units[spinner.selectedItemPosition]) }
        fun update() { preview.text = selection()?.let { "本次：${food.quantity(it.amount, it.unit)}\n${it.values.summary()}" } ?: "请输入有效份量" }
        watch(amount) { update() }
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
    private fun editFood(existing: QuickFood?, saved: (QuickFood) -> Unit) {
        val c = column(); label(c, "填写下面参考克重对应的营养，不要把每100g数值与每份数值混用。能量单位为 kcal。")
        val name = field(c, "名称", existing?.name ?: "")
        val basis = field(c, "这些数值对应多少克（g）", existing?.basisGrams?.toString() ?: "100", true)
        val kcal = field(c, "热量（kcal，允许0）", existing?.values?.kcal?.toString() ?: "", true)
        val protein = field(c, "蛋白质（g，确实没有填0）", existing?.values?.protein?.toString() ?: "", true)
        val carbs = field(c, "碳水化合物（g）", existing?.values?.carbs?.toString() ?: "", true)
        val fat = field(c, "脂肪（g）", existing?.values?.fat?.toString() ?: "", true)
        val sodium = field(c, "钠（mg，不知道可留空）", existing?.values?.sodium?.toString() ?: "", true)
        val unit = field(c, "快捷单位，例如个、勺、份", existing?.unitName ?: "份")
        val unitGrams = field(c, "一个快捷单位的克重（g，留空同参考克重）", existing?.unitGrams?.toString() ?: "", true)
        val notes = field(c, "备注 / 包装或数据来源", existing?.let { "${it.source}\n${it.sourceUrl}\n${it.note}" } ?: "", multiline = true)
        form("自定义快捷食物", c, "保存快捷食物") {
            val g = Energy.number(basis.text.toString(), 100000.0)
            val numbers = listOf(kcal to 50000.0, protein to 5000.0, carbs to 5000.0, fat to 5000.0)
            val bad = numbers.firstOrNull { FoodMath.nonNegative(it.first.text.toString(), it.second) == null }
            val na = FoodMath.nonNegative(sodium.text.toString(), 1000000.0)
            val ug = if (unitGrams.text.isBlank()) g else Energy.number(unitGrams.text.toString(), 100000.0)
            when {
                name.text.isBlank() || name.text.length > 80 -> { name.error = "请输入1–80字的名称"; false }
                g == null -> { basis.error = "请输入有效参考克重"; false }
                bad != null -> { bad.first.error = "请输入0–${bad.second.toInt()}的数值"; false }
                sodium.text.isNotBlank() && na == null -> { sodium.error = "请输入0–1000000 mg或留空"; false }
                ug == null -> { unitGrams.error = "请输入有效单位克重或留空"; false }
                unit.text.isBlank() || unit.text.length > 20 -> { unit.error = "请输入1–20字的单位"; false }
                notes.text.length > 4000 -> { notes.error = "备注最多4000字"; false }
                else -> {
                    val values = numbers.map { FoodMath.nonNegative(it.first.text.toString(), it.second)!! }
                    saved(store.saveQuickFood(QuickFood(existing?.id ?: 0, name.text.toString().trim(), g, FoodValues(values[0], values[1], values[2], values[3], na),
                        unit.text.toString().trim(), ug, "用户自定义", note = notes.text.toString().trim())))
                    true
                }
            }
        }
    }
    private fun editRecipe(existing: QuickFood?, saved: (QuickFood) -> Unit) {
        val c = column(); val items = existing?.ingredients?.toMutableList() ?: mutableListOf()
        val name = field(c, "菜名 / 配方名称", existing?.name ?: "")
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
                val parsed = FoodParser.parse(raw.text.toString(), store.quickFoods())
                when {
                    parsed.errors.isNotEmpty() -> raw.error = parsed.errors.joinToString("\n")
                    items.size + parsed.items.size > 100 -> raw.error = "每道配方最多100项原料"
                    else -> { items.addAll(parsed.items); raw.text.clear(); refresh() }
                }
            }
        }
        label(c, "“个、根、碗、勺”使用条目注明的估重，请逐项核对。油、糖、盐、酱油都可以作为原料。计算假设所填原料全部进入成品；残油、弃汤和烹调损失需自行调整。")
        form("原材料配方计算", c, "保存配方") {
            val grams = if (cooked.text.isBlank()) null else Energy.number(cooked.text.toString(), 100000.0)
            when {
                name.text.isBlank() || name.text.length > 80 -> { name.error = "请输入1–80字的菜名"; false }
                items.isEmpty() -> { raw.error = "请至少添加一项原料"; false }
                raw.text.isNotBlank() -> { raw.error = "这段文字还没加入配方，请先识别添加或清空"; false }
                cooked.text.isNotBlank() && grams == null -> { cooked.error = "请输入有效熟重，或留空按整锅分摊"; false }
                else -> { saved(store.saveQuickFood(FoodMath.recipe(name.text.toString().trim(), items, grams, existing?.id ?: 0))); true }
            }
        }
    }
}
