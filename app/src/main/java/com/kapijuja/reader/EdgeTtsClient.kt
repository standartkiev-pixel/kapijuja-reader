package com.kapijuja.reader

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

object EdgeTtsClient {
    private const val BASE =
        "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    private const val TRUSTED_CLIENT_TOKEN =
        "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
    private const val WIN_EPOCH = 11644473600L

    @Volatile
    private var clockSkewSeconds = 0.0

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun synthesize(
        text: String,
        voice: String,
        speed: Float = 1.0f,
        context: Context? = null
    ): ByteArray {
        require(text.isNotBlank()) { "Пустой текст" }
        require(voice.isNotBlank()) { "Голос Microsoft Edge не выбран" }

        var last: Throwable? = null
        for (attempt in 1..2) {
            try {
                AppDiagnostics.info(
                    context,
                    "Edge TTS request: voice=$voice chars=${text.length} speed=$speed attempt=$attempt"
                )
                return synthesizeOnce(text.take(3800), voice, speed, context)
            } catch (e: EdgeForbiddenException) {
                last = e
                if (attempt == 1 && e.serverDate != null && adjustClockSkew(e.serverDate)) {
                    AppDiagnostics.info(
                        context,
                        "Edge 403: adjusted clock skew to ${String.format(Locale.US, "%.2f", clockSkewSeconds)}s and retrying"
                    )
                    continue
                }
                throw IllegalStateException(
                    "Microsoft Edge TTS отклонил соединение (HTTP 403). Сервис Edge неофициальный и мог изменить протокол.",
                    e
                )
            } catch (t: Throwable) {
                last = t
                break
            }
        }
        throw IllegalStateException(
            "Microsoft Edge TTS: ${last?.message ?: "не удалось получить аудио"}",
            last
        )
    }

    private fun synthesizeOnce(
        text: String,
        voice: String,
        speed: Float,
        context: Context?
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val connectionId = randomHex()
        val url =
            "wss://$BASE/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=${generateSecMsGec()}" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header(
                "Origin",
                "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
            )
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "muid=${randomHex()};")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppDiagnostics.info(context, "Edge WebSocket connected HTTP ${response.code}")
                val config =
                    "X-Timestamp:${edgeTimestamp()}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{" +
                        "\"metadataoptions\":{" +
                        "\"sentenceBoundaryEnabled\":\"true\"," +
                        "\"wordBoundaryEnabled\":\"false\"}," +
                        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"" +
                        "}}}}\r\n"

                val requestId = randomHex()
                val rate = ratePercent(speed)
                val ssml =
                    "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>" +
                        "<voice name='${escapeXml(voice)}'>" +
                        "<prosody pitch='+0Hz' rate='$rate' volume='+0%'>" +
                        escapeXml(cleanText(text)) +
                        "</prosody></voice></speak>"

                val speech =
                    "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${edgeTimestamp()}Z\r\n" +
                        "Path:ssml\r\n\r\n" +
                        ssml

                if (!webSocket.send(config) || !webSocket.send(speech)) {
                    failure.compareAndSet(
                        null,
                        IllegalStateException("Microsoft Edge: не удалось отправить запрос")
                    )
                    done.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when {
                    text.contains("Path:turn.end") -> {
                        webSocket.close(1000, "done")
                        done.countDown()
                    }
                    text.contains("Path:audio.metadata") -> Unit
                    text.contains("Path:turn.start") -> Unit
                    text.contains("Path:response") -> Unit
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size < 2) return
                val headerLength =
                    ((data[0].toInt() and 0xff) shl 8) or
                        (data[1].toInt() and 0xff)
                val dataStart = 2 + headerLength
                if (dataStart <= data.size && dataStart < data.size) {
                    synchronized(output) {
                        output.write(data, dataStart, data.size - dataStart)
                    }
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                val error: Throwable =
                    if (response?.code == 403) {
                        EdgeForbiddenException(response.header("Date"), t)
                    } else {
                        IllegalStateException(
                            "Microsoft Edge WebSocket: HTTP ${response?.code ?: "-"} ${t.message ?: ""}",
                            t
                        )
                    }
                failure.compareAndSet(null, error)
                AppDiagnostics.error(context, "Edge WebSocket failure", error)
                done.countDown()
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                done.countDown()
            }
        }

        val socket = client.newWebSocket(request, listener)
        if (!done.await(65, TimeUnit.SECONDS)) {
            socket.cancel()
            throw IllegalStateException("Microsoft Edge TTS: таймаут ожидания аудио")
        }

        failure.get()?.let { throw it }
        val bytes = synchronized(output) { output.toByteArray() }
        if (bytes.size < 256) {
            throw IllegalStateException(
                "Microsoft Edge TTS не вернул аудио (получено ${bytes.size} байт)"
            )
        }
        AppDiagnostics.info(context, "Edge audio received: ${bytes.size} bytes")
        return bytes
    }

    fun splitForApi(text: String, maxChars: Int = 3400): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            var end = minOf(cursor + maxChars, text.length)
            if (end < text.length) {
                val sentence = text.lastIndexOfAny(
                    charArrayOf('.', '!', '?', '\n'),
                    end
                )
                val space = text.lastIndexOf(' ', end)
                end = when {
                    sentence > cursor + maxChars / 2 -> sentence + 1
                    space > cursor + maxChars / 2 -> space + 1
                    else -> end
                }
            }
            result += text.substring(cursor, end).trim()
            cursor = end
        }
        return result.filter { it.isNotBlank() }
    }

    private fun ratePercent(speed: Float): String {
        val percent = ((speed.coerceIn(0.5f, 2.0f) - 1f) * 100f)
            .roundToInt()
            .coerceIn(-50, 100)
        return if (percent >= 0) "+$percent%" else "$percent%"
    }

    private fun cleanText(value: String): String =
        buildString(value.length) {
            value.forEach { ch ->
                val code = ch.code
                append(
                    if ((code in 0..8) || (code in 11..12) || (code in 14..31)) ' '
                    else ch
                )
            }
        }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
            .replace("\"", "&quot;")

    private fun generateSecMsGec(): String {
        var seconds =
            System.currentTimeMillis() / 1000.0 + clockSkewSeconds + WIN_EPOCH
        seconds -= seconds % 300.0
        val ticks = seconds * 10_000_000.0
        val raw =
            String.format(Locale.US, "%.0f", ticks) + TRUSTED_CLIENT_TOKEN
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun adjustClockSkew(serverDate: String): Boolean =
        try {
            val server = ZonedDateTime.parse(
                serverDate,
                DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant().toEpochMilli() / 1000.0
            val clientNow = System.currentTimeMillis() / 1000.0
            clockSkewSeconds += server - clientNow
            true
        } catch (_: Throwable) {
            false
        }

    private fun edgeTimestamp(): String =
        DateTimeFormatter
            .ofPattern(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                Locale.US
            )
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())

    private fun randomHex(): String =
        UUID.randomUUID().toString().replace("-", "")

    private class EdgeForbiddenException(
        val serverDate: String?,
        cause: Throwable?
    ) : Exception("Edge HTTP 403", cause)
}
