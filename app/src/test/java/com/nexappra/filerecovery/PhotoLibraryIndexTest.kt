package com.nexappra.filerecovery

import com.nexappra.filerecovery.data.repository.MediaFileIdentity
import com.nexappra.filerecovery.data.repository.MediaLibraryIndex
import com.nexappra.filerecovery.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PhotoLibraryIndexTest {
    private val root = "/storage/emulated/0"
    private fun photo(id: String) = RecoverableFile(id, "file://$root/Pictures/Trash/photo.jpg", "photo.jpg",
        "image/jpeg", 100, 0, "Pictures/Trash", RecoveryFileType.Photo, emptySet(), false, null, false, "external_primary")

    @Test fun indexedIdentityBlocksTheSamePhotoThroughDirectAndDocumentAliases() {
        val index = MediaLibraryIndex(RecoveryFileType.Photo, complete = true)
        index.add(MediaFileIdentity.storage("external_primary", "Pictures/Trash", "photo.jpg"))
        val direct = photo(checkNotNull(MediaFileIdentity.absolute("$root/Pictures/Trash/photo.jpg", root)))
        val document = photo(MediaFileIdentity.document("com.android.externalstorage.documents", "primary:Pictures/Trash/photo.jpg", root))
        assertFalse(PhotoRecoveryPolicy.accepts(index.check(direct)))
        assertFalse(PhotoRecoveryPolicy.accepts(index.check(document)))
    }

    @Test fun aHiddenCopyWithTheSameNameOnAnotherPathOrVolumeRemainsEligible() {
        val index = MediaLibraryIndex(RecoveryFileType.Photo, complete = true)
        index.add(MediaFileIdentity.storage("external_primary", "Pictures/Trash", "photo.jpg"))
        assertTrue(PhotoRecoveryPolicy.accepts(index.check(photo(MediaFileIdentity.storage("primary", "Pictures/.private", "photo.jpg")))))
        assertTrue(PhotoRecoveryPolicy.accepts(index.check(photo(MediaFileIdentity.storage("abcd-1234", "Pictures/Trash", "photo.jpg")))))
    }

    @Test fun incompleteLibraryCheckDoesNotMistakeUnknownPhotosForAbsentPhotos() {
        val index = MediaLibraryIndex(RecoveryFileType.Photo, complete = false)
        assertNull(index.check(photo("unverified")).isInPhotoLibrary)
        assertFalse(PhotoRecoveryPolicy.accepts(index.check(photo("unverified"))))
        assertTrue(PhotoRecoveryPolicy.accepts(index.check(photo("trash").copy(isTrashed = true))))
        index.add("known")
        assertEquals(true, index.check(photo("known")).isInPhotoLibrary)
    }

    @Test fun aFreshLibraryCheckReplacesStoredVisibilityWithoutChangingOtherMedia() {
        val index = MediaLibraryIndex(RecoveryFileType.Photo, complete = true)
        assertEquals(false, index.check(photo("hidden").copy(isInPhotoLibrary = true)).isInPhotoLibrary)
        val video = photo("video").copy(fileType = RecoveryFileType.Video)
        assertSame(video, index.check(video))
    }
}
