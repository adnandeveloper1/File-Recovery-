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
}
