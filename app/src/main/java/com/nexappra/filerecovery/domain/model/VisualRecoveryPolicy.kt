package com.nexappra.filerecovery.domain.model

import java.util.Locale

/** Photos and videos require a hidden or recycle-bin context, even in combined scans. */
object VisualRecoveryPolicy {
    private val recycleDirectories = setOf(
        ".trash", ".trashbin", ".trashed", ".recycle", ".recyclebin", ".recycle_bin",
        "trash", "trashbin", "recyclebin", "recycle_bin", "recycle bin", "\$recycle.bin",
    )
    private val quickParents = setOf("dcim", "pictures", "movies", "videos", "download", "downloads", "whatsapp", "telegram")
    private val trashedFileName = Regex("^\\.trashed-\\d+-.*")
    private val trashedPrefix = Regex("^\\.trashed-\\d+-")

    fun isVisual(type: RecoveryFileType): Boolean = type == RecoveryFileType.Photo || type == RecoveryFileType.Video

    fun isThumbnailLocation(path: String): Boolean = parts(path)
        .any { it.equals(".thumbnails", true) || it.equals("thumbnails", true) }

    fun recoveryName(safeName: String, fallback: String = "recovered_media"): String =
        safeName.replace(trashedPrefix, "").trimStart('.').ifBlank { fallback }

    fun hiddenReason(name: String, path: String, noMedia: Boolean = false): RecoveryFileHiddenReason? = when {
        noMedia -> RecoveryFileHiddenReason.NoMediaDirectory
        name.startsWith('.') -> RecoveryFileHiddenReason.DotFile
        parts(path).any { it.startsWith('.') } -> RecoveryFileHiddenReason.HiddenDirectory
        else -> null
    }

    // A recycle-bin location does not by itself prove a provider-confirmed deletion.
    fun isRecycleLocation(name: String, path: String): Boolean =
        parts(path).any { it.lowercase(Locale.ROOT) in recycleDirectories } || trashedFileName.matches(name)

    fun isCandidate(name: String, path: String, trashed: Boolean = false, noMedia: Boolean = false): Boolean =
        name != ".nomedia" && (trashed || hiddenReason(name, path, noMedia) != null || isRecycleLocation(name, path))

    // Retain the previous visual-only policy for saved-selection migration.
    fun accepts(file: RecoverableFile): Boolean = !isVisual(file.fileType) || hasRecoveryContext(file)

    fun hasRecoveryContext(file: RecoverableFile): Boolean =
        isCandidate(file.displayName, file.relativePath.orEmpty(), file.isTrashed,
            file.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory || RecoveryFileSource.NoMedia in file.sources) ||
        (file.displayName != ".nomedia" && (file.isHidden || RecoveryFileSource.RecycleBin in file.sources))

    fun exploreQuickDirectory(path: String, noMedia: Boolean): Boolean =
        noMedia || isCandidate("", path) || parts(path).firstOrNull()?.lowercase(Locale.ROOT) in quickParents ||
            parts(path).take(2).map { it.lowercase(Locale.ROOT) } == listOf("android", "media")

    /** Match hidden/recycle folder components rather than ordinary gallery paths. */
    fun indexedPathPatterns(): List<String> = listOf(".%/%", "%/.%/%") + recycleDirectories.flatMap { name ->
        val escaped = name.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%")
        listOf("$escaped/%", "%/$escaped/%")
    }

    private fun parts(path: String): List<String> = path.split('/', '\\').filter { it.isNotEmpty() }
}
