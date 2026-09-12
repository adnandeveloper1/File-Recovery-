package com.nexappra.filerecovery.presentation.scan

import androidx.annotation.StringRes
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.domain.model.PhotoResultFilter
import com.nexappra.filerecovery.domain.model.PhotoScanLocationState
import com.nexappra.filerecovery.domain.model.PhotoScanLocationType
import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.ScanLocationStatus

fun orderedPhotoLocationsFor(mode: PhotoScanMode): List<PhotoScanLocationType> = when (mode) {
    PhotoScanMode.Quick -> listOf(
        PhotoScanLocationType.Camera,
        PhotoScanLocationType.Screenshots,
        PhotoScanLocationType.Downloads,
        PhotoScanLocationType.Pictures,
        PhotoScanLocationType.Gallery,
    )

    PhotoScanMode.Deep -> listOf(
        PhotoScanLocationType.Camera,
        PhotoScanLocationType.Screenshots,
        PhotoScanLocationType.Downloads,
        PhotoScanLocationType.Pictures,
        PhotoScanLocationType.Gallery,
        PhotoScanLocationType.OtherMedia,
    )
}

fun defaultLocationStatesFor(mode: PhotoScanMode): List<PhotoScanLocationState> = orderedPhotoLocationsFor(mode)
    .map { type -> PhotoScanLocationState(type = type, status = ScanLocationStatus.Pending) }

@StringRes
fun PhotoScanLocationType.titleRes(): Int = when (this) {
    PhotoScanLocationType.Camera -> R.string.scan_location_camera
    PhotoScanLocationType.Screenshots -> R.string.scan_location_screenshots
    PhotoScanLocationType.Downloads -> R.string.scan_location_downloads
    PhotoScanLocationType.Pictures -> R.string.scan_location_pictures
    PhotoScanLocationType.Gallery -> R.string.scan_location_gallery
    PhotoScanLocationType.OtherMedia -> R.string.scan_location_other_media
}

@StringRes
fun PhotoResultFilter.labelRes(): Int = when (this) {
    PhotoResultFilter.All -> R.string.photo_filter_all
    PhotoResultFilter.Photos -> R.string.photo_filter_photos
    PhotoResultFilter.Screenshots -> R.string.photo_filter_screenshots
    PhotoResultFilter.Camera -> R.string.photo_filter_camera
    PhotoResultFilter.Downloads -> R.string.photo_filter_downloads
}

@StringRes
fun PhotoScanMode.labelRes(): Int = when (this) {
    PhotoScanMode.Quick -> R.string.quick_scan_label
    PhotoScanMode.Deep -> R.string.deep_scan_label
}
