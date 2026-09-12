package com.nexappra.filerecovery.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexappra.filerecovery.core.ui.components.MessageCard
import com.nexappra.filerecovery.core.ui.components.SecondaryTopBar
import com.nexappra.filerecovery.domain.model.RecoveryCategory

@Composable
fun CategoryPlaceholderScreen(
    category: RecoveryCategory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = category.toUiSpec()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SecondaryTopBar(
            title = stringResource(spec.titleRes),
            onBack = onBack,
        )
        Text(
            text = stringResource(spec.subtitleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MessageCard(
            title = stringResource(spec.titleRes),
            description = stringResource(spec.detailRes),
            icon = spec.icon,
            accentColor = spec.accentColor,
        )
    }
}
