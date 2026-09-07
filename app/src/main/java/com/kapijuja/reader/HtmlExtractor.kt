package com.kapijuja.reader

import android.text.Html

data class ExtractedPage(val title: String, val text: String)

object HtmlExtractor {
    fun extract(html: String, fallbackTitle: String): ExtractedPage {
        val title = Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(html)?.groupValues?.getOrNull(1)
            ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle

        var body = html
        val removable = listOf("script", "style", "noscript", "svg", "nav", "header", "footer", "aside")
        removable.forEach { tag ->
            body = body.replace(Regex("(?is)<$tag\\b[^>]*>.*?</$tag>"), " ")
        }

        body = body
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|article|section|h[1-6]|li)>"), "\n")

        val plain = Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        return ExtractedPage(title, plain)
    }
}
