package org.gnosco.share2archivetoday.download.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.ListView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.gnosco.share2archivetoday.R

/**
 * Interactive swipe-to-reveal on history rows.
 *
 * Swipe left reveals Open; swipe right reveals Remove. Progress scales the
 * action labels so the gesture teaches the mapping before commit.
 *
 * While a horizontal drag is active (or was on this gesture), click / long-press
 * must be ignored — ListView's long-press timer otherwise still fires once we
 * start consuming MOVE events.
 */
class HistorySwipeController(
    private val listView: ListView,
    private val onOpen: (position: Int) -> Unit,
    private val onDelete: (position: Int) -> Unit,
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(listView.context).scaledTouchSlop
    private val density = listView.resources.displayMetrics.density

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    /** True from drag-start until the next DOWN — blocks click/long-press for this gesture. */
    private var gestureOwnedBySwipe = false
    private var activePosition = -1
    private var activeRow: View? = null
    private var foreground: View? = null
    private var openLabel: View? = null
    private var deleteLabel: View? = null
    private var crossedThreshold = false
    private var animator: ValueAnimator? = null

    /** Whether the current (or just-finished) gesture was a swipe, not a tap/hold. */
    fun shouldSuppressItemInteraction(): Boolean = gestureOwnedBySwipe || dragging

    fun attach() {
        listView.setOnTouchListener(this)
    }

    fun resetRow(row: View) {
        val fg = row.findViewById<View>(R.id.row_foreground) ?: return
        fg.translationX = 0f
        row.findViewById<View>(R.id.row_action_open)?.alpha = 0.35f
        row.findViewById<View>(R.id.row_action_delete)?.alpha = 0.35f
    }

    private fun haptic(view: View, type: Int) {
        view.performHapticFeedback(type)
    }

    private fun confirmHaptic(view: View) {
        if (Build.VERSION.SDK_INT >= 30) {
            haptic(view, HapticFeedbackConstants.CONFIRM)
        } else {
            haptic(view, HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                dragging = false
                gestureOwnedBySwipe = false
                crossedThreshold = false
                downX = event.x
                downY = event.y
                val pos = listView.pointToPosition(event.x.toInt(), event.y.toInt())
                if (pos < 0) {
                    clearActive()
                    return false
                }
                // Close any other open row.
                resetOtherRows(pos)
                activePosition = pos
                activeRow = listView.getChildAt(pos - listView.firstVisiblePosition)
                foreground = activeRow?.findViewById(R.id.row_foreground)
                openLabel = activeRow?.findViewById(R.id.row_action_open)
                deleteLabel = activeRow?.findViewById(R.id.row_action_delete)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val fg = foreground ?: return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                        beginSwipe()
                    } else {
                        return false
                    }
                }
                val maxW = fg.width.toFloat().coerceAtLeast(1f)
                val clamped = min(maxW, max(-maxW, dx))
                fg.translationX = clamped
                updateReveal(clamped, maxW)
                val threshold = commitDistance(maxW)
                val nowPast = abs(clamped) >= threshold
                if (nowPast && !crossedThreshold) {
                    crossedThreshold = true
                    haptic(listView, HapticFeedbackConstants.CONTEXT_CLICK)
                } else if (!nowPast) {
                    crossedThreshold = false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    clearActive()
                    return false
                }
                val fg = foreground
                val maxW = fg?.width?.toFloat()?.coerceAtLeast(1f) ?: 1f
                val tx = fg?.translationX ?: 0f
                val threshold = commitDistance(maxW)
                val position = activePosition
                when {
                    event.actionMasked == MotionEvent.ACTION_CANCEL ->
                        springBack(fg)
                    tx >= threshold ->
                        commitSwipe(fg, maxW, open = false, position) // swipe right → remove
                    tx <= -threshold ->
                        commitSwipe(fg, -maxW, open = true, position) // swipe left → open
                    else ->
                        springBack(fg)
                }
                dragging = false
                // Keep gestureOwnedBySwipe until the next DOWN so the ListView
                // click/long-press that may still be dispatched is ignored.
                return true
            }
        }
        return false
    }

    private fun beginSwipe() {
        dragging = true
        gestureOwnedBySwipe = true
        listView.parent?.requestDisallowInterceptTouchEvent(true)
        // We are about to consume MOVE; without this, ListView's pending long-press fires.
        listView.cancelLongPress()
        activeRow?.cancelLongPress()
        haptic(listView, HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun commitDistance(rowWidth: Float): Float =
        max(96f * density, rowWidth * 0.32f)

    private fun updateReveal(translationX: Float, rowWidth: Float) {
        val progress = min(1f, abs(translationX) / commitDistance(rowWidth))
        if (translationX > 0) {
            // Swiping right → reveal Remove (left side)
            deleteLabel?.alpha = 0.35f + 0.65f * progress
            openLabel?.alpha = 0.2f
            deleteLabel?.scaleX = 0.92f + 0.08f * progress
            deleteLabel?.scaleY = 0.92f + 0.08f * progress
        } else if (translationX < 0) {
            // Swiping left → reveal Open (right side)
            openLabel?.alpha = 0.35f + 0.65f * progress
            deleteLabel?.alpha = 0.2f
            openLabel?.scaleX = 0.92f + 0.08f * progress
            openLabel?.scaleY = 0.92f + 0.08f * progress
        } else {
            openLabel?.alpha = 0.35f
            deleteLabel?.alpha = 0.35f
        }
    }

    private fun springBack(fg: View?) {
        if (fg == null) {
            clearActive()
            return
        }
        animateTranslation(fg, 0f) {
            openLabel?.alpha = 0.35f
            deleteLabel?.alpha = 0.35f
            clearActive()
        }
    }

    private fun commitSwipe(fg: View?, target: Float, open: Boolean, position: Int) {
        if (fg == null || position < 0) {
            clearActive()
            return
        }
        confirmHaptic(listView)
        animateTranslation(fg, target) {
            if (open) onOpen(position) else onDelete(position)
            fg.translationX = 0f
            openLabel?.alpha = 0.35f
            deleteLabel?.alpha = 0.35f
            clearActive()
        }
    }

    private fun animateTranslation(fg: View, to: Float, end: () -> Unit) {
        animator?.cancel()
        val from = fg.translationX
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                fg.translationX = v
                updateReveal(v, fg.width.toFloat().coerceAtLeast(1f))
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = end()
            })
            start()
        }
    }

    private fun resetOtherRows(keepPosition: Int) {
        for (i in 0 until listView.childCount) {
            val child = listView.getChildAt(i) ?: continue
            val pos = listView.firstVisiblePosition + i
            if (pos == keepPosition) continue
            resetRow(child)
        }
    }

    private fun clearActive() {
        activePosition = -1
        activeRow = null
        foreground = null
        openLabel = null
        deleteLabel = null
    }
}
