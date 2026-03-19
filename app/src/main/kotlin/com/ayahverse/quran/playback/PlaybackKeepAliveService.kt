package com.ayahverse.quran.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Minimal foreground service used to keep continuous recitation alive while the screen is locked.
 *
 * Playback still happens in the UI process (MediaPlayer in SurahWheelGlHolder), but Android will
 * deprioritize background apps when the device locks. A foreground service keeps the app in a
 * foreground importance class so auto-advance (network + next ayah) continues reliably.
 */
class PlaybackKeepAliveService : Service() {
	companion object {
		private const val CHANNEL_ID = "playback_keep_alive"
		private const val CHANNEL_NAME = "Playback"
		private const val NOTIFICATION_ID = 9001

		const val ACTION_START = "com.ayahverse.quran.playback.KEEP_ALIVE_START"
		const val ACTION_STOP = "com.ayahverse.quran.playback.KEEP_ALIVE_STOP"
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_STOP -> {
				stopForeground(STOP_FOREGROUND_REMOVE)
				stopSelf()
				return START_NOT_STICKY
			}
			ACTION_START, null -> {
				ensureChannel()
				val notification = buildNotification()
				ServiceCompat.startForeground(
					this,
					NOTIFICATION_ID,
					notification,
					ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
				)
				return START_STICKY
			}
			else -> return START_STICKY
		}
	}

	override fun onDestroy() {
		super.onDestroy()
	}

	private fun ensureChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		val existing = mgr.getNotificationChannel(CHANNEL_ID)
		if (existing != null) return
		mgr.createNotificationChannel(
			NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
				description = "Keeps recitation running while the screen is locked."
				setShowBadge(false)
				lockscreenVisibility = Notification.VISIBILITY_PUBLIC
			},
		)
	}

	private fun buildNotification(): Notification {
		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.ic_media_play)
			.setContentTitle("Quran")
			.setContentText("Reciting in background")
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build()
	}
}
