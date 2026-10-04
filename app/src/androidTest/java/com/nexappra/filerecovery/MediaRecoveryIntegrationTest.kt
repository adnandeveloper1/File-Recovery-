package com.nexappra.filerecovery

import android.content.ContentValues
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private val writer = MediaRecoveryWriter(context, premium, RecoveryHistoryStore(context))
    private val repository = MediaStoreRecoveryScanRepository(context, store, premium, writer)
    private val imageBytes = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.GREEN); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        }; output.toByteArray()
    }
    private fun seed(collection: Uri, name: String, mime: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + name); put(MediaStore.MediaColumns.MIME_TYPE, mime) }
        val uri = checkNotNull(resolver.insert(collection, values)); created += uri
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }
    @After fun cleanup() {
        created.forEach { runCatching { resolver.delete(it, null, null) } }
        testDirectory.deleteRecursively()
    }
    @Test fun indexedScansAreSelectiveAndPublishResults() = runBlocking {
        seed(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ".png", "image/png", imageBytes)
        seed(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ".mp4", "video/mp4", ByteArray(64) { 1 })
        val snapshots = mutableListOf<RecoveryScanSnapshot>()
        val photos = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) { snapshots += it }
        assertTrue(photos.files.any { it.displayName.startsWith(prefix) })
        assertTrue(photos.files.all { it.fileType == RecoveryFileType.Photo })
        assertTrue(snapshots.any { it.previewFiles.isNotEmpty() })
        val videos = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Quick) {}
        assertTrue(videos.files.any { it.displayName.startsWith(prefix) })
        assertTrue(videos.files.all { it.fileType == RecoveryFileType.Video })
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
}
