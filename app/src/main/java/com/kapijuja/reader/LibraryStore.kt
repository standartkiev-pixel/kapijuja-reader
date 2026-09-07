package com.kapijuja.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LibraryItem(
    val id: String,
    val title: String,
    val source: String,
    val createdAt: Long
)

data class LibraryPruneResult(
    val removedItems: Int,
    val removedBytes: Long
)

object LibraryStore {
    // Even when the user selects "all", the reader is not intended to become
    // an unlimited document archive. Keep a hard storage safety ceiling.
    const val HARD_MAX_BYTES = 1024L * 1024L * 1024L

    private fun dir(context: Context): File =
        File(context.filesDir, "library").apply { mkdirs() }

    private fun indexFile(context: Context): File =
        File(dir(context), "index.json")

    fun list(context: Context): List<LibraryItem> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        LibraryItem(
                            id = o.getString("id"),
                            title = o.optString("title", "Без названия"),
                            source = o.optString("source", ""),
                            createdAt = o.optLong("createdAt", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun add(context: Context, title: String, source: String, text: String): String {
        val id = UUID.randomUUID().toString()
        File(dir(context), "$id.txt").writeText(text)
        val current = list(context).toMutableList()
        current.removeAll { it.id == id }
        current.add(
            0,
            LibraryItem(
                id,
                title.ifBlank { "Без названия" },
                source,
                System.currentTimeMillis()
            )
        )
        writeIndex(context, current)
        enforceLimits(context)
        return id
    }

    fun update(context: Context, id: String, title: String, source: String, text: String) {
        File(dir(context), "$id.txt").writeText(text)
        val current = list(context).toMutableList()
        val old = current.firstOrNull { it.id == id }
        current.removeAll { it.id == id }
        current.add(
            0,
            LibraryItem(
                id = id,
                title = title.ifBlank { old?.title ?: "Без названия" },
                source = source.ifBlank { old?.source.orEmpty() },
                createdAt = System.currentTimeMillis()
            )
        )
        writeIndex(context, current)
        enforceLimits(context)
    }

    fun delete(context: Context, id: String): Boolean {
        val current = list(context).toMutableList()
        val existed = current.removeAll { it.id == id }
        File(dir(context), "$id.txt").delete()
        if (existed) writeIndex(context, current)
        return existed
    }

    fun enforceLimits(context: Context): LibraryPruneResult {
        val maxItems = SettingsStore.libraryLimit(context)
        val items = list(context).toMutableList()
        if (items.isEmpty()) return LibraryPruneResult(0, 0L)

        var totalBytes =
            items.sumOf { item ->
                File(dir(context), "${item.id}.txt").takeIf { it.exists() }?.length() ?: 0L
            }

        var removed = 0
        var removedBytes = 0L

        // list() is newest first; remove only from the oldest end.
        while (
            items.isNotEmpty() &&
            (
                (maxItems > 0 && items.size > maxItems) ||
                    totalBytes > HARD_MAX_BYTES
                )
        ) {
            val oldest = items.removeAt(items.lastIndex)
            val file = File(dir(context), "${oldest.id}.txt")
            val bytes = if (file.exists()) file.length() else 0L
            file.delete()
            totalBytes = (totalBytes - bytes).coerceAtLeast(0L)
            removed += 1
            removedBytes += bytes
        }

        if (removed > 0) writeIndex(context, items)
        return LibraryPruneResult(removed, removedBytes)
    }

    fun totalBytes(context: Context): Long =
        list(context).sumOf { item ->
            File(dir(context), "${item.id}.txt").takeIf { it.exists() }?.length() ?: 0L
        }

    fun text(context: Context, id: String): String =
        File(dir(context), "$id.txt").takeIf { it.exists() }?.readText().orEmpty()

    fun excerpt(context: Context, id: String, maxChars: Int = 150): String {
        val file = File(dir(context), "$id.txt")
        if (!file.exists()) return ""
        return try {
            file.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(512)
                while (out.length < maxChars) {
                    val count = reader.read(
                        buffer,
                        0,
                        minOf(buffer.size, maxChars - out.length)
                    )
                    if (count <= 0) break
                    out.append(buffer, 0, count)
                }
                out.toString()
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .trim()
                    .take(maxChars)
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun item(context: Context, id: String): LibraryItem? =
        list(context).firstOrNull { it.id == id }

    private fun writeIndex(context: Context, items: List<LibraryItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("source", it.source)
                    .put("createdAt", it.createdAt)
            )
        }
        indexFile(context).writeText(array.toString())
    }
}
