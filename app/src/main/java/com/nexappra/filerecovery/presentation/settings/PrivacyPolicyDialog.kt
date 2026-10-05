package com.nexappra.filerecovery.presentation.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexappra.filerecovery.R

@Composable
internal fun PrivacyPolicyDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val policy = remember(context) {
        context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Privacy policy") },
        text = { Text(policy, modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
