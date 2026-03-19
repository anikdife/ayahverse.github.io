package com.ayahverse.quran.feature.tajweed.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TajweedRecognizedSpeaker(
	private val appContext: Context,
) {
	private var tts: TextToSpeech? = null
	private val isInitInProgress = AtomicBoolean(false)
	private var isReady: Boolean = false

	fun speak(text: String, onError: (String) -> Unit) {
		val trimmed = text.trim()
		if (trimmed.isBlank()) {
			onError("Nothing to play")
			return
		}

		ensureInit { ok, message ->
			if (!ok) {
				onError(message ?: "Text-to-Speech unavailable")
				return@ensureInit
			}
			try {
				tts?.stop()
				tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, "tajweed-recognized")
			} catch (t: Throwable) {
				onError(t.message ?: "Failed to play recognized text")
			}
		}
	}

	fun stop() {
		try {
			tts?.stop()
		} catch (_: Throwable) {
		}
	}

	fun shutdown() {
		try {
			tts?.stop()
		} catch (_: Throwable) {
		}
		try {
			tts?.shutdown()
		} catch (_: Throwable) {
		}
		tts = null
		isReady = false
		isInitInProgress.set(false)
	}

	private fun ensureInit(onReady: (ok: Boolean, message: String?) -> Unit) {
		if (isReady && tts != null) {
			onReady(true, null)
			return
		}
		if (!isInitInProgress.compareAndSet(false, true)) {
			onReady(isReady, if (isReady) null else "Text-to-Speech is starting")
			return
		}

		try {
			tts = TextToSpeech(appContext.applicationContext) { status ->
				isInitInProgress.set(false)
				if (status != TextToSpeech.SUCCESS) {
					isReady = false
					onReady(false, "Text-to-Speech init failed")
					return@TextToSpeech
				}

				val engine = tts ?: run {
					isReady = false
					onReady(false, "Text-to-Speech unavailable")
					return@TextToSpeech
				}

				// Prefer Arabic voice if available; fall back to default.
				runCatching {
					val ar = Locale("ar")
					val res = engine.setLanguage(ar)
					if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
						engine.setLanguage(Locale.getDefault())
					}
				}

				isReady = true
				onReady(true, null)
			}
		} catch (t: Throwable) {
			isInitInProgress.set(false)
			isReady = false
			onReady(false, t.message ?: "Text-to-Speech init failed")
		}
	}
}
