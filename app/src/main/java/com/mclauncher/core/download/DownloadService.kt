package com.mclauncher.core.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mclauncher.MainActivity
import com.mclauncher.MCLauncherApp
import com.mclauncher.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    private val binder = DownloadBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var isDownloading = false

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("DownloadService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                startForeground(NOTIFICATION_ID, createNotification("Preparing download...", 0))
                startDownloads()
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelDownloads()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownloads() {
        if (isDownloading) {
            Timber.w("Downloads already in progress")
            return
        }

        isDownloading = true

        serviceScope.launch {
            downloadManager.startDownloads().collect { progress ->
                updateNotification(progress)

                if (progress.isComplete) {
                    isDownloading = false
                    showCompletionNotification()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                } else if (progress.error != null) {
                    isDownloading = false
                    showErrorNotification(progress.error)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    private fun cancelDownloads() {
        downloadManager.cancelAllDownloads()
        isDownloading = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(progress: DownloadProgress) {
        val notification = createNotification(
            "Downloading: ${progress.completed}/${progress.total} files",
            (progress.progress * 100).toInt()
        )
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MCLauncherApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("MC Launcher")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()
    }

    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(this, MCLauncherApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("All files have been downloaded successfully")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun showErrorNotification(error: String) {
        val notification = NotificationCompat.Builder(this, MCLauncherApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.d("DownloadService destroyed")
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.mclauncher.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.mclauncher.action.CANCEL_DOWNLOAD"
        const val ACTION_STOP = "com.mclauncher.action.STOP"

        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_COMPLETE = 1002
        private const val NOTIFICATION_ID_ERROR = 1003
    }
}
