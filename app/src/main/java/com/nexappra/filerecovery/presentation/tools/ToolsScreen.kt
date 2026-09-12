package com.nexappra.filerecovery.presentation.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.ui.components.MessageCard

@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.tools_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MessageCard(
            title = stringResource(R.string.tools_large_file_finder),
            description = stringResource(R.string.tools_large_file_finder_description),
            icon = Icons.Rounded.Folder,
        )
        MessageCard(
            title = stringResource(R.string.tools_storage_analyzer),
            description = stringResource(R.string.tools_storage_analyzer_description),
            icon = Icons.Rounded.Storage,
        )
        MessageCard(
            title = stringResource(R.string.tools_reliable_only),
            description = stringResource(R.string.tools_reliable_only_description),
            icon = Icons.Rounded.Build,
        )
    }
}
