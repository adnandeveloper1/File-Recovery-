package com.nexappra.filerecovery

import com.nexappra.filerecovery.data.repository.MediaFileIdentity
import com.nexappra.filerecovery.data.repository.MediaLibraryIndex
import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VideoLibraryIndexTest {
    private val root = "/storage/emulated/0"
    private fun video(id: String) = RecoverableFile(id, "file://$root/Movies/Trash/movie.mp4", "movie.mp4", "video/mp4",
        100, 0, "Movies/Trash", RecoveryFileType.Video, emptySet(), false, null, false, "external_primary")

    @Test fun indexedVideoIsRejectedAcrossDirectAndDocumentAliases() {
        val index = MediaLibraryIndex(RecoveryFileType.Video, complete = true)
        index.add(MediaFileIdentity.storage("external_primary", "Movies/Trash", "movie.mp4"))
        val direct = video(checkNotNull(MediaFileIdentity.absolute("$root/Movies/Trash/movie.mp4", root)))
        val document = video(MediaFileIdentity.document("com.android.externalstorage.documents", "primary:Movies/Trash/movie.mp4", root))
        assertFalse(VideoRecoveryPolicy.accepts(index.check(direct)))
        assertFalse(VideoRecoveryPolicy.accepts(index.check(document)))
    }

    @Test fun distinctFolderAndSdCardCopiesAreNotMistakenForTheIndexedSource() {
        val index = MediaLibraryIndex(RecoveryFileType.Video, complete = true)
        index.add(MediaFileIdentity.storage("primary", "Movies/Trash", "movie.mp4"))
        assertTrue(VideoRecoveryPolicy.accepts(index.check(video(MediaFileIdentity.storage("primary", "Movies/.private", "movie.mp4")))))
        assertTrue(VideoRecoveryPolicy.accepts(index.check(video(MediaFileIdentity.storage("abcd-1234", "Movies/Trash", "movie.mp4")))))
    }

    @Test fun incompleteChecksWithholdUnknownVideosButStillBlockKnownLibraryVideos() {
        val index = MediaLibraryIndex(RecoveryFileType.Video, complete = false)
        assertNull(index.check(video("unknown")).isInVideoLibrary)
        assertFalse(VideoRecoveryPolicy.accepts(index.check(video("unknown"))))
        assertTrue(VideoRecoveryPolicy.accepts(index.check(video("trash").copy(isTrashed = true))))
        index.add("known")
        assertFalse(VideoRecoveryPolicy.accepts(index.check(video("known").copy(isTrashed = true))))
    }

    @Test fun freshVideoChecksReplaceOldVisibilityAndPreservePhotoAndAudioState() {
        val index = MediaLibraryIndex(RecoveryFileType.Video, complete = true)
        assertEquals(false, index.check(video("hidden").copy(isInVideoLibrary = true)).isInVideoLibrary)
        val photo = video("photo").copy(fileType = RecoveryFileType.Photo, isInPhotoLibrary = false)
        val audio = video("audio").copy(fileType = RecoveryFileType.Audio)
        assertSame(photo, index.check(photo))
        assertSame(audio, index.check(audio))
    }
}
