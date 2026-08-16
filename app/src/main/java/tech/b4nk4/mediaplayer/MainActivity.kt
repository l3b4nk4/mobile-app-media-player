package tech.b4nk4.mediaplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import tech.b4nk4.mediaplayer.R
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : AppCompatActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvStatus: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnReplay: Button
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private val playlist = listOf(
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

        tvStatus = findViewById(R.id.tvStatus)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnReplay = findViewById(R.id.btnReplay)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        
        btnPlay.setOnClickListener {
            controller?.play()
        }
        btnPause.setOnClickListener {
            controller?.pause()
        }
        btnReplay.setOnClickListener {
            controller?.seekTo(0)
            controller?.play()
        }
        btnPrev.setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }
        btnNext.setOnClickListener {
            controller?.seekToNextMediaItem()
        }
        
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

    private fun setupController() {
        controller?.let { ctrl ->
            if (ctrl.mediaItemCount == 0) {
                ctrl.setMediaItems(playlist)
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
                    } else if (ctrl.playbackState != Player.STATE_ENDED) {
                        tvStatus.text = "Paused"
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