package com.nexappra.filerecovery.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("File Recovery", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                    Text("Photos, videos & audio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onPremiumClick, shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(if (uiState.isPremium) "Plus" else "Get Plus", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = RecoveryInk) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Find your media.", fontSize = 29.sp, lineHeight = 35.sp, letterSpacing = (-0.8).sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Search your device. Preview your files before choosing what to save.",
                        color = Color(0xFFD0DDF1), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F0FF), contentColor = RecoveryInk)) {
                        Text("Quick scan", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Browse by type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Choose a category to start scanning.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CategoryTile("Photos", "JPG, PNG, HEIC + more", Icons.Rounded.PhotoLibrary, Color(0xFFE8F0FD), Modifier.fillMaxWidth()) { onCategoryClick(RecoveryCategory.Photos) }
                CategoryTile("Videos", "MP4, MOV, MKV + more", Icons.Rounded.VideoLibrary, Color(0xFFEDE8F8), Modifier.fillMaxWidth()) { onCategoryClick(RecoveryCategory.Videos) }
                CategoryTile("Audio", "Music, recordings & voice notes", Icons.Rounded.Audiotrack, Color(0xFFFAECD9), Modifier.fillMaxWidth()) { onCategoryClick(RecoveryCategory.Audio) }
            }
        }
        item {
            Surface(onClick = onDeepScan, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.TravelExplore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Deep scan", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("PLUS", modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Search chosen folders for hidden media and cached copies.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storage, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); Text("Device storage", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(uiState.storageInfo?.let { "${it.availableBytes.toDisplayGigabytes()} GB free" } ?: "Checking…", style = MaterialTheme.typography.labelMedium)
                }
                val storage = uiState.storageInfo
                if (storage != null) LinearProgressIndicator(progress = { storage.usedFraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                else if (uiState.errorMessage != null) TextButton(onClick = onRetryStorage) { Text("Retry storage check") }
                Text("Scans stay on your device. Files are exported only when you choose a destination.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(accent, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = RecoveryInk, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
