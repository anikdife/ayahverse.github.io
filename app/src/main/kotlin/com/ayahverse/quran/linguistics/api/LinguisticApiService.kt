package com.ayahverse.quran.linguistics.api

/**
 * API abstraction for later integration with a free Quran linguistic source.
 *
 * Examples of future backends:
 * - Quranic Arabic Corpus (if permitted by its terms)
 * - A custom free JSON endpoint
 */
interface LinguisticApiService {
	suspend fun fetchWordDetails(word: String): WordApiResponse?
}
