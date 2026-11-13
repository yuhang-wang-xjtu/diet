package cn.qingheng.app

import android.app.Activity
import android.app.Dialog
import android.view.WindowInsets
import android.widget.*

/** A scrollable secondary screen with a native back action and reachable save button. */
class DesignSheet(activity: Activity, title: String, subtitle: String, saveLabel: String? = null) : Dialog(activity, R.style.AppTheme) {
    var navigateBack: (() -> Unit)? = null
    var refreshOnResume: (() -> Unit)? = null
    val content = DesignUi.column(activity).apply { setPadding(DesignUi.dp(activity, 20), 0, DesignUi.dp(activity, 20), DesignUi.dp(activity, 20)) }
    val saveButton: Button? = saveLabel?.let { DesignUi.button(activity, it) {} }
    val footer = DesignUi.column(activity).apply {
        setPadding(DesignUi.dp(activity, 20), DesignUi.dp(activity, 12), DesignUi.dp(activity, 20), DesignUi.dp(activity, 16))
        visibility = android.view.View.GONE
    }
    val scroll = ScrollView(activity).apply { isFillViewport = true; addView(content) }
    private val heading: TextView
    fun updateTitle(title: String) { heading.text = title }
    init {
        setOwnerActivity(activity)
        val lifecycle = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) dismiss()
            else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isShowing) refreshOnResume?.invoke()
        }
        lifecycle?.addObserver(observer)
        setOnDismissListener { lifecycle?.removeObserver(observer) }
        val root = DesignUi.column(activity).apply { setBackgroundColor(DesignUi.background) }
        heading = DesignUi.header(content, title, subtitle, back = { navigateBack?.invoke() ?: dismiss() })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (saveButton != null) {
            footer.addView(saveButton, LinearLayout.LayoutParams(-1, DesignUi.dp(activity, 52))); footer.visibility = android.view.View.VISIBLE
        }
        root.addView(footer)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                footer.setPadding(DesignUi.dp(activity, 20), DesignUi.dp(activity, 12), DesignUi.dp(activity, 20), DesignUi.dp(activity, if (insets.isVisible(WindowInsets.Type.ime())) 12 else 16))
            } else { @Suppress("DEPRECATION") view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom) }
            insets
        }
        setContentView(root)
        window?.setLayout(-1, -1)
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setOnShowListener { window?.setLayout(-1, -1); root.requestApplyInsets() }
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() { navigateBack?.invoke() ?: super.onBackPressed() }
}
