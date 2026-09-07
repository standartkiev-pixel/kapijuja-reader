package com.kapijuja.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder

class ReaderPlaybackService : Service() {
    private lateinit var mediaSession: MediaSession
    private var currentTitle: String = "Kapijuja Reader"
    private var playing = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSession(this, "KapijujaReader").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = toggle()
                override fun onPause() = toggle()
                override fun onStop() = stopPlayback()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_STOP -> stopPlayback()
            ACTION_SYNC, null -> {
                currentTitle = intent?.getStringExtra(EXTRA_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: currentTitle
                playing = intent?.getBooleanExtra(EXTRA_PLAYING, playing) ?: playing
                publish()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun toggle() {
        PlaybackBridge.controller?.toggleFromNotification()
        playing = !playing
        publish()
    }

    private fun stopPlayback() {
        PlaybackBridge.controller?.stopFromNotification()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        stopSelf()
    }

    private fun publish() {
        val actions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP

        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Kapijuja Reader")
                .build()
        )

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val togglePending = PendingIntent.getService(
            this,
            1,
            Intent(this, ReaderPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            flags
        )
        val stopPending = PendingIntent.getService(
            this,
            2,
            Intent(this, ReaderPlaybackService::class.java).apply { action = ACTION_STOP },
            flags
        )
        val contentPending = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 3, it, flags)
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(
                if (playing) text("Читается", "Reading") else text("Пауза", "Paused")
            )
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentPending)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) text("Пауза", "Pause") else text("Продолжить", "Resume"),
                togglePending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                text("Стоп", "Stop"),
                stopPending
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Kapijuja Reader playback",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun text(ru: String, en: String) = UiText.get(this, ru, en)

    override fun onDestroy() {
        if (::mediaSession.isInitialized) mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SYNC = "com.kapijuja.reader.PLAYBACK_SYNC"
        const val ACTION_TOGGLE = "com.kapijuja.reader.PLAYBACK_TOGGLE"
        const val ACTION_STOP = "com.kapijuja.reader.PLAYBACK_STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PLAYING = "playing"
        private const val CHANNEL_ID = "reader_playback"
        private const val NOTIFICATION_ID = 4107
    }
}
