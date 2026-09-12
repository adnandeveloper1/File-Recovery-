package com.nexappra.filerecovery.data.repository

import android.content.Context
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
     * File APIs are used only when Android grants broad shared-storage access. On scoped-storage
     * devices the recovery repository falls back to user-authorized SAF trees.
     */
    suspend fun scanAccessibleSharedStorage(
        includePath: (String) -> Boolean,
        onFile: suspend (AccessibleStorageFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!canTraverseSharedStorage()) return@withContext
        val root = Environment.getExternalStorageDirectory()
        val rootPath = root.canonicalPath
        val roots = root.listFiles()
            .orEmpty()
            .filter { file -> file.isDirectory && !file.isRestrictedSharedDirectory(rootPath) }

        suspend fun walk(directory: File, hiddenAncestor: Boolean, noMediaAncestor: Boolean) {
            coroutineContext.ensureActive()
            val children = runCatching { directory.listFiles().orEmpty() }.getOrDefault(emptyArray())
            val hasNoMedia = noMediaAncestor || children.any { child -> child.name == ".nomedia" }
            children.forEach { child ->
                coroutineContext.ensureActive()
                val canonical = runCatching { child.canonicalPath }.getOrNull() ?: return@forEach
                if (!canonical.startsWith(rootPath)) return@forEach
                if (child.isDirectory && child.isRestrictedSharedDirectory(rootPath)) return@forEach
                val relativePath = canonical.removePrefix(rootPath).trimStart(File.separatorChar).replace('\\', '/')
                if (child.isDirectory) {
                    walk(child, hiddenAncestor || child.name.startsWith("."), hasNoMedia)
                } else if (child.isFile && child.name != ".nomedia" && includePath(relativePath)) {
                    onFile(AccessibleStorageFile(child, relativePath, hiddenAncestor || child.name.startsWith("."), hasNoMedia))
                }
            }
        }
        roots.forEach { rootDirectory -> walk(rootDirectory, rootDirectory.name.startsWith("."), false) }
    }

    private fun canTraverseSharedStorage(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
