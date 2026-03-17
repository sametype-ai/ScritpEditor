package com.example.videoplayer

import android.net.Uri

data class VideoItem(
    val id: Long,
    val name: String,
    val uri: Uri,
    val duration: Long,   // milliseconds
    val size: Long        // bytes
) {
    val durationText: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val sizeText: String
        get() {
            return when {
                size >= 1024 * 1024 * 1024 -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
                size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
                size >= 1024 -> String.format("%.1f KB", size / 1024.0)
                else -> "$size B"
            }
        }
}
