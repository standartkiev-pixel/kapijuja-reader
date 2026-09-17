package com.kapijuja.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import kotlin.math.max

/**
 * A real draggable scrollbar handle for long Reader documents.
 *
 * Android's ordinary ScrollView scrollbar is only an indicator; this overlay
 * lets the user grab the thumb and jump through a book-sized document quickly.
 */
class ReaderFastScroller(context: Context) : View(context) {
    private var scrollView: ScrollView? = null
    private var thumbTop = 0f
    private var thumbHeight = 0f
    private var dragOffset = 0f
    private var dragging = false

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KapijujaUiTheme.BLUE_DARK
        alpha = 150
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KapijujaUiTheme.CYAN
        alpha = 235
    }

    fun attachTo(scroll: ScrollView) {
        scrollView = scroll
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> invalidate() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateVisibility()
            invalidate()
        }
        scroll.post {
            updateVisibility()
            invalidate()
        }
    }

    private fun updateVisibility() {
        val scroll = scrollView ?: return
        val child = scroll.getChildAt(0)
        val range = if (child == null) 0 else (child.height - scroll.height).coerceAtLeast(0)
        visibility = if (scroll.height > 0 && range > scroll.height) VISIBLE else INVISIBLE
    }

    private fun geometry(): Geometry? {
        val scroll = scrollView ?: return null
        val child = scroll.getChildAt(0) ?: return null
        val scrollRange = (child.height - scroll.height).coerceAtLeast(0)
        if (scrollRange <= 0 || height <= 0) return null

        val inset = dp(6).toFloat()
        val trackTop = inset
        val trackBottom = (height - inset).coerceAtLeast(trackTop + 1f)
        val trackLength = trackBottom - trackTop
        val visibleRatio = (scroll.height.toFloat() / child.height.toFloat()).coerceIn(0f, 1f)
        val minThumb = dp(48).toFloat()
        val actualThumbHeight = max(minThumb, trackLength * visibleRatio).coerceAtMost(trackLength)
        val travel = (trackLength - actualThumbHeight).coerceAtLeast(0f)
        val fraction = (scroll.scrollY.toFloat() / scrollRange.toFloat()).coerceIn(0f, 1f)
        val actualThumbTop = trackTop + travel * fraction

        thumbTop = actualThumbTop
        thumbHeight = actualThumbHeight
        return Geometry(trackTop, trackBottom, travel, scrollRange)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateVisibility()
        if (visibility != VISIBLE) return
        val g = geometry() ?: return
        val cx = width / 2f
        val trackHalf = dp(2).toFloat()
        val thumbHalf = dp(5).toFloat()
        canvas.drawRoundRect(
            cx - trackHalf,
            g.trackTop,
            cx + trackHalf,
            g.trackBottom,
            trackHalf,
            trackHalf,
            trackPaint
        )
        canvas.drawRoundRect(
            cx - thumbHalf,
            thumbTop,
            cx + thumbHalf,
            thumbTop + thumbHeight,
            thumbHalf,
            thumbHalf,
            thumbPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scroll = scrollView ?: return false
        val g = geometry() ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                dragOffset =
                    if (event.y in thumbTop..(thumbTop + thumbHeight)) {
                        event.y - thumbTop
                    } else {
                        thumbHeight / 2f
                    }
                moveThumbTo(event.y, g, scroll)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                moveThumbTo(event.y, g, scroll)
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveThumbTo(y: Float, g: Geometry, scroll: ScrollView) {
        val desiredTop =
            (y - dragOffset)
                .coerceIn(g.trackTop, g.trackTop + g.travel)
        val fraction = if (g.travel <= 0f) 0f else (desiredTop - g.trackTop) / g.travel
        scroll.scrollTo(0, (fraction * g.scrollRange).toInt())
        invalidate()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class Geometry(
        val trackTop: Float,
        val trackBottom: Float,
        val travel: Float,
        val scrollRange: Int
    )
}
