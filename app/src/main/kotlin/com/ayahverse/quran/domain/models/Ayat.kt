package com.ayahverse.quran.domain.models

data class Ayat(
	val text: String,
	val translation: String,
	val audioUrl: String? = null,
)
