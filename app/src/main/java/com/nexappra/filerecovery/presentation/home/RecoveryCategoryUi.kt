package com.nexappra.filerecovery.presentation.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.designsystem.theme.AudioAccent
import com.nexappra.filerecovery.core.designsystem.theme.DocumentAccent
import com.nexappra.filerecovery.core.designsystem.theme.DownloadAccent
import com.nexappra.filerecovery.core.designsystem.theme.LargeFileAccent
import com.nexappra.filerecovery.core.designsystem.theme.PhotoAccent
import com.nexappra.filerecovery.core.designsystem.theme.RecycleBinAccent
import com.nexappra.filerecovery.core.designsystem.theme.ScreenshotAccent
import com.nexappra.filerecovery.core.designsystem.theme.VaultAccent
import com.nexappra.filerecovery.core.designsystem.theme.VideoAccent
import com.nexappra.filerecovery.core.designsystem.theme.WhatsAppAccent
import com.nexappra.filerecovery.domain.model.RecoveryCategory

data class RecoveryCategoryUiSpec(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    @param:StringRes val detailRes: Int,
    val icon: ImageVector,
    val accentColor: Color,
)

fun RecoveryCategory.toUiSpec(): RecoveryCategoryUiSpec = when (this) {
    RecoveryCategory.Photos -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_photos,
        subtitleRes = R.string.category_photos_subtitle,
        detailRes = R.string.category_photos_detail,
        icon = Icons.Rounded.Collections,
        accentColor = PhotoAccent,
    )
    RecoveryCategory.Videos -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_videos,
        subtitleRes = R.string.category_videos_subtitle,
        detailRes = R.string.category_videos_detail,
        icon = Icons.Rounded.Movie,
        accentColor = VideoAccent,
    )
    RecoveryCategory.Audio -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_audio,
        subtitleRes = R.string.category_audio_subtitle,
        detailRes = R.string.category_audio_detail,
        icon = Icons.Rounded.Audiotrack,
        accentColor = AudioAccent,
    )
    RecoveryCategory.Documents -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_documents,
        subtitleRes = R.string.category_documents_subtitle,
        detailRes = R.string.category_documents_detail,
        icon = Icons.Rounded.Description,
        accentColor = DocumentAccent,
    )
    RecoveryCategory.WhatsAppImages -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_whatsapp_images,
        subtitleRes = R.string.category_whatsapp_images_subtitle,
        detailRes = R.string.category_whatsapp_images_detail,
        icon = Icons.Rounded.Image,
        accentColor = WhatsAppAccent,
    )
    RecoveryCategory.WhatsAppVideos -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_whatsapp_videos,
        subtitleRes = R.string.category_whatsapp_videos_subtitle,
        detailRes = R.string.category_whatsapp_videos_detail,
        icon = Icons.Rounded.SmartDisplay,
        accentColor = WhatsAppAccent,
    )
    RecoveryCategory.HiddenVaults -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_hidden_vaults,
        subtitleRes = R.string.category_hidden_vaults_subtitle,
        detailRes = R.string.category_hidden_vaults_detail,
        icon = Icons.Rounded.Lock,
        accentColor = VaultAccent,
    )
    RecoveryCategory.Downloads -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_downloads,
        subtitleRes = R.string.category_downloads_subtitle,
        detailRes = R.string.category_downloads_detail,
        icon = Icons.Rounded.Download,
        accentColor = DownloadAccent,
    )
    RecoveryCategory.Screenshots -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_screenshots,
        subtitleRes = R.string.category_screenshots_subtitle,
        detailRes = R.string.category_screenshots_detail,
        icon = Icons.Rounded.CropFree,
        accentColor = ScreenshotAccent,
    )
    RecoveryCategory.RecycleBin -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_recycle_bin,
        subtitleRes = R.string.category_recycle_bin_subtitle,
        detailRes = R.string.category_recycle_bin_detail,
        icon = Icons.Rounded.DeleteOutline,
        accentColor = RecycleBinAccent,
    )
    RecoveryCategory.LargeFiles -> RecoveryCategoryUiSpec(
        titleRes = R.string.category_large_files,
        subtitleRes = R.string.category_large_files_subtitle,
        detailRes = R.string.category_large_files_detail,
        icon = Icons.Rounded.Folder,
        accentColor = LargeFileAccent,
    )
}
