package cn.qingheng.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*

data class FoodTile(val title: String, val subtitle: String, val image: String = "", val fallback: String = FoodIcons.categoryKey(FoodCategories.LEGACY), val selected: Boolean = false)
class FoodTiles(private val context: Context, private val items: List<FoodTile>) : BaseAdapter() {
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private data class Cell(val image: ImageView, val title: TextView, val subtitle: TextView)
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val root = convertView as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(225, 233, 223)) }
            val image = ImageView(context); addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
            val title = TextView(context).apply { textSize = 15f; setTextColor(Color.rgb(31, 53, 44)); setTypeface(typeface, Typeface.BOLD); maxLines = 2; setPadding(0, dp(6), 0, 0) }; addView(title)
            val subtitle = TextView(context).apply { textSize = 11f; maxLines = 1; setTextColor(Color.rgb(116, 127, 117)) }; addView(subtitle)
            tag = Cell(image, title, subtitle)
        }
        val columns = (parent as? GridView)?.numColumns?.takeIf { it > 0 } ?: 2
        val width = ((if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels - dp(64)) - dp(12) * (columns - 1)) / columns
        root.layoutParams = android.widget.AbsListView.LayoutParams(-1, width.coerceAtLeast(dp(if (columns > 2) 80 else 110)))
        val item = items[position]; val cell = root.tag as Cell
        (root.background as GradientDrawable).apply {
            setColor(if (item.selected) Color.rgb(235, 246, 238) else Color.WHITE)
            setStroke(dp(if (item.selected) 2 else 1), if (item.selected) Color.rgb(36, 122, 98) else Color.rgb(225, 233, 223))
        }
        cell.title.text = item.title; cell.subtitle.text = item.subtitle
        cell.subtitle.visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
        FoodPhotos.display(cell.image, item.image, item.fallback)
        root.contentDescription = "${item.title}，${item.subtitle}"
        return root
    }
    companion object {
        fun grid(context: Context, columns: Int = 2) = GridView(context).apply {
            numColumns = columns; val gap = (12 * resources.displayMetrics.density).toInt(); horizontalSpacing = gap; verticalSpacing = gap
            stretchMode = GridView.STRETCH_COLUMN_WIDTH; clipToPadding = false
        }
        fun food(food: QuickFood) = FoodTile(food.name, "${FoodValues.f(food.values.kcal)} kcal / ${food.basisGrams?.let { FoodValues.f(it) + "g" } ?: "整锅"}", food.image, FoodPhotos.fallback(food))
    }
}

/** Wraps all tiles in the page's ScrollView, without a second scrolling/touch surface. */
class FoodTileGrid(context: Context) : GridLayout(context) {
    private val gap = DesignUi.dp(context, 12)
    init { columnCount = 2; orientation = HORIZONTAL }
    fun submit(items: List<FoodTile>, select: (Int) -> Unit) {
        removeAllViews()
        val adapter = FoodTiles(context, items)
        items.indices.forEach { index ->
            val tile = adapter.getView(index, null, this)
            val params = LayoutParams(spec(index / 2), spec(index % 2)).apply {
                width = 0; height = 0
                rightMargin = if (index % 2 == 0) gap else 0
                bottomMargin = if (index / 2 < (items.size - 1) / 2) gap else 0
            }
            tile.setOnClickListener { select(index) }; tile.isFocusable = true
            addView(tile, params)
        }
        visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }
    fun select(position: Int) {
        for (index in 0 until childCount) {
            val selected = index == position
            (getChildAt(index).background as GradientDrawable).apply {
                setColor(if (selected) Color.rgb(235, 246, 238) else Color.WHITE)
                setStroke(DesignUi.dp(context, if (selected) 2 else 1), if (selected) DesignUi.green else Color.rgb(225, 233, 223))
            }
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = ((MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - gap) / 2).coerceAtLeast(1)
        for (index in 0 until childCount) (getChildAt(index).layoutParams as LayoutParams).apply { width = side; height = side }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
