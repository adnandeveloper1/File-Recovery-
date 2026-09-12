package com.nexappra.filerecovery.data.repository

import com.nexappra.filerecovery.domain.model.RecoveryFileType
import java.io.File
import java.io.FileInputStream

/** Identifies common recoverable media from a small header read, not its filename. */
internal object FileSignatureDetector {

    fun detect(file: File): RecoveryFileType? = runCatching {
        FileInputStream(file).use { input ->
            val header = ByteArray(32)
            val count = input.read(header)
            if (count < 3) return null
            when {
                header.startsWith(0xFF, 0xD8, 0xFF) -> RecoveryFileType.Photo
                header.startsWith(0x89, 0x50, 0x4E, 0x47) -> RecoveryFileType.Photo
                header.asciiAt(0, "GIF87a") || header.asciiAt(0, "GIF89a") -> RecoveryFileType.Photo
                header.asciiAt(0, "RIFF") && header.asciiAt(8, "WEBP") -> RecoveryFileType.Photo
                header.asciiAt(0, "%PDF") -> RecoveryFileType.Document
                header.startsWith(0x50, 0x4B, 0x03, 0x04) -> RecoveryFileType.Archive
                header.asciiAt(4, "ftyp") && header.hasHeifBrand() -> RecoveryFileType.Photo
                header.asciiAt(4, "ftyp") -> RecoveryFileType.Video
                header.asciiAt(0, "ID3") || header.hasMpegAudioFrame() -> RecoveryFileType.Audio
                else -> null
            }
        }
    }.getOrNull()

    private fun ByteArray.startsWith(vararg values: Int): Boolean =
        values.indices.all { index -> size > index && (this[index].toInt() and 0xFF) == values[index] }

    private fun ByteArray.asciiAt(offset: Int, value: String): Boolean =
        value.indices.all { index -> size > offset + index && this[offset + index].toInt().toChar() == value[index] }

    private fun ByteArray.hasHeifBrand(): Boolean {
        val brands = listOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
        return brands.any { brand -> asciiAt(8, brand) || asciiAt(16, brand) }
    }

    private fun ByteArray.hasMpegAudioFrame(): Boolean =
        size > 1 && (this[0].toInt() and 0xFF) == 0xFF && (this[1].toInt() and 0xE0) == 0xE0
}
