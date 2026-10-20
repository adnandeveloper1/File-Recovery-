package com.nexappra.filerecovery

import android.content.ContentValues
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** Actual shared-storage and provider fixtures; ordinary gallery contents are never decoded. */
@RunWith(AndroidJUnit4::class)
class PhotoRecoveryIntegrationTest {
    private val prefix = "photo_recovery_" + UUID.randomUUID()
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDirectory = File(appContext.cacheDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(appContext) {
        override fun getCacheDir() = testDirectory
        override fun getFilesDir() = testDirectory
    }
    private val resolver = context.contentResolver
    private val created = mutableListOf<Uri>()
    private val root = Environment.getExternalStorageDirectory()
    private val fixtureDirectory = File(root, "Pictures/$prefix")
    private val premium = object : PremiumRepository {
        override val state = MutableStateFlow(BillingState(access = PremiumAccess(true, Long.MAX_VALUE)))
        override fun refresh() {}
    }
    private val store = ScanSessionStore(context)
    private val writer = MediaRecoveryWriter(context, premium, RecoveryHistoryStore(context))
    private val repository = MediaStoreRecoveryScanRepository(context, store, premium, writer)
    private val imageBytes = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.BLUE)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
        }
        output.toByteArray()
    }

    @Before fun requireSharedStorageAccess() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 30)
        Assume.assumeTrue("Grant All Files access on the QA device", Environment.isExternalStorageManager())
        assertTrue(fixtureDirectory.canonicalPath.startsWith(File(root, "Pictures").canonicalPath + "/"))
        assertTrue(fixtureDirectory.mkdirs())
    }

    @After fun cleanup() {
        created.forEach { uri -> runCatching {
            resolver.delete(uri, Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) })
        } }
        fixtureDirectory.deleteRecursively()
        testDirectory.deleteRecursively()
    }

    private fun rawPhoto(path: String): File = File(fixtureDirectory, path).apply {
        parentFile!!.mkdirs()
        writeBytes(imageBytes)
    }

    private fun indexedPhoto(folder: String, name: String, trashed: Boolean = false): Uri {
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$prefix/$folder/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        created += uri
        resolver.openOutputStream(uri)!!.use { it.write(imageBytes) }
        assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null))
        if (trashed) assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null))
        return uri
    }

    @Test fun quickAndDeepExcludeGalleryAndThumbnailsWhileKeepingHiddenAndTrashedPhotos() = runBlocking {
        indexedPhoto("Gallery", "_gallery.png")
        val indexedRecycle = indexedPhoto("Trash", "_indexed_trash.png")
        indexedPhoto("ProviderTrash", "_provider_trash.png", trashed = true)
        val hidden = rawPhoto(".private/${prefix}_hidden.png")
        val recycled = rawPhoto(".Trash/${prefix}_recycle.png")
        File(fixtureDirectory, "Private/.nomedia").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf()) }
        val noMedia = rawPhoto("Private/${prefix}_nomedia.png")
        val nested = rawPhoto("Private/Nested/${prefix}_nested.png")
        val deep = rawPhoto("Private/One/Two/Three/Four/Five/Six/${prefix}_deep.png")
        val thumbnail = rawPhoto(".thumbnails/${prefix}_thumbnail.png")
        resolver.query(indexedRecycle, arrayOf(MediaStore.MediaColumns.IS_TRASHED), null, null, null)!!.use {
            assertTrue("The indexed recycle-folder fixture is a visible photo", it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        val quick = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Quick) {}
        val full = repository.performFullDeviceScan(RecoveryCategory.Photos, RecoveryScanMode.Deep) {}
        val quickFiles = quick.files.filter { it.displayName.startsWith(prefix) }
        val deepFiles = full.files.filter { it.displayName.startsWith(prefix) }
        assertEquals("Trash + hidden + recycle + .nomedia + nested; warnings=${quick.warnings}", 5, quickFiles.size)
        assertEquals("Deep adds the photo beyond Quick's depth limit; warnings=${full.warnings}", 6, deepFiles.size)
        listOf(quick, full).forEach { scan ->
            // The provider's merged "external" URI and the volume URI can identify the same photo.
            assertFalse(scan.files.any { it.displayName == prefix + "_gallery.png" || it.displayName == prefix + "_indexed_trash.png" })
            assertFalse(scan.files.any { it.displayName == thumbnail.name })
            assertTrue("Expected confirmed provider trash; found=${scan.files.filter { it.displayName.contains(prefix) }.map { Triple(it.displayName, it.uriString, it.isTrashed) }}",
                scan.files.any { it.displayName == prefix + "_provider_trash.png" && it.isTrashed })
            assertTrue(scan.files.any { it.displayName == hidden.name })
            assertTrue(scan.files.any { it.displayName == recycled.name })
            assertTrue(scan.files.any { it.displayName == noMedia.name && it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
            assertTrue(scan.files.any { it.displayName == nested.name && it.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory })
            assertTrue(scan.files.all(PhotoRecoveryPolicy::accepts))
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
            assertNotNull(stream.nextEntry); assertArrayEquals(imageBytes, stream.readBytes())
        }
        assertArrayEquals(imageBytes, hidden.readBytes())
    }

    @Test fun savedPhotoResultsAreRecheckedAgainstTheCurrentLibrary() = runBlocking {
        val gallery = indexedPhoto("Trash", "_old_result.png")
        val hidden = rawPhoto(".private/${prefix}_hidden.png")
        val hiddenFile = RecoverableFile(checkNotNull(MediaFileIdentity.absolute(hidden.path, root.path)), Uri.fromFile(hidden).toString(),
            hidden.name, "image/png", hidden.length(), 0, "Pictures/$prefix/.private", RecoveryFileType.Photo,
            setOf(RecoveryFileSource.Hidden), true, RecoveryFileHiddenReason.HiddenDirectory, false, "external_primary")
        val staleFile = hiddenFile.copy(id = "old-library-result", uriString = gallery.toString(), displayName = prefix + "_old_result.png",
            relativePath = "Pictures/$prefix/Trash", isInPhotoLibrary = false)
        val old = RecoveryScanSession(UUID.randomUUID().toString(), RecoveryScanMode.Quick, RecoveryCategory.Photos,
            1, hidden.length() * 2, listOf(staleFile, hiddenFile), emptyList(), System.currentTimeMillis())
        store.save(old)
        val rechecked = checkNotNull(repository.getSession(old.id))
        assertEquals(listOf(staleFile.id, hiddenFile.id), rechecked.files.map { it.id })
        assertEquals(listOf(hiddenFile.id), rechecked.files.filter(PhotoRecoveryPolicy::accepts).map { it.id })
        assertEquals(true, rechecked.files.first().isInPhotoLibrary)
        assertEquals(false, rechecked.files.last().isInPhotoLibrary)
    }

    @Test fun filesTrashFallbackFindsRenamedPhotosWithMissingSizeAndRejectsGalleryAndOtherFormats() = runBlocking {
        data class Row(val id: Long, val file: File, val mime: String?, val size: Long?, val trashed: Boolean, val hasPath: Boolean = true)
        val renamed = rawPhoto("Provider/${prefix}_renamed")
        val zeroSize = rawPhoto("Provider/${prefix}_zero.cache")
        val gallery = rawPhoto("Provider/${prefix}_gallery.png")
        val empty = File(fixtureDirectory, "Provider/${prefix}_empty.png").apply { writeBytes(byteArrayOf()) }
        val pdf = File(fixtureDirectory, "Provider/${prefix}_document.jpg").apply { writeText("%PDF-1.7 not an image") }
        val unknown = Row(1, renamed, null, null, true)
        val zero = Row(2, zeroSize, "application/octet-stream", 0, true)
        val visible = Row(3, gallery, "image/png", gallery.length(), false)
        val staleTrashAlias = visible.copy(id = 4, trashed = true)
        val rows = listOf(unknown, zero, visible, staleTrashAlias, visible.copy(trashed = true, hasPath = false),
            Row(5, empty, "image/png", 0, true), Row(6, pdf, "application/pdf", pdf.length(), true),
            Row(7, pdf, "video/mp4", pdf.length(), true))
        val queries = java.util.Collections.synchronizedList(mutableListOf<Pair<Uri, Bundle>>())
        val opened = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                error("Use the bundle query so trash filtering can be checked")
            override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor {
                val args = Bundle(queryArgs ?: Bundle())
                queries += uri to args
                val trashOnly = args.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED) == MediaStore.MATCH_ONLY
                val selected = when {
                    !trashOnly -> listOf(visible)
                    "file" in uri.pathSegments -> rows
                    else -> listOf(zero)
                }
                val columns = checkNotNull(projection)
                return MatrixCursor(columns).apply {
                    selected.forEach { row ->
                        val values = mapOf<String, Any?>("_id" to row.id, "_display_name" to row.file.name, "mime_type" to row.mime,
                            "_size" to row.size, "date_modified" to 0, "_data" to row.file.path.takeIf { row.hasPath },
                            "relative_path" to "Pictures/$prefix/Provider/".takeIf { row.hasPath }, "is_trashed" to if (row.trashed) 1 else 0, "is_pending" to 0)
                        addRow(columns.map { values[it] }.toTypedArray())
                    }
                }
            }
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                if (uri.pathSegments.firstOrNull() == "qa_export") {
                    val name = checkNotNull(uri.lastPathSegment)
                    require(name in setOf("Quick.zip", "Deep.zip"))
                    return ParcelFileDescriptor.open(File(testDirectory, name), ParcelFileDescriptor.parseMode(mode))
                }
                val id = checkNotNull(uri.lastPathSegment).toLong()
                opened += id
                return ParcelFileDescriptor.open(rows.single { it.id == id }.file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            override fun getType(uri: Uri) = if (uri.pathSegments.firstOrNull() == "qa_export") "application/zip" else "image/png"
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only fixture")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only fixture")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only fixture")
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        val fixtureResolver = ContentResolver.wrap(provider)
        val fixtureContext = object : ContextWrapper(context) { override fun getContentResolver() = fixtureResolver }
        val fixtureRepository = MediaStoreRecoveryScanRepository(fixtureContext, store, premium,
            MediaRecoveryWriter(fixtureContext, premium, RecoveryHistoryStore(fixtureContext)))
        listOf(RecoveryScanMode.Quick, RecoveryScanMode.Deep).forEach { mode ->
            val scan = fixtureRepository.performFullDeviceScan(RecoveryCategory.Photos, mode) {}
            val found = scan.files.filter { it.displayName.startsWith(prefix) }
            assertEquals("Only the readable renamed trash photos; warnings=${scan.warnings}", setOf(renamed.name, zeroSize.name), found.map { it.displayName }.toSet())
            assertEquals(2, found.size)
            assertTrue(found.all { it.isTrashed && it.mimeType == "image/png" && it.sizeBytes == imageBytes.size.toLong() })
            assertFalse(opened.any { it in setOf(3L, 4L, 6L, 7L) })
            assertTrue(queries.any { (uri, args) -> "file" in uri.pathSegments && args.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED) == MediaStore.MATCH_ONLY &&
                args.getInt(MediaStore.QUERY_ARG_MATCH_PENDING) == MediaStore.MATCH_EXCLUDE })
            val zip = File(testDirectory, "${mode.name}.zip")
            assertEquals(2, fixtureRepository.exportArchive(scan.id, found.map { it.id }.toSet(), "content://media/qa_export/${mode.name}.zip").recoveredCount)
            ZipInputStream(zip.inputStream()).use { stream ->
                repeat(2) { assertNotNull(stream.nextEntry); assertArrayEquals(imageBytes, stream.readBytes()) }
                assertNull(stream.nextEntry)
            }
        }
        assertArrayEquals(imageBytes, renamed.readBytes())
        assertArrayEquals(imageBytes, zeroSize.readBytes())
    }

    @Test fun quickAndDeepReachAnAccessibleAndroidRecycleFolder() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("shared_trash_fixture")
        Assume.assumeTrue("Supply an isolated shared-trash fixture from the QA runner", !path.isNullOrBlank())
        val photo = File(checkNotNull(path))
        assertTrue(photo.canonicalPath.startsWith(File(root, "Android/.Trash").canonicalPath + "/photo_recovery_qa_"))
        assertTrue("The fixture must be readable with All Files access", photo.isFile && photo.canRead())
        listOf(RecoveryScanMode.Quick, RecoveryScanMode.Deep).forEach { mode ->
            val scan = repository.performFullDeviceScan(RecoveryCategory.Photos, mode) {}
            val found = scan.files.singleOrNull { it.displayName == photo.name }
            assertNotNull("$mode must reach the Android recycle folder; warnings=${scan.warnings}", found)
            assertTrue(RecoveryFileSource.RecycleBin in checkNotNull(found).sources)
            assertEquals("image/png", found.mimeType)
        }
    }
}
