package com.nexappra.filerecovery.data.repository

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.nexappra.filerecovery.core.utils.FILE_RECOVERY_DEBUG_TAG
import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryCopyResult
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.RecoveryFileHiddenReason
import com.nexappra.filerecovery.domain.model.RecoveryFileSource
import com.nexappra.filerecovery.domain.model.RecoveryFileType
import com.nexappra.filerecovery.domain.model.RecoveryResultFilter
import com.nexappra.filerecovery.domain.model.RecoveryScanLocationState
import com.nexappra.filerecovery.domain.model.RecoveryScanLocationType
import com.nexappra.filerecovery.domain.model.RecoveryScanMode
import com.nexappra.filerecovery.domain.model.RecoveryScanSession
import com.nexappra.filerecovery.domain.model.RecoveryScanSnapshot
import com.nexappra.filerecovery.domain.model.RecoveryScanStatus
import com.nexappra.filerecovery.domain.model.ScanLocationStatus
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

@Singleton
class MediaStoreRecoveryScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageRepository: AndroidStorageRepository,
) : RecoveryScanRepository {

    private val sessions = ConcurrentHashMap<String, RecoveryScanSession>()
    private val resolver: ContentResolver
        get() = context.contentResolver

    override suspend fun performFullDeviceScan(
        selectedCategory: RecoveryCategory?,
        onSnapshot: suspend (RecoveryScanSnapshot) -> Unit,
    ): RecoveryScanSession = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val access = currentAccessSummary()
        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            buildString {
                appendLine("Starting recovery scan session=$sessionId scope=${selectedCategory ?: "FullDevice"}")
                appendLine("SDK=${Build.VERSION.SDK_INT}")
                appendLine("Target SDK=${context.applicationInfo.targetSdkVersion}")
                appendLine("READ_MEDIA_IMAGES=${access.hasImages}")
                appendLine("READ_MEDIA_VIDEO=${access.hasVideos}")
                appendLine("READ_MEDIA_AUDIO=${access.hasAudio}")
                appendLine("READ_EXTERNAL_STORAGE=${access.hasLegacyRead}")
                appendLine("READ_MEDIA_VISUAL_USER_SELECTED=${access.hasSelectedVisual}")
                appendLine("All Files Access=${access.hasAllFilesAccess}")
                append("SAF persisted trees=${access.safRoots}")
            },
        )
        if (!access.hasAnyAccess) {
            Log.w(
                FILE_RECOVERY_DEBUG_TAG,
                "Full device scan blocked: no accessible media or authorized folders are currently granted.",
            )
            throw SecurityException("No accessible media or authorized folders are currently granted.")
        }

        val stageOrder = scanLocationTypesFor(selectedCategory)
        var states = stageOrder.map { type ->
            RecoveryScanLocationState(
                type = type,
                status = ScanLocationStatus.Pending,
            )
        }
        val scanStart = SystemClock.elapsedRealtime()
        var itemsInspected = 0
        var bytesInspected = 0L
        var duplicatesRemoved = 0
        var normalSkipped = 0
        var unreadableSkipped = 0
        val discovered = LinkedHashMap<String, RecoverableFile>()

        suspend fun publish(
            status: RecoveryScanStatus,
            currentStage: RecoveryScanLocationType? = null,
            stageProgress: Float = 0f,
            errorMessage: String? = null,
        ) {
            val completedStages = states.count { state ->
                state.status == ScanLocationStatus.Completed || state.status == ScanLocationStatus.Failed
            }
            val progress = when {
                status == RecoveryScanStatus.Completed || status == RecoveryScanStatus.CompletedEmpty -> 1f
                currentStage == null -> completedStages.toFloat() / stageOrder.size.toFloat()
                else -> ((completedStages - 1).coerceAtLeast(0) + stageProgress.coerceIn(0f, 1f)) /
                    stageOrder.size.toFloat()
            }
            val elapsedMillis = SystemClock.elapsedRealtime() - scanStart
            val estimatedRemainingMillis = if (progress > 0f &&
                status != RecoveryScanStatus.Completed &&
                status != RecoveryScanStatus.CompletedEmpty
            ) {
                ((elapsedMillis / progress) - elapsedMillis).toLong().coerceAtLeast(0L)
            } else if (status == RecoveryScanStatus.Completed || status == RecoveryScanStatus.CompletedEmpty) {
                0L
            } else {
                null
            }

            onSnapshot(
                RecoveryScanSnapshot(
                    mode = RecoveryScanMode.FullDevice,
                    selectedCategory = selectedCategory,
                    status = status,
                    progress = progress,
                    totalItemsScanned = itemsInspected,
                    totalFilesFound = discovered.size,
                    totalBytesScanned = bytesInspected,
                    estimatedRemainingMillis = estimatedRemainingMillis,
                    currentLocation = currentStage?.let { stage ->
                        states.firstOrNull { state -> state.type == stage }
                    },
                    locations = states,
                    locationsChecked = states.count { state ->
                        state.status == ScanLocationStatus.Completed || state.status == ScanLocationStatus.Failed
                    },
                    categoryCounts = computeCategoryCounts(discovered.values),
                    elapsedMillis = elapsedMillis,
                    errorMessage = errorMessage,
                ),
            )
        }

        fun updateStage(
            type: RecoveryScanLocationType,
            transform: (RecoveryScanLocationState) -> RecoveryScanLocationState,
        ) {
            states = states.map { state ->
                if (state.type == type) transform(state) else state
            }
        }

        fun beginStage(
            type: RecoveryScanLocationType,
            displayPath: String? = null,
        ) {
            updateStage(type) { state ->
                state.copy(
                    status = ScanLocationStatus.Scanning,
                    displayPath = displayPath ?: state.displayPath,
                )
            }
        }

        fun completeStage(
            type: RecoveryScanLocationType,
            foundCount: Int,
            totalCount: Int = foundCount,
            displayPath: String? = null,
        ) {
            updateStage(type) { state ->
                state.copy(
                    status = ScanLocationStatus.Completed,
                    foundCount = foundCount,
                    totalCount = totalCount,
                    displayPath = displayPath ?: state.displayPath,
                )
            }
        }

        fun failStage(
            type: RecoveryScanLocationType,
            displayPath: String? = null,
        ) {
            updateStage(type) { state ->
                state.copy(
                    status = ScanLocationStatus.Failed,
                    displayPath = displayPath ?: state.displayPath,
                )
            }
        }

        var lastPublishTime = 0L

        suspend fun recordCandidate(
            file: RecoverableFile,
            stage: RecoveryScanLocationType,
            stageCount: Int,
            displayPath: String,
            publishProgress: Float,
        ) {
            val existing = discovered[file.id]
            if (existing == null) {
                discovered[file.id] = file
            } else {
                duplicatesRemoved += 1
                discovered[file.id] = mergeFiles(existing, file)
            }
            updateStage(stage) { state ->
                state.copy(
                    foundCount = stageCount,
                    totalCount = stageCount,
                    displayPath = displayPath,
                )
            }
            val now = SystemClock.elapsedRealtime()
            if (stageCount == 1 || now - lastPublishTime >= 100L) {
                lastPublishTime = now
                publish(
                    status = RecoveryScanStatus.Scanning,
                    currentStage = stage,
                    stageProgress = publishProgress,
                )
            }
        }

        publish(status = RecoveryScanStatus.Preparing)

        beginStage(
            type = RecoveryScanLocationType.MediaPermissions,
            displayPath = access.summaryLabel,
        )
        publish(
            status = RecoveryScanStatus.Scanning,
            currentStage = RecoveryScanLocationType.MediaPermissions,
            stageProgress = 0.5f,
        )
        completeStage(
            type = RecoveryScanLocationType.MediaPermissions,
            foundCount = access.grantedSourceCount,
            displayPath = access.summaryLabel,
        )

        val volumes = availableMediaStoreVolumes()
        beginStage(
            type = RecoveryScanLocationType.StorageVolumes,
            displayPath = volumes.joinToString(),
        )
        publish(
            status = RecoveryScanStatus.Scanning,
            currentStage = RecoveryScanLocationType.StorageVolumes,
            stageProgress = 0.5f,
        )
        completeStage(
            type = RecoveryScanLocationType.StorageVolumes,
            foundCount = volumes.size,
            displayPath = volumes.joinToString(),
        )

        suspend fun scanMediaStage(
            stage: RecoveryScanLocationType,
            volumesToScan: List<String>,
            query: suspend (String) -> CandidateCollectionResult,
        ) {
            beginStage(stage)
            publish(
                status = RecoveryScanStatus.Scanning,
                currentStage = stage,
                stageProgress = 0.05f,
            )
            var stageCount = 0
            try {
                if (volumesToScan.isEmpty()) {
                    Log.d(
                        FILE_RECOVERY_DEBUG_TAG,
                        "${stage.name} skipped: no accessible volumes for this permission state.",
                    )
                }
                volumesToScan.forEachIndexed { volumeIndex, volume ->
                    coroutineContext.ensureActive()
                    val displayPath = volume.toDisplayVolumeLabel()
                    updateStage(stage) { state ->
                        state.copy(displayPath = displayPath)
                    }
                    val result = query(volume)
                    itemsInspected += result.inspectedCount
                    bytesInspected += result.inspectedBytes
                    normalSkipped += result.normalSkippedCount
                    result.candidates.forEach { file ->
                        coroutineContext.ensureActive()
                        stageCount += 1
                        recordCandidate(
                            file = file,
                            stage = stage,
                            stageCount = stageCount,
                            displayPath = file.relativePath ?: displayPath,
                            publishProgress = (volumeIndex + 1).toFloat() / volumesToScan.size.coerceAtLeast(1),
                        )
                    }
                    publish(
                        status = RecoveryScanStatus.Scanning,
                        currentStage = stage,
                        stageProgress = (volumeIndex + 1).toFloat() / volumesToScan.size.coerceAtLeast(1),
                    )
                }
                completeStage(stage, foundCount = stageCount)
            } catch (error: SecurityException) {
                Log.e(
                    FILE_RECOVERY_DEBUG_TAG,
                    "${stage.name} failed with SecurityException",
                    error,
                )
                failStage(stage, displayPath = error.message)
            } catch (error: IllegalArgumentException) {
                Log.e(
                    FILE_RECOVERY_DEBUG_TAG,
                    "${stage.name} failed with IllegalArgumentException",
                    error,
                )
                failStage(stage, displayPath = error.message)
            } catch (error: IOException) {
                Log.e(
                    FILE_RECOVERY_DEBUG_TAG,
                    "${stage.name} failed with IOException",
                    error,
                )
                failStage(stage, displayPath = error.message)
            }
        }

        if (selectedCategory != RecoveryCategory.RecycleBin && selectedCategory.scansImages()) {
            scanMediaStage(
                stage = RecoveryScanLocationType.MediaStoreImages,
                volumesToScan = volumes.filter { access.canReadImages },
                query = { volume -> queryImages(volume, selectedCategory) },
            )
        }
        if (selectedCategory != RecoveryCategory.RecycleBin && selectedCategory.scansVideos()) {
            scanMediaStage(
                stage = RecoveryScanLocationType.MediaStoreVideos,
                volumesToScan = volumes.filter { access.canReadVideos },
                query = { volume -> queryVideos(volume, selectedCategory) },
            )
        }
        if (selectedCategory != RecoveryCategory.RecycleBin && selectedCategory.scansAudio()) {
            scanMediaStage(
                stage = RecoveryScanLocationType.MediaStoreAudio,
                volumesToScan = volumes.filter { access.canReadAudio },
                query = { volume -> queryAudio(volume, selectedCategory) },
            )
        }
        if (selectedCategory != RecoveryCategory.RecycleBin && selectedCategory.scansFiles()) {
            scanMediaStage(
                stage = RecoveryScanLocationType.MediaStoreFiles,
                volumesToScan = volumes.filter { access.canReadMediaStoreFiles },
                query = { volume -> queryFiles(volume, selectedCategory) },
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (access.canReadImages || access.canReadVideos || access.canReadAudio || access.canReadMediaStoreFiles)
        ) {
            scanMediaStage(
                stage = RecoveryScanLocationType.MediaStoreTrash,
                volumesToScan = volumes,
                query = { volume -> queryTrash(volume, selectedCategory) },
            )
        } else {
            failStage(
                type = RecoveryScanLocationType.MediaStoreTrash,
                displayPath = "Not available on this device",
            )
        }

        if (selectedCategory != RecoveryCategory.RecycleBin) {
        beginStage(RecoveryScanLocationType.AuthorizedFolders)
        publish(
            status = RecoveryScanStatus.Scanning,
            currentStage = RecoveryScanLocationType.AuthorizedFolders,
            stageProgress = 0.05f,
        )
        var safCount = 0
        val persistedTrees = resolver.persistedUriPermissions
            .filter { permission -> permission.isReadPermission }
            .map { permission -> permission.uri }
        if (persistedTrees.isEmpty()) {
            failStage(
                type = RecoveryScanLocationType.AuthorizedFolders,
                displayPath = "No authorized folders",
            )
        } else {
            persistedTrees.forEachIndexed { index, treeUri ->
                coroutineContext.ensureActive()
                val tree = DocumentFile.fromTreeUri(context, treeUri)
                if (tree == null || !tree.canRead()) {
                    return@forEachIndexed
                }
                val rootLabel = tree.name ?: "Authorized folder"
                val result = scanTree(
                    root = tree,
                    rootLabel = rootLabel,
                    selectedCategory = selectedCategory,
                    onFile = { file ->
                        safCount += 1
                        recordCandidate(
                            file = file,
                            stage = RecoveryScanLocationType.AuthorizedFolders,
                            stageCount = safCount,
                            displayPath = file.relativePath ?: rootLabel,
                            publishProgress = (index + 1).toFloat() / persistedTrees.size.coerceAtLeast(1),
                        )
                    },
                )
                itemsInspected += result.inspectedCount
                bytesInspected += result.inspectedBytes
                normalSkipped += result.normalSkippedCount
            }
            completeStage(
                type = RecoveryScanLocationType.AuthorizedFolders,
                foundCount = safCount,
            )
        }

        if (access.hasAllFilesAccess && selectedCategory != RecoveryCategory.RecycleBin) {
            beginStage(RecoveryScanLocationType.AccessibleStorage)
            var filesystemCount = 0
            storageRepository.scanAccessibleSharedStorage(
                includePath = { path -> path.matchesFilesystemScope(selectedCategory) },
            ) { storageFile ->
                val signatureType = if (storageFile.hiddenAncestor || storageFile.noMediaAncestor ||
                    storageFile.file.extension.isBlank() || storageFile.file.extension.lowercase() in suspiciousExtensions
                ) {
                    FileSignatureDetector.detect(storageFile.file)
                } else {
                    null
                }
                val file = buildRecoverableFile(
                    id = storageFile.relativePath.hashCode().toLong(),
                    uri = Uri.fromFile(storageFile.file),
                    displayName = storageFile.file.name,
                    mimeType = null,
                    sizeBytes = storageFile.file.length(),
                    dateModifiedMillis = storageFile.file.lastModified(),
                    relativePath = storageFile.relativePath,
                    explicitType = null,
                    detectedFileType = signatureType,
                    volumeName = "shared-storage",
                    isFromSaf = false,
                    isTrashed = false,
                    durationMillis = null,
                    dateExpiresMillis = null,
                    noMediaAncestor = storageFile.noMediaAncestor,
                    hiddenAncestor = storageFile.hiddenAncestor,
                )
                itemsInspected += 1
                bytesInspected += storageFile.file.length()
                if (file.isRecoveryCandidate() && file.matchesSelectedCategory(selectedCategory)) {
                    filesystemCount += 1
                    recordCandidate(file, RecoveryScanLocationType.AccessibleStorage, filesystemCount, file.relativePath.orEmpty(), 0.5f)
                } else {
                    normalSkipped += 1
                }
            }
            completeStage(RecoveryScanLocationType.AccessibleStorage, filesystemCount)
        }
        }

        beginStage(RecoveryScanLocationType.ValidateCandidates)
        publish(
            status = RecoveryScanStatus.Scanning,
            currentStage = RecoveryScanLocationType.ValidateCandidates,
            stageProgress = 0.05f,
        )
        val validated = LinkedHashMap<String, RecoverableFile>()
        val candidates = discovered.values.toList()
        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            "Scanner inspected=$itemsInspected rawCandidates=${candidates.size} normalSkipped=$normalSkipped",
        )
        candidates.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            val isReadable = runCatching { validateReadable(file) }.getOrElse { false }
            if (isReadable) {
                validated[file.id] = file
            } else {
                unreadableSkipped += 1
                if (unreadableSkipped <= 5) {
                    Log.w(
                        FILE_RECOVERY_DEBUG_TAG,
                        "Validation rejected candidate scheme=${Uri.parse(file.uriString).scheme} mime=${file.mimeType} size=${file.sizeBytes}",
                    )
                }
            }
            val checkedCount = index + 1
            updateStage(RecoveryScanLocationType.ValidateCandidates) { state ->
                state.copy(
                    foundCount = validated.size,
                    totalCount = checkedCount,
                    displayPath = file.relativePath ?: file.displayName,
                )
            }
            if (checkedCount == 1 || checkedCount % 24 == 0 || checkedCount == candidates.size) {
                publish(
                    status = RecoveryScanStatus.Scanning,
                    currentStage = RecoveryScanLocationType.ValidateCandidates,
                    stageProgress = checkedCount.toFloat() / candidates.size.coerceAtLeast(1),
                )
            }
        }
        completeStage(
            type = RecoveryScanLocationType.ValidateCandidates,
            foundCount = validated.size,
            totalCount = candidates.size,
        )
        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            "Valid results=${validated.size}",
        )
        discovered.clear()
        discovered.putAll(validated)

        beginStage(RecoveryScanLocationType.RemoveDuplicates)
        publish(
            status = RecoveryScanStatus.Scanning,
            currentStage = RecoveryScanLocationType.RemoveDuplicates,
            stageProgress = 0.5f,
        )
        completeStage(
            type = RecoveryScanLocationType.RemoveDuplicates,
            foundCount = duplicatesRemoved,
            displayPath = "Duplicates removed: $duplicatesRemoved",
        )

        beginStage(RecoveryScanLocationType.PersistResults)
        publish(
            status = RecoveryScanStatus.Scanning,
            currentStage = RecoveryScanLocationType.PersistResults,
            stageProgress = 0.5f,
        )
        val session = RecoveryScanSession(
            id = sessionId,
            mode = RecoveryScanMode.FullDevice,
            selectedCategory = selectedCategory,
            durationMillis = SystemClock.elapsedRealtime() - scanStart,
            totalBytesScanned = bytesInspected,
            files = discovered.values.sortedByDescending { file -> file.dateModifiedMillis },
            locations = states,
            completedAtMillis = System.currentTimeMillis(),
        )
        sessions[session.id] = session
        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            "Saving session ${session.id}",
        )
        completeStage(
            type = RecoveryScanLocationType.PersistResults,
            foundCount = session.files.size,
            displayPath = "Session ${session.id.take(8)}",
        )

        logDebugSummary(
            session = session,
            itemsInspected = itemsInspected,
            duplicatesRemoved = duplicatesRemoved,
            normalSkipped = normalSkipped,
            unreadableSkipped = unreadableSkipped,
        )

        publish(
            status = if (session.files.isEmpty()) {
                RecoveryScanStatus.CompletedEmpty
            } else {
                RecoveryScanStatus.Completed
            },
            currentStage = RecoveryScanLocationType.PersistResults,
            stageProgress = 1f,
        )

        session
    }

    override suspend fun getSession(sessionId: String): RecoveryScanSession? = sessions[sessionId]

    override suspend fun recoverToFolder(
        sessionId: String,
        fileIds: Set<String>,
        treeUriString: String,
    ): RecoveryCopyResult = withContext(Dispatchers.IO) {
        val session = sessions[sessionId] ?: return@withContext RecoveryCopyResult(
            recoveredCount = 0,
            skippedCount = fileIds.size,
        )
        val destinationRoot = DocumentFile.fromTreeUri(
            context,
            Uri.parse(treeUriString),
        ) ?: return@withContext RecoveryCopyResult(
            recoveredCount = 0,
            skippedCount = fileIds.size,
        )
        val selectedFiles = session.files.filter { file -> file.id in fileIds }
        var recoveredCount = 0
        var skippedCount = 0
        selectedFiles.forEach { file ->
            coroutineContext.ensureActive()
            runCatching {
                val sourceUri = Uri.parse(file.uriString)
                if (!validateReadable(file)) {
                    throw FileNotFoundException("Source is no longer readable: ${file.displayName}")
                }
                val fileName = findAvailableName(destinationRoot, file.displayName)
                val destination = destinationRoot.createFile(
                    file.mimeType ?: inferMimeType(file.displayName),
                    fileName,
                ) ?: throw IOException("Unable to create destination file.")
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(destination.uri, "w")?.use { output ->
                        input.copyTo(output)
                    } ?: throw IOException("Unable to open destination output stream.")
                } ?: throw IOException("Unable to open source input stream.")
            }.onSuccess {
                recoveredCount += 1
            }.onFailure {
                skippedCount += 1
            }
        }
        RecoveryCopyResult(
            recoveredCount = recoveredCount,
            skippedCount = skippedCount,
        )
    }

    private suspend fun queryImages(
        volumeName: String,
        selectedCategory: RecoveryCategory?,
    ): CandidateCollectionResult = withContext(Dispatchers.IO) {
        queryMediaCollection(
            baseUri = imageUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = RecoveryFileType.Photo,
            selectedCategory = selectedCategory,
            includeTrashedOnly = false,
            queryLabel = "MediaStore Images",
        )
    }

    private suspend fun queryVideos(
        volumeName: String,
        selectedCategory: RecoveryCategory?,
    ): CandidateCollectionResult = withContext(Dispatchers.IO) {
        queryMediaCollection(
            baseUri = videoUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = RecoveryFileType.Video,
            selectedCategory = selectedCategory,
            includeTrashedOnly = false,
            queryLabel = "MediaStore Videos",
        )
    }

    private suspend fun queryAudio(
        volumeName: String,
        selectedCategory: RecoveryCategory?,
    ): CandidateCollectionResult = withContext(Dispatchers.IO) {
        queryMediaCollection(
            baseUri = audioUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = RecoveryFileType.Audio,
            selectedCategory = selectedCategory,
            includeTrashedOnly = false,
            queryLabel = "MediaStore Audio",
        )
    }

    private suspend fun queryTrash(
        volumeName: String,
        selectedCategory: RecoveryCategory?,
    ): CandidateCollectionResult = withContext(Dispatchers.IO) {
        val results = buildList {
            if (selectedCategory.scansImages()) add(queryMediaCollection(
            baseUri = imageUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = RecoveryFileType.Photo,
            selectedCategory = selectedCategory,
            includeTrashedOnly = true,
            queryLabel = "Trash Images",
            ))
            if (selectedCategory.scansVideos()) add(queryMediaCollection(
            baseUri = videoUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = RecoveryFileType.Video,
            selectedCategory = selectedCategory,
            includeTrashedOnly = true,
            queryLabel = "Trash Videos",
            ))
            if (selectedCategory.scansAudio()) add(queryMediaCollection(
            baseUri = audioUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = RecoveryFileType.Audio,
            selectedCategory = selectedCategory,
            includeTrashedOnly = true,
            queryLabel = "Trash Audio",
            ))
            if (selectedCategory.scansFiles()) add(queryMediaCollection(
            baseUri = fileUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = null,
            selectedCategory = selectedCategory,
            includeTrashedOnly = true,
            queryLabel = "Trash Files",
            ))
        }
        CandidateCollectionResult(
            candidates = results.flatMap { it.candidates },
            inspectedCount = results.sumOf { it.inspectedCount },
            inspectedBytes = results.sumOf { it.inspectedBytes },
            normalSkippedCount = results.sumOf { it.normalSkippedCount },
        )
    }

    private suspend fun queryFiles(
        volumeName: String,
        selectedCategory: RecoveryCategory?,
    ): CandidateCollectionResult = withContext(Dispatchers.IO) {
        queryMediaCollection(
            baseUri = fileUriForVolume(volumeName),
            volumeName = volumeName,
            explicitType = null,
            selectedCategory = selectedCategory,
            includeTrashedOnly = false,
            queryLabel = "MediaStore Files",
        )
    }

    private suspend fun queryMediaCollection(
        baseUri: Uri,
        volumeName: String,
        explicitType: RecoveryFileType?,
        selectedCategory: RecoveryCategory?,
        includeTrashedOnly: Boolean,
        queryLabel: String,
    ): CandidateCollectionResult {
        val usesRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pathColumn = if (usesRelativePath) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val includeDurationColumn = explicitType == RecoveryFileType.Video || explicitType == RecoveryFileType.Audio
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(pathColumn)
            if (includeDurationColumn) {
                add(MediaStore.MediaColumns.DURATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && includeTrashedOnly) {
                add(MediaStore.MediaColumns.IS_TRASHED)
                add(MediaStore.MediaColumns.DATE_EXPIRES)
            }
        }.toTypedArray()

        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            "$queryLabel query started volume=$volumeName uri=$baseUri",
        )
        val querySpec = buildQuerySpec(
            pathColumn = pathColumn,
            usesRelativePath = usesRelativePath,
            includeTrashedOnly = includeTrashedOnly,
            selectedCategory = selectedCategory,
        )
        val cursor = queryCursor(
            uri = baseUri,
            projection = projection,
            selection = querySpec.selection,
            selectionArgs = querySpec.selectionArgs,
            includeTrashedOnly = includeTrashedOnly,
        )
        if (cursor == null) {
            Log.d(
                FILE_RECOVERY_DEBUG_TAG,
                "$queryLabel cursor count = 0 (null cursor) volume=$volumeName",
            )
            return CandidateCollectionResult()
        }

        return cursor.use {
            val cursorCount = it.count
            Log.d(
                FILE_RECOVERY_DEBUG_TAG,
                "$queryLabel cursor count = $cursorCount volume=$volumeName",
            )
            val idIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val displayNameIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeTypeIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateModifiedIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = it.getColumnIndexOrThrow(pathColumn)
            val durationIndex = if (includeDurationColumn) {
                it.getColumnIndex(MediaStore.MediaColumns.DURATION)
            } else {
                -1
            }
            val trashedIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && includeTrashedOnly) {
                it.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            } else {
                -1
            }
            val expiresIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && includeTrashedOnly) {
                it.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
            } else {
                -1
            }
            val candidates = mutableListOf<RecoverableFile>()
            var inspectedCount = 0
            var inspectedBytes = 0L
            var normalSkippedCount = 0
            while (it.moveToNext()) {
                coroutineContext.ensureActive()
                inspectedCount += 1
                val id = it.getLong(idIndex)
                val displayName = it.getString(displayNameIndex) ?: "File $id"
                val mimeType = it.getString(mimeTypeIndex)
                val sizeBytes = it.getLong(sizeIndex)
                inspectedBytes += sizeBytes
                val dateModifiedMillis = it.getLong(dateModifiedIndex) * 1000L
                val relativePath = it.getString(pathIndex)?.normalizePath()
                val isTrashed = if (trashedIndex >= 0) it.getInt(trashedIndex) == 1 else includeTrashedOnly
                val durationMillis = if (durationIndex >= 0 && !it.isNull(durationIndex)) {
                    it.getLong(durationIndex)
                } else {
                    null
                }
                val dateExpiresMillis = if (expiresIndex >= 0 && !it.isNull(expiresIndex)) {
                    it.getLong(expiresIndex) * 1000L
                } else {
                    null
                }
                val file = buildRecoverableFile(
                    id = id,
                    uri = ContentUris.withAppendedId(baseUri, id),
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    dateModifiedMillis = dateModifiedMillis,
                    relativePath = relativePath,
                    explicitType = explicitType,
                    volumeName = volumeName,
                    isFromSaf = false,
                    isTrashed = isTrashed,
                    durationMillis = durationMillis,
                    dateExpiresMillis = dateExpiresMillis,
                    noMediaAncestor = false,
                )
                if (file.isRecoveryCandidate() && file.matchesSelectedCategory(selectedCategory)) {
                    candidates += file
                } else {
                    normalSkippedCount += 1
                }
            }
            Log.d(
                FILE_RECOVERY_DEBUG_TAG,
                "$queryLabel inspected=$inspectedCount candidates=${candidates.size} normalSkipped=$normalSkippedCount volume=$volumeName",
            )
            CandidateCollectionResult(
                candidates = candidates,
                inspectedCount = inspectedCount,
                inspectedBytes = inspectedBytes,
                normalSkippedCount = normalSkippedCount,
            )
        }
    }

    private fun queryCursor(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        includeTrashedOnly: Boolean,
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && includeTrashedOnly) {
        resolver.query(
            uri,
            projection,
            Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                if (selectionArgs != null) {
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
                putString(
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                )
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            },
            null,
        )
    } else {
        resolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )
    }

    private fun buildQuerySpec(
        pathColumn: String,
        usesRelativePath: Boolean,
        includeTrashedOnly: Boolean,
        selectedCategory: RecoveryCategory?,
    ): MediaQuerySpec {
        val clauses = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clauses += "${MediaStore.MediaColumns.IS_PENDING} = 0"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && includeTrashedOnly) {
            clauses += "${MediaStore.MediaColumns.IS_TRASHED} = 1"
        } else {
            clauses += "(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR $pathColumn LIKE ? OR $pathColumn LIKE ?)"
            selectionArgs += ".%"
            selectionArgs += if (usesRelativePath) ".%" else "%/.%"
            selectionArgs += "%/.%"
        }
        selectedCategory.pathScopePattern()?.let { pattern ->
            clauses += "LOWER($pathColumn) LIKE ?"
            selectionArgs += pattern
        }
        if (selectedCategory == RecoveryCategory.LargeFiles) {
            clauses += "${MediaStore.MediaColumns.SIZE} >= ?"
            selectionArgs += largeFileThresholdBytes.toString()
        }
        return MediaQuerySpec(
            selection = clauses.joinToString(" AND ").ifBlank { null },
            selectionArgs = selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray(),
        )
    }

    private suspend fun scanTree(
        root: DocumentFile,
        rootLabel: String,
        selectedCategory: RecoveryCategory?,
        onFile: suspend (RecoverableFile) -> Unit,
    ): TreeScanResult {
        var inspectedCount = 0
        var inspectedBytes = 0L
        var normalSkippedCount = 0

        suspend fun walk(
            directory: DocumentFile,
            relativeSegments: List<String>,
            hiddenAncestor: Boolean,
            noMediaAncestor: Boolean,
        ) {
            coroutineContext.ensureActive()
            val children = runCatching { directory.listFiles().toList() }.getOrElse { emptyList() }
            val hasNoMediaMarker = children.any { child -> child.name == ".nomedia" }
            children.forEach { child ->
                coroutineContext.ensureActive()
                val name = child.name ?: return@forEach
                if (child.isDirectory) {
                    walk(
                        directory = child,
                        relativeSegments = relativeSegments + name,
                        hiddenAncestor = hiddenAncestor || name.startsWith("."),
                        noMediaAncestor = noMediaAncestor || hasNoMediaMarker,
                    )
                } else if (child.isFile) {
                    val relativePath = (relativeSegments + name).joinToString("/").normalizePath()
                    val file = buildRecoverableFile(
                        id = relativePath.hashCode().toLong(),
                        uri = child.uri,
                        displayName = name,
                        mimeType = child.type,
                        sizeBytes = child.length(),
                        dateModifiedMillis = child.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                        relativePath = "$rootLabel/$relativePath",
                        explicitType = null,
                        volumeName = rootLabel,
                        isFromSaf = true,
                        isTrashed = false,
                        durationMillis = null,
                        dateExpiresMillis = null,
                        noMediaAncestor = noMediaAncestor || hasNoMediaMarker,
                        hiddenAncestor = hiddenAncestor,
                    )
                    inspectedCount += 1
                    inspectedBytes += file.sizeBytes
                    if (file.isRecoveryCandidate() && file.matchesSelectedCategory(selectedCategory)) {
                        onFile(file)
                    } else {
                        normalSkippedCount += 1
                    }
                }
            }
        }

        walk(
            directory = root,
            relativeSegments = emptyList(),
            hiddenAncestor = root.name?.startsWith(".") == true,
            noMediaAncestor = false,
        )
        return TreeScanResult(
            inspectedCount = inspectedCount,
            inspectedBytes = inspectedBytes,
            normalSkippedCount = normalSkippedCount,
        )
    }

    private fun buildRecoverableFile(
        id: Long,
        uri: Uri,
        displayName: String,
        mimeType: String?,
        sizeBytes: Long,
        dateModifiedMillis: Long,
        relativePath: String?,
        explicitType: RecoveryFileType?,
        detectedFileType: RecoveryFileType? = null,
        volumeName: String?,
        isFromSaf: Boolean,
        isTrashed: Boolean,
        durationMillis: Long?,
        dateExpiresMillis: Long?,
        noMediaAncestor: Boolean,
        hiddenAncestor: Boolean = false,
    ): RecoverableFile {
        val fileType = detectedFileType ?: explicitType ?: classifyFileType(
            mimeType = mimeType,
            displayName = displayName,
        )
        val path = relativePath.orEmpty()
        val isDotFile = displayName.startsWith(".")
        val hasHiddenDirectory = hiddenAncestor || path.hiddenPathSegments().isNotEmpty()
        val isHidden = noMediaAncestor || isDotFile || hasHiddenDirectory
        val hiddenReason = when {
            noMediaAncestor -> RecoveryFileHiddenReason.NoMediaDirectory
            isDotFile -> RecoveryFileHiddenReason.DotFile
            hasHiddenDirectory -> RecoveryFileHiddenReason.HiddenDirectory
            else -> null
        }
        val sources = buildSources(
            path = path,
            displayName = displayName,
            volumeName = volumeName,
            isTrashed = isTrashed,
            isHidden = isHidden,
            hiddenReason = hiddenReason,
            isFromSaf = isFromSaf,
        )
        val identity = buildIdentityKey(
            volumeName = volumeName,
            relativePath = path,
            displayName = displayName,
            sizeBytes = sizeBytes,
            fallbackUri = uri.toString(),
        )
        return RecoverableFile(
            id = identity,
            uriString = uri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            dateModifiedMillis = dateModifiedMillis,
            relativePath = relativePath,
            fileType = fileType,
            sources = sources,
            isHidden = isHidden,
            hiddenReason = hiddenReason,
            isTrashed = isTrashed,
            storageVolume = volumeName,
            durationMillis = durationMillis,
            dateExpiresMillis = dateExpiresMillis,
        )
    }

    private fun RecoverableFile.isRecoveryCandidate(): Boolean {
        return isTrashed || isHidden
    }

    private fun RecoverableFile.matchesSelectedCategory(category: RecoveryCategory?): Boolean {
        return when (category) {
            null -> true
            RecoveryCategory.Photos -> fileType == RecoveryFileType.Photo
            RecoveryCategory.Videos -> fileType == RecoveryFileType.Video
            RecoveryCategory.Audio -> fileType == RecoveryFileType.Audio
            RecoveryCategory.Documents -> fileType == RecoveryFileType.Document || fileType == RecoveryFileType.Archive
            RecoveryCategory.WhatsAppImages -> fileType == RecoveryFileType.Photo && RecoveryFileSource.WhatsApp in sources
            RecoveryCategory.WhatsAppVideos -> fileType == RecoveryFileType.Video && RecoveryFileSource.WhatsApp in sources
            RecoveryCategory.Downloads -> RecoveryFileSource.Downloads in sources
            RecoveryCategory.Screenshots -> RecoveryFileSource.Screenshots in sources
            RecoveryCategory.RecycleBin -> isTrashed
            RecoveryCategory.LargeFiles -> sizeBytes >= largeFileThresholdBytes
        }
    }

    private fun RecoveryCategory?.scansImages(): Boolean = this in setOf(
        null,
        RecoveryCategory.Photos,
        RecoveryCategory.WhatsAppImages,
        RecoveryCategory.Downloads,
        RecoveryCategory.Screenshots,
        RecoveryCategory.RecycleBin,
        RecoveryCategory.LargeFiles,
    )

    private fun RecoveryCategory?.scansVideos(): Boolean = this in setOf(
        null,
        RecoveryCategory.Videos,
        RecoveryCategory.WhatsAppVideos,
        RecoveryCategory.Downloads,
        RecoveryCategory.RecycleBin,
        RecoveryCategory.LargeFiles,
    )

    private fun RecoveryCategory?.scansAudio(): Boolean = this in setOf(
        null,
        RecoveryCategory.Audio,
        RecoveryCategory.Downloads,
        RecoveryCategory.RecycleBin,
        RecoveryCategory.LargeFiles,
    )

    private fun RecoveryCategory?.scansFiles(): Boolean = this in setOf(
        null,
        RecoveryCategory.Documents,
        RecoveryCategory.Downloads,
        RecoveryCategory.RecycleBin,
        RecoveryCategory.LargeFiles,
    )

    private fun RecoveryCategory?.pathScopePattern(): String? = when (this) {
        RecoveryCategory.WhatsAppImages,
        RecoveryCategory.WhatsAppVideos -> "%whatsapp%"
        RecoveryCategory.Downloads -> "%download%"
        RecoveryCategory.Screenshots -> "%screenshot%"
        else -> null
    }

    private fun String.matchesFilesystemScope(category: RecoveryCategory?): Boolean {
        val normalizedPath = lowercase()
        return when (category) {
            RecoveryCategory.Downloads -> normalizedPath.contains("download")
            RecoveryCategory.Screenshots -> normalizedPath.contains("screenshot")
            RecoveryCategory.WhatsAppImages,
            RecoveryCategory.WhatsAppVideos -> normalizedPath.contains("whatsapp")
            else -> true
        }
    }

    private fun scanLocationTypesFor(category: RecoveryCategory?): List<RecoveryScanLocationType> {
        return buildList {
            add(RecoveryScanLocationType.MediaPermissions)
            add(RecoveryScanLocationType.StorageVolumes)
            if (category != RecoveryCategory.RecycleBin && category.scansImages()) add(RecoveryScanLocationType.MediaStoreImages)
            if (category != RecoveryCategory.RecycleBin && category.scansVideos()) add(RecoveryScanLocationType.MediaStoreVideos)
            if (category != RecoveryCategory.RecycleBin && category.scansAudio()) add(RecoveryScanLocationType.MediaStoreAudio)
            if (category != RecoveryCategory.RecycleBin && category.scansFiles()) add(RecoveryScanLocationType.MediaStoreFiles)
            add(RecoveryScanLocationType.MediaStoreTrash)
            if (category != RecoveryCategory.RecycleBin) add(RecoveryScanLocationType.AuthorizedFolders)
            if (category != RecoveryCategory.RecycleBin) add(RecoveryScanLocationType.AccessibleStorage)
            add(RecoveryScanLocationType.ValidateCandidates)
            add(RecoveryScanLocationType.RemoveDuplicates)
            add(RecoveryScanLocationType.PersistResults)
        }
    }

    private fun classifyFileType(
        mimeType: String?,
        displayName: String,
    ): RecoveryFileType {
        val normalizedMime = mimeType.orEmpty().lowercase()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when {
            normalizedMime.startsWith("image/") -> RecoveryFileType.Photo
            normalizedMime.startsWith("video/") -> RecoveryFileType.Video
            normalizedMime.startsWith("audio/") -> RecoveryFileType.Audio
            normalizedMime.startsWith("text/") ||
                normalizedMime == "application/pdf" ||
                normalizedMime.contains("document") ||
                normalizedMime.contains("sheet") ||
                normalizedMime.contains("presentation") -> RecoveryFileType.Document
            extension in archiveExtensions -> RecoveryFileType.Archive
            extension in documentExtensions -> RecoveryFileType.Document
            else -> RecoveryFileType.Other
        }
    }

    private fun buildSources(
        path: String,
        displayName: String,
        volumeName: String?,
        isTrashed: Boolean,
        isHidden: Boolean,
        hiddenReason: RecoveryFileHiddenReason?,
        isFromSaf: Boolean,
    ): Set<RecoveryFileSource> {
        val normalizedPath = path.lowercase()
        val normalizedName = displayName.lowercase()
        return buildSet {
            if (normalizedPath.contains("dcim/camera")) add(RecoveryFileSource.Camera)
            if (normalizedPath.contains("screenshot") || normalizedName.contains("screenshot")) {
                add(RecoveryFileSource.Screenshots)
            }
            if (normalizedPath.contains("download")) add(RecoveryFileSource.Downloads)
            if (normalizedPath.contains("whatsapp")) add(RecoveryFileSource.WhatsApp)
            if (normalizedPath.contains("telegram") ||
                normalizedPath.contains("messenger") ||
                normalizedPath.contains("signal")
            ) {
                add(RecoveryFileSource.MessagingMedia)
            }
            if (normalizedPath.contains("bluetooth")) add(RecoveryFileSource.Bluetooth)
            if (isHidden) add(RecoveryFileSource.Hidden)
            if (hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory) {
                add(RecoveryFileSource.NoMedia)
            }
            if (isTrashed) add(RecoveryFileSource.RecycleBin)
            if (isFromSaf) add(RecoveryFileSource.Saf)
            if (volumeName != null && volumeName.isSdCardVolume()) add(RecoveryFileSource.SdCard)
            if (isEmpty()) add(RecoveryFileSource.Other)
        }
    }

    private fun buildIdentityKey(
        volumeName: String?,
        relativePath: String,
        displayName: String,
        sizeBytes: Long,
        fallbackUri: String,
    ): String {
        val normalizedPath = relativePath.normalizePath()
        return if (normalizedPath.isNotBlank()) {
            "${volumeName.orEmpty()}|${normalizedPath.lowercase()}|${displayName.lowercase()}|$sizeBytes"
        } else {
            fallbackUri
        }
    }

    private fun mergeFiles(
        current: RecoverableFile,
        incoming: RecoverableFile,
    ): RecoverableFile {
        return current.copy(
            mimeType = current.mimeType ?: incoming.mimeType,
            sizeBytes = maxOf(current.sizeBytes, incoming.sizeBytes),
            dateModifiedMillis = maxOf(current.dateModifiedMillis, incoming.dateModifiedMillis),
            relativePath = current.relativePath ?: incoming.relativePath,
            sources = current.sources + incoming.sources,
            isHidden = current.isHidden || incoming.isHidden,
            hiddenReason = current.hiddenReason ?: incoming.hiddenReason,
            isTrashed = current.isTrashed || incoming.isTrashed,
            storageVolume = current.storageVolume ?: incoming.storageVolume,
            durationMillis = current.durationMillis ?: incoming.durationMillis,
            dateExpiresMillis = current.dateExpiresMillis ?: incoming.dateExpiresMillis,
        )
    }

    private fun computeCategoryCounts(
        files: Collection<RecoverableFile>,
    ): Map<RecoveryResultFilter, Int> {
        return RecoveryResultFilter.entries.associateWith { filter ->
            files.count { file -> file.matchesFilter(filter) }
        }
    }

    private fun RecoverableFile.matchesFilter(filter: RecoveryResultFilter): Boolean {
        return when (filter) {
            RecoveryResultFilter.All -> true
            RecoveryResultFilter.Photos -> fileType == RecoveryFileType.Photo
            RecoveryResultFilter.Videos -> fileType == RecoveryFileType.Video
            RecoveryResultFilter.Audio -> fileType == RecoveryFileType.Audio
            RecoveryResultFilter.Documents -> fileType == RecoveryFileType.Document || fileType == RecoveryFileType.Archive
            RecoveryResultFilter.WhatsApp -> RecoveryFileSource.WhatsApp in sources
            RecoveryResultFilter.Downloads -> RecoveryFileSource.Downloads in sources
            RecoveryResultFilter.Screenshots -> RecoveryFileSource.Screenshots in sources
            RecoveryResultFilter.Hidden -> isHidden
            RecoveryResultFilter.RecycleBin -> isTrashed
            RecoveryResultFilter.LargeFiles -> sizeBytes >= largeFileThresholdBytes
            RecoveryResultFilter.Other -> fileType == RecoveryFileType.Other ||
                (sources.contains(RecoveryFileSource.Other) &&
                    fileType != RecoveryFileType.Photo &&
                    fileType != RecoveryFileType.Video &&
                    fileType != RecoveryFileType.Audio &&
                    fileType != RecoveryFileType.Document &&
                    fileType != RecoveryFileType.Archive)
        }
    }

    private fun validateReadable(file: RecoverableFile): Boolean {
        if (file.sizeBytes <= 0L) return false
        val uri = Uri.parse(file.uriString)

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return false
            val f = File(path)
            return f.exists() && f.length() > 0L
        }

        val descriptorReadable = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize != 0L || file.sizeBytes > 0L
            }
        }.getOrNull()

        if (descriptorReadable == true) {
            return true
        }

        if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
            return runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    input.read() != -1 || file.sizeBytes > 0L
                } ?: false
            }.getOrDefault(false)
        }

        return true
    }

    private fun currentAccessSummary(): AccessSummary {
        val hasLegacyRead = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        val hasImages = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        val hasVideos = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
        val hasAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        val hasSelectedVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val hasAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        val safRoots = resolver.persistedUriPermissions.count { permission -> permission.isReadPermission }
        val grantedSourceCount = listOf(hasLegacyRead, hasImages, hasVideos, hasAudio, hasSelectedVisual).count { it } +
            safRoots
        val labels = buildList {
            if (hasLegacyRead) add("Shared storage")
            if (hasImages) add("Images")
            if (hasVideos) add("Videos")
            if (hasAudio) add("Audio")
            if (hasSelectedVisual) add("Selected photos/videos")
            if (safRoots > 0) add("$safRoots folder grant(s)")
        }
        return AccessSummary(
            hasLegacyRead = hasLegacyRead,
            hasImages = hasImages,
            hasVideos = hasVideos,
            hasAudio = hasAudio,
            hasSelectedVisual = hasSelectedVisual,
            hasAllFilesAccess = hasAllFilesAccess,
            safRoots = safRoots,
            grantedSourceCount = grantedSourceCount,
            summaryLabel = labels.joinToString(" • "),
        )
    }

    private fun availableMediaStoreVolumes(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
                .ifEmpty { setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
                .sorted()
        } else {
            listOf("external")
        }
    }

    private fun imageUriForVolume(volumeName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(volumeName)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun videoUriForVolume(volumeName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(volumeName)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun audioUriForVolume(volumeName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(volumeName)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun fileUriForVolume(volumeName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(volumeName)
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun String.normalizePath(): String = replace('\\', '/').trim().trim('/')

    private fun String.hiddenPathSegments(): List<String> {
        return split('/')
            .map { segment -> segment.trim() }
            .filter { segment -> segment.isNotEmpty() && segment.startsWith(".") }
    }

    private fun String.toDisplayVolumeLabel(): String {
        return when (this) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY, "external" -> "Internal storage"
            else -> "Volume $this"
        }
    }

    private fun String.isSdCardVolume(): Boolean {
        return this != MediaStore.VOLUME_EXTERNAL_PRIMARY &&
            this != "external" &&
            this != MediaStore.VOLUME_EXTERNAL
    }

    private fun inferMimeType(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when {
            extension in imageExtensions -> "image/*"
            extension in videoExtensions -> "video/*"
            extension in audioExtensions -> "audio/*"
            extension == "pdf" -> "application/pdf"
            extension in archiveExtensions -> "application/octet-stream"
            extension in documentExtensions -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    private fun findAvailableName(
        root: DocumentFile,
        originalName: String,
    ): String {
        if (root.findFile(originalName) == null) {
            return originalName
        }
        val extension = originalName.substringAfterLast('.', missingDelimiterValue = "")
        val stem = originalName.removeSuffix(if (extension.isBlank()) "" else ".$extension")
        var suffix = 1
        while (true) {
            val candidate = if (extension.isBlank()) {
                "$stem ($suffix)"
            } else {
                "$stem ($suffix).$extension"
            }
            if (root.findFile(candidate) == null) {
                return candidate
            }
            suffix += 1
        }
    }

    private fun logDebugSummary(
        session: RecoveryScanSession,
        itemsInspected: Int,
        duplicatesRemoved: Int,
        normalSkipped: Int,
        unreadableSkipped: Int,
    ) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val counts = computeCategoryCounts(session.files)
        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            buildString {
                appendLine("Full Device Scan completed")
                appendLine("Inspected: $itemsInspected")
                appendLine("Photos: ${counts[RecoveryResultFilter.Photos] ?: 0}")
                appendLine("Videos: ${counts[RecoveryResultFilter.Videos] ?: 0}")
                appendLine("Audio: ${counts[RecoveryResultFilter.Audio] ?: 0}")
                appendLine("Documents: ${counts[RecoveryResultFilter.Documents] ?: 0}")
                appendLine("Hidden: ${counts[RecoveryResultFilter.Hidden] ?: 0}")
                appendLine("Trash: ${counts[RecoveryResultFilter.RecycleBin] ?: 0}")
                appendLine("WhatsApp: ${counts[RecoveryResultFilter.WhatsApp] ?: 0}")
                appendLine("Downloads: ${counts[RecoveryResultFilter.Downloads] ?: 0}")
                appendLine("Normal skipped: $normalSkipped")
                appendLine("Duplicates removed: $duplicatesRemoved")
                appendLine("Unreadable skipped: $unreadableSkipped")
                append("Final unique results: ${session.files.size}")
            },
        )
    }

    private data class AccessSummary(
        val hasLegacyRead: Boolean,
        val hasImages: Boolean,
        val hasVideos: Boolean,
        val hasAudio: Boolean,
        val hasSelectedVisual: Boolean,
        val hasAllFilesAccess: Boolean,
        val safRoots: Int,
        val grantedSourceCount: Int,
        val summaryLabel: String,
    ) {
        val canReadImages: Boolean
            get() = hasLegacyRead || hasImages || hasSelectedVisual

        val canReadVideos: Boolean
            get() = hasLegacyRead || hasVideos || hasSelectedVisual

        val canReadAudio: Boolean
            get() = hasLegacyRead || hasAudio

        val canReadMediaStoreFiles: Boolean
            get() = hasLegacyRead || hasAllFilesAccess

        val hasAnyAccess: Boolean
            get() = canReadImages || canReadVideos || canReadAudio || canReadMediaStoreFiles || safRoots > 0
    }

    private data class MediaQuerySpec(
        val selection: String? = null,
        val selectionArgs: Array<String>? = null,
    )

    private data class CandidateCollectionResult(
        val candidates: List<RecoverableFile> = emptyList(),
        val inspectedCount: Int = 0,
        val inspectedBytes: Long = 0L,
        val normalSkippedCount: Int = 0,
    )

    private data class TreeScanResult(
        val inspectedCount: Int,
        val inspectedBytes: Long,
        val normalSkippedCount: Int,
    )

    private companion object {
        private const val largeFileThresholdBytes = 100L * 1024L * 1024L
        private val suspiciousExtensions = setOf("dat", "bin", "cache", "tmp")
        private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
        private val videoExtensions = setOf("mp4", "mkv", "3gp", "webm", "mov", "avi")
        private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "amr", "opus")
        private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz")
        private val documentExtensions = setOf(
            "pdf",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "ppt",
            "pptx",
            "txt",
            "csv",
            "rtf",
        )
    }
}
