package com.ayahverse.quran.feature.tajweed.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot push-to-talk PCM recorder for Vosk.
 * Records 16kHz mono 16-bit PCM into a raw .pcm file.
 */
class TajweedRecorder {
	companion object {
		const val SAMPLE_RATE_HZ = 16_000
		private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
		private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
	}

	private var audioRecord: AudioRecord? = null
	private val running = AtomicBoolean(false)
	private var outputFile: File? = null

	fun isRecording(): Boolean = running.get()

	suspend fun start(output: File) {
		stop()
		outputFile = output
		val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
		val bufSize = (minBuf.coerceAtLeast(SAMPLE_RATE_HZ / 2) * 2)
		val recorder = AudioRecord(
			MediaRecorder.AudioSource.VOICE_RECOGNITION,
			SAMPLE_RATE_HZ,
			CHANNEL_CONFIG,
			AUDIO_FORMAT,
			bufSize,
		)
		audioRecord = recorder
		running.set(true)

		withContext(Dispatchers.IO) {
			FileOutputStream(output).use { out ->
				val buffer = ByteArray(bufSize)
				recorder.startRecording()
				while (running.get()) {
					val read = recorder.read(buffer, 0, buffer.size)
					if (read > 0) out.write(buffer, 0, read)
				}
			}
		}
	}

	fun stop(): File? {
		running.set(false)
		val recorder = audioRecord
		audioRecord = null
		try {
			recorder?.stop()
		} catch (_: Throwable) {
		}
		try {
			recorder?.release()
		} catch (_: Throwable) {
		}
		return outputFile
	}
}
