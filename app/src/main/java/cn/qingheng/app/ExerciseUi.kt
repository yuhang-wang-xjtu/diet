package cn.qingheng.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*

class ExerciseUi(private val activity: Activity, private val store: Store, private val targetDate: String, private val changed: () -> Unit) {
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(12)) }
    private fun label(c: LinearLayout, value: String) = TextView(activity).apply {
        text = value; textSize = 13f; setTextColor(Color.rgb(72, 91, 78)); setPadding(0, dp(8), 0, dp(6)); c.addView(this)
    }
    private fun field(c: LinearLayout, title: String, value: String = "", number: Boolean = false): EditText {
        label(c, title)
        return EditText(activity).apply {
            setText(value); textSize = 16f; minHeight = dp(48); setSingleLine(true)
            inputType = if (number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            c.addView(this)
        }
    }
    private fun spinner(c: LinearLayout, options: List<String>, index: Int = 0) = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options); minimumHeight = dp(48); setSelection(index); c.addView(this)
    }
    private fun button(c: LinearLayout, title: String, click: () -> Unit) = Button(activity).apply { text = title; isAllCaps = false; minHeight = dp(48); setOnClickListener { click() }; c.addView(this) }
    private fun watch(e: EditText, action: () -> Unit) { e.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
        override fun afterTextChanged(s: Editable?) = Unit
    }) }
    private fun selected(s: Spinner, action: () -> Unit) { s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action()
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    } }
    private fun rate(e: EditText, units: Spinner): Double? {
        val raw = Energy.number(e.text.toString(), 30000.0) ?: return null
        return ExerciseMath.perMinute(raw, units.selectedItemPosition == 1).takeIf { it <= 500.0 }
    }
    fun entry(existing: Entry? = null) {
        val date = existing?.date ?: targetDate
        val weight = ExerciseMath.weightAt(store.weights(), date)
        val c = column(); label(c, "$date · 手动运动")
        val name = field(c, "运动种类 / 强度，例如慢跑、较强骑行", existing?.name ?: "")
        val mode = spinner(c, listOf("按单位时间消耗 × 时长", "直接填写额外总消耗"), if (existing != null && existing.exerciseRate == null) 1 else 0)
        val r = field(c, "单位时间消耗", existing?.exerciseRate?.toString() ?: "", true)
        val units = spinner(c, listOf("kcal / 分钟", "kcal / 小时"))
        val kinds = spinner(c, listOf("额外 / 活动消耗（不含静息）", "运动期间总消耗（包含静息）"), if (existing?.exerciseRateKind == "TOTAL") 1 else 0)
        val minutes = field(c, "时长（整数分钟）", existing?.minutes?.toString() ?: "30", true)
        val direct = field(c, "直接填写时：额外总消耗（kcal，不含静息）", if (existing?.exerciseRate == null) existing?.kcal?.toString() ?: "" else "", true)
        val preview = label(c, "")
        label(c, weight?.let { "换算体重：${FoodValues.f(it)} kg（该日或之前最近记录）。总消耗会扣除约1 kcal/kg/小时的静息部分。" } ?: "尚无该日体重：活动消耗可直接计算；选择总消耗时需先记录体重。")
        label(c, "如果这次运动的步数也在当天总步数中，填写下面的重叠步数，从自动步行中扣除。无法确定时，可在步数卡片关闭当天步行计热。")
        val overlap = field(c, "已包含在当日总步数的本次运动步数（没有填0）", existing?.overlapSteps?.toString() ?: "0", true)
        val saveType = CheckBox(activity).apply { text = "把该种类和单位时间消耗保存为快捷运动"; c.addView(this) }
        fun kind() = if (kinds.selectedItemPosition == 1) ExerciseRateKind.TOTAL else ExerciseRateKind.ACTIVE
        fun calculated(): Double? {
            val m = minutes.text.toString().toIntOrNull()?.takeIf { it in 1..1440 } ?: return null
            return if (mode.selectedItemPosition == 1) FoodMath.nonNegative(direct.text.toString(), 50000.0)
            else rate(r, units)?.let { ExerciseMath.calories(it, kind(), m, weight) }
        }
        fun update() {
            val byRate = mode.selectedItemPosition == 0
            r.isEnabled = byRate; units.isEnabled = byRate; kinds.isEnabled = byRate; direct.isEnabled = !byRate; saveType.isEnabled = byRate
            preview.text = calculated()?.let { "本次计入额外消耗：${FoodValues.f(it)} kcal" } ?: "填写有效时长和消耗；总消耗模式还需要体重。"
        }
        listOf(r, minutes, direct).forEach { watch(it) { update() } }; listOf(mode, units, kinds).forEach { selected(it) { update() } }; update()
        button(c, "选择已保存的运动种类") {
            val types = store.exerciseTemplates()
            if (types.isEmpty()) Toast.makeText(activity, "暂无快捷运动，可勾选上面的保存选项，或进入管理新增", Toast.LENGTH_LONG).show()
            else AlertDialog.Builder(activity).setTitle("快捷运动").setItems(types.map { "${it.name} · ${FoodValues.f(it.kcalPerMinute)} kcal/分钟" }.toTypedArray()) { _, i ->
                val t = types[i]; name.setText(t.name); r.setText(t.kcalPerMinute.toString()); units.setSelection(0); kinds.setSelection(if (t.kind == ExerciseRateKind.TOTAL) 1 else 0); mode.setSelection(0); update()
            }.setNegativeButton("取消", null).show()
        }
        button(c, "管理运动种类与强度") { manage() }
        val d = AlertDialog.Builder(activity).setTitle(if (existing == null) "记录运动" else "编辑运动").setView(ScrollView(activity).apply { addView(c) })
            .setNegativeButton("取消", null).setPositiveButton("保存", null).apply {
                if (existing != null) setNeutralButton("删除") { _, _ -> AlertDialog.Builder(activity).setTitle("删除这条运动？").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.delete(existing); changed() }.show() }
            }.create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val m = minutes.text.toString().toIntOrNull(); val steps = overlap.text.toString().toLongOrNull(); val kcal = calculated()
            when {
                name.text.isBlank() || name.text.length > 80 -> name.error = "请输入1–80字运动名称"
                m == null || m !in 1..1440 -> minutes.error = "请输入1–1440整数分钟"
                steps == null || steps !in 0..300000 -> overlap.error = "请输入0–300000整数步数"
                kcal == null || !kcal.isFinite() || kcal > 50000 -> preview.text = "请核对消耗值（至多50000 kcal）与体重；单位时间值需大于0且不超过500 kcal/分钟。"
                else -> {
                    val usedRate = if (mode.selectedItemPosition == 0) rate(r, units) else null
                    val usedKind = if (usedRate == null) ExerciseRateKind.ACTIVE else kind()
                    val details = if (usedRate == null) "用户填写额外总消耗" else "${FoodValues.f(usedRate)} kcal/分钟 × $m 分钟；${if (usedKind == ExerciseRateKind.ACTIVE) "活动消耗" else "总消耗，按${weight}kg扣除静息部分"}"
                    store.save(Entry(id = existing?.id ?: 0, date = date, kind = "exercise", name = name.text.toString().trim(), kcal = kcal, minutes = m,
                        details = "$details；与当天总步数重叠 $steps 步", exerciseRate = usedRate, exerciseRateKind = usedKind.name, overlapSteps = steps))
                    if (usedRate != null && saveType.isChecked) store.saveExerciseTemplate(ExerciseTemplate(name = name.text.toString().trim(), kcalPerMinute = usedRate, kind = usedKind))
                    d.dismiss(); changed()
                }
            }
        } }; d.show()
    }
    fun manage() {
        val types = store.exerciseTemplates()
        AlertDialog.Builder(activity).setTitle("运动种类 / 强度").setItems(types.map { "${it.name} · ${FoodValues.f(it.kcalPerMinute)} kcal/分钟" }.toTypedArray()) { _, i ->
            val t = types[i]
            AlertDialog.Builder(activity).setTitle(t.name).setItems(arrayOf("编辑单位时间消耗", "删除快捷运动")) { _, action ->
                if (action == 0) template(t)
                else AlertDialog.Builder(activity).setTitle("删除快捷运动？").setMessage("历史运动记录保留。").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.deleteExerciseTemplate(t); manage() }.show()
            }.setNegativeButton("关闭", null).show()
        }.setPositiveButton("＋ 新增种类") { _, _ -> template(null) }.setNegativeButton("关闭", null).show()
    }
    private fun template(t: ExerciseTemplate?) {
        val c = column(); val name = field(c, "运动名称和强度", t?.name ?: "")
        val r = field(c, "单位时间消耗", t?.kcalPerMinute?.toString() ?: "", true)
        val units = spinner(c, listOf("kcal / 分钟", "kcal / 小时"))
        val kind = spinner(c, listOf("额外 / 活动消耗", "包含静息的总消耗"), if (t?.kind == ExerciseRateKind.TOTAL) 1 else 0)
        label(c, "请参考自己的设备或实际情况填写。不同体重、强度的值应分别保存；固定kcal/分钟值不会随体重自动变化。修改快捷运动不会改写历史记录。")
        val d = AlertDialog.Builder(activity).setTitle("快捷运动").setView(ScrollView(activity).apply { addView(c) }).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = rate(r, units)
            if (name.text.isBlank() || name.text.length > 80) name.error = "请输入1–80字名称"
            else if (value == null) r.error = "需大于0且不超过500 kcal/分钟（或30000 kcal/小时）"
            else { store.saveExerciseTemplate(ExerciseTemplate(t?.id ?: 0, name.text.toString().trim(), value, if (kind.selectedItemPosition == 1) ExerciseRateKind.TOTAL else ExerciseRateKind.ACTIVE)); d.dismiss(); manage() }
        } }; d.show()
    }
}
