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

object LibraryStore {
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
        current.add(0, LibraryItem(id, title.ifBlank { "Без названия" }, source, System.currentTimeMillis()))
        writeIndex(context, current)
        return id
    }

    fun text(context: Context, id: String): String =
        File(dir(context), "$id.txt").takeIf { it.exists() }?.readText().orEmpty()

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
