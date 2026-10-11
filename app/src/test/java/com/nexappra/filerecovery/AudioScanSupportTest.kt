package com.nexappra.filerecovery

import android.Manifest
import com.nexappra.filerecovery.data.repository.AudioScanSupport
import com.nexappra.filerecovery.data.repository.AudioScanSupport.ContainerMetadata
import com.nexappra.filerecovery.data.repository.FileSignatureDetector
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.RecoveryFileType
import com.nexappra.filerecovery.presentation.scan.mediaPermissions
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class AudioScanSupportTest {
    @Test fun audioOnlyContainersKeepTheirContainerFormat() {
        mapOf("video/mp4" to "audio/mp4", "video/3gpp" to "audio/3gpp",
            "video/webm" to "audio/webm", "video/x-matroska" to "audio/x-matroska",
            "application/ogg" to "audio/ogg").forEach { (reported, expected) ->
            assertEquals(RecoveryFileType.Audio to expected,
                AudioScanSupport.resolveContainer(RecoveryFileType.Video, reported, ContainerMetadata(true, false, reported)))
        }
    }

    @Test fun realVideosAreNotIncludedAsAudio() {
        assertEquals(RecoveryFileType.Video, AudioScanSupport.resolveContainer(
            RecoveryFileType.Video, "video/mp4", ContainerMetadata(true, true, "video/mp4"))?.first)
        assertEquals(RecoveryFileType.Video, AudioScanSupport.resolveContainer(
            RecoveryFileType.Audio, "audio/ogg", ContainerMetadata(true, true, "video/ogg"))?.first)
    }

    @Test fun verifiedAudioContainersRequireAudioTracksAndExcludeAnyVideoTrack() {
        assertEquals("audio/mp4", AudioScanSupport.verifiedAudioMime("video/mp4", ContainerMetadata(true, false, "video/mp4")))
        assertEquals("audio/ogg", AudioScanSupport.verifiedAudioMime("application/ogg", ContainerMetadata(true, false, "application/ogg")))
        assertNull(AudioScanSupport.verifiedAudioMime("audio/mp4", ContainerMetadata(true, true, "video/mp4")))
        assertNull(AudioScanSupport.verifiedAudioMime("audio/ogg", ContainerMetadata(false, false, "audio/ogg")))
    }

    @Test fun ambiguousContainerSignaturesNeedTrackChecks() {
        assertTrue(AudioScanSupport.isAudioOnlySignature("audio/wav"))
        assertTrue(AudioScanSupport.isAudioOnlySignature("audio/mpeg"))
        assertFalse(AudioScanSupport.isAudioOnlySignature("audio/mp4"))
        assertFalse(AudioScanSupport.isAudioOnlySignature("audio/ogg"))
        assertFalse(AudioScanSupport.isAudioOnlySignature("video/mp4"))
    }

    @Test fun midiAndAiffCopiesRemainRecoverableWithoutRequiringAPlaybackDecoder() {
        val midi = "MThd".toByteArray() + byteArrayOf(0, 0, 0, 6, 0, 0, 0, 1, 0, 96) +
            "MTrk".toByteArray() + byteArrayOf(0, 0, 0, 4, 0, 0xFF.toByte(), 0x2F, 0)
        val aiff = "FORM".toByteArray() + byteArrayOf(0, 0, 0, 4) + "AIFF".toByteArray()
        val aifc = "FORM".toByteArray() + byteArrayOf(0, 0, 0, 4) + "AIFC".toByteArray()
        listOf(midi to "audio/midi", aiff to "audio/aiff", aifc to "audio/aiff").forEach { (bytes, mime) ->
            val signature = checkNotNull(FileSignatureDetector.inspect(ByteArrayInputStream(bytes)))
            assertEquals(RecoveryFileType.Audio, signature.type)
            assertEquals(mime, signature.mimeType)
            assertTrue(AudioScanSupport.isAudioOnlySignature(signature.mimeType))
        }
        assertNull(FileSignatureDetector.inspect(ByteArrayInputStream("MThd".toByteArray())))
    }

    @Test fun failedMetadataReadKeepsExistingEvidenceWithoutInventingAudio() {
        assertEquals(RecoveryFileType.Video, AudioScanSupport.resolveContainer(RecoveryFileType.Video, "video/mp4", null)?.first)
        assertEquals(RecoveryFileType.Audio to "audio/ogg",
            AudioScanSupport.resolveContainer(RecoveryFileType.Audio, "audio/ogg", null))
        assertNull(AudioScanSupport.resolveContainer(null, "application/ogg", null))
    }

    @Test fun unknownSizeAudioRequiresReadableNonemptyContent() {
        assertEquals(0L, AudioScanSupport.readableSize(null) { true })
        assertNull(AudioScanSupport.readableSize(null) { false })
        assertNull(AudioScanSupport.readableSize(0L) { error("Known empty files must not be opened") })
        assertEquals(100L, AudioScanSupport.readableSize(100L) { error("Known sizes need no extra read") })
    }

    @Test fun audioPermissionRequestsExcludeVisualAccess() {
        listOf(33, 34, 36).forEach { sdk ->
            assertEquals(listOf(Manifest.permission.READ_MEDIA_AUDIO), mediaPermissions(RecoveryCategory.Audio, sdk).toList())
        }
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), mediaPermissions(RecoveryCategory.Audio, 32).toList())
        assertTrue(mediaPermissions(null, 34).contains(Manifest.permission.READ_MEDIA_AUDIO))
        assertTrue(mediaPermissions(RecoveryCategory.Photos, 34).contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(mediaPermissions(RecoveryCategory.Videos, 34).contains(Manifest.permission.READ_MEDIA_VIDEO))
    }
}
