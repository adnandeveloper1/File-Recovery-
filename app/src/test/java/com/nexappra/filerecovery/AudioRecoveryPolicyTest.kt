package com.nexappra.filerecovery

import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AudioRecoveryPolicyTest {
    private fun audio(path: String = "Music", name: String = "voice.m4a") = RecoverableFile(
        "audio", "content://audio/1", name, "audio/mp4", 200, 0, path, RecoveryFileType.Audio,
        emptySet(), false, null, false, "external_primary", isInAudioLibrary = false)

    @Test fun ordinaryMusicAndRecordingsNeedRecoveryEvidenceEvenWhenNotIndexed() {
        listOf("Music", "Recordings", "Download", "Android/media/chat/Audio").forEach {
            assertFalse(it, AudioRecoveryPolicy.accepts(audio(it)))
        }
        assertFalse(AudioRecoveryPolicy.accepts(audio(name = "deleted_voice.m4a")))
        assertFalse(AudioRecoveryPolicy.accepts(audio().copy(isHidden = true, sources = setOf(RecoveryFileSource.Hidden))))
        assertFalse(AudioRecoveryPolicy.accepts(audio().copy(sources = setOf(RecoveryFileSource.RecycleBin))))
    }

    @Test fun verifiedHiddenFilesAncestorsAndNoMediaFoldersRemainEligible() {
        assertTrue(AudioRecoveryPolicy.accepts(audio(name = ".voice.m4a")))
        assertTrue(AudioRecoveryPolicy.accepts(audio("Recordings/.private/Nested")))
        assertTrue(AudioRecoveryPolicy.accepts(audio("Recordings\\.private\\Nested")))
        assertTrue(AudioRecoveryPolicy.accepts(audio("Music/Private").copy(hiddenReason = RecoveryFileHiddenReason.NoMediaDirectory)))
        assertTrue(AudioRecoveryPolicy.accepts(audio().copy(sources = setOf(RecoveryFileSource.NoMedia))))
    }

    @Test fun ordinaryLibraryMembershipOverridesHiddenAndTrashHints() {
        listOf("Music/.private", "Music/Trash", "Music/Private").forEach {
            assertFalse(it, AudioRecoveryPolicy.accepts(audio(it).copy(isInAudioLibrary = true,
                hiddenReason = RecoveryFileHiddenReason.NoMediaDirectory, sources = setOf(RecoveryFileSource.NoMedia))))
        }
        assertFalse(AudioRecoveryPolicy.accepts(audio().copy(isInAudioLibrary = true, isTrashed = true)))
    }

    @Test fun incompleteChecksOnlyPermitConfirmedProviderTrash() {
        assertFalse(AudioRecoveryPolicy.accepts(audio("Music/.private").copy(isInAudioLibrary = null)))
        assertFalse(AudioRecoveryPolicy.accepts(audio("Music/Trash").copy(isInAudioLibrary = null)))
        assertTrue(AudioRecoveryPolicy.accepts(audio().copy(isInAudioLibrary = null, isTrashed = true)))
    }

    @Test fun exactRecycleFoldersRemainDistinctFromNamesSuggestingDeletion() {
        listOf("Trash", "Music/.Trash/Nested", "Recordings/RECYCLE_BIN", "\$Recycle.Bin").forEach {
            assertTrue(it, AudioRecoveryPolicy.accepts(audio(it)))
        }
        assertTrue(AudioRecoveryPolicy.accepts(audio(name = ".trashed-1777777777-voice.m4a")))
        listOf("Music/TrashHoliday", "Recordings/deleted", "Music/my_recycle_bin").forEach {
            assertFalse(it, AudioRecoveryPolicy.accepts(audio(it)))
        }
        assertFalse(AudioRecoveryPolicy.isCandidate(".nomedia", "Music/.private", noMedia = true))
    }

    @Test fun recoveryEligibilityDoesNotDependOnDecoderSupportOrAudioExtension() {
        listOf("voice.mp3", "voice.wav", "voice.m4a", "voice.mid", "voice.aiff", "voice.cache").forEach {
            assertTrue(it, AudioRecoveryPolicy.accepts(audio(".private", it)))
        }
    }

    @Test fun sharedPolicyPreservesVerifiedPhotosAndVideos() {
        assertTrue(MediaRecoveryPolicy.accepts(audio(".private").copy(fileType = RecoveryFileType.Photo, isInPhotoLibrary = false)))
        assertTrue(MediaRecoveryPolicy.accepts(audio(".private").copy(fileType = RecoveryFileType.Video, isInVideoLibrary = false)))
        assertFalse(MediaRecoveryPolicy.accepts(audio(".private").copy(fileType = RecoveryFileType.Video)))
    }
}
