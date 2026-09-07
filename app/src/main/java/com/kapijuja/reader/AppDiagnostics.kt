package com.kapijuja.reader

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppDiagnostics {
    private const val TAG = "KapijujaReader"
    private const val MAX_BYTES = 96 * 1024

    fun info(context: Context?, message: String) {
        Log.i(TAG, message)
        context?.let { append(it, "INFO", message) }
    }

    fun error(context: Context?, message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
        context?.let {
            val suffix = error?.let { e -> " | ${e.javaClass.simpleName}: ${e.message}" }.orEmpty()
            append(it, "ERROR", message + suffix)
        }
    }

    fun lastLines(context: Context, count: Int = 16): String {
        val file = file(context)
        if (!file.exists()) return "Диагностический журнал пока пуст."
        return file.readLines().takeLast(count).joinToString("\n")
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    @Synchronized
    private fun append(context: Context, level: String, message: String) {
        try {
            val file = file(context)
            if (file.exists() && file.length() > MAX_BYTES) {
                val tail = file.readText().takeLast(MAX_BYTES / 2)
                file.writeText(tail)
            }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$stamp $level ${message.replace("\n", " ").take(1200)}\n")
        } catch (_: Throwable) {
        }
    }

    private fun file(context: Context) = File(context.filesDir, "kapijuja-reader-diagnostics.log")
}
