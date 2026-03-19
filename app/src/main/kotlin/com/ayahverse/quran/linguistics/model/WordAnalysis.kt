package com.ayahverse.quran.linguistics.model

data class WordAnalysis(
	val index: Int,
	val originalText: String,
	val normalizedText: String,
	val root: String? = null,
	val lemma: String? = null,
	val partOfSpeech: String? = null,
	val morphology: String? = null,
	val lexicography: String? = null,
	val translation: String? = null,
	val occurrences: Int? = null,
	val prefixes: List<String> = emptyList(),
	val suffixes: List<String> = emptyList(),
	val apiStatus: ApiStatus = ApiStatus.NOT_FETCHED,
)
