package com.nexappra.filerecovery

import android.Manifest
import com.nexappra.filerecovery.data.repository.AudioScanSupport
import com.nexappra.filerecovery.data.repository.AudioScanSupport.ContainerMetadata
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.RecoveryFileType
import com.nexappra.filerecovery.presentation.scan.mediaPermissions
import org.junit.Assert.*
import org.junit.Test

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
