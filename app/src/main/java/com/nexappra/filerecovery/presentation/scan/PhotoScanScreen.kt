package com.nexappra.filerecovery.presentation.scan

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.designsystem.theme.SuccessGreen
import com.nexappra.filerecovery.core.designsystem.theme.spacing
import com.nexappra.filerecovery.core.utils.toDetailedDurationLabel
import com.nexappra.filerecovery.core.utils.toFormattedCount
import com.nexappra.filerecovery.core.utils.toReadableFileSize
import com.nexappra.filerecovery.core.utils.toRemainingDurationLabel
import com.nexappra.filerecovery.domain.model.PhotoScanLocationState
import com.nexappra.filerecovery.domain.model.PhotoScanStatus
import com.nexappra.filerecovery.domain.model.ScanLocationStatus

@Composable
fun PhotoScanRoute(
    onBack: () -> Unit,
    onNavigateToResults: (String) -> Unit,
    viewModel: PhotoScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var hasPermission by remember { mutableStateOf(context.hasPhotoPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        hasPermission = context.hasPhotoPermission()
    }

    LaunchedEffect(hasPermission) {
        viewModel.onPermissionStateChanged(hasPermission)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PhotoScanNavigationEvent.Close -> onBack()
                is PhotoScanNavigationEvent.OpenResults -> onNavigateToResults(event.sessionId)
            }
        }
    }

    PhotoScanScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRequestPermission = {
            permissionLauncher.launch(photoReadPermission())
        },
        onRetry = {
            hasPermission = context.hasPhotoPermission()
            if (hasPermission) {
                viewModel.retryScan()
            } else {
                permissionLauncher.launch(photoReadPermission())
            }
        },
        onStopConfirmed = viewModel::stopScan,
    )
}

@Composable
fun PhotoScanScreen(
    uiState: PhotoScanUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onStopConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress ?: 0f,
        label = "scanProgress",
    )

    BackHandler(enabled = uiState.isActiveScan) {
        showStopDialog = true
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(text = stringResource(R.string.scan_stop_title)) },
            text = { Text(text = stringResource(R.string.scan_stop_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStopConfirmed()
                    },
                ) {
                    Text(text = stringResource(R.string.stop_scan))
                }
            },
            dismissButton = {
                Button(onClick = { showStopDialog = false }) {
                    Text(text = stringResource(R.string.continue_scanning))
                }
            },
        )
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(text = stringResource(R.string.scan_details_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.scan_details_mode,
                            stringResource(uiState.scanMode.labelRes()),
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.scan_details_found,
                            uiState.totalFilesFound.toFormattedCount(),
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.scan_details_data,
                            uiState.bytesScanned.toReadableFileSize(),
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.scan_details_elapsed,
                            uiState.elapsedMillis.toDetailedDurationLabel(),
                        ),
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDetailsDialog = false }) {
                    Text(text = stringResource(R.string.done))
                }
            },
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(text = stringResource(R.string.scan_help_title)) },
            text = { Text(text = stringResource(R.string.scan_help_body)) },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }) {
                    Text(text = stringResource(R.string.done))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.large),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (uiState.isActiveScan) {
                                showStopDialog = true
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.scanning_photos_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.scan_more_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.scan_details_menu)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                showDetailsDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.scan_help_menu)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.HelpOutline,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                showHelpDialog = true
                            },
                        )
                        if (uiState.isActiveScan) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.cancel_scan)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Cancel,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showStopDialog = true
                                },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (uiState.isActiveScan) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = spacing.large, vertical = spacing.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { showStopDialog = true }) {
                        Text(text = stringResource(R.string.stop_scan))
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            uiState.scanStatus == PhotoScanStatus.PermissionRequired -> {
                PermissionRequiredState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = spacing.large),
                    onRequestPermission = onRequestPermission,
                )
            }

            uiState.scanStatus == PhotoScanStatus.Error -> {
                ErrorState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = spacing.large),
                    message = uiState.errorMessage ?: stringResource(R.string.scan_generic_error),
                    onRetry = onRetry,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = spacing.large,
                        top = 0.dp,
                        end = spacing.large,
                        bottom = spacing.section,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.large),
                ) {
                    item {
                        ScanProgressHero(
                            progress = animatedProgress,
                            progressValue = uiState.progress,
                        )
                    }

                    item {
                        ScanStatsRow(
                            filesFound = uiState.totalFilesFound.toFormattedCount(),
                            dataScanned = uiState.bytesScanned.toReadableFileSize(),
                            timeLeft = uiState.estimatedRemainingMillis.toRemainingDurationLabel(),
                        )
                    }

                    item {
                        CurrentScanningCard(
                            location = uiState.currentLocation,
                            progress = uiState.progress ?: 0f,
                        )
                    }

                    item {
                        ScanLocationsCard(locations = uiState.locations)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanProgressHero(
    progress: Float,
    progressValue: Float?,
) {
    val animatedProgressValue by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progressValue ?: 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "PhotoScanProgress"
    )
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(156.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(156.dp)) {
                drawCircle(
                    color = trackColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 16.dp.toPx()),
                )
            }
            CircularProgressIndicator(
                progress = { animatedProgressValue },
                modifier = Modifier.size(140.dp),
                strokeWidth = 12.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (progressValue == null) "--" else "${(animatedProgressValue * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.scan_progress_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.scan_progress_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanStatsRow(
    filesFound: String,
    dataScanned: String,
    timeLeft: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScanStatCard(
            modifier = Modifier.weight(1f),
            value = filesFound,
            label = stringResource(R.string.scan_stat_files_found),
        )
        ScanStatCard(
            modifier = Modifier.weight(1f),
            value = dataScanned,
            label = stringResource(R.string.scan_stat_data_scanned),
        )
        ScanStatCard(
            modifier = Modifier.weight(1f),
            value = timeLeft,
            label = stringResource(R.string.scan_stat_time_left),
        )
    }
}

@Composable
private fun ScanStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrentScanningCard(
    location: PhotoScanLocationState?,
    progress: Float,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.currently_scanning_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CenterFocusWeak,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = location?.let { stringResource(it.type.titleRes()) }
                            ?: stringResource(R.string.scan_location_waiting),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = location?.displayPath ?: stringResource(R.string.scan_path_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ScanLocationsCard(
    locations: List<PhotoScanLocationState>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column {
            locations.forEachIndexed { index, location ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LocationStatusIcon(status = location.status)
                        Text(
                            text = stringResource(location.type.titleRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = when (location.status) {
                            ScanLocationStatus.Completed -> stringResource(
                                R.string.scan_location_found_count,
                                location.foundCount.toFormattedCount(),
                            )
                            ScanLocationStatus.Scanning -> if (location.foundCount > 0) {
                                stringResource(
                                    R.string.scan_location_found_progress,
                                    location.foundCount.toFormattedCount(),
                                )
                            } else {
                                stringResource(R.string.scan_location_scanning)
                            }
                            ScanLocationStatus.Failed -> stringResource(R.string.scan_location_unavailable)
                            ScanLocationStatus.Pending -> stringResource(R.string.scan_location_pending)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (index != locations.lastIndex) {
                    HorizontalDivider(
                        color = DividerDefaults.color.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationStatusIcon(status: ScanLocationStatus) {
    when (status) {
        ScanLocationStatus.Completed -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(18.dp),
        )
        ScanLocationStatus.Scanning -> CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        ScanLocationStatus.Failed -> Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        ScanLocationStatus.Pending -> Icon(
            imageVector = Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PermissionRequiredState(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.scan_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.scan_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestPermission) {
                    Text(text = stringResource(R.string.scan_permission_action))
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.scan_generic_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRetry) {
                    Text(text = stringResource(R.string.retry))
                }
            }
        }
    }
}

private fun photoReadPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_IMAGES
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun android.content.Context.hasPhotoPermission(): Boolean {
    val permission = photoReadPermission()
    return ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
