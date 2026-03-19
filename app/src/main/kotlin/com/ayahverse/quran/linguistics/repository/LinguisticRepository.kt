package com.ayahverse.quran.linguistics.repository

import com.ayahverse.quran.linguistics.model.WordAnalysis
import com.ayahverse.quran.linguistics.util.IndexedToken

interface LinguisticRepository {
	suspend fun buildWordAnalyses(tokens: List<IndexedToken>): List<WordAnalysis>
}
