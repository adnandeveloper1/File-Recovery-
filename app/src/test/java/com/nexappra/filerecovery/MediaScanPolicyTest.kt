package com.nexappra.filerecovery
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.data.repository.FileSignatureDetector
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class MediaScanPolicyTest {
    @Test fun photoScanRejectsVideosAudioAndDisguisedDocuments() {
        val policy = MediaScanPolicy(RecoveryCategory.Photos)
        assertTrue(policy.accepts("holiday.JPG", null))
        assertTrue(policy.accepts("cache", "image/webp"))
        assertFalse(policy.accepts("holiday.mp4", "video/mp4"))
        assertFalse(policy.accepts("cover.jpg", "audio/mpeg"))
        assertFalse(policy.accepts("document.jpg", "application/pdf"))
    }
    @Test fun videoScanRejectsPhotosAndQuickScanContainsOnlyVisualMedia() {
        assertTrue(MediaScanPolicy(RecoveryCategory.Videos).accepts("MOVIE.MOV", "application/octet-stream"))
        assertFalse(MediaScanPolicy(RecoveryCategory.Videos).accepts("photo.png", "image/png"))
        assertEquals(setOf(RecoveryFileType.Photo, RecoveryFileType.Video), MediaScanPolicy().types)
        assertFalse(MediaScanPolicy().accepts("voice.mp3", null))
        assertFalse(MediaScanPolicy().accepts("backup.zip", null))
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedCategoryCannotStartVisualScan() { MediaScanPolicy(RecoveryCategory.Audio) }
    @Test fun unsafeFileNamesCannotEscapeDestination() {
        assertEquals("holiday.jpg", MediaScanPolicy.safeFileName("../../holiday.jpg"))
        assertEquals("photo.jpg", MediaScanPolicy.safeFileName("C:\\private\\photo.jpg"))
        assertEquals("recovered_media", MediaScanPolicy.safeFileName(".."))
        assertFalse(MediaScanPolicy.safeFileName("bad\u0000:name.jpg").contains(':'))
    }
    @Test fun entitlementMustBeVerifiedAndUnexpired() {
        assertFalse(PremiumAccess(false, 200).isActive(100))
        assertFalse(PremiumAccess(true, 100).isActive(100))
        assertTrue(PremiumAccess(true, 101).isActive(100))
    }
    @Test fun headerDetectionDoesNotTreatM4AAudioAsVideo() {
        fun header(brand: String) = ByteArray(32).apply {
            "ftyp".toByteArray().copyInto(this, 4); brand.toByteArray().copyInto(this, 8)
        }
        assertNull(FileSignatureDetector.detect(ByteArrayInputStream(header("M4A "))))
        assertEquals(RecoveryFileType.Photo, FileSignatureDetector.detect(ByteArrayInputStream(header("avif"))))
        assertEquals(RecoveryFileType.Video, FileSignatureDetector.detect(ByteArrayInputStream(header("mp42"))))
    }
    @Test fun ambiguousCacheMimeAllowsHeaderInspectionButKnownDocumentsDoNot() {
        val policy = MediaScanPolicy(RecoveryCategory.Photos)
        assertTrue(policy.shouldReadHeader("hidden_copy.cache", "chemical/x-cache"))
        assertTrue(policy.shouldReadHeader("thumbnail.bin", "application/octet-stream"))
        assertFalse(policy.shouldReadHeader("document.cache", "application/pdf"))
        assertFalse(policy.shouldReadHeader("voice.tmp", "audio/mpeg"))
        assertEquals(RecoveryFileType.Photo, FileSignatureDetector.detect(ByteArrayInputStream(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))))
    }

    @Test fun cacheSignatureHandlesShortReadsAndKeepsItsRealMimeType() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val stream = object : ByteArrayInputStream(png) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = super.read(bytes, offset, minOf(length, 1))
        }
        val signature = FileSignatureDetector.inspect(stream)
        assertEquals(RecoveryFileType.Photo, signature?.type)
        assertEquals("image/png", signature?.mimeType)
        assertNull(FileSignatureDetector.inspect(ByteArrayInputStream(png.copyOf(4))))
    }

    @Test fun compatibleAvifBrandBeyondFirstSlotIsStillAnImage() {
        val header = ByteArray(32).apply {
            "ftyp".toByteArray().copyInto(this, 4)
            "mif1".toByteArray().copyInto(this, 8)
            "avif".toByteArray().copyInto(this, 24)
        }
        assertEquals("image/avif", FileSignatureDetector.inspect(ByteArrayInputStream(header))?.mimeType)
    }
}
