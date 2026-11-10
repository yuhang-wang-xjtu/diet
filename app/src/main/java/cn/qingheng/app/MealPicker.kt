package cn.qingheng.app

import android.view.View
import android.widget.*

object MealPicker {
    fun add(parent: LinearLayout, initial: Meal, changed: (Meal) -> Unit): Spinner {
        val context = parent.context
        parent.addView(DesignUi.text(context, "餐次（可切换，零食不提醒）", 13f, DesignUi.muted))
        val picker = Spinner(context).apply {
            contentDescription = "选择餐次"
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, Meal.entries.map { it.label })
            setSelection(initial.ordinal); minimumHeight = DesignUi.dp(context, 48)
            setPadding(DesignUi.dp(context, 12), 0, DesignUi.dp(context, 12), 0)
        }
        val container = DesignUi.column(context).apply { background = DesignUi.shape(context, android.graphics.Color.WHITE, 12) }
        container.addView(picker, LinearLayout.LayoutParams(-1, DesignUi.dp(context, 48)))
        parent.addView(container)
        DesignUi.space(parent, 16)
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = changed(Meal.entries[position])
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        return picker
    }
}
