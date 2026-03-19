package com.ayahverse.quran.gl

data class SurahLabel(
	val index: Int,
	val nameArabic: String,
	val baseCenter: FloatArray,
	val driftAxis: FloatArray,
	val driftAmplitude: Float,
	val driftPhase: Float,
	val baseSize: Float,
)
