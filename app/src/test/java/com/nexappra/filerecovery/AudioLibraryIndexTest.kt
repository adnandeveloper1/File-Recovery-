package com.nexappra.filerecovery

import com.nexappra.filerecovery.data.repository.MediaFileIdentity
import com.nexappra.filerecovery.data.repository.MediaLibraryIndex
import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AudioLibraryIndexTest {
    private val root = "/storage/emulated/0"
    private fun audio(id: String) = RecoverableFile(id, "file://$root/Music/Trash/voice.m4a", "voice.m4a", "audio/mp4",
        100, 0, "Music/Trash", RecoveryFileType.Audio, emptySet(), false, null, false, "external_primary")

    @Test fun anIndexedRecordingIsBlockedAcrossDirectAndDocumentAliases() {
        val index = MediaLibraryIndex(RecoveryFileType.Audio, complete = true)
        index.add(MediaFileIdentity.storage("external_primary", "Music/Trash", "voice.m4a"))
        val direct = audio(checkNotNull(MediaFileIdentity.absolute("$root/Music/Trash/voice.m4a", root)))
        val document = audio(MediaFileIdentity.document("com.android.externalstorage.documents", "primary:Music/Trash/voice.m4a", root))
        assertFalse(AudioRecoveryPolicy.accepts(index.check(direct)))
        assertFalse(AudioRecoveryPolicy.accepts(index.check(document)))
    }

    @Test fun differentFolderAndSdCardCopiesAreNotConfusedWithTheIndexedRecording() {
        val index = MediaLibraryIndex(RecoveryFileType.Audio, complete = true)
        index.add(MediaFileIdentity.storage("primary", "Music/Trash", "voice.m4a"))
        assertTrue(AudioRecoveryPolicy.accepts(index.check(audio(MediaFileIdentity.storage("primary", "Music/.private", "voice.m4a")))))
        assertTrue(AudioRecoveryPolicy.accepts(index.check(audio(MediaFileIdentity.storage("abcd-1234", "Music/Trash", "voice.m4a")))))
    }

    @Test fun incompleteChecksWithholdUnknownAudioAndContinueBlockingKnownLibraryAudio() {
        val index = MediaLibraryIndex(RecoveryFileType.Audio, complete = false)
        assertNull(index.check(audio("unknown")).isInAudioLibrary)
        assertFalse(AudioRecoveryPolicy.accepts(index.check(audio("unknown"))))
        assertTrue(AudioRecoveryPolicy.accepts(index.check(audio("trash").copy(isTrashed = true))))
        index.add("known")
        assertFalse(AudioRecoveryPolicy.accepts(index.check(audio("known").copy(isTrashed = true))))
    }

    @Test fun freshAudioChecksReplaceOldStateAndPreservePhotoAndVideoState() {
        val index = MediaLibraryIndex(RecoveryFileType.Audio, complete = true)
        assertEquals(false, index.check(audio("hidden").copy(isInAudioLibrary = true)).isInAudioLibrary)
        val photo = audio("photo").copy(fileType = RecoveryFileType.Photo, isInPhotoLibrary = false)
        val video = audio("video").copy(fileType = RecoveryFileType.Video, isInVideoLibrary = false)
        assertSame(photo, index.check(photo))
        assertSame(video, index.check(video))
    }
}
