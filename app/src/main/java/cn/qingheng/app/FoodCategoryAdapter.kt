package cn.qingheng.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** The category menu and editable category dropdown share the same bundled pictures. */
class FoodCategoryAdapter(context: Context, categories: List<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, categories) {
    private data class Row(val image: ImageView, val title: TextView)
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private fun cell(position: Int, reused: View?): View {
        val root = reused as? LinearLayout ?: LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48); setPadding(dp(6), dp(4), dp(8), dp(4))
            val image = ImageView(context); addView(image, LinearLayout.LayoutParams(dp(40), dp(40)))
            val title = TextView(context).apply { textSize = 16f; setTextColor(Color.rgb(31, 53, 44)); setPadding(dp(8), 0, 0, 0) }
            addView(title, LinearLayout.LayoutParams(0, -2, 1f)); tag = Row(image, title)
        }
        val title = getItem(position).orEmpty(); val row = root.tag as Row
        row.title.text = title; FoodPhotos.display(row.image, FoodIcons.categoryKey(title))
        root.contentDescription = title
        return root
    }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = cell(position, convertView)
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = cell(position, convertView)
}
