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
 * Text transformations stay in [GrokEditorMarkup]; this class only owns the
 * compact editor button/dialog and applies the chosen transformation to the
 * active EditText. Keeping this out of ReaderActivity protects the legacy
 * Activity from growing with provider-specific editor UI.
 */
object GrokEditorToolbar {
    fun create(
        activity: Activity,
        editor: EditText,
        setResultText: (String) -> Unit,
        pauseIfPlaying: () -> Unit,
        scrollToOffset: (Int) -> Unit
    ): Button {
        val button = Button(activity).apply {
            text =
                tr(
                    activity,
                    "Grok: ударение / подача ▾",
                    "Grok: akcent / sposób czytania ▾",
                    "Grok: stress / delivery ▾"
                )
            textSize = 14f
            visibility = View.GONE
        }
        KapijujaUiTheme.button(activity, button, primary = true)
        button.setOnClickListener {
            showActions(
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
        button: Button,
        editMode: Boolean,
        engine: String
    ) {
        button.visibility =
            if (editMode && engine == SettingsStore.ENGINE_XAI) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun showActions(
        activity: Activity,
        editor: EditText,
        setResultText: (String) -> Unit,
        pauseIfPlaying: () -> Unit,
        scrollToOffset: (Int) -> Unit
    ) {
        val actions =
            GrokEditorMarkup.actions
                .filter { it.kind != GrokEditorMarkup.Kind.SECTION }
        val labels =
            actions
                .map { GrokEditorMarkup.label(it, UiText.language(activity)) }
                .toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(
                tr(
                    activity,
                    "Grok — произношение и подача",
                    "Grok — wymowa i sposób czytania",
                    "Grok — pronunciation and delivery"
                )
            )
            .setItems(labels) { _, which ->
                applyAction(
                    activity = activity,
                    editor = editor,
                    setResultText = setResultText,
                    action = actions[which],
                    pauseIfPlaying = pauseIfPlaying,
                    scrollToOffset = scrollToOffset
                )
            }
            .setNegativeButton(
                tr(activity, "Отмена", "Anuluj", "Cancel"),
                null
            )
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
                            "Ударение поставлено. Нажмите «Слушать от курсора» для проверки.",
                            "Akcent został ustawiony. Naciśnij „Czytaj od kursora”, aby sprawdzić.",
                            "Stress mark added. Tap “Listen from cursor” to check it."
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
