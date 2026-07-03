package com.kryon.filemanager.features.media

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kryon.filemanager.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object MediaPlaybackState {
    val currentTrackPath = MutableStateFlow<String?>(null)
    val currentTrackName = MutableStateFlow<String?>(null)
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0)
    val duration = MutableStateFlow(0)
    val playbackSpeed = MutableStateFlow(1.0f)
    val isVideo = MutableStateFlow(false)
}

class MediaPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val TAG = "MediaPlaybackService"
        private const val NOTIFICATION_ID = 8001
        private const val CHANNEL_ID = "media_playback_channel"

        const val ACTION_PLAY = "com.kryon.filemanager.features.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.kryon.filemanager.features.media.ACTION_PAUSE"
        const val ACTION_RESUME = "com.kryon.filemanager.features.media.ACTION_RESUME"
        const val ACTION_STOP = "com.kryon.filemanager.features.media.ACTION_STOP"
        const val ACTION_SEEK = "com.kryon.filemanager.features.media.ACTION_SEEK"
        const val ACTION_SPEED = "com.kryon.filemanager.features.media.ACTION_SPEED"

        const val EXTRA_PATH = "extra_path"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_SPEED = "extra_speed"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var positionUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupMediaSession()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        when (action) {
            ACTION_PLAY -> {
                val path = intent.getStringExtra(EXTRA_PATH)
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Unknown Track"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                if (path != null) {
                    playNewTrack(path, name, isVideo)
                }
            }
            ACTION_PAUSE -> {
                pausePlayback()
            }
            ACTION_RESUME -> {
                resumePlayback()
            }
            ACTION_STOP -> {
                stopPlaybackService()
            }
            ACTION_SEEK -> {
                val position = intent.getIntExtra(EXTRA_POSITION, 0)
                seekTo(position)
            }
            ACTION_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                setPlaybackSpeed(speed)
            }
        }

        return START_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "KryonMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    resumePlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onStop() {
                    stopPlaybackService()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                }
            })
            isActive = true
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            return audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager?.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (MediaPlaybackState.isPlaying.value) {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                    mediaPlayer?.start()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
        }
    }

    private fun playNewTrack(path: String, name: String, isVideo: Boolean) {
        if (!requestAudioFocus()) {
            Log.e(TAG, "Could not acquire audio focus.")
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                
                setOnCompletionListener {
                    stopPlaybackService()
                }
            }

            MediaPlaybackState.currentTrackPath.value = path
            MediaPlaybackState.currentTrackName.value = name
            MediaPlaybackState.isPlaying.value = true
            MediaPlaybackState.duration.value = mediaPlayer?.duration ?: 0
            MediaPlaybackState.currentPosition.value = 0
            MediaPlaybackState.playbackSpeed.value = 1.0f
            MediaPlaybackState.isVideo.value = isVideo

            // Set playback speed on supported Android versions
            setPlaybackSpeed(MediaPlaybackState.playbackSpeed.value)

            updateMediaSessionState(PlaybackState.STATE_PLAYING)
            showForegroundNotification()
            startPositionUpdater()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing file: $path", e)
            stopSelf()
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                MediaPlaybackState.isPlaying.value = false
                updateMediaSessionState(PlaybackState.STATE_PAUSED)
                showForegroundNotification()
                stopPositionUpdater()
            }
        }
    }

    private fun resumePlayback() {
        if (!requestAudioFocus()) return
        mediaPlayer?.let {
            it.start()
            MediaPlaybackState.isPlaying.value = true
            updateMediaSessionState(PlaybackState.STATE_PLAYING)
            showForegroundNotification()
            startPositionUpdater()
        }
    }

    private fun seekTo(pos: Int) {
        mediaPlayer?.let {
            it.seekTo(pos)
            MediaPlaybackState.currentPosition.value = pos
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val params = it.playbackParams
                    params.speed = speed
                    it.playbackParams = params
                    MediaPlaybackState.playbackSpeed.value = speed
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting playback speed", e)
                }
            }
        }
    }

    private fun stopPlaybackService() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: Exception) {}
            it.release()
        }
        mediaPlayer = null
        
        MediaPlaybackState.currentTrackPath.value = null
        MediaPlaybackState.currentTrackName.value = null
        MediaPlaybackState.isPlaying.value = false
        MediaPlaybackState.currentPosition.value = 0
        MediaPlaybackState.duration.value = 0

        abandonAudioFocus()
        stopPositionUpdater()
        stopForeground(true)
        stopSelf()
    }

    private fun startPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        MediaPlaybackState.currentPosition.value = it.currentPosition
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updateMediaSessionState(state: Int) {
        mediaSession?.let {
            val playbackStateBuilder = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SEEK_TO
                )
                .setState(state, MediaPlaybackState.currentPosition.value.toLong(), MediaPlaybackState.playbackSpeed.value)
            it.setPlaybackState(playbackStateBuilder.build())
        }
    }

    private fun showForegroundNotification() {
        val trackName = MediaPlaybackState.currentTrackName.value ?: "Kryon Media Player"
        val isPlayingVal = MediaPlaybackState.isPlaying.value

        val openActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            501,
            openActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification Action intents
        val pauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = if (isPlayingVal) ACTION_PAUSE else ACTION_RESUME
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            502,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            503,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseText = if (isPlayingVal) "Pause" else "Play"
        val playPauseIcon = if (isPlayingVal) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentTitle(trackName)
            .setContentText(if (isPlayingVal) "Playing in background" else "Paused")
            .setSubText("Kryon Media")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val style = Notification.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1)
            builder.setStyle(style)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, playPauseIcon),
                playPauseText,
                pausePendingIntent
            ).build())
            builder.addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Close",
                stopPendingIntent
            ).build())
        } else {
            @Suppress("DEPRECATION")
            builder.addAction(playPauseIcon, playPauseText, pausePendingIntent)
            @Suppress("DEPRECATION")
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for active background media playback"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopPlaybackService()
        mediaSession?.release()
        serviceJob.cancel()
        super.onDestroy()
    }
}
