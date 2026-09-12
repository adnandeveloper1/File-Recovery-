package com.nexappra.filerecovery.presentation.scan

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.designsystem.theme.SuccessGreen
import com.nexappra.filerecovery.core.designsystem.theme.spacing
import com.nexappra.filerecovery.core.utils.toCompactMediaDurationLabel
import com.nexappra.filerecovery.core.utils.toDetailedDurationLabel
import com.nexappra.filerecovery.core.utils.toFormattedCount
import com.nexappra.filerecovery.core.utils.toReadableFileSize
import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryFileSource
import com.nexappra.filerecovery.domain.model.RecoveryFileType
import com.nexappra.filerecovery.domain.model.RecoveryResultFilter
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun FullRecoveryResultsRoute(
    onBack: () -> Unit,
    viewModel: FullRecoveryResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.recoverSelectedTo(uri.toString())
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        val message = if (it.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onRestoreSelectedConfirmed()
            "Restore request submitted."
        } else {
            "Restore was not completed."
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FullRecoveryResultsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    FullRecoveryResultsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onFilterSelected = viewModel::onFilterSelected,
        onToggleSearch = viewModel::onSearchVisibilityToggle,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFileClick = { file -> viewModel.onToggleSelection(file.id) },
        onRecoverSelected = {
            if (uiState.selectedFilesAreAllTrashed) {
                createRestoreRequest(
                    context = context,
                    files = uiState.selectedFiles,
                )?.let { pendingIntent ->
                    restoreLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                    )
                } ?: folderLauncher.launch(null)
            } else {
                folderLauncher.launch(null)
            }
        },
    )
}

@Composable
fun FullRecoveryResultsScreen(
    uiState: FullRecoveryResultsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFilterSelected: (RecoveryResultFilter) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFileClick: (RecoverableFile) -> Unit,
    onRecoverSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val filters = listOf(
        RecoveryResultFilter.All,
        RecoveryResultFilter.Photos,
        RecoveryResultFilter.Videos,
        RecoveryResultFilter.Audio,
        RecoveryResultFilter.Documents,
        RecoveryResultFilter.WhatsApp,
        RecoveryResultFilter.Downloads,
        RecoveryResultFilter.Screenshots,
        RecoveryResultFilter.Hidden,
        RecoveryResultFilter.RecycleBin,
        RecoveryResultFilter.LargeFiles,
        RecoveryResultFilter.Other,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = spacing.large, vertical = spacing.large),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onBack,
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
                        Column {
                            Text(
                                text = stringResource(R.string.full_results_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(
                                    R.string.full_results_count,
                                    uiState.totalFound.toFormattedCount(),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }

                if (uiState.isSearchVisible) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(text = stringResource(R.string.full_results_search_placeholder))
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (uiState.selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = spacing.large, vertical = spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.photo_selection_size,
                                uiState.selectedTotalSizeBytes.toReadableFileSize(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onRecoverSelected,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (uiState.selectedFilesAreAllTrashed) {
                                    Icons.Rounded.RestoreFromTrash
                                } else {
                                    Icons.Rounded.FolderOpen
                                },
                                contentDescription = null,
                            )
                            Text(
                                text = if (uiState.selectedFilesAreAllTrashed) {
                                    stringResource(
                                        R.string.restore_selected_files,
                                        uiState.selectedIds.size.toFormattedCount(),
                                    )
                                } else {
                                    stringResource(
                                        R.string.recover_selected_files,
                                        uiState.selectedIds.size.toFormattedCount(),
                                    )
                                },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = spacing.large,
                top = 0.dp,
                end = spacing.large,
                bottom = if (uiState.selectedIds.isNotEmpty()) 112.dp else spacing.section,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filters, key = { filter -> filter.name }) { filter ->
                        FilterChip(
                            selected = uiState.selectedFilter == filter,
                            onClick = { onFilterSelected(filter) },
                            label = {
                                Text(text = stringResource(filter.fullResultsLabelRes()))
                            },
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.full_results_summary,
                                uiState.scanDurationMillis.toDetailedDurationLabel(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.photo_results_found,
                                uiState.totalFound.toFormattedCount(),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = SuccessGreen,
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.loading),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                uiState.errorMessage != null || uiState.hasMissingSession -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = if (uiState.hasMissingSession) {
                                    stringResource(R.string.full_results_missing_session)
                                } else {
                                    uiState.errorMessage.orEmpty()
                                },
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                uiState.visibleFiles.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.full_results_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (uiState.searchQuery.isBlank()) {
                                        stringResource(R.string.full_results_empty_body)
                                    } else {
                                        stringResource(R.string.full_results_empty_search_body)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                else -> {
                    gridItems(
                        items = uiState.visibleFiles,
                        key = { file -> file.id },
                        span = { file ->
                            if (file.isVisualPreview()) {
                                GridItemSpan(1)
                            } else {
                                GridItemSpan(maxLineSpan)
                            }
                        },
                    ) { file ->
                        FullRecoveryFileCard(
                            file = file,
                            isSelected = file.id in uiState.selectedIds,
                            onClick = { onFileClick(file) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullRecoveryFileCard(
    file: RecoverableFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    if (file.isVisualPreview()) {
        VisualRecoveryFileCard(
            file = file,
            isSelected = isSelected,
            onClick = onClick,
        )
    } else {
        MetadataRecoveryFileCard(
            file = file,
            isSelected = isSelected,
            onClick = onClick,
        )
    }
}

@Composable
private fun VisualRecoveryFileCard(
    file: RecoverableFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.88f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                SubcomposeAsyncImage(
                    model = file.uriString,
                    contentDescription = file.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { FileCardFallback(file = file) },
                    error = { FileCardFallback(file = file) },
                )

                if (file.fileType == RecoveryFileType.Video) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }

                SelectionBadge(
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FileBadge(label = file.sizeBytes.toReadableFileSize())
                    file.durationMillis.toCompactMediaDurationLabel()?.let { durationLabel ->
                        FileBadge(label = durationLabel)
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (file.isHidden) {
                        FileBadge(
                            label = stringResource(R.string.full_results_badge_hidden),
                        )
                    }
                    if (file.isTrashed) {
                        FileBadge(
                            label = stringResource(R.string.full_results_badge_trashed),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file.visualMetadataLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file.sourceLine(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetadataRecoveryFileCard(
    file: RecoverableFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (file.fileType) {
                            RecoveryFileType.Audio -> Icons.Rounded.Audiotrack
                            RecoveryFileType.Document,
                            RecoveryFileType.Archive -> Icons.Rounded.Description
                            RecoveryFileType.Other -> Icons.AutoMirrored.Rounded.InsertDriveFile
                            else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file.nonVisualMetadataLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = file.sourceLine(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                file.relativePath?.takeIf { it.isNotBlank() }?.let { relativePath ->
                    Text(
                        text = relativePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (file.isHidden) {
                        FileBadge(
                            label = stringResource(R.string.full_results_badge_hidden),
                        )
                    }
                    if (file.isTrashed) {
                        FileBadge(
                            label = file.trashBadgeLabel(),
                        )
                    }
                }
            }

            SelectionBadge(isSelected = isSelected)
        }
    }
}

@Composable
private fun FileCardFallback(
    file: RecoverableFile,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (file.fileType) {
                RecoveryFileType.Photo -> Icons.Rounded.Image
                RecoveryFileType.Video -> Icons.Rounded.PlayArrow
                RecoveryFileType.Audio -> Icons.Rounded.Audiotrack
                RecoveryFileType.Document,
                RecoveryFileType.Archive -> Icons.Rounded.Description
                RecoveryFileType.Other -> Icons.AutoMirrored.Rounded.InsertDriveFile
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun SelectionBadge(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.18f)
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun FileBadge(
    label: String,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

private fun RecoverableFile.isVisualPreview(): Boolean {
    return fileType == RecoveryFileType.Photo || fileType == RecoveryFileType.Video
}

@Composable
private fun RecoverableFile.visualMetadataLine(): String {
    val details = buildList {
        durationMillis.toCompactMediaDurationLabel()?.let(::add)
        add(sizeBytes.toReadableFileSize())
    }
    return details.joinToString(" | ")
}

@Composable
private fun RecoverableFile.nonVisualMetadataLine(): String {
    val details = buildList {
        add(typeLabel())
        durationMillis.toCompactMediaDurationLabel()?.let(::add)
        add(sizeBytes.toReadableFileSize())
    }
    return details.joinToString(" | ")
}

@Composable
private fun RecoverableFile.typeLabel(): String {
    return when (fileType) {
        RecoveryFileType.Photo -> stringResource(R.string.category_photos)
        RecoveryFileType.Video -> stringResource(R.string.category_videos)
        RecoveryFileType.Audio -> stringResource(R.string.category_audio)
        RecoveryFileType.Document -> displayName.substringAfterLast('.', "").uppercase().ifBlank {
            stringResource(R.string.full_results_filter_documents)
        }
        RecoveryFileType.Archive -> stringResource(R.string.full_results_label_archive)
        RecoveryFileType.Other -> mimeType
            ?.substringAfterLast('/')
            ?.replace('_', ' ')
            ?.uppercase()
            .orEmpty()
            .ifBlank { stringResource(R.string.full_results_filter_other) }
    }
}

@Composable
private fun RecoverableFile.sourceLine(): String {
    return when {
        isTrashed -> stringResource(R.string.category_recycle_bin)
        RecoveryFileSource.WhatsApp in sources -> stringResource(R.string.full_results_filter_whatsapp)
        RecoveryFileSource.Screenshots in sources -> stringResource(R.string.photo_filter_screenshots)
        RecoveryFileSource.Downloads in sources -> stringResource(R.string.photo_filter_downloads)
        RecoveryFileSource.Camera in sources -> stringResource(R.string.scan_location_camera)
        RecoveryFileSource.Bluetooth in sources -> stringResource(R.string.full_results_source_bluetooth)
        RecoveryFileSource.MessagingMedia in sources -> stringResource(R.string.full_results_source_messaging)
        RecoveryFileSource.SdCard in sources -> stringResource(R.string.full_results_source_sd_card)
        RecoveryFileSource.Saf in sources -> stringResource(R.string.full_results_source_authorized_folder)
        isHidden -> stringResource(R.string.full_results_badge_hidden)
        else -> storageVolume ?: stringResource(R.string.full_results_filter_other)
    }
}

@Composable
private fun RecoverableFile.trashBadgeLabel(): String {
    return dateExpiresMillis?.let { expiresAt ->
        stringResource(
            R.string.full_results_expires_on,
            expiresAt.toResultDateLabel(),
        )
    } ?: stringResource(R.string.full_results_badge_trashed)
}

private fun Long.toResultDateLabel(): String {
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
}

private fun RecoveryResultFilter.fullResultsLabelRes(): Int = when (this) {
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

private fun RecoverableFile.secondaryLabel(): String {
    return when {
        RecoveryFileSource.WhatsApp in sources -> "WhatsApp"
        RecoveryFileSource.Screenshots in sources -> "Screenshots"
        RecoveryFileSource.Downloads in sources -> "Downloads"
        RecoveryFileSource.Bluetooth in sources -> "Bluetooth"
        isTrashed -> "Recycle Bin"
        isHidden -> "Hidden"
        mimeType != null -> mimeType
        else -> fileType.name
    }
}

private fun createRestoreRequest(
    context: Context,
    files: List<RecoverableFile>,
): PendingIntent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || files.isEmpty()) {
        return null
    }
    val uris = files.map { file -> Uri.parse(file.uriString) }
    return runCatching {
        MediaStore.createTrashRequest(
            context.contentResolver,
            uris,
            false,
        )
    }.getOrNull()
}
