package cn.qingheng.app

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity

class FoodManagerActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var photos: FoodPhotoPicker
    private var category = "全部"
    private var query = ""
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store = Store(this); photos = FoodPhotoPicker(this)
        category = savedInstanceState?.getString("category") ?: "全部"; query = savedInstanceState?.getString("query") ?: ""
        render()
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("category", category); out.putString("query", query) }
    private fun library() = FoodLibraryUi(this, store, photos) { }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)); setBackgroundColor(Color.rgb(246, 247, 242)) }
        val header = LinearLayout(this)
        header.addView(Button(this).apply { text = "‹ 返回"; setOnClickListener { finish() } })
        header.addView(TextView(this).apply { text = "管理我的菜品"; textSize = 22f; setTextColor(Color.rgb(31, 53, 44)); setPadding(dp(12), dp(12), 0, 0) })
        root.addView(header)
        root.addView(TextView(this).apply { text = "编辑分类、图片、营养和配方。旧菜品保留在“原有菜品”；修改快捷项不改写历史记餐。"; textSize = 13f; setPadding(0, dp(8), 0, dp(8)) })
        val foods = store.quickFoods(); val categories = listOf("全部") + FoodCategories.categories(foods).filter { c -> foods.any { FoodCategories.of(it) == c } }
        if (category !in categories) category = "全部"
        val picker = Spinner(this).apply { adapter = ArrayAdapter(this@FoodManagerActivity, android.R.layout.simple_spinner_dropdown_item, categories); setSelection(categories.indexOf(category)); minimumHeight = dp(48) }; root.addView(picker)
        val search = EditText(this).apply { hint = "搜索我的菜品"; setSingleLine(true); setText(query) }; root.addView(search)
        val count = TextView(this); root.addView(count)
        val grid = FoodTiles.grid(this); root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        var filtered = foods
        fun filter() {
            query = search.text.toString(); filtered = foods.filter { (category == "全部" || FoodCategories.of(it) == category) && it.name.contains(query.trim(), true) }
            count.text = "${filtered.size}项 · 点击方格编辑"; grid.adapter = FoodTiles(this, filtered.map { FoodTiles.food(it) })
        }
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { category = categories[position]; filter() }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        grid.setOnItemClickListener { _, _, position, _ -> actions(filtered[position]) }; filter()
        root.addView(Button(this).apply { text = "批量调整当前显示菜品的分类"; isEnabled = foods.isNotEmpty(); setOnClickListener { changeCategory(filtered) } })
        val add = LinearLayout(this)
        add.addView(Button(this).apply { text = "＋ 食物"; setOnClickListener { library().edit(null, false) { render() } } }, LinearLayout.LayoutParams(0, -2, 1f))
        add.addView(Button(this).apply { text = "＋ 配方"; setOnClickListener { library().edit(null, true) { render() } } }, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(add)
        root.addView(Button(this).apply { text = "复制内置或已有食物"; setOnClickListener {
            FoodLibraryUi(this@FoodManagerActivity, store, photos) { food -> library().edit(food?.copy(id = 0)) { render() } }.show()
        } })
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()); v.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, dp(12) + bars.bottom) }
            else { @Suppress("DEPRECATION") v.setPadding(dp(20), dp(12) + insets.systemWindowInsetTop, dp(20), dp(12) + insets.systemWindowInsetBottom) }
            insets
        }; setContentView(root); root.requestApplyInsets()
    }
    private fun actions(food: QuickFood) {
        if (photos.busy) { Toast.makeText(this, "图片处理中，请稍候", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle(food.name).setItems(arrayOf("编辑菜品 / 配方", "修改分类", "选择图片", "使用默认图片", "删除快捷项")) { _, index -> when (index) {
            0 -> library().edit(food) { render() }
            1 -> changeCategory(listOf(food))
            2 -> photos.choose { image -> store.quickFoods().find { it.id == food.id }?.let { store.saveQuickFood(it.copy(image = image)) }; render() }
            3 -> { store.saveQuickFood(food.copy(image = "")); render() }
            4 -> AlertDialog.Builder(this).setTitle("删除“${food.name}”？").setMessage("只删除快捷项，已记录的饮食和营养保留。").setPositiveButton("删除") { _, _ -> store.deleteQuickFood(food); render() }.setNegativeButton("取消", null).show()
        } }.setNegativeButton("关闭", null).show()
    }
    private fun changeCategory(foods: List<QuickFood>) {
        if (foods.isEmpty()) { Toast.makeText(this, "没有要调整的菜品", Toast.LENGTH_SHORT).show(); return }
        val field = AutoCompleteTextView(this).apply {
            hint = "输入或选择分类"; setSingleLine(true); threshold = 0
            setAdapter(ArrayAdapter(this@FoodManagerActivity, android.R.layout.simple_dropdown_item_1line, FoodCategories.categories(store.quickFoods() + FoodCatalog.all)))
            setOnClickListener { showDropDown() }
        }
        val dialog = AlertDialog.Builder(this).setTitle("调整${foods.size}项的分类").setView(field).setPositiveButton("保存", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = field.text.toString().trim()
            if (value.isBlank() || value.length > 30) field.error = "请输入1–30字分类"
            else { store.moveQuickFoods(foods.map { it.id }.toSet(), value); dialog.dismiss(); category = value; render() }
        } }; dialog.show()
    }
    override fun onDestroy() { store.close(); super.onDestroy() }
}
