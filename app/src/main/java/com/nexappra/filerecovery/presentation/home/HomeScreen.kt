package com.nexappra.filerecovery.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.core.ui.components.*
import com.nexappra.filerecovery.core.utils.toDisplayGigabytes
import com.nexappra.filerecovery.domain.model.RecoveryCategory

@Composable
fun HomeRoute(onCategoryClick: (RecoveryCategory) -> Unit, onScanClick: () -> Unit, onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit, onDeepScan: () -> Unit = onPremiumClick, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, onCategoryClick, onScanClick, onPremiumClick, viewModel::refreshStorage,
        onDeepScan = { if (state.isPremium) onDeepScan() else onPremiumClick() })
}

@Composable
fun HomeScreen(uiState: HomeUiState, onCategoryClick: (RecoveryCategory) -> Unit, onScanClick: () -> Unit,
    onPremiumClick: () -> Unit, onRetryStorage: () -> Unit, modifier: Modifier = Modifier, onDeepScan: () -> Unit = onPremiumClick) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            FlowHeader("File Recovery", "Photos, videos & audio") {
                FilledTonalButton(onClick = onPremiumClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Icon(Icons.Rounded.WorkspacePremium, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
                    Text(if (uiState.isPremium) "Plus" else "Get Plus", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().recoveryHero().padding(26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Radar, null, tint = RecoveryMint, modifier = Modifier.size(22.dp))
                    Text("PHOTO, VIDEO & AUDIO RECOVERY", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = RecoveryMint)
                }
                Text("Find your media.\nSave what matters.", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Scan photos, videos or audio separately. Preview what is available as we find it.", color = Color(0xFFDCE8FA), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RecoveryMint, contentColor = RecoveryInk)) {
                    Text("Quick scan", fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                }
                Text("FREE SCAN  ·  LIVE PREVIEWS  ·  ON YOUR DEVICE", color = RecoveryMint, fontSize = 9.sp, letterSpacing = 0.7.sp)
            }
        }
        item {
            Text("Choose what to find", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Scan one category. See only what matters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CategoryTile("Photos", "JPG, PNG, HEIC + more", Icons.Rounded.PhotoLibrary, Color(0xFFDBEAFE), Modifier.weight(1f)) { onCategoryClick(RecoveryCategory.Photos) }
                CategoryTile("Videos", "MP4, MOV, MKV + more", Icons.Rounded.VideoLibrary, Color(0xFFEDE7F9), Modifier.weight(1f)) { onCategoryClick(RecoveryCategory.Videos) }
            }
            Spacer(Modifier.height(0.dp))
            CategoryTile("Audio", "MP3, M4A, WAV, Voice Notes", Icons.Rounded.Audiotrack, Color(0xFFFCE7F3), Modifier.fillMaxWidth()) { onCategoryClick(RecoveryCategory.Audio) }
        }
        item {
            FlowPanel(Modifier.fillMaxWidth().clickable(onClick = onDeepScan)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TravelExplore, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp)); Text("Deep scan", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("PLUS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                Text("Search chosen folders for hidden media and readable cached copies.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text("Explore deep scan →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            FlowPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storage, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); Text("Device storage", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(uiState.storageInfo?.let { "${it.availableBytes.toDisplayGigabytes()} GB free" } ?: "Checking…", style = MaterialTheme.typography.labelMedium)
                }
                val storage = uiState.storageInfo
                if (storage != null) LinearProgressIndicator(progress = { storage.usedFraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
                else if (uiState.errorMessage != null) TextButton(onClick = onRetryStorage) { Text("Retry storage check") }
                Text("Quick scans stay on this device. Export happens only when you choose a destination.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).background(accent, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = RecoveryInk) }
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
