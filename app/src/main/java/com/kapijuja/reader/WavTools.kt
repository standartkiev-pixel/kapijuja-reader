package com.kapijuja.reader

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

object WavTools {
    private data class Parsed(
        val fmt: ByteArray,
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Long
    )

    fun joinTo(files: List<File>, output: OutputStream) {
        require(files.isNotEmpty()) { "Нет WAV-фрагментов" }

        val parsed = files.map { parse(it) }
        val first = parsed.first()

        parsed.drop(1).forEach {
            require(
                it.audioFormat == first.audioFormat &&
                    it.channels == first.channels &&
                    it.sampleRate == first.sampleRate &&
                    it.bitsPerSample == first.bitsPerSample &&
                    it.fmt.contentEquals(first.fmt)
            ) {
                "Android TTS вернул WAV-фрагменты с разными аудиоформатами"
            }
        }

        val dataSize = parsed.sumOf { it.dataSize }
        require(dataSize <= 0xFFFF_FFFFL - 64L) {
            "WAV больше 4 ГБ не поддерживается"
        }

        val riffSize = 4L + (8L + padded(first.fmt.size.toLong())) +
            (8L + padded(dataSize))
        require(riffSize <= 0xFFFF_FFFFL) {
            "WAV больше 4 ГБ не поддерживается"
        }

        output.writeAscii("RIFF")
        output.writeLe32(riffSize.toInt())
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeLe32(first.fmt.size)
        output.write(first.fmt)
        if (first.fmt.size and 1 != 0) output.write(0)

        output.writeAscii("data")
        output.writeLe32(dataSize.toInt())

        val buffer = ByteArray(64 * 1024)
        files.zip(parsed).forEach { (file, info) ->
            RandomAccessFile(file, "r").use { input ->
                input.seek(info.dataOffset)
                var remaining = info.dataSize
                while (remaining > 0) {
                    val want = minOf(buffer.size.toLong(), remaining).toInt()
                    val count = input.read(buffer, 0, want)
                    if (count <= 0) error("Повреждён WAV-фрагмент")
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
        }

        if (dataSize and 1L != 0L) output.write(0)
        output.flush()
    }

    fun pcmToWavTo(
        pcmFile: File,
        output: OutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        require(sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
            "Некорректный PCM формат"
        }

        val dataSize = pcmFile.length()
        require(dataSize <= 0xFFFF_FFFFL - 64L) {
            "WAV больше 4 ГБ не поддерживается"
        }

        val byteRate =
            sampleRate.toLong() * channels.toLong() * bitsPerSample.toLong() / 8L
        val blockAlign =
            channels * bitsPerSample / 8

        output.writeAscii("RIFF")
        output.writeLe32((36L + dataSize).toInt())
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeLe32(16)
        output.writeLe16(1)
        output.writeLe16(channels)
        output.writeLe32(sampleRate)
        output.writeLe32(byteRate.toInt())
        output.writeLe16(blockAlign)
        output.writeLe16(bitsPerSample)
        output.writeAscii("data")
        output.writeLe32(dataSize.toInt())

        val buffer = ByteArray(64 * 1024)
        pcmFile.inputStream().buffered().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
            }
        }
        output.flush()
    }

    private fun parse(file: File): Parsed {
        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 44L) { "Слишком короткий WAV" }

            val riff = ByteArray(12)
            input.readFully(riff)
            require(riff.ascii(0, 4) == "RIFF" && riff.ascii(8, 4) == "WAVE") {
                "Android TTS вернул не WAV/RIFF"
            }

            var fmt: ByteArray? = null
            var dataOffset = -1L
            var dataSize = -1L

            while (input.filePointer + 8L <= input.length()) {
                val idBytes = ByteArray(4)
                input.readFully(idBytes)
                val id = String(idBytes, Charsets.US_ASCII)
                val size = input.readLe32Unsigned()
                val start = input.filePointer

                when (id) {
                    "fmt " -> {
                        require(size <= 1024L) { "Слишком большой fmt-блок WAV" }
                        fmt = ByteArray(size.toInt()).also { input.readFully(it) }
                    }
                    "data" -> {
                        dataOffset = start
                        dataSize = minOf(size, input.length() - start)
                        input.seek(start + size.coerceAtMost(input.length() - start))
                    }
                    else -> input.seek(
                        (start + size).coerceAtMost(input.length())
                    )
                }

                if (size and 1L != 0L && input.filePointer < input.length()) {
                    input.seek(input.filePointer + 1L)
                }

                if (fmt != null && dataOffset >= 0L) break
            }

            val format = requireNotNull(fmt) { "В WAV нет fmt-блока" }
            require(format.size >= 16) { "Повреждён fmt-блок WAV" }
            require(dataOffset >= 0L && dataSize >= 0L) { "В WAV нет data-блока" }

            return Parsed(
                fmt = format,
                audioFormat = format.le16(0),
                channels = format.le16(2),
                sampleRate = format.le32(4),
                bitsPerSample = format.le16(14),
                dataOffset = dataOffset,
                dataSize = dataSize
            )
        }
    }

    private fun padded(value: Long): Long =
        value + (value and 1L)

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

    private fun RandomAccessFile.readLe32Unsigned(): Long {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        require(b3 >= 0) { "Повреждён WAV" }
        return (
            (b0.toLong() and 0xffL) or
                ((b1.toLong() and 0xffL) shl 8) or
                ((b2.toLong() and 0xffL) shl 16) or
                ((b3.toLong() and 0xffL) shl 24)
            )
    }

    private fun OutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun OutputStream.writeLe16(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun OutputStream.writeLe32(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }
}
