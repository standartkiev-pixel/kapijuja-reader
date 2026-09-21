package com.kapijuja.reader

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
