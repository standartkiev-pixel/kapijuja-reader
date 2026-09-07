package com.kapijuja.reader

import android.content.Context
import android.content.Intent
import android.os.Build

object PlaybackBridge {
    interface Controller {
        fun toggleFromNotification()
        fun stopFromNotification()
    }

    @Volatile
    var controller: Controller? = null

    fun sync(context: Context, title: String, playing: Boolean) {
        val intent = Intent(context, ReaderPlaybackService::class.java).apply {
            action = ReaderPlaybackService.ACTION_SYNC
            putExtra(ReaderPlaybackService.EXTRA_TITLE, title)
            putExtra(ReaderPlaybackService.EXTRA_PLAYING, playing)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, ReaderPlaybackService::class.java))
    }
}
