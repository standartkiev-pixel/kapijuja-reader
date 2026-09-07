package com.kapijuja.reader

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView

object KapijujaUiTheme {
    const val NAVY: Int = 0xFF031126.toInt()
    const val PANEL: Int = 0xE8132846.toInt()
    const val PANEL_DARK: Int = 0xE806172C.toInt()
    const val BLUE: Int = 0xFF168CFF.toInt()
    const val BLUE_DARK: Int = 0xFF0A3A72.toInt()
    const val CYAN: Int = 0xFF61C7FF.toInt()
    const val SILVER: Int = 0xFFF0F4FA.toInt()
    const val MUTED: Int = 0xFFB8C7D9.toInt()
    const val AMBER: Int = 0xFFFFA000.toInt()

    fun applyWindow(activity: Activity) {
        activity.window.statusBarColor = Color.rgb(1, 8, 20)
        activity.window.navigationBarColor = Color.rgb(1, 8, 20)
        if (Build.VERSION.SDK_INT >= 23) {
            activity.window.decorView.systemUiVisibility =
                activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    fun applySafeArea(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        view.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                v.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom
                )
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    initialLeft + insets.systemWindowInsetLeft,
                    initialTop + insets.systemWindowInsetTop,
                    initialRight + insets.systemWindowInsetRight,
                    initialBottom + insets.systemWindowInsetBottom
                )
            }
            insets
        }
        view.post { view.requestApplyInsets() }
    }

    fun title(view: TextView) {
        view.setTextColor(SILVER)
    }

    fun secondary(view: TextView) {
        view.setTextColor(MUTED)
    }

    fun input(context: Context, view: EditText) {
        view.setTextColor(SILVER)
        view.setHintTextColor(MUTED)
        view.background = panel(context, PANEL_DARK, BLUE_DARK, 1, 14)
        view.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
    }

    fun button(context: Context, view: Button, primary: Boolean = false) {
        view.setTextColor(Color.WHITE)
        view.isAllCaps = false
        view.background = if (primary) primaryButton(context) else normalButton(context)
    }

    fun normalButton(context: Context): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.rgb(7, 29, 57), Color.rgb(10, 71, 126))
    ).apply {
        cornerRadius = dp(context, 13).toFloat()
        setStroke(dp(context, 1), BLUE)
    }

    fun primaryButton(context: Context): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.rgb(8, 54, 126), Color.rgb(12, 134, 245))
    ).apply {
        cornerRadius = dp(context, 18).toFloat()
        setStroke(dp(context, 2), AMBER)
    }

    fun panel(
        context: Context,
        fill: Int = PANEL,
        stroke: Int = BLUE_DARK,
        widthDp: Int = 1,
        radiusDp: Int = 14
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(fill)
        setStroke(dp(context, widthDp), stroke)
    }

    fun progress(view: ProgressBar) {
        view.progressTintList = ColorStateList.valueOf(AMBER)
        view.indeterminateTintList = ColorStateList.valueOf(CYAN)
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
