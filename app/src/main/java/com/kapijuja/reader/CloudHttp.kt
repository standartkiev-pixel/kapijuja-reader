package com.kapijuja.reader

import android.content.Context
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Small shared HTTP helper for per-character cloud TTS providers.
 *
 * DNS/connect failures are retried because no request reached the provider.
 * A read timeout is never retried automatically: the provider may already
 * have generated (and billed) the audio.
 */
internal object CloudHttp {
    class Response(val code: Int, val body: ByteArray) {
        val ok: Boolean get() = code in 200..299
        fun text(): String = String(body, Charsets.UTF_8)
    }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        jsonBody: String? = null,
        readTimeoutMs: Int = 120_000
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = readTimeoutMs
            headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use {
                    it.write(jsonBody.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val stream =
                if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            return Response(code, body)
        } finally {
            connection.disconnect()
        }
    }

    fun <T> withConnectionRetry(
        context: Context?,
        provider: String,
        block: () -> T
    ): T {
        var last: Throwable? = null
        for (attempt in 1..3) {
            try {
                return block()
            } catch (error: UnknownHostException) {
                last = error
                AppDiagnostics.error(context, "$provider DNS attempt $attempt/3", error)
                if (attempt < 3) Thread.sleep(650L * attempt)
            } catch (error: ConnectException) {
                last = error
                AppDiagnostics.error(context, "$provider connect attempt $attempt/3", error)
                if (attempt < 3) Thread.sleep(650L * attempt)
            } catch (error: SocketTimeoutException) {
                AppDiagnostics.error(context, "$provider timeout", error)
                throw IllegalStateException(
                    "$provider: сервер не ответил вовремя. Повторите запрос вручную; автоматический повтор отключён, чтобы исключить двойную оплату.",
                    error
                )
            }
        }

        throw IllegalStateException(
            "$provider: не удалось соединиться с сервером после 3 попыток. Проверьте интернет/DNS и попробуйте ещё раз.",
            last
        )
    }

    /** Joins MP3 parts of one logical fragment into a single playable file. */
    fun joinMp3(parts: List<ByteArray>): ByteArray {
        if (parts.size == 1) return parts[0]
        val output = java.io.ByteArrayOutputStream()
        parts.forEachIndexed { index, bytes ->
            output.write(if (index == 0) bytes else OpenAiTtsClient.stripLeadingId3(bytes))
        }
        return output.toByteArray()
    }
}
