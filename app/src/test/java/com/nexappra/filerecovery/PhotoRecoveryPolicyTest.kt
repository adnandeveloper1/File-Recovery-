package com.nexappra.filerecovery

import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PhotoRecoveryPolicyTest {
    private fun photo(path: String = "DCIM/Camera", name: String = "holiday.jpg") = RecoverableFile(
        "photo", "content://photos/1", name, "image/jpeg", 200, 0, path, RecoveryFileType.Photo,
        emptySet(), false, null, false, "external_primary",
    )

    @Test fun ordinaryGalleryPhotosAreExcludedEvenInCombinedScans() {
        listOf("DCIM/Camera", "Pictures/Screenshots", "Download", "WhatsApp/Media/WhatsApp Images").forEach { path ->
            assertFalse(path, PhotoRecoveryPolicy.accepts(photo(path)))
        }
    }

    @Test fun hiddenFilesAndAncestorsHaveAccurateReasons() {
        assertEquals(RecoveryFileHiddenReason.DotFile, PhotoRecoveryPolicy.hiddenReason(".photo.jpg", "Pictures"))
        assertEquals(RecoveryFileHiddenReason.HiddenDirectory, PhotoRecoveryPolicy.hiddenReason("photo.jpg", "Pictures/.hidden/Nested"))
        assertTrue(PhotoRecoveryPolicy.accepts(photo("Pictures\\.hidden\\Nested")))
        assertFalse(PhotoRecoveryPolicy.accepts(photo("Pictures/holiday.hidden")))
    }

    @Test fun noMediaContextIncludesDescendantsWithoutTreatingTheMarkerAsAPhoto() {
        assertTrue(PhotoRecoveryPolicy.isCandidate("photo.jpg", "Pictures/Private/Nested", noMedia = true))
        assertEquals(RecoveryFileHiddenReason.NoMediaDirectory, PhotoRecoveryPolicy.hiddenReason("photo.jpg", "Pictures/Private/Nested", true))
        assertTrue(PhotoRecoveryPolicy.accepts(photo("Pictures/Private/Nested").copy(
            sources = setOf(RecoveryFileSource.NoMedia), hiddenReason = RecoveryFileHiddenReason.NoMediaDirectory)))
        assertFalse(PhotoRecoveryPolicy.isCandidate(".nomedia", "Pictures/.hidden", trashed = true, noMedia = true))
        assertFalse(PhotoRecoveryPolicy.accepts(photo("Pictures/.hidden", ".nomedia").copy(isHidden = true)))
    }

    @Test fun recycleFoldersAndProviderTrashStayDistinctFromNamesThatOnlySuggestDeletion() {
        listOf("Pictures/Trash", "DCIM/.Trash/Nested", "Pictures/RECYCLE_BIN", "\$Recycle.Bin").forEach { path ->
            assertTrue(path, PhotoRecoveryPolicy.accepts(photo(path)))
        }
        assertTrue(PhotoRecoveryPolicy.accepts(photo().copy(isTrashed = true)))
        assertTrue(PhotoRecoveryPolicy.isRecycleLocation(".trashed-1777777777-holiday.jpg", "Pictures"))
        listOf("Pictures/TrashHoliday", "Pictures/deleted", "Pictures/my_recycle_bin").forEach { path ->
            assertFalse(path, PhotoRecoveryPolicy.accepts(photo(path)))
        }
        assertFalse(PhotoRecoveryPolicy.isRecycleLocation("deleted_photo.jpg", "Pictures"))
    }

    @Test fun photoRulesLeaveOrdinaryVideoAndAudioCandidatesAlone() {
        assertTrue(PhotoRecoveryPolicy.accepts(photo().copy(fileType = RecoveryFileType.Video, displayName = "movie.mp4")))
        assertTrue(PhotoRecoveryPolicy.accepts(photo().copy(fileType = RecoveryFileType.Audio, displayName = "voice.mp3")))
    }

    @Test fun quickDiscoveryTargetsCommonParentsAndEligibleFolders() {
        listOf("DCIM/Camera", "Pictures/Album", "Android/media/app", ".hidden", "Custom/Trash").forEach { path ->
            assertTrue(path, PhotoRecoveryPolicy.exploreQuickDirectory(path, false))
        }
        assertTrue(PhotoRecoveryPolicy.exploreQuickDirectory("Custom/Private", true))
        assertFalse(PhotoRecoveryPolicy.exploreQuickDirectory("Documents/Work", false))
        assertFalse(PhotoRecoveryPolicy.exploreQuickDirectory("Android/mediabackup", false))
    }

    @Test fun indexedPatternsEscapeUnderscoresAndDoNotTargetOrdinaryGalleryFolders() {
        val patterns = PhotoRecoveryPolicy.indexedPathPatterns()
        assertTrue(patterns.contains("recycle\\_bin/%"))
        assertTrue(patterns.contains("%/recycle\\_bin/%"))
        assertFalse(patterns.any { it.contains("DCIM") || it.contains("Camera") || it.contains("Download") })
    }

    @Test fun recoveredPhotoNamesAreVisibleAndRemoveOnlyReservedTrashPrefixes() {
        assertEquals("holiday.jpg", PhotoRecoveryPolicy.recoveryName(".holiday.jpg"))
        assertEquals("holiday.jpg", PhotoRecoveryPolicy.recoveryName(".trashed-1777777777-holiday.jpg"))
        assertEquals("holiday.jpg", PhotoRecoveryPolicy.recoveryName("holiday.jpg"))
        assertEquals("trashed-family.jpg", PhotoRecoveryPolicy.recoveryName("trashed-family.jpg"))
        assertEquals("recovered_photo", PhotoRecoveryPolicy.recoveryName("..."))
    }
}
