package com.ayahverse.quran.linguistics.controller

import com.ayahverse.quran.linguistics.model.AyahLinguisticAnalysis
import com.ayahverse.quran.linguistics.usecase.AnalyzeAyahUseCase

/**
 * Controller-ready layer (can be used by a ViewModel, presenter, or any UI layer).
 * No Android UI dependencies.
 */
class AyahLinguisticsController(
	private val analyzeAyahUseCase: AnalyzeAyahUseCase,
) {
	suspend fun analyzeAyah(
		ayahText: String,
		surahNumber: Int? = null,
		ayahNumber: Int? = null,
	): AyahLinguisticAnalysis = analyzeAyahUseCase.analyzeAyah(
		ayahText = ayahText,
		surahNumber = surahNumber,
		ayahNumber = ayahNumber,
	)
}
