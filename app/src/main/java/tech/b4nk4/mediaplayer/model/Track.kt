package tech.b4nk4.mediaplayer.model

import android.net.Uri

/**
 * Represents a single audio file found on the device via MediaStore.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: Uri
)
