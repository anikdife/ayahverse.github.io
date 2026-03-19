package com.ayahverse.quran.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit endpoints for the project Worker (quran-backend).
 *
 * Default points to local wrangler dev for Android emulators.
 */
interface QuranBackendApi {
	@GET("verse-translation")
	suspend fun getVerseTranslation(
		@Query("translation_id") translationId: Int,
		@Query("verse_key") verseKey: String,
	): BackendVerseTranslationResponse

	@GET("ayah-words")
	suspend fun getAyahWords(
		@Query("verse_key") verseKey: String,
	): BackendAyahWordsResponse

	@POST("translate")
	suspend fun translateText(
		@Body body: BackendTranslateRequest,
	): BackendTranslateResponse

	companion object {
		const val BASE_URL: String = "https://quran-backend.anik-dife.workers.dev/"
	}
}

data class BackendTranslateRequest(
	val text: String,
	@SerializedName("target_lang") val targetLang: String,
	@SerializedName("source_lang") val sourceLang: String? = null,
)

data class BackendTranslateResponse(
	val text: String,
	@SerializedName("translated_text") val translatedText: String,
	@SerializedName("source_lang") val sourceLang: String? = null,
	@SerializedName("target_lang") val targetLang: String,
	val source: String? = null,
)

data class BackendVerseTranslationResponse(
	@SerializedName("verse_key") val verseKey: String,
	@SerializedName("translation_id") val translationId: Int? = null,
	val text: String,
	val source: String? = null,
)

data class BackendAyahWordsResponse(
	@SerializedName("verse_key") val verseKey: String,
	val words: List<BackendAyahWordDto> = emptyList(),
	val source: String? = null,
)

data class BackendAyahWordDto(
	val position: Int? = null,
	val text: String = "",
	@SerializedName("char_type_name") val charTypeName: String? = null,
	val transliteration: String? = null,
	val translation: String? = null,
)
