package com.ayahverse.quran.linguistics.api

/** Placeholder implementation until a real free API is wired. */
class FakeLinguisticApiService : LinguisticApiService {
	override suspend fun fetchWordDetails(word: String): WordApiResponse? {
		// TODO: Implement real networking + caching.
		return null
	}
}
