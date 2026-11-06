package cn.qingheng.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

object FoodIconPicker {
    fun show(activity: Activity, current: String = "", category: String? = null, chosen: (String) -> Unit) {
        fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        body.addView(TextView(activity).apply { text = activity.getString(R.string.food_icon_picker_help); textSize = 13f })
        val groups = listOf("全部") + FoodIcons.categories
        val filter = Spinner(activity).apply {
            adapter = FoodCategoryAdapter(activity, groups); minimumHeight = dp(48)
            setSelection(groups.indexOf(category).coerceAtLeast(0)); body.addView(this)
        }
        val search = EditText(activity).apply { hint = "搜索图标，例如牛奶、米饭、鸡蛋"; setSingleLine(true); minHeight = dp(48); body.addView(this) }
        val count = TextView(activity).apply { textSize = 12f; setTextColor(Color.rgb(116, 127, 117)); body.addView(this) }
        val grid = FoodTiles.grid(activity, 3); body.addView(grid, LinearLayout.LayoutParams(-1, dp(320)))
        var filtered = FoodIcons.all
        fun refresh() {
            filtered = FoodIcons.filter(groups.getOrNull(filter.selectedItemPosition)?.takeUnless { it == "全部" }, search.text.toString())
            count.text = if (filtered.isEmpty()) "没有匹配图标，请修改分类或搜索词" else "${filtered.size} 个图标 · 点击选用"
            grid.adapter = FoodTiles(activity, filtered.map { FoodTile(it.title, if (it.key == current) "已选择" else "", it.key, selected = it.key == current) })
        }
        val dialog = AlertDialog.Builder(activity).setTitle("选择食物图标").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("取消", null).setNeutralButton("素材与许可") { _, _ -> credits(activity) }.create()
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        grid.setOnItemClickListener { _, _, position, _ -> chosen(filtered[position].key); dialog.dismiss() }
        refresh(); dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { credits(activity) }
        FormKeyboard(search) { activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0); search.clearFocus() }
    }

    fun credits(activity: Activity) {
        val license = activity.assets.open("licenses/fluent-emoji-MIT.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val content = TextView(activity).apply {
            setPadding(padding, padding / 2, padding, padding); textSize = 14f; autoLinkMask = Linkify.WEB_URLS
            text = activity.getString(R.string.food_icon_credits, license)
            movementMethod = LinkMovementMethod.getInstance()
        }
        AlertDialog.Builder(activity).setTitle("图标素材与许可").setView(ScrollView(activity).apply { addView(content) }).setPositiveButton("关闭", null).show()
    }
}
