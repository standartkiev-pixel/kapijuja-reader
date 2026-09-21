package com.kapijuja.reader

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var libraryBox: LinearLayout
    private lateinit var loading: ProgressBar
    private var libraryVisibleCount = LIBRARY_PAGE_SIZE
    private var builtLanguage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KapijujaUiTheme.applyWindow(this)
        builtLanguage = UiText.language(this)
        buildScreen()
        maybeOfferBackgroundSetup()
    }

    override fun onResume() {
        super.onResume()
        if (builtLanguage.isNotBlank() && builtLanguage != UiText.language(this)) {
            recreate()
            return
        }
        if (::libraryBox.isInitialized) refreshLibrary()
    }

    private fun buildScreen() {
        val frame = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.kapijuja_screen_bg)
        }
        KapijujaUiTheme.applySafeArea(frame)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(104))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Kapijuja Reader"
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        KapijujaUiTheme.title(title)
        topRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val settings = Button(this).apply {
            text = t("⚙ Настройки", "⚙ Settings")
            textSize = 14f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, settings)
        topRow.addView(settings, LinearLayout.LayoutParams(dp(132), dp(50)))

        column.addView(topRow)

        val subtitle = TextView(this).apply {
            text = t("Библиотека", "Library")
            textSize = 17f
            setPadding(0, dp(6), 0, dp(18))
        }
        KapijujaUiTheme.secondary(subtitle)
        column.addView(subtitle)

        loading = ProgressBar(this).apply {
            visibility = View.GONE
        }
        KapijujaUiTheme.progress(loading)
        column.addView(loading, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })

        val scroll = ScrollView(this)
        libraryBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(libraryBox)
        column.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        frame.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val plus = Button(this).apply {
            text = "+"
            textSize = 36f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
            setOnClickListener { showImportDialog() }
        }
        KapijujaUiTheme.button(this, plus, primary = true)
        frame.addView(plus, FrameLayout.LayoutParams(dp(76), dp(76)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(22)
        })

        setContentView(frame)
        refreshLibrary()
    }

    private fun refreshLibrary() {
        libraryBox.removeAllViews()
        val items = LibraryStore.list(this)
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = t(
                    "Текстов пока нет.\nНажмите +, чтобы открыть документ, вставить текст или ссылку.",
                    "Nie ma jeszcze tekstów.\nNaciśnij +, aby otworzyć dokument, wkleić tekst lub link.",
                    "No texts yet.\nTap + to open a document, paste text or add a link."
                )
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(70), dp(20), dp(20))
            }
            KapijujaUiTheme.secondary(empty)
            libraryBox.addView(empty)
            return
        }

        val shown = items.take(libraryVisibleCount)

        shown.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                background = KapijujaUiTheme.panel(this@MainActivity)
                isClickable = true
                isFocusable = true
                setOnClickListener { openLibraryItem(item.id) }
                setOnLongClickListener {
                    confirmDeleteLibraryItem(item)
                    true
                }
            }
            val titleView = TextView(this).apply {
                text = item.title
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            KapijujaUiTheme.title(titleView)
            card.addView(titleView)

            if (item.source.isNotBlank()) {
                val sourceView = TextView(this).apply {
                    text = item.source
                    textSize = 13f
                    maxLines = 1
                    setPadding(0, dp(6), 0, 0)
                }
                KapijujaUiTheme.secondary(sourceView)
                card.addView(sourceView)
            }

            val excerpt = LibraryStore.excerpt(this, item.id)
            val excerptView = TextView(this).apply {
                text = excerpt
                textSize = 15f
                maxLines = 3
                setPadding(0, dp(9), 0, 0)
            }
            KapijujaUiTheme.secondary(excerptView)
            card.addView(excerptView)

            val hint = TextView(this).apply {
                text = t(
                    "Долгое нажатие — удалить",
                    "Przytrzymaj — usuń",
                    "Long press — delete"
                )
                textSize = 11f
                setPadding(0, dp(6), 0, 0)
            }
            KapijujaUiTheme.secondary(hint)
            card.addView(hint)

            libraryBox.addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            )
        }

        if (shown.size < items.size) {
            val remaining = minOf(LIBRARY_PAGE_SIZE, items.size - shown.size)
            val more = Button(this).apply {
                text = t(
                    "Показать ещё $remaining",
                    "Pokaż kolejne $remaining",
                    "Show $remaining more"
                )
                textSize = 16f
                setOnClickListener {
                    libraryVisibleCount += LIBRARY_PAGE_SIZE
                    refreshLibrary()
                }
            }
            KapijujaUiTheme.button(this, more)
            libraryBox.addView(
                more,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(56)
                ).apply { bottomMargin = dp(14) }
            )
        }
    }

    private fun confirmDeleteLibraryItem(item: LibraryItem) {
        AlertDialog.Builder(this)
            .setTitle(
                t(
                    "Удалить текст?",
                    "Usunąć tekst?",
                    "Delete text?"
                )
            )
            .setMessage(item.title)
            .setNegativeButton(
                t("Отмена", "Anuluj", "Cancel"),
                null
            )
            .setPositiveButton(
                t("Удалить", "Usuń", "Delete")
            ) { _, _ ->
                LibraryStore.delete(this, item.id)
                refreshLibrary()
            }
            .show()
    }
    private fun showImportDialog() {
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = KapijujaUiTheme.panel(this@MainActivity, KapijujaUiTheme.PANEL_DARK, KapijujaUiTheme.BLUE, 1, 20)
        }

        val title = TextView(this).apply {
            text = t("Добавить текст", "Add text")
            textSize = 23f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), 0, 0, dp(14))
        }
        KapijujaUiTheme.title(title)
        box.addView(title)

        fun option(label: String, action: () -> Unit) {
            val b = Button(this).apply {
                text = label
                textSize = 18f
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            KapijujaUiTheme.button(this, b)
            box.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66)
            ).apply { bottomMargin = dp(12) })
        }

        option(t("Открыть PDF / Word / TXT", "Otwórz PDF / Word / TXT", "Open PDF / Word / TXT")) { chooseDocument() }
        option(t("Ввести текст", "Enter text")) { showManualTextDialog() }
        option(t("Вставить ссылку", "Paste link")) { showLinkDialog() }

        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.91).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun chooseDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-word.document.macroEnabled.12",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                    "application/vnd.ms-word.template.macroEnabled.12",
                    "text/plain",
                    "text/markdown",
                    "text/html",
                    "text/csv",
                    "application/json",
                    "application/xml",
                    "text/xml"
                )
            )
        }
        startActivityForResult(intent, REQ_DOCUMENT)
    }

    @Deprecated("legacy result is sufficient for the prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_DOCUMENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        loading.visibility = View.VISIBLE
        Thread {
            try {
                val doc = DocumentTextExtractor.extract(this, uri)
                runOnUiThread {
                    loading.visibility = View.GONE
                    openDraft(
                        doc.title,
                        t("Документ", "Dokument", "Document"),
                        doc.text
                    )
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    val message =
                        UiText.localizeMessage(
                            this,
                            t.message ?: t(
                                "Не удалось прочитать документ",
                                "Nie udało się odczytać dokumentu",
                                "Could not read document"
                            )
                        )
                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun showManualTextDialog() {
        val dialog = Dialog(this)
        val box = dialogBox(t("Ввести текст", "Enter text"))

        val titleInput = EditText(this).apply {
            hint = t("Название (необязательно)", "Title (optional)")
            textSize = 17f
        }
        KapijujaUiTheme.input(this, titleInput)
        box.addView(titleInput, matchWrap(dp(10)))

        val textInput = EditText(this).apply {
            hint = t("Вставьте или напишите текст", "Paste or type text")
            textSize = 18f
            minLines = 9
            gravity = Gravity.TOP
        }
        KapijujaUiTheme.input(this, textInput)
        box.addView(textInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(280)
        ).apply { bottomMargin = dp(14) })

        val open = Button(this).apply {
            text = t("Открыть", "Open")
            textSize = 18f
            setOnClickListener {
                val text = textInput.text.toString().trim()
                if (text.isBlank()) return@setOnClickListener
                val title = titleInput.text.toString().trim()
                    .ifBlank {
                        text.lineSequence().firstOrNull()?.take(60).orEmpty()
                            .ifBlank { t("Текст", "Tekst", "Text") }
                    }
                dialog.dismiss()
                openDraft(title, t("Текст", "Tekst", "Text"), text)
            }
        }
        KapijujaUiTheme.button(this, open, primary = true)
        box.addView(open, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
        ))
        showDialog(dialog, box)
    }

    private fun showLinkDialog() {
        val dialog = Dialog(this)
        val box = dialogBox(t("Вставить ссылку", "Paste link"))
        val input = EditText(this).apply {
            hint = "https://..."
            textSize = 17f
            setSingleLine(true)
        }
        KapijujaUiTheme.input(this, input)
        box.addView(input, matchWrap(dp(14)))

        val open = Button(this).apply {
            text = t("Загрузить текст", "Load text")
            textSize = 18f
            setOnClickListener {
                var address = input.text.toString().trim()
                if (address.isBlank()) return@setOnClickListener
                if (!address.startsWith("http://") && !address.startsWith("https://")) {
                    address = "https://$address"
                }
                dialog.dismiss()
                loadUrl(address)
            }
        }
        KapijujaUiTheme.button(this, open, primary = true)
        box.addView(open, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
        ))
        showDialog(dialog, box)
    }

    private fun loadUrl(address: String) {
        loading.visibility = View.VISIBLE
        Thread {
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 18000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 KapijujaReader/0.1")
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val fallback =
                    Uri.parse(address).host
                        ?: t("Страница", "Strona", "Page")
                val page = HtmlExtractor.extract(html, fallback)
                if (page.text.length < 20) {
                    error(
                        t(
                            "На странице не найден читаемый текст",
                            "Na stronie nie znaleziono tekstu do odczytu",
                            "No readable text was found on the page"
                        )
                    )
                }
                runOnUiThread {
                    loading.visibility = View.GONE
                    openDraft(page.title, address, page.text)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    Toast.makeText(
                        this,
                        t(
                            "Не удалось получить страницу: ${t.message}",
                            "Nie udało się pobrać strony: ${t.message}",
                            "Could not load page: ${t.message}"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun openDraft(title: String, source: String, text: String) {
        if (text.length > ReaderLimits.MAX_DOCUMENT_CHARS) {
            AlertDialog.Builder(this)
                .setTitle(t("Файл слишком большой", "Plik jest zbyt duży", "File too large"))
                .setMessage(
                    t(
                        "В тексте ${text.length} символов. Для защиты памяти Android один документ ограничен ${ReaderLimits.MAX_DOCUMENT_CHARS} символами. Разделите файл на несколько частей.",
                        "Tekst ma ${text.length} znaków. Dla ochrony pamięci Android jeden dokument jest ograniczony do ${ReaderLimits.MAX_DOCUMENT_CHARS} znaków. Podziel plik na części.",
                        "The text contains ${text.length} characters. To protect Android memory, one document is limited to ${ReaderLimits.MAX_DOCUMENT_CHARS} characters. Split the file into parts."
                    )
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        if (text.length > ReaderLimits.WARN_DOCUMENT_CHARS) {
            Toast.makeText(
                this,
                t(
                    "Большой документ: быстрый ползунок справа поможет перемещаться по тексту.",
                    "Duży dokument: szybki suwak po prawej ułatwi poruszanie się po tekście.",
                    "Large document: use the fast handle on the right to move through the text."
                ),
                Toast.LENGTH_LONG
            ).show()
        }
        val dir = File(cacheDir, "drafts").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".txt")
        file.writeText(text)
        startActivity(Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_DRAFT_PATH, file.absolutePath)
            putExtra(ReaderActivity.EXTRA_TITLE, title)
            putExtra(ReaderActivity.EXTRA_SOURCE, source)
        })
    }

    private fun openLibraryItem(id: String) {
        startActivity(Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_LIBRARY_ID, id)
        })
    }

    private fun dialogBox(titleText: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = KapijujaUiTheme.panel(this@MainActivity, KapijujaUiTheme.PANEL_DARK, KapijujaUiTheme.BLUE, 1, 20)
            val title = TextView(this@MainActivity).apply {
                text = titleText
                textSize = 23f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), 0, 0, dp(14))
            }
            KapijujaUiTheme.title(title)
            addView(title)
        }
    }

    private fun showDialog(dialog: Dialog, box: View) {
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.93).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun matchWrap(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = bottom }

    private fun maybeOfferBackgroundSetup() {
        val prefs = getSharedPreferences("kapijuja_reader_runtime", MODE_PRIVATE)
        if (prefs.getBoolean("background_setup_offered", false)) return
        prefs.edit().putBoolean("background_setup_offered", true).apply()

        AlertDialog.Builder(this)
            .setTitle(t("Фоновое чтение", "Background reading"))
            .setMessage(
                t(
                    "Чтобы чтение не замораживалось при выключенном экране, разрешите уведомления и снимите ограничение батареи для Kapijuja Reader.",
                    "To keep reading alive with the screen off, allow notifications and remove battery restrictions for Kapijuja Reader."
                )
            )
            .setNegativeButton(t("Позже", "Later"), null)
            .setPositiveButton(t("Настроить", "Set up")) { _, _ ->
                requestBackgroundPermissions()
            }
            .show()
    }

    private fun requestBackgroundPermissions() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS
            )
        } else {
            requestBatteryExemption()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) requestBatteryExemption()
    }

    private fun requestBatteryExemption() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Throwable) {
            startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
    }

    private fun t(ru: String, en: String) = UiText.get(this, ru, en)

    private fun t(ru: String, pl: String, en: String) =
        UiText.get(this, ru, pl, en)

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)

    companion object {
        private const val REQ_DOCUMENT = 901
        private const val REQ_NOTIFICATIONS = 902
        private const val LIBRARY_PAGE_SIZE = 80
    }
}
