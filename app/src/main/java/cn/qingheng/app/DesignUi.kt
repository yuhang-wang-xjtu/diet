package cn.qingheng.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.caverock.androidsvg.SVG

/** Native views and tokens translated from the supplied Figma design. */
object DesignUi {
    val ink = Color.rgb(31, 53, 44)
    val green = Color.rgb(36, 122, 98)
    val forest = Color.rgb(23, 75, 60)
    val muted = Color.rgb(95, 113, 104)
    val background = Color.rgb(246, 247, 242)
    val soft = Color.rgb(232, 239, 229)
    val food = Color.rgb(232, 239, 220)
    val lime = Color.rgb(216, 239, 165)
    val macroColors = listOf(green, Color.rgb(207, 151, 100), Color.rgb(141, 164, 86))
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    fun shape(context: Context, color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
    }
    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun text(context: Context, value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false, line: Int? = null) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        if (android.os.Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = false
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
        if (line != null) minHeight = dp(context, line)
    }
    fun space(parent: LinearLayout, value: Int) { parent.addView(View(parent.context), LinearLayout.LayoutParams(1, dp(parent.context, value))) }
    fun surface(context: Context, color: Int = Color.WHITE, radius: Int = 17, padding: Int = 15) = column(context).apply {
        background = shape(context, color, radius); val p = dp(context, padding); setPadding(p, p, p, p)
    }
    fun svg(context: Context, name: String): ImageView = ImageView(context).apply {
        bindSvg(this, name)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun bindSvg(view: ImageView, name: String) = with(view) {
        setTag(R.id.design_asset_name, name)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(0, 0, 0, 0)
        setImageDrawable(PictureDrawable(SVG.getFromAsset(context.assets, "design/$name").renderToPicture()))
    }
    fun foodFeature(context: Context, title: String, subtitle: String, detail: String, image: String, fallback: String, category: Boolean = false): LinearLayout {
        val card = surface(context, food, 20, 0)
        card.setPadding(dp(context, 18), dp(context, 22), dp(context, 25), dp(context, 24))
        val row = row(context).apply { gravity = Gravity.TOP }
        val info = column(context)
        info.addView(text(context, title, if (category) 25f else 26f, bold = true, line = if (category) 34 else 36))
        space(info, if (category) 9 else 10); info.addView(text(context, subtitle, 14f, muted, line = 19))
        space(info, if (category) 27 else 22); info.addView(text(context, detail, if (category) 14f else 23f, green, true, if (category) 19 else 32))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val picture = ImageView(context); FoodPhotos.display(picture, image, fallback)
        row.addView(picture, LinearLayout.LayoutParams(dp(context, 105), dp(context, 78)).apply { topMargin = dp(context, 13) })
        card.addView(row); card.minimumHeight = dp(context, if (category) 154 else 170)
        return card
    }
    fun button(context: Context, title: String, primary: Boolean = true, action: () -> Unit) = Button(context).apply {
        text = title; textSize = 16f; isAllCaps = false; includeFontPadding = false
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else green)
        val fill = shape(context, if (primary) green else soft, 15)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(35, 0, 0, 0)), fill, null)
        minHeight = dp(context, 52); minimumHeight = dp(context, 52)
        setPadding(dp(context, 15), dp(context, 8), dp(context, 15), dp(context, 8)); setOnClickListener { action() }
    }
    fun header(parent: LinearLayout, title: String, subtitle: String, back: (() -> Unit)? = null, dateAction: (() -> Unit)? = null,
        previousDate: (() -> Unit)? = null, nextDate: (() -> Unit)? = null): TextView {
        val context = parent.context
        space(parent, 14)
        val brand = row(context)
        if (back == null) {
            brand.addView(text(context, "轻衡", 14f, green, true, 19))
            brand.addView(svg(context, "brand_leaf.svg"), LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)).apply { marginStart = dp(context, 9) })
        } else {
            brand.addView(text(context, "‹  返回", 14f, green, true, 20))
            brand.minimumHeight = dp(context, 32); brand.setOnClickListener { back() }; brand.contentDescription = "返回"
        }
        parent.addView(brand, LinearLayout.LayoutParams(-1, dp(context, 20)))
        val titleView = text(context, title, 31f, bold = true, line = 42)
        space(parent, 10); parent.addView(titleView)
        space(parent, 9)
        val subtitleView = text(context, subtitle, 13f, muted, line = 18).apply {
            if (dateAction != null) { setOnClickListener { dateAction() }; contentDescription = "$subtitle，点击选择日期" }
        }
        if (previousDate != null && nextDate != null) {
            val dates = row(context).apply { background = shape(context, soft, 13) }
            fun arrow(label: String, description: String, action: () -> Unit) = button(context, label, false, action).apply {
                contentDescription = description; textSize = 23f; setPadding(0, 0, 0, 0)
            }
            dates.addView(arrow("‹", "前一天", previousDate), LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)))
            subtitleView.gravity = Gravity.CENTER
            dates.addView(subtitleView, LinearLayout.LayoutParams(0, dp(context, 48), 1f))
            dates.addView(arrow("›", "后一天", nextDate), LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)))
            parent.addView(dates, LinearLayout.LayoutParams(-1, -2)); space(parent, 9)
        } else { parent.addView(subtitleView); space(parent, 17) }
        return titleView
    }
    fun section(parent: LinearLayout, title: String, before: Int = 22) {
        space(parent, before); parent.addView(text(parent.context, title, 19f, bold = true, line = 26))
    }
    fun item(parent: LinearLayout, title: String, value: String, action: (() -> Unit)? = null, description: String? = null): LinearLayout {
        val context = parent.context
        val card = surface(context, radius = 13, padding = 15)
        if (description == null) card.gravity = Gravity.CENTER_VERTICAL else card.setPadding(dp(context, 15), dp(context, 11), dp(context, 15), dp(context, 19))
        val line = row(context)
        line.addView(text(context, title, if (description == null) 15f else 16f, bold = description != null, line = if (description == null) 21 else 22), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(text(context, value, 14f, muted, line = 19).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(line)
        if (description != null) { space(card, 20); card.addView(text(context, description, 14f, muted, line = 19)) }
        card.minimumHeight = dp(context, if (description == null) 54 else 91)
        if (action != null) { card.setOnClickListener { action() }; card.isFocusable = true }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2))
        return card
    }
    fun navigation(context: Context, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val nav = row(context).apply { setPadding(dp(context, 3), dp(context, 9), dp(context, 3), dp(context, 9)); setBackgroundColor(Color.WHITE) }
        listOf("今日" to "today", "记录" to "diary", "趋势" to "trends", "设置" to "settings").forEachIndexed { index, (label, icon) ->
            val cell = column(context).apply { gravity = Gravity.CENTER; if (index == selected) background = shape(context, soft, 12) }
            cell.addView(svg(context, "nav_$icon${if (index == selected) "_selected" else ""}.svg"), LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)))
            space(cell, 2); cell.addView(text(context, label, 12f, if (index == selected) green else muted, line = 17).apply { gravity = Gravity.CENTER })
            cell.contentDescription = label; cell.isSelected = index == selected; cell.setOnClickListener { onSelect(index) }
            nav.addView(cell, LinearLayout.LayoutParams(0, dp(context, 56), 1f).apply { if (index < 3) marginEnd = dp(context, 8) })
        }
        return nav
    }
    fun energyCard(parent: LinearLayout, totals: Totals, expenditure: Double?, automatic: Boolean, settings: () -> Unit) {
        val context = parent.context
        val card = surface(context, forest, 22, 18)
        card.setPadding(dp(context, 18), dp(context, 17), dp(context, 22), dp(context, 18))
        val top = row(context).apply { gravity = Gravity.TOP }
        val figures = column(context)
        figures.addView(text(context, "当日热量缺口 · 估算", 14f, Color.rgb(226, 239, 221), line = 19))
        space(figures, 8)
        val value = row(context).apply { gravity = Gravity.BOTTOM }
        value.addView(text(context, totals.deficit?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "待设置", if (totals.deficit == null) 35f else 51f, Color.WHITE, true, 69))
        value.addView(text(context, "kcal", 16f, lime, line = 22), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 10); bottomMargin = dp(context, 10) })
        figures.addView(value); top.addView(figures, LinearLayout.LayoutParams(0, -2, 1f))
        val gauge = FrameLayout(context)
        gauge.addView(svg(context, "intake_reference.svg"), FrameLayout.LayoutParams(dp(context, 90), dp(context, 90)))
        val percentage = if (expenditure != null && expenditure > 0) "${FoodValues.f(totals.intake / expenditure * 100)}%" else "—"
        gauge.addView(text(context, percentage, 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 1 }, FrameLayout.LayoutParams(-1, -1))
        gauge.contentDescription = "摄入占日常消耗 $percentage"
        top.addView(gauge, LinearLayout.LayoutParams(dp(context, 90), dp(context, 90)).apply { topMargin = dp(context, 9) })
        card.addView(top); space(card, 22)
        card.addView(text(context, "${if (automatic) "全天消耗" else "日常消耗"}  ${expenditure?.let { FoodValues.f(it) } ?: "待设置"}    /    饮食摄入  ${FoodValues.f(totals.intake)}", 13f, Color.rgb(226, 239, 221), line = 18))
        card.minimumHeight = dp(context, 176); card.setOnClickListener { settings() }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2))
    }
    data class Macro(val label: String, val value: Double?, val target: Double?)
    fun macros(parent: LinearLayout, values: List<Macro>, targets: Boolean = true): LinearLayout {
        val context = parent.context
        val card = row(context).apply { background = shape(context, Color.WHITE, 17); setPadding(dp(context, 14), dp(context, 13), dp(context, 26), dp(context, if (targets) 15 else 13)) }
        values.forEachIndexed { index, item ->
            val col = column(context)
            col.addView(text(context, item.label, 13f, muted, line = 18)); space(col, 5)
            col.addView(text(context, item.value?.let { "${FoodValues.f(it)} g" } ?: "—", 19f, bold = true, line = 26))
            if (targets) {
                space(col, 8)
                col.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    progressDrawable = LayerDrawable(arrayOf(shape(context, soft, 2), ClipDrawable(shape(context, macroColors[index], 2), Gravity.START, ClipDrawable.HORIZONTAL))).apply {
                        setId(0, android.R.id.background); setId(1, android.R.id.progress)
                    }
                    max = 1000; progress = if (item.target != null && item.target > 0 && item.value != null) (item.value / item.target * 1000).toInt().coerceIn(0, 1000) else 0
                    progressTintList = ColorStateList.valueOf(macroColors[index]); progressBackgroundTintList = ColorStateList.valueOf(soft)
                    contentDescription = "${item.label} ${item.value?.let { FoodValues.f(it) } ?: "未知"} 克，目标 ${item.target?.let { FoodValues.f(it) } ?: "未设置"} 克"
                }, LinearLayout.LayoutParams(-1, dp(context, 5)))
                space(col, 9); col.addView(text(context, item.target?.let { "目标 ${FoodValues.f(it)} g" } ?: "目标未设置", 11f, muted, line = 15))
            }
            card.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index < 2) marginEnd = dp(context, 17) })
        }
        card.minimumHeight = dp(context, if (targets) 114 else 84)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2)); return card
    }
}
