package com.kapijuja.reader

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KapijujaUiTheme.applyWindow(this)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::libraryBox.isInitialized) refreshLibrary()
    }

    private fun buildScreen() {
        val frame = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.kapijuja_screen_bg)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(104))
        }

        val title = TextView(this).apply {
            text = "Kapijuja Reader"
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        KapijujaUiTheme.title(title)
        column.addView(title)

        val subtitle = TextView(this).apply {
            text = "Библиотека"
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
                text = "Текстов пока нет.\nНажмите +, чтобы открыть документ, вставить текст или ссылку."
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(70), dp(20), dp(20))
            }
            KapijujaUiTheme.secondary(empty)
            libraryBox.addView(empty)
            return
        }

        items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                background = KapijujaUiTheme.panel(this@MainActivity)
                isClickable = true
                isFocusable = true
                setOnClickListener { openLibraryItem(item.id) }
            }
            val t = TextView(this).apply {
                text = item.title
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            KapijujaUiTheme.title(t)
            card.addView(t)

            if (item.source.isNotBlank()) {
                val s = TextView(this).apply {
                    text = item.source
                    textSize = 13f
                    maxLines = 1
                    setPadding(0, dp(6), 0, 0)
                }
                KapijujaUiTheme.secondary(s)
                card.addView(s)
            }

            val excerpt = LibraryStore.text(this, item.id)
                .replace("\n", " ")
                .trim()
                .take(150)
            val e = TextView(this).apply {
                text = excerpt
                textSize = 15f
                maxLines = 3
                setPadding(0, dp(9), 0, 0)
            }
            KapijujaUiTheme.secondary(e)
            card.addView(e)

            libraryBox.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
        }
    }

    private fun showImportDialog() {
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = KapijujaUiTheme.panel(this@MainActivity, KapijujaUiTheme.PANEL_DARK, KapijujaUiTheme.BLUE, 1, 20)
        }

        val title = TextView(this).apply {
            text = "Добавить текст"
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

        option("Открыть документ") { chooseDocument() }
        option("Ввести текст") { showManualTextDialog() }
        option("Вставить ссылку") { showLinkDialog() }

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
                    openDraft(doc.title, "Документ", doc.text)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    Toast.makeText(this, t.message ?: "Не удалось прочитать документ", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showManualTextDialog() {
        val dialog = Dialog(this)
        val box = dialogBox("Ввести текст")

        val titleInput = EditText(this).apply {
            hint = "Название (необязательно)"
            textSize = 17f
        }
        KapijujaUiTheme.input(this, titleInput)
        box.addView(titleInput, matchWrap(dp(10)))

        val textInput = EditText(this).apply {
            hint = "Вставьте или напишите текст"
            textSize = 18f
            minLines = 9
            gravity = Gravity.TOP
        }
        KapijujaUiTheme.input(this, textInput)
        box.addView(textInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(280)
        ).apply { bottomMargin = dp(14) })

        val open = Button(this).apply {
            text = "Открыть"
            textSize = 18f
            setOnClickListener {
                val text = textInput.text.toString().trim()
                if (text.isBlank()) return@setOnClickListener
                val title = titleInput.text.toString().trim()
                    .ifBlank { text.lineSequence().firstOrNull()?.take(60).orEmpty().ifBlank { "Текст" } }
                dialog.dismiss()
                openDraft(title, "Текст", text)
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
        val box = dialogBox("Вставить ссылку")
        val input = EditText(this).apply {
            hint = "https://..."
            textSize = 17f
            setSingleLine(true)
        }
        KapijujaUiTheme.input(this, input)
        box.addView(input, matchWrap(dp(14)))

        val open = Button(this).apply {
            text = "Загрузить текст"
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
                val fallback = Uri.parse(address).host ?: "Страница"
                val page = HtmlExtractor.extract(html, fallback)
                if (page.text.length < 20) error("На странице не найден читаемый текст")
                runOnUiThread {
                    loading.visibility = View.GONE
                    openDraft(page.title, address, page.text)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    Toast.makeText(this, "Не удалось получить страницу: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun openDraft(title: String, source: String, text: String) {
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

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)

    companion object {
        private const val REQ_DOCUMENT = 901
    }
}
