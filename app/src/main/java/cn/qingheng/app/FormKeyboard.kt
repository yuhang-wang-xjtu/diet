package cn.qingheng.app

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** Route keyboard actions through the same validation and save button as a tap. */
class FormKeyboard(private val root: View, private val submit: () -> Unit) {
    private fun fields(view: View = root): List<EditText> = when {
        view.visibility != View.VISIBLE || !view.isEnabled -> emptyList()
        view is EditText -> listOf(view)
        view is ViewGroup -> (0 until view.childCount).flatMap { fields(view.getChildAt(it)) }
        else -> emptyList()
    }

    init { refresh() }

    fun refresh() {
        val inputs = fields()
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
    }
}
