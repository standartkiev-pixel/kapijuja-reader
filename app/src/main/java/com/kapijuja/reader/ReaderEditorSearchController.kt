package com.kapijuja.reader

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object ReaderEditorSearchLogic {
    fun next(
        text: String,
        query: String,
        from: Int
    ): Int? {
        if (query.isBlank() || text.isEmpty()) return null
        val start = from.coerceIn(0, text.length)
        val direct = text.indexOf(query, startIndex = start, ignoreCase = true)
        if (direct >= 0) return direct
        val wrapped = text.indexOf(query, startIndex = 0, ignoreCase = true)
        return wrapped.takeIf { it >= 0 }
    }

    fun previous(
        text: String,
        query: String,
        before: Int
    ): Int? {
        if (query.isBlank() || text.isEmpty()) return null
        val maxStart = (text.length - query.length).coerceAtLeast(0)
        val directStart = (before - 1).coerceIn(0, maxStart)
        val direct = text.lastIndexOf(query, startIndex = directStart, ignoreCase = true)
        if (direct >= 0 && direct < before) return direct

        val wrapped = text.lastIndexOf(
            query,
            startIndex = maxStart,
            ignoreCase = true
        )
        return wrapped.takeIf { it >= 0 }
    }
}

class ReaderEditorSearchController(
    private val activity: Activity,
    private val editor: EditText,
    private val scrollToOffset: (Int) -> Unit
) {
    val view: LinearLayout

    private val queryInput: EditText
    private val statusText: TextView

    init {
        fun dp(value: Int) = KapijujaUiTheme.dp(activity, value)

        view = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(3), dp(2), dp(3), dp(2))
            background = KapijujaUiTheme.panel(
                activity,
                KapijujaUiTheme.PANEL_DARK,
                KapijujaUiTheme.BLUE,
                1,
                10
            )
        }

        queryInput = EditText(activity).apply {
            hint = UiText.get(activity, "Найти…", "Znajdź…", "Find…")
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setPadding(dp(8), 0, dp(6), 0)
        }
        KapijujaUiTheme.input(activity, queryInput)
        view.addView(
            queryInput,
            LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginEnd = dp(3)
            }
        )

        statusText = TextView(activity).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            text = ""
        }
        KapijujaUiTheme.secondary(statusText)
        view.addView(
            statusText,
            LinearLayout.LayoutParams(dp(28), dp(36)).apply {
                marginEnd = dp(2)
            }
        )

        val previous = compactButton("‹") {
            findPrevious()
        }
        view.addView(
            previous,
            LinearLayout.LayoutParams(dp(38), dp(36)).apply {
                marginEnd = dp(2)
            }
        )

        val next = compactButton("›") {
            findNext()
        }
        view.addView(
            next,
            LinearLayout.LayoutParams(dp(38), dp(36)).apply {
                marginEnd = dp(2)
            }
        )

        val close = compactButton("×") {
            hide(refocusEditor = true)
        }
        view.addView(
            close,
            LinearLayout.LayoutParams(dp(38), dp(36))
        )

        queryInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    findFromCurrentPosition()
                }
            }
        )

        queryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                findNext()
                true
            } else {
                false
            }
        }
    }

    fun toggle() {
        if (view.visibility == View.VISIBLE) {
            hide(refocusEditor = true)
        } else {
            show()
        }
    }

    fun show() {
        view.visibility = View.VISIBLE
        queryInput.requestFocus()
        queryInput.setSelection(queryInput.text.length)
        queryInput.post {
            val input =
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as?
                    InputMethodManager
            input?.showSoftInput(queryInput, InputMethodManager.SHOW_IMPLICIT)
        }
        if (queryInput.text.isNotEmpty()) {
            findFromCurrentPosition()
        }
    }

    fun hide(refocusEditor: Boolean) {
        view.visibility = View.GONE
        statusText.text = ""
        queryInput.clearFocus()

        if (refocusEditor) {
            editor.requestFocus()
            val end = maxOf(editor.selectionStart, editor.selectionEnd)
                .coerceIn(0, editor.text.length)
            editor.setSelection(end)
            editor.post {
                val input =
                    activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as?
                        InputMethodManager
                input?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun resetAndHide() {
        queryInput.setText("")
        hide(refocusEditor = false)
    }

    private fun compactButton(
        label: String,
        action: () -> Unit
    ): Button =
        Button(activity).apply {
            text = label
            textSize = 20f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { action() }
            KapijujaUiTheme.button(activity, this)
        }

    private fun findFromCurrentPosition() {
        val query = queryInput.text.toString()
        if (query.isBlank()) {
            statusText.text = ""
            return
        }

        val from =
            minOf(editor.selectionStart, editor.selectionEnd)
                .coerceAtLeast(0)
        val found =
            ReaderEditorSearchLogic.next(
                text = editor.text.toString(),
                query = query,
                from = from
            )
        selectMatch(found, query)
    }

    private fun findNext() {
        val query = queryInput.text.toString()
        if (query.isBlank()) return

        val from =
            maxOf(editor.selectionStart, editor.selectionEnd)
                .coerceAtLeast(0)
        val found =
            ReaderEditorSearchLogic.next(
                text = editor.text.toString(),
                query = query,
                from = from
            )
        selectMatch(found, query)
    }

    private fun findPrevious() {
        val query = queryInput.text.toString()
        if (query.isBlank()) return

        val before =
            minOf(editor.selectionStart, editor.selectionEnd)
                .coerceAtLeast(0)
        val found =
            ReaderEditorSearchLogic.previous(
                text = editor.text.toString(),
                query = query,
                before = before
            )
        selectMatch(found, query)
    }

    private fun selectMatch(
        start: Int?,
        query: String
    ) {
        if (start == null) {
            statusText.text = "0"
            return
        }

        val end =
            (start + query.length)
                .coerceAtMost(editor.text.length)
        editor.setSelection(start, end)
        statusText.text = "✓"
        scrollToOffset(start)
    }
}
