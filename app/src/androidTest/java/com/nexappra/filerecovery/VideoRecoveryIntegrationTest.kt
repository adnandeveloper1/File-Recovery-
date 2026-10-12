package com.nexappra.filerecovery

import android.content.ContentValues
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexappra.filerecovery.data.repository.*
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.PremiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** Real video fixtures exercise gallery exclusion and shared-storage recovery together. */
@RunWith(AndroidJUnit4::class)
class VideoRecoveryIntegrationTest {
    private val prefix = "video_recovery_" + UUID.randomUUID()
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDirectory = File(appContext.cacheDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(appContext) {
        override fun getCacheDir() = testDirectory
        override fun getFilesDir() = testDirectory
    }
    private val resolver = context.contentResolver
    private val created = mutableListOf<Uri>()
    private val root = Environment.getExternalStorageDirectory()
    private val fixtureDirectory = File(root, "Movies/$prefix")
    private val premium = object : PremiumRepository {
        override val state = MutableStateFlow(BillingState(access = PremiumAccess(true, Long.MAX_VALUE)))
        override fun refresh() {}
    }
    private val store = ScanSessionStore(context)
    private val writer = MediaRecoveryWriter(context, premium, RecoveryHistoryStore(context))
    private val repository = MediaStoreRecoveryScanRepository(context, store, premium, writer)
    private val videoBytes by lazy { RecoveryVideoFixtures.video(testDirectory) }
    private val audioBytes by lazy { RecoveryVideoFixtures.audioOnlyMp4(testDirectory) }

    @Before fun requireSharedStorageAccess() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 30)
        Assume.assumeTrue("Grant All Files access on the QA device", Environment.isExternalStorageManager())
        assertTrue(fixtureDirectory.canonicalPath.startsWith(File(root, "Movies").canonicalPath + "/"))
        assertTrue(fixtureDirectory.mkdirs())
    }

    @After fun cleanup() {
        created.forEach { uri -> runCatching {
            resolver.delete(uri, Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) })
        } }
        fixtureDirectory.deleteRecursively()
        testDirectory.deleteRecursively()
    }

    private fun rawVideo(path: String, bytes: ByteArray = videoBytes): File = File(fixtureDirectory, path).apply {
        parentFile!!.mkdirs()
        writeBytes(bytes)
    }

    private fun indexedVideo(folder: String, name: String, trashed: Boolean = false): Uri {
        val uri = checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/$prefix/$folder/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        created += uri
        resolver.openOutputStream(uri)!!.use { it.write(videoBytes) }
        assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null))
        if (trashed) assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null))
        return uri
    }

    @Test fun quickAndDeepExcludeGalleryVideosAndKeepOnlyReadableRecoverySources() = runBlocking {
        indexedVideo("Gallery", "_gallery.mp4")
        val indexedRecycle = indexedVideo("Trash", "_indexed_trash.mp4")
        indexedVideo("ProviderTrash", "_provider_trash.mp4", trashed = true)
        val hidden = rawVideo(".private/${prefix}_hidden.mp4")
        val recycled = rawVideo(".Trash/${prefix}_recycle.mp4")
        File(fixtureDirectory, "Private/.nomedia").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf()) }
        val noMedia = rawVideo("Private/${prefix}_nomedia.mp4")
        val nested = rawVideo("Private/Nested/${prefix}_nested.mp4")
        val deep = rawVideo("Private/One/Two/Three/Four/Five/Six/${prefix}_deep.mp4")
        val preview = rawVideo(".thumbnails/${prefix}_preview.mp4")
        val audioOnly = rawVideo(".private/${prefix}_audio_only.mp4", audioBytes)
        val invalid = rawVideo(".private/${prefix}_invalid.mp4", ByteArray(64) { 1 })
        resolver.query(indexedRecycle, arrayOf(MediaStore.MediaColumns.IS_TRASHED), null, null, null)!!.use {
            assertTrue("The indexed Trash-folder fixture is a visible video", it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        val quick = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Quick) {}
        val full = repository.performFullDeviceScan(RecoveryCategory.Videos, RecoveryScanMode.Deep) {}
        val quickFiles = quick.files.filter { it.displayName.startsWith(prefix) }
        val deepFiles = full.files.filter { it.displayName.startsWith(prefix) }
        assertEquals("Trash + hidden + recycle + .nomedia + nested; warnings=${quick.warnings}", 5, quickFiles.size)
        assertEquals("Deep adds the video beyond Quick's depth limit; warnings=${full.warnings}", 6, deepFiles.size)
        listOf(quick, full).forEach { scan ->
            assertFalse(scan.files.any { it.displayName == prefix + "_gallery.mp4" || it.displayName == prefix + "_indexed_trash.mp4" })
            assertFalse(scan.files.any { it.displayName in setOf(preview.name, audioOnly.name, invalid.name) })
            assertTrue("Expected confirmed provider trash; found=${scan.files.filter { it.displayName.contains(prefix) }.map { Triple(it.displayName, it.uriString, it.isTrashed) }}",
                scan.files.any { it.displayName == prefix + "_provider_trash.mp4" && it.isTrashed })
            assertTrue(scan.files.any { it.displayName == hidden.name })
            assertTrue(scan.files.any { it.displayName == recycled.name })
            assertTrue(scan.files.any { it.displayName == noMedia.name && it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
            assertTrue(scan.files.any { it.displayName == nested.name && it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
            assertTrue(scan.files.all { it.fileType == RecoveryFileType.Video && VideoRecoveryPolicy.accepts(it) })
        }
        assertFalse(quickFiles.any { it.displayName == deep.name })
        assertTrue(deepFiles.any { it.displayName == deep.name })
        assertEquals(quick.files, ScanSessionStore(context).load(quick.id)!!.files)

        val zip = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + ".zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        }))
        created += zip
        val source = quickFiles.single { it.displayName == hidden.name }
        assertEquals(1, repository.exportArchive(quick.id, setOf(source.id), zip.toString()).recoveredCount)
        ZipInputStream(resolver.openInputStream(zip)).use { stream ->
            assertNotNull(stream.nextEntry); assertArrayEquals(videoBytes, stream.readBytes())
        }
        assertArrayEquals(videoBytes, hidden.readBytes())
    }

    @Test fun savedVideoResultsAreRecheckedAgainstTheCurrentLibrary() = runBlocking {
        val gallery = indexedVideo("Trash", "_old_result.mp4")
        val hidden = rawVideo(".private/${prefix}_hidden.mp4")
        val hiddenFile = RecoverableFile(checkNotNull(MediaFileIdentity.absolute(hidden.path, root.path)), Uri.fromFile(hidden).toString(),
            hidden.name, "video/mp4", hidden.length(), 0, "Movies/$prefix/.private", RecoveryFileType.Video,
            setOf(RecoveryFileSource.Hidden), true, RecoveryFileHiddenReason.HiddenDirectory, false, "external_primary")
        val staleFile = hiddenFile.copy(id = "old-library-video", uriString = gallery.toString(), displayName = prefix + "_old_result.mp4",
            relativePath = "Movies/$prefix/Trash", isInVideoLibrary = false)
        val old = RecoveryScanSession(UUID.randomUUID().toString(), RecoveryScanMode.Quick, RecoveryCategory.Videos,
            1, hidden.length() * 2, listOf(staleFile, hiddenFile), emptyList(), System.currentTimeMillis())
        store.save(old)
        val rechecked = checkNotNull(repository.getSession(old.id))
        assertEquals(listOf(staleFile.id, hiddenFile.id), rechecked.files.map { it.id })
        assertEquals(listOf(hiddenFile.id), rechecked.files.filter(VideoRecoveryPolicy::accepts).map { it.id })
        assertEquals(true, rechecked.files.first().isInVideoLibrary)
        assertEquals(false, rechecked.files.last().isInVideoLibrary)
    }
}
