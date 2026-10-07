package com.nexappra.filerecovery.data.repository

import java.util.Locale

/** Match the same local file across MediaStore, document trees and directory scans. */
internal object MediaFileIdentity {
    fun storage(volume: String, path: String, name: String): String {
        val normalizedVolume = when (volume.lowercase(Locale.ROOT)) {
            "primary", "external_primary" -> "external_primary"
            else -> volume.lowercase(Locale.ROOT)
        }
        return "$normalizedVolume/" + (path.trim('/') + "/" + name).trimStart('/')
    }

    fun absolute(path: String, primaryRoot: String): String? = when {
        path.startsWith(primaryRoot.trimEnd('/') + "/") ->
            storage("primary", "", path.removePrefix(primaryRoot.trimEnd('/') + "/"))
        path.startsWith("/storage/") && !path.startsWith("/storage/emulated/") -> {
            val relative = path.removePrefix("/storage/")
            if ('/' in relative) storage(relative.substringBefore('/'), "", relative.substringAfter('/')) else null
        }
        else -> null
    }

    fun document(authority: String?, documentId: String, primaryRoot: String): String {
        if (authority == "com.android.externalstorage.documents" && ':' in documentId) {
            return storage(documentId.substringBefore(':'), "", documentId.substringAfter(':'))
        }
        if (authority == "com.android.providers.downloads.documents" && documentId.startsWith("raw:")) {
            absolute(documentId.removePrefix("raw:"), primaryRoot)?.let { return it }
        }
        // Overlapping grants to the same provider document must also share an ID.
        return "document:$authority:$documentId"
    }
}
