package com.nexappra.filerecovery.presentation.recovered

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.core.ui.components.*
import com.nexappra.filerecovery.data.repository.RecoveryHistoryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class RecoveryHistoryViewModel @Inject constructor(store: RecoveryHistoryStore) : ViewModel() {
    val entries = store.entries
    init { viewModelScope.launch { store.refresh() } }
}

@Composable
fun RecoveredScreen(onScan: () -> Unit = {}, modifier: Modifier = Modifier, viewModel: RecoveryHistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { FlowHeader("Saved moments", "YOUR RECOVERY HISTORY") }
        if (entries.isEmpty()) item {
            FlowPanel {
                FeatureLine(Icons.Rounded.History, "A fresh start", "Your successful file saves and cloud exports will appear here.")
                Button(onClick = onScan) { Text("Find photos & videos") }
            }
        }
        items(entries, key = { it.id }) { entry ->
            FlowPanel(Modifier.fillMaxWidth()) {
                Text(entry.kind, style = MaterialTheme.typography.titleMedium)
                Text("${entry.count} file(s) saved", style = MaterialTheme.typography.headlineSmall)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.timeMillis)), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    try {
                        val uri = Uri.parse(entry.destination)
                        val mime = when (entry.kind) { "Original files" -> "vnd.android.document/directory"; "Repaired PNG copy" -> "image/png"; else -> "application/zip" }
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: Exception) { Toast.makeText(context, "Open your chosen folder or cloud provider to view these files.", Toast.LENGTH_LONG).show() }
                }) { Text("Open destination") }
            }
        }
    }
}
