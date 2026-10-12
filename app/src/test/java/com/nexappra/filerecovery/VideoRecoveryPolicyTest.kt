package com.nexappra.filerecovery

import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VideoRecoveryPolicyTest {
    private fun video(path: String = "Movies", name: String = "movie.mp4") = RecoverableFile(
        "video", "content://videos/1", name, "video/mp4", 200, 0, path, RecoveryFileType.Video,
        emptySet(), false, null, false, "external_primary", isInVideoLibrary = false)

    @Test fun ordinaryVideosNeedRecoveryEvidenceEvenWhenNotIndexed() {
        listOf("DCIM/Camera", "Movies", "Download", "Android/media/chat/Video").forEach {
            assertFalse(it, VideoRecoveryPolicy.accepts(video(it)))
        }
        assertFalse(VideoRecoveryPolicy.accepts(video(name = "deleted_movie.mp4")))
        assertFalse(VideoRecoveryPolicy.accepts(video().copy(isHidden = true, sources = setOf(RecoveryFileSource.Hidden))))
        assertFalse(VideoRecoveryPolicy.accepts(video().copy(sources = setOf(RecoveryFileSource.RecycleBin))))
    }

    @Test fun verifiedHiddenNamesAncestorsAndNoMediaFoldersAreEligible() {
        assertTrue(VideoRecoveryPolicy.accepts(video(name = ".movie.mp4")))
        assertTrue(VideoRecoveryPolicy.accepts(video("Movies/.private/Nested")))
        assertTrue(VideoRecoveryPolicy.accepts(video("Movies\\.private\\Nested")))
        assertTrue(VideoRecoveryPolicy.accepts(video("Movies/Private").copy(hiddenReason = RecoveryFileHiddenReason.NoMediaDirectory)))
        assertTrue(VideoRecoveryPolicy.accepts(video().copy(sources = setOf(RecoveryFileSource.NoMedia))))
    }

    @Test fun indexedGalleryEvidenceOverridesEveryHiddenOrTrashHint() {
        listOf("Movies/.private", "Movies/Trash", "Movies/Private").forEach {
            assertFalse(it, VideoRecoveryPolicy.accepts(video(it).copy(isInVideoLibrary = true,
                sources = setOf(RecoveryFileSource.NoMedia), hiddenReason = RecoveryFileHiddenReason.NoMediaDirectory)))
        }
        assertFalse(VideoRecoveryPolicy.accepts(video().copy(isInVideoLibrary = true, isTrashed = true)))
    }

    @Test fun onlyConfirmedTrashIsEligibleWhenLibraryVerificationIsIncomplete() {
        assertFalse(VideoRecoveryPolicy.accepts(video("Movies/.private").copy(isInVideoLibrary = null)))
        assertFalse(VideoRecoveryPolicy.accepts(video("Movies/Trash").copy(isInVideoLibrary = null)))
        assertTrue(VideoRecoveryPolicy.accepts(video().copy(isInVideoLibrary = null, isTrashed = true)))
    }

    @Test fun galleryThumbnailFoldersAndTheirMarkersAreExcluded() {
        listOf("DCIM/.thumbnails", "Movies/.THUMBNAILS/Nested", "Movies/Thumbnails").forEach {
            assertFalse(it, VideoRecoveryPolicy.accepts(video(it).copy(isTrashed = true)))
        }
        assertFalse(VideoRecoveryPolicy.isCandidate(".nomedia", "Movies/.private", noMedia = true))
        assertFalse(VideoRecoveryPolicy.isCandidate(".thumbdata4-123", "Movies/.private"))
    }

    @Test fun exactRecycleLocationsAreAcceptedWithoutClaimingProviderDeletion() {
        listOf("Trash", "Movies/.Trash/Nested", "Movies/RECYCLE_BIN", "\$Recycle.Bin").forEach {
            assertTrue(it, VideoRecoveryPolicy.accepts(video(it)))
        }
        assertTrue(VideoRecoveryPolicy.accepts(video(name = ".trashed-1777777777-movie.mp4")))
        listOf("Movies/TrashHoliday", "Movies/my_recycle_bin", "Movies/deleted").forEach {
            assertFalse(it, VideoRecoveryPolicy.accepts(video(it)))
        }
    }

    @Test fun sharedPolicyLeavesAudioAndVerifiedPhotosEligible() {
        assertTrue(MediaRecoveryPolicy.accepts(video(".private").copy(fileType = RecoveryFileType.Audio)))
        assertTrue(MediaRecoveryPolicy.accepts(video(".private").copy(fileType = RecoveryFileType.Photo, isInPhotoLibrary = false)))
        assertFalse(MediaRecoveryPolicy.accepts(video(".private").copy(fileType = RecoveryFileType.Photo)))
    }
}
