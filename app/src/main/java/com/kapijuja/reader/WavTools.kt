package com.kapijuja.reader

import java.io.ByteArrayOutputStream
import java.io.File

object WavTools {
    private data class Parsed(
        val fmt: ByteArray,
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val data: ByteArray
    )

    fun join(files: List<File>): ByteArray {
        require(files.isNotEmpty()) { "Нет WAV-фрагментов" }
        val waves = files.map { parse(it.readBytes()) }
        val first = waves.first()

        waves.drop(1).forEach {
            require(
                it.audioFormat == first.audioFormat &&
                    it.channels == first.channels &&
                    it.sampleRate == first.sampleRate &&
                    it.bitsPerSample == first.bitsPerSample
            ) {
                "Android TTS вернул WAV-фрагменты с разными аудиоформатами"
            }
        }

        val dataSize = waves.sumOf { it.data.size }
        val out = ByteArrayOutputStream(12 + 8 + first.fmt.size + 8 + dataSize)

        out.writeAscii("RIFF")
        out.writeLe32(4 + (8 + first.fmt.size) + (8 + dataSize))
        out.writeAscii("WAVE")
        out.writeAscii("fmt ")
        out.writeLe32(first.fmt.size)
        out.write(first.fmt)
        if (first.fmt.size and 1 != 0) out.write(0)

        out.writeAscii("data")
        out.writeLe32(dataSize)
        waves.forEach { out.write(it.data) }
        if (dataSize and 1 != 0) out.write(0)

        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray): Parsed {
        require(bytes.size >= 44) { "Слишком короткий WAV" }
        require(bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WAVE") {
            "Android TTS вернул не WAV/RIFF"
        }

        var offset = 12
        var fmt: ByteArray? = null
        var data: ByteArray? = null

        while (offset + 8 <= bytes.size) {
            val id = bytes.ascii(offset, 4)
            val size = bytes.le32(offset + 4)
            val start = offset + 8
            val end = (start + size).coerceAtMost(bytes.size)
            if (start > bytes.size || end < start) break

            when (id) {
                "fmt " -> fmt = bytes.copyOfRange(start, end)
                "data" -> data = bytes.copyOfRange(start, end)
            }

            offset = start + size + (size and 1)
        }

        val format = requireNotNull(fmt) { "В WAV нет fmt-блока" }
        val pcm = requireNotNull(data) { "В WAV нет data-блока" }
        require(format.size >= 16) { "Повреждён fmt-блок WAV" }

        return Parsed(
            fmt = format,
            audioFormat = format.le16(0),
            channels = format.le16(2),
            sampleRate = format.le32(4),
            bitsPerSample = format.le16(14),
            data = pcm
        )
    }

    private fun ByteArray.ascii(offset: Int, count: Int): String =
        String(this, offset, count, Charsets.US_ASCII)

    private fun ByteArray.le16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.le32(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }
}
