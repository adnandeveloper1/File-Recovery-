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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipInputStream

/** Real AAC, PCM and invalid fixtures cover recovery without touching ordinary library contents. */
@RunWith(AndroidJUnit4::class)
class AudioRecoveryIntegrationTest {
    private val prefix = "audio_recovery_" + UUID.randomUUID()
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDirectory = File(appContext.cacheDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(appContext) {
        override fun getCacheDir() = testDirectory
        override fun getFilesDir() = testDirectory
    }
    private val resolver = context.contentResolver
    private val created = mutableListOf<Uri>()
    private val root = Environment.getExternalStorageDirectory()
    private val fixtureDirectory = File(root, "Music/$prefix")
    private val premium = object : PremiumRepository {
        override val state = MutableStateFlow(BillingState(access = PremiumAccess(true, Long.MAX_VALUE)))
        override fun refresh() {}
    }
    private val store = ScanSessionStore(context)
    private val repository = MediaStoreRecoveryScanRepository(context, store, premium,
        MediaRecoveryWriter(context, premium, RecoveryHistoryStore(context)))
    private val audioBytes by lazy { RecoveryVideoFixtures.audioOnlyMp4(testDirectory) }
    private val videoBytes by lazy { RecoveryVideoFixtures.video(testDirectory) }
    private val wavBytes = ByteBuffer.allocate(44 + 512).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + 512); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(44_100); putInt(88_200); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(512); put(ByteArray(512))
    }.array()

    @Before fun requireSharedStorageAccess() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 30)
        Assume.assumeTrue("Grant All Files access on the QA device", Environment.isExternalStorageManager())
        assertTrue(fixtureDirectory.canonicalPath.startsWith(File(root, "Music").canonicalPath + "/"))
        assertTrue(fixtureDirectory.mkdirs())
    }

    @After fun cleanup() {
        created.forEach { uri -> runCatching {
            resolver.delete(uri, Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) })
        } }
        fixtureDirectory.deleteRecursively()
        testDirectory.deleteRecursively()
    }

    private fun rawAudio(path: String, bytes: ByteArray = audioBytes): File = File(fixtureDirectory, path).apply {
        parentFile!!.mkdirs()
        writeBytes(bytes)
    }

    private fun indexedAudio(folder: String, name: String, trashed: Boolean = false): Uri {
        val uri = checkNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/$prefix/$folder/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        created += uri
        resolver.openOutputStream(uri)!!.use { it.write(audioBytes) }
        assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null))
        if (trashed) assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null))
        return uri
    }

    @Test fun quickAndDeepExcludeLibraryAudioAndKeepReadableHiddenAndTrashCopies() = runBlocking {
        indexedAudio("Library", "_library.m4a")
        val indexedRecycle = indexedAudio("Trash", "_indexed_trash.m4a")
        indexedAudio("ProviderTrash", "_provider_trash.m4a", trashed = true)
        val hidden = rawAudio(".private/${prefix}_hidden.m4a")
        val recycled = rawAudio(".Trash/${prefix}_recycle.m4a")
        File(fixtureDirectory, "Private/.nomedia").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf()) }
        val noMedia = rawAudio("Private/${prefix}_nomedia.m4a")
        val nested = rawAudio("Private/Nested/${prefix}_nested.m4a")
        val cache = rawAudio("Private/${prefix}_hidden.cache")
        val wav = rawAudio(".private/${prefix}_voice.wav", wavBytes)
        val deep = rawAudio("Private/One/Two/Three/Four/Five/Six/${prefix}_deep.m4a")
        val wrongVideo = rawAudio(".private/${prefix}_video.m4a", videoBytes)
        val invalid = rawAudio(".private/${prefix}_invalid.m4a", ByteArray(64) { 1 })
        val document = rawAudio(".private/${prefix}_document.mp3", "%PDF-1.7\nnot an audio file".toByteArray())
        resolver.query(indexedRecycle, arrayOf(MediaStore.MediaColumns.IS_TRASHED), null, null, null)!!.use {
            assertTrue("The indexed Trash-folder fixture is ordinary library audio", it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        val quick = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Quick) {}
        val full = repository.performFullDeviceScan(RecoveryCategory.Audio, RecoveryScanMode.Deep) {}
        val quickFiles = quick.files.filter { it.displayName.startsWith(prefix) }
        val deepFiles = full.files.filter { it.displayName.startsWith(prefix) }
        assertEquals("Trash + hidden + recycle + .nomedia + nested + cache + WAV; warnings=${quick.warnings}", 7, quickFiles.size)
        assertEquals("Deep adds the recording beyond Quick's depth limit; warnings=${full.warnings}", 8, deepFiles.size)
        listOf(quick, full).forEach { scan ->
            assertFalse(scan.files.any { it.displayName == prefix + "_library.m4a" || it.displayName == prefix + "_indexed_trash.m4a" })
            assertFalse(scan.files.any { it.displayName in setOf(wrongVideo.name, invalid.name, document.name) })
            assertTrue("Expected confirmed provider trash; found=${scan.files.filter { it.displayName.contains(prefix) }.map { Triple(it.displayName, it.uriString, it.isTrashed) }}",
                scan.files.any { it.displayName == prefix + "_provider_trash.m4a" && it.isTrashed })
            assertTrue(scan.files.any { it.displayName == hidden.name })
            assertTrue(scan.files.any { it.displayName == recycled.name })
            assertTrue(scan.files.any { it.displayName == noMedia.name && it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
            assertTrue(scan.files.any { it.displayName == nested.name && it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
            assertTrue(scan.files.any { it.displayName == cache.name && it.mimeType == "audio/mp4" })
            assertTrue(scan.files.any { it.displayName == wav.name && it.mimeType == "audio/wav" })
            assertTrue(scan.files.all { it.fileType == RecoveryFileType.Audio && AudioRecoveryPolicy.accepts(it) })
        }
        assertFalse(quickFiles.any { it.displayName == deep.name })
        assertTrue(deepFiles.any { it.displayName == deep.name })
        assertEquals(quick.files, ScanSessionStore(context).load(quick.id)!!.files)

        val zip = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + ".zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        }))
        created += zip
        val sources = quickFiles.filter { it.displayName == hidden.name || it.displayName == wav.name }
        assertEquals(2, repository.exportArchive(quick.id, sources.map { it.id }.toSet(), zip.toString()).recoveredCount)
        ZipInputStream(resolver.openInputStream(zip)).use { stream ->
            repeat(2) {
                val entry = checkNotNull(stream.nextEntry)
                assertArrayEquals(if (entry.name.contains("_voice")) wavBytes else audioBytes, stream.readBytes())
            }
            assertNull(stream.nextEntry)
        }
        assertArrayEquals(audioBytes, hidden.readBytes())
        assertArrayEquals(wavBytes, wav.readBytes())
    }

    @Test fun savedAudioResultsAreRecheckedAgainstTheCurrentLibrary() = runBlocking {
        val library = indexedAudio("Trash", "_old_result.m4a")
        val hidden = rawAudio(".private/${prefix}_hidden.m4a")
        val hiddenFile = RecoverableFile(checkNotNull(MediaFileIdentity.absolute(hidden.path, root.path)), Uri.fromFile(hidden).toString(),
            hidden.name, "audio/mp4", hidden.length(), 0, "Music/$prefix/.private", RecoveryFileType.Audio,
            setOf(RecoveryFileSource.Hidden), true, RecoveryFileHiddenReason.HiddenDirectory, false, "external_primary")
        val staleFile = hiddenFile.copy(id = "old-library-audio", uriString = library.toString(), displayName = prefix + "_old_result.m4a",
            relativePath = "Music/$prefix/Trash", isInAudioLibrary = false)
        val old = RecoveryScanSession(UUID.randomUUID().toString(), RecoveryScanMode.Quick, RecoveryCategory.Audio,
            1, hidden.length() * 2, listOf(staleFile, hiddenFile), emptyList(), System.currentTimeMillis())
        store.save(old)
        val rechecked = checkNotNull(repository.getSession(old.id))
        assertEquals(listOf(staleFile.id, hiddenFile.id), rechecked.files.map { it.id })
        assertEquals(listOf(hiddenFile.id), rechecked.files.filter(AudioRecoveryPolicy::accepts).map { it.id })
        assertEquals(true, rechecked.files.first().isInAudioLibrary)
        assertEquals(false, rechecked.files.last().isInAudioLibrary)
    }
}
