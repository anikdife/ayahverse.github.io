package com.ayahverse.quran.feature.tajweed.domain

enum class TajweedStatus {
	IDLE,
	MODEL_LOADING,
	READY,
	PLAYING_REFERENCE,
	RECORDING,
	ANALYZING,
	COMPLETED,
	ERROR,
}

data class TajweedPracticeSelection(
	val surahNumber: Int,
	val ayahNumber: Int,
	val reciterId: Int,
)

data class TajweedResult(
	val overallScore: Int,
	val rating: String,
	val expectedText: String,
	val recognizedText: String,
	val matchedWordCount: Int,
	val totalWordCount: Int,
	val missingWords: List<String>,
	val extraWords: List<String>,
	val notes: List<String>,
)

data class TajweedUiState(
	val status: TajweedStatus = TajweedStatus.IDLE,
	val isLoadingModel: Boolean = false,
	val isReady: Boolean = false,
	val selectedSurahNumber: Int = 1,
	val selectedAyahNumber: Int = 1,
	val selectedReciterId: Int = -1,
	val isPlayingReference: Boolean = false,
	val isRecording: Boolean = false,
	val isAnalyzing: Boolean = false,
	val expectedAyahText: String = "",
	val recognizedText: String = "",
	val result: TajweedResult? = null,
	val errorMessage: String? = null,
)

data class VoskRecognitionResult(
	val text: String,
	val rawJson: String,
)
