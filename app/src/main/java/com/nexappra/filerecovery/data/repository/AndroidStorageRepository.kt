package com.nexappra.filerecovery.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.nexappra.filerecovery.domain.model.StorageInfo
import com.nexappra.filerecovery.domain.repository.StorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidStorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : StorageRepository {
    override suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        StorageInfo(
            totalBytes = statFs.totalBytes,
            availableBytes = statFs.availableBytes,
        )
    }

    /**
     * Traverses shared storage recursively including root files, hidden folders (.nomedia, vaults,
     * status savers, app trash, and hidden directories).
     */
    /**
     * Traverses shared storage, including hidden folders and folders excluded from MediaStore with
     * a .nomedia marker. Android private app-specific folders remain outside this scan.
     */
    suspend fun scanAccessibleSharedStorage(
        includePath: (String) -> Boolean,
        onFile: suspend (AccessibleStorageFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!canTraverseSharedStorage()) return@withContext
        val root = Environment.getExternalStorageDirectory()
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return@withContext
        val rootPrefix = rootPath.trimEnd(File.separatorChar) + File.separator
        val visitedDirectories = HashSet<String>()

        suspend fun walk(directory: File, hiddenAncestor: Boolean, noMediaAncestor: Boolean) {
            coroutineContext.ensureActive()
            val canonicalDirectory = runCatching { directory.canonicalPath }.getOrNull() ?: return
            if (canonicalDirectory != rootPath && !canonicalDirectory.startsWith(rootPrefix)) return
            if (!visitedDirectories.add(canonicalDirectory)) return
            val children = runCatching { directory.listFiles().orEmpty() }.getOrDefault(emptyArray())
            val hasNoMedia = noMediaAncestor || children.any { child ->
                child.name.equals(".nomedia", ignoreCase = true)
            }
            children.forEach { child ->
                coroutineContext.ensureActive()
                val canonical = runCatching { child.canonicalPath }.getOrNull() ?: return@forEach
                if (canonical != rootPath && !canonical.startsWith(rootPrefix)) return@forEach
                if (child.isDirectory && child.isRestrictedSharedDirectory(rootPath)) return@forEach
                val relativePath = canonical.removePrefix(rootPath)
                    .trimStart(File.separatorChar)
                    .replace('\\', '/')
                val isHidden = child.name.startsWith(".") ||
                    child.name.contains("vault", ignoreCase = true) ||
                    child.name.contains("trash", ignoreCase = true) ||
                    child.name.contains("statuses", ignoreCase = true) ||
                    child.name.contains("private", ignoreCase = true)
                if (child.isDirectory) {
                    walk(child, hiddenAncestor || isHidden, hasNoMedia)
                } else if (child.isFile && !child.name.equals(".nomedia", ignoreCase = true) && includePath(relativePath)) {
                    onFile(AccessibleStorageFile(child, relativePath, hiddenAncestor || isHidden, hasNoMedia))
                }
            }
        }

        walk(root, root.name.startsWith("."), false)
    }
    private fun canTraverseSharedStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    private fun File.isRestrictedSharedDirectory(rootPath: String): Boolean {
        val relativePath = runCatching { canonicalPath.removePrefix(rootPath) }.getOrDefault("")
            .trimStart(File.separatorChar)
            .replace('\\', '/')
        return relativePath == "Android/data" || relativePath.startsWith("Android/data/") ||
            relativePath == "Android/obb" || relativePath.startsWith("Android/obb/")
    }
}

data class AccessibleStorageFile(
    val file: File,
    val relativePath: String,
    val hiddenAncestor: Boolean,
    val noMediaAncestor: Boolean,
)
