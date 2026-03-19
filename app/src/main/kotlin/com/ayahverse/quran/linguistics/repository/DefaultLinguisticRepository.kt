package com.ayahverse.quran.linguistics.repository

import com.ayahverse.quran.linguistics.api.LinguisticApiService
import com.ayahverse.quran.linguistics.model.ApiStatus
import com.ayahverse.quran.linguistics.model.WordAnalysis
import com.ayahverse.quran.linguistics.util.IndexedToken

/**
 * Repository that will eventually map API results into [WordAnalysis].
 *
 * Currently returns placeholder analyses, ready for API wiring later.
 */
class DefaultLinguisticRepository(
	private val apiService: LinguisticApiService,
) : LinguisticRepository {
	override suspend fun buildWordAnalyses(tokens: List<IndexedToken>): List<WordAnalysis> {
		return tokens.map { token ->
			// TODO: Wire free API call:
			// val api = apiService.fetchWordDetails(token.normalizedText)
			// mapApiToWordAnalysis(token, api)
			WordAnalysis(
				index = token.index,
				originalText = token.originalText,
				normalizedText = token.normalizedText,
				root = null,
				lemma = null,
				partOfSpeech = null,
				morphology = null,
				lexicography = null,
				translation = null,
				occurrences = null,
				prefixes = emptyList(),
				suffixes = emptyList(),
				apiStatus = ApiStatus.NOT_FETCHED,
			)
		}
	}

	@Suppress("unused")
	private fun mapApiToWordAnalysis(token: IndexedToken, api: com.ayahverse.quran.linguistics.api.WordApiResponse?): WordAnalysis {
		return if (api == null) {
			WordAnalysis(
				index = token.index,
				originalText = token.originalText,
				normalizedText = token.normalizedText,
				apiStatus = ApiStatus.FAILED,
			)
		} else {
			WordAnalysis(
				index = token.index,
				originalText = token.originalText,
				normalizedText = token.normalizedText,
				root = api.root,
				lemma = api.lemma,
				partOfSpeech = api.partOfSpeech,
				morphology = api.morphology,
				lexicography = api.lexicography,
				translation = api.translation,
				occurrences = api.occurrences,
				prefixes = api.prefixes,
				suffixes = api.suffixes,
				apiStatus = ApiStatus.SUCCESS,
			)
		}
	}
}
