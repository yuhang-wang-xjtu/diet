package cn.qingheng.app

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Horizontal gestures change tabs; the child's ScrollView owns vertical gestures. */
class SwipePageLayout @JvmOverloads constructor(context: Context, private val navigate: (Int) -> Unit = {}) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var horizontal = false
    private var vertical = false
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // A still-flinging ScrollView claims DOWN before it knows the new direction.
        // Let it claim a vertical drag once classified, while keeping the next horizontal swipe available.
        if (!disallowIntercept || vertical) super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; horizontal = false; vertical = false }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1) { vertical = true; return false }
                val dx = abs(event.x - startX); val dy = abs(event.y - startY)
                if (!horizontal && !vertical) {
                    if (dy > slop && dy >= dx) vertical = true
                    else if (dx > slop * 2 && dx > dy * 1.5f) horizontal = true
                }
            }
        }
        return horizontal
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!horizontal) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val distance = event.x - startX
            if (abs(distance) >= DesignUi.dp(context, 48)) navigate(if (distance < 0) 1 else -1) else performClick()
            horizontal = false
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) horizontal = false
        return true
    }
    override fun performClick(): Boolean = super.performClick()
}
