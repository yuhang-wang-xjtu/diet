package cn.qingheng.app

import androidx.activity.ComponentActivity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val ink = Color.rgb(31, 53, 44)
    private val green = Color.rgb(36, 122, 98)
    private val muted = Color.rgb(116, 127, 117)
    private val bg = Color.rgb(246, 247, 242)
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var date = LocalDate.now()
    private var tab = 0
    private var exactWasAllowed = false
    private val stepsClient by lazy { StepsClient(this) }
    private val stepsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stepsJob: Job? = null
    private var isForeground = false
    private val stepsMessages = mutableMapOf<String, String>()
    private val stepsPermissionLauncher = registerForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { granted ->
        prefs.edit().putBoolean("steps_permission_waiting", false).apply()
        if (granted.containsAll(stepsClient.permissions)) consumePendingStepsRead()
        else {
            val pending = prefs.getString("pending_steps_date", null)
            prefs.edit().remove("pending_steps_date").apply()
            pending?.let { stepsMessages[it] = "步数权限未授予。请在健康连接中允许轻衡读取步数；小米端还需允许写入步数。" }
            if (::store.isInitialized && isForeground) render()
        }
    }
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val appUpdates by lazy { AppUpdates(this) { render() } }
    private val baseline get() = if (prefs.contains("baseline")) prefs.getFloat("baseline", 0f).toDouble() else null
    private val automatic get() = prefs.getBoolean("automatic_body", false)
    private fun estimate(target: String = date.toString()) = ProfileMath.estimate(ProfileMath.at(store.profiles(), target), store.weights(), target)
    private fun goal(key: String): Double? = if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
    private fun walkingConfig() = WalkingConfig(prefs.getFloat("walking_met", 3.8f).toDouble(), prefs.getFloat("walking_cadence", 100f).toDouble())
    private fun walkingEstimate(target: String, entries: List<Entry>) = ExerciseMath.walking(store.stepDay(target), entries, ExerciseMath.weightAt(store.weights(), target), walkingConfig())
    private fun stepsOrigin() = StepsPolicy.origin(prefs.getString("steps_origin", null))
    private fun phoneStepsSelected() = prefs.getString("steps_backend", "health_connect") == "phone_sensor"
    private fun sourceLabel(source: String): String = when {
        source == "manual" -> "手动填写"
        source == "phone_sensor" -> "手机本机计步 · 从启用后记录"
        source == "health_connect" -> "健康连接 · 系统汇总"
        StepsPolicy.isHealthSource(source) -> "健康连接 · ${appLabel(source.substringAfter(':'))}"
        else -> source
    }
    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { pkg }
    private val formatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun fmt(n: Double) = String.format(Locale.US, "%.0f", n)
    private fun kg(n: Double) = String.format(Locale.US, "%.1f", n)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        if (savedInstanceState == null) prefs.edit().putBoolean("steps_permission_waiting", false).remove("pending_steps_date").apply()
        savedInstanceState?.let { date = LocalDate.parse(it.getString("date")); tab = it.getInt("tab") }
        MealReminders.reschedule(this)
        exactWasAllowed = MealReminders.exactAllowed(this)
        render()
        if (savedInstanceState == null) handleMealIntent(intent)
        if (!prefs.getBoolean("notification_asked", false)) {
            prefs.edit().putBoolean("notification_asked", true).apply()
            askNotificationPermission()
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleMealIntent(intent) }
    private fun handleMealIntent(source: Intent) {
        if (source.action != MealReminders.OPEN) return
        val meal = Meal.from(source.getStringExtra("meal"))
        val targetDate = try { LocalDate.parse(source.getStringExtra("date")) } catch (_: Exception) { return }
        if (!meal.remindable) return
        date = targetDate; tab = 1; render(); entryDialog("food", selectedMeal = meal)
        // A rotation or task restoration must not reopen an already-consumed reminder.
        setIntent(Intent(this, MainActivity::class.java))
    }
    override fun onResume() {
        super.onResume()
        isForeground = true
        if (!::store.isInitialized) return
        val exact = MealReminders.exactAllowed(this)
        if (exact != exactWasAllowed) { MealReminders.reschedule(this); exactWasAllowed = exact }
        render()
        appUpdates.resume()
        if (phoneStepsSelected()) {
            if (prefs.getBoolean("phone_steps_enabled", false)) startPhoneStepsService()
            syncSteps(LocalDate.now())
        } else if (!consumePendingStepsRead() && prefs.getBoolean("health_steps_sync", false) && stepsJob?.isActive != true) syncSteps(LocalDate.now())
    }
    private fun consumePendingStepsRead(): Boolean {
        // Do not let onResume consume the target before the Activity Result callback has delivered permission.
        if (prefs.getBoolean("steps_permission_waiting", false)) return true
        if (!isForeground || !::store.isInitialized) return false
        val pending = prefs.getString("pending_steps_date", null) ?: return false
        prefs.edit().remove("pending_steps_date").apply()
        val target = try { LocalDate.parse(pending) } catch (_: Exception) { LocalDate.now() }
        syncSteps(target, replaceManual = true)
        return true
    }
    override fun onPause() { isForeground = false; stepsJob?.cancel(); appUpdates.pause(); super.onPause() }
    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 40)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 40) render()
        if (requestCode == 41) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) enablePhoneSteps()
            else { Toast.makeText(this, "未允许身体活动权限，本机计步未开启", Toast.LENGTH_LONG).show(); render() }
        }
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("date", date.toString()); out.putInt("tab", tab) }
    private fun shape(color: Int, radius: Int = 22) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun space(parent: LinearLayout, h: Int = 16) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(h))) }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false; minHeight = dp(48); minimumHeight = dp(48)
        setTextColor(if (primary) Color.WHITE else green); background = shape(if (primary) green else Color.rgb(231, 238, 225), 14)
        setPadding(dp(12), dp(4), dp(12), dp(4)); setOnClickListener { action() }
    }
    private fun card(parent: LinearLayout = body, color: Int = Color.WHITE): LinearLayout {
        val c = column().apply { background = shape(color); setPadding(dp(20), dp(20), dp(20), dp(20)) }
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }); return c
    }
    private fun heading(value: String, sub: String? = null) { body.addView(text(value, 27f, bold = true)); sub?.let { body.addView(text(it, 13f, muted)) }; space(body, 22) }
    private fun render() {
        root = column().apply { setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else { @Suppress("DEPRECATION") v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom) }
            insets
        }
        val brand = row().apply { setPadding(dp(24), dp(16), dp(24), dp(8)) }
        brand.addView(text("◒  轻衡", 22f, green, true), LinearLayout.LayoutParams(0, -2, 1f))
        brand.addView(text("DAILY BALANCE", 10f, muted, true)); root.addView(brand)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = column().apply { setPadding(dp(24), dp(16), dp(24), dp(24)) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (tab) { 0 -> overview(); 1 -> diary(); 2 -> trends(); else -> settings() }
        val nav = row().apply { setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Color.WHITE) }
        listOf("◉\n今日", "≡\n记录", "↗\n趋势", "⚙\n设置").forEachIndexed { i, s ->
            nav.addView(TextView(this).apply { text = s; textSize = 13f; gravity = Gravity.CENTER; setTextColor(if (i == tab) green else muted)
                setPadding(0, dp(4), 0, dp(4)); if (i == tab) background = shape(Color.rgb(235, 242, 230), 12)
                contentDescription = listOf("今日概览", "每日记录", "体重趋势", "设置")[i]; setOnClickListener { tab = i; render() }
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        root.addView(nav); setContentView(root); root.requestApplyInsets()
    }
    private fun dateRow() {
        val r = row()
        r.addView(button("‹") { date = date.minusDays(1); render() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        r.addView(text(date.format(formatter), 14f, ink, true).apply { gravity = Gravity.CENTER; setOnClickListener { pickDate() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        r.addView(button("›") { date = date.plusDays(1); render() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(r); space(body)
    }
    private fun pickDate() { DatePickerDialog(this, { _, y, m, d -> date = LocalDate.of(y, m + 1, d); render() }, date.year, date.monthValue - 1, date.dayOfMonth).show() }
    private fun overview() {
        heading(if (date == LocalDate.now()) "每一天，都有进步。" else "回看这一天。", "记录饮食与运动，看见自己的变化")
        dateRow()
        val entries = store.entries(date.toString())
        val walking = walkingEstimate(date.toString(), entries)
        val bodyEstimate = estimate()
        val daily = if (automatic) bodyEstimate?.expenditure else baseline
        val t = Energy.totals(entries, daily, walking.kcal ?: 0.0, addExercise = !automatic)
        if (store.profiles().isEmpty()) {
            val setup = card(); setup.addView(text("先认识一下你", 19f, bold = true))
            setup.addView(text("填写年龄、性别、身高、体重与活动水平，即可自动计算消耗和营养参考目标。", 13f, muted))
            setup.addView(button("填写个人资料", true) { profileDialog() })
        }
        val hero = card(color = green)
        hero.addView(text("当日热量缺口 · 估算", 14f, Color.rgb(217, 236, 216)))
        space(hero, 8)
        hero.addView(text(t.deficit?.let { if (it >= 0) fmt(it) else "−${fmt(-it)}" } ?: "待设置", 48f, Color.WHITE, true))
        hero.addView(text(if (t.deficit == null) "填写有效个人资料或手动消耗 · 点击设置" else "kcal  /  ${if (t.deficit!! >= 0) "消耗大于摄入" else "摄入大于消耗"}", 13f, Color.rgb(217, 236, 216)))
        hero.setOnClickListener { tab = 3; render() }
        space(hero)
        hero.addView(text(if (automatic) "全天总消耗（已含活动） − 饮食摄入" else "日常消耗  +  额外运动  −  饮食摄入", 12f, Color.WHITE))
        val metrics = card()
        metric(metrics, "饮食摄入", "${fmt(t.intake)} kcal", "本日 ${entries.count { it.kind == "food" }} 条记录")
        space(metrics); metric(metrics, "运动记录", "${fmt(t.exercise)} kcal", if (automatic) "活动已包含在全天估算中，此处展示记录，不再叠加" else "含手动运动与计入的步行净消耗")
        space(metrics); metric(metrics, "${if (automatic) "全天总消耗" else "日常消耗"}", daily?.let { "${fmt(it)} kcal" } ?: "尚未设置", if (automatic) "静息代谢 × 活动系数 · 维持体重参考" else "包含日常活动，不含另记的额外运动")
        if (automatic) {
            space(metrics); metric(metrics, "BMR参考", bodyEstimate?.let { "${fmt(it.resting)} kcal" } ?: "资料或体重不足", "Mifflin–St Jeor 静息代谢估算，非实测BMR")
        }
        val actions = row()
        actions.addView(button("＋ 饮食", true) { entryDialog("food") }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(10) })
        actions.addView(button("＋ 运动") { entryDialog("exercise") }, LinearLayout.LayoutParams(0, dp(50), 1f))
        body.addView(actions); space(body)
        stepsCard(entries)
        val extras = row()
        extras.addView(button("＋ 零食") { entryDialog("food", selectedMeal = Meal.SNACK) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(10) })
        extras.addView(button("回顾食物") { foodHistory() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(extras); space(body)
        body.addView(button("快捷记餐 · 食物库 / 原材料配方", true) { quickRecord() }); space(body)
        val meals = card(); meals.addView(text("三餐记录", 19f, bold = true))
        Meal.regular.forEach { meal ->
            space(meals, 10)
            meals.addView(button("${meal.label} · ${MealTiming.status(entries.any { it.kind == "food" && it.meal == meal.key }, store.isSkipped(date.toString(), meal))}") {
                tab = 1; render(); entryDialog("food", selectedMeal = meal)
            })
        }
        meals.addView(text(if (!MealReminders.enabled(this)) "三餐提醒已关闭，可在设置中开启。" else if (!MealReminders.notificationsAllowed(this)) "通知未允许，请在设置中开启以接收记餐提醒。" else "提醒 ${Meal.regular.joinToString(" / ") { MealReminders.timeText(this, it) }} · 零食不提醒", 11f, muted))
        nutritionCard(entries)
        val w = store.weights().find { it.date == date.toString() }
        val wc = card()
        metric(wc, "当日体重", w?.let { "${kg(it.kg)} kg" } ?: "还没记录", "固定时段记录，更容易比较")
        space(wc, 12); wc.addView(button(if (w == null) "＋ 记录体重" else "修改体重") { weightDialog(w) })
        body.addView(text("饮食与运动为空时按 0 计算；完整记录后，缺口才有参考意义。", 12f, muted))
    }
    private fun metric(parent: LinearLayout, label: String, value: String, hint: String) {
        val r = row(); r.addView(text(label, 14f, muted), LinearLayout.LayoutParams(0, -2, 1f)); r.addView(text(value, 20f, ink, true)); parent.addView(r)
        parent.addView(text(hint, 11f, muted))
    }
    private fun diary() {
        heading("每日记录", "一点一滴，积累成改变")
        dateRow()
        body.addView(button("快捷记餐 · 食物库 / 原材料配方", true) { quickRecord() }); space(body)
        body.addView(button("回顾之前的食物") { foodHistory() }); space(body)
        val entries = store.entries(date.toString())
        stepsCard(entries)
        Meal.entries.forEach { meal ->
            val items = entries.filter { it.kind == "food" && Meal.from(it.meal) == meal }
            if (meal == Meal.UNSPECIFIED && items.isEmpty()) return@forEach
            val c = card(); val r = row()
            r.addView(text(meal.label, 19f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(button("＋ 添加") { entryDialog("food", selectedMeal = meal) }); c.addView(r)
            if (meal.remindable) {
                val skipped = store.isSkipped(date.toString(), meal)
                c.addView(text(MealTiming.status(items.isNotEmpty(), skipped), 13f, green))
                if (items.isEmpty()) {
                    space(c, 8)
                    c.addView(button(if (skipped) "撤销没吃标记" else "这一餐没吃") {
                        if (skipped) { store.undoSkip(date.toString(), meal); render() } else skipMeal(date.toString(), meal)
                    })
                }
            } else if (meal == Meal.SNACK) c.addView(text("随时记录，不发送提醒；计入当日热量与营养。", 12f, muted))
            else c.addView(text("旧版食物保留在这里，编辑可指定餐次。", 12f, muted))
            if (items.isNotEmpty()) c.addView(text("合计 ${fmt(items.sumOf { it.kcal })} kcal", 13f, green))
            items.forEach { entryRow(c, it) }
            if (items.isEmpty() && !meal.remindable) { space(c, 8); c.addView(text("暂无记录", 13f, muted)) }
        }
        val exercise = card(); val line = row()
        line.addView(text("运动", 19f, bold = true), LinearLayout.LayoutParams(0, -2, 1f)); line.addView(button("＋ 添加") { entryDialog("exercise") }); exercise.addView(line)
        entries.filter { it.kind == "exercise" }.forEach { entryRow(exercise, it) }
        nutritionCard(entries)
    }
    private fun entryRow(parent: LinearLayout, e: Entry) {
        space(parent, 12)
        val item = column().apply { background = shape(bg, 12); setPadding(dp(14), dp(12), dp(14), dp(12)); setOnClickListener { entryDialog(e.kind, e) } }
        val line = row(); line.addView(text(e.name, 15f, bold = true), LinearLayout.LayoutParams(0, -2, 1f)); line.addView(text("${fmt(e.kcal)} kcal", 14f, green, true)); item.addView(line)
        item.addView(text(if (e.kind == "exercise") "${e.minutes} 分钟 · 点击编辑" else "点击编辑或删除", 11f, muted)); parent.addView(item)
        if (e.kind == "food") item.addView(text("蛋白 ${e.protein?.let { kg(it) } ?: "未填"} / 碳水 ${e.carbs?.let { kg(it) } ?: "未填"} / 脂肪 ${e.fat?.let { kg(it) } ?: "未填"} g", 11f, muted))
        if (e.kind == "food") item.addView(text("钠 ${e.sodium?.let { fmt(it) + " mg" } ?: "未填"}${if (e.details.isNotBlank()) " · 含份量 / 配方说明" else ""}", 11f, muted))
        if (e.kind == "exercise") item.addView(text("${e.exerciseRate?.let { "${if (e.exerciseRateKind == "TOTAL") "总" else "活动"}消耗 ${kg(it)} kcal/分钟 · " } ?: "直接填写 · "}重叠步数 ${e.overlapSteps}", 11f, muted))
    }
    private fun foodHistory() {
        val foods = store.entries().filter { it.kind == "food" }
        if (foods.isEmpty()) { Toast.makeText(this, "还没有食物记录", Toast.LENGTH_SHORT).show(); return }
        val dates = foods.groupBy { it.date }
        val keys = dates.keys.sortedDescending()
        AlertDialog.Builder(this).setTitle("历史食物 · 选择日期回顾").setItems(keys.map { key ->
            "$key · ${dates.getValue(key).size} 条 · ${fmt(dates.getValue(key).sumOf { it.kcal })} kcal"
        }.toTypedArray()) { _, i -> date = LocalDate.parse(keys[i]); tab = 1; render() }.setNegativeButton("关闭", null).show()
    }
    private fun skipMeal(target: String, meal: Meal, after: () -> Unit = {}) {
        if (store.skip(target, meal)) {
            MealReminders.resolve(this, target, meal); after(); render()
            Toast.makeText(this, "已标记${meal.label}没吃，可在记录页撤销", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "这一餐已有食物记录，不能标记没吃", Toast.LENGTH_SHORT).show()
    }
    private fun nutritionCard(entries: List<Entry>) {
        val c = card(); c.addView(text("每日营养", 19f, bold = true))
        c.addView(text("${date} · 已记录摄入 / 每日参考目标", 12f, muted))
        val reference = if (automatic) estimate() else null
        val proteinTarget = goal("protein_goal") ?: if (automatic) reference?.protein else Nutrition.proteinTarget(store.weights(), date.toString(), null)
        val nutrients = listOf(
            Triple("蛋白质", Nutrition.total(entries) { it.protein }, proteinTarget),
            Triple("碳水化合物", Nutrition.total(entries) { it.carbs }, goal("carbs_goal") ?: reference?.carbs),
            Triple("脂肪", Nutrition.total(entries) { it.fat }, goal("fat_goal") ?: reference?.fat))
        nutrients.forEach { (label, total, target) ->
            space(c, 16)
            c.addView(text(label, 14f, bold = true))
            c.addView(text("${if (total.recorded == 0) "未记录" else "${kg(total.grams)} g"} / ${target?.let { "${kg(it)} g" } ?: "目标未设置"}", 20f, green, true))
            if (target != null && total.recorded > 0) {
                val percent = total.grams / target * 100
                c.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100; progress = percent.toInt().coerceIn(0, 100)
                    progressTintList = android.content.res.ColorStateList.valueOf(green)
                    contentDescription = "$label 已记录 ${kg(total.grams)} 克，目标 ${kg(target)} 克"
                }, LinearLayout.LayoutParams(-1, dp(12)))
                c.addView(text("已记录量达到目标的 ${fmt(percent)}% · ${if (total.grams < target) "相差 ${kg(target - total.grams)} g" else "已达到参考目标"}", 11f, muted))
            }
            c.addView(text(when {
                total.foods == 0 -> "当日暂无饮食记录"
                total.recorded < total.foods -> "${total.foods - total.recorded} 条饮食未填此项，当前合计不完整"
                else -> "已合计 ${total.recorded} 条饮食记录"
            }, 11f, muted))
        }
        val sodium = Nutrition.total(entries) { it.sodium }
        space(c, 16); c.addView(text("钠摄入 · 已记录 ${if (sodium.recorded == 0) "未知" else "${fmt(sodium.grams)} mg"}", 15f, bold = true))
        c.addView(text("${sodium.recorded}/${sodium.foods} 条饮食填写钠；未知部分不当作0。钠以mg计，不是食盐克数。", 11f, muted))
        space(c, 12)
        c.addView(text(if (automatic) "自动目标：维持体重热量按蛋白质20%、碳水50%、脂肪30%分配；自定义项优先。缺少有效资料或体重时不推算。" else "手动模式：蛋白质默认按健康成人参考 0.83 g/kg/天，其余目标可自定义。", 11f, muted))
        c.addView(button("设置营养目标") { tab = 3; render() })
    }
    private fun trends() {
        heading("体重的轨迹", "关注长期变化，也接纳每天的起伏")
        val all = store.weights()
        val recent = all.filter { LocalDate.parse(it.date) >= LocalDate.now().minusDays(29) && LocalDate.parse(it.date) <= LocalDate.now() }
        val c = card()
        c.addView(text("最近 30 天", 14f, muted))
        if (recent.isEmpty()) { space(c); c.addView(text("从第一次记录开始", 24f, bold = true)); c.addView(text("记录体重后，这里会出现变化曲线。", 13f, muted)) }
        else {
            c.addView(text("${kg(recent.last().kg)} kg", 36f, bold = true))
            c.addView(text(if (recent.size < 2) "再记录一次，即可比较变化" else "较本时段首次 ${String.format(Locale.US, "%+.1f", recent.last().kg - recent.first().kg)} kg", 13f, green))
            c.addView(WeightChart(this, recent), LinearLayout.LayoutParams(-1, dp(195)))
        }
        space(c); c.addView(button("＋ 记录所选日期体重", true) { weightDialog(all.find { it.date == date.toString() }) })
        body.addView(text("所选日期：${date} · 点击更改", 12f, muted).apply { setOnClickListener { pickDate() } }); space(body)
        val history = card(); history.addView(text("全部体重记录", 19f, bold = true))
        if (all.isEmpty()) { space(history); history.addView(text("暂无记录", 14f, muted)) }
        all.asReversed().forEach { w ->
            val r = row().apply { setPadding(0, dp(14), 0, dp(14)); setOnClickListener { weightDialog(w) } }
            r.addView(text(w.date, 14f, muted), LinearLayout.LayoutParams(0, -2, 1f)); r.addView(text("${kg(w.kg)} kg  ›", 17f, bold = true)); history.addView(r)
        }
    }
    private fun profileSettings() {
        val today = LocalDate.now().toString()
        val profile = ProfileMath.at(store.profiles(), today)
        val e = estimate(today)
        val c = card(); c.addView(text("个人资料与自动计算", 19f, bold = true)); space(c, 8)
        c.addView(text(profile?.let { "${ProfileMath.ageAt(it, today)}岁 · ${it.sex.label} · ${kg(it.heightCm)} cm\n${it.activity.label} · 资料生效于 ${it.date}" } ?: "填写基本资料后自动估算，无需手动填写BMR或营养目标。", 13f, muted))
        if (e != null) {
            space(c, 12)
            c.addView(text("BMR参考 ${fmt(e.resting)} kcal/天\n全天总消耗 ${fmt(e.expenditure)} kcal/天\nBMI ${kg(e.bmi)}", 19f, green, true))
            c.addView(text("维持体重参考：蛋白质 ${kg(e.protein)}g · 碳水 ${kg(e.carbs)}g · 脂肪 ${kg(e.fat)}g", 13f, muted))
        }
        c.addView(button(if (profile == null) "填写个人资料" else "修改个人资料（从今天生效）", true) { profileDialog() })
        c.addView(Switch(this).apply {
            text = "自动估算消耗和营养"; minHeight = dp(48); isChecked = automatic
            setOnCheckedChangeListener { _, checked ->
                if (checked && e == null) {
                    isChecked = false
                    Toast.makeText(this@MainActivity, "请先填写有效的成人资料和体重", Toast.LENGTH_LONG).show()
                } else { prefs.edit().putBoolean("automatic_body", checked).apply(); render() }
            }
        })
        c.addView(text("面向19–78岁一般健康成人；孕哺期或疾病相关需求请关闭自动估算并填写专业目标。年龄按录入日周年近似增长，可修改校正。体重取对应日期或之前最近一次记录，资料从保存当天生效。", 11f, muted))
        c.addView(text("计算方法与依据 ↗", 13f, green).apply { setPadding(0, dp(12), 0, dp(12)); setOnClickListener {
            AlertDialog.Builder(this@MainActivity).setTitle("估算依据").setMessage("Mifflin–St Jeor：10×体重kg + 6.25×身高cm − 5×年龄 + 性别常数（男+5，女−161），估算静息代谢REE，作为BMR参考，不是实测基础代谢。\n\n总消耗=静息代谢×PAL。活动水平为粗略选项，含平时走路及运动。\n\n维持体重热量默认按蛋白质20%、碳水50%、脂肪30%分配，分别按4、4、9kcal/g换算。该分配是应用默认方案，位于成人AMDR范围内，不是个体处方。自定义营养目标优先。")
                .setPositiveButton("知道了", null).setNeutralButton("代谢公式原文") { _, _ -> openSource("https://pubmed.ncbi.nlm.nih.gov/2305711/") }
                .setNegativeButton("营养参考范围") { _, _ -> openSource("https://www.canada.ca/en/health-canada/services/food-nutrition/healthy-eating/dietary-reference-intakes/tables/reference-values-macronutrients.html") }.show()
        } })
    }
    private fun openSource(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "设备上没有可用浏览器", Toast.LENGTH_SHORT).show() }
    }
    private fun profileDialog() {
        val today = LocalDate.now().toString()
        val previous = ProfileMath.at(store.profiles(), today)
        val c = dialogBody()
        c.addView(text("只需填写这些数据，自动得到维持体重消耗和营养参考。保存会同时更新今天的体重。", 13f, muted))
        fun field(label: String, value: String): EditText { c.addView(text(label, 13f, muted)); return input(label, value, true).also { c.addView(it) } }
        val age = field("年龄（周岁）", previous?.let { ProfileMath.ageAt(it, today).toString() } ?: "")
        c.addView(text("性别（用于代谢公式系数）", 13f, muted))
        val sex = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("请选择") + FormulaSex.entries.map { it.label })
            setSelection(previous?.let { it.sex.ordinal + 1 } ?: 0); minimumHeight = dp(48)
        }; c.addView(sex)
        val height = field("身高（cm）", previous?.heightCm?.toString() ?: "")
        val weight = field("当前体重（kg）", ExerciseMath.weightAt(store.weights(), today)?.toString() ?: "")
        c.addView(text("平时全天活动水平（包括走路和运动）", 13f, muted))
        val activity = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("请选择") + ActivityLevel.entries.map { it.label })
            setSelection(previous?.let { it.activity.ordinal + 1 } ?: 0); minimumHeight = dp(48)
        }; c.addView(activity)
        c.addView(text("从较少活动到较多活动选择最接近的一档；活动系数是粗略估算，可结合长期体重变化调整。", 12f, muted))
        val auto = CheckBox(this).apply { text = "启用一般健康成人自动估算"; isChecked = if (previous == null) true else automatic }; c.addView(auto)
        c.addView(text("自动估算适用19–78岁；孕哺期及疾病相关需求请取消勾选并使用专业目标。默认维持体重，不自动设置减重缺口。已有自定义营养目标继续优先使用，清空即可恢复自动目标。", 12f, muted))
        val d = AlertDialog.Builder(this).setTitle("个人资料").setView(ScrollView(this).apply { addView(c) }).setNegativeButton("取消", null).setPositiveButton("保存并计算", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val a = age.text.toString().toIntOrNull()
            val h = Energy.number(height.text.toString(), 250.0)
            val w = Energy.number(weight.text.toString(), 500.0)
            when {
                a == null || a !in 1..120 -> age.error = "请输入1–120的整数年龄"
                h == null || h < 100 -> height.error = "请输入100–250 cm"
                w == null || w < 20 -> weight.error = "请输入20–500 kg"
                sex.selectedItemPosition == 0 || activity.selectedItemPosition == 0 -> Toast.makeText(this, "请选择性别和活动水平", Toast.LENGTH_SHORT).show()
                auto.isChecked && a !in 19..78 -> age.error = "自动公式仅适用19–78岁；可取消自动估算后保存资料"
                else -> {
                    store.saveProfile(Profile(today, a, FormulaSex.entries[sex.selectedItemPosition - 1], h, ActivityLevel.entries[activity.selectedItemPosition - 1]), Weight(today, w))
                    prefs.edit().putBoolean("automatic_body", auto.isChecked).apply()
                    d.dismiss(); render()
                    Toast.makeText(this, "资料已保存，从今天生效", Toast.LENGTH_SHORT).show()
                }
            }
        } }; d.show()
    }
    private fun settings() {
        heading("按自己的节奏", "你的数据，保存在这台设备上")
        profileSettings()
        walkingSettings()
        reminderSettings()
        nutritionSettings()
        val c = card(); c.addView(text("日常消耗基准", 19f, bold = true)); space(c, 8)
        c.addView(text("填写每日消耗基准，不重复包含另记的运动与步数步行。若你的基准已包含日常走路，请在步数卡片关闭该日步行计热。该基准统一用于所有日期。", 13f, muted)); space(c)
        val field = input("每日消耗（kcal）", baseline?.let { fmt(it) } ?: "", true); c.addView(field); space(c)
        c.addView(button("保存消耗基准", true) {
            val n = Energy.number(field.text.toString(), 20000.0)
            if (n == null) field.error = "请输入大于 0、至多 20000 的数值"
            else { prefs.edit().putFloat("baseline", n.toFloat()).putBoolean("automatic_body", false).apply(); Toast.makeText(this, "已保存并切换到手动消耗模式", Toast.LENGTH_SHORT).show(); render() }
        })
        val explanation = card(); explanation.addView(text("缺口如何计算", 19f, bold = true)); space(explanation, 8)
        explanation.addView(text("自动模式：缺口 = 全天总消耗 − 饮食摄入。活动水平包含平时走路和运动，所有运动记录只展示，不再次叠加。\n\n手动模式：缺口 = 日常消耗基准 + 额外运动（含选中的步行）− 摄入。请确保基准不包含另记的活动。正数代表消耗大于摄入；全天完整记录后才有参考意义。", 14f, muted))
        val data = card(); data.addView(text("数据与隐私", 19f, bold = true)); space(data, 8)
        data.addView(text("无需账号，健康记录保存在本机。仅在你检查更新及下载时访问GitHub，不上传饮食、体重或步数。卸载应用会删除数据。CSV导出包含食物、运动、步数、配方、体重及各项设置。", 13f, muted)); space(data)
        data.addView(button("导出全部记录 · CSV") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "轻衡-${LocalDate.now()}.csv") }, 12) })
        val updates = card(); updates.addView(text("应用更新", 19f, bold = true)); space(updates, 8)
        updates.addView(text("当前版本 ${appUpdates.version}\n更新源：${appUpdates.repo?.slug ?: "尚未配置GitHub仓库"}", 13f, muted))
        val updateStatus = text("", 13f, green); updates.addView(updateStatus); appUpdates.bind(updateStatus)
        updates.addView(button("检查更新 · GitHub", true) { appUpdates.check() })
        updates.addView(button("下载状态 / 安装更新") { appUpdates.continueUpdate() })
        updates.addView(button("配置GitHub更新源") { appUpdates.configure() })
        updates.addView(button("查看最新Release") { appUpdates.openRelease() })
        updates.addView(button("清除更新下载 / 重试") { appUpdates.cancelDownload() })
        updates.addView(text("读取最新正式Release，下载APK后交给系统确认安装。需使用相同签名、递增版本号发布才能覆盖更新。", 11f, muted))
        body.addView(text("轻衡 ${appUpdates.version}  ·  热量与营养数值为估算，仅用于个人记录。", 12f, muted))
    }
    private fun stepsCard(entries: List<Entry>) {
        val target = date.toString(); val day = store.stepDay(target); val walking = walkingEstimate(target, entries)
        val c = card(); c.addView(text("每日步数 · 步行", 19f, bold = true))
        c.addView(text("$target · ${day?.let { "${it.steps} 步" } ?: "未读取"}", 26f, green, true))
        if (day != null) {
            val time = java.time.Instant.ofEpochMilli(day.updatedAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            c.addView(text("${sourceLabel(day.source)} · 缓存更新 $time", 11f, muted))
            c.addView(Switch(this).apply {
                text = "手动模式下本日步行计入缺口"; minHeight = dp(48); isChecked = day.included; isEnabled = !automatic
                setOnCheckedChangeListener { _, value -> store.saveSteps(day.copy(included = value)); render() }
            })
        }
        c.addView(text("步行额外消耗：${walking.kcal?.let { fmt(it) + " kcal" } ?: "待估算"}", 18f, bold = true))
        if (day != null && day.included) c.addView(text("参考体重 ${ExerciseMath.weightAt(store.weights(), target)?.let { kg(it) + " kg" } ?: "未记录"} · 步行时长约 ${kg(walking.minutes)} 分钟", 12f, muted))
        c.addView(text("${walking.message}。扣除手动运动重叠 ${walking.excluded} 步；用于步行 ${walking.steps} 步。", 12f, muted))
        val config = walkingConfig()
        if (automatic) c.addView(text("自动模式已用活动水平估算全天消耗，步行和手动运动均不再叠加。", 12f, green))
        c.addView(text("估算强度 ${kg(config.met)} MET，步频 ${fmt(config.cadence)} 步/分钟；均可在设置中修改，并非实测强度。", 11f, muted))
        stepsMessages[target]?.let { c.addView(text(it, 12f, green)) }
        space(c, 8)
        c.addView(button(if (phoneStepsSelected()) "刷新该日本机步数" else "授权 / 刷新该日健康步数") { syncSteps(date, request = true) })
        c.addView(text(if (phoneStepsSelected()) "当前方式：手机传感器；不读取小米或手环数据" else "读取方式：${stepsOrigin()?.let { appLabel(it) } ?: "系统去重汇总"} · 通过健康连接读取", 12f, muted))
        c.addView(button(if (prefs.getBoolean("phone_steps_enabled", false)) "本机计步状态 / 停止" else "启用手机本机计步（无需健康连接）") { phoneStepsSetup() })
        prefs.getString("phone_steps_error", null)?.let { c.addView(text(it, 12f, muted)) }
        c.addView(button("连接诊断 / 选择步数来源") { diagnoseSteps(date) })
        c.addView(button("小米运动健康连接指南") { xiaomiStepsHelp() })
        c.addView(button("手动补录 / 修正步数") { manualSteps(target) })
        c.addView(text(if (phoneStepsSelected()) "启用后后台记录；打开应用或点刷新查看最新值。需随身携带手机，系统清理或跨午夜数据空档可能造成少计。" else "首次成功连接后，默认在打开应用时同步当天步数；可在设置中关闭。", 11f, muted))
        if (day != null) c.addView(button("清除该日步数") {
            AlertDialog.Builder(this).setTitle("清除本机这一天的步数？").setMessage(if (day.source == "phone_sensor") "会清除该日本机采集及显示步数。若计步仍开启，当天会继续记录后续步数；可先停止本机计步。" else "只清除当日显示记录；健康连接或本机传感器采集的原始数据保留，后续刷新可重新读取。可在设置中停止自动读取/计步。")
                .setNegativeButton("取消", null).setPositiveButton("清除") { _, _ -> stepsJob?.cancel(); store.deleteSteps(target); if (day.source == "phone_sensor") store.deletePhoneDay(target); stepsMessages.remove(target); render() }.show()
        })
        c.addView(text("手动模式下，基准若已包含走路，请关闭本日步行计热；跑步/走路时填重叠步数，避免重复。自动模式按全天活动水平统一估算，不逐条叠加活动。", 11f, muted))
    }
    private fun manualSteps(target: String) {
        if (LocalDate.parse(target) > LocalDate.now()) { Toast.makeText(this, "不能补录未来日期的实际步数", Toast.LENGTH_SHORT).show(); return }
        val day = store.stepDay(target); val c = dialogBody(); c.addView(text("$target · 当天总步数（不是新增步数）", 13f, muted))
        val count = input("当天总步数", day?.steps?.toString() ?: "", true); c.addView(count)
        val include = CheckBox(this).apply { text = "手动模式下把本日步行计入缺口"; isChecked = day?.included ?: true; isEnabled = !automatic; c.addView(this) }
        c.addView(text("手动数值优先保存，自动同步不会覆盖；主动刷新并确认后可恢复健康连接数据。", 12f, muted))
        val d = AlertDialog.Builder(this).setTitle("手动步数").setView(c).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = count.text.toString().trim().toLongOrNull()
            if (n == null || n !in 0..300000) count.error = "请输入0–300000整数步数"
            else { stepsJob?.cancel(); store.saveSteps(StepDay(target, n, "manual", System.currentTimeMillis(), include.isChecked)); stepsMessages[target] = "已保存手动总步数"; d.dismiss(); render() }
        } }; d.show()
    }
    private fun syncSteps(targetDate: LocalDate, request: Boolean = false, replaceManual: Boolean = false) {
        val target = targetDate.toString()
        if (targetDate > LocalDate.now()) { stepsMessages[target] = "未来日期暂时没有实际步数"; render(); return }
        val existing = store.stepDay(target)
        if (existing?.source == "manual" && !replaceManual) {
            if (request) AlertDialog.Builder(this).setTitle("用${if (phoneStepsSelected()) "本机记录" else "健康连接"}替换手动步数？").setMessage("读取成功后覆盖该日总步数，不重复相加。").setNegativeButton("取消", null).setPositiveButton("读取并替换") { _, _ -> syncSteps(targetDate, true, true) }.show()
            else { stepsMessages[target] = "使用手动步数，自动读取不会覆盖"; render() }
            return
        }
        if (phoneStepsSelected()) {
            val phone = store.phoneStepDay(target)
            if (phone == null) stepsMessages[target] = "这一天没有本机计步记录；只能读取启用后采集的数据。启用后走几步，再点刷新。已有值保留。"
            else {
                store.saveSteps(phone.copy(included = existing?.included ?: true))
                stepsMessages[target] = "已读取手机本机记录 ${phone.steps} 步，非小米或手环总步数；当天覆盖更新。"
            }
            render(); return
        }
        val available = try { stepsClient.available() } catch (_: Exception) { false }
        if (!available) {
            stepsMessages[target] = "此设备的健康连接不可用或需安装/更新，可手动填步数"
            if (request && android.os.Build.VERSION.SDK_INT < 28) AlertDialog.Builder(this).setTitle("此系统暂不支持健康连接").setMessage("Android8可使用手动总步数和步行估算。").setNegativeButton("关闭", null).setPositiveButton("手动补录") { _, _ -> manualSteps(target) }.show()
            else if (request) AlertDialog.Builder(this).setTitle("健康连接尚不可用").setMessage("Android9及以上需要可用的Health Connect及步数数据源；也可手动填写当天步数。").setNegativeButton("关闭", null).setPositiveButton("安装 / 更新健康连接") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }
                catch (_: android.content.ActivityNotFoundException) { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) } catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "请通过系统或应用商店安装健康连接", Toast.LENGTH_LONG).show() } }
            }.show()
            render(); return
        }
        stepsJob?.cancel()
        stepsJob = stepsScope.launch {
            stepsMessages[target] = "正在读取健康连接汇总步数…"; render()
            try {
                val permitted = withContext(Dispatchers.IO) { stepsClient.permitted() }
                if (!permitted) {
                    stepsMessages[target] = "尚未授权读取步数；已有缓存保留"
                    if (request) {
                        prefs.edit().putString("pending_steps_date", target).putBoolean("steps_permission_waiting", true).apply()
                        stepsPermissionLauncher.launch(stepsClient.permissions)
                    }
                    render(); return@launch
                }
                if (!prefs.contains("health_steps_sync")) prefs.edit().putBoolean("health_steps_sync", true).apply()
                val origin = stepsOrigin()
                val result = withContext(Dispatchers.IO) { stepsClient.read(targetDate, origin) }
                val count = result.availableCount
                ensureActive()
                val current = store.stepDay(target)
                when {
                    count == null -> stepsMessages[target] = "读取权限正常，但${origin?.let { appLabel(it) } ?: "健康连接"}没有该日可用步数。已有缓存保留。点“连接诊断”检查小米是否已写入数据。"
                    count !in 0..300000 -> stepsMessages[target] = "读取到的步数异常，未覆盖已有记录"
                    current?.source == "manual" && current.updatedAt != existing?.updatedAt -> stepsMessages[target] = "本日手动步数已更新，保留手动值"
                    else -> {
                        store.saveSteps(StepDay(target, count, StepsPolicy.source(origin), System.currentTimeMillis(), current?.included ?: true))
                        stepsMessages[target] = "已读取 $count 步；来源：${result.origins.joinToString { appLabel(it) }.ifEmpty { "健康连接" }}。当天覆盖更新，不重复累加。"
                        render()
                        if (targetDate == LocalDate.now() && prefs.getBoolean("health_steps_sync", false)) backfillRecentSteps()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                prefs.edit().putBoolean("steps_permission_waiting", false).remove("pending_steps_date").apply()
                stepsMessages[target] = stepsFailure(e)
            }
            render()
        }
    }
    private suspend fun backfillRecentSteps() {
        val today = LocalDate.now()
        if (prefs.getString("steps_backfill_date", null) == today.toString()) return
        var successful = true
        for (offset in 1L..7L) {
            val day = today.minusDays(offset); val key = day.toString()
            if (store.stepDay(key)?.source == "manual") continue
            try {
                val origin = stepsOrigin()
                val count = withContext(Dispatchers.IO) { stepsClient.read(day, origin).availableCount }
                currentCoroutineContext().ensureActive()
                if (count != null && count in 0..300000) {
                    val current = store.stepDay(key)
                    if (current?.source != "manual") store.saveSteps(StepDay(key, count, StepsPolicy.source(origin), System.currentTimeMillis(), current?.included ?: true))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { successful = false }
        }
        if (successful) prefs.edit().putString("steps_backfill_date", today.toString()).apply()
    }
    private fun phoneStepsSetup() {
        val enabled = prefs.getBoolean("phone_steps_enabled", false)
        AlertDialog.Builder(this).setTitle("手机本机计步")
            .setMessage("${if (enabled) "本机计步已请求开启。" else "无需健康连接或小米接口，使用手机计步传感器。"}\n\n从启用后的首个传感器读数开始记录，不读取小米或手环历史数据，不代表小米全天总步数。需要身体活动权限，后台记录时有常驻通知。\n\n需随身携带手机；若系统清理后台或手机重启，可能少计，重新打开轻衡可恢复。跨午夜无法分配的步数空档不会猜测计入。可在系统中允许轻衡后台运行并设为不限制省电。\n\n手动填写的步数会优先保留，点刷新可确认替换。当前状态：${prefs.getString("phone_steps_error", null) ?: if (enabled) "已开启，等待/接收传感器数据" else "未开启"}")
            .setPositiveButton(if (enabled) "停止本机计步" else "启用并授权") { _, _ ->
                if (enabled) {
                    prefs.edit().putBoolean("phone_steps_enabled", false).apply()
                    stopService(Intent(this, PhoneStepsService::class.java)); store.resetPhoneCounter(); render()
                } else if ((getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager).getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) == null) {
                    Toast.makeText(this, "系统未提供计步传感器，可手动补录", Toast.LENGTH_LONG).show()
                } else if (android.os.Build.VERSION.SDK_INT >= 29 && checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION), 41)
                } else enablePhoneSteps()
            }.setNeutralButton("改用健康连接") { _, _ ->
                prefs.edit().putString("steps_backend", "health_connect").putBoolean("phone_steps_enabled", false).apply()
                stopService(Intent(this, PhoneStepsService::class.java)); store.resetPhoneCounter(); render(); diagnoseSteps(date)
            }.setNegativeButton("关闭", null).show()
    }
    private fun enablePhoneSteps() {
        stepsJob?.cancel()
        if (!prefs.getBoolean("phone_steps_enabled", false)) store.resetPhoneCounter()
        prefs.edit().putString("steps_backend", "phone_sensor").putBoolean("phone_steps_enabled", true).remove("phone_steps_error").apply()
        startPhoneStepsService(); render()
        Toast.makeText(this, "本机计步已请求开启，走几步后点刷新查看", Toast.LENGTH_LONG).show()
    }
    private fun startPhoneStepsService() {
        try { startForegroundService(Intent(this, PhoneStepsService::class.java)) }
        catch (e: Exception) {
            prefs.edit().putBoolean("phone_steps_enabled", false).putString("phone_steps_error", "本机计步启动失败（${e.javaClass.simpleName}），请检查权限及后台设置。").apply()
        }
    }
    private fun stepsFailure(error: Exception): String = when (error) {
        is SecurityException -> "步数读取被拒绝，请检查轻衡的读取权限；较早历史还可能超出授权日期范围。已有缓存保留。"
        is android.os.RemoteException -> "健康连接服务通信失败，请更新组件或稍后刷新。已有缓存保留。"
        else -> "步数读取失败（${error.javaClass.simpleName}），请稍后重试或检查连接诊断。已有缓存保留。"
    }
    private fun xiaomiStepsHelp() {
        AlertDialog.Builder(this).setTitle("小米运动健康 → 轻衡")
            .setMessage("轻衡通过健康连接读取数据，不会自动取得其他应用的私有步数。\n\n1. 先打开小米运动健康，让手机或手环完成当天同步。\n2. 在“我的/个人资料 → 第三方数据”中查找“健康连接 / Health Connect”，连接并允许写入步数。小米部分版本还要求开启云同步，请按小米端提示设置。\n3. 在健康连接中允许轻衡读取步数，并查看“数据和访问权限 → 活动 → 步数”是否已有小米记录。\n4. 回到轻衡，点“连接诊断”，可选择实际写入步数的小米数据源，再刷新。\n\n不同地区和版本的入口可能不同。如果小米端没有健康连接入口，或系统没有健康连接，仅给轻衡授权仍不能读取小米私有数据。目前可手动补录；小米官方直连接口需要应用注册、服务开通和签名配置，当前版本未接入。")
            .setPositiveButton("打开小米运动健康") { _, _ ->
                val launch = listOf("com.xiaomi.wearable", "com.mi.health").firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
                if (launch != null) try { startActivity(launch) } catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "请手动打开小米运动健康", Toast.LENGTH_LONG).show() }
                else Toast.makeText(this, "未找到可打开的入口，请手动打开小米运动健康", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("健康连接设置") { _, _ ->
                try { startActivity(stepsClient.settingsIntent()) } catch (_: Exception) { Toast.makeText(this, "系统健康连接不可用，请检查系统组件或应用商店", Toast.LENGTH_LONG).show() }
            }.setNegativeButton("关闭", null).show()
    }
    private fun diagnoseSteps(targetDate: LocalDate) {
        if (targetDate > LocalDate.now()) { Toast.makeText(this, "请选择今天或历史日期", Toast.LENGTH_SHORT).show(); return }
        val target = targetDate.toString()
        if (!try { stepsClient.available() } catch (_: Exception) { false }) {
            AlertDialog.Builder(this).setTitle("健康连接不可用")
                .setMessage("此设备的健康连接组件不可用或需更新。小米运动健康内有步数不代表健康连接已有数据。系统：Android ${android.os.Build.VERSION.RELEASE}（API ${android.os.Build.VERSION.SDK_INT}）。")
                .setPositiveButton("查看连接指南") { _, _ -> xiaomiStepsHelp() }.setNeutralButton("检查组件") { _, _ -> syncSteps(targetDate, request = true) }.setNegativeButton("关闭", null).show()
            return
        }
        stepsJob?.cancel()
        stepsJob = stepsScope.launch {
            stepsMessages[target] = "正在检查读取权限、所选日期步数与最近7日写入来源…"; render()
            try {
                if (!withContext(Dispatchers.IO) { stepsClient.permitted() }) {
                    stepsMessages[target] = "健康连接可用，但轻衡未获得步数读取权限。"; render()
                    AlertDialog.Builder(this@MainActivity).setTitle("尚未授权轻衡读取步数").setMessage("健康连接组件正常。请允许轻衡读取步数；还需要在小米端开启共享并允许小米写入步数。")
                        .setPositiveButton("授权读取") { _, _ -> syncSteps(targetDate, request = true) }.setNeutralButton("小米连接指南") { _, _ -> xiaomiStepsHelp() }.setNegativeButton("关闭", null).show()
                    return@launch
                }
                val selected = stepsOrigin()
                val reading = withContext(Dispatchers.IO) { stepsClient.read(targetDate, selected) }
                var scanError: Exception? = null
                val scan = try { withContext(Dispatchers.IO) { stepsClient.sources(targetDate) } }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { scanError = e; StepsSources(emptySet(), false) }
                ensureActive()
                val packages = (scan.packages + reading.origins).sorted()
                val info = buildString {
                    append("系统：Android ${android.os.Build.VERSION.RELEASE}\n健康连接：可用\n轻衡读取权限：已允许\n日期：$target\n读取方式：${selected?.let { appLabel(it) } ?: "系统去重汇总"}\n结果：${reading.availableCount?.let { "$it 步" } ?: "没有可用数据"}\n\n")
                    append(if (packages.isEmpty()) "最近7日未发现可读取的步数来源。请先在小米端开启共享并完成同步。" else "最近7日可读取的来源：\n" + packages.joinToString("\n") { "${appLabel(it)} ($it)" })
                    if (!scan.complete) append("\n来源扫描未完成${scanError?.let { "（${it.javaClass.simpleName}）" } ?: "（读取上限）"}，列表可能不全。")
                    if (selected != null && selected !in packages) append("\n当前所选来源近期未发现记录，可切回系统汇总。")
                    append("\n\n来源存在不代表当天有记录。可选择单一来源，避免系统优先级导致读到其他应用数据；不会与系统总数相加。诊断本身不会改写步数。")
                }
                stepsMessages[target] = "诊断完成：权限已允许，${reading.availableCount?.let { "读到 $it 步" } ?: "没有可用数据"}，发现${packages.size}个近期来源。"; render()
                AlertDialog.Builder(this@MainActivity).setTitle("步数连接诊断").setMessage(info)
                    .setPositiveButton("选择数据源") { _, _ -> chooseStepsSource(targetDate, packages) }
                    .setNeutralButton("小米连接指南") { _, _ -> xiaomiStepsHelp() }.setNegativeButton("关闭", null).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { stepsMessages[target] = stepsFailure(e); render() }
        }
    }
    private fun chooseStepsSource(targetDate: LocalDate, packages: List<String>) {
        val options = listOf<String?>(null) + packages + listOfNotNull(stepsOrigin()).filter { it !in packages }
        val labels = options.map { it?.let { pkg -> "${appLabel(pkg)} ($pkg)" } ?: "系统去重汇总（默认）" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("选择后读取所选日期")
            .setSingleChoiceItems(labels, options.indexOf(stepsOrigin()).coerceAtLeast(0)) { dialog, index ->
                stepsJob?.cancel()
                prefs.edit().apply { options[index]?.let { putString("steps_origin", it) } ?: remove("steps_origin"); putString("steps_backend", "health_connect"); putBoolean("phone_steps_enabled", false); remove("steps_backfill_date") }.apply()
                stopService(Intent(this, PhoneStepsService::class.java)); store.resetPhoneCounter()
                dialog.dismiss(); syncSteps(targetDate, request = true)
            }.setNegativeButton("取消", null).show()
    }
    private fun walkingSettings() {
        val c = card(); c.addView(text("步数与步行估算", 19f, bold = true))
        c.addView(button(if (prefs.getBoolean("phone_steps_enabled", false)) "本机计步状态 / 停止" else "启用手机本机计步（无需健康连接）") { phoneStepsSetup() })
        c.addView(button("手机计步权限 / 后台设置") {
            try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "请在系统设置中打开轻衡应用信息", Toast.LENGTH_LONG).show() }
        })
        c.addView(button("连接诊断 / 选择步数来源") { diagnoseSteps(LocalDate.now()) })
        c.addView(button("小米运动健康连接指南") { xiaomiStepsHelp() })
        c.addView(Switch(this).apply {
            text = "健康连接模式下打开应用更新步数"; minHeight = dp(48); isChecked = prefs.getBoolean("health_steps_sync", false); isEnabled = !phoneStepsSelected()
            setOnCheckedChangeListener { _, value ->
                prefs.edit().putBoolean("health_steps_sync", value).apply()
                if (value) syncSteps(LocalDate.now(), request = true) else { stepsJob?.cancel(); render() }
            }
        })
        c.addView(text("读取当天汇总，每天还会尝试补齐最近7日可用数据；手动步数优先保留。应用不在后台读取，计步数据源需先共享到健康连接。", 12f, muted))
        val met = input("步行强度MET", walkingConfig().met.toString(), true)
        val cadence = input("估算步频（步/分钟）", walkingConfig().cadence.toString(), true)
        c.addView(text("步行强度（MET）", 12f, muted)); c.addView(met)
        val presets = row(); listOf("慢走2.8" to 2.8, "中速3.8" to 3.8, "快走4.8" to 4.8).forEach { (label, value) -> presets.addView(button(label) { met.setText(value.toString()) }, LinearLayout.LayoutParams(0, dp(48), 1f)) }; c.addView(presets)
        c.addView(text("估算步频（步/分钟，默认100，可自行校准）", 12f, muted)); c.addView(cadence)
        c.addView(button("保存步行参数", true) {
            val m = Energy.number(met.text.toString(), 20.0); val pace = Energy.number(cadence.text.toString(), 200.0)
            when {
                m == null || m < 1 -> met.error = "请输入1–20 MET"
                pace == null || pace < 40 -> cadence.error = "请输入40–200步/分钟"
                else -> { prefs.edit().putFloat("walking_met", m.toFloat()).putFloat("walking_cadence", pace.toFloat()).apply(); Toast.makeText(this, "已保存，历史步行估算也会更新", Toast.LENGTH_SHORT).show(); render() }
            }
        })
        c.addView(text("步行分钟≈有效步数÷步频；额外kcal≈(MET−1)×体重kg×分钟÷60。来源：2024成人体力活动汇编，默认参数并非实测。修改参数会更新历史估算。", 11f, muted))
        c.addView(button("管理自定义运动种类与强度") { ExerciseUi(this, store, date.toString()) { render() }.manage() })
        c.addView(button("健康连接 / 数据源与权限") {
            try { startActivity(stepsClient.settingsIntent()) } catch (_: Exception) { Toast.makeText(this, "健康连接不可用，可在系统设置或应用商店中检查", Toast.LENGTH_LONG).show() }
        })
        c.addView(button("健康数据使用说明") { startActivity(Intent(this, HealthPrivacyActivity::class.java)) })
    }
    private fun reminderSettings() {
        val c = card(); c.addView(text("三餐记餐提醒", 19f, bold = true))
        c.addView(Switch(this).apply {
            text = "每天提醒记录食物"; minHeight = dp(48); isChecked = MealReminders.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, value -> MealReminders.setEnabled(this@MainActivity, value); if (value) askNotificationPermission(); render() }
        })
        Meal.regular.forEach { meal ->
            val r = row(); r.addView(text(meal.label, 15f), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(button(MealReminders.timeText(this, meal)) {
                val t = MealReminders.minutes(this, meal)
                TimePickerDialog(this, { _, hour, minute -> MealReminders.setTime(this, meal, hour * 60 + minute); render() }, t / 60, t % 60, true).show()
            }); c.addView(r); space(c, 6)
        }
        c.addView(text("按手机本地时间提醒；记餐或标记没吃后停止该餐提醒。通知支持推迟30分钟，零食不提醒。", 12f, muted))
        space(c, 8)
        c.addView(text(if (MealReminders.notificationsAllowed(this)) "通知已允许" else "通知未允许或提醒渠道被关闭，请开启通知。", 13f, green))
        c.addView(button("通知权限与声音设置") {
            try {
                startActivity(if (MealReminders.notificationsAllowed(this)) MealReminders.notificationSettings(this)
                    else Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
            } catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "请在系统设置中开启本应用通知", Toast.LENGTH_LONG).show() }
        })
        space(c, 10)
        c.addView(text(if (MealReminders.exactAllowed(this)) "定时权限已就绪" else "当前使用系统非精确定时，可能延后；允许“闹钟和提醒”可提高准时性。", 12f, muted))
        if (android.os.Build.VERSION.SDK_INT >= 31 && !MealReminders.exactAllowed(this)) c.addView(button("允许准时提醒") {
            try { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName"))) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "此设备不支持打开定时权限页面", Toast.LENGTH_SHORT).show() }
        })
        c.addView(text("重启后自动恢复；强行停止应用或手机后台限制可能影响提醒，重新打开应用可恢复排程。", 11f, muted))
    }
    private fun nutritionSettings() {
        val c = card(); c.addView(text("每日营养目标", 19f, bold = true)); space(c, 8)
        c.addView(text("留空使用当前模式的默认规则：自动模式按维持体重热量分配三大营养素；手动模式蛋白质按0.83g/kg，其余只统计。自定义项优先，修改会更新所有日期对比。", 13f, muted))
        val fields = listOf("protein_goal" to "蛋白质目标（g/天）", "carbs_goal" to "碳水目标（g/天）", "fat_goal" to "脂肪目标（g/天）").map { (key, label) ->
            c.addView(text(label, 12f, muted)); val field = input("留空使用默认规则", goal(key)?.toString() ?: "", true); c.addView(field); key to field
        }
        space(c, 8); c.addView(button("保存营养目标", true) {
            var valid = true
            val values = fields.map { (key, field) ->
                val raw = field.text.toString().trim(); val n = Energy.number(raw, 5000.0)
                if (raw.isNotEmpty() && n == null) { field.error = "请输入大于 0、至多 5000 的目标，或留空"; valid = false }
                key to n
            }
            if (valid) {
                val editor = prefs.edit(); values.forEach { (key, n) -> if (n == null) editor.remove(key) else editor.putFloat(key, n.toFloat()) }; editor.apply()
                Toast.makeText(this, "营养目标已保存", Toast.LENGTH_SHORT).show(); render()
            }
        })
        space(c, 12)
        c.addView(text("蛋白质来源：EFSA 成人群体参考摄入量（2012）。此值是一般健康成人的参考，不是个体处方；孕哺期、未成年人、训练或疾病相关需求请使用适合自己的专业目标。", 11f, muted))
        c.addView(text("查看 EFSA 参考依据 ↗", 13f, green).apply { setPadding(0, dp(12), 0, dp(12)); setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.efsa.europa.eu/en/press/news/120209"))) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this@MainActivity, "设备上没有可用浏览器", Toast.LENGTH_SHORT).show() }
        } })
    }
    private fun input(hint: String, initial: String = "", numeric: Boolean = false) = EditText(this).apply {
        this.hint = hint; setText(initial); textSize = 16f; setTextColor(ink); setSingleLine(true)
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setPadding(dp(8), dp(12), dp(8), dp(12)); minHeight = dp(52)
    }
    private fun dialogBody() = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
    private fun quickRecord() {
        val target = date.toString()
        FoodLibraryUi(this, store) { selected ->
            val n = selected.values
            entryDialog("food", draft = Entry(date = target, kind = "food", name = selected.food.name, kcal = n.kcal,
                protein = n.protein, carbs = n.carbs, fat = n.fat, sodium = n.sodium, details = selected.details))
        }.show()
    }
    private fun entryDialog(kind: String, existing: Entry? = null, selectedMeal: Meal? = null, draft: Entry? = null) {
        if (kind == "exercise") { ExerciseUi(this, store, date.toString()) { render() }.entry(existing); return }
        val initial = existing ?: draft
        val targetDate = initial?.date ?: date.toString()
        val c = dialogBody(); c.addView(text("$targetDate · ${if (kind == "food") "饮食摄入" else "额外运动"}", 13f, muted))
        val mealPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Meal.entries.map { it.label })
            setSelection((selectedMeal ?: initial?.let { Meal.from(it.meal) } ?: Meal.UNSPECIFIED).ordinal)
            minimumHeight = dp(48)
        }
        if (kind == "food") { c.addView(text("餐次（零食不提醒）", 12f, muted)); c.addView(mealPicker) }
        val name = input(if (kind == "food") "食物 / 餐次，例如午餐" else "运动名称，例如慢跑", initial?.name ?: "")
        val kcal = input("${if (kind == "food") "摄入" else "额外消耗"}热量（kcal）", initial?.let { it.kcal.toString() } ?: "", true)
        val mins = input("运动时长（整数分钟）", existing?.minutes?.toString() ?: "", true)
        val protein = input("蛋白质（g，可留空）", initial?.protein?.toString() ?: "", true)
        val carbs = input("碳水化合物（g，可留空）", initial?.carbs?.toString() ?: "", true)
        val fat = input("脂肪（g，可留空）", initial?.fat?.toString() ?: "", true)
        val sodium = input("钠（mg，可留空）", initial?.sodium?.toString() ?: "", true)
        var foodDetails = initial?.details ?: ""
        val detailsView = text(foodDetails, 11f, muted)
        if (kind == "food") {
            c.addView(button("从快捷食物 / 原材料配方填入") {
                FoodLibraryUi(this, store) { selected ->
                    val n = selected.values; name.setText(selected.food.name); kcal.setText(n.kcal.toString())
                    protein.setText(n.protein.toString()); carbs.setText(n.carbs.toString()); fat.setText(n.fat.toString()); sodium.setText(n.sodium?.toString() ?: "")
                    foodDetails = selected.details; detailsView.text = foodDetails
                }.show()
            })
        }
        c.addView(name); c.addView(kcal); if (kind == "exercise") { c.addView(mins); c.addView(text("填写该次运动的总消耗，不是每分钟消耗。", 12f, muted)) }
        if (kind == "food") {
            c.addView(text("填写本次实际食用份量的营养总克数，不是每 100 g 含量。留空表示未知，填 0 表示没有摄入该项。", 12f, muted))
            listOf("蛋白质（g）" to protein, "碳水化合物（g）" to carbs, "脂肪（g）" to fat).forEach { (label, field) -> c.addView(text(label, 12f, muted)); c.addView(field) }
            c.addView(text("钠（mg）", 12f, muted)); c.addView(sodium)
            c.addView(text("以下为快捷输入的参考份量与来源；如手动修改营养，以最终保存数值为准。", 11f, muted)); c.addView(detailsView)
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "添加记录" else "编辑记录").setView(ScrollView(this).apply { addView(c) }).setNegativeButton("取消", null).setPositiveButton("保存", null)
        if (existing != null) dialog.setNeutralButton("删除") { _, _ -> confirmDelete { store.delete(existing); render() } }
        val d = dialog.create()
        if (kind == "food" && existing == null) {
            space(c, 12); c.addView(button("这一餐没吃") {
                val chosen = Meal.entries[mealPicker.selectedItemPosition]
                if (chosen.remindable) skipMeal(targetDate, chosen) { d.dismiss() }
                else Toast.makeText(this, "仅早餐、午餐和晚餐可标记没吃", Toast.LENGTH_SHORT).show()
            })
        }
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = if (kind == "food") FoodMath.nonNegative(kcal.text.toString(), 50000.0) else Energy.number(kcal.text.toString(), 50000.0)
            val m = if (kind == "exercise") mins.text.toString().trim().toIntOrNull() else 0
            val na = FoodMath.nonNegative(sodium.text.toString(), 1000000.0)
            val nutrients = if (kind == "food") listOf(protein, carbs, fat) else emptyList()
            val invalidNutrient = nutrients.firstOrNull { it.text.toString().isNotBlank() && Nutrition.grams(it.text.toString()) == null }
            when {
                name.text.toString().trim().isEmpty() || name.text.length > 80 -> name.error = "请输入 1–80 字的名称"
                n == null -> kcal.error = if (kind == "food") "请输入0–50000 kcal的热量" else "请输入大于0、至多50000的热量"
                m == null || m !in 1..1440 && kind == "exercise" -> mins.error = "请输入 1–1440 的整数分钟"
                invalidNutrient != null -> invalidNutrient.error = "请输入 0–5000 g，或留空"
                kind == "food" && sodium.text.isNotBlank() && na == null -> sodium.error = "请输入0–1000000 mg，或留空"
                else -> { store.save(Entry(existing?.id ?: 0L, targetDate, kind, name.text.toString().trim(), n, m,
                    if (kind == "food") Nutrition.grams(protein.text.toString()) else null,
                    if (kind == "food") Nutrition.grams(carbs.text.toString()) else null,
                    if (kind == "food") Nutrition.grams(fat.text.toString()) else null,
                    if (kind == "food") Meal.entries[mealPicker.selectedItemPosition].key else "unspecified",
                    if (kind == "food") na else null, if (kind == "food") foodDetails else ""))
                    if (kind == "food") MealReminders.resolve(this, targetDate, Meal.entries[mealPicker.selectedItemPosition])
                    d.dismiss(); render() }
            }
        } }; d.show()
    }
    private fun weightDialog(existing: Weight? = null) {
        val target = existing?.date ?: date.toString()
        val c = dialogBody(); c.addView(text(target, 14f, muted)); val field = input("体重（kg）", existing?.kg?.toString() ?: "", true); c.addView(field)
        c.addView(text("每天保留一条体重记录，再次保存会更新。", 12f, muted))
        val builder = AlertDialog.Builder(this).setTitle("记录体重").setView(c).setNegativeButton("取消", null).setPositiveButton("保存", null)
        if (existing != null) builder.setNeutralButton("删除") { _, _ -> confirmDelete { store.deleteWeight(target); render() } }
        val d = builder.create(); d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = Energy.number(field.text.toString(), 1000.0)
            if (n == null) field.error = "请输入大于 0、至多 1000 的体重"
            else { store.saveWeight(Weight(target, n)); d.dismiss(); render() }
        } }; d.show()
    }
    private fun confirmDelete(action: () -> Unit) { AlertDialog.Builder(this).setTitle("删除这条记录？").setMessage("删除后无法撤销。").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> action() }.show() }
    @Deprecated("Legacy activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 12 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val csv = StringBuilder("\uFEFF日期,类型,名称,热量(kcal),时长(分钟),体重(kg),蛋白质(g),碳水(g),脂肪(g),餐次,钠(mg),份量与配方说明,运动kcal每分钟,运动消耗类型,运动重叠步数,当日步数,步数来源,步数更新时间,步行计入\r\n")
            fun csvRow(vararg cells: Any?) { csv.append((cells.toList() + List((19 - cells.size).coerceAtLeast(0)) { null }).joinToString(",") { Energy.csv(it?.toString() ?: "") }).append("\r\n") }
            baseline?.let { csvRow("", "消耗基准", "日常消耗", it, "", "", "", "", "") }
            csvRow("", "计算模式", if (automatic) "自动：TDEE减摄入，不叠加运动" else "手动：基准加额外运动减摄入")
            csvRow("", "步数读取方式", if (phoneStepsSelected()) "手机本机计步" else "健康连接：${stepsOrigin() ?: "系统去重汇总"}")
            store.phoneStepDays().forEach { day -> csvRow(day.date, "本机传感器独立记录（非小米历史）", "仅采集存档，非新增运动，不与每日步数重复相加", "", "", "", "", "", "", "", "", "从启用后记录，可能少计", "", "", "", day.steps, day.source, java.time.Instant.ofEpochMilli(day.updatedAt)) }
            store.profiles().forEach { p ->
                val e = estimate(p.date)
                csvRow(p.date, "个人资料", "Mifflin–St Jeor维持体重参考", e?.expenditure, "", ExerciseMath.weightAt(store.weights(), p.date), e?.protein, e?.carbs, e?.fat, "", "",
                    "年龄=${p.age};性别=${p.sex.name};身高cm=${p.heightCm};PAL=${p.activity.pal};REE=${e?.resting};BMI=${e?.bmi};年龄按录入日周年近似增长")
            }
            csvRow("", "营养规则", if (automatic) "维持体重TDEE：蛋白质20%、碳水50%、脂肪30%，4/4/9kcal每克；自定义项优先" else "健康成人蛋白质：所选日期或之前最近体重kg × 0.83g/天；自定义项优先")
            listOf("protein_goal", "carbs_goal", "fat_goal").forEachIndexed { i, key -> goal(key)?.let {
                csvRow("", "自定义目标", listOf("蛋白质", "碳水", "脂肪")[i], "", "", "", if (i == 0) it else "", if (i == 1) it else "", if (i == 2) it else "")
            } }
            val allEntries = store.entries()
            allEntries.forEach { e -> csvRow(e.date, if (e.kind == "food") "饮食" else "运动", e.name, e.kcal, e.minutes, "", e.protein, e.carbs, e.fat, if (e.kind == "food") Meal.from(e.meal).label else "", e.sodium, e.details, e.exerciseRate, if (e.kind == "exercise") e.exerciseRateKind else "", if (e.kind == "exercise") e.overlapSteps else "") }
            store.stepDays().forEach { day ->
                val walk = walkingEstimate(day.date, allEntries.filter { it.date == day.date }); val config = walkingConfig()
                csvRow(day.date, "每日步数步行估算", "步行净消耗", walk.kcal, walk.minutes, ExerciseMath.weightAt(store.weights(), day.date), "", "", "", "", "",
                    "${walk.message}；MET=${config.met}；步频=${config.cadence}；扣除重叠${walk.excluded}步", "", "", "", day.steps, day.source, java.time.Instant.ofEpochMilli(day.updatedAt), day.included)
            }
            store.exerciseTemplates().forEach { t -> csvRow("", "快捷运动", t.name, "", "", "", "", "", "", "", "", "单位时间参考值，不是一次运动记录", t.kcalPerMinute, t.kind.name) }
            store.quickFoods().forEach { f -> csvRow("", "快捷食物参考份量", f.name, f.values.kcal, "", "", f.values.protein, f.values.carbs, f.values.fat, "", f.values.sodium, FoodJson.encode(f)) }
            store.skippedMeals().forEach { csvRow(it.date, "这一餐没吃", "", "", "", "", "", "", "", Meal.from(it.meal).label) }
            store.weights().forEach { w -> csvRow(w.date, "体重", "", "", "", w.kg, "", "", "") }
            val stream = contentResolver.openOutputStream(uri) ?: error("无法打开文件")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(csv.toString()) }
            Toast.makeText(this, "全部记录已导出", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, "导出失败，请选择可写入的位置重试", Toast.LENGTH_LONG).show() }
    }
    override fun onDestroy() { stepsScope.cancel(); appUpdates.close(); store.close(); super.onDestroy() }
}
