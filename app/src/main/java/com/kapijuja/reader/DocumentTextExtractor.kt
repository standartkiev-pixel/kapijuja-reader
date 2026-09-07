package com.kapijuja.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

data class ExtractedDocument(val title: String, val text: String)

object DocumentTextExtractor {
    fun fileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && c.moveToFirst()) return c.getString(index)
        }
        return "Документ"
    }

    fun extract(context: Context, uri: Uri): ExtractedDocument {
        val name = fileName(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось открыть файл")
        val lower = name.lowercase()

        val text = when {
            lower.endsWith(".docx") -> extractDocx(bytes)
            lower.endsWith(".html") || lower.endsWith(".htm") -> {
                HtmlExtractor.extract(bytes.toString(Charsets.UTF_8), name).text
            }
            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv") ||
                lower.endsWith(".json") || lower.endsWith(".xml") -> bytes.toString(Charsets.UTF_8)
            lower.endsWith(".pdf") -> error("PDF будет подключён на следующем шаге")
            lower.endsWith(".doc") -> error("Старый DOC будет подключён на следующем шаге")
            else -> {
                val candidate = bytes.toString(Charsets.UTF_8)
                if (candidate.count { it == '\u0000' } > 3) error("Пока поддерживаются TXT/MD/HTML/DOCX")
                candidate
            }
        }

        return ExtractedDocument(name.substringBeforeLast('.'), text.trim())
    }

    private fun extractDocx(bytes: ByteArray): String {
        var xml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    xml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
            }
        }
        val docXml = xml ?: error("В DOCX не найден текст")
        val withBreaks = docXml
            .replace(Regex("(?i)<w:tab[^>]*/>"), "\t")
            .replace(Regex("(?i)</w:p>"), "\n")
            .replace(Regex("(?i)</w:tr>"), "\n")
        return Html.fromHtml(withBreaks.replace(Regex("<[^>]+>"), ""), Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}
