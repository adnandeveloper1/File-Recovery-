package com.nexappra.filerecovery.presentation.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.designsystem.theme.SuccessGreen
import com.nexappra.filerecovery.core.designsystem.theme.WarningAmber
import com.nexappra.filerecovery.core.designsystem.theme.spacing
import com.nexappra.filerecovery.core.ui.components.MessageCard

@Composable
fun PhotoRecoveryScreen(
    onBack: () -> Unit,
    onStartQuickScan: () -> Unit,
    onStartDeepScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    val spacing = MaterialTheme.spacing

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(text = stringResource(R.string.photo_recovery_info_title)) },
            text = { Text(text = stringResource(R.string.photo_recovery_info_body)) },
            confirmButton = {
                Button(onClick = { showInfoDialog = false }) {
                    Text(text = stringResource(R.string.done))
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spacing.large,
            vertical = spacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.photo_recovery_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.photo_recovery_info_title),
                    )
                }
            }
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = stringResource(R.string.recover_photos_title),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.recover_photos_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.recover_photos_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            ScanModeCard(
                title = stringResource(R.string.quick_scan_title),
                description = stringResource(R.string.quick_scan_description),
                badge = stringResource(R.string.quick_scan_recommended),
                icon = Icons.Rounded.Bolt,
                primary = true,
                bullets = listOf(
                    stringResource(R.string.quick_scan_bullet_fast),
                    stringResource(R.string.quick_scan_bullet_battery),
                    stringResource(R.string.quick_scan_bullet_best_first),
                ),
                onClick = onStartQuickScan,
                buttonLabel = stringResource(R.string.start_quick_scan),
            )
        }

        item {
            ScanModeCard(
                title = stringResource(R.string.deep_scan_title),
                description = stringResource(R.string.deep_scan_description),
                badge = null,
                icon = Icons.Rounded.Storage,
                primary = false,
                bullets = listOf(
                    stringResource(R.string.deep_scan_bullet_thorough),
                    stringResource(R.string.deep_scan_bullet_time),
                    stringResource(R.string.deep_scan_bullet_battery),
                ),
                onClick = onStartDeepScan,
                buttonLabel = stringResource(R.string.start_deep_scan),
            )
        }

        item {
            MessageCard(
                title = stringResource(R.string.scan_notice_title),
                description = stringResource(R.string.scan_notice_body),
                icon = Icons.Rounded.Info,
                actionLabel = null,
                onAction = null,
            )
        }
    }
}

@Composable
private fun ScanModeCard(
    title: String,
    description: String,
    badge: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    bullets: List<String>,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bullets.forEachIndexed { index, bullet ->
                    val tint = when {
                        primary -> SuccessGreen
                        index == 0 -> SuccessGreen
                        else -> WarningAmber
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (index == 1 && !primary) Icons.Rounded.MoreTime else Icons.Rounded.Check,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (primary) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = buttonLabel)
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buttonLabel,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
