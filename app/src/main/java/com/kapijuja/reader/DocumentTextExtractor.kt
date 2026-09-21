package com.kapijuja.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.OldWordFileFormatException
import org.apache.poi.hwpf.extractor.Word6Extractor
import org.apache.poi.hwpf.extractor.WordExtractor
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class ExtractedDocument(
    val title: String,
    val text: String
)

object DocumentTextExtractor {
    private enum class Kind {
        PDF,
        DOC,
        DOCX,
        HTML,
        TEXT
    }

    fun fileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && c.moveToFirst()) return c.getString(index)
        }
        return "Документ"
    }

    fun extract(context: Context, uri: Uri): ExtractedDocument {
        val name = fileName(context, uri)
        val kind = detectKind(context, uri, name)
        val reportedSize = declaredSize(context, uri)
        val sourceLimit =
            if (kind == Kind.PDF) {
                ReaderLimits.MAX_PDF_SOURCE_BYTES
            } else {
                ReaderLimits.MAX_SOURCE_BYTES
            }

        require(reportedSize == null || reportedSize <= sourceLimit) {
            "Файл слишком большой: ${reportedSize ?: 0L} байт. Максимум $sourceLimit байт."
        }

        val rawText =
            when (kind) {
                Kind.PDF -> extractPdf(context, uri)
                Kind.DOC -> extractLegacyWord(context, uri)
                Kind.DOCX -> {
                    val bytes = readUriLimited(context, uri, ReaderLimits.MAX_SOURCE_BYTES)
                    extractDocx(bytes)
                }
                Kind.HTML -> {
                    val bytes = readUriLimited(context, uri, ReaderLimits.MAX_SOURCE_BYTES)
                    HtmlExtractor.extract(decodeText(bytes), name).text
                }
                Kind.TEXT -> {
                    val bytes = readUriLimited(context, uri, ReaderLimits.MAX_SOURCE_BYTES)
                    decodeText(bytes)
                }
            }

        val text =
            ImportedTextNormalizer
                .normalize(
                    raw = rawText,
                    mergeVisualLines = kind == Kind.PDF
                )
                .trim()

        require(text.isNotBlank()) {
            if (kind == Kind.PDF) {
                "В PDF не найден текстовый слой. Возможно, это скан или изображение без встроенного текста."
            } else {
                "В документе не найден читаемый текст."
            }
        }

        ReaderLimits.requireDisplaySafe(text)
        return ExtractedDocument(
            title = name.substringBeforeLast('.').ifBlank { name },
            text = text
        )
    }

    private fun detectKind(
        context: Context,
        uri: Uri,
        name: String
    ): Kind {
        val lower = name.lowercase()
        when {
            lower.endsWith(".pdf") -> return Kind.PDF
            lower.endsWith(".docx") -> return Kind.DOCX
            lower.endsWith(".doc") -> return Kind.DOC
            lower.endsWith(".html") || lower.endsWith(".htm") -> return Kind.HTML
            lower.endsWith(".txt") ||
                lower.endsWith(".md") ||
                lower.endsWith(".csv") ||
                lower.endsWith(".json") ||
                lower.endsWith(".xml") ||
                lower.endsWith(".log") -> return Kind.TEXT
        }

        when (context.contentResolver.getType(uri)?.lowercase()) {
            "application/pdf" -> return Kind.PDF
            "application/msword" -> return Kind.DOC
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                return Kind.DOCX
            "text/html" -> return Kind.HTML
            "text/plain", "text/markdown", "text/csv", "application/json", "application/xml",
            "text/xml" -> return Kind.TEXT
        }

        val header = ByteArray(8)
        val count =
            context.contentResolver.openInputStream(uri)?.use {
                it.read(header)
            } ?: 0

        if (count >= 5 && header.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-") {
            return Kind.PDF
        }

        if (
            count >= 8 &&
            header[0] == 0xD0.toByte() &&
            header[1] == 0xCF.toByte() &&
            header[2] == 0x11.toByte() &&
            header[3] == 0xE0.toByte() &&
            header[4] == 0xA1.toByte() &&
            header[5] == 0xB1.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0xE1.toByte()
        ) {
            return Kind.DOC
        }

        if (
            count >= 4 &&
            header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte()
        ) {
            return Kind.DOCX
        }

        return Kind.TEXT
    }

    private fun extractPdf(
        context: Context,
        uri: Uri
    ): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val temp =
            File.createTempFile(
                "kapijuja-import-",
                ".pdf",
                context.cacheDir
            )

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().buffered().use { output ->
                    copyLimited(
                        input = input,
                        output = output,
                        maxBytes = ReaderLimits.MAX_PDF_SOURCE_BYTES,
                        label = "PDF"
                    )
                }
            } ?: error("Не удалось открыть PDF")

            PDDocument.load(
                temp,
                MemoryUsageSetting.setupTempFileOnly()
            ).use { document ->
                require(document.numberOfPages > 0) {
                    "PDF не содержит страниц."
                }

                val stripper =
                    PDFTextStripper().apply {
                        setSortByPosition(true)
                        setLineSeparator("\n")
                        setParagraphStart("")
                        setParagraphEnd("\n\n")
                        setPageStart("")
                        setPageEnd("")
                    }

                val out = StringBuilder()
                for (page in 1..document.numberOfPages) {
                    stripper.startPage = page
                    stripper.endPage = page

                    val pageText = stripper.getText(document)
                    if (pageText.isNotBlank()) {
                        if (out.isNotEmpty()) out.append("\n\n")
                        out.append(pageText.trim())
                    }

                    require(out.length <= ReaderLimits.MAX_DOCUMENT_CHARS) {
                        "PDF содержит слишком много текста для безопасного редактирования: более ${ReaderLimits.MAX_DOCUMENT_CHARS} символов."
                    }
                }
                return out.toString()
            }
        } finally {
            temp.delete()
        }
    }

    private fun extractLegacyWord(
        context: Context,
        uri: Uri
    ): String {
        val bytes =
            readUriLimited(
                context,
                uri,
                ReaderLimits.MAX_SOURCE_BYTES
            )

        return try {
            WordExtractor(ByteArrayInputStream(bytes)).use { extractor ->
                extractor.paragraphText
                    .asSequence()
                    .map { cleanWordParagraph(it) }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
            }
        } catch (_: OldWordFileFormatException) {
            Word6Extractor(ByteArrayInputStream(bytes)).use { extractor ->
                @Suppress("DEPRECATION")
                extractor.paragraphText
                    .asSequence()
                    .map { cleanWordParagraph(it) }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
            }
        } catch (error: Throwable) {
            throw IllegalArgumentException(
                "Не удалось прочитать старый Word DOC: ${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    private fun cleanWordParagraph(value: String): String =
        value
            .replace('\u0007', ' ')
            .replace('\u000B', '\n')
            .replace('\r', '\n')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n+"), "\n")
            .trim()

    private fun extractDocx(bytes: ByteArray): String {
        var documentXml: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    documentXml =
                        readLimited(
                            zip,
                            ReaderLimits.MAX_DOCX_XML_BYTES,
                            "Распакованный DOCX document.xml"
                        )
                    break
                }
            }
        }

        val xml =
            documentXml
                ?: error("В DOCX не найден word/document.xml")

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(xml), "UTF-8")
        }

        val out = StringBuilder()
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "t" -> {
                            val value = parser.nextText()
                            out.append(value)
                        }
                        "tab" -> out.append('\t')
                        "br", "cr" -> out.append('\n')
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "p" -> appendParagraphBreak(out)
                        "tc" -> {
                            if (out.isNotEmpty() && !out.endsWithWhitespace()) {
                                out.append('\t')
                            }
                        }
                        "tr" -> appendParagraphBreak(out)
                    }
                }
            }

            require(out.length <= ReaderLimits.MAX_DOCUMENT_CHARS + 16_384) {
                "DOCX содержит слишком много текста для безопасного редактирования."
            }
            event = parser.next()
        }

        return out.toString()
    }

    private fun appendParagraphBreak(out: StringBuilder) {
        while (out.endsWith(' ') || out.endsWith('\t')) {
            out.setLength(out.length - 1)
        }
        if (out.isEmpty()) return
        if (!out.endsWith("\n\n")) {
            if (!out.endsWith("\n")) out.append('\n')
            out.append('\n')
        }
    }

    private fun StringBuilder.endsWithWhitespace(): Boolean =
        isNotEmpty() && this[length - 1].isWhitespace()

    private fun StringBuilder.endsWith(char: Char): Boolean =
        isNotEmpty() && this[length - 1] == char

    private fun StringBuilder.endsWith(value: String): Boolean {
        if (length < value.length) return false
        for (i in value.indices) {
            if (this[length - value.length + i] != value[i]) return false
        }
        return true
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }

        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return bytes.copyOfRange(2, bytes.size)
                .toString(Charsets.UTF_16LE)
        }

        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return bytes.copyOfRange(2, bytes.size)
                .toString(Charsets.UTF_16BE)
        }

        detectBomlessUtf16(bytes)?.let { charset ->
            return bytes.toString(charset)
        }

        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
        }

        val windows1251 = java.nio.charset.Charset.forName("windows-1251")
        return bytes.toString(windows1251)
    }

    private fun detectBomlessUtf16(
        bytes: ByteArray
    ): java.nio.charset.Charset? {
        val sample = minOf(bytes.size, 1024)
        if (sample < 8) return null

        var evenZeros = 0
        var oddZeros = 0
        var evenCount = 0
        var oddCount = 0

        for (i in 0 until sample) {
            if (i % 2 == 0) {
                evenCount++
                if (bytes[i] == 0.toByte()) evenZeros++
            } else {
                oddCount++
                if (bytes[i] == 0.toByte()) oddZeros++
            }
        }

        val evenRatio = evenZeros.toDouble() / evenCount.coerceAtLeast(1)
        val oddRatio = oddZeros.toDouble() / oddCount.coerceAtLeast(1)

        return when {
            oddRatio > 0.35 && evenRatio < 0.10 -> Charsets.UTF_16LE
            evenRatio > 0.35 && oddRatio < 0.10 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun declaredSize(
        context: Context,
        uri: Uri
    ): Long? {
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

    private fun readUriLimited(
        context: Context,
        uri: Uri,
        maxBytes: Long
    ): ByteArray =
        context.contentResolver.openInputStream(uri)?.use {
            readLimited(it, maxBytes, "Файл")
        } ?: error("Не удалось открыть файл")

    private fun readLimited(
        input: InputStream,
        maxBytes: Long,
        label: String
    ): ByteArray {
        val output = ByteArrayOutputStream()
        copyLimited(
            input = input,
            output = output,
            maxBytes = maxBytes,
            label = label
        )
        return output.toByteArray()
    }

    private fun copyLimited(
        input: InputStream,
        output: java.io.OutputStream,
        maxBytes: Long,
        label: String
    ) {
        val buffer = ByteArray(64 * 1024)
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
    }
}

object ImportedTextNormalizer {
    fun normalize(
        raw: String,
        mergeVisualLines: Boolean
    ): String {
        val clean =
            raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace("\u0000", "")

        return if (mergeVisualLines) {
            mergePdfLikeLines(clean)
        } else {
            preserveParagraphs(clean)
        }
    }

    private fun preserveParagraphs(value: String): String {
        val out = StringBuilder()
        var blankPending = false

        value.lineSequence().forEach { sourceLine ->
            val line =
                sourceLine
                    .replace(Regex("[ \\t]+"), " ")
                    .trim()

            if (line.isBlank()) {
                blankPending = out.isNotEmpty()
            } else {
                if (out.isNotEmpty()) {
                    if (blankPending) out.append("\n\n") else out.append('\n')
                }
                out.append(line)
                blankPending = false
            }
        }
        return out.toString()
    }

    private fun mergePdfLikeLines(value: String): String {
        val out = StringBuilder()
        var paragraph = StringBuilder()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            if (text.isNotBlank()) {
                if (out.isNotEmpty()) out.append("\n\n")
                out.append(text)
            }
            paragraph = StringBuilder()
        }

        value.lineSequence().forEach { sourceLine ->
            val line =
                sourceLine
                    .replace(Regex("[ \\t]+"), " ")
                    .trim()

            if (line.isBlank()) {
                flushParagraph()
                return@forEach
            }

            if (isStandaloneLine(line)) {
                flushParagraph()
                paragraph.append(line)
                flushParagraph()
                return@forEach
            }

            if (paragraph.isEmpty()) {
                paragraph.append(line)
            } else if (
                paragraph.last() == '-' &&
                line.firstOrNull()?.isLowerCase() == true
            ) {
                paragraph.setLength(paragraph.length - 1)
                paragraph.append(line)
            } else {
                paragraph.append(' ')
                paragraph.append(line)
            }
        }

        flushParagraph()
        return out.toString()
    }

    private fun isStandaloneLine(line: String): Boolean {
        if ('\t' in line) return true

        return line.matches(
            Regex(
                """^(?:[•◦▪‣●○◆◇■□*]|[-–—]|\d{1,4}[.)]|[A-Za-zА-Яа-яЁёІіЇїЄєҐґ][.)])\s+.+"""
            )
        )
    }
}
