package com.kapijuja.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
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

    private fun declaredSize(context: Context, uri: Uri): Long? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { c ->
            val index = c.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && c.moveToFirst() && !c.isNull(index)) {
                return c.getLong(index).takeIf { it >= 0L }
            }
        }
        return null
    }

    fun extract(context: Context, uri: Uri): ExtractedDocument {
        val name = fileName(context, uri)
        val reportedSize = declaredSize(context, uri)
        require(reportedSize == null || reportedSize <= ReaderLimits.MAX_SOURCE_BYTES) {
            "Файл слишком большой: ${reportedSize ?: 0L} байт. Максимум ${ReaderLimits.MAX_SOURCE_BYTES} байт."
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use {
            readLimited(it, ReaderLimits.MAX_SOURCE_BYTES, "Файл")
        } ?: error("Не удалось открыть файл")
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
                if (candidate.count { it == '\u0000' } > 3) {
                    error("Пока поддерживаются TXT/MD/HTML/DOCX")
                }
                candidate
            }
        }.trim()

        ReaderLimits.requireDisplaySafe(text)
        return ExtractedDocument(name.substringBeforeLast('.'), text)
    }

    private fun extractDocx(bytes: ByteArray): String {
        var xml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val documentBytes =
                        readLimited(
                            zip,
                            ReaderLimits.MAX_DOCX_XML_BYTES,
                            "Распакованный DOCX document.xml"
                        )
                    xml = documentBytes.toString(Charsets.UTF_8)
                    break
                }
            }
        }
        val docXml = xml ?: error("В DOCX не найден текст")
        val withBreaks = docXml
            .replace(Regex("(?i)<w:tab[^>]*/>"), "\t")
            .replace(Regex("(?i)</w:p>"), "\n")
            .replace(Regex("(?i)</w:tr>"), "\n")
        return Html.fromHtml(
            withBreaks.replace(Regex("<[^>]+>"), ""),
            Html.FROM_HTML_MODE_LEGACY
        )
            .toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun readLimited(input: InputStream, maxBytes: Long, label: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) {
                "$label слишком большой для безопасной обработки. Максимум $maxBytes байт."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
