package com.nexappra.filerecovery.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import com.nexappra.filerecovery.domain.model.PhotoResultFilter
import com.nexappra.filerecovery.domain.model.PhotoScanLocationState
import com.nexappra.filerecovery.domain.model.PhotoScanLocationType
import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.PhotoScanSession
import com.nexappra.filerecovery.domain.model.PhotoScanSnapshot
import com.nexappra.filerecovery.domain.model.PhotoScanStatus
import com.nexappra.filerecovery.domain.model.RecoverablePhoto
import com.nexappra.filerecovery.domain.model.ScanLocationStatus
import com.nexappra.filerecovery.domain.repository.PhotoScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class MediaStorePhotoScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoScanRepository {

    private val sessions = ConcurrentHashMap<String, PhotoScanSession>()

    override suspend fun performPhotoScan(
        mode: PhotoScanMode,
        onSnapshot: suspend (PhotoScanSnapshot) -> Unit,
    ): PhotoScanSession = withContext(Dispatchers.IO) {
        val orderedLocations = orderedLocationsFor(mode)
        val allPhotos = queryPhotos(mode)
        val groupedPhotos = orderedLocations.associateWith { type ->
            allPhotos.filter { photo -> photo.locationType == type }
        }
        val initialStates = orderedLocations.map { type ->
            PhotoScanLocationState(
                type = type,
                status = ScanLocationStatus.Pending,
                totalCount = groupedPhotos[type].orEmpty().size,
                displayPath = defaultPathFor(type),
            )
        }
        val startedAt = System.currentTimeMillis()
        val scanStart = SystemClock.elapsedRealtime()
        val totalItems = groupedPhotos.values.sumOf { photos -> photos.size }
        var bytesScanned = 0L
        var processedItems = 0
        val recoveredPhotos = mutableListOf<RecoverablePhoto>()
        var states = initialStates

        onSnapshot(
            PhotoScanSnapshot(
                mode = mode,
                status = PhotoScanStatus.Preparing,
                progress = if (totalItems == 0) 0f else 0f,
                totalFilesFound = 0,
                bytesScanned = 0L,
                estimatedRemainingMillis = null,
                currentLocation = null,
                locations = states,
                elapsedMillis = 0L,
            ),
        )

        if (totalItems == 0) {
            val emptySession = PhotoScanSession(
                id = UUID.randomUUID().toString(),
                mode = mode,
                durationMillis = 0L,
                totalBytesScanned = 0L,
                photos = emptyList(),
                locations = states.map { state ->
                    state.copy(status = ScanLocationStatus.Completed)
                },
                completedAtMillis = startedAt,
            )
            sessions[emptySession.id] = emptySession
            onSnapshot(
                PhotoScanSnapshot(
                    mode = mode,
                    status = PhotoScanStatus.Completed,
                    progress = 1f,
                    totalFilesFound = 0,
                    bytesScanned = 0L,
                    estimatedRemainingMillis = 0L,
                    currentLocation = null,
                    locations = emptySession.locations,
                    elapsedMillis = 0L,
                ),
            )
            return@withContext emptySession
        }

        orderedLocations.forEach { locationType ->
            coroutineContext.ensureActive()

            val locationItems = groupedPhotos[locationType].orEmpty()
            states = states.map { state ->
                when (state.type) {
                    locationType -> state.copy(
                        status = ScanLocationStatus.Scanning,
                        displayPath = defaultPathFor(locationType),
                    )
                    else -> state
                }
            }

            onSnapshot(
                buildSnapshot(
                    mode = mode,
                    status = PhotoScanStatus.Scanning,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    bytesScanned = bytesScanned,
                    states = states,
                    currentLocation = states.firstOrNull { state -> state.type == locationType },
                    elapsedMillis = SystemClock.elapsedRealtime() - scanStart,
                ),
            )

            locationItems.forEachIndexed { index, photo ->
                coroutineContext.ensureActive()
                recoveredPhotos += photo
                processedItems += 1
                bytesScanned += photo.sizeBytes

                states = states.map { state ->
                    if (state.type == locationType) {
                        state.copy(
                            status = ScanLocationStatus.Scanning,
                            foundCount = index + 1,
                            totalCount = locationItems.size,
                            displayPath = photo.relativePath?.toDisplayPath() ?: defaultPathFor(locationType),
                        )
                    } else {
                        state
                    }
                }

                val shouldPublish = index == locationItems.lastIndex || processedItems % 24 == 0
                if (shouldPublish) {
                    onSnapshot(
                        buildSnapshot(
                            mode = mode,
                            status = PhotoScanStatus.Scanning,
                            processedItems = processedItems,
                            totalItems = totalItems,
                            bytesScanned = bytesScanned,
                            states = states,
                            currentLocation = states.firstOrNull { state -> state.type == locationType },
                            elapsedMillis = SystemClock.elapsedRealtime() - scanStart,
                        ),
                    )
                }
            }

            states = states.map { state ->
                if (state.type == locationType) {
                    state.copy(
                        status = ScanLocationStatus.Completed,
                        foundCount = locationItems.size,
                        totalCount = locationItems.size,
                    )
                } else {
                    state
                }
            }

            onSnapshot(
                buildSnapshot(
                    mode = mode,
                    status = PhotoScanStatus.Scanning,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    bytesScanned = bytesScanned,
                    states = states,
                    currentLocation = states.firstOrNull { state -> state.type == locationType },
                    elapsedMillis = SystemClock.elapsedRealtime() - scanStart,
                ),
            )
        }

        val durationMillis = SystemClock.elapsedRealtime() - scanStart
        val finalSession = PhotoScanSession(
            id = UUID.randomUUID().toString(),
            mode = mode,
            durationMillis = durationMillis,
            totalBytesScanned = bytesScanned,
            photos = recoveredPhotos.sortedByDescending { photo -> photo.dateModifiedMillis },
            locations = states,
            completedAtMillis = System.currentTimeMillis(),
        )
        sessions[finalSession.id] = finalSession

        onSnapshot(
            buildSnapshot(
                mode = mode,
                status = PhotoScanStatus.Completed,
                processedItems = processedItems,
                totalItems = totalItems,
                bytesScanned = bytesScanned,
                states = states,
                currentLocation = states.lastOrNull { state -> state.foundCount > 0 },
                elapsedMillis = durationMillis,
            ),
        )

        finalSession
    }

    override suspend fun getSession(sessionId: String): PhotoScanSession? = sessions[sessionId]

    private suspend fun queryPhotos(mode: PhotoScanMode): List<RecoverablePhoto> = withContext(Dispatchers.IO) {
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val usesRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pathColumn = if (usesRelativePath) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            pathColumn,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
        )
        val quickSelectionPatterns = if (usesRelativePath) {
            arrayOf(
                "DCIM/Camera/%",
                "%Screenshots/%",
                "Download/%",
                "Pictures/%",
                "DCIM/%",
            )
        } else {
            arrayOf(
                "%/DCIM/Camera/%",
                "%/Screenshots/%",
                "%/Download/%",
                "%/Pictures/%",
                "%/DCIM/%",
            )
        }

        val selection = buildString {
            if (mode == PhotoScanMode.Quick) {
                append("(")
                append(quickSelectionPatterns.joinToString(" OR ") { "$pathColumn LIKE ?" })
                append(")")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isNotBlank()) append(" AND ")
                append("${MediaStore.MediaColumns.IS_PENDING} = 0")
            }
        }.ifBlank { null }

        val selectionArgs = if (mode == PhotoScanMode.Quick) quickSelectionPatterns else null
        val photos = mutableListOf<RecoverablePhoto>()

        context.contentResolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)
            val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()

                val id = cursor.getLong(idIndex)
                val displayName = cursor.getString(nameIndex) ?: "Image $id"
                val sizeBytes = cursor.getLong(sizeIndex)
                val modifiedMillis = cursor.getLong(modifiedIndex) * 1000L
                val relativePath = cursor.getString(pathIndex)
                val bucketName = cursor.getString(bucketIndex)
                val locationType = classifyLocation(
                    path = relativePath.orEmpty(),
                    bucketName = bucketName.orEmpty(),
                )
                val filter = when (locationType) {
                    PhotoScanLocationType.Camera -> PhotoResultFilter.Camera
                    PhotoScanLocationType.Screenshots -> PhotoResultFilter.Screenshots
                    PhotoScanLocationType.Downloads -> PhotoResultFilter.Downloads
                    PhotoScanLocationType.Pictures,
                    PhotoScanLocationType.Gallery,
                    PhotoScanLocationType.OtherMedia -> PhotoResultFilter.Photos
                }

                photos += RecoverablePhoto(
                    id = id,
                    uriString = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ).toString(),
                    displayName = displayName,
                    sizeBytes = sizeBytes,
                    dateModifiedMillis = modifiedMillis,
                    relativePath = relativePath,
                    bucketName = bucketName,
                    filter = filter,
                    locationType = locationType,
                )
            }
        }

        photos
    }

    private fun buildSnapshot(
        mode: PhotoScanMode,
        status: PhotoScanStatus,
        processedItems: Int,
        totalItems: Int,
        bytesScanned: Long,
        states: List<PhotoScanLocationState>,
        currentLocation: PhotoScanLocationState?,
        elapsedMillis: Long,
    ): PhotoScanSnapshot {
        val progress = if (totalItems > 0) {
            processedItems.toFloat() / totalItems.toFloat()
        } else {
            null
        }
        val estimatedRemainingMillis = if (progress != null && progress > 0f && status == PhotoScanStatus.Scanning) {
            ((elapsedMillis / progress) - elapsedMillis).toLong().coerceAtLeast(0L)
        } else if (status == PhotoScanStatus.Completed) {
            0L
        } else {
            null
        }

        return PhotoScanSnapshot(
            mode = mode,
            status = status,
            progress = progress,
            totalFilesFound = processedItems,
            bytesScanned = bytesScanned,
            estimatedRemainingMillis = estimatedRemainingMillis,
            currentLocation = currentLocation,
            locations = states,
            elapsedMillis = elapsedMillis,
        )
    }

    private fun orderedLocationsFor(mode: PhotoScanMode): List<PhotoScanLocationType> = when (mode) {
        PhotoScanMode.Quick -> listOf(
            PhotoScanLocationType.Camera,
            PhotoScanLocationType.Screenshots,
            PhotoScanLocationType.Downloads,
            PhotoScanLocationType.Pictures,
            PhotoScanLocationType.Gallery,
        )

        PhotoScanMode.Deep -> listOf(
            PhotoScanLocationType.Camera,
            PhotoScanLocationType.Screenshots,
            PhotoScanLocationType.Downloads,
            PhotoScanLocationType.Pictures,
            PhotoScanLocationType.Gallery,
            PhotoScanLocationType.OtherMedia,
        )
    }

    private fun classifyLocation(
        path: String,
        bucketName: String,
    ): PhotoScanLocationType {
        val normalizedPath = path.lowercase()
        val normalizedBucket = bucketName.lowercase()

        return when {
            "screenshot" in normalizedPath || "screenshot" in normalizedBucket -> PhotoScanLocationType.Screenshots
            normalizedPath.contains("dcim/camera") || normalizedBucket == "camera" -> PhotoScanLocationType.Camera
            normalizedPath.contains("download") || normalizedBucket == "download" || normalizedBucket == "downloads" ->
                PhotoScanLocationType.Downloads
            normalizedPath.contains("pictures") -> PhotoScanLocationType.Pictures
            normalizedPath.contains("dcim") || normalizedBucket.contains("gallery") -> PhotoScanLocationType.Gallery
            else -> PhotoScanLocationType.OtherMedia
        }
    }

    private fun defaultPathFor(type: PhotoScanLocationType): String = when (type) {
        PhotoScanLocationType.Camera -> "/storage/emulated/0/DCIM/Camera"
        PhotoScanLocationType.Screenshots -> "/storage/emulated/0/Pictures/Screenshots"
        PhotoScanLocationType.Downloads -> "/storage/emulated/0/Download"
        PhotoScanLocationType.Pictures -> "/storage/emulated/0/Pictures"
        PhotoScanLocationType.Gallery -> "/storage/emulated/0/DCIM"
        PhotoScanLocationType.OtherMedia -> "Accessible media folders"
    }

    private fun String.toDisplayPath(): String = replace('\\', '/').trimEnd('/')
}
