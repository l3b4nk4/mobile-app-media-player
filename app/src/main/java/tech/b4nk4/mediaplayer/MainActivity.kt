package tech.b4nk4.mediaplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.view.GravityCompat
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import tech.b4nk4.mediaplayer.data.FavoritesManager
import tech.b4nk4.mediaplayer.data.MediaStoreRepository
import tech.b4nk4.mediaplayer.model.Track
import tech.b4nk4.mediaplayer.ui.TrackAdapter

class MainActivity : AppCompatActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvStatus: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var ivAlbumArt: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnReplay: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnOpenDrawer: ImageButton

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rvTracks: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnShowAllSongs: Button
    private lateinit var btnShowFavorites: Button

    private lateinit var favoritesManager: FavoritesManager
    private lateinit var trackAdapter: TrackAdapter

    /** All tracks found on the device via MediaStore. */
    private var allTracks: List<Track> = emptyList()

    /** Which list is currently shown/queued: device songs or favorites. */
    private var showingFavorites = false

    // Fallback online playlist, used until the user picks a track from the device.
    private val defaultPlaylist = listOf(
        MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"),
        MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/play.mp3")
    )

    private val updateProgressAction = object : Runnable {
        override fun run() {
            controller?.let {
                if (it.isPlaying) {
                    seekBar.progress = it.currentPosition.toInt()
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadDeviceTracks() else tvEmptyState.apply {
            text = getString(R.string.no_songs_found)
            visibility = android.view.View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                controller = controllerFuture?.get()
                setupController()
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val rootView = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        favoritesManager = FavoritesManager(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnReplay = findViewById(R.id.btnReplay)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnOpenDrawer = findViewById(R.id.btnOpenDrawer)

        drawerLayout = findViewById(R.id.drawerLayout)
        rvTracks = findViewById(R.id.rvTracks)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnShowAllSongs = findViewById(R.id.btnShowAllSongs)
        btnShowFavorites = findViewById(R.id.btnShowFavorites)

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        setupDrawer()
        ensureAudioPermissionThenLoad()

        btnOpenDrawer.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        btnPlayPause.setOnClickListener {
            controller?.let { ctrl ->
                if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
            }
        }
        btnReplay.setOnClickListener {
            controller?.seekTo(0)
            controller?.play()
        }
        btnPrev.setOnClickListener { controller?.seekToPreviousMediaItem() }
        btnNext.setOnClickListener { controller?.seekToNextMediaItem() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    controller?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // --- Drawer / device song list -----------------------------------------

    private fun setupDrawer() {
        trackAdapter = TrackAdapter(
            isFavorite = { track -> favoritesManager.isFavorite(track.id) },
            onTrackClick = { track -> playFromList(currentDisplayedTracks(), track) },
            onFavoriteClick = { track ->
                favoritesManager.toggleFavorite(track.id)
                trackAdapter.notifyDataSetChanged()
                if (showingFavorites) refreshDrawerList()
            }
        )
        rvTracks.layoutManager = LinearLayoutManager(this)
        rvTracks.adapter = trackAdapter

        btnShowAllSongs.setOnClickListener {
            showingFavorites = false
            refreshDrawerList()
        }
        btnShowFavorites.setOnClickListener {
            showingFavorites = true
            refreshDrawerList()
        }
    }

    private fun ensureAudioPermissionThenLoad() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadDeviceTracks()
        } else {
            requestAudioPermission.launch(permission)
        }
    }

    private fun loadDeviceTracks() {
        allTracks = MediaStoreRepository.loadTracks(this)
        refreshDrawerList()
    }

    private fun currentDisplayedTracks(): List<Track> {
        return if (showingFavorites) {
            allTracks.filter { favoritesManager.isFavorite(it.id) }
        } else {
            allTracks
        }
    }

    private fun refreshDrawerList() {
        val tracks = currentDisplayedTracks()
        trackAdapter.submitList(tracks)

        val emptyMessage = if (showingFavorites) {
            getString(R.string.no_favorites_yet)
        } else {
            getString(R.string.no_songs_found)
        }
        tvEmptyState.text = emptyMessage
        tvEmptyState.visibility = if (tracks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        rvTracks.visibility = if (tracks.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun playFromList(tracks: List<Track>, selected: Track) {
        val ctrl = controller ?: return
        val startIndex = tracks.indexOf(selected).coerceAtLeast(0)
        val mediaItems = tracks.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setArtworkUri(Uri.parse("content://media/external/audio/albumart/${track.albumId}"))
                .build()
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.contentUri)
                .setMediaMetadata(metadata)
                .build()
        }

        ctrl.setMediaItems(mediaItems, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()

        drawerLayout.closeDrawer(GravityCompat.START)
    }

    // --- Player controller ---------------------------------------------------

    private fun setupController() {
        controller?.let { ctrl ->
            if (ctrl.mediaItemCount == 0) {
                ctrl.setMediaItems(defaultPlaylist)
                ctrl.prepare()
            }

            ctrl.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            seekBar.max = ctrl.duration.toInt()
                            tvStatus.text = "Ready to play"
                        }
                        Player.STATE_BUFFERING -> tvStatus.text = "Buffering..."
                        Player.STATE_ENDED -> tvStatus.text = "Ended"
                        Player.STATE_IDLE -> tvStatus.text = "Idle"
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        tvStatus.text = "Playing"
                        btnPlayPause.setImageResource(R.drawable.ic_pause)
                    } else if (ctrl.playbackState != Player.STATE_ENDED) {
                        tvStatus.text = "Paused"
                        btnPlayPause.setImageResource(R.drawable.ic_play)
                    }
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title ?: "Unknown Title"
                    val artist = mediaMetadata.artist ?: "Unknown Artist"
                    tvNowPlaying.text = "$title — $artist"

                    ivAlbumArt.load(mediaMetadata.artworkUri) {
                        placeholder(R.drawable.ic_default_album_art)
                        error(R.drawable.ic_default_album_art)
                        crossfade(true)
                    }
                }
            })

            // start updating seekbar
            handler.post(updateProgressAction)
        }
    }

    override fun onStop() {
        handler.removeCallbacks(updateProgressAction)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }
}
