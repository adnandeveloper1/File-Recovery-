package com.nexappra.filerecovery.presentation.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.utils.FILE_RECOVERY_DEBUG_TAG
import com.nexappra.filerecovery.core.designsystem.theme.SuccessGreen
import com.nexappra.filerecovery.core.designsystem.theme.spacing
import com.nexappra.filerecovery.core.utils.toDetailedDurationLabel
import com.nexappra.filerecovery.core.utils.toFormattedCount
import com.nexappra.filerecovery.core.utils.toRemainingDurationLabel
import com.nexappra.filerecovery.domain.model.RecoveryResultFilter
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.RecoveryScanLocationState
import com.nexappra.filerecovery.domain.model.RecoveryScanLocationType
import com.nexappra.filerecovery.domain.model.RecoveryScanStatus
import com.nexappra.filerecovery.domain.model.ScanLocationStatus
import com.nexappra.filerecovery.presentation.home.toUiSpec

@Composable
fun FullDeviceScanRoute(
    onBack: () -> Unit,
    onNavigateToResults: (String) -> Unit,
    viewModel: FullDeviceScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var accessState by remember { mutableStateOf(context.currentFullDeviceAccessState()) }
    var allFilesAccessPromptHandled by rememberSaveable { mutableStateOf(false) }
    var showAllFilesAccessPrompt by rememberSaveable { mutableStateOf(false) }
    var awaitingAllFilesSettings by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        accessState = context.currentFullDeviceAccessState()
    }
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        accessState = context.currentFullDeviceAccessState()
        awaitingAllFilesSettings = false
        allFilesAccessPromptHandled = true
    }
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        accessState = context.currentFullDeviceAccessState()
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        awaitingAllFilesSettings = true
        allFilesAccessPromptHandled = true
        showAllFilesAccessPrompt = false
        val appSettingsIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName),
        )
        try {
            allFilesSettingsLauncher.launch(appSettingsIntent)
        } catch (_: Exception) {
            try {
                allFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                awaitingAllFilesSettings = false
                accessState = context.currentFullDeviceAccessState()
            }
        }
    }

    LaunchedEffect(accessState, allFilesAccessPromptHandled, awaitingAllFilesSettings) {
        if (awaitingAllFilesSettings) return@LaunchedEffect
        val shouldOfferAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            accessState.canStartFullDeviceScan &&
            !accessState.permissions.allFilesAccess &&
            !allFilesAccessPromptHandled
        if (shouldOfferAllFilesAccess) {
            showAllFilesAccessPrompt = true
        } else {
            showAllFilesAccessPrompt = false
            viewModel.onAccessStateChanged(accessState)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                FullDeviceScanNavigationEvent.Close -> onBack()
                is FullDeviceScanNavigationEvent.OpenResults -> {
                    Log.d(
                        FILE_RECOVERY_DEBUG_TAG,
                        "OpenResults emitted session=${event.sessionId}",
                    )
                    onNavigateToResults(event.sessionId)
                }
            }
        }
    }

    if (showAllFilesAccessPrompt) {
        AllFilesAccessDialog(
            onEnableAllFilesAccess = ::requestAllFilesAccess,
            onContinueWithMediaAccess = {
                allFilesAccessPromptHandled = true
                showAllFilesAccessPrompt = false
            },
        )
    }

    FullDeviceScanScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        canRequestAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        onRequestAllFilesAccess = ::requestAllFilesAccess,
        onRequestMediaAccess = {
            permissionLauncher.launch(fullDevicePermissions())
        },
        onChooseFolders = {
            folderLauncher.launch(null)
        },
        onRetry = {
            accessState = context.currentFullDeviceAccessState()
            if (accessState.canStartFullDeviceScan) {
                viewModel.retryScan()
            } else {
                Log.d(
                    FILE_RECOVERY_DEBUG_TAG,
                    "Retry requested before access was granted; requesting permissions again.",
                )
                permissionLauncher.launch(fullDevicePermissions())
            }
        },
        onStopConfirmed = viewModel::stopScan,
    )
}

@Composable
private fun AllFilesAccessDialog(
    onEnableAllFilesAccess: () -> Unit,
    onContinueWithMediaAccess: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onContinueWithMediaAccess,
        title = { Text(text = stringResource(R.string.full_scan_all_files_title)) },
        text = { Text(text = stringResource(R.string.full_scan_all_files_body)) },
        confirmButton = {
            Button(onClick = onEnableAllFilesAccess) {
                Text(text = stringResource(R.string.full_scan_all_files_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueWithMediaAccess) {
                Text(text = stringResource(R.string.full_scan_all_files_skip))
            }
        },
    )
}
@Composable
fun FullDeviceScanScreen(
    uiState: FullDeviceScanUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRequestMediaAccess: () -> Unit,
    canRequestAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onChooseFolders: () -> Unit,
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
        label = "fullDeviceScanProgress",
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
                            R.string.full_scan_details_access,
                            uiState.accessSummary ?: stringResource(R.string.full_scan_access_unknown),
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
                            R.string.full_scan_details_locations,
                            uiState.locationsChecked.toFormattedCount(),
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
            text = { Text(text = stringResource(R.string.full_scan_help_body)) },
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
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.full_scan_screen_title),
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
        when (uiState.scanStatus) {
            RecoveryScanStatus.AccessRequired -> {
                FullScanAccessRequiredState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = spacing.large),
                    onRequestMediaAccess = onRequestMediaAccess,
                    canRequestAllFilesAccess = canRequestAllFilesAccess,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onChooseFolders = onChooseFolders,
                    hasAuthorizedFolders = uiState.hasAuthorizedFolders,
                    accessSummary = uiState.accessSummary,
                )
            }

            RecoveryScanStatus.Error -> {
                FullScanErrorState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = spacing.large),
                    message = uiState.errorMessage ?: stringResource(R.string.full_scan_generic_error),
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
                        FullScanProgressHero(
                            progress = animatedProgress,
                            progressValue = uiState.progress,
                            selectedCategory = uiState.selectedCategory,
                        )
                    }

                    item {
                        FullScanStatsRow(
                            filesFound = uiState.totalFilesFound.toFormattedCount(),
                            locationsChecked = uiState.locationsChecked.toFormattedCount(),
                            timeLeft = uiState.estimatedRemainingMillis.toRemainingDurationLabel(),
                        )
                    }

                    if (uiState.isLimitedAccess) {
                        item {
                            LimitedAccessCard(
                                accessSummary = uiState.accessSummary,
                                canRequestAllFilesAccess = canRequestAllFilesAccess && !uiState.permissions.allFilesAccess,
                                onRequestAllFilesAccess = onRequestAllFilesAccess,
                            )
                        }
                    }

                    item {
                        CategoryCountersCard(
                            counts = uiState.categoryCounts,
                            hiddenPhotoCount = uiState.hiddenPhotoCount,
                            hiddenVideoCount = uiState.hiddenVideoCount,
                        )
                    }

                    item {
                        FullScanCurrentStageCard(
                            location = uiState.currentLocation,
                            progress = uiState.progress ?: 0f,
                        )
                    }

                    item {
                        FullScanLocationsCard(locations = uiState.locations)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScanProgressHero(
    progress: Float,
    progressValue: Float?,
    selectedCategory: RecoveryCategory?,
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(164.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {}

            CircularProgressIndicator(
                progress = { progressValue ?: 0f },
                modifier = Modifier.size(144.dp),
                strokeWidth = 10.dp,
                color = SuccessGreen,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (progressValue == null) "--" else "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.scan_progress_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = selectedCategory?.let { category ->
                stringResource(
                    R.string.full_scan_category_progress_subtitle,
                    stringResource(category.toUiSpec().titleRes),
                )
            } ?: stringResource(R.string.full_scan_progress_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FullScanStatsRow(
    filesFound: String,
    locationsChecked: String,
    timeLeft: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FullScanStatCard(
            modifier = Modifier.weight(1f),
            value = filesFound,
            label = stringResource(R.string.scan_stat_files_found),
        )
        FullScanStatCard(
            modifier = Modifier.weight(1f),
            value = locationsChecked,
            label = stringResource(R.string.full_scan_stat_locations_checked),
        )
        FullScanStatCard(
            modifier = Modifier.weight(1f),
            value = timeLeft,
            label = stringResource(R.string.scan_stat_time_left),
        )
    }
}

@Composable
private fun FullScanStatCard(
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
private fun LimitedAccessCard(
    accessSummary: String?,
    canRequestAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.full_scan_limited_access_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.full_scan_limited_access_body,
                    accessSummary ?: stringResource(R.string.full_scan_access_unknown),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canRequestAllFilesAccess) {
                TextButton(onClick = onRequestAllFilesAccess) {
                    Text(text = stringResource(R.string.full_scan_all_files_action))
                }
            }
        }
    }
}
@Composable
private fun CategoryCountersCard(
    counts: Map<RecoveryResultFilter, Int>,
    hiddenPhotoCount: Int,
    hiddenVideoCount: Int,
) {
    val items = listOf(
        RecoveryResultFilter.Photos,
        RecoveryResultFilter.Videos,
        RecoveryResultFilter.Audio,
        RecoveryResultFilter.Documents,
        RecoveryResultFilter.Hidden,
        RecoveryResultFilter.RecycleBin,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.full_scan_live_counts_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { filter ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(filter.labelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = (counts[filter] ?: 0).toFormattedCount(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    R.string.full_scan_hidden_photos to hiddenPhotoCount,
                    R.string.full_scan_hidden_videos to hiddenVideoCount,
                ).forEach { (labelRes, count) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = count.toFormattedCount(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun FullScanCurrentStageCard(
    location: RecoveryScanLocationState?,
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
private fun FullScanLocationsCard(
    locations: List<RecoveryScanLocationState>,
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
                        FullScanLocationStatusIcon(status = location.status)
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
private fun FullScanLocationStatusIcon(status: ScanLocationStatus) {
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
private fun FullScanAccessRequiredState(
    onRequestMediaAccess: () -> Unit,
    canRequestAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onChooseFolders: () -> Unit,
    hasAuthorizedFolders: Boolean,
    accessSummary: String?,
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
                    text = stringResource(R.string.full_scan_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.full_scan_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (accessSummary != null) {
                    Text(
                        text = accessSummary,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(onClick = onRequestMediaAccess) {
                    Text(text = stringResource(R.string.full_scan_permission_media_action))
                }
                if (canRequestAllFilesAccess) {
                    TextButton(onClick = onRequestAllFilesAccess) {
                        Text(text = stringResource(R.string.full_scan_all_files_action))
                    }
                }
                TextButton(onClick = onChooseFolders) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.full_scan_permission_folder_action),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScanErrorState(
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

private fun fullDevicePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.currentFullDeviceAccessState(): DeviceScanAccessState {
    val permissions = DeviceScanPermissions(
        fullImagesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES),
        fullVideosAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_MEDIA_VIDEO),
        partialVisualAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        audioAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_MEDIA_AUDIO),
        legacyReadAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE),
        allFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager(),
        sharedStorageTraversalAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        },
        safFolderCount = contentResolver.persistedUriPermissions.count { permission ->
            permission.isReadPermission
        },
    )
    return DeviceScanAccessState(permissions = permissions)
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        permission,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun RecoveryResultFilter.labelRes(): Int = when (this) {
    RecoveryResultFilter.All -> R.string.photo_filter_all
    RecoveryResultFilter.Photos -> R.string.photo_filter_photos
    RecoveryResultFilter.Videos -> R.string.full_results_filter_videos
    RecoveryResultFilter.Audio -> R.string.full_results_filter_audio
    RecoveryResultFilter.Documents -> R.string.full_results_filter_documents
    RecoveryResultFilter.WhatsApp -> R.string.full_results_filter_whatsapp
    RecoveryResultFilter.Downloads -> R.string.photo_filter_downloads
    RecoveryResultFilter.Screenshots -> R.string.photo_filter_screenshots
    RecoveryResultFilter.Hidden -> R.string.full_results_filter_hidden
    RecoveryResultFilter.RecycleBin -> R.string.full_results_filter_recycle_bin
    RecoveryResultFilter.LargeFiles -> R.string.full_results_filter_large_files
    RecoveryResultFilter.Other -> R.string.full_results_filter_other
}

private fun RecoveryScanLocationType.titleRes(): Int = when (this) {
    RecoveryScanLocationType.MediaPermissions -> R.string.full_scan_stage_permissions
    RecoveryScanLocationType.StorageVolumes -> R.string.full_scan_stage_volumes
    RecoveryScanLocationType.MediaStoreImages -> R.string.full_scan_stage_images
    RecoveryScanLocationType.MediaStoreVideos -> R.string.full_scan_stage_videos
    RecoveryScanLocationType.MediaStoreAudio -> R.string.full_scan_stage_audio
    RecoveryScanLocationType.MediaStoreFiles -> R.string.full_scan_stage_files
    RecoveryScanLocationType.MediaStoreTrash -> R.string.full_scan_stage_trash
    RecoveryScanLocationType.AuthorizedFolders -> R.string.full_scan_stage_authorized_folders
    RecoveryScanLocationType.AccessibleStorage -> R.string.full_scan_stage_accessible_storage
    RecoveryScanLocationType.ValidateCandidates -> R.string.full_scan_stage_validate
    RecoveryScanLocationType.RemoveDuplicates -> R.string.full_scan_stage_deduplicate
    RecoveryScanLocationType.PersistResults -> R.string.full_scan_stage_persist
}
