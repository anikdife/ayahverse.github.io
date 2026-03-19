package com.ayahverse.quran.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * System overlay mini player: shows a draggable floating icon above other apps.
 * Requires the user to grant "Draw over other apps" permission.
 */
class MiniListenOverlayService : Service() {
	companion object {
		private const val CHANNEL_ID = "mini_listen_overlay"
		private const val CHANNEL_NAME = "Mini Player"
		private const val NOTIFICATION_ID = 9011

		const val ACTION_SHOW = "com.ayahverse.quran.playback.MINI_OVERLAY_SHOW"
		const val ACTION_HIDE = "com.ayahverse.quran.playback.MINI_OVERLAY_HIDE"
	}

	private var windowManager: WindowManager? = null
	private var overlayView: View? = null
	private var overlayParams: WindowManager.LayoutParams? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_HIDE -> {
				removeOverlay()
				stopForeground(STOP_FOREGROUND_REMOVE)
				stopSelf()
				return START_NOT_STICKY
			}
			ACTION_SHOW, null -> {
				if (!Settings.canDrawOverlays(this)) {
					// Can't show without permission.
					stopSelf()
					return START_NOT_STICKY
				}
				ensureChannel()
				ServiceCompat.startForeground(
					this,
					NOTIFICATION_ID,
					buildNotification(),
					ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
				)
				showOverlay()
				return START_STICKY
			}
			else -> return START_STICKY
		}
	}

	override fun onDestroy() {
		removeOverlay()
		super.onDestroy()
	}

	private fun ensureChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		val existing = mgr.getNotificationChannel(CHANNEL_ID)
		if (existing != null) return
		mgr.createNotificationChannel(
			NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
				description = "Shows a floating mini player while listening."
				setShowBadge(false)
				lockscreenVisibility = Notification.VISIBILITY_PUBLIC
			},
		)
	}

	private fun buildNotification(): Notification {
		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.ic_media_play)
			.setContentTitle("Quran")
			.setContentText("Listening")
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build()
	}

	private fun showOverlay() {
		if (overlayView != null) return
		val wm = (getSystemService(WINDOW_SERVICE) as? WindowManager) ?: return
		windowManager = wm

		val icon = ImageView(this).apply {
			contentDescription = "Mini player"
			isClickable = true
			isFocusable = false
			scaleType = ImageView.ScaleType.FIT_CENTER
			val bmp = runCatching {
				val candidates = listOf("global_menu_logo.png", "logo.png")
				for (name in candidates) {
					val b = runCatching {
						assets.open(name).use { input ->
							BitmapFactory.decodeStream(input)
						}
					}.getOrNull()
					if (b != null) return@runCatching b
				}
				null
			}.getOrNull()
			if (bmp != null) {
				setImageBitmap(bmp)
				clearColorFilter()
			} else {
				// Fallback to app launcher icon.
				runCatching { setImageDrawable(packageManager.getApplicationIcon(packageName)) }
			}
			background = GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(
					Color.argb(210, 15, 18, 26),
					Color.argb(210, 8, 10, 16),
				),
			).apply {
				cornerRadius = (22f * resources.displayMetrics.density)
				setStroke((1f * resources.displayMetrics.density).toInt(), Color.argb(90, 255, 255, 255))
			}
			val pad = (10f * resources.displayMetrics.density).toInt()
			setPadding(pad, pad, pad, pad)
		}

		val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
		} else {
			@Suppress("DEPRECATION")
			WindowManager.LayoutParams.TYPE_PHONE
		}
		val sizePx = (68f * resources.displayMetrics.density).toInt()
		val params = WindowManager.LayoutParams(
			sizePx,
			sizePx,
			type,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
			android.graphics.PixelFormat.TRANSLUCENT,
		).apply {
			gravity = Gravity.TOP or Gravity.START
			x = (16f * resources.displayMetrics.density).toInt()
			y = (54f * resources.displayMetrics.density).toInt()
		}
		overlayParams = params

		// Drag handling.
		icon.setOnTouchListener(object : View.OnTouchListener {
			private var downRawX = 0f
			private var downRawY = 0f
			private var startX = 0
			private var startY = 0
			override fun onTouch(v: View, event: MotionEvent): Boolean {
				val p = overlayParams ?: return false
				return when (event.actionMasked) {
					MotionEvent.ACTION_DOWN -> {
						downRawX = event.rawX
						downRawY = event.rawY
						startX = p.x
						startY = p.y
						true
					}
					MotionEvent.ACTION_MOVE -> {
						val dx = (event.rawX - downRawX).toInt()
						val dy = (event.rawY - downRawY).toInt()
						p.x = startX + dx
						p.y = startY + dy
						windowManager?.updateViewLayout(v, p)
						true
					}
					MotionEvent.ACTION_UP -> {
						val dist = kotlin.math.abs(event.rawX - downRawX) + kotlin.math.abs(event.rawY - downRawY)
						if (dist < (6f * resources.displayMetrics.density)) {
							v.performClick()
						}
						true
					}
					else -> false
				}
			}
		})

		icon.setOnClickListener {
			// Bring app to front and hide overlay.
			removeOverlay()
			stopForeground(STOP_FOREGROUND_REMOVE)
			stopSelf()
			val launch = packageManager.getLaunchIntentForPackage(packageName)
			launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
			if (launch != null) startActivity(launch)
		}

		overlayView = icon
		wm.addView(icon, params)
	}

	private fun removeOverlay() {
		val v = overlayView ?: return
		overlayView = null
		runCatching { windowManager?.removeView(v) }
		overlayParams = null
		windowManager = null
	}
}
