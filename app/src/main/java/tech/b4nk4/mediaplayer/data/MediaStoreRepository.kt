package tech.b4nk4.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import tech.b4nk4.mediaplayer.model.Track

/**
 * Queries the device's MediaStore for all playable audio files.
 * Broadened to include all audio files > 2 seconds (recordings, podcasts, etc.)
 */
object MediaStoreRepository {

    fun loadTracks(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        
        // Include any audio file longer than 2 seconds
        val selection = "${MediaStore.Audio.Media.DURATION} >= 2000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

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
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown title"
                val artist = cursor.getString(artistCol) ?: "Unknown artist"
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                val contentUri = ContentUris.withAppendedId(collection, id)

                tracks.add(Track(id, title, artist, albumId, duration, size, dateAdded, contentUri))
            }
        }

        return tracks
    }
}
