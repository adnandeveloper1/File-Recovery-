package com.nexappra.filerecovery.data.repository

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RecoveryHistoryEntry(val id: String, val count: Int, val destination: String, val timeMillis: Long, val kind: String)

@Singleton
class RecoveryHistoryStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.filesDir, "recovery_history.json"))
    private val mutableEntries = MutableStateFlow<List<RecoveryHistoryEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) { synchronized(this@RecoveryHistoryStore) { read() } }
    private fun read() {
        mutableEntries.value = runCatching {
            val a = JSONArray(file.readFully().toString(Charsets.UTF_8))
            (0 until a.length()).map { i -> a.getJSONObject(i).let { j -> RecoveryHistoryEntry(j.getString("id"), j.getInt("count"), j.getString("destination"), j.getLong("time"), j.getString("kind")) } }
        }.getOrDefault(emptyList())
    }
    suspend fun record(count: Int, destination: String, kind: String) = withContext(Dispatchers.IO) {
        if (count == 0) return@withContext
        synchronized(this@RecoveryHistoryStore) {
            read()
            val updated = (listOf(RecoveryHistoryEntry(UUID.randomUUID().toString(), count, destination, System.currentTimeMillis(), kind)) + mutableEntries.value).take(100)
            val json = JSONArray().apply { updated.forEach { e -> put(JSONObject().put("id", e.id).put("count", e.count).put("destination", e.destination).put("time", e.timeMillis).put("kind", e.kind)) } }
            val stream = file.startWrite()
            try { stream.write(json.toString().toByteArray()); file.finishWrite(stream); mutableEntries.value = updated }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
    }
}
