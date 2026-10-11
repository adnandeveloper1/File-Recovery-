package com.nexappra.filerecovery

import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VisualRecoveryPolicyTest {
    private fun video(path: String = "DCIM/Camera", name: String = "movie.mp4") = RecoverableFile(
        "video", "content://videos/1", name, "video/mp4", 200, 0, path, RecoveryFileType.Video,
        emptySet(), false, null, false, "external_primary",
    )

    @Test fun ordinaryGalleryVideosAreExcludedRegardlessOfFolderOrCombinedCategory() {
        listOf("DCIM/Camera", "Movies", "Download", "WhatsApp/Media/WhatsApp Video").forEach { path ->
            assertFalse(path, VisualRecoveryPolicy.accepts(video(path)))
        }
        // An unindexed normal file is not evidence of hiding or deletion.
        assertFalse(VisualRecoveryPolicy.accepts(video().copy(uriString = "file:///storage/emulated/0/DCIM/Camera/movie.mp4")))
    }

    @Test fun hiddenNamesAndAncestorDirectoriesAreEligible() {
        assertTrue(VisualRecoveryPolicy.accepts(video(name = ".movie.mp4")))
        assertTrue(VisualRecoveryPolicy.accepts(video("Movies/.private/Nested")))
        assertTrue(VisualRecoveryPolicy.accepts(video("Movies\\.private\\Nested")))
        assertFalse(VisualRecoveryPolicy.accepts(video("Movies/private.hidden")))
        assertFalse(VisualRecoveryPolicy.accepts(video(name = "deleted_movie.mp4")))
    }

    @Test fun noMediaContextSurvivesSessionStorageAndHidesDescendants() {
        assertTrue(VisualRecoveryPolicy.isCandidate("movie.mp4", "Custom/Private/Nested", noMedia = true))
        assertTrue(VisualRecoveryPolicy.accepts(video("Custom/Private/Nested").copy(sources = setOf(RecoveryFileSource.NoMedia))))
        assertTrue(VisualRecoveryPolicy.accepts(video().copy(hiddenReason = RecoveryFileHiddenReason.NoMediaDirectory)))
        assertFalse(VisualRecoveryPolicy.accepts(video(".hidden", ".nomedia").copy(isTrashed = true, isHidden = true)))
    }

    @Test fun realTrashFlagsAndExactRecycleFoldersAreAccepted() {
        assertTrue(VisualRecoveryPolicy.accepts(video().copy(isTrashed = true)))
        listOf("Trash", "DCIM/.Trash/Nested", "Movies/RECYCLE_BIN", "\$Recycle.Bin").forEach { path ->
            val candidate = video(path)
            assertTrue(path, VisualRecoveryPolicy.accepts(candidate))
            assertFalse(candidate.isTrashed)
        }
        listOf("Movies/TrashHoliday", "Movies/my_recycle_bin", "Movies/deleted").forEach { path ->
            assertFalse(path, VisualRecoveryPolicy.accepts(video(path)))
        }
    }

    @Test fun sharedRulesPreservePhotoEligibilityAndOrdinaryAudio() {
        assertFalse(VisualRecoveryPolicy.accepts(video().copy(fileType = RecoveryFileType.Photo)))
        assertTrue(VisualRecoveryPolicy.accepts(video("Pictures/.hidden").copy(fileType = RecoveryFileType.Photo)))
        assertTrue(VisualRecoveryPolicy.accepts(video().copy(fileType = RecoveryFileType.Audio)))
    }

    @Test fun quickSearchIncludesVideoParentsWhileStillRejectingTheirNormalFiles() {
        listOf("Movies/Family", "Videos/Clips", "DCIM/Camera", "Android/media/chat/Video", "Custom/Trash").forEach { path ->
            assertTrue(path, VisualRecoveryPolicy.exploreQuickDirectory(path, false))
        }
        assertFalse(VisualRecoveryPolicy.exploreQuickDirectory("Documents/Work", false))
        assertTrue(VisualRecoveryPolicy.exploreQuickDirectory("Documents/Work", true))
        assertFalse(VisualRecoveryPolicy.isCandidate("clip.mp4", "Movies/Family"))
    }

    @Test fun recoveredVideoNamesAreVisibleWithoutLosingNormalPrefixes() {
        assertEquals("movie.mp4", VisualRecoveryPolicy.recoveryName(".movie.mp4", "recovered_video"))
        assertEquals("movie.mp4", VisualRecoveryPolicy.recoveryName(".trashed-1777777777-movie.mp4", "recovered_video"))
        assertEquals("trashed-family.mp4", VisualRecoveryPolicy.recoveryName("trashed-family.mp4"))
        assertEquals("recovered_video", VisualRecoveryPolicy.recoveryName("...", "recovered_video"))
    }
}
