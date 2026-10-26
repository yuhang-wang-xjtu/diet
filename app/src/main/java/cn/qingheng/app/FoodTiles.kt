package cn.qingheng.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*

data class FoodTile(val title: String, val subtitle: String, val image: String = "")
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
        val width = (if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels - dp(64)) / 2 - dp(6)
        root.layoutParams = android.widget.AbsListView.LayoutParams(-1, width.coerceAtLeast(dp(110)))
        val item = items[position]; val cell = root.tag as Cell
        cell.title.text = item.title; cell.subtitle.text = item.subtitle; FoodPhotos.display(cell.image, item.image)
        root.contentDescription = "${item.title}，${item.subtitle}"
        return root
    }
    companion object {
        fun grid(context: Context) = GridView(context).apply {
            numColumns = 2; val gap = (12 * resources.displayMetrics.density).toInt(); horizontalSpacing = gap; verticalSpacing = gap
            stretchMode = GridView.STRETCH_COLUMN_WIDTH; clipToPadding = false
        }
        fun food(food: QuickFood) = FoodTile(food.name, "${FoodValues.f(food.values.kcal)} kcal / ${food.basisGrams?.let { FoodValues.f(it) + "g" } ?: "整锅"}", food.image)
    }
}
