package com.nexappra.filerecovery.presentation.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.BuildConfig
import com.nexappra.filerecovery.core.ui.components.*

@Composable
fun PremiumRoute(onClose: () -> Unit, viewModel: PremiumViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = state.offers.firstOrNull { it.productId == selected } ?: state.offers.firstOrNull()
    val active = state.access.isActive()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { FlowHeader("Recovery Plus", "Premium recovery tools", onBack = onClose) }
        item {
            Column(Modifier.fillMaxWidth().recoveryHero().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = RecoveryMint, modifier = Modifier.size(28.dp))
                Text(if (active) "Ready to recover" else "Keep what matters", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text("Save original photos, videos and audio, search deeper and export your files in one place.", color = Color.White.copy(alpha = .8f))
                if (active) Text(if (state.isDebugPreview) "TEST ACCESS · DEBUG BUILD" else "SUBSCRIPTION ACTIVE", style = MaterialTheme.typography.labelMedium, color = RecoveryMint)
            }
        }
        if (BuildConfig.DEBUG) item {
            FlowPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Test Premium features", style = MaterialTheme.typography.titleSmall)
                        Text("Debug build only · no payment", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.isDebugPreview, onCheckedChange = viewModel::setDebugPreview,
                        modifier = Modifier.semantics { contentDescription = "Test Premium features" })
                }
                Text("Try deep scan, original recovery, detailed previews, repair and export. Test access resets when the app closes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("Included with Plus", style = MaterialTheme.typography.titleMedium)
        }
        item {
            FlowPanel(Modifier.fillMaxWidth()) {
                FeatureLine(Icons.Rounded.HighQuality, "Unlimited original files", "Copy all readable photos, videos and audio in their existing quality.")
                FeatureLine(Icons.Rounded.ManageSearch, "Deeper folder search", "Search folders you choose, including accessible hidden and cached media.")
                FeatureLine(Icons.Rounded.ZoomIn, "Detailed previews", "Zoom into photos and play videos before saving.")
                FeatureLine(Icons.Rounded.AutoFixHigh, "Repair a readable photo", "Create a fresh PNG from decodable pixels, up to 2048 px.")
                FeatureLine(Icons.Rounded.CloudUpload, "Cloud export", "Save an original-file ZIP to Drive or another installed file provider.")
            }
        }
        if (active) {
            item { Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Continue with Plus") } }
            if (!state.isDebugPreview) item {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=" + BuildConfig.APPLICATION_ID))) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Manage subscription in Google Play") }
            }
        } else {
            if (state.offers.isNotEmpty()) item {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.offers.forEach { offer ->
                        val isSelected = chosen?.productId == offer.productId
                        Surface(shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().selectable(selected = isSelected,
                                onClick = { selected = offer.productId }, role = Role.RadioButton).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = isSelected, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(offer.title, style = MaterialTheme.typography.titleMedium)
                                    Text(if (offer.billingPeriod == "P1Y") "Billed yearly" else "Billed monthly", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(offer.price, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { val activity = context.findActivity(); if (activity != null && chosen != null) viewModel.purchase(activity, chosen.productId) },
                    enabled = state.configured && chosen != null && !state.isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Text(if (state.isLoading) "Checking Google Play…" else if (!state.configured || chosen == null) "Plans currently unavailable" else "Subscribe with Google Play")
                }
            }
            if (state.offers.isNotEmpty()) item {
                Text("Subscription renews automatically at the price shown for the selected period unless cancelled in Google Play. Cancel any time; access continues until the paid period ends.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = viewModel::restore, enabled = !state.isLoading) { Text("Restore purchases") }
                TextButton(onClick = onClose) { Text("Keep scanning") }
            }
            Text("Recovery depends on what Android still allows you to read. Deep scan cannot access erased sectors or other apps’ private storage. Repair cannot recreate missing image data or undo severe blur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
