package com.nexappra.filerecovery.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.designsystem.theme.FileRecoveryTheme
import com.nexappra.filerecovery.core.designsystem.theme.spacing
import com.nexappra.filerecovery.core.ui.components.FullScanCard
import com.nexappra.filerecovery.core.ui.components.PremiumBadge
import com.nexappra.filerecovery.core.ui.components.RecoveryCategoryCard
import com.nexappra.filerecovery.core.ui.components.RecoveryLogoBadge
import com.nexappra.filerecovery.core.ui.components.SectionHeader
import com.nexappra.filerecovery.core.ui.components.StorageUsageCard
import com.nexappra.filerecovery.core.ui.components.StorageUsageErrorCard
import com.nexappra.filerecovery.core.ui.components.StorageUsageLoadingCard
import com.nexappra.filerecovery.core.utils.toDisplayGigabytes
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.StorageInfo

@Composable
fun HomeRoute(
    onCategoryClick: (RecoveryCategory) -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onCategoryClick = onCategoryClick,
        onScanClick = onScanClick,
        onPremiumClick = onPremiumClick,
        onRetryStorage = viewModel::refreshStorage,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onCategoryClick: (RecoveryCategory) -> Unit,
    onScanClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onRetryStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val compactGridSpacing = 10.dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = spacing.large,
            vertical = spacing.large,
        ),
        horizontalArrangement = Arrangement.spacedBy(
            compactGridSpacing,
        ),
        verticalArrangement = Arrangement.spacedBy(
            compactGridSpacing,
        ),
    ) {

        /*
         * TOP BAR
         */
        item(
            span = {
                GridItemSpan(maxLineSpan)
            },
        ) {
            HomeTopBar(
                onPremiumClick = onPremiumClick,
            )
        }

        /*
         * DEVICE STORAGE
         */
        item(
            span = {
                GridItemSpan(maxLineSpan)
            },
        ) {

            when {

                uiState.isLoading &&
                        uiState.storageInfo == null -> {

                    StorageUsageLoadingCard(
                        title = stringResource(
                            R.string.device_storage,
                        ),
                        message = stringResource(
                            R.string.loading_device_storage,
                        ),
                    )
                }

                uiState.storageInfo != null -> {

                    val storageInfo = uiState.storageInfo

                    val usedStorage =
                        storageInfo.usedBytes
                            .toDisplayGigabytes()

                    val availableStorage =
                        storageInfo.availableBytes
                            .toDisplayGigabytes()

                    val totalStorage =
                        storageInfo.totalBytes
                            .toDisplayGigabytes()

                    StorageUsageCard(
                        title = stringResource(
                            R.string.device_storage,
                        ),
                        usageSummary = stringResource(
                            R.string.storage_used_format,
                            usedStorage,
                            totalStorage,
                        ),
                        usedLegend = stringResource(
                            R.string.storage_legend_used,
                            usedStorage,
                        ),
                        availableLegend = stringResource(
                            R.string.storage_legend_available,
                            availableStorage,
                        ),
                        progress = storageInfo.usedFraction,
                        progressLabel = stringResource(
                            R.string.storage_percentage_format,
                            storageInfo.usedPercentage,
                        ),
                    )
                }

                else -> {

                    StorageUsageErrorCard(
                        title = stringResource(
                            R.string.device_storage,
                        ),
                        message = uiState.errorMessage
                            ?: stringResource(
                                R.string.storage_error_message,
                            ),
                        actionLabel = stringResource(
                            R.string.retry,
                        ),
                        onAction = onRetryStorage,
                    )
                }
            }
        }

        /*
         * FULL DEVICE SCAN
         */
        item(
            span = {
                GridItemSpan(maxLineSpan)
            },
        ) {

            FullScanCard(
                title = stringResource(
                    R.string.start_full_device_scan,
                ),
                subtitle = stringResource(
                    R.string.search_recoverable_files,
                ),
                buttonLabel = stringResource(
                    R.string.scan_device,
                ),
                onClick = onScanClick,
            )
        }

        /*
         * RECOVERY CATEGORIES HEADER
         */
        item(
            span = {
                GridItemSpan(maxLineSpan)
            },
        ) {

            SectionHeader(
                title = stringResource(
                    R.string.recovery_categories,
                ),
            )
        }

        /*
         * RECOVERY CATEGORY GRID
         */
        items(
            items = uiState.categories,
            key = { category ->
                category.name
            },
        ) { category ->

            val spec = category.toUiSpec()

            RecoveryCategoryCard(
                title = stringResource(
                    spec.titleRes,
                ),
                subtitle = stringResource(
                    spec.subtitleRes,
                ),
                icon = spec.icon,
                accentColor = spec.accentColor,
                onClick = {
                    onCategoryClick(category)
                },
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    onPremiumClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {

            RecoveryLogoBadge(
                contentDescription = stringResource(
                    R.string.app_name,
                ),
            )

            Text(
                text = stringResource(
                    R.string.home_title,
                ),
                style =
                    MaterialTheme.typography.titleLarge,
                color =
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PremiumBadge(
            label = stringResource(
                R.string.premium_label,
            ),
            onClick = onPremiumClick,
        )
    }
}

/*
 * LIGHT MODE PREVIEW
 */
@Preview(
    name = "Home - Light",
    showBackground = true,
)
@Composable
private fun HomeScreenPreview() {

    FileRecoveryTheme(
        darkTheme = false,
    ) {

        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                storageInfo = StorageInfo(
                    totalBytes =
                        128L * 1024L * 1024L * 1024L,
                    availableBytes =
                        45L * 1024L * 1024L * 1024L,
                ),
            ),
            onCategoryClick = {},
            onScanClick = {},
            onPremiumClick = {},
            onRetryStorage = {},
        )
    }
}

/*
 * DARK MODE PREVIEW
 */
@Preview(
    name = "Home - Dark",
    showBackground = true,
)
@Composable
private fun HomeScreenDarkPreview() {

    FileRecoveryTheme(
        darkTheme = true,
    ) {

        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                storageInfo = StorageInfo(
                    totalBytes =
                        128L * 1024L * 1024L * 1024L,
                    availableBytes =
                        45L * 1024L * 1024L * 1024L,
                ),
            ),
            onCategoryClick = {},
            onScanClick = {},
            onPremiumClick = {},
            onRetryStorage = {},
        )
    }
}