package com.nexappra.filerecovery.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
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
        val folderTypes = policy.types
        val folderBudget = FolderScanBudget(mode == RecoveryScanMode.Quick)
        val stages = buildList {
            if (Build.VERSION.SDK_INT >= 30) add(RecoveryScanLocationType.MediaStoreTrash)
            addAll(policy.types.map { locationFor(it) })
            addAll(listOf(RecoveryScanLocationType.AuthorizedFolders, RecoveryScanLocationType.AccessibleStorage))
        }
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
            if (file.sizeBytes < 0 || (file.sizeBytes == 0L && file.fileType != RecoveryFileType.Audio) ||
                file.fileType !in policy.types || !MediaRecoveryPolicy.accepts(file)) {
                if (inspected % 500 == 0) coroutineContext.ensureActive()
                return
            }
            if (file.id in files) return
            val candidate = try {
                when (file.fileType) {
                    RecoveryFileType.Photo -> readablePhoto(file) ?: return
                    RecoveryFileType.Video -> readableVideo(file) ?: return
                    RecoveryFileType.Audio -> readableAudio(file) ?: return
                    else -> file
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { readFailed(error); return }
            if (files.putIfAbsent(candidate.id, candidate) == null) {
                bytes += candidate.sizeBytes
                when (candidate.fileType) {
                    RecoveryFileType.Photo -> { photoCount++; if (candidate.isHidden) hiddenPhotoCount++ }
                    RecoveryFileType.Video -> { videoCount++; if (candidate.isHidden) hiddenVideoCount++ }
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
                val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context).toList() else listOf("external")
                if (Build.VERSION.SDK_INT >= 30) {
                    stage(RecoveryScanLocationType.MediaStoreTrash) {
                        volumes.forEach { volume ->
                            policy.types.forEach { type ->
                                readLocation { scanMediaCollection(volume, type, policy, ::record, trashOnly = true) }
                            }
                        }
                    }
                }
                policy.types.forEach { type ->
                    stage(locationFor(type)) {
                        volumes.forEach { volume ->
                            readLocation { scanMediaCollection(volume, type, policy, ::record) }
                        }
                    }
                }
                run {
                    suspend fun searchFolderSources() {
                        stage(RecoveryScanLocationType.AuthorizedFolders) {
                            resolver.persistedUriPermissions.filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
                                .sortedByDescending { it.persistedTime }
                                .forEach { permission -> readLocation { scanTree(permission.uri, policy, folderTypes, folderBudget, ::readFailed, ::record) } }
                        }
                        stage(RecoveryScanLocationType.AccessibleStorage) {
                            if (canTraverseStorage()) {
                                scanDirectories(policy, folderTypes, mode, folderBudget, ::readFailed, ::record)
                            }
                            if (!canTraverseStorage()) {
                                warnings += "Hidden media searches are limited to chosen folders. Choose a folder or allow All Files access to search more locations."
                            }
                        }
                    }
                    val foldersCompleted = if (mode == RecoveryScanMode.Quick) {
                        withTimeoutOrNull(20_000L) { searchFolderSources(); true } ?: false
                    } else { searchFolderSources(); true }
                    if (!foldersCompleted) locations = locations.map {
                        if (it.status == ScanLocationStatus.Scanning) it.copy(status = ScanLocationStatus.Failed) else it
                    }
                    if (folderBudget.limited || !foldersCompleted) {
                        partial = true
                        warnings += if (mode == RecoveryScanMode.Quick) "Quick Scan reached a folder limit. Found files are ready; use Deep Scan to search further."
                            else "Some folders exceed the scan depth limit. Choose a more specific folder to search them."
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

    private suspend fun scanMediaCollection(volume: String, type: RecoveryFileType, policy: MediaScanPolicy,
        record: suspend (RecoverableFile) -> Unit, trashOnly: Boolean = false) {
        val uri = when (type) {
            RecoveryFileType.Photo -> MediaStore.Images.Media.getContentUri(volume)
            RecoveryFileType.Video -> MediaStore.Video.Media.getContentUri(volume)
            RecoveryFileType.Audio -> MediaStore.Audio.Media.getContentUri(volume)
            else -> error("Unsupported media type: $type")
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = mutableListOf("_id", "_display_name", "mime_type", "_size", "date_modified", pathColumn)
        if (Build.VERSION.SDK_INT >= 29) projection += MediaStore.MediaColumns.DATA
        if (Build.VERSION.SDK_INT >= 30) projection += MediaStore.MediaColumns.IS_TRASHED
        if (type == RecoveryFileType.Video) projection += MediaStore.Video.VideoColumns.DURATION
        if (type == RecoveryFileType.Audio) projection += MediaStore.Audio.AudioColumns.DURATION
        val args = Bundle().apply {
            val hiddenMediaQuery = !trashOnly
            val pathPatterns = VisualRecoveryPolicy.indexedPathPatterns()
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, buildString {
                append("${MediaStore.MediaColumns.SIZE} > 0")
                if (hiddenMediaQuery) {
                    append(" AND (${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ")
                    append(pathPatterns.joinToString(" OR ") { "$pathColumn LIKE ? ESCAPE '\\'" })
                    append(")")
                }
            })
            if (hiddenMediaQuery) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, (listOf(".%") + pathPatterns).toTypedArray())
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            if (Build.VERSION.SDK_INT >= 30) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, when {
                trashOnly -> MediaStore.MATCH_ONLY
                else -> MediaStore.MATCH_EXCLUDE
            })
        }
        (query(uri, projection.toTypedArray(), args) ?: throw java.io.IOException("Media query failed")).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("_id")
            val nameIndex = cursor.getColumnIndexOrThrow("_display_name")
            val mimeIndex = cursor.getColumnIndexOrThrow("mime_type")
            val sizeIndex = cursor.getColumnIndexOrThrow("_size")
            val dateIndex = cursor.getColumnIndexOrThrow("date_modified")
            val pathIndex = cursor.getColumnIndex(pathColumn)
            val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val trashIndex = cursor.getColumnIndex("is_trashed")
            val durationIndex = cursor.getColumnIndex("duration")
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val name = cursor.getString(nameIndex) ?: continue
                val mime = cursor.getString(mimeIndex)
                val fileUri = ContentUris.withAppendedId(uri, cursor.getLong(idIndex))
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                val trashed = trashIndex >= 0 && cursor.getInt(trashIndex) == 1
                val folderPath = if (Build.VERSION.SDK_INT < 29) path.substringBeforeLast('/', "") else path
                if (trashOnly && !trashed || !VisualRecoveryPolicy.isCandidate(name, folderPath, trashed)) continue
                val resolvedMime = if (policy.accepts(name, mime)) mime else {
                    if (type != RecoveryFileType.Audio) continue
                    val identified = identifyMedia(fileUri, name, mime, policy)
                    if (identified?.first != RecoveryFileType.Audio) continue
                    identified.second
                }
                record(mediaFile(fileUri, name, resolvedMime, cursor.getLong(sizeIndex), cursor.getLong(dateIndex) * 1000L,
                    folderPath,
                    type, volume, trashed, duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else null,
                    identity = when {
                        dataIndex >= 0 && !cursor.isNull(dataIndex) ->
                            MediaFileIdentity.absolute(cursor.getString(dataIndex), Environment.getExternalStorageDirectory().path)
                        pathIndex < 0 || cursor.isNull(pathIndex) -> fileUri.toString()
                        Build.VERSION.SDK_INT < 29 -> MediaFileIdentity.absolute(path, Environment.getExternalStorageDirectory().path) ?: fileUri.toString()
                        else -> null
                    }))
            }
        }
    }

    private data class TreeFolder(val id: String, val path: String, val depth: Int, val noMedia: Boolean)
    private data class TreeEntry(val id: String, val name: String, val mime: String?, val size: Long?, val modified: Long)

    /** One child query per directory; gather metadata first so a trailing .nomedia also hides earlier rows. */
    private suspend fun scanTree(tree: Uri, policy: MediaScanPolicy, types: Set<RecoveryFileType>, budget: FolderScanBudget,
        onReadError: (Exception) -> Unit, record: suspend (RecoverableFile) -> Unit) {
        if (!budget.canSearchDirectory()) return
        val queue = ArrayDeque<TreeFolder>()
        val visited = HashSet<String>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootPath = if (tree.authority == "com.android.externalstorage.documents") rootId.substringAfter(':', "") else {
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            query(rootUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), Bundle())?.use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            }.orEmpty()
        }
        queue.add(TreeFolder(rootId, rootPath, 0, false))
        val projection = arrayOf("document_id", "_display_name", "mime_type", "_size", "last_modified")
        while (queue.isNotEmpty() && budget.canSearchDirectory()) {
            coroutineContext.ensureActive()
            val folder = queue.removeFirst()
            if (!visited.add(folder.id) || !budget.enterDirectory(folder.depth)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id)
            try {
                (query(children, projection, Bundle()) ?: throw java.io.IOException("Folder query failed")).use { c ->
                    var inspectedRows = 0
                    var noMedia = folder.noMedia
                    while (c.moveToNext() && budget.inspectEntry()) {
                        coroutineContext.ensureActive()
                        inspectedRows++
                        if (c.getString(1) == ".nomedia" && c.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR) noMedia = true
                    }
                    c.moveToPosition(-1)
                    var processedRows = 0
                    while (processedRows++ < inspectedRows && c.moveToNext()) {
                        coroutineContext.ensureActive()
                        if (!budget.canContinue()) break
                        val entry = TreeEntry(c.getString(0), c.getString(1).orEmpty(), c.getString(2),
                            if (c.isNull(3)) null else c.getLong(3), c.getLong(4))
                        if (entry.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (entry.name !in setOf(".git", "node_modules", "Android")) {
                                val childPath = listOf(folder.path, entry.name).filter { it.isNotBlank() }.joinToString("/")
                                val child = TreeFolder(entry.id, childPath, folder.depth + 1, noMedia)
                                if (VisualRecoveryPolicy.isCandidate("", childPath, noMedia = noMedia)) queue.addFirst(child)
                                else queue.addLast(child)
                            }
                        } else {
                            if (entry.name == ".nomedia") continue
                            if (!VisualRecoveryPolicy.isCandidate(entry.name, folder.path, noMedia = noMedia)) continue
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)
                            val (type, resolvedMime) = identifyMedia(uri, entry.name, entry.mime, policy) ?: continue
                            if (type !in types) continue
                            val size = if (type == RecoveryFileType.Audio) {
                                AudioScanSupport.readableSize(entry.size) {
                                    try { resolver.openInputStream(uri)?.use { it.read() >= 0 } == true }
                                    catch (_: java.io.IOException) { false } catch (_: SecurityException) { false }
                                } ?: continue
                            } else entry.size ?: 0L
                            record(mediaFile(uri, entry.name, resolvedMime, size, entry.modified, folder.path,
                                type, "folder", false, fromFolder = true,
                                identity = MediaFileIdentity.document(tree.authority, entry.id, Environment.getExternalStorageDirectory().path), noMedia = noMedia))
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onReadError(error) }
        }
    }

    private data class StorageFolder(val root: File, val directory: File, val depth: Int, val noMedia: Boolean)

    private suspend fun scanDirectories(policy: MediaScanPolicy, types: Set<RecoveryFileType>, mode: RecoveryScanMode, budget: FolderScanBudget,
        onReadError: (Exception) -> Unit, record: suspend (RecoverableFile) -> Unit) {
        val storageRoot = Environment.getExternalStorageDirectory()
        val roots = linkedSetOf(storageRoot)
        context.getExternalFilesDirs(null)?.forEach { ext ->
            if (ext != null) {
                val prefix = ext.path.substringBefore("/Android/")
                roots += File(prefix)
            }
        }
        val queue = ArrayDeque<StorageFolder>()
        roots.forEach { root ->
            if (root.isDirectory) queue.add(StorageFolder(root, root, 0, false))
            val androidMedia = File(root, "Android/media")
            if (androidMedia.isDirectory) queue.add(StorageFolder(root, androidMedia, 0, File(root, ".nomedia").isFile || File(root, "Android/.nomedia").isFile))
        }
        val visited = HashSet<String>()
        while (queue.isNotEmpty() && budget.canSearchDirectory()) {
            coroutineContext.ensureActive()
            val (root, directory, depth, inheritedNoMedia) = queue.removeFirst()
            val canonical = try { directory.canonicalPath } catch (_: Exception) { directory.path }
            val rootPrefix = try { root.canonicalPath } catch (_: Exception) { root.path }
            val isUnderRoot = canonical == rootPrefix || canonical.startsWith("$rootPrefix/")
            if (!isUnderRoot || !visited.add(canonical) || !budget.enterDirectory(depth)) continue
            try {
                val noMedia = inheritedNoMedia || File(directory, ".nomedia").isFile
                val folderPath = directory.relativeTo(root).path
                for (file in directory.listFiles() ?: throw java.io.IOException("Directory could not be read")) {
                    coroutineContext.ensureActive()
                    if (!budget.inspectEntry()) break
                    if (file.isDirectory) {
                        val childPath = file.relativeTo(root).path
                        val childNoMedia = noMedia || File(file, ".nomedia").isFile
                        if (file.name != "Android" &&
                            file.name !in setOf(".git", "node_modules") &&
                            (mode == RecoveryScanMode.Deep || MediaRecoveryPolicy.exploreQuickDirectory(childPath, childNoMedia, RecoveryFileType.Audio in types))) {
                            val child = StorageFolder(root, file, depth + 1, childNoMedia)
                            if (VisualRecoveryPolicy.isCandidate("", childPath, noMedia = childNoMedia)) queue.addFirst(child)
                            else queue.addLast(child)
                        }
                        continue
                    }
                    if (!file.isFile || file.length() <= 0L || file.name == ".nomedia") continue
                    if (!VisualRecoveryPolicy.isCandidate(file.name, folderPath, noMedia = noMedia)) continue
                    // Reject links to sources outside the storage volume as well as directory links.
                    if (!file.canonicalPath.startsWith("$rootPrefix/")) continue
                    val uri = Uri.fromFile(file)
                    val (type, resolvedMime) = identifyMedia(uri, file.name, null, policy) ?: continue
                    if (type !in types) continue
                    record(mediaFile(uri, file.name, resolvedMime, file.length(), file.lastModified(),
                        folderPath, type, if (root == storageRoot) "external_primary" else root.name, false, noMedia = noMedia))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onReadError(error) }
        }
    }

    private fun canTraverseStorage(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= 29 -> Environment.isExternalStorageLegacy()
        else -> context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Check only eligible photos; preserve the source and retain the actual image MIME for renamed copies. */
    private suspend fun readablePhoto(file: RecoverableFile): RecoverableFile? {
        coroutineContext.ensureActive()
        val source = resolver.openInputStream(Uri.parse(file.uriString)) ?: throw java.io.IOException("Photo source could not be read")
        return source.buffered().use { input ->
            input.mark(64)
            if (input.read() < 0) return@use null
            input.reset()
            val signature = FileSignatureDetector.inspect(input)
            if (signature != null) {
                if (signature.type == RecoveryFileType.Photo) file.copy(mimeType = signature.mimeType) else null
            } else {
                input.reset()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) file.copy(mimeType = bounds.outMimeType ?: file.mimeType) else null
            }
        }
    }

    /** Verify container tracks without decoding frames or rewriting the source. */
    private suspend fun readableVideo(file: RecoverableFile): RecoverableFile? {
        coroutineContext.ensureActive()
        val uri = Uri.parse(file.uriString)
        val source = resolver.openInputStream(uri) ?: throw java.io.IOException("Video source could not be read")
        val signature = source.buffered().use { input ->
            input.mark(32)
            if (input.read() < 0) return null
            input.reset()
            FileSignatureDetector.inspect(input)
        }
        if (signature != null && signature.type !in setOf(RecoveryFileType.Video, RecoveryFileType.Audio)) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                coroutineContext.ensureActive()
                val track = extractor.getTrackFormat(index)
                if (track.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                val duration = if (track.containsKey(MediaFormat.KEY_DURATION)) track.getLong(MediaFormat.KEY_DURATION) / 1_000L else null
                return file.copy(
                    mimeType = signature?.takeIf { it.type == RecoveryFileType.Video }?.mimeType ?: file.mimeType,
                    durationMillis = duration?.takeIf { it > 0 } ?: file.durationMillis,
                )
            }
            return null // An MP4/audio container alone is not proof of a video track.
        } catch (_: java.io.IOException) { return null }
        catch (_: IllegalArgumentException) { return null }
        finally { extractor.release() }
    }

    /** Only recovery candidates reach this check; audio signatures do not require playback. */
    private suspend fun readableAudio(file: RecoverableFile): RecoverableFile? {
        coroutineContext.ensureActive()
        val uri = Uri.parse(file.uriString)
        val source = resolver.openInputStream(uri) ?: throw java.io.IOException("Audio source could not be read")
        val signature = source.buffered().use { input ->
            input.mark(32)
            if (input.read() < 0) return null
            input.reset()
            FileSignatureDetector.inspect(input)
        }
        if (signature?.type == RecoveryFileType.Audio && AudioScanSupport.isAudioOnlySignature(signature.mimeType)) {
            return file.copy(mimeType = signature.mimeType)
        }
        if (signature != null && signature.type !in setOf(RecoveryFileType.Audio, RecoveryFileType.Video)) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var hasAudio = false
            var hasVideo = false
            var duration = file.durationMillis
            for (index in 0 until extractor.trackCount) {
                coroutineContext.ensureActive()
                val track = extractor.getTrackFormat(index)
                val trackMime = track.getString(MediaFormat.KEY_MIME).orEmpty()
                if (trackMime.startsWith("video/")) hasVideo = true
                if (trackMime.startsWith("audio/")) {
                    hasAudio = true
                    if (track.containsKey(MediaFormat.KEY_DURATION)) {
                        val trackDuration = track.getLong(MediaFormat.KEY_DURATION) / 1_000L
                        if (trackDuration > (duration ?: 0L)) duration = trackDuration
                    }
                }
            }
            // EBML headers identify a container family; preserve a known Matroska audio MIME.
            val containerMime = if (signature?.mimeType == "video/webm" && file.mimeType == "audio/x-matroska") file.mimeType
                else signature?.mimeType ?: file.mimeType
            val mime = AudioScanSupport.verifiedAudioMime(containerMime,
                AudioScanSupport.ContainerMetadata(hasAudio, hasVideo, containerMime)) ?: return null
            return file.copy(mimeType = mime, durationMillis = duration)
        } catch (_: java.io.IOException) { return null }
        catch (_: IllegalArgumentException) { return null }
        finally { extractor.release() }
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
        type: RecoveryFileType, volume: String, trashed: Boolean, duration: Long? = null, fromFolder: Boolean = false,
        identity: String? = null, noMedia: Boolean = false): RecoverableFile {
        val hiddenReason = VisualRecoveryPolicy.hiddenReason(name, path, noMedia)
        val hidden = hiddenReason != null
        val sources = buildSet {
            if (trashed || VisualRecoveryPolicy.isRecycleLocation(name, path)) add(RecoveryFileSource.RecycleBin)
            if (hidden) add(RecoveryFileSource.Hidden)
            if (noMedia) add(RecoveryFileSource.NoMedia)
            if (fromFolder) add(RecoveryFileSource.Saf)
            if (path.contains("DCIM", true)) add(RecoveryFileSource.Camera)
            if (path.contains("screenshot", true)) add(RecoveryFileSource.Screenshots)
            if (path.contains("download", true)) add(RecoveryFileSource.Downloads)
            if (isEmpty()) add(RecoveryFileSource.Other)
        }
        val normalizedPath = path.replace('\\', '/').trim('/')
        val key = identity ?: if (fromFolder) uri.toString() else MediaFileIdentity.storage(volume, normalizedPath, name)
        return RecoverableFile(key, uri.toString(), name, mime ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase()),
            size, modified, path, type, sources, hidden, hiddenReason, trashed, volume, duration)
    }

    override suspend fun getSession(sessionId: String): RecoveryScanSession? = withContext(Dispatchers.IO) { sessions[sessionId] ?: store.load(sessionId)?.also { sessions[sessionId] = it } }
    override suspend fun recoverToFolder(sessionId: String, fileIds: Set<String>, treeUriString: String): RecoveryCopyResult = writer.copyToFolder(requireSession(sessionId), fileIds, treeUriString)
    override suspend fun exportArchive(sessionId: String, fileIds: Set<String>, documentUri: String): RecoveryCopyResult = writer.exportArchive(requireSession(sessionId), fileIds, documentUri)
    override suspend fun repairPhoto(sessionId: String, fileId: String, documentUri: String): RecoveryCopyResult = writer.repairPhoto(requireSession(sessionId), fileId, documentUri)
    private suspend fun requireSession(id: String) = getSession(id) ?: error("This scan is no longer available. Please scan again.")
}
