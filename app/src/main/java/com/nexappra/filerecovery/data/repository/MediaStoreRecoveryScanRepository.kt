package com.nexappra.filerecovery.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.PremiumRepository
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

@Singleton
class MediaStoreRecoveryScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ScanSessionStore,
    private val premium: PremiumRepository,
    private val writer: MediaRecoveryWriter,
) : RecoveryScanRepository {
    private val sessions = ConcurrentHashMap<String, RecoveryScanSession>()
    private val resolver get() = context.contentResolver
    private val queryExecutor = Executors.newFixedThreadPool(2) { task -> Thread(task, "MediaQuery").apply { isDaemon = true } }

    private fun locationFor(type: RecoveryFileType) = when (type) {
        RecoveryFileType.Photo -> RecoveryScanLocationType.MediaStoreImages
        RecoveryFileType.Video -> RecoveryScanLocationType.MediaStoreVideos
        RecoveryFileType.Audio -> RecoveryScanLocationType.MediaStoreAudio
        else -> error("Unsupported media type: $type")
    }

    override suspend fun performFullDeviceScan(
        selectedCategory: RecoveryCategory?,
        mode: RecoveryScanMode,
        onSnapshot: suspend (RecoveryScanSnapshot) -> Unit,
    ): RecoveryScanSession = withContext(Dispatchers.IO) {
        val policy = MediaScanPolicy(selectedCategory)
        if (mode == RecoveryScanMode.Deep) premium.requirePremium()
        val id = UUID.randomUUID().toString()
        val started = SystemClock.elapsedRealtime()
        val files = LinkedHashMap<String, RecoverableFile>()
        val warnings = linkedSetOf<String>()
        val stages = policy.types.map { locationFor(it) } +
            if (mode == RecoveryScanMode.Deep) listOf(RecoveryScanLocationType.AuthorizedFolders, RecoveryScanLocationType.AccessibleStorage) else emptyList()
        var locations = stages.map { RecoveryScanLocationState(it) }
        var currentStage = stages.first()
        var inspected = 0
        var bytes = 0L
        var lastPublish = 0L
        var partial = false
        val failedStages = mutableSetOf<RecoveryScanLocationType>()

        fun readFailed(error: Exception) {
            partial = true
            failedStages += currentStage
            warnings += if (error is SecurityException) "Some locations need additional access. Results include the files Android allowed this app to read."
                else "One storage location could not be read. Other available results are shown."
        }

        suspend fun readLocation(action: suspend () -> Unit) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { readFailed(error) }
        }

        var photoCount = 0
        var videoCount = 0
        var audioCount = 0
        var hiddenPhotoCount = 0
        var hiddenVideoCount = 0

        fun session() = RecoveryScanSession(id, mode, selectedCategory, SystemClock.elapsedRealtime() - started,
            bytes, files.values.toList(), locations, System.currentTimeMillis(), partial, warnings.toList())

        suspend fun publish(force: Boolean = false, complete: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastPublish < 250L) return
            lastPublish = now
            if (complete) sessions[id] = session()
            val counts = mapOf(RecoveryResultFilter.All to files.size,
                RecoveryResultFilter.Photos to photoCount,
                RecoveryResultFilter.Videos to videoCount,
                RecoveryResultFilter.Audio to audioCount)
            val completedCount = locations.count { it.status == ScanLocationStatus.Completed }
            val currentProgress = if (complete) 1f else {
                val base = if (stages.isNotEmpty()) completedCount.toFloat() / stages.size else 0f
                val step = if (stages.isNotEmpty()) 1f / stages.size else 0.5f
                val factor = 1f - 1f / (1f + (inspected.toFloat() / 250f))
                (base + step * factor).coerceIn(0.05f, 0.98f)
            }
            onSnapshot(RecoveryScanSnapshot(mode, selectedCategory,
                if (complete) { if (files.isEmpty()) RecoveryScanStatus.CompletedEmpty else RecoveryScanStatus.Completed } else RecoveryScanStatus.Scanning,
                currentProgress, inspected, files.size,
                hiddenPhotoCount, hiddenVideoCount, bytes, null,
                locations.firstOrNull { it.type == currentStage }, locations,
                completedCount, counts, now - started,
                sessionId = id, previewFiles = files.values.take(18).toList()))
        }

        suspend fun record(file: RecoverableFile) {
            inspected++
            if (file.sizeBytes < 0 || (file.sizeBytes == 0L && file.fileType != RecoveryFileType.Audio) || file.fileType !in policy.types) {
                if (inspected % 500 == 0) coroutineContext.ensureActive()
                return
            }
            if (files.putIfAbsent(file.id, file) == null) {
                bytes += file.sizeBytes
                when (file.fileType) {
                    RecoveryFileType.Photo -> { photoCount++; if (file.isHidden) hiddenPhotoCount++ }
                    RecoveryFileType.Video -> { videoCount++; if (file.isHidden) hiddenVideoCount++ }
                    RecoveryFileType.Audio -> audioCount++
                    else -> {}
                }
            }
            if (inspected % 50 == 0) {
                coroutineContext.ensureActive()
                publish()
            }
        }

        suspend fun stage(type: RecoveryScanLocationType, action: suspend () -> Unit) {
            currentStage = type
            locations = locations.map { if (it.type == type) it.copy(status = ScanLocationStatus.Scanning) else it }
            publish(force = true)
            val before = files.size
            readLocation(action)
            locations = locations.map { if (it.type == type) it.copy(status = if (type !in failedStages) ScanLocationStatus.Completed else ScanLocationStatus.Failed, foundCount = files.size - before) else it }
            publish(force = true)
        }

        try {
            publish(force = true)
            val completed = withTimeoutOrNull(3_600_000L) {
                policy.types.forEach { type ->
                    stage(locationFor(type)) {
                        val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context).toList() else listOf("external")
                        volumes.forEach { volume ->
                            readLocation { scanMediaCollection(volume, type, policy, ::record) }
                        }
                    }
                }
                if (mode == RecoveryScanMode.Deep) {
                    stage(RecoveryScanLocationType.AuthorizedFolders) {
                        resolver.persistedUriPermissions.filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
                            .forEach { permission -> readLocation { scanTree(permission.uri, policy, ::readFailed, ::record) } }
                    }
                    stage(RecoveryScanLocationType.AccessibleStorage) {
                        scanDirectories(policy, ::readFailed, ::record)
                    }
                }
                true
            } ?: false
            if (!completed) { partial = true; warnings += "The scan reached its time limit. Found files are ready; scan a specific folder for more results." }
            try { store.save(session()) } catch (_: java.io.IOException) { warnings += "Results are available now but could not be kept after closing the app." }
            val result = session()
            sessions[id] = result
            sessions.keys.filter { it != id }.take((sessions.size - 3).coerceAtLeast(0)).forEach(sessions::remove)
            publish(force = true, complete = true)
            result
        } catch (cancelled: CancellationException) {
            partial = true
            val result = session()
            sessions[id] = result
            withContext(NonCancellable + Dispatchers.IO) { try { store.save(result) } catch (_: java.io.IOException) { /* Keep the in-memory partial session. */ } }
            throw cancelled
        }
    }

    private suspend fun scanMediaCollection(volume: String, type: RecoveryFileType, policy: MediaScanPolicy, record: suspend (RecoverableFile) -> Unit) {
        val uri = when (type) {
            RecoveryFileType.Photo -> MediaStore.Images.Media.getContentUri(volume)
            RecoveryFileType.Video -> MediaStore.Video.Media.getContentUri(volume)
            RecoveryFileType.Audio -> MediaStore.Audio.Media.getContentUri(volume)
            else -> error("Unsupported media type: $type")
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = mutableListOf("_id", "_display_name", "mime_type", "_size", "date_modified", pathColumn)
        if (Build.VERSION.SDK_INT >= 30) projection += MediaStore.MediaColumns.IS_TRASHED
        if (type == RecoveryFileType.Video) projection += MediaStore.Video.VideoColumns.DURATION
        if (type == RecoveryFileType.Audio) projection += MediaStore.Audio.AudioColumns.DURATION
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.SIZE} > 0")
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            if (Build.VERSION.SDK_INT >= 30) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        (query(uri, projection.toTypedArray(), args) ?: throw java.io.IOException("Media query failed")).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("_id")
            val nameIndex = cursor.getColumnIndexOrThrow("_display_name")
            val mimeIndex = cursor.getColumnIndexOrThrow("mime_type")
            val sizeIndex = cursor.getColumnIndexOrThrow("_size")
            val dateIndex = cursor.getColumnIndexOrThrow("date_modified")
            val pathIndex = cursor.getColumnIndex(pathColumn)
            val trashIndex = cursor.getColumnIndex("is_trashed")
            val durationIndex = cursor.getColumnIndex("duration")
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val name = cursor.getString(nameIndex) ?: continue
                val mime = cursor.getString(mimeIndex)
                val fileUri = ContentUris.withAppendedId(uri, cursor.getLong(idIndex))
                val resolvedMime = if (policy.accepts(name, mime)) mime else {
                    if (type != RecoveryFileType.Audio) continue
                    val identified = identifyMedia(fileUri, name, mime, policy)
                    if (identified?.first != RecoveryFileType.Audio) continue
                    identified.second
                }
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                val trashed = trashIndex >= 0 && cursor.getInt(trashIndex) == 1
                record(mediaFile(fileUri, name, resolvedMime, cursor.getLong(sizeIndex), cursor.getLong(dateIndex) * 1000L,
                    if (Build.VERSION.SDK_INT < 29) path.substringBeforeLast('/', "") else path,
                    type, volume, trashed, duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else null,
                    identity = when {
                        pathIndex < 0 || cursor.isNull(pathIndex) -> fileUri.toString()
                        Build.VERSION.SDK_INT < 29 -> MediaFileIdentity.absolute(path, Environment.getExternalStorageDirectory().path) ?: fileUri.toString()
                        else -> null
                    }))
            }
        }
    }

    /** One provider query per directory, avoiding DocumentFile's per-property IPC calls. */
    private suspend fun scanTree(tree: Uri, policy: MediaScanPolicy, onReadError: (Exception) -> Unit, record: suspend (RecoverableFile) -> Unit) {
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = HashSet<String>()
        queue.add(DocumentsContract.getTreeDocumentId(tree) to 0)
        val projection = arrayOf("document_id", "_display_name", "mime_type", "_size", "last_modified")
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (parent, depth) = queue.removeFirst()
            if (depth > 32 || !visited.add(parent)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            try {
                (query(children, projection, Bundle()) ?: throw java.io.IOException("Folder query failed")).use { c ->
                    while (c.moveToNext()) {
                        coroutineContext.ensureActive()
                        val id = c.getString(0)
                        val name = c.getString(1).orEmpty()
                        val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (name !in setOf(".git", "node_modules", "Android")) queue.add(id to depth + 1)
                        } else {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            val (type, resolvedMime) = identifyMedia(uri, name, mime, policy) ?: continue
                            if (type !in policy.types) continue
                            val size = if (type == RecoveryFileType.Audio) {
                                AudioScanSupport.readableSize(if (c.isNull(3)) null else c.getLong(3)) {
                                    try { resolver.openInputStream(uri)?.use { it.read() >= 0 } == true }
                                    catch (_: java.io.IOException) { false } catch (_: SecurityException) { false }
                                } ?: continue
                            } else c.getLong(3)
                            record(mediaFile(uri, name, resolvedMime, size, c.getLong(4), parent,
                                type, "folder", false, fromFolder = true,
                                identity = MediaFileIdentity.document(tree.authority, id, Environment.getExternalStorageDirectory().path)))
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onReadError(error) }
        }
    }

    private suspend fun scanDirectories(policy: MediaScanPolicy, onReadError: (Exception) -> Unit, record: suspend (RecoverableFile) -> Unit) {
        val storageRoot = Environment.getExternalStorageDirectory()
        val roots = linkedSetOf(storageRoot)
        context.getExternalFilesDirs(null)?.forEach { ext ->
            if (ext != null) {
                val prefix = ext.path.substringBefore("/Android/")
                roots += File(prefix)
            }
        }
        val queue = ArrayDeque<Triple<File, File, Int>>()
        roots.forEach { root ->
            if (root.isDirectory) queue.add(Triple(root, root, 0))
            val androidMedia = File(root, "Android/media")
            if (androidMedia.isDirectory) queue.add(Triple(root, androidMedia, 0))
        }
        val visited = HashSet<String>()
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (root, directory, depth) = queue.removeFirst()
            val canonical = try { directory.canonicalPath } catch (_: Exception) { directory.path }
            val rootPrefix = try { root.canonicalPath } catch (_: Exception) { root.path }
            val isUnderRoot = canonical == rootPrefix || canonical.startsWith("$rootPrefix/")
            if (depth > 32 || !isUnderRoot || !visited.add(canonical)) continue
            try {
                (directory.listFiles() ?: throw java.io.IOException("Directory could not be read")).forEach { file ->
                    coroutineContext.ensureActive()
                    val path = file.path
                    if (file.isDirectory) {
                        if (file.name != "Android" || path.endsWith("Android/media") || path.endsWith("Android\\media")) {
                            queue.add(Triple(root, file, depth + 1))
                        }
                        return@forEach
                    }
                    if (!file.isFile || file.length() <= 0L || file.name == ".nomedia") return@forEach
                    val uri = Uri.fromFile(file)
                    val (type, resolvedMime) = identifyMedia(uri, file.name, null, policy) ?: return@forEach
                    if (type !in policy.types) return@forEach
                    record(mediaFile(uri, file.name, resolvedMime, file.length(), file.lastModified(),
                        file.parentFile!!.relativeTo(root).path, type, if (root == storageRoot) "external_primary" else root.name, false))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onReadError(error) }
        }
    }

    private fun identifyMedia(uri: Uri, name: String, mime: String?, policy: MediaScanPolicy): Pair<RecoveryFileType, String?>? {
        val known = MediaScanPolicy.classify(name, mime)
        val inferred = if (known == null && policy.shouldReadHeader(name, mime)) {
            try { resolver.openInputStream(uri)?.use { FileSignatureDetector.inspect(it) } }
            catch (_: java.io.IOException) { null } catch (_: SecurityException) { null }
        } else null
        val type = known ?: inferred?.type
        val resolvedMime = inferred?.mimeType ?: mime
        val oggContainer = mime?.substringBefore(';')?.trim()?.equals("application/ogg", ignoreCase = true) == true
        if (RecoveryFileType.Audio !in policy.types || (type != RecoveryFileType.Video && !oggContainer)) {
            return type?.let { it to resolvedMime }
        }
        // Container headers alone cannot tell audio-only recordings from videos.
        val retriever = MediaMetadataRetriever()
        val metadata = try {
            retriever.setDataSource(context, uri)
            AudioScanSupport.ContainerMetadata(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes",
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        finally { retriever.release() }
        return AudioScanSupport.resolveContainer(type, resolvedMime, metadata)
    }

    private suspend fun query(uri: Uri, projection: Array<String>, args: Bundle): Cursor? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        val future = queryExecutor.submit {
            try {
                val cursor = resolver.query(uri, projection, args, cancellation)
                continuation.resume(cursor) { _, value, _ -> value?.close() }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation { cancellation.cancel(); future.cancel(true) }
    }

    private fun mediaFile(uri: Uri, name: String, mime: String?, size: Long, modified: Long, path: String,
        type: RecoveryFileType, volume: String, trashed: Boolean, duration: Long? = null, fromFolder: Boolean = false, identity: String? = null): RecoverableFile {
        val hidden = name.startsWith(".") || path.split('/', '\\').any { it.startsWith(".") }
        val sources = buildSet {
            if (trashed) add(RecoveryFileSource.RecycleBin)
            if (hidden) add(RecoveryFileSource.Hidden)
            if (fromFolder) add(RecoveryFileSource.Saf)
            if (path.contains("DCIM", true)) add(RecoveryFileSource.Camera)
            if (path.contains("screenshot", true)) add(RecoveryFileSource.Screenshots)
            if (path.contains("download", true)) add(RecoveryFileSource.Downloads)
            if (isEmpty()) add(RecoveryFileSource.Other)
        }
        val normalizedPath = path.replace('\\', '/').trim('/')
        val key = identity ?: if (fromFolder) uri.toString() else MediaFileIdentity.storage(volume, normalizedPath, name)
        return RecoverableFile(key, uri.toString(), name, mime ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase()),
            size, modified, path, type, sources, hidden, if (hidden) RecoveryFileHiddenReason.HiddenDirectory else null, trashed, volume, duration)
    }

    override suspend fun getSession(sessionId: String): RecoveryScanSession? = withContext(Dispatchers.IO) { sessions[sessionId] ?: store.load(sessionId)?.also { sessions[sessionId] = it } }
    override suspend fun recoverToFolder(sessionId: String, fileIds: Set<String>, treeUriString: String): RecoveryCopyResult = writer.copyToFolder(requireSession(sessionId), fileIds, treeUriString)
    override suspend fun exportArchive(sessionId: String, fileIds: Set<String>, documentUri: String): RecoveryCopyResult = writer.exportArchive(requireSession(sessionId), fileIds, documentUri)
    override suspend fun repairPhoto(sessionId: String, fileId: String, documentUri: String): RecoveryCopyResult = writer.repairPhoto(requireSession(sessionId), fileId, documentUri)
    private suspend fun requireSession(id: String) = getSession(id) ?: error("This scan is no longer available. Please scan again.")
}
