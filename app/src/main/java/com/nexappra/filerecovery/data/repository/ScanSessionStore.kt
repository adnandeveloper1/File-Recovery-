package com.nexappra.filerecovery.data.repository

import android.content.Context
import android.util.AtomicFile
import com.nexappra.filerecovery.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Private, bounded scan metadata survives process recreation; media is never copied here. */
@Singleton
class ScanSessionStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory get() = File(context.cacheDir, "scan_sessions").apply { mkdirs() }

    @Synchronized
    fun save(session: RecoveryScanSession) {
        require(session.id.matches(Regex("[a-f0-9-]{36}")))
        val json = JSONObject().put("id", session.id).put("mode", session.mode.name)
            .put("category", session.selectedCategory?.name).put("duration", session.durationMillis)
            .put("bytes", session.totalBytesScanned).put("completed", session.completedAtMillis)
            .put("partial", session.isPartial).put("warnings", JSONArray(session.warnings))
            .put("files", JSONArray().apply { session.files.forEach { f -> put(JSONObject()
                .put("id", f.id).put("uri", f.uriString).put("name", f.displayName)
                .put("mime", f.mimeType).put("size", f.sizeBytes).put("modified", f.dateModifiedMillis)
                .put("path", f.relativePath).put("type", f.fileType.name).put("hidden", f.isHidden)
                .put("trashed", f.isTrashed).put("volume", f.storageVolume).put("duration", f.durationMillis)
                .put("sources", JSONArray(f.sources.map { it.name }))) } })
        val file = AtomicFile(File(directory, "${session.id}.json"))
        val output = file.startWrite()
        try { output.write(json.toString().toByteArray()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?.drop(3)?.forEach { it.delete() }
    }

    @Synchronized
    fun load(id: String): RecoveryScanSession? {
        if (!id.matches(Regex("[a-f0-9-]{36}"))) return null
        return runCatching {
            val root = JSONObject(AtomicFile(File(directory, "$id.json")).readFully().toString(Charsets.UTF_8))
            val files = root.getJSONArray("files")
            RecoveryScanSession(id, RecoveryScanMode.valueOf(root.getString("mode")),
                root.optString("category").takeIf { it.isNotBlank() }?.let(RecoveryCategory::valueOf),
                root.getLong("duration"), root.getLong("bytes"),
                (0 until files.length()).map { index ->
                    val f = files.getJSONObject(index)
                    val sources = f.getJSONArray("sources")
                    RecoverableFile(f.getString("id"), f.getString("uri"), f.getString("name"),
                        f.optString("mime").takeIf { it.isNotBlank() }, f.getLong("size"), f.getLong("modified"),
                        f.optString("path"), RecoveryFileType.valueOf(f.getString("type")),
                        (0 until sources.length()).map { RecoveryFileSource.valueOf(sources.getString(it)) }.toSet(),
                        f.optBoolean("hidden"), null, f.optBoolean("trashed"), f.optString("volume"), f.optLong("duration"))
                }, emptyList(), root.getLong("completed"), root.optBoolean("partial"),
                root.optJSONArray("warnings")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty())
        }.getOrNull()
    }
}
