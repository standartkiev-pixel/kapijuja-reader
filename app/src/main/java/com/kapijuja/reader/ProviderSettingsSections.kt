package com.kapijuja.reader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Settings blocks extracted from SettingsActivity: "where to get a key / where
 * to pay" links for every provider, the reading-language choice and the
 * ElevenLabs and Inworld credential sections.
 */
object ProviderSettingsSections {
    /** Adds "Get API key" and "Top up balance" buttons that open the provider site. */
    fun addLinks(activity: Activity, root: LinearLayout, engine: String) {
        val links = CloudEngines.links(engine) ?: return
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row.addView(
            linkButton(
                activity,
                t(activity, "🔑 Получить ключ", "🔑 Pobierz klucz", "🔑 Get API key"),
                links.keyUrl
            ),
            LinearLayout.LayoutParams(0, dp(activity, 50), 1f).apply {
                rightMargin = dp(activity, 6)
            }
        )
        row.addView(
            linkButton(
                activity,
                t(activity, "💳 Оплата и баланс", "💳 Płatność i saldo", "💳 Billing & balance"),
                links.billingUrl
            ),
            LinearLayout.LayoutParams(0, dp(activity, 50), 1f)
        )
        root.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(activity, 10) }
        )
    }

    /**
     * Adds the reading-language choice plus the ElevenLabs and Inworld
     * sections. Returns a function that saves the entered keys.
     */
    fun addNewProviders(
        activity: Activity,
        root: LinearLayout,
        onTest: (engine: String) -> Unit
    ): () -> Unit {
        addSpeechLanguage(activity, root)

        val elevenKey =
            addProvider(
                activity = activity,
                root = root,
                engine = SettingsStore.ENGINE_ELEVEN,
                title = "ElevenLabs",
                note = t(
                    activity,
                    "Очень живой голос, русский и украинский. Оплата без подписки: пополняете баланс картой (от \$5) и тратите по мере чтения. После ввода ключа нажмите «Проверить» — загрузится список ваших голосов.",
                    "Bardzo naturalny głos, rosyjski i ukraiński. Płatność bez subskrypcji: doładowujesz saldo kartą (od \$5). Po wpisaniu klucza naciśnij „Sprawdź” — pobierze się lista głosów.",
                    "Very natural voice, Russian and Ukrainian. No subscription needed: top up a balance by card (from \$5). After entering the key press Test to download your voice list."
                ),
                keyHint = "ElevenLabs API key (sk_...)",
                key = SettingsStore.elevenApiKey(activity),
                models = ElevenLabsTtsClient.models,
                currentModel = { SettingsStore.elevenModel(activity) },
                setModel = { SettingsStore.setElevenModel(activity, it) },
                onTest = onTest
            )

        val inworldKey =
            addProvider(
                activity = activity,
                root = root,
                engine = SettingsStore.ENGINE_INWORLD,
                title = "Inworld TTS",
                note = t(
                    activity,
                    "Качественный голос по цене Grok, русский и украинский. Оплата: покупаете кредиты картой (от \$10), есть бесплатные минуты для пробы. В разделе API Keys скопируйте значение «Basic (Base64)» и вставьте сюда. Затем нажмите «Проверить» — загрузится список голосов.",
                    "Dobry głos w cenie Grok, rosyjski i ukraiński. Płatność: kupujesz kredyty kartą (od \$10), są darmowe minuty na próbę. W sekcji API Keys skopiuj wartość „Basic (Base64)” i wklej tutaj. Potem naciśnij „Sprawdź”.",
                    "Good voice at Grok's price, Russian and Ukrainian. Buy credits by card (from \$10); free trial minutes are included. In API Keys copy the \"Basic (Base64)\" value and paste it here, then press Test."
                ),
                keyHint = "Inworld API key — Basic (Base64)",
                key = SettingsStore.inworldApiKey(activity),
                models = InworldTtsClient.models,
                currentModel = { SettingsStore.inworldModel(activity) },
                setModel = { SettingsStore.setInworldModel(activity, it) },
                onTest = onTest
            )

        return {
            SettingsStore.setElevenApiKey(activity, elevenKey.text.toString())
            SettingsStore.setInworldApiKey(activity, inworldKey.text.toString())
        }
    }

    /** Status line under the engine button for the engines owned by this file. */
    fun status(activity: Activity, engine: String): String =
        when (engine) {
            SettingsStore.ENGINE_ELEVEN ->
                t(
                    activity,
                    "ElevenLabs подключён через официальный API. Платный движок: стоимость считается по символам, перед длинным чтением приложение покажет оценку.",
                    "ElevenLabs korzysta z oficjalnego API. Płatny silnik: koszt liczony jest za znaki, przed długim czytaniem aplikacja pokaże szacunek.",
                    "ElevenLabs uses the official API. Paid engine: billed per character; the app shows an estimate before long readings."
                )
            SettingsStore.ENGINE_INWORLD ->
                t(
                    activity,
                    "Inworld TTS подключён через официальный API. Платный движок: стоимость считается по символам, перед длинным чтением приложение покажет оценку.",
                    "Inworld TTS korzysta z oficjalnego API. Płatny silnik: koszt liczony jest za znaki, przed długim czytaniem aplikacja pokaże szacunek.",
                    "Inworld TTS uses the official API. Paid engine: billed per character; the app shows an estimate before long readings."
                )
            else -> ""
        }

    private fun addSpeechLanguage(activity: Activity, root: LinearLayout) {
        root.addView(
            sectionTitle(
                activity,
                t(activity, "Язык текста для озвучки", "Język czytanego tekstu", "Language of the text")
            )
        )

        val choices = listOf(
            SettingsStore.SPEECH_LANGUAGE_AUTO to
                t(activity, "Определять автоматически", "Wykrywaj automatycznie", "Detect automatically"),
            SettingsStore.SPEECH_LANGUAGE_RU to "Русский",
            SettingsStore.SPEECH_LANGUAGE_UK to "Українська"
        )
        fun label(): String =
            choices.firstOrNull { it.first == SettingsStore.speechLanguage(activity) }?.second
                ?: choices[0].second

        val button = Button(activity).apply {
            text = label()
            textSize = 17f
        }
        button.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle(t(activity, "Язык текста", "Język tekstu", "Text language"))
                .setItems(choices.map { it.second }.toTypedArray()) { _, which ->
                    SettingsStore.setSpeechLanguage(activity, choices[which].first)
                    button.text = label()
                }
                .show()
        }
        KapijujaUiTheme.button(activity, button)
        root.addView(button, fullButton(activity))

        root.addView(
            note(
                activity,
                t(
                    activity,
                    "Подсказка для ElevenLabs, Inworld и Grok, на каком языке текст. Для украинских книг выберите «Українська». В Edge и Azure язык задаётся выбором голоса (Ostap, Polina — украинские).",
                    "Wskazówka dla ElevenLabs, Inworld i Grok, w jakim języku jest tekst. W Edge i Azure język wynika z wybranego głosu (Ostap, Polina — ukraińskie).",
                    "Tells ElevenLabs, Inworld and Grok which language the text is in. In Edge and Azure the language follows the chosen voice (Ostap, Polina are Ukrainian)."
                )
            )
        )
    }

    private fun addProvider(
        activity: Activity,
        root: LinearLayout,
        engine: String,
        title: String,
        note: String,
        keyHint: String,
        key: String,
        models: List<VoiceChoice>,
        currentModel: () -> String,
        setModel: (String) -> Unit,
        onTest: (String) -> Unit
    ): EditText {
        root.addView(sectionTitle(activity, title))
        root.addView(note(activity, note))

        val keyInput = EditText(activity).apply {
            hint = keyHint
            textSize = 15f
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(key)
        }
        KapijujaUiTheme.input(activity, keyInput)
        root.addView(
            keyInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(activity, 10) }
        )

        addLinks(activity, root, engine)

        fun modelLabel(): String {
            val id = currentModel()
            val name = models.firstOrNull { it.id == id }?.label ?: id
            val hourUsd =
                CloudEngines.priceUsdPerMillionChars(activity, engine) * 60_000 / 1_000_000.0
            return t(activity, "Модель: ", "Model: ", "Model: ") + name +
                String.format(Locale.US, " (≈ \$%.1f/", hourUsd) +
                t(activity, "час)", "godz.)", "hour)")
        }

        val modelButton = Button(activity).apply {
            text = modelLabel()
            textSize = 14f
        }
        modelButton.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle(t(activity, "Модель $title", "Model $title", "$title model"))
                .setItems(models.map { it.label }.toTypedArray()) { _, which ->
                    setModel(models[which].id)
                    modelButton.text = modelLabel()
                }
                .show()
        }
        KapijujaUiTheme.button(activity, modelButton)
        root.addView(modelButton, fullButton(activity))

        val testButton = Button(activity).apply {
            text = t(
                activity,
                "Проверить $title и загрузить голоса",
                "Sprawdź $title i pobierz głosy",
                "Test $title and load voices"
            )
            textSize = 15f
            setOnClickListener { onTest(engine) }
        }
        KapijujaUiTheme.button(activity, testButton)
        root.addView(testButton, fullButton(activity))

        return keyInput
    }

    private fun linkButton(activity: Activity, label: String, url: String): Button {
        val button = Button(activity).apply {
            text = label
            textSize = 14f
            setOnClickListener {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (error: Exception) {
                    AppDiagnostics.error(activity, "Could not open link: $url", error)
                    Toast.makeText(activity, url, Toast.LENGTH_LONG).show()
                }
            }
        }
        KapijujaUiTheme.button(activity, button)
        return button
    }

    private fun sectionTitle(activity: Activity, value: String) =
        TextView(activity).apply {
            text = value
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(activity, 4), dp(activity, 18), dp(activity, 4), dp(activity, 8))
            KapijujaUiTheme.title(this)
        }

    private fun note(activity: Activity, value: String) =
        TextView(activity).apply {
            text = value
            textSize = 14f
            setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 8))
            KapijujaUiTheme.secondary(this)
        }

    private fun fullButton(activity: Activity) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(activity, 58)
        ).apply { bottomMargin = dp(activity, 6) }

    private fun t(activity: Activity, ru: String, pl: String, en: String): String =
        UiText.get(activity, ru, pl, en)

    private fun dp(activity: Activity, value: Int) = KapijujaUiTheme.dp(activity, value)
}
