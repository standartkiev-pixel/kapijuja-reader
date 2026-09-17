package com.kapijuja.reader

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

/**
 * Android UI adapter for Grok-only text markup tools.
 *
 * The editor keeps the frequent stress action as a dedicated compact button.
 * Less-frequent Grok controls are grouped into a short two-level menu so the
 * toolbar can remain usable while the on-screen keyboard is visible.
 */
object GrokEditorToolbar {
    fun createStressButton(
        activity: Activity,
        editor: EditText,
        setResultText: (String) -> Unit,
        pauseIfPlaying: () -> Unit,
        scrollToOffset: (Int) -> Unit
    ): Button {
        val stressAction =
            GrokEditorMarkup.actions.first { it.kind == GrokEditorMarkup.Kind.STRESS }
        return Button(activity).apply {
            text = "´"
            textSize = 22f
            contentDescription = GrokEditorMarkup.label(stressAction, UiText.language(activity))
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            visibility = View.GONE
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                applyAction(
                    activity = activity,
                    editor = editor,
                    setResultText = setResultText,
                    action = stressAction,
                    pauseIfPlaying = pauseIfPlaying,
                    scrollToOffset = scrollToOffset
                )
            }
        }.also { KapijujaUiTheme.button(activity, it, primary = true) }
    }

    fun create(
        activity: Activity,
        editor: EditText,
        setResultText: (String) -> Unit,
        pauseIfPlaying: () -> Unit,
        scrollToOffset: (Int) -> Unit
    ): Button {
        val button = Button(activity).apply {
            text = "Grok ▾"
            textSize = 12f
            contentDescription =
                tr(
                    activity,
                    "Инструменты Grok",
                    "Narzędzia Grok",
                    "Grok tools"
                )
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            visibility = View.GONE
            isFocusable = false
            isFocusableInTouchMode = false
        }
        KapijujaUiTheme.button(activity, button)
        button.setOnClickListener {
            showMainMenu(
                activity = activity,
                editor = editor,
                setResultText = setResultText,
                pauseIfPlaying = pauseIfPlaying,
                scrollToOffset = scrollToOffset
            )
        }
        return button
    }

    fun updateVisibility(
        stressButton: Button,
        menuButton: Button,
        editMode: Boolean,
        engine: String
    ) {
        val visibility =
            if (editMode && engine == SettingsStore.ENGINE_XAI) {
                View.VISIBLE
            } else {
                View.GONE
            }
        stressButton.visibility = visibility
        menuButton.visibility = visibility
    }

    private fun showMainMenu(
        activity: Activity,
        editor: EditText,
        setResultText: (String) -> Unit,
        pauseIfPlaying: () -> Unit,
        scrollToOffset: (Int) -> Unit
    ) {
        val labels =
            arrayOf(
                tr(activity, "´  Ударение", "´  Akcent", "´  Stress"),
                tr(
                    activity,
                    "Подача, темп и тон ›",
                    "Sposób czytania, tempo i ton ›",
                    "Delivery, speed and pitch ›"
                ),
                tr(
                    activity,
                    "Паузы, дыхание и звуки ›",
                    "Pauzy, oddech i dźwięki ›",
                    "Pauses, breath and sounds ›"
                )
            )

        AlertDialog.Builder(activity)
            .setTitle(tr(activity, "Grok — инструменты", "Grok — narzędzia", "Grok tools"))
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> {
                        val stress =
                            GrokEditorMarkup.actions.first {
                                it.kind == GrokEditorMarkup.Kind.STRESS
                            }
                        applyAction(
                            activity,
                            editor,
                            setResultText,
                            stress,
                            pauseIfPlaying,
                            scrollToOffset
                        )
                    }
                    1 -> showActionList(
                        activity,
                        editor,
                        setResultText,
                        GrokEditorMarkup.Kind.WRAP,
                        pauseIfPlaying,
                        scrollToOffset
                    )
                    2 -> showActionList(
                        activity,
                        editor,
                        setResultText,
                        GrokEditorMarkup.Kind.INSERT,
                        pauseIfPlaying,
                        scrollToOffset
                    )
                }
            }
            .setNegativeButton(tr(activity, "Отмена", "Anuluj", "Cancel"), null)
            .show()
    }

    private fun showActionList(
        activity: Activity,
        editor: EditText,
        setResultText: (String) -> Unit,
        kind: GrokEditorMarkup.Kind,
        pauseIfPlaying: () -> Unit,
        scrollToOffset: (Int) -> Unit
    ) {
        val actions = GrokEditorMarkup.actions.filter { it.kind == kind }
        val labels =
            actions
                .map { GrokEditorMarkup.label(it, UiText.language(activity)) }
                .toTypedArray()

        val title =
            when (kind) {
                GrokEditorMarkup.Kind.WRAP ->
                    tr(
                        activity,
                        "Подача выделенного текста",
                        "Sposób czytania zaznaczonego tekstu",
                        "Selected text delivery"
                    )
                GrokEditorMarkup.Kind.INSERT ->
                    tr(
                        activity,
                        "Вставить в позицию курсора",
                        "Wstaw przy kursorze",
                        "Insert at cursor"
                    )
                else -> tr(activity, "Grok", "Grok", "Grok")
            }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(labels) { _, which ->
                applyAction(
                    activity,
                    editor,
                    setResultText,
                    actions[which],
                    pauseIfPlaying,
                    scrollToOffset
                )
            }
            .setNegativeButton(tr(activity, "Назад", "Wstecz", "Back")) { _, _ ->
                showMainMenu(
                    activity,
                    editor,
                    setResultText,
                    pauseIfPlaying,
                    scrollToOffset
                )
            }
            .show()
    }

    private fun applyAction(
        activity: Activity,
        editor: EditText,
        setResultText: (String) -> Unit,
        action: GrokEditorMarkup.Action,
        pauseIfPlaying: () -> Unit,
        scrollToOffset: (Int) -> Unit
    ) {
        pauseIfPlaying()

        try {
            val current = editor.text.toString()
            val start = editor.selectionStart.coerceAtLeast(0)
            val end = editor.selectionEnd.coerceAtLeast(0)
            val result =
                when (action.kind) {
                    GrokEditorMarkup.Kind.STRESS ->
                        GrokEditorMarkup.addStress(current, start, end)
                    GrokEditorMarkup.Kind.WRAP ->
                        GrokEditorMarkup.wrapSelection(
                            current,
                            start,
                            end,
                            action.token
                        )
                    GrokEditorMarkup.Kind.INSERT ->
                        GrokEditorMarkup.insertToken(
                            current,
                            start,
                            end,
                            action.token
                        )
                    GrokEditorMarkup.Kind.SECTION -> return
                }

            editor.setText(result.text)
            editor.setSelection(
                result.selectionStart.coerceIn(0, editor.text.length),
                result.selectionEnd.coerceIn(0, editor.text.length)
            )
            editor.requestFocus()
            scrollToOffset(result.selectionEnd)

            setResultText(
                when (action.kind) {
                    GrokEditorMarkup.Kind.STRESS ->
                        tr(
                            activity,
                            "Ударение поставлено. Можно сразу проверить кнопкой ▶.",
                            "Akcent został ustawiony. Możesz od razu sprawdzić przyciskiem ▶.",
                            "Stress mark added. You can check it immediately with ▶."
                        )
                    GrokEditorMarkup.Kind.WRAP ->
                        "<${action.token}>…</${action.token}>"
                    GrokEditorMarkup.Kind.INSERT -> action.token
                    GrokEditorMarkup.Kind.SECTION -> ""
                }
            )
        } catch (error: IllegalArgumentException) {
            Toast.makeText(
                activity,
                error.message
                    ?: tr(
                        activity,
                        "Неверное выделение",
                        "Nieprawidłowe zaznaczenie",
                        "Invalid selection"
                    ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun tr(
        activity: Activity,
        ru: String,
        pl: String,
        en: String
    ): String = UiText.get(activity, ru, pl, en)
}
