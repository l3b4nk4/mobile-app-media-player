package tech.b4nk4.mediaplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tech.b4nk4.mediaplayer.R
import tech.b4nk4.mediaplayer.model.Track

import java.util.Locale

class TrackAdapter(
    private val isFavorite: (Track) -> Boolean,
    private val onTrackClick: (Track) -> Unit,
    private val onFavoriteClick: (Track) -> Unit
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    private val tracks = mutableListOf<Track>()

    fun submitList(newTracks: List<Track>) {
        tracks.clear()
        tracks.addAll(newTracks)
        notifyDataSetChanged()
    }

    inner class TrackViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTrackTitle)
        val tvArtist: TextView = itemView.findViewById(R.id.tvTrackArtist)
        val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.tvTitle.text = track.title
        
        val duration = track.durationMs / 1000
        val mins = duration / 60
        val secs = duration % 60
        holder.tvArtist.text = "${track.artist} • ${String.format(java.util.Locale.getDefault(), "%d:%02d", mins, secs)}"

        val favorited = isFavorite(track)
        holder.btnFavorite.setImageResource(
            if (favorited) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )

        holder.itemView.setOnClickListener { onTrackClick(track) }
        holder.btnFavorite.setOnClickListener { onFavoriteClick(track) }
    }

    override fun getItemCount(): Int = tracks.size
}
