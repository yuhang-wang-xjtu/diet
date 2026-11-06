package cn.qingheng.app

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
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
    private fun snapshot(root: View, name: String) {
        root.setPadding(root.paddingLeft, 36, root.paddingRight, if (name.startsWith("food-category") || name == "food-entry") 24 else 0)
        root.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 390, 800)
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
            click(text(root, "手动输入食物"))
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
}
