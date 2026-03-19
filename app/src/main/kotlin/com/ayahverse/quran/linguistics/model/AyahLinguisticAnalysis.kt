package com.ayahverse.quran.linguistics.model

data class AyahLinguisticAnalysis(
	val surahNumber: Int? = null,
	val ayahNumber: Int? = null,
	val ayahText: String,
	val words: List<WordAnalysis>,
)
