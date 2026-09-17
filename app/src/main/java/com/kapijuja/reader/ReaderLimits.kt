package com.kapijuja.reader

/** Safety ceilings for text shown in Android TextView/EditText and library metadata. */
object ReaderLimits {
    // Large enough for a very long book, but prevents accidental multi-million
    // line/log dumps from asking Android's text layout to allocate without bound.
    const val WARN_DOCUMENT_CHARS = 750_000
    const val MAX_DOCUMENT_CHARS = 2_000_000

    // The actual document bodies live in separate .txt files. The JSON index is
    // metadata only, but still needs a hard count ceiling when "all" is selected.
    const val HARD_MAX_LIBRARY_ITEMS = 5_000
    const val HARD_MAX_LIBRARY_INDEX_BYTES = 8L * 1024L * 1024L

    fun requireDisplaySafe(text: String) {
        require(text.length <= MAX_DOCUMENT_CHARS) {
            "Документ слишком большой для безопасного редактирования: ${text.length} символов. Максимум $MAX_DOCUMENT_CHARS. Разделите файл на несколько частей."
        }
    }
}
