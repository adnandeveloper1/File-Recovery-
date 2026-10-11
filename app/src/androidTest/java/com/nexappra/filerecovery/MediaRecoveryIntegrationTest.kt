package com.nexappra.filerecovery

import android.content.ContentValues
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.documentfile.provider.DocumentFile
import com.nexappra.filerecovery.data.repository.*
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.PremiumRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class MediaRecoveryIntegrationTest {
    private val testDirectory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "recovery-tests-" + UUID.randomUUID()).apply { mkdirs() }
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getCacheDir() = testDirectory
        override fun getFilesDir() = testDirectory
    }
    private val resolver = context.contentResolver
    private val created = mutableListOf<Uri>()
    private val prefix = "recovery_test_" + UUID.randomUUID()
    private val premium = object : PremiumRepository {
        override val state = MutableStateFlow(BillingState(access = PremiumAccess(true, Long.MAX_VALUE)))
        override fun refresh() {}
    }
    private val store = ScanSessionStore(context)
    private val history = RecoveryHistoryStore(context)
    private val writer = MediaRecoveryWriter(context, premium, history)
    private val repository = MediaStoreRecoveryScanRepository(context, store, premium, writer)
    private val imageBytes = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.GREEN); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        }; output.toByteArray()
    }
    private val videoBytes by lazy { RecoveryVideoFixtures.video(testDirectory) }
    private val audioOnlyMp4Bytes by lazy { RecoveryVideoFixtures.audioOnlyMp4(testDirectory) }
    private fun seed(collection: Uri, name: String, mime: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(collection, values)); created += uri
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        // Publishing a pending item makes the provider index the completed write before scanning.
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)!!.use {
            assertTrue("Published fixture must exist", it.moveToFirst())
            assertTrue("Published fixture must have indexed bytes", it.getLong(0) > 0)
        }
        return uri
    }
    private fun seedRecoverablePhoto(): Uri {
        Assume.assumeTrue("Provider trash needs Android 11+", Build.VERSION.SDK_INT >= 30)
        val uri = seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ".png", "image/png", imageBytes)
        assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null))
        return uri
    }
    private fun seedRecoverableVideo(): Uri {
        Assume.assumeTrue("Provider trash needs Android 11+", Build.VERSION.SDK_INT >= 30)
        val uri = seed(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "_trashed.mp4", "video/mp4", videoBytes)
        assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null))
        return uri
    }
    private fun seedRecoverableAudio(): Uri {
        Assume.assumeTrue("Provider trash needs Android 11+", Build.VERSION.SDK_INT >= 30)
        val uri = seed(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "_trashed.m4a", "audio/mp4", audioOnlyMp4Bytes)
        assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null))
        return uri
    }
    @After fun cleanup() {
        created.forEach { uri -> runCatching {
            if (Build.VERSION.SDK_INT >= 30) resolver.delete(uri, Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) })
            else resolver.delete(uri, null, null)
        } }
        testDirectory.deleteRecursively()
    }
    @Test fun indexedScansAreSelectiveAndPublishResults() = runBlocking {
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        Assume.assumeTrue("Grant audio or All Files access before running this integration test",
            context.checkSelfPermission(audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager())
        val gallery = seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "_gallery.png", "image/png", imageBytes)
        val recoverable = seedRecoverablePhoto()
        val galleryVideo = seed(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "_gallery.mp4", "video/mp4", videoBytes)
        val trashedVideo = seedRecoverableVideo()
        val libraryAudio = seed(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "_library.m4a", "audio/mp4", audioOnlyMp4Bytes)
        val trashedAudio = seedRecoverableAudio()
        val snapshots = mutableListOf<RecoveryScanSnapshot>()
        val photos = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) { snapshots += it }
        assertTrue("Expected photo fixture; warnings=${photos.warnings}", photos.files.any { it.displayName.contains(prefix) })
        assertTrue(photos.files.any { it.uriString == recoverable.toString() && it.isTrashed })
        assertFalse(photos.files.any { it.uriString == gallery.toString() })
        assertTrue(photos.files.all { it.fileType == RecoveryFileType.Photo })
        assertTrue(snapshots.any { it.previewFiles.isNotEmpty() })
        val videos = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Quick) {}
        val foundVideos = videos.files.filter { it.displayName.startsWith(prefix) }
        assertEquals(1, foundVideos.size)
        assertTrue(foundVideos.single().isTrashed)
        assertEquals(trashedVideo.toString(), foundVideos.single().uriString)
        assertFalse(videos.files.any { it.uriString == galleryVideo.toString() })
        assertTrue(videos.files.all { it.fileType == RecoveryFileType.Video })
        assertFalse(videos.files.any { it.fileType == RecoveryFileType.Audio })
        val zip = seed(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "_videos.zip", "application/zip", byteArrayOf(0))
        assertEquals(1, repository.exportArchive(videos.id, foundVideos.map { it.id }.toSet(), zip.toString()).recoveredCount)
        ZipInputStream(resolver.openInputStream(zip)).use { stream -> assertNotNull(stream.nextEntry); assertArrayEquals(videoBytes, stream.readBytes()) }
        resolver.openInputStream(trashedVideo)!!.use { assertArrayEquals(videoBytes, it.readBytes()) }
        val audio = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Quick) {}
        val foundAudio = audio.files.filter { it.displayName.startsWith(prefix) }
        assertEquals(1, foundAudio.size)
        assertEquals(RecoveryFileType.Audio, foundAudio.single().fileType)
        assertTrue(foundAudio.single().isTrashed)
        assertEquals(trashedAudio.toString(), foundAudio.single().uriString)
        assertFalse(audio.files.any { it.uriString == libraryAudio.toString() })
        assertTrue(audio.files.all { it.fileType == RecoveryFileType.Audio })
        val audioZip = seed(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "_audio.zip", "application/zip", byteArrayOf(0))
        assertEquals(1, repository.exportArchive(audio.id, foundAudio.map { it.id }.toSet(), audioZip.toString()).recoveredCount)
        ZipInputStream(resolver.openInputStream(audioZip)).use { stream -> assertNotNull(stream.nextEntry); assertArrayEquals(audioOnlyMp4Bytes, stream.readBytes()) }
        resolver.openInputStream(trashedAudio)!!.use { assertArrayEquals(audioOnlyMp4Bytes, it.readBytes()) }
        assertEquals(photos.files, ScanSessionStore(context).load(photos.id)!!.files)
    }
    @Test fun cancellationKeepsPartialSession() = runBlocking {
        seedRecoverablePhoto()
        var id = ""
        try {
            repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {
                id = it.sessionId
                if (it.previewFiles.isNotEmpty()) throw CancellationException("Test early stop")
            }
        } catch (_: CancellationException) {}
        val session = checkNotNull(repository.getSession(id))
        assertTrue(session.isPartial)
        assertTrue(session.files.isNotEmpty())
    }
    @Test fun cloudArchivePreservesOriginalBytesAndRepairProducesReadablePng() = runBlocking {
        seedRecoverablePhoto()
        val scan = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
        val file = scan.files.single { it.displayName.contains(prefix) }
        val zip = seed(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ".zip", "application/zip", byteArrayOf(0))
        val copied = repository.exportArchive(scan.id, setOf(file.id), zip.toString())
        assertEquals(1, copied.recoveredCount)
        ZipInputStream(resolver.openInputStream(zip)).use { stream -> assertNotNull(stream.nextEntry); assertArrayEquals(imageBytes, stream.readBytes()) }
        val repaired = seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "_repaired.png", "image/png", byteArrayOf(0))
        repository.repairPhoto(scan.id, file.id, repaired.toString())
        resolver.openInputStream(repaired).use { input ->
            val bitmap = BitmapFactory.decodeStream(input)
            assertNotNull(bitmap); assertEquals(16, bitmap.width); bitmap.recycle()
        }
    }
    @Test fun repositoryRejectsUnverifiedPremium() = runBlocking {
        premium.state.value = BillingState()
        try { repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Deep) {}; fail("Deep scan must require verified Premium") }
        catch (_: PremiumRequiredException) {}
    }

    @Test fun exportAndRepairCannotOverwriteTheirSource() = runBlocking {
        val source = seedRecoverablePhoto()
        val scan = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
        val file = scan.files.single { it.displayName.contains(prefix) }
        try { repository.exportArchive(scan.id, setOf(file.id), file.uriString); fail("Source must be preserved") }
        catch (_: IllegalArgumentException) {}
        try { repository.repairPhoto(scan.id, file.id, file.uriString); fail("Source must be preserved") }
        catch (_: IllegalArgumentException) {}
        resolver.openInputStream(source)!!.use { assertArrayEquals(imageBytes, it.readBytes()) }
        assertTrue(history.entries.value.isEmpty())
    }

    @Test fun failedArchiveRemovesPartialOutputAndDoesNotRecordSuccess() = runBlocking {
        seedRecoverablePhoto()
        val scan = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
        val file = scan.files.single { it.displayName.contains(prefix) }
        val changed = scan.copy(files = listOf(file.copy(sizeBytes = file.sizeBytes + 1)))
        val zip = seed(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ".zip", "application/zip", byteArrayOf(0))
        try { writer.exportArchive(changed, setOf(file.id), zip.toString()); fail("A changed source must fail") }
        catch (_: java.io.IOException) {}
        resolver.query(zip, arrayOf("_id"), null, null, null)!!.use { assertFalse(it.moveToFirst()) }
        assertTrue(history.entries.value.isEmpty())
    }

    /** Select the documented QA folder once in the app before running this provider test. */
    @Test fun quickAndDeepFolderScansFindHiddenMediaAndCopyOriginalBytes() {
        val permission = resolver.persistedUriPermissions.firstOrNull {
            it.isReadPermission && it.isWritePermission && Uri.decode(it.uri.toString()).endsWith("Download/RecoveryQA_20261004")
        }
        Assume.assumeNotNull(permission)
        val root = checkNotNull(DocumentFile.fromTreeUri(context, permission!!.uri))
        val fixtureFolder = checkNotNull(root.createDirectory(prefix))
        try {
            fun write(name: String, mime: String, bytes: ByteArray) {
                val file = checkNotNull(fixtureFolder.createFile(mime, prefix + name))
                resolver.openOutputStream(file.uri)!!.use { it.write(bytes) }
            }
            write(".png", "image/png", imageBytes)
            write(".cache", "application/octet-stream", imageBytes)
            write(".mp4", "video/mp4", videoBytes)
            write("_video.cache", "application/octet-stream", videoBytes)
            write("_invalid.mp4", "video/mp4", ByteArray(64) { 1 })
            write("_audio_only.mp4", "video/mp4", audioOnlyMp4Bytes)
            write(".m4a", "audio/mp4", audioOnlyMp4Bytes)
            write("_audio.cache", "application/octet-stream", audioOnlyMp4Bytes)
            write("_document_audio.mp3", "audio/mpeg", "%PDF-disguised-audio".toByteArray())
            write("_invalid.m4a", "audio/mp4", ByteArray(64) { 1 })
            write(".txt", "text/plain", "Skip this document".toByteArray())
            write("_document.jpg", "application/octet-stream", "%PDF-disguised-document".toByteArray())
            val nested = checkNotNull(fixtureFolder.createDirectory("Nested"))
            val nestedPhoto = checkNotNull(nested.createFile("image/png", prefix + "_nested.png"))
            resolver.openOutputStream(nestedPhoto.uri)!!.use { it.write(imageBytes) }
            val nestedVideo = checkNotNull(nested.createFile("video/mp4", prefix + "_nested.mp4"))
            resolver.openOutputStream(nestedVideo.uri)!!.use { it.write(videoBytes) }
            val nestedAudio = checkNotNull(nested.createFile("audio/mp4", prefix + "_nested.m4a"))
            resolver.openOutputStream(nestedAudio.uri)!!.use { it.write(audioOnlyMp4Bytes) }
            var deepFolder = nested
            repeat(6) { depth -> deepFolder = checkNotNull(deepFolder.createDirectory("Level$depth")) }
            val deepVideo = checkNotNull(deepFolder.createFile("video/mp4", prefix + "_deep.mp4"))
            resolver.openOutputStream(deepVideo.uri)!!.use { it.write(videoBytes) }
            val deepAudio = checkNotNull(deepFolder.createFile("audio/mp4", prefix + "_deep.m4a"))
            resolver.openOutputStream(deepAudio.uri)!!.use { it.write(audioOnlyMp4Bytes) }
            // Put the marker after the media entries to exercise metadata-first detection.
            val marker = checkNotNull(fixtureFolder.createFile("application/octet-stream", ".nomedia"))
            assertEquals(".nomedia", marker.name)
            val gallery = checkNotNull(root.createFile("image/png", prefix + "_gallery.png"))
            resolver.openOutputStream(gallery.uri)!!.use { it.write(imageBytes) }
            val galleryVideo = checkNotNull(root.createFile("video/mp4", prefix + "_gallery.mp4"))
            resolver.openOutputStream(galleryVideo.uri)!!.use { it.write(videoBytes) }
            val libraryAudio = checkNotNull(root.createFile("audio/mp4", prefix + "_library.m4a"))
            resolver.openOutputStream(libraryAudio.uri)!!.use { it.write(audioOnlyMp4Bytes) }
            val hiddenAudio = checkNotNull(root.createFile("audio/mp4", "." + prefix + "_hidden.m4a"))
            assertTrue(hiddenAudio.name!!.startsWith('.'))
            resolver.openOutputStream(hiddenAudio.uri)!!.use { it.write(audioOnlyMp4Bytes) }
            runBlocking {
                val quick = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
                val quickFound = quick.files.filter { it.displayName.startsWith(prefix) }
                assertEquals(3, quickFound.size)
                assertTrue(quickFound.all { it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
                assertFalse(quick.files.any { it.uriString == gallery.uri.toString() })
                val photos = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Deep) {}
                val found = photos.files.filter { it.displayName.startsWith(prefix) }
                assertEquals("The PNG, cache image and nested photo must be found", 3, found.size)
                assertTrue(found.all { it.fileType == RecoveryFileType.Photo })
                assertTrue(found.all { it.mimeType == "image/png" })
                assertTrue(found.all { it.isHidden && RecoveryFileSource.NoMedia in it.sources })
                assertFalse(photos.files.any { it.uriString == gallery.uri.toString() })
                assertEquals(photos.files, ScanSessionStore(context).load(photos.id)!!.files)
                val copied = repository.recoverToFolder(photos.id, found.map { it.id }.toSet(), permission.uri.toString())
                assertEquals(3, copied.recoveredCount)
                assertEquals(0, copied.skippedCount)
                val copies = root.listFiles().filter { it.isFile && it.name.orEmpty().startsWith(prefix) && it.name.orEmpty().contains("_recovered_") }
                assertEquals(3, copies.size)
                copies.forEach { copy ->
                    assertTrue(copy.name.orEmpty().endsWith(".png"))
                    assertEquals("image/png", copy.type)
                    resolver.openInputStream(copy.uri)!!.use { assertArrayEquals(imageBytes, it.readBytes()) }
                }
                val repeated = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
                assertEquals("Recovered copies in an ordinary folder must not return as recovery candidates", 3,
                    repeated.files.count { it.displayName.startsWith(prefix) })

                val zip = checkNotNull(fixtureFolder.createFile("application/zip", prefix + ".zip"))
                val exported = repository.exportArchive(photos.id, found.map { it.id }.toSet(), zip.uri.toString())
                assertEquals(3, exported.recoveredCount)
                assertEquals(0, exported.skippedCount)
                ZipInputStream(resolver.openInputStream(zip.uri)).use { archive ->
                    val names = mutableSetOf<String>()
                    repeat(3) {
                        val entry = checkNotNull(archive.nextEntry)
                        assertTrue("Cache images need usable extensions in ZIP exports", entry.name.endsWith(".png"))
                        assertTrue("Archive entry names must remain unique", names.add(entry.name))
                        assertArrayEquals(imageBytes, archive.readBytes())
                    }
                    assertNull(archive.nextEntry)
                }

                // Cancel before the second file and ensure the completed first copy is remembered.
                val interruptedHistory = RecoveryHistoryStore(context)
                var checks = 0
                val interruptedPremium = object : PremiumRepository {
                    override val state = premium.state
                    override fun refresh() {}
                    override fun requirePremium() {
                        if (++checks == 3) throw CancellationException("Stop before second file")
                    }
                }
                val interruptedWriter = MediaRecoveryWriter(context, interruptedPremium, interruptedHistory)
                try { interruptedWriter.copyToFolder(photos, found.map { it.id }.toSet(), permission.uri.toString()); fail("Expected cancellation") }
                catch (_: CancellationException) {}
                assertEquals(1, interruptedHistory.entries.value.first().count)
                val quickVideos = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Quick) {}
                val quickFoundVideos = quickVideos.files.filter { it.displayName.startsWith(prefix) }
                assertEquals(3, quickFoundVideos.size)
                assertTrue(quickFoundVideos.all { it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
                assertFalse(quickVideos.files.any { it.uriString == galleryVideo.uri.toString() || it.uriString == deepVideo.uri.toString() })
                val videos = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Deep) {}
                assertTrue(videos.files.all { it.fileType == RecoveryFileType.Video })
                val foundVideos = videos.files.filter { it.displayName.startsWith(prefix) }
                assertEquals(4, foundVideos.size)
                assertTrue(foundVideos.all { it.fileType == RecoveryFileType.Video && it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
                assertTrue(foundVideos.any { it.uriString == deepVideo.uri.toString() })
                assertFalse(videos.files.any { it.uriString == galleryVideo.uri.toString() })
                assertTrue(foundVideos.none { it.displayName.contains("_invalid") || it.displayName.contains("_audio_only") })
                val recoveredVideo = writer.copyToFolder(videos.copy(files = listOf(foundVideos.first())), setOf(foundVideos.first().id), permission.uri.toString())
                assertEquals(1, recoveredVideo.recoveredCount)
                val visibleVideoCopy = root.listFiles().single { it.name.orEmpty().contains(prefix) && it.name.orEmpty().contains("_recovered_") && it.name.orEmpty().endsWith(".mp4") }
                assertFalse(visibleVideoCopy.name!!.startsWith('.'))
                resolver.openInputStream(visibleVideoCopy.uri)!!.use { assertArrayEquals(videoBytes, it.readBytes()) }
                visibleVideoCopy.delete()

                val quickAudio = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Quick) {}
                val quickFoundAudio = quickAudio.files.filter { it.displayName.contains(prefix) }
                assertEquals(5, quickFoundAudio.size)
                assertTrue(quickFoundAudio.all { it.fileType == RecoveryFileType.Audio && it.isHidden })
                assertEquals(4, quickFoundAudio.count { it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
                assertFalse(quickAudio.files.any { it.displayName == libraryAudio.name || it.displayName == deepAudio.name })
                val audio = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Deep) {}
                assertTrue(audio.files.all { it.fileType == RecoveryFileType.Audio })
                val foundAudio = audio.files.filter { it.displayName.contains(prefix) }
                assertEquals(6, foundAudio.size)
                assertTrue(foundAudio.any { it.displayName.contains("_audio_only") })
                assertEquals(5, foundAudio.count { it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
                assertTrue(foundAudio.any { it.displayName == deepAudio.name })
                assertFalse(audio.files.any { it.displayName == libraryAudio.name })
                assertTrue(foundAudio.none { it.displayName.contains("_invalid") || it.displayName.contains("_document_audio") })
                // Index and folder visits can use different URIs for the same physical source.
                val voice = foundAudio.single { it.displayName == hiddenAudio.name }
                assertEquals(RecoveryFileHiddenReason.DotFile, voice.hiddenReason)
                val recoveredAudio = repository.recoverToFolder(audio.id, setOf(voice.id), permission.uri.toString())
                assertEquals(1, recoveredAudio.recoveredCount)
                val audioCopies = root.listFiles().filter { it.name.orEmpty().startsWith(prefix + "_hidden_recovered_") }
                assertEquals(1, audioCopies.size)
                assertFalse(audioCopies.single().name!!.startsWith('.'))
                resolver.openInputStream(audioCopies.single().uri)!!.use { assertArrayEquals(audioOnlyMp4Bytes, it.readBytes()) }
                resolver.openInputStream(hiddenAudio.uri)!!.use { assertArrayEquals(audioOnlyMp4Bytes, it.readBytes()) }
                val rescan = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Quick) {}
                assertFalse(rescan.files.any { it.displayName == audioCopies.single().name })
            }
        } finally {
            fixtureFolder.delete()
            root.listFiles().filter { it.isFile && it.name.orEmpty().contains(prefix) }.forEach { it.delete() }
        }
    }
}
