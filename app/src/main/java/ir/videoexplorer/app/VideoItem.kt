package ir.videoexplorer.app

import android.net.Uri

data class VideoItem(
    val uri: Uri,
    val name: String,
    val folder: String
)
