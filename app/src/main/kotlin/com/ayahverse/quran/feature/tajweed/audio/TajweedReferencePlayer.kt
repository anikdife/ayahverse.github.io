package com.ayahverse.quran.feature.tajweed.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager

class TajweedReferencePlayer(private val context: Context) {
	private var mediaPlayer: MediaPlayer? = null

	fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

	fun stop() {
		val mp = mediaPlayer
		mediaPlayer = null
		try {
			mp?.stop()
		} catch (_: Throwable) {
		}
		try {
			mp?.release()
		} catch (_: Throwable) {
		}
	}

	fun play(audioUrl: String, onStarted: () -> Unit, onCompleted: () -> Unit, onError: (String) -> Unit) {
		stop()
		try {
			val mp = MediaPlayer().apply {
				setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(AudioAttributes.USAGE_MEDIA)
						.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
						.build(),
				)
				setDataSource(audioUrl)
				setOnPreparedListener {
					try {
						start()
						onStarted()
					} catch (t: Throwable) {
						onError(t.message ?: "Failed to start playback")
					}
				}
				setOnCompletionListener {
					onCompleted()
				}
				setOnErrorListener { _, what, extra ->
					onError("Playback error ($what/$extra)")
					true
				}
				prepareAsync()
			}
			mediaPlayer = mp
		} catch (t: Throwable) {
			onError(t.message ?: "Failed to play reference")
		}
	}
}
