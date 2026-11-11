package cn.qingheng.app

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w390dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignFlowTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val screenshots = File("build/design-preview").apply { mkdirs() }
    @Before fun records() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().putBoolean("notification_asked", true)
            .putString("last_open_date", java.time.LocalDate.now().toString())
            .putFloat("baseline", 2000f).putFloat("protein_goal", 100f).putFloat("fat_goal", 67f).putFloat("carbs_goal", 250f).commit()
        context.getSharedPreferences("meal_reminders", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        Store(context).use { store ->
            store.save(Entry(date = "2026-11-06", kind = "food", name = "早餐", kcal = 420.0, protein = 15.0, fat = 10.0, carbs = 60.0, meal = Meal.BREAKFAST.key))
            store.save(Entry(date = "2026-11-06", kind = "food", name = "番茄炒蛋等", kcal = 860.0, protein = 33.0, fat = 30.0, carbs = 122.0, meal = Meal.LUNCH.key))
            listOf("2026-10-08" to 65.8, "2026-10-13" to 65.4, "2026-10-18" to 65.5, "2026-10-23" to 65.0,
                "2026-10-27" to 65.2, "2026-11-01" to 64.9, "2026-11-06" to 64.8).forEach { store.saveWeight(Weight(it.first, it.second)) }
        }
    }
    private fun activity(tab: Int) = Robolectric.buildActivity(MainActivity::class.java).create(Bundle().apply { putString("date", "2026-11-06"); putInt("tab", tab) }).start().resume().visible()
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun text(root: View, value: String) = descendants(root).filterIsInstance<TextView>().first { it.text.toString() == value }
    private fun click(view: View) { var target: View = view; while (!target.isClickable) target = target.parent as View; assertTrue(target.performClick()) }
    private fun latestSheet() = ShadowDialog.getLatestDialog() as DesignSheet
    private fun root(sheet: Dialog) = sheet.window!!.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    private fun root(main: MainActivity) = main.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 390, 800)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun swipe(view: View, x: Float, y: Float, endX: Float, endY: Float) {
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, step: Int) {
            MotionEvent.obtain(start, start + step * 20L, action, x + (endX - x) * step / 10, y + (endY - y) * step / 10, 0).let { event ->
                view.dispatchTouchEvent(event); event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, 0)
        (1..9).forEach { send(MotionEvent.ACTION_MOVE, it) }
        send(MotionEvent.ACTION_UP, 10)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun meal(sheet: DesignSheet, selected: Meal? = null): Spinner {
        val picker = descendants(sheet.content).filterIsInstance<Spinner>().single { it.contentDescription == "选择餐次" }
        if (selected != null) { picker.setSelection(selected.ordinal); layout(root(sheet)) }
        return picker
    }
    private fun snapshot(root: View, name: String) {
        root.setPadding(root.paddingLeft, 36, root.paddingRight, if (name.startsWith("food-category") || name == "food-entry") 24 else 0)
        root.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 390, 800)
        root.viewTreeObserver.dispatchOnPreDraw()
        val bitmap = Bitmap.createBitmap(390, 800, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        root.draw(canvas)
        // System chrome is a fixture; the application's status bar remains owned by Android.
        val status = FrameLayout(context)
        status.addView(DesignUi.text(context, "12:30", 13f, bold = true), FrameLayout.LayoutParams(60, 18).apply { leftMargin = 22; topMargin = 8 })
        status.addView(DesignUi.svg(context, "system_status.svg"), FrameLayout.LayoutParams(46, 16).apply { leftMargin = 320; topMargin = 9 })
        status.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(36, View.MeasureSpec.EXACTLY)); status.layout(0, 0, 390, 36); status.draw(canvas)
        File(screenshots, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
        descendants(root).filterIsInstance<ImageView>().forEach { image ->
            val asset = image.getTag(R.id.design_asset_name) as? String ?: return@forEach
            val size = when { asset.startsWith("nav_") -> 24 to 24; asset == "brand_leaf.svg" -> 20 to 20; asset == "rice.svg" -> 105 to 78; asset == "intake_reference.svg" -> 90 to 90; else -> return@forEach }
            assertNotNull(asset, image.drawable)
            assertEquals(asset, size.first, image.drawable.intrinsicWidth); assertEquals(asset, size.second, image.drawable.intrinsicHeight)
            if (image.isShown && asset != "rice.svg") { assertEquals(asset, size.first, image.width); assertEquals(asset, size.second, image.height) }
        }
    }
    @Test fun fourPrimaryScreensRenderActualRecordsAndKeepNavigation() {
        listOf("today", "diary", "trends", "settings").forEachIndexed { tab, name ->
            val controller = activity(tab); val main = controller.get()
            val root = main.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            snapshot(root, name)
            assertTrue(descendants(root).any { it.contentDescription?.toString() == listOf("今日", "记录", "趋势", "设置")[tab] && it.isSelected })
            if (tab == 0) { assertNotNull(text(root, "720")); assertNotNull(text(root, "48 g")); assertNotNull(text(root, "182 g")) }
            controller.pause().stop().destroy()
        }
    }
    @Test fun pickingRiceAndSubmitting150gDoesNotDuplicateOrChangeReferenceNutrition() {
        val controller = activity(0); val main = controller.get()
        val root = main.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        click(text(root, "＋  记录午餐"))
        var sheet = ShadowDialog.getLatestDialog() as DesignSheet
        snapshot(sheet.window!!.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0), "food-categories")
        click(text(sheet.content, "主食"))
        sheet = ShadowDialog.getLatestDialog() as DesignSheet
        snapshot(sheet.window!!.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0), "food-category")
        sheet.saveButton!!.performClick()
        sheet = ShadowDialog.getLatestDialog() as DesignSheet
        snapshot(sheet.window!!.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0), "food-entry")
        val actual = descendants(sheet.content).filterIsInstance<EditText>().single { it.contentDescription?.toString() == "实际食用克数，可输入计算式，留空按100克" }
        assertEquals(EditorInfo.IME_ACTION_DONE, actual.imeOptions and EditorInfo.IME_MASK_ACTION)
        actual.setText("300/2"); actual.onEditorAction(EditorInfo.IME_ACTION_DONE)
        assertFalse(sheet.isShowing)
        Store(context).use { store ->
            val entries = store.entries("2026-11-06"); assertEquals(3, entries.size)
            val rice = entries.first { it.name == FoodCatalog.rice.name }
            assertEquals(FoodCatalog.rice.values.kcal * 1.5, rice.kcal, 1e-9)
            assertEquals(150.0, PortionJson.decode(rice.portion)!!.actual, 0.0)
            assertEquals(100.0, PortionJson.decode(rice.portion)!!.basis, 0.0)
        }
        controller.pause().stop().destroy()
    }
    @Test fun manualFoodKeepsOptionalGramsUnknownMacrosAndSignedAmounts() {
        val controller = activity(0); val main = controller.get()
        listOf("" to 200.0, "-25" to -50.0, "0" to 0.0).forEachIndexed { index, (grams, expected) ->
            val root = main.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            click(text(root, "＋  记录午餐"))
            click(text(latestSheet().content, "＋ 手动输入食物"))
            val sheet = ShadowDialog.getLatestDialog() as DesignSheet
            val fields = descendants(sheet.content).filterIsInstance<EditText>()
            fields.single { it.hint?.toString() == "食物 / 菜品名称" }.setText("手动食物$index")
            fields.single { it.hint?.toString() == "参考热量（kcal）" }.setText("200")
            val actual = fields.single { it.contentDescription?.toString() == "实际食用克数，可输入计算式，留空按100克" }
            actual.setText(grams); actual.onEditorAction(EditorInfo.IME_ACTION_DONE)
            assertFalse(sheet.isShowing)
            Store(context).use { store ->
                val entry = store.entries("2026-11-06").first { it.name == "手动食物$index" }
                assertEquals(expected, entry.kcal, 0.0); assertNull(entry.protein); assertNull(entry.fat); assertNull(entry.carbs)
            }
        }
        controller.pause().stop().destroy()
    }
    @Test fun categoriesHideEmptyDefaultsAndVerticalDragsOverTilesScrollTheWholePage() {
        Store(context).use { store ->
            (1..10).forEach { store.saveQuickFood(FoodCatalog.rice.copy(id = 0, name = "自定义$it", category = "分类$it")) }
            val deleted = store.saveQuickFood(FoodCatalog.rice.copy(id = 0, category = "已清空分类"))
            store.deleteQuickFood(deleted)
        }
        val controller = activity(0)
        click(text(root(controller.get()), "＋  记录午餐"))
        val sheet = latestSheet(); val page = root(sheet); layout(page)
        val grid = descendants(page).filterIsInstance<FoodTileGrid>().single()
        val scroll = descendants(page).filterIsInstance<ScrollView>().single()
        assertTrue(descendants(page).none { it is GridView })
        listOf("零食", FoodCategories.LEGACY, "已清空分类").forEach { empty ->
            assertTrue(descendants(grid).filterIsInstance<TextView>().none { it.text.toString() == empty })
        }
        assertTrue(scroll.canScrollVertically(1))
        val first = android.graphics.Rect(); assertTrue(grid.getChildAt(0).getGlobalVisibleRect(first))
        swipe(page, first.centerX().toFloat(), (first.bottom - 12).toFloat(), first.centerX().toFloat(), (first.bottom - 220).toFloat())
        assertTrue("Dragging a tile must scroll its page", scroll.scrollY > 0)
        scroll.isSmoothScrollingEnabled = false; scroll.fullScroll(View.FOCUS_DOWN); layout(page)
        assertTrue(text(sheet.content, "管理已有菜品 · 分类 / 图片 / 编辑").getGlobalVisibleRect(android.graphics.Rect()))
        snapshot(page, "usability-categories-scrolled")
        sheet.dismiss(); controller.pause().stop().destroy()
    }
    @Test fun longFoodCategoryCanReachAndSelectTheLastTileAndSearchDoesNotKeepHiddenSelection() {
        Store(context).use { store -> (1..20).forEach { store.saveQuickFood(FoodCatalog.rice.copy(id = 0, name = "套餐$it", category = "我的套餐")) } }
        val controller = activity(0)
        click(text(root(controller.get()), "＋  记录午餐")); click(text(latestSheet().content, "我的套餐"))
        val sheet = latestSheet(); val page = root(sheet); layout(page)
        val grid = descendants(page).filterIsInstance<FoodTileGrid>().single()
        val scroll = descendants(page).filterIsInstance<ScrollView>().single()
        assertEquals(20, grid.childCount); assertTrue(scroll.canScrollVertically(1))
        val first = android.graphics.Rect(); assertTrue(grid.getChildAt(0).getGlobalVisibleRect(first))
        swipe(page, first.centerX().toFloat(), (first.bottom - 12).toFloat(), first.centerX().toFloat(), (first.bottom - 220).toFloat())
        assertTrue(scroll.scrollY > 0)
        scroll.isSmoothScrollingEnabled = false; scroll.fullScroll(View.FOCUS_DOWN); layout(page)
        assertTrue(grid.getChildAt(19).getGlobalVisibleRect(android.graphics.Rect()))
        grid.getChildAt(19).performClick(); assertEquals("选择套餐1", sheet.saveButton!!.text.toString())
        layout(page); assertTrue("Selection must not jump back to the search field", grid.getChildAt(19).getGlobalVisibleRect(android.graphics.Rect()))
        snapshot(page, "usability-foods-scrolled")
        val search = descendants(sheet.content).filterIsInstance<EditText>().single()
        search.setText("找不到的食物"); assertFalse(sheet.saveButton.isEnabled)
        search.setText("套餐19"); assertTrue(sheet.saveButton.isEnabled); assertEquals("选择套餐19", sheet.saveButton.text.toString())
        sheet.saveButton.performClick()
        assertEquals("套餐19", descendants(latestSheet().content).filterIsInstance<EditText>().first().text.toString())
        latestSheet().dismiss(); controller.pause().stop().destroy()
    }
    @Test fun mealCanChangeInCategoriesFoodsAndEntryAndSurvivesBackNavigation() {
        val controller = activity(0)
        click(text(root(controller.get()), "＋  记录午餐"))
        meal(latestSheet(), Meal.BREAKFAST); click(text(latestSheet().content, "主食"))
        assertEquals(Meal.BREAKFAST.ordinal, meal(latestSheet()).selectedItemPosition)
        meal(latestSheet(), Meal.DINNER); latestSheet().navigateBack!!.invoke()
        assertEquals(Meal.DINNER.ordinal, meal(latestSheet()).selectedItemPosition)
        click(text(latestSheet().content, "饮品")); assertEquals(Meal.DINNER.ordinal, meal(latestSheet()).selectedItemPosition)
        latestSheet().saveButton!!.performClick()
        val entry = latestSheet(); assertEquals(Meal.DINNER.ordinal, meal(entry).selectedItemPosition)
        meal(entry, Meal.SNACK); assertEquals("保存到零食", entry.saveButton!!.text.toString()); assertNotNull(text(entry.content, "记录零食"))
        val actual = descendants(entry.content).filterIsInstance<EditText>().single { it.contentDescription?.toString()?.startsWith("实际食用克数") == true }
        actual.onEditorAction(EditorInfo.IME_ACTION_DONE)
        Store(context).use { store -> assertEquals(Meal.SNACK.key, store.entries("2026-11-06").single { it.name == FoodCatalog.milk.name }.meal) }
        controller.pause().stop().destroy()
    }
    @Test fun unweighedLegacyMealSavesWithoutGramsAndReopensAsPortionsWithoutLosingPrecision() {
        val food = QuickFood(name = "旧套餐", basisGrams = null, values = FoodValues(432.123456789, 20.123456789, 50.23456789, 15.3456789))
        Store(context).use { it.saveQuickFood(food) }
        val controller = activity(0); val main = controller.get()
        click(text(root(main), "＋  记录午餐")); click(text(latestSheet().content, FoodCategories.LEGACY)); latestSheet().saveButton!!.performClick()
        val sheet = latestSheet(); snapshot(root(sheet), "usability-unweighed-meal")
        val actual = descendants(sheet.content).filterIsInstance<EditText>().single { it.contentDescription?.toString()?.startsWith("实际食用份数") == true }
        assertEquals("1", actual.text.toString()); actual.setText(""); actual.onEditorAction(EditorInfo.IME_ACTION_DONE)
        assertFalse(sheet.isShowing)
        Store(context).use { store ->
            val entry = store.entries("2026-11-06").single { it.name == food.name }
            assertEquals(food.values.kcal, entry.kcal, 0.0); assertEquals(food.values.protein, entry.protein!!, 0.0)
            assertEquals(FoodUnit.PORTIONS, PortionJson.decode(entry.portion)!!.unit)
            assertNull(store.quickFoods().single().basisGrams)
        }
        click(text(root(main), Meal.LUNCH.label)); click(text(latestSheet().content, food.name))
        var editing = latestSheet()
        assertEquals("1", descendants(editing.content).filterIsInstance<EditText>().single { it.contentDescription?.toString()?.startsWith("实际食用份数") == true }.text.toString())
        editing.saveButton!!.performClick()
        Store(context).use { store -> assertEquals(food.values.kcal, store.entries("2026-11-06").single { it.name == food.name }.kcal, 0.0) }
        click(text(root(main), Meal.LUNCH.label)); click(text(latestSheet().content, food.name)); editing = latestSheet()
        val amount = descendants(editing.content).filterIsInstance<EditText>().single { it.contentDescription?.toString()?.startsWith("实际食用份数") == true }
        amount.setText("1/2"); amount.onEditorAction(EditorInfo.IME_ACTION_DONE)
        Store(context).use { store ->
            val entry = store.entries("2026-11-06").single { it.name == food.name }
            assertEquals(food.values.kcal / 2, entry.kcal, 0.0); assertEquals(0.5, PortionJson.decode(entry.portion)!!.actual, 0.0)
            assertEquals(3, store.entries("2026-11-06").size)
        }
        controller.pause().stop().destroy()
    }
    @Test fun unweighedMealCanOptionallyUseRealGramsButNeverInvents100g() {
        val controller = activity(0); val c = LinearLayout(controller.get()).apply { orientation = LinearLayout.VERTICAL }
        val food = FoodCatalog.tomatoEgg
        val fields = FoodNutritionInput(controller.get(), c, food.name, FoodInputDraft.fromFood(food), food)
        assertEquals(food.values.kcal, fields.portion()!!.values.kcal, 0.0); assertFalse(fields.basis.isShown)
        click(text(c, "改按克记录（需填写成品克重）")); assertNull(fields.portion())
        fields.basis.setText("500"); fields.actual.setText("250")
        assertEquals(food.values.kcal / 2, fields.portion()!!.values.kcal, 0.0); assertEquals(FoodUnit.GRAMS, fields.portion()!!.unit)
        click(text(c, "改按整份记录（无需重量）")); assertEquals(food.values.kcal, fields.portion()!!.values.kcal, 0.0)
        fields.actual.setText("-1/2"); assertEquals(-food.values.kcal / 2, fields.portion()!!.values.kcal, 0.0)
        fields.actual.setText("0"); assertEquals(0.0, fields.portion()!!.values.kcal, 0.0)
        controller.pause().stop().destroy()
    }
    @Test fun horizontalGesturesSwitchAllFourTabsWhileVerticalGesturesAndBoundariesStayPut() {
        val controller = activity(0); val main = controller.get()
        fun selected(label: String) { assertTrue(descendants(root(main)).any { it.contentDescription == label && it.isSelected }) }
        layout(root(main)); swipe(root(main), 70f, 320f, 330f, 320f); selected("今日")
        swipe(root(main), 330f, 320f, 70f, 320f); selected("记录")
        layout(root(main)); swipe(root(main), 200f, 550f, 202f, 270f); selected("记录")
        layout(root(main)); swipe(root(main), 330f, 320f, 70f, 320f); selected("趋势")
        layout(root(main)); swipe(root(main), 330f, 320f, 70f, 320f); selected("设置")
        layout(root(main)); swipe(root(main), 330f, 320f, 70f, 320f); selected("设置")
        listOf("趋势", "记录", "今日").forEach { label -> layout(root(main)); swipe(root(main), 70f, 320f, 330f, 320f); selected(label) }
        assertNotNull(text(root(main), "11 月 6 日，星期五"))
        assertTrue(descendants(root(main)).filterIsInstance<TextView>().none { it.text == "每日营养" || it.text == "手动输入食物" || it.text == "＋ 零食" })
        assertEquals(1, descendants(root(main)).filterIsInstance<TextView>().count { it.text == "营养摄入  ›" })
        controller.pause().stop().destroy()
    }
    private fun assertDate(main: MainActivity, date: java.time.LocalDate) {
        assertNotNull(text(root(main), date.format(java.time.format.DateTimeFormatter.ofPattern("M 月 d 日，EEEE", java.util.Locale.SIMPLIFIED_CHINESE))))
    }
    @Test fun headerArrowsChangeDisplayedRecordsAndSaveToTheSelectedDate() {
        val controller = activity(0); val main = controller.get()
        click(descendants(root(main)).single { it.contentDescription == "前一天" })
        assertDate(main, java.time.LocalDate.parse("2026-11-05")); assertNotNull(text(root(main), "2000"))
        click(text(root(main), "＋  记录午餐")); click(text(latestSheet().content, "主食")); latestSheet().saveButton!!.performClick()
        latestSheet().saveButton!!.performClick()
        Store(context).use { store ->
            assertEquals(1, store.entries("2026-11-05").size); assertEquals(2, store.entries("2026-11-06").size)
            assertEquals(FoodCatalog.rice.values.kcal, store.entries("2026-11-05").single().kcal, 1e-10)
        }
        assertDate(main, java.time.LocalDate.parse("2026-11-05"))
        click(descendants(root(main)).single { it.contentDescription == "后一天" })
        assertDate(main, java.time.LocalDate.parse("2026-11-06")); assertNotNull(text(root(main), "720"))
        controller.pause().stop().destroy()
    }
    @Test fun firstOpenOnANewDayResetsRestoredDateButSameDayRecreationKeepsTheChosenDate() {
        val today = java.time.LocalDate.now()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("last_open_date", today.minusDays(1).toString()).commit()
        val controller = activity(0)
        assertDate(controller.get(), today)
        assertEquals(today.toString(), context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("last_open_date", null))
        click(descendants(root(controller.get())).single { it.contentDescription == "前一天" })
        assertDate(controller.get(), today.minusDays(1))
        controller.recreate(); assertDate(controller.get(), today.minusDays(1))
        controller.pause().stop().destroy()
    }
    @Test fun returningFromBackgroundOnANewDayUpdatesOnceAndKeepsLaterHistoryBrowsing() {
        val today = java.time.LocalDate.now()
        val controller = activity(0)
        controller.pause()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("last_open_date", today.minusDays(1).toString()).commit()
        controller.resume(); assertDate(controller.get(), today)
        click(descendants(root(controller.get())).single { it.contentDescription == "前一天" })
        controller.pause().resume(); assertDate(controller.get(), today.minusDays(1))
        controller.pause().stop().destroy()
    }
    @Test fun historicalReminderTakesPriorityOverNewDayResetOnColdAndWarmLaunches() {
        val today = java.time.LocalDate.now(); val target = today.minusDays(1)
        fun reminder() = android.content.Intent(context, MainActivity::class.java).apply {
            action = MealReminders.OPEN; putExtra("date", target.toString()); putExtra("meal", Meal.DINNER.key)
        }
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString("last_open_date", target.toString()).commit()
        var controller = Robolectric.buildActivity(MainActivity::class.java, reminder()).create().start().resume().visible()
        assertNotNull(text(latestSheet().content, "$target / 饮食摄入")); latestSheet().dismiss()
        controller.pause().stop().destroy()
        controller = activity(0); controller.pause()
        prefs.edit().putString("last_open_date", target.toString()).commit()
        controller.newIntent(reminder()).resume()
        val sheet = latestSheet(); assertNotNull(text(sheet.content, "$target / 饮食摄入"))
        assertEquals(Meal.DINNER.ordinal, meal(sheet).selectedItemPosition)
        click(text(sheet.content, "从分类快捷食物 / 配方填入")); click(text(latestSheet().content, "主食")); latestSheet().saveButton!!.performClick()
        sheet.saveButton!!.performClick()
        Store(context).use { store ->
            val entry = store.entries(target.toString()).single { it.name == FoodCatalog.rice.name }
            assertEquals(Meal.DINNER.key, entry.meal); assertTrue(store.entries(today.toString()).isEmpty())
        }
        controller.pause().stop().destroy()
    }
    @Test fun numericImeKeepsExpressionsAndTheContextualSymbolsFollowTheFocusedField() {
        val controller = activity(0); val main = controller.get()
        click(text(root(main), "＋  记录午餐")); click(text(latestSheet().content, "＋ 手动输入食物"))
        val sheet = latestSheet(); layout(root(sheet))
        val fields = descendants(sheet.content).filterIsInstance<EditText>()
        val name = fields.single { it.hint.toString() == "食物 / 菜品名称" }
        assertEquals(android.text.InputType.TYPE_CLASS_TEXT, name.inputType and android.text.InputType.TYPE_MASK_CLASS)
        fields.filter { it.getTag(R.id.numeric_setting) != null }.forEach { field ->
            val info = EditorInfo(); assertNotNull(field.onCreateInputConnection(info))
            assertEquals(android.text.InputType.TYPE_CLASS_NUMBER, info.inputType and android.text.InputType.TYPE_MASK_CLASS)
            assertTrue(info.inputType and android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL != 0)
            assertTrue(info.inputType and android.text.InputType.TYPE_NUMBER_FLAG_SIGNED != 0)
        }
        name.setText("键盘测试")
        val energy = fields.single { it.hint.toString() == "参考热量（kcal）" }
        energy.setText("200"); NumberInput.finish(energy)
        click(text(sheet.content, "每 100 g 含 200 kcal · 编辑"))
        val actual = fields.single { it.contentDescription?.toString()?.startsWith("实际食用克数") == true }
        actual.requestFocus(); actual.setText("17"); actual.setSelection(2)
        assertEquals(1, descendants(sheet.content).count { it.contentDescription == "算式符号" })
        click(descendants(sheet.content).single { it.contentDescription == "插入 ÷" })
        actual.onCreateInputConnection(EditorInfo())!!.commitText("4.18", 1)
        assertEquals("17÷4.18", actual.text.toString()); assertNull(actual.error)
        snapshot(root(sheet), "numeric-keyboard-symbols")
        assertTrue(actual.hasFocus())
        assertEquals("17÷4.18", actual.text.toString())
        assertTrue(descendants(sheet.content).single { it.contentDescription == "算式符号" }.getGlobalVisibleRect(android.graphics.Rect()))
        actual.onEditorAction(EditorInfo.IME_ACTION_DONE)
        assertFalse(sheet.isShowing)
        Store(context).use { store -> assertEquals(200.0 * (17.0 / 4.18) / 100, store.entries("2026-11-06").single { it.name == "键盘测试" }.kcal, 1e-12) }
        controller.pause().stop().destroy()
    }
    @Test fun numericInputAllowsPastedCharactersAndRejectsOnlyOnCompletion() {
        val controller = activity(0); val main = controller.get()
        val c = LinearLayout(main).apply { orientation = LinearLayout.VERTICAL; isFocusableInTouchMode = true }
        val first = EditText(main).also { c.addView(it); NumberInput.configure(it, "") }
        val second = EditText(main).also { c.addView(it); NumberInput.configure(it, "") }
        main.setContentView(c)
        var submitted = 0
        FormKeyboard(c) { submitted++ }
        first.requestFocus(); first.onCreateInputConnection(EditorInfo())!!.commitText("17/4.18abc", 1)
        assertEquals("17/4.18abc", first.text.toString()); assertNull(first.error)
        first.onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertNotNull(first.error); assertTrue(first.hasFocus()); assertEquals(0, submitted)
        first.setText("17/4.18"); first.onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertTrue(second.hasFocus()); assertEquals(1, descendants(c).count { it.contentDescription == "算式符号" })
        second.setText("123.4"); second.setSelection(0, 3); click(descendants(c).single { it.contentDescription == "插入 −" })
        assertEquals("−.4", second.text.toString()); second.onEditorAction(EditorInfo.IME_ACTION_DONE)
        assertEquals(-0.4, FoodMath.number(NumberInput.raw(second))!!, 0.0); assertEquals(1, submitted)
        controller.pause().stop().destroy()
    }
    @Test fun newLauncherIntentOnAnAlreadyRunningActivityRefreshesTheDateImmediately() {
        val controller = activity(0); val today = java.time.LocalDate.now()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("last_open_date", today.minusDays(1).toString()).commit()
        controller.newIntent(android.content.Intent(context, MainActivity::class.java).setAction(android.content.Intent.ACTION_MAIN))
        assertDate(controller.get(), today)
        controller.pause().stop().destroy()
    }
}
