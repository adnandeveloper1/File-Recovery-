package com.nexappra.filerecovery.presentation.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.designsystem.theme.SuccessGreen
import com.nexappra.filerecovery.core.designsystem.theme.spacing
import com.nexappra.filerecovery.core.utils.toDetailedDurationLabel
import com.nexappra.filerecovery.core.utils.toFormattedCount
import com.nexappra.filerecovery.core.utils.toReadableFileSize
import com.nexappra.filerecovery.domain.model.PhotoResultFilter
import com.nexappra.filerecovery.domain.model.RecoverablePhoto

@Composable
fun PhotoResultsRoute(
    onBack: () -> Unit,
    viewModel: PhotoResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PhotoResultsEvent.ShowRecoveryUnavailable -> {
                    snackbarHostState.showSnackbar(
                        message = "Recovery flow is not available in this build yet.",
                    )
                }
            }
        }
    }

    PhotoResultsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onFilterSelected = viewModel::onFilterSelected,
        onToggleSearch = viewModel::onSearchVisibilityToggle,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onPhotoClick = { photo -> viewModel.onToggleSelection(photo.id) },
        onRecoverSelected = viewModel::onRecoverSelected,
    )
}

@Composable
fun PhotoResultsScreen(
    uiState: PhotoResultsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFilterSelected: (PhotoResultFilter) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPhotoClick: (RecoverablePhoto) -> Unit,
    onRecoverSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val filters = listOf(
        PhotoResultFilter.All,
        PhotoResultFilter.Photos,
        PhotoResultFilter.Screenshots,
        PhotoResultFilter.Camera,
        PhotoResultFilter.Downloads,
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
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.photos_found_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(
                                    R.string.photos_found_count,
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
                            Text(text = stringResource(R.string.photo_search_placeholder))
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
                            Text(
                                text = stringResource(
                                    R.string.recover_selected_photos,
                                    uiState.selectedIds.size.toFormattedCount(),
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = spacing.large,
                top = 0.dp,
                end = spacing.large,
                bottom = if (uiState.selectedIds.isNotEmpty()) 112.dp else spacing.section,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                Text(text = stringResource(filter.labelRes()))
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
                                R.string.photo_results_summary,
                                stringResource(uiState.scanMode.labelRes()),
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
                                    stringResource(R.string.photo_results_missing_session)
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

                uiState.visiblePhotos.isEmpty() -> {
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
                                    text = stringResource(R.string.photo_results_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (uiState.searchQuery.isBlank()) {
                                        stringResource(R.string.photo_results_empty_body)
                                    } else {
                                        stringResource(R.string.photo_results_empty_search_body)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                else -> {
                    items(
                        items = uiState.visiblePhotos,
                        key = { photo -> photo.id },
                    ) { photo ->
                        PhotoGridItem(
                            photo = photo,
                            isSelected = photo.id in uiState.selectedIds,
                            onClick = { onPhotoClick(photo) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: RecoverablePhoto,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box {
            SubcomposeAsyncImage(
                model = photo.uriString,
                contentDescription = photo.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                        shape = CircleShape,
                    )
                    .then(
                        if (isSelected) Modifier else Modifier
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
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Transparent, CircleShape)
                            .clip(CircleShape)
                            .background(Color.Transparent),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Text(
                    text = photo.sizeBytes.toReadableFileSize(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}
