package com.nexappra.filerecovery

import com.nexappra.filerecovery.data.repository.MediaFileIdentity
import org.junit.Assert.*
import org.junit.Test

class MediaFileIdentityTest {
    private val primaryRoot = "/storage/emulated/0"
    private val provider = "com.android.externalstorage.documents"

    @Test fun indexedFolderAndDirectVisitsKeepOneResultForEachMediaFile() {
        val ids = listOf("photo.jpg", "video.mp4", "audio.mp3").flatMap { name ->
            listOf(
                MediaFileIdentity.storage("external_primary", "DCIM/Camera/", name),
                MediaFileIdentity.document(provider, "primary:DCIM/Camera/$name", primaryRoot),
                MediaFileIdentity.absolute("$primaryRoot/DCIM/Camera/$name", primaryRoot),
            )
        }
        assertEquals(9, ids.size)
        assertEquals(3, ids.toSet().size)
    }

    @Test fun sdCardIdsMatchWithoutMergingFilesOnDifferentVolumes() {
        val indexed = MediaFileIdentity.storage("abcd-1234", "Android/media/app/", "photo.jpg")
        assertEquals(indexed, MediaFileIdentity.document(provider, "ABCD-1234:Android/media/app/photo.jpg", primaryRoot))
        assertEquals(indexed, MediaFileIdentity.absolute("/storage/ABCD-1234/Android/media/app/photo.jpg", primaryRoot))
        assertNotEquals(indexed, MediaFileIdentity.storage("external_primary", "Android/media/app/", "photo.jpg"))
    }

    @Test fun filenamesAndDirectoriesStayDistinct() {
        val camera = MediaFileIdentity.storage("primary", "DCIM/Camera", "Photo.jpg")
        assertNotEquals(camera, MediaFileIdentity.storage("primary", "Pictures", "Photo.jpg"))
        assertNotEquals(camera, MediaFileIdentity.storage("primary", "DCIM/Camera", "photo.jpg"))
    }

    @Test fun rootFilesAndRawDownloadsUseTheSameLocalIdentity() {
        assertEquals(MediaFileIdentity.storage("external_primary", "", "photo.jpg"),
            MediaFileIdentity.document(provider, "primary:photo.jpg", primaryRoot))
        assertEquals(MediaFileIdentity.storage("primary", "Download", "audio.mp3"),
            MediaFileIdentity.document("com.android.providers.downloads.documents", "raw:$primaryRoot/Download/audio.mp3", primaryRoot))
    }

    @Test fun unrelatedProvidersNeverMergeOpaqueDocumentIds() {
        assertNotEquals(MediaFileIdentity.document("cloud.one", "123", primaryRoot),
            MediaFileIdentity.document("cloud.two", "123", primaryRoot))
        assertNull(MediaFileIdentity.absolute("/unknown/photo.jpg", primaryRoot))
    }
}
