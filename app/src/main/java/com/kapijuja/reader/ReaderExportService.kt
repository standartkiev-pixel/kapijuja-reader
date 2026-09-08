package com.kapijuja.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class ReaderExportService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentFormat = "AUDIO"
    private var currentPercent = 0
    private var cancelling = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentFormat =
                    intent.getStringExtra(EXTRA_FORMAT)
                        ?.takeIf { it.isNotBlank() }
                        ?: "AUDIO"
                currentPercent =
                    intent.getIntExtra(EXTRA_PERCENT, 0)
                        .coerceIn(0, 100)
                cancelling = false
                acquireWakeLock()
                publish()
            }

            ACTION_PROGRESS -> {
                currentFormat =
                    intent.getStringExtra(EXTRA_FORMAT)
                        ?.takeIf { it.isNotBlank() }
                        ?: currentFormat
                currentPercent =
                    intent.getIntExtra(EXTRA_PERCENT, currentPercent)
                        .coerceIn(0, 100)
                publish()
            }

            ACTION_CANCEL -> {
                cancelling = true
                ExportBridge.controller?.cancelExportFromNotification()
                publish()
            }

            ACTION_STOP -> stopExportService()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "KapijujaReader:AudioExport"
                )
                .apply {
                    setReferenceCounted(false)
                    // Safety timeout: no Reader export should legitimately need six hours.
                    acquire(6L * 60L * 60L * 1000L)
                }

        AppDiagnostics.info(this, "Audio export wake lock acquired")
    }

    private fun releaseWakeLock() {
        try {
            wakeLock
                ?.takeIf { it.isHeld }
                ?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
        AppDiagnostics.info(this, "Audio export wake lock released")
    }

    private fun publish() {
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE

        val cancelPending =
            PendingIntent.getService(
                this,
                21,
                Intent(this, ReaderExportService::class.java).apply {
                    action = ACTION_CANCEL
                },
                flags
            )

        val contentPending =
            packageManager
                .getLaunchIntentForPackage(packageName)
                ?.let {
                    PendingIntent.getActivity(
                        this,
                        22,
                        it,
                        flags
                    )
                }

        val title =
            if (cancelling) {
                text(
                    "Отмена экспорта…",
                    "Anulowanie eksportu…",
                    "Cancelling export…"
                )
            } else {
                text(
                    "Создание $currentFormat",
                    "Tworzenie $currentFormat",
                    "Creating $currentFormat"
                )
            }

        val body =
            if (cancelling) {
                text(
                    "Останавливаем текущую операцию",
                    "Zatrzymywanie bieżącej operacji",
                    "Stopping the current operation"
                )
            } else {
                text(
                    "Kapijuja Reader продолжает работу с выключенным экраном • $currentPercent%",
                    "Kapijuja Reader pracuje dalej przy wyłączonym ekranie • $currentPercent%",
                    "Kapijuja Reader keeps working with the screen off • $currentPercent%"
                )
            }

        val builder =
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(body)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentPending)
                .setProgress(
                    100,
                    currentPercent,
                    cancelling
                )

        if (!cancelling) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                text(
                    "Отменить",
                    "Anuluj",
                    "Cancel"
                ),
                cancelPending
            )
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun stopExportService() {
        releaseWakeLock()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Kapijuja Reader export",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
    }

    private fun text(
        ru: String,
        pl: String,
        en: String
    ) =
        UiText.get(
            this,
            ru,
            pl,
            en
        )

    companion object {
        const val ACTION_START =
            "com.kapijuja.reader.EXPORT_START"
        const val ACTION_PROGRESS =
            "com.kapijuja.reader.EXPORT_PROGRESS"
        const val ACTION_CANCEL =
            "com.kapijuja.reader.EXPORT_CANCEL"
        const val ACTION_STOP =
            "com.kapijuja.reader.EXPORT_STOP"

        const val EXTRA_FORMAT = "format"
        const val EXTRA_PERCENT = "percent"

        private const val CHANNEL_ID =
            "reader_audio_export"
        private const val NOTIFICATION_ID =
            4111
    }
}
