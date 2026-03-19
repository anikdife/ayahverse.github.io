package com.ayahverse.quran.domain.models

data class AyatReference(
	val surahNumber: Int,
	val ayahNumber: Int,
) {
	val verseKey: String get() = "$surahNumber:$ayahNumber"
}
