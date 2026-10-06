package com.nexappra.filerecovery.data.repository

import com.nexappra.filerecovery.domain.model.RecoveryFileType
import java.io.File
import java.io.InputStream

/** Identifies media using at most 32 bytes, including streams that return short reads. */
internal object FileSignatureDetector {
    data class Signature(val type: RecoveryFileType, val mimeType: String)

    fun detect(file: File): RecoveryFileType? = inspect(file)?.type
    fun detect(input: InputStream): RecoveryFileType? = inspect(input)?.type
    fun inspect(file: File): Signature? = runCatching { file.inputStream().use(::inspect) }.getOrNull()

    fun inspect(input: InputStream): Signature? {
        val buffer = ByteArray(32)
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) break
            if (read == 0) {
                val next = input.read()
                if (next < 0) break
                buffer[count++] = next.toByte()
            } else count += read
        }
        val header = buffer.copyOf(count)
        fun photo(mime: String) = Signature(RecoveryFileType.Photo, mime)
        fun video(mime: String) = Signature(RecoveryFileType.Video, mime)
        fun audio(mime: String) = Signature(RecoveryFileType.Audio, mime)
        return when {
            header.startsWith(0xFF, 0xD8, 0xFF) -> photo("image/jpeg")
            header.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> photo("image/png")
            header.asciiAt(0, "GIF87a") || header.asciiAt(0, "GIF89a") -> photo("image/gif")
            header.asciiAt(0, "RIFF") && header.asciiAt(8, "WEBP") -> photo("image/webp")
            header.asciiAt(0, "RIFF") && header.asciiAt(8, "WAVE") -> audio("audio/wav")
            header.asciiAt(0, "RIFF") && header.asciiAt(8, "AVI ") -> video("video/x-msvideo")
            header.asciiAt(0, "fLaC") -> audio("audio/flac")
            header.asciiAt(0, "OggS") -> audio("audio/ogg")
            header.asciiAt(0, "#!AMR") -> audio("audio/amr")
            header.startsWith(0x1A, 0x45, 0xDF, 0xA3) -> video("video/webm")
            header.asciiAt(0, "FLV") -> video("video/x-flv")
            header.asciiAt(0, "BM") && count >= 14 -> photo("image/bmp")
            header.startsWith(0x49, 0x49, 0x2A, 0x00) || header.startsWith(0x4D, 0x4D, 0x00, 0x2A) -> photo("image/tiff")
            header.asciiAt(0, "%PDF") -> Signature(RecoveryFileType.Document, "application/pdf")
            header.startsWith(0x50, 0x4B, 0x03, 0x04) -> Signature(RecoveryFileType.Archive, "application/zip")
            header.asciiAt(4, "ftyp") -> when {
                header.hasBrand("avif", "avis") -> photo("image/avif")
                header.hasBrand("heic", "heix", "hevc", "hevx") -> photo("image/heic")
                header.hasBrand("mif1", "msf1") -> photo("image/heif")
                header.hasBrand("M4A ", "M4B ", "m4a ", "m4b ") -> audio("audio/mp4")
                header.asciiAt(8, "qt  ") -> video("video/quicktime")
                listOf("3gp4", "3gp5", "3gp6", "3gp7", "3g2a", "kddi").any { header.asciiAt(8, it) } -> video("video/3gpp")
                listOf("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "m4v ", "dash", "ndas", "mp71", "MSNV", "mmp4", "f4v ", "f4p ").any { header.asciiAt(8, it) } -> video("video/mp4")
                else -> null
            }
            header.asciiAt(0, "ID3") || header.hasMpegAudioFrame() -> Signature(RecoveryFileType.Audio, "audio/mpeg")
            (header.size > 1 && (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xF6) == 0xF0) -> Signature(RecoveryFileType.Audio, "audio/aac")
            else -> null
        }
    }

    private fun ByteArray.startsWith(vararg values: Int): Boolean =
        values.indices.all { index -> size > index && (this[index].toInt() and 0xFF) == values[index] }

    private fun ByteArray.asciiAt(offset: Int, value: String): Boolean =
        value.indices.all { index -> size > offset + index && this[offset + index].toInt().toChar() == value[index] }

    private fun ByteArray.hasBrand(vararg brands: String): Boolean =
        brands.any { brand -> asciiAt(8, brand) || (16..size - 4 step 4).any { asciiAt(it, brand) } }

    private fun ByteArray.hasMpegAudioFrame(): Boolean =
        size > 1 && (this[0].toInt() and 0xFF) == 0xFF && (this[1].toInt() and 0xE0) == 0xE0
}
