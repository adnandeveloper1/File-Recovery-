package com.nexappra.filerecovery.presentation.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.core.ui.components.*
import com.nexappra.filerecovery.domain.model.*

@Composable
fun FullDeviceScanRoute(onBack: () -> Unit, onNavigateToResults: (String) -> Unit, onOpenPremium: () -> Unit = {}, viewModel: FullDeviceScanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var folderError by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.onAccessStateChanged(context.mediaAccess()) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); folderError = null }
            catch (_: SecurityException) { folderError = "This folder does not provide persistent access. Please choose another folder." }
            viewModel.onAccessStateChanged(context.mediaAccess())
        }
    }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onAccessStateChanged(context.mediaAccess()) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        viewModel.onAccessStateChanged(context.mediaAccess())
        viewModel.events.collect { event -> when (event) {
            FullDeviceScanNavigationEvent.Close -> onBack()
            is FullDeviceScanNavigationEvent.OpenResults -> onNavigateToResults(event.sessionId)
        } }
    }
    BackHandler { viewModel.stopScan() }
    val title = when (state.selectedCategory) { RecoveryCategory.Photos -> "Photo scan"; RecoveryCategory.Videos -> "Video scan"; else -> "Photos & videos" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { FlowHeader(title, if (state.mode == RecoveryScanMode.Deep) "DEEP SEARCH" else "QUICK SCAN", onBack = viewModel::stopScan) }
        if (state.mode == RecoveryScanMode.Deep && !state.isPremium) {
            item { FlowPanel(Modifier.fillMaxWidth()) {
                FeatureLine(Icons.Rounded.WorkspacePremium, "Go deeper with Premium", "Search selected folders for hidden photos, videos and readable cached media.")
                Button(onClick = onOpenPremium, modifier = Modifier.fillMaxWidth()) { Text("Explore Premium") }
            } }
        } else if (!state.isActiveScan && state.sessionId.isBlank() && state.scanStatus != RecoveryScanStatus.Error) {
            item { FlowPanel(Modifier.fillMaxWidth()) {
                FeatureLine(Icons.Rounded.FolderOpen, "Choose your scan access", "Allow the selected media category. Android may limit results to the photos and videos you choose.")
                Button(onClick = { permissions.launch(mediaPermissions(state.selectedCategory)) }, modifier = Modifier.fillMaxWidth()) { Text("Allow media access") }
                if (state.mode == RecoveryScanMode.Deep) {
                    OutlinedButton(onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Choose a folder") }
                    Text("${state.permissions.safFolderCount} chosen folder(s)", style = MaterialTheme.typography.bodySmall)
                    Text("Deep scan searches files that still exist in accessible folders. Android does not allow raw-sector recovery or access to other apps' private caches.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = viewModel::startScan, modifier = Modifier.fillMaxWidth(), enabled = state.canStartFullDeviceScan) { Text("Start deep scan") }
                }
                if (folderError != null) Text(folderError!!, color = MaterialTheme.colorScheme.error)
            } }
        } else if (state.scanStatus == RecoveryScanStatus.Error) {
            item { FlowPanel(Modifier.fillMaxWidth()) {
                FeatureLine(Icons.Rounded.Info, "Let's try again", state.errorMessage ?: "This storage could not be read.")
                Button(onClick = viewModel::retryScan) { Text("Retry scan") }
            } }
        } else {
            item { FlowPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircularProgressIndicator(Modifier.size(46.dp), strokeWidth = 3.dp)
                    Column {
                        Text(if (state.isStopping) "Keeping your results…" else "Finding your media", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${state.totalFilesFound} found so far", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(stageLabel(state.currentLocation?.type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Results appear as they are found. You can stop and browse at any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { viewModel.finishEarly() }, enabled = state.totalFilesFound > 0 && !state.isStopping, modifier = Modifier.fillMaxWidth()) { Text("View ${state.totalFilesFound} results") }
                TextButton(onClick = viewModel::stopScan, enabled = !state.isStopping, modifier = Modifier.fillMaxWidth()) { Text("Cancel scan") }
            } }
            if (state.previewFiles.isNotEmpty()) {
                item { Text("Found on your device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                state.previewFiles.chunked(3).forEach { row -> item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { file -> MediaThumbnail(file, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                } }
            }
        }
    }
}

internal fun mediaPermissions(category: RecoveryCategory?): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> buildList {
        if (category != RecoveryCategory.Videos) add(Manifest.permission.READ_MEDIA_IMAGES)
        if (category != RecoveryCategory.Photos) add(Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }.toTypedArray()
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.mediaAccess(): DeviceScanAccessState {
    fun granted(name: String) = ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED
    return DeviceScanAccessState(DeviceScanPermissions(
        fullImagesAccess = Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES),
        fullVideosAccess = Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_VIDEO),
        partialVisualAccess = Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        legacyReadAccess = Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE),
        allFilesAccess = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager(),
        safFolderCount = contentResolver.persistedUriPermissions.count { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) },
    ))
}

private fun stageLabel(stage: RecoveryScanLocationType?) = when (stage) {
    RecoveryScanLocationType.MediaStoreImages -> "Checking the photo index…"
    RecoveryScanLocationType.MediaStoreVideos -> "Checking the video index…"
    RecoveryScanLocationType.AuthorizedFolders -> "Looking in your chosen folders…"
    RecoveryScanLocationType.AccessibleStorage -> "Looking for hidden and cached media…"
    else -> "Preparing your scan…"
}
