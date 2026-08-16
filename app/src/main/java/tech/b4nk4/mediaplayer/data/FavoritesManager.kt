package tech.b4nk4.mediaplayer.data

import android.content.Context

/**
 * Stores the set of favorited track IDs in SharedPreferences.
 * Simple and durable across app restarts; swap for a Room table later
 * if you need more than just an ID set (e.g. custom ordering, notes).
 */
class FavoritesManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFavorite(trackId: Long): Boolean {
        return getFavoriteIds().contains(trackId.toString())
    }

    /** Returns the new favorite state after toggling (true = now favorited). */
    fun toggleFavorite(trackId: Long): Boolean {
        val current = getFavoriteIds().toMutableSet()
        val idStr = trackId.toString()
        val nowFavorite: Boolean
        if (current.contains(idStr)) {
            current.remove(idStr)
            nowFavorite = false
        } else {
            current.add(idStr)
            nowFavorite = true
        }
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, current).apply()
        return nowFavorite
    }

    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_IDS, emptySet()) ?: emptySet()
    }

    companion object {
        private const val PREFS_NAME = "media_player_favorites"
        private const val KEY_FAVORITE_IDS = "favorite_track_ids"
    }
}
