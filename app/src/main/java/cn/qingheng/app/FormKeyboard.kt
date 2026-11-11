package cn.qingheng.app

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout

/** Route keyboard actions through the same validation and save button as a tap. */
class FormKeyboard(private val root: View, private val submit: () -> Unit) {
    private var activeNumber: EditText? = null
    private var symbols: LinearLayout? = null
    /** One contextual symbol row per form; numeric IMEs often omit division and parentheses. */
    fun numberFocus(field: EditText, focused: Boolean) {
        if (!focused && activeNumber != field) return
        symbols?.let { (it.parent as? ViewGroup)?.removeView(it) }
        symbols = null; activeNumber = null
        if (!focused) return
        var anchor: View = field
        var container = anchor.parent as? ViewGroup
        while (container != null && (container !is LinearLayout || container.orientation != LinearLayout.VERTICAL)) {
            anchor = container; container = anchor.parent as? ViewGroup
        }
        val column = container as? LinearLayout ?: return
        activeNumber = field
        val row = DesignUi.row(root.context).apply {
            contentDescription = "算式符号"; setPadding(0, DesignUi.dp(context, 4), 0, 0)
        }
        listOf("+", "−", "×", "÷", "(", ")", "%").forEach { symbol ->
            val key = DesignUi.button(root.context, symbol, false) { NumberInput.insert(field, symbol) }.apply {
                textSize = 18f; minWidth = 0; minimumWidth = 0
                minHeight = DesignUi.dp(context, 44); minimumHeight = minHeight
                setPadding(0, 0, 0, 0); isFocusable = false; isFocusableInTouchMode = false
                contentDescription = "插入 $symbol"
            }
            row.addView(key, LinearLayout.LayoutParams(0, DesignUi.dp(root.context, 44), 1f).apply { marginEnd = DesignUi.dp(root.context, 3) })
        }
        column.addView(row, column.indexOfChild(anchor), LinearLayout.LayoutParams(-1, -2))
        symbols = row
    }
    private fun fields(view: View = root): List<EditText> = when {
        view.visibility != View.VISIBLE || !view.isEnabled -> emptyList()
        view is EditText -> listOf(view)
        view is ViewGroup -> (0 until view.childCount).flatMap { fields(view.getChildAt(it)) }
        else -> emptyList()
    }

    init { refresh() }

    fun refresh() {
        val inputs = fields()
        activeNumber?.takeIf { it !in inputs }?.let { numberFocus(it, false) }
        inputs.forEach { if (it.id == View.NO_ID) it.id = View.generateViewId() }
        inputs.forEachIndexed { index, field ->
            field.setTag(R.id.form_keyboard, this)
            val next = inputs.getOrNull(index + 1)
            val action = if (next == null) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            val options = action or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            val changed = field.imeOptions != options
            field.imeOptions = options
            field.setImeActionLabel(if (next == null) "完成" else "下一个", action)
            field.nextFocusForwardId = next?.id ?: View.NO_ID
            field.nextFocusDownId = next?.id ?: View.NO_ID
            field.setOnEditorActionListener { _, requested, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (requested != EditorInfo.IME_ACTION_NEXT && requested != EditorInfo.IME_ACTION_DONE && !enter) false
                else {
                    if (event == null || event.action == KeyEvent.ACTION_UP) {
                        val valid = field.getTag(R.id.numeric_setting) == null || NumberInput.finish(field)
                        if (valid) {
                            val current = fields()
                            val following = current.getOrNull(current.indexOf(field) + 1)
                            if (following != null) {
                                following.requestFocus(); following.setSelection(following.text.length)
                            } else submit()
                        }
                    }
                    true
                }
            }
            if (changed && field.hasFocus()) root.context.getSystemService(InputMethodManager::class.java)?.restartInput(field)
        }
        inputs.firstOrNull { it.hasFocus() && it.getTag(R.id.numeric_setting) != null }?.let { if (it != activeNumber) numberFocus(it, true) }
    }
}
