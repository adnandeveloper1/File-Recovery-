package com.nexappra.filerecovery.presentation.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexappra.filerecovery.core.ui.components.*

@Composable
fun ToolsScreen(onDeepScan: () -> Unit = {}, onScan: () -> Unit = {}, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { FlowHeader("A little extra care", "RECOVERY TOOLS") }
        item { FlowPanel {
            FeatureLine(Icons.Rounded.ManageSearch, "Look a little deeper", "Choose an accessible folder and search for photos, videos and readable cache copies. Premium required.")
            Button(onClick = onDeepScan) { Text("Open deep scan") }
        } }
        item { FlowPanel {
            FeatureLine(Icons.Rounded.AutoFixHigh, "Give a photo a fresh copy", "Scan, select one photo, then choose Repair copy. Re-encodes readable pixels to PNG; missing data cannot be reconstructed.")
            OutlinedButton(onClick = onScan) { Text("Find a photo to repair") }
        } }
        item { FlowPanel {
            FeatureLine(Icons.Rounded.CloudUpload, "Keep a copy elsewhere", "Select files from scan results, then Cloud export. Choose Google Drive or another provider in the system picker.")
            OutlinedButton(onClick = onScan) { Text("Choose files to export") }
        } }
    }
}
