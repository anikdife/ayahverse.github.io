package com.ayahverse.quran.linguistics.api

data class WordApiResponse(
	val root: String? = null,
	val lemma: String? = null,
	val partOfSpeech: String? = null,
	val morphology: String? = null,
	val lexicography: String? = null,
	val translation: String? = null,
	val occurrences: Int? = null,
	val prefixes: List<String> = emptyList(),
	val suffixes: List<String> = emptyList(),
)
