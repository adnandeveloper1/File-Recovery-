package com.nexappra.filerecovery.presentation.scan

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nexappra.filerecovery.core.ui.components.*
import com.nexappra.filerecovery.domain.model.*

@Composable
fun FullRecoveryResultsRoute(onBack: () -> Unit, onOpenPremium: () -> Unit = {}, onDeepScan: (RecoveryCategory?) -> Unit = {}, viewModel: FullRecoveryResultsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRepairInfo by rememberSaveable { mutableStateOf(false) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            viewModel.recoverSelectedTo(uri.toString())
        }
    }
    fun keepReadAccess(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    val cloud = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { keepReadAccess(uri); viewModel.exportSelectedTo(uri.toString()) }
    }
    val repair = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) { keepReadAccess(uri); viewModel.repairSelectedTo(uri.toString()) }
    }
    LaunchedEffect(Unit) { viewModel.events.collect { event -> when (event) {
        is FullRecoveryResultsEvent.ShowMessage -> snackbar.showSnackbar(event.message)
        FullRecoveryResultsEvent.OpenPremium -> onOpenPremium()
    } } }

    if (showRepairInfo) AlertDialog(onDismissRequest = { showRepairInfo = false }, title = { Text("Create a repair copy") },
        text = { Text("Re-encode readable image data into a fresh PNG, up to 2048 pixels. This can fix some encoding problems, but cannot reconstruct missing pixels or severe blur. The source file is preserved.") },
        confirmButton = { TextButton(onClick = { showRepairInfo = false; repair.launch("repaired_photo.png") }) { Text("Create copy") } },
        dismissButton = { TextButton(onClick = { showRepairInfo = false }) { Text("Cancel") } })
    state.allFiles.firstOrNull { it.id == previewId }?.let { file ->
        MediaPreview(file, state.isPremium, onClose = { previewId = null }, onPremium = { previewId = null; onOpenPremium() })
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        if (!state.isLoading && state.allFiles.isNotEmpty()) Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isRecovering) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Saving original files… Keep the app open.", style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { if (state.isPremium) folder.launch(null) else onOpenPremium() }, enabled = state.selectedIds.isNotEmpty() && !state.isRecovering,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(if (state.isPremium) Icons.Rounded.FileDownload else Icons.Rounded.LockOpen, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (state.isPremium) "Recover ${state.selectedIds.size} selected" else "Recover with Premium", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { if (state.isPremium) cloud.launch("recovered_media.zip") else onOpenPremium() }, enabled = state.selectedIds.isNotEmpty() && !state.isRecovering) {
                        Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Export ZIP")
                    }
                    TextButton(onClick = { if (state.isPremium) showRepairInfo = true else onOpenPremium() },
                        enabled = state.selectedFiles.size == 1 && state.selectedFiles.first().fileType == RecoveryFileType.Photo && !state.isRecovering) {
                        Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Repair copy")
                    }
                }
            }
        }
    }) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { FlowHeader("Your scan results", "${state.totalFound} photos & videos found", onBack) }
            if (state.isLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (state.hasMissingSession || state.errorMessage != null) item(span = { GridItemSpan(maxLineSpan) }) {
                FlowPanel { Text(state.errorMessage ?: "This scan has expired. Start a new scan to find your files."); Button(onClick = onBack) { Text("Back to scanning") } }
            }
            if (!state.isLoading && !state.hasMissingSession && state.errorMessage == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (state.isPartial) "Scan stopped early. Your found files are ready." else "Preview first. Recover the files you choose.", fontWeight = FontWeight.SemiBold)
                        Text("Includes existing media and any accessible hidden or trashed copies. Files erased from storage cannot be recreated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        OutlinedTextField(state.searchQuery, viewModel::onSearchQueryChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Search filenames") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(RecoveryResultFilter.All, RecoveryResultFilter.Photos, RecoveryResultFilter.Videos).forEach { filter ->
                                FilterChip(selected = state.selectedFilter == filter, onClick = { viewModel.onFilterSelected(filter) }, label = { Text(filter.name) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.selectedIds.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = viewModel::selectVisible, enabled = !state.isRecovering && state.visibleFiles.isNotEmpty()) {
                                Text(if (state.visibleFiles.isNotEmpty() && state.visibleFiles.all { it.id in state.selectedIds }) "Clear visible" else "Select visible")
                            }
                        }
                    }
                }
                if (state.visibleFiles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    FlowPanel(Modifier.fillMaxWidth()) {
                        FeatureLine(Icons.Rounded.PhotoLibrary, if (state.allFiles.isEmpty()) "No media found here" else "No matching files", "Try another category, change your media access, or search a chosen folder.")
                        TextButton(onClick = { if (state.isPremium) onDeepScan(state.selectedCategory) else onOpenPremium() }) { Text("Explore deep scan") }
                    }
                }
                items(state.visibleFiles, key = { it.id }) { file ->
                    val selected = file.id in state.selectedIds
                    Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column {
                            Box {
                                MediaThumbnail(file, Modifier.fillMaxWidth().aspectRatio(1f).clickable { previewId = file.id })
                                Checkbox(selected, onCheckedChange = { viewModel.onToggleSelection(file.id) }, enabled = !state.isRecovering,
                                    modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(bottomStart = 14.dp)))
                            }
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                Text(if (file.isTrashed) "Trash copy" else if (file.isHidden) "Hidden media" else "On device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Cloud export saves original files in a ZIP through the system picker. Choose Drive or another installed cloud provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MediaPreview(file: RecoverableFile, premium: Boolean, onClose: () -> Unit, onPremium: () -> Unit) {
    val context = LocalContext.current
    var scale by remember(file.id) { mutableFloatStateOf(1f) }
    var previewFailed by remember(file.id) { mutableStateOf(false) }
    val imageRequest = remember(file.uriString, premium) {
        ImageRequest.Builder(context).data(file.uriString).size(if (premium) 2048 else 640).build()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (premium) "Detailed" else "Standard"} preview", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close preview") }
                }
                if (file.fileType == RecoveryFileType.Video && premium) VideoPreview(file.uriString)
                else if (file.fileType == RecoveryFileType.Video) MediaThumbnail(file, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                else Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(imageRequest, file.displayName, onError = { previewFailed = true }, onSuccess = { previewFailed = false },
                        modifier = Modifier.fillMaxSize().pointerInput(premium) { if (premium) detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f) } }
                            .graphicsLayer(scaleX = scale, scaleY = scale), contentScale = ContentScale.Fit)
                    if (previewFailed) Text("This photo cannot be previewed. It may be unavailable, damaged or use an unsupported format.", modifier = Modifier.align(Alignment.Center).padding(20.dp))
                }
                Text(file.displayName, fontWeight = FontWeight.SemiBold)
                Text("${file.fileType.name} · ${"%.2f".format(file.sizeBytes / (1024.0 * 1024.0))} MB", style = MaterialTheme.typography.bodySmall)
                if (!premium) Button(onClick = onPremium, modifier = Modifier.fillMaxWidth()) { Text("Unlock advanced preview") }
                else Text(if (file.fileType == RecoveryFileType.Photo) "Pinch to inspect detail. Recovery copies the original source bytes." else "Use the playback controls to inspect your video.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VideoPreview(uri: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val video = remember(uri) { VideoView(context).apply { setVideoURI(Uri.parse(uri)); setMediaController(MediaController(context).also { it.setAnchorView(this) }); setOnPreparedListener { start() } } }
    var error by remember(uri) { mutableStateOf(false) }
    DisposableEffect(video, owner) {
        video.setOnErrorListener { _, _, _ -> error = true; true }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) video.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); video.stopPlayback() }
    }
    if (error) Text("This video cannot be played by this device. The source may be damaged or use an unsupported format.")
    else AndroidView(factory = { video }, modifier = Modifier.fillMaxWidth().height(300.dp))
}
