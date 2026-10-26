package cn.qingheng.app

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText

object NumberInput {
    @Suppress("UNCHECKED_CAST")
    fun raw(field: EditText): String {
        val visible = field.text.toString()
        return (field.getTag(R.id.numeric_initial) as? Pair<String, String>)?.takeIf { field.getTag(R.id.numeric_dirty) != true && it.first == visible }?.second ?: visible
    }
    fun setValue(field: EditText, value: Double?) { setRaw(field, value?.toString() ?: "") }
    private fun setRaw(field: EditText, raw: String) {
        val display = raw.toDoubleOrNull()?.let { DecimalText.display(it) } ?: raw
        field.setTag(R.id.numeric_initial, display to raw); field.setTag(R.id.numeric_setting, true); field.setText(display)
        field.setTag(R.id.numeric_setting, false); field.setTag(R.id.numeric_dirty, false)
    }
    fun configure(field: EditText, initial: String) {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        field.imeOptions = EditorInfo.IME_ACTION_DONE
        setRaw(field, initial)
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (field.getTag(R.id.numeric_setting) != true) field.setTag(R.id.numeric_dirty, true) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        field.setOnFocusChangeListener { _, focused -> if (!focused) finish(field) }
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { val focused = field.hasFocus(); field.clearFocus(); if (!focused) finish(field); true } else false
        }
    }
    fun onFinished(field: EditText, action: () -> Unit) { field.setTag(R.id.numeric_finished, action) }
    @Suppress("UNCHECKED_CAST")
    private fun finish(field: EditText) {
        val value = Expressions.evaluate(raw(field))
        field.error = if (field.text.isNotBlank() && value == null) "计算式无效：支持 + − × ÷、括号和%，不能除以0" else null
        if (value != null) setValue(field, value)
        (field.getTag(R.id.numeric_finished) as? (() -> Unit))?.invoke()
    }
}
