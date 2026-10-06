package com.nexappra.filerecovery

import android.content.ContentValues
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
    @After fun cleanup() {
        created.forEach { runCatching { resolver.delete(it, null, null) } }
        testDirectory.deleteRecursively()
    }
    @Test fun indexedScansAreSelectiveAndPublishResults() = runBlocking {
        val audioPermission = android.Manifest.permission.READ_MEDIA_AUDIO
        Assume.assumeTrue("Grant READ_MEDIA_AUDIO before running this integration test", context.checkSelfPermission(audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ".png", "image/png", imageBytes)
        seed(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ".mp4", "video/mp4", ByteArray(64) { 1 })
        seed(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ".mp3", "audio/mpeg", "ID3-recovery-test".toByteArray())
        val snapshots = mutableListOf<RecoveryScanSnapshot>()
        val photos = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) { snapshots += it }
        assertTrue("Expected photo fixture; warnings=${photos.warnings}", photos.files.any { it.displayName.startsWith(prefix) })
        assertTrue(photos.files.all { it.fileType == RecoveryFileType.Photo })
        assertTrue(snapshots.any { it.previewFiles.isNotEmpty() })
        val videos = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Quick) {}
        assertTrue(videos.files.any { it.displayName.startsWith(prefix) })
        assertTrue(videos.files.all { it.fileType == RecoveryFileType.Video })
        assertFalse(videos.files.any { it.fileType == RecoveryFileType.Audio })
        val audio = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Quick) {}
        val foundAudio = audio.files.filter { it.displayName.startsWith(prefix) }
        assertEquals(1, foundAudio.size)
        assertEquals(RecoveryFileType.Audio, foundAudio.single().fileType)
        assertTrue(audio.files.all { it.fileType == RecoveryFileType.Audio })
        assertEquals(photos.files, ScanSessionStore(context).load(photos.id)!!.files)
    }
    @Test fun cancellationKeepsPartialSession() = runBlocking {
        seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ".png", "image/png", imageBytes)
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
        seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ".png", "image/png", imageBytes)
        val scan = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
        val file = scan.files.single { it.displayName.startsWith(prefix) }
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
        val source = seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ".png", "image/png", imageBytes)
        val scan = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
        val file = scan.files.single { it.displayName.startsWith(prefix) }
        try { repository.exportArchive(scan.id, setOf(file.id), file.uriString); fail("Source must be preserved") }
        catch (_: IllegalArgumentException) {}
        try { repository.repairPhoto(scan.id, file.id, file.uriString); fail("Source must be preserved") }
        catch (_: IllegalArgumentException) {}
        resolver.openInputStream(source)!!.use { assertArrayEquals(imageBytes, it.readBytes()) }
        assertTrue(history.entries.value.isEmpty())
    }

    @Test fun failedArchiveRemovesPartialOutputAndDoesNotRecordSuccess() = runBlocking {
        seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ".png", "image/png", imageBytes)
        val scan = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
        val file = scan.files.single { it.displayName.startsWith(prefix) }
        val changed = scan.copy(files = listOf(file.copy(sizeBytes = file.sizeBytes + 1)))
        val zip = seed(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ".zip", "application/zip", byteArrayOf(0))
        try { writer.exportArchive(changed, setOf(file.id), zip.toString()); fail("A changed source must fail") }
        catch (_: java.io.IOException) {}
        resolver.query(zip, arrayOf("_id"), null, null, null)!!.use { assertFalse(it.moveToFirst()) }
        assertTrue(history.entries.value.isEmpty())
    }

    /** Select the documented QA folder once in the app before running this provider test. */
    @Test fun deepFolderScanFindsCacheImagesAndCopiesOriginalBytes() {
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
            write(".mp4", "video/mp4", ByteArray(64) { 1 })
            write(".mp3", "audio/mpeg", "ID3-deep-scan".toByteArray())
            write(".txt", "text/plain", "Skip this document".toByteArray())
            runBlocking {
                val photos = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Deep) {}
                val found = photos.files.filter { it.displayName.startsWith(prefix) }
                assertEquals("The PNG and cache image must both be found", 2, found.size)
                assertTrue(found.all { it.fileType == RecoveryFileType.Photo })
                assertTrue(found.all { it.mimeType == "image/png" })
                assertEquals(photos.files, ScanSessionStore(context).load(photos.id)!!.files)
                val copied = repository.recoverToFolder(photos.id, found.map { it.id }.toSet(), permission.uri.toString())
                assertEquals(2, copied.recoveredCount)
                assertEquals(0, copied.skippedCount)
                val copies = root.listFiles().filter { it.isFile && it.name.orEmpty().startsWith(prefix) }
                assertEquals(2, copies.size)
                copies.forEach { copy ->
                    assertTrue(copy.name.orEmpty().endsWith(".png"))
                    assertEquals("image/png", copy.type)
                    resolver.openInputStream(copy.uri)!!.use { assertArrayEquals(imageBytes, it.readBytes()) }
                }

                val zip = checkNotNull(fixtureFolder.createFile("application/zip", prefix + ".zip"))
                val exported = repository.exportArchive(photos.id, found.map { it.id }.toSet(), zip.uri.toString())
                assertEquals(2, exported.recoveredCount)
                assertEquals(0, exported.skippedCount)
                ZipInputStream(resolver.openInputStream(zip.uri)).use { archive ->
                    val names = mutableSetOf<String>()
                    repeat(2) {
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
                val videos = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Deep) {}
                assertTrue(videos.files.all { it.fileType == RecoveryFileType.Video })
                val foundVideos = videos.files.filter { it.displayName.startsWith(prefix) }
                assertEquals(1, foundVideos.size)
                assertEquals(RecoveryFileType.Video, foundVideos.single().fileType)

                val audio = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Deep) {}
                assertTrue(audio.files.all { it.fileType == RecoveryFileType.Audio })
                val foundAudio = audio.files.filter { it.displayName.startsWith(prefix) }
                assertEquals(1, foundAudio.size)
                assertEquals(RecoveryFileType.Audio, foundAudio.single().fileType)
                val recoveredAudio = repository.recoverToFolder(audio.id, setOf(foundAudio.single().id), permission.uri.toString())
                assertEquals(1, recoveredAudio.recoveredCount)
                val audioCopies = root.listFiles().filter { it.name.orEmpty().startsWith(prefix) && it.name.orEmpty().endsWith(".mp3") }
                assertEquals(1, audioCopies.size)
                resolver.openInputStream(audioCopies.single().uri)!!.use { assertArrayEquals("ID3-deep-scan".toByteArray(), it.readBytes()) }
            }
        } finally {
            fixtureFolder.delete()
            root.listFiles().filter { it.isFile && it.name.orEmpty().startsWith(prefix) }.forEach { it.delete() }
        }
    }
}
