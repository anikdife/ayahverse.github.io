package com.ayahverse.quran.linguistics.usecase

import com.ayahverse.quran.linguistics.model.AyahLinguisticAnalysis
import com.ayahverse.quran.linguistics.repository.LinguisticRepository
import com.ayahverse.quran.linguistics.tokenizer.AyahWordSegmenter

/**
 * Use case: segment an ayah and prepare word-level linguistic analysis objects.
 *
 * Coroutine-friendly and ready for later async API enrichment.
 */
class AnalyzeAyahUseCase(
	private val segmenter: AyahWordSegmenter,
	private val repository: LinguisticRepository,
) {
	suspend fun analyzeAyah(
		ayahText: String,
		surahNumber: Int? = null,
		ayahNumber: Int? = null,
	): AyahLinguisticAnalysis {
		val tokens = segmenter.segment(ayahText)
		val analyses = repository.buildWordAnalyses(tokens)
		return AyahLinguisticAnalysis(
			surahNumber = surahNumber,
			ayahNumber = ayahNumber,
			ayahText = ayahText,
			words = analyses,
		)
	}
}
