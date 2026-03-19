package com.ayahverse.quran.feature.tajweed.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayahverse.quran.feature.tajweed.audio.TajweedRecognizedSpeaker
import com.ayahverse.quran.feature.tajweed.audio.TajweedRecorder
import com.ayahverse.quran.feature.tajweed.audio.TajweedReferencePlayer
import com.ayahverse.quran.feature.tajweed.domain.ArabicVowelizeFromExpected
import com.ayahverse.quran.feature.tajweed.domain.TajweedResult
import com.ayahverse.quran.feature.tajweed.domain.TajweedScorer
import com.ayahverse.quran.feature.tajweed.domain.TajweedStatus
import com.ayahverse.quran.feature.tajweed.domain.TajweedUiState
import com.ayahverse.quran.feature.tajweed.vosk.TajweedVoskManager
import com.ayahverse.quran.feature.tajweed.vosk.VoskModelInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class TajweedViewModel(app: Application) : AndroidViewModel(app) {
	companion object {
		private const val TAG = "Tajweed"
		private const val MIN_PCM_BYTES = 8_000 // ~0.25s at 16kHz mono 16-bit
	}

	private val referencePlayer = TajweedReferencePlayer(app)
	private val recorder = TajweedRecorder()
	private val vosk = TajweedVoskManager(app)
	private val recognizedSpeaker = TajweedRecognizedSpeaker(app)

	private val _uiState = MutableStateFlow(TajweedUiState())
	val uiState: StateFlow<TajweedUiState> = _uiState

	private var recordJob: Job? = null
	private var lastPcmFile: File? = null

	init {
		// Kick off model download/install once if it's missing.
		VoskModelInstaller.ensureInstalled(getApplication())
		viewModelScope.launch {
			VoskModelInstaller.state.collect { st ->
				when (st) {
					VoskModelInstaller.State.Ready -> {
						_uiState.update { cur ->
							cur.copy(
								isLoadingModel = false,
								isReady = true,
								status = if (cur.status == TajweedStatus.IDLE || cur.status == TajweedStatus.MODEL_LOADING) TajweedStatus.READY else cur.status,
								errorMessage = null,
							)
						}
					}
					VoskModelInstaller.State.Downloading, VoskModelInstaller.State.Installing -> {
						_uiState.update { cur ->
							cur.copy(
								isLoadingModel = true,
								isReady = false,
								status = if (cur.status == TajweedStatus.IDLE || cur.status == TajweedStatus.READY) TajweedStatus.MODEL_LOADING else cur.status,
								errorMessage = null,
							)
						}
					}
					is VoskModelInstaller.State.Error -> {
						_uiState.update { cur ->
							cur.copy(
								isLoadingModel = false,
								isReady = false,
								status = TajweedStatus.ERROR,
								errorMessage = st.message,
							)
						}
					}
					VoskModelInstaller.State.Idle -> {
						_uiState.update { cur ->
							cur.copy(
								isLoadingModel = false,
								isReady = VoskModelInstaller.isModelPresent(getApplication()),
								status = if (VoskModelInstaller.isModelPresent(getApplication())) TajweedStatus.READY else TajweedStatus.MODEL_LOADING,
							)
						}
						if (!VoskModelInstaller.isModelPresent(getApplication())) {
							VoskModelInstaller.ensureInstalled(getApplication())
						}
					}
				}
			}
		}
	}

	fun setSelection(surahNumber: Int, ayahNumber: Int, reciterId: Int) {
		_uiState.update { cur ->
			cur.copy(
				selectedSurahNumber = surahNumber.coerceIn(1, 114),
				selectedAyahNumber = ayahNumber.coerceAtLeast(1),
				selectedReciterId = reciterId,
				status = if (cur.isLoadingModel || !cur.isReady) TajweedStatus.MODEL_LOADING else TajweedStatus.READY,
				isRecording = false,
				isAnalyzing = false,
				recognizedText = "",
				result = null,
				errorMessage = null,
			)
		}
	}

	fun setExpectedAyahText(text: String) {
		_uiState.update { it.copy(expectedAyahText = text, result = null, errorMessage = null) }
	}

	fun playReference(audioUrl: String) {
		_uiState.update {
			it.copy(
				status = TajweedStatus.PLAYING_REFERENCE,
				isPlayingReference = true,
				errorMessage = null,
			)
		}
		referencePlayer.play(
			audioUrl = audioUrl,
			onStarted = {
				_uiState.update { it.copy(status = TajweedStatus.PLAYING_REFERENCE, isPlayingReference = true) }
			},
			onCompleted = {
				_uiState.update { it.copy(status = TajweedStatus.READY, isPlayingReference = false) }
			},
			onError = { msg ->
				_uiState.update { it.copy(status = TajweedStatus.ERROR, isPlayingReference = false, errorMessage = msg) }
			},
		)
	}

	fun stopReference() {
		referencePlayer.stop()
		_uiState.update { it.copy(status = TajweedStatus.READY, isPlayingReference = false) }
	}

	fun playRecognized() {
		val text = _uiState.value.recognizedText
		recognizedSpeaker.speak(text) { msg ->
			_uiState.update { it.copy(status = TajweedStatus.ERROR, errorMessage = msg) }
		}
	}

	fun startRecording() {
		if (_uiState.value.isLoadingModel || !_uiState.value.isReady) {
			_uiState.update { it.copy(status = TajweedStatus.MODEL_LOADING, errorMessage = null) }
			VoskModelInstaller.ensureInstalled(getApplication())
			return
		}
		if (recorder.isRecording()) return
		referencePlayer.stop()
		_uiState.update {
			it.copy(
				status = TajweedStatus.RECORDING,
				isRecording = true,
				isAnalyzing = false,
				result = null,
				errorMessage = null,
			)
		}

		val out = File(getApplication<Application>().cacheDir, "tajweed_recording_${System.currentTimeMillis()}.pcm")
		lastPcmFile = out
		recordJob?.cancel()
		recordJob = viewModelScope.launch {
			try {
				recorder.start(out)
			} catch (_: CancellationException) {
				// Expected when the ViewModel is cleared or a job is cancelled.
				// Never show this as a user-facing error.
			} catch (t: Throwable) {
				_uiState.update { it.copy(status = TajweedStatus.ERROR, isRecording = false, errorMessage = t.message ?: "Recording failed") }
			}
		}.also { job ->
			job.invokeOnCompletion {
				if (recordJob === job) recordJob = null
			}
		}
	}

	fun stopRecordingAndRecognize() {
		if (_uiState.value.isLoadingModel || !_uiState.value.isReady) {
			_uiState.update { it.copy(status = TajweedStatus.MODEL_LOADING, errorMessage = null) }
			VoskModelInstaller.ensureInstalled(getApplication())
			return
		}
		if (!recorder.isRecording()) return
		val out = recorder.stop()
		val jobToJoin = recordJob
		recordJob = null

		val expectedText = _uiState.value.expectedAyahText
		if (out == null) {
			_uiState.update { it.copy(status = TajweedStatus.ERROR, isRecording = false, errorMessage = "No recording captured") }
			return
		}

		viewModelScope.launch {
			try {
				// Ensure the recorder writer coroutine has flushed/closed the file before we read it.
				jobToJoin?.join()
			} catch (_: CancellationException) {
				return@launch
			}

			val bytes = runCatching { out.length() }.getOrDefault(0L)
			Log.d(TAG, "PCM captured: ${bytes} bytes at ${out.absolutePath}")
			if (bytes < MIN_PCM_BYTES) {
				_uiState.update {
					it.copy(
						status = TajweedStatus.ERROR,
						isRecording = false,
						isAnalyzing = false,
						errorMessage = "Recording too short. Hold Record and recite clearly, then Stop.",
					)
				}
				return@launch
			}

			_uiState.update { it.copy(status = TajweedStatus.ANALYZING, isRecording = false, isAnalyzing = true, errorMessage = null) }
			when (val init = vosk.initIfNeeded()) {
				is TajweedVoskManager.InitResult.MissingModel -> {
					// If we're missing the model, kick off background install and show loading.
					VoskModelInstaller.ensureInstalled(getApplication())
					_uiState.update { it.copy(status = TajweedStatus.MODEL_LOADING, isAnalyzing = false, isLoadingModel = true, errorMessage = null) }
					return@launch
				}
				is TajweedVoskManager.InitResult.Error -> {
					_uiState.update { it.copy(status = TajweedStatus.ERROR, isAnalyzing = false, errorMessage = init.message) }
					return@launch
				}
				TajweedVoskManager.InitResult.Ok -> Unit
			}

			try {
				// small delay to avoid edge cases right after AudioRecord stop
				delay(100)
				val recognizedText = vosk.recognizePcmFile(out, TajweedRecorder.SAMPLE_RATE_HZ.toFloat())
				Log.d(TAG, "Recognized text length=${recognizedText.length}")
				if (recognizedText.isBlank()) {
					_uiState.update {
						it.copy(
							status = TajweedStatus.ERROR,
							isAnalyzing = false,
							errorMessage = "No speech detected. Try speaking closer to the mic and recite a bit longer.",
						)
					}
					return@launch
				}
				val vowelizedForPlayback = runCatching {
					ArabicVowelizeFromExpected.vowelize(
						expectedArabicWithHarakat = expectedText,
						recognizedArabic = recognizedText,
					)
				}.getOrDefault(recognizedText)

				val result = score(expectedText = expectedText, recognizedText = recognizedText)
				_uiState.update {
					it.copy(
						status = TajweedStatus.COMPLETED,
						isAnalyzing = false,
						recognizedText = vowelizedForPlayback,
						result = result,
					)
				}
			} catch (_: CancellationException) {
				// ViewModel cleared / screen dismissed while analyzing.
				return@launch
			} catch (t: Throwable) {
				_uiState.update { it.copy(status = TajweedStatus.ERROR, isAnalyzing = false, errorMessage = t.message ?: "Recognition failed") }
			}
		}
	}

	private fun score(expectedText: String, recognizedText: String): TajweedResult {
		val cmp = TajweedScorer.compare(expectedText = expectedText, recognizedText = recognizedText)
		return TajweedResult(
			overallScore = cmp.score0to100,
			rating = cmp.rating,
			expectedText = expectedText,
			recognizedText = recognizedText,
			matchedWordCount = cmp.matchedTokens.size,
			totalWordCount = cmp.expectedTokens.size,
			missingWords = cmp.missingTokens,
			extraWords = cmp.extraTokens,
			notes = cmp.notes,
		)
	}

	override fun onCleared() {
		super.onCleared()
		try {
			referencePlayer.stop()
		} catch (_: Throwable) {
		}
		try {
			recorder.stop()
		} catch (_: Throwable) {
		}
		try {
			recognizedSpeaker.shutdown()
		} catch (_: Throwable) {
		}
	}
}
