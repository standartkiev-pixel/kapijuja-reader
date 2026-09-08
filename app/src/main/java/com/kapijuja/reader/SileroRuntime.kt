package com.kapijuja.reader

import android.content.Context
import org.json.JSONObject
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.HashMap
import kotlin.math.roundToInt

object SileroRuntime {
    private const val ASSET_DIR = "silero"
    private const val MODEL_ASSET = "$ASSET_DIR/silero-v5_5-tts-jit.pt"
    private const val METADATA_ASSET = "$ASSET_DIR/silero-v5_5-metadata.json"
    private const val MODEL_FILE = "silero-v5_5-tts-jit.pt"
    const val DEFAULT_SAMPLE_RATE = 48_000
    private const val MAX_CHARS = 650

    @Volatile
    private var module: Module? = null

    @Volatile
    private var metadata: Metadata? = null

    private val loadLock = Any()

    data class Metadata(
        val modelId: String,
        val speakers: List<String>,
        val speakerToId: Map<String, Long>,
        val symbolToId: Map<Char, Long>,
        val symbols: String,
        val sampleRates: List<Int>
    )

    fun isBundled(context: Context): Boolean =
        try {
            context.assets.open(METADATA_ASSET).close()
            true
        } catch (_: Throwable) {
            false
        }

    fun voiceChoices(context: Context): List<VoiceChoice> =
        try {
            getMetadata(context).speakers.map { id ->
                VoiceChoice(
                    id = id,
                    label = id.replaceFirstChar {
                        if (it.isLowerCase()) {
                            it.titlecase()
                        } else {
                            it.toString()
                        }
                    }
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }

    fun modelDescription(context: Context): String =
        try {
            val m = getMetadata(context)
            "${m.modelId} • ${m.speakers.size} voices • ${m.sampleRates.joinToString("/") { it.toString() }} Hz"
        } catch (_: Throwable) {
            "Silero v5.5"
        }

    fun warmUp(
        context: Context,
        onReady: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        Thread {
            try {
                ensureLoaded(context.applicationContext)
                android.os.Handler(context.mainLooper).post(onReady)
            } catch (error: Throwable) {
                AppDiagnostics.error(
                    context,
                    "Silero runtime warm-up failed",
                    error
                )
                android.os.Handler(context.mainLooper).post {
                    onError(error)
                }
            }
        }.start()
    }

    fun synthesizeWav(
        context: Context,
        text: String,
        speaker: String,
        speed: Float
    ): ByteArray {
        val pcm =
            synthesizePcm16(
                context = context,
                text = text,
                speaker = speaker,
                speed = speed
            )
        return pcm16ToWav(
            pcm = pcm,
            sampleRate = DEFAULT_SAMPLE_RATE
        )
    }

    fun synthesizePcm16(
        context: Context,
        text: String,
        speaker: String,
        speed: Float
    ): ByteArray {
        val app = context.applicationContext
        val localModule = ensureLoaded(app)
        val meta = getMetadata(app)

        val speakerId =
            meta.speakerToId[speaker]
                ?: error("Silero speaker not found: $speaker")

        val clean = normalizeText(text, meta)
        require(clean.isNotBlank()) {
            "Silero: empty text after normalization"
        }

        val sequenceText =
            buildString(clean.length + 2) {
                append('|')
                append(clean)
                append('~')
            }

        val ids =
            sequenceText.mapNotNull { ch ->
                meta.symbolToId[ch]
            }.toLongArray()

        require(ids.size >= 3) {
            "Silero: text contains no supported symbols"
        }

        val sequence =
            Tensor.fromBlob(
                ids,
                longArrayOf(1L, ids.size.toLong())
            )

        val speakerTensor =
            Tensor.fromBlob(
                longArrayOf(speakerId),
                longArrayOf(1L)
            )

        val rate =
            speed.coerceIn(0.65f, 1.45f)

        val rates =
            Tensor.fromBlob(
                FloatArray(ids.size) { rate },
                longArrayOf(1L, ids.size.toLong())
            )

        val pitches =
            Tensor.fromBlob(
                FloatArray(ids.size) { 1f },
                longArrayOf(1L, ids.size.toLong())
            )

        val emptyDurations =
            IValue.dictLongKeyFrom(
                HashMap<Long, IValue>()
            )

        AppDiagnostics.info(
            app,
            "Silero inference: speaker=$speaker speakerId=$speakerId chars=${clean.length} tokens=${ids.size} speed=$rate"
        )

        val result =
            synchronized(localModule) {
                localModule.forward(
                    IValue.from(sequence),
                    IValue.from(speakerTensor),
                    IValue.from(DEFAULT_SAMPLE_RATE.toLong()),
                    emptyDurations,
                    IValue.from(rates),
                    IValue.from(pitches)
                )
            }

        val tuple = result.toTuple()
        require(tuple.isNotEmpty()) {
            "Silero returned empty tuple"
        }

        val audioTensor = tuple[0].toTensor()
        val floats = audioTensor.getDataAsFloatArray()
        require(floats.isNotEmpty()) {
            "Silero returned empty audio"
        }

        val pcm = ByteArray(floats.size * 2)
        var offset = 0
        floats.forEach { sample ->
            val value =
                (sample.coerceIn(-1f, 1f) * 32767f)
                    .roundToInt()
                    .coerceIn(-32768, 32767)
            pcm[offset++] = (value and 0xff).toByte()
            pcm[offset++] = ((value shr 8) and 0xff).toByte()
        }

        AppDiagnostics.info(
            app,
            "Silero audio ready: speaker=$speaker samples=${floats.size} bytes=${pcm.size}"
        )

        return pcm
    }

    fun splitForInference(
        value: String,
        maxChars: Int = MAX_CHARS
    ): List<String> {
        val text = value.trim()
        if (text.isBlank()) return emptyList()
        if (text.length <= maxChars) return listOf(text)

        val result = mutableListOf<String>()
        var cursor = 0

        while (cursor < text.length) {
            var end = minOf(cursor + maxChars, text.length)

            if (end < text.length) {
                val sentence =
                    text.lastIndexOfAny(
                        charArrayOf('.', '!', '?', '…'),
                        end - 1
                    )
                val comma =
                    text.lastIndexOfAny(
                        charArrayOf(',', ';', ':', ' '),
                        end - 1
                    )

                end =
                    when {
                        sentence > cursor + maxChars / 2 ->
                            sentence + 1
                        comma > cursor + maxChars / 2 ->
                            comma + 1
                        else ->
                            end
                    }
            }

            val part = text.substring(cursor, end).trim()
            if (part.isNotBlank()) result += part
            cursor = end
        }

        return result
    }

    private fun ensureLoaded(context: Context): Module {
        module?.let { return it }

        synchronized(loadLock) {
            module?.let { return it }

            val target =
                File(
                    File(context.filesDir, "silero").apply {
                        mkdirs()
                    },
                    MODEL_FILE
                )

            if (!target.exists() || target.length() < 1024L * 1024L) {
                val temp = File(target.parentFile, "$MODEL_FILE.tmp")
                temp.delete()

                AppDiagnostics.info(
                    context,
                    "Copying bundled Silero model to private storage"
                )

                context.assets.open(MODEL_ASSET).use { input ->
                    temp.outputStream().buffered().use { output ->
                        input.copyTo(
                            output,
                            bufferSize = 256 * 1024
                        )
                    }
                }

                require(temp.length() > 1024L * 1024L) {
                    "Bundled Silero model is missing or damaged"
                }

                if (target.exists()) target.delete()
                require(temp.renameTo(target)) {
                    "Could not install bundled Silero model"
                }
            }

            AppDiagnostics.info(
                context,
                "Loading Silero TorchScript model: bytes=${target.length()}"
            )

            val loaded = Module.load(target.absolutePath)
            module = loaded

            AppDiagnostics.info(
                context,
                "Silero TorchScript model loaded"
            )

            return loaded
        }
    }

    private fun getMetadata(context: Context): Metadata {
        metadata?.let { return it }

        synchronized(loadLock) {
            metadata?.let { return it }

            val json =
                context.assets
                    .open(METADATA_ASSET)
                    .bufferedReader()
                    .use { it.readText() }

            val root = JSONObject(json)
            val speakersJson = root.getJSONArray("speakers")
            val speakers =
                buildList {
                    for (i in 0 until speakersJson.length()) {
                        add(speakersJson.getString(i))
                    }
                }

            val speakerIdsJson =
                root.getJSONObject("speaker_to_id")
            val speakerIds =
                buildMap {
                    val keys = speakerIdsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(
                            key,
                            speakerIdsJson.getLong(key)
                        )
                    }
                }

            val symbolIdsJson =
                root.getJSONObject("symbol_to_id")
            val symbolIds =
                buildMap {
                    val keys = symbolIdsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.length == 1) {
                            put(
                                key[0],
                                symbolIdsJson.getLong(key)
                            )
                        }
                    }
                }

            val ratesJson =
                root.getJSONArray("sample_rates")
            val rates =
                buildList {
                    for (i in 0 until ratesJson.length()) {
                        add(ratesJson.getInt(i))
                    }
                }

            return Metadata(
                modelId = root.optString(
                    "model_id",
                    "v5_5_ru"
                ),
                speakers = speakers,
                speakerToId = speakerIds,
                symbolToId = symbolIds,
                symbols = root.getString("symbols"),
                sampleRates = rates
            ).also {
                metadata = it
            }
        }
    }

    private fun normalizeText(
        value: String,
        metadata: Metadata
    ): String {
        val allowed =
            metadata.symbolToId.keys
                .filterNot {
                    it == '_' ||
                        it == '~' ||
                        it == '|'
                }
                .toSet()

        return value
            .lowercase()
            .replace('—', '–')
            .replace('‑', '-')
            .map { ch ->
                when {
                    ch in allowed -> ch
                    ch == '\n' || ch == '\r' || ch == '\t' -> ' '
                    else -> ' '
                }
            }
            .joinToString("")
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
            .take(MAX_CHARS)
            .trim()
    }

    private fun pcm16ToWav(
        pcm: ByteArray,
        sampleRate: Int
    ): ByteArray {
        val out =
            ByteArrayOutputStream(
                pcm.size + 44
            )

        fun ascii(value: String) {
            out.write(
                value.toByteArray(
                    Charsets.US_ASCII
                )
            )
        }

        fun le16(value: Int) {
            out.write(value and 0xff)
            out.write((value ushr 8) and 0xff)
        }

        fun le32(value: Int) {
            out.write(value and 0xff)
            out.write((value ushr 8) and 0xff)
            out.write((value ushr 16) and 0xff)
            out.write((value ushr 24) and 0xff)
        }

        ascii("RIFF")
        le32(36 + pcm.size)
        ascii("WAVE")
        ascii("fmt ")
        le32(16)
        le16(1)
        le16(1)
        le32(sampleRate)
        le32(sampleRate * 2)
        le16(2)
        le16(16)
        ascii("data")
        le32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}
