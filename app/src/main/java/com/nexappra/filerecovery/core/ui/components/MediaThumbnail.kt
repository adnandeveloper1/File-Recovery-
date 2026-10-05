package com.nexappra.filerecovery.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryFileType

@Composable
fun MediaThumbnail(file: RecoverableFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(file.uriString) {
        ImageRequest.Builder(context).data(file.uriString).size(320).apply {
            if (file.fileType == RecoveryFileType.Video) decoderFactory(VideoFrameDecoder.Factory())
        }.build()
    }
    var failed by remember(file.uriString) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (file.fileType == RecoveryFileType.Audio) {
            Icon(Icons.Rounded.Audiotrack, "Audio file", tint = MaterialTheme.colorScheme.primary)
        } else {
            AsyncImage(request, file.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
            if (failed) Icon(Icons.Rounded.ImageNotSupported, "Preview unavailable", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (file.fileType == RecoveryFileType.Video && !failed) Icon(Icons.Rounded.PlayCircle, "Video", tint = Color.White)
        }
    }
}
