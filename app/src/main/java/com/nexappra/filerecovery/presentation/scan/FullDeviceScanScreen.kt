package com.nexappra.filerecovery.presentation.scan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    var folderMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDenied by rememberSaveable(state.selectedCategory) { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val access = context.mediaAccess()
        permissionDenied = !access.permissions.canReadMedia(state.selectedCategory)
        viewModel.onAccessStateChanged(access)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folderMessage = "Folder access granted. Tap Start deep scan to search your selected category."
            } catch (_: SecurityException) { folderMessage = "This folder does not provide persistent access. Please choose another folder." }
            viewModel.onAccessStateChanged(context.mediaAccess())
        } else {
            folderMessage = "No folder selected. Choose a folder, then confirm Use this folder and Allow."
        }
    }
    val chooseFolder: () -> Unit = {
        try { folder.launch(null) }
        catch (_: ActivityNotFoundException) { folderMessage = "Android's file picker is unavailable. Enable the system Files app and try again." }
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
    val title = when (state.selectedCategory) { RecoveryCategory.Photos -> "Photo scan"; RecoveryCategory.Videos -> "Video scan"; RecoveryCategory.Audio -> "Audio scan"; else -> "Photos & videos" }
    val hasCategoryMediaAccess = state.permissions.canReadMedia(state.selectedCategory)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { FlowHeader(title, if (state.mode == RecoveryScanMode.Deep) "DEEP SEARCH" else "QUICK SCAN", onBack = viewModel::stopScan) }
        if (state.mode == RecoveryScanMode.Deep && !state.isPremium) {
            item { FlowPanel(Modifier.fillMaxWidth()) {
                FeatureLine(Icons.Rounded.WorkspacePremium, "Go deeper with Premium", "Search selected folders for hidden photos, videos, audio and readable cached media.")
                Button(onClick = onOpenPremium, modifier = Modifier.fillMaxWidth()) { Text("Explore Premium") }
            } }
        } else if (!state.isActiveScan && state.sessionId.isBlank() && state.scanStatus != RecoveryScanStatus.Error) {
            item { FlowPanel(Modifier.fillMaxWidth()) {
                if (state.mode == RecoveryScanMode.Deep) {
                    DeepScanCategoryPicker(state.selectedCategory ?: RecoveryCategory.Photos, viewModel::selectDeepCategory)
                    FeatureLine(Icons.Rounded.FolderOpen, "Choose your deep scan access", "Search permitted media and chosen folders for ${state.selectedCategory?.name?.lowercase() ?: "photos"} only. Choose a folder to include readable hidden and cached copies.")
                } else {
                    FeatureLine(Icons.Rounded.FolderOpen, "Choose your scan access", "Allow access to the selected media category. Android may limit the results you can scan.")
                }
                if (!hasCategoryMediaAccess) {
                    Button(onClick = { permissions.launch(mediaPermissions(state.selectedCategory)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (hasCategoryMediaAccess) "Update media access" else "Allow media access")
                    }
                } else {
                    val limitedSelection = state.permissions.partialVisualAccess && when (state.selectedCategory) {
                        RecoveryCategory.Photos -> !state.permissions.fullImagesAccess
                        RecoveryCategory.Videos -> !state.permissions.fullVideosAccess
                        else -> false
                    }
                    Text(if (limitedSelection) "Only selected photos/videos are available. You can update your selection below." else "${state.selectedCategory?.name ?: "Media"} access is allowed. You can start the scan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (limitedSelection) OutlinedButton(onClick = { permissions.launch(mediaPermissions(state.selectedCategory)) }, modifier = Modifier.fillMaxWidth()) { Text("Update selected media") }
                }
                if (permissionDenied && !hasCategoryMediaAccess) {
                    Text("Media access was not granted. Retry, or open app settings and enable permission for this category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        catch (_: ActivityNotFoundException) { folderMessage = "Open Android Settings > Apps > File Recovery > Permissions to allow access." }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open app settings") }
                }
                if (state.mode == RecoveryScanMode.Deep) {
                    if (Build.VERSION.SDK_INT >= 30 && !state.permissions.allFilesAccess) {
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                            } catch (_: ActivityNotFoundException) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                } catch (_: Exception) {
                                    folderMessage = "Open Settings > Privacy > All files access to allow full storage access."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Allow All Files access") }
                    }
                    OutlinedButton(onClick = chooseFolder, modifier = Modifier.fillMaxWidth()) { Text("Choose a folder") }
                    Text("${state.permissions.safFolderCount} chosen folder(s)", style = MaterialTheme.typography.bodySmall)
                    if (state.hasAuthorizedFolders && !hasCategoryMediaAccess) Text("Folder access is ready. Media permission is optional for scanning your chosen folders.", style = MaterialTheme.typography.bodySmall)
                    Text("Deep scan searches files that still exist in accessible folders. Android does not allow raw-sector recovery or access to other apps' private caches.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = viewModel::startScan, modifier = Modifier.fillMaxWidth(), enabled = state.canStartFullDeviceScan) { Text("Start deep scan") }
                }
                if (folderMessage != null) Text(folderMessage!!, style = MaterialTheme.typography.bodySmall)
            } }
        } else if (state.scanStatus == RecoveryScanStatus.Error) {
            item { FlowPanel(Modifier.fillMaxWidth()) {
                FeatureLine(Icons.Rounded.Info, "Let's try again", state.errorMessage ?: "This storage could not be read.")
                Button(onClick = viewModel::retryScan) { Text("Retry scan") }
                if (state.mode == RecoveryScanMode.Deep) {
                    OutlinedButton(onClick = chooseFolder) { Text("Choose a folder") }
                    if (folderMessage != null) Text(folderMessage!!, style = MaterialTheme.typography.bodySmall)
                }
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

@Composable
internal fun DeepScanCategoryPicker(selected: RecoveryCategory, onSelect: (RecoveryCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What do you want to find?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RecoveryCategory.Photos, RecoveryCategory.Videos, RecoveryCategory.Audio).forEach { category ->
                FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(category.name) })
            }
        }
    }
}

internal fun mediaPermissions(category: RecoveryCategory?): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> buildList {
        if (category == RecoveryCategory.Audio) add(Manifest.permission.READ_MEDIA_AUDIO) else {
            if (category != RecoveryCategory.Videos) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (category != RecoveryCategory.Photos) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
    }.toTypedArray()
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.mediaAccess(): DeviceScanAccessState {
    fun granted(name: String) = ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED
    return DeviceScanAccessState(DeviceScanPermissions(
        fullImagesAccess = Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES),
        fullVideosAccess = Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_VIDEO),
        partialVisualAccess = Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        audioAccess = Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_AUDIO),
        legacyReadAccess = Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE),
        allFilesAccess = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager(),
        safFolderCount = contentResolver.persistedUriPermissions.count { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) },
    ))
}

private fun stageLabel(stage: RecoveryScanLocationType?) = when (stage) {
    RecoveryScanLocationType.MediaStoreImages -> "Checking the photo index…"
    RecoveryScanLocationType.MediaStoreVideos -> "Checking the video index…"
    RecoveryScanLocationType.MediaStoreAudio -> "Checking the audio index…"
    RecoveryScanLocationType.AuthorizedFolders -> "Looking in your chosen folders…"
    RecoveryScanLocationType.AccessibleStorage -> "Looking for hidden and cached media…"
    else -> "Preparing your scan…"
}
