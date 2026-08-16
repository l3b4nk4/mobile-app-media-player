package tech.b4nk4.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import tech.b4nk4.mediaplayer.model.Track

/**
 * Queries the device's MediaStore for all playable audio files.
 * Requires READ_MEDIA_AUDIO (API 33+) or READ_EXTERNAL_STORAGE (below)
 * to already be granted before calling [loadTracks].
 */
object MediaStoreRepository {

    fun loadTracks(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        // Only actual music/audio tracks, not notification sounds, ringtones, etc.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown title"
                val artist = cursor.getString(artistCol) ?: "Unknown artist"
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(collection, id)

                tracks.add(Track(id, title, artist, duration, contentUri))
            }
        }

        return tracks
    }
}
