package com.nexappra.filerecovery.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.PremiumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class MediaRecoveryWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val premium: PremiumRepository,
    private val history: RecoveryHistoryStore,
) {
    private val resolver get() = context.contentResolver

    suspend fun copyToFolder(session: RecoveryScanSession, ids: Set<String>, tree: String): RecoveryCopyResult = withContext(Dispatchers.IO) {
        premium.requirePremium()
        val files = selected(session, ids)
        val destination = DocumentFile.fromTreeUri(context, destinationUri(tree)) ?: error("Choose a writable folder.")
        check(destination.canWrite()) { "This folder is read-only. Choose another folder." }
        var copied = 0
        var skipped = 0
        for (file in files) {
            coroutineContext.ensureActive()
            premium.requirePremium()
            var created: DocumentFile? = null
            try {
                resolver.openInputStream(Uri.parse(file.uriString))?.use { input ->
                    // A short random suffix prevents collisions without repeated directory listings.
                    val safe = MediaScanPolicy.safeFileName(file.displayName)
                    val dot = safe.lastIndexOf('.').takeIf { it > 0 } ?: safe.length
                    val name = safe.take(dot) + "_recovered_" + UUID.randomUUID().toString().take(8) + safe.drop(dot)
                    created = destination.createFile(file.mimeType ?: defaultMime(file), name) ?: throw IOException("Cannot create a file here.")
                    resolver.openOutputStream(created!!.uri, "w")?.use { copyChecked(input, it, file.sizeBytes) }
                        ?: throw IOException("Cannot write to this folder.")
                } ?: throw IOException("The source is no longer available.")
                copied++
            } catch (cancelled: CancellationException) {
                created?.delete()
                throw cancelled
            } catch (_: Exception) {
                created?.delete()
                skipped++
            }
        }
        recordHistory(copied, tree, "Original files")
        RecoveryCopyResult(copied, skipped)
    }

    suspend fun exportArchive(session: RecoveryScanSession, ids: Set<String>, document: String): RecoveryCopyResult = withContext(Dispatchers.IO) {
        premium.requirePremium()
        val files = selected(session, ids)
        val destination = destinationUri(document)
        var copied = 0
        var skipped = 0
        try {
            val output = resolver.openOutputStream(destination, "w") ?: throw IOException("Unable to open the selected destination.")
            ZipOutputStream(output.buffered()).use { archive ->
                files.forEachIndexed { index, file ->
                    coroutineContext.ensureActive()
                    premium.requirePremium()
                    val input = try { resolver.openInputStream(Uri.parse(file.uriString)) } catch (_: IOException) { null } catch (_: SecurityException) { null }
                    if (input == null) { skipped++; return@forEachIndexed }
                    input.use {
                        archive.putNextEntry(ZipEntry("${index + 1}_${MediaScanPolicy.safeFileName(file.displayName)}"))
                        copyChecked(it, archive, file.sizeBytes)
                        archive.closeEntry()
                        copied++
                    }
                }
                check(copied > 0) { "None of the selected source files could be read." }
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(destination, null, null) }
            throw error
        }
        recordHistory(copied, document, "Cloud / ZIP export")
        RecoveryCopyResult(copied, skipped)
    }

    /** A separate, bounded PNG copy; never modifies or promises reconstruction of missing bytes. */
    suspend fun repairPhoto(session: RecoveryScanSession, id: String, document: String): RecoveryCopyResult = withContext(Dispatchers.IO) {
        premium.requirePremium()
        val file = selected(session, setOf(id)).single()
        require(file.fileType == RecoveryFileType.Photo) { "Image repair supports photos only." }
        val destination = destinationUri(document)
        var bitmap: Bitmap? = null
        try {
            val sourceUri = Uri.parse(file.uriString)
            bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, sourceUri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    decoder.setTargetSampleSize(kotlin.math.ceil(maxOf(info.size.width, info.size.height) / 2048.0).toInt().coerceAtLeast(1))
                    decoder.setOnPartialImageListener { error -> error.error == ImageDecoder.DecodeException.SOURCE_INCOMPLETE }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                check(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo cannot be decoded." }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }) }
                    ?: error("This photo cannot be decoded.")
            }
            if (Build.VERSION.SDK_INT < 28) {
                val orientation = resolver.openInputStream(sourceUri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                val matrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                    }
                }
                val decoded = checkNotNull(bitmap)
                if (!matrix.isIdentity) bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
            }
            coroutineContext.ensureActive()
            val image = checkNotNull(bitmap)
            check(image.width > 0 && image.height > 0) { "This image has no readable pixels." }
            resolver.openOutputStream(destination, "w")?.use { output ->
                check(image.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to write the repair copy." }
            } ?: throw IOException("Unable to write the repair copy.")
        } catch (error: Exception) {
            runCatching { resolver.delete(destination, null, null) }
            throw error
        } finally { bitmap?.recycle() }
        recordHistory(1, document, "Repaired PNG copy")
        RecoveryCopyResult(1, 0)
    }

    private fun selected(session: RecoveryScanSession, ids: Set<String>): List<RecoverableFile> {
        require(ids.isNotEmpty()) { "Select at least one photo or video." }
        val files = session.files.filter { it.id in ids && it.fileType in setOf(RecoveryFileType.Photo, RecoveryFileType.Video) }
        require(files.size == ids.size) { "Some selected files no longer belong to this scan." }
        return files
    }

    private suspend fun copyChecked(input: InputStream, output: OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
        }
        if (total == 0L || (expectedBytes > 0 && total != expectedBytes)) throw IOException("The source file is incomplete or changed during recovery.")
    }

    private fun destinationUri(value: String): Uri = Uri.parse(value).also { require(it.scheme == "content") { "Choose a destination through the system file picker." } }
    private fun defaultMime(file: RecoverableFile) = if (file.fileType == RecoveryFileType.Photo) "image/jpeg" else "video/mp4"
    private suspend fun recordHistory(count: Int, destination: String, kind: String) {
        try { history.record(count, destination, kind) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) { /* Files are already saved; a full metadata store must not undo recovery. */ }
    }
}
