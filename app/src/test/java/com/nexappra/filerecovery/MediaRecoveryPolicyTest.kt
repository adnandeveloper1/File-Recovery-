package com.nexappra.filerecovery

import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MediaRecoveryPolicyTest {
    private fun audio(path: String = "Music", name: String = "recording.m4a") = RecoverableFile(
        "audio", "content://audio/1", name, "audio/mp4", 200, 0, path, RecoveryFileType.Audio,
        emptySet(), false, null, false, "external_primary",
    )

    @Test fun normalMusicRecordingsAndUnindexedCopiesAreExcluded() {
        listOf("Music", "Recordings", "Download", "Android/media/chat/Audio").forEach { path ->
            assertFalse(path, MediaRecoveryPolicy.accepts(audio(path)))
        }
        assertFalse(MediaRecoveryPolicy.accepts(audio().copy(uriString = "file:///storage/emulated/0/Music/recording.m4a")))
        assertFalse(MediaRecoveryPolicy.accepts(audio(name = "deleted_recording.m4a")))
    }

    @Test fun dotFilesHiddenAncestorsAndInheritedNoMediaAreEligible() {
        assertTrue(MediaRecoveryPolicy.accepts(audio(name = ".recording.m4a")))
        assertTrue(MediaRecoveryPolicy.accepts(audio("Recordings/.private/Nested")))
        assertTrue(MediaRecoveryPolicy.accepts(audio("Recordings\\.private\\Nested")))
        assertTrue(MediaRecoveryPolicy.accepts(audio("Custom/Private/Nested").copy(
            hiddenReason = RecoveryFileHiddenReason.NoMediaDirectory, sources = setOf(RecoveryFileSource.NoMedia))))
        assertFalse(MediaRecoveryPolicy.accepts(audio(".private", ".nomedia").copy(isHidden = true, isTrashed = true)))
    }

    @Test fun providerTrashAndExactRecycleLocationsAreEligibleWithoutInferringDeletion() {
        assertTrue(MediaRecoveryPolicy.accepts(audio().copy(isTrashed = true)))
        listOf("Trash", "Recordings/.Trash/Nested", "Music/RECYCLE_BIN", "\$Recycle.Bin").forEach { path ->
            val candidate = audio(path)
            assertTrue(path, MediaRecoveryPolicy.accepts(candidate))
            assertFalse(candidate.isTrashed)
        }
        assertTrue(MediaRecoveryPolicy.accepts(audio(name = ".trashed-1777777777-recording.m4a")))
        listOf("Music/TrashHoliday", "Recordings/deleted", "Music/my_recycle_bin").forEach { path ->
            assertFalse(path, MediaRecoveryPolicy.accepts(audio(path)))
        }
    }

    @Test fun combinedScansApplyTheSameContextToEveryMediaType() {
        listOf(RecoveryFileType.Photo, RecoveryFileType.Video, RecoveryFileType.Audio).forEach { type ->
            assertFalse(MediaRecoveryPolicy.accepts(audio().copy(fileType = type)))
            assertTrue(MediaRecoveryPolicy.accepts(audio(".hidden").copy(fileType = type, isInPhotoLibrary = false, isInVideoLibrary = false)))
        }
        assertFalse(MediaRecoveryPolicy.accepts(audio(".hidden").copy(fileType = RecoveryFileType.Document)))
    }

    @Test fun quickAudioDiscoveryAddsAudioParentsWithoutChangingVisualOnlySearches() {
        listOf("Music/Album", "Recordings/Calls", "Podcasts/Archive", "Sounds", "Android/media/chat/Audio").forEach { path ->
            assertTrue(path, MediaRecoveryPolicy.exploreQuickDirectory(path, false, includesAudio = true))
        }
        assertFalse(MediaRecoveryPolicy.exploreQuickDirectory("Music/Album", false, includesAudio = false))
        assertFalse(MediaRecoveryPolicy.isMedia(RecoveryFileType.Document))
        assertFalse(MediaRecoveryPolicy.exploreQuickDirectory("Documents/Work", false, includesAudio = true))
        assertTrue(MediaRecoveryPolicy.exploreQuickDirectory("Documents/Work", true, includesAudio = true))
    }

    @Test fun recoveredAudioNamesRemoveHiddenAndReservedTrashPrefixes() {
        assertEquals("recording.m4a", VisualRecoveryPolicy.recoveryName(".recording.m4a", "recovered_audio"))
        assertEquals("recording.m4a", VisualRecoveryPolicy.recoveryName(".trashed-1777777777-recording.m4a", "recovered_audio"))
        assertEquals("trashed-family.m4a", VisualRecoveryPolicy.recoveryName("trashed-family.m4a", "recovered_audio"))
        assertEquals("recovered_audio", VisualRecoveryPolicy.recoveryName("...", "recovered_audio"))
    }
}
