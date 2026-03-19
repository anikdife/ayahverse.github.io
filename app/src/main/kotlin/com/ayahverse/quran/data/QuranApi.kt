package com.ayahverse.quran.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.Headers
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit endpoints for Quran.com API v4.
 * Base URL: https://api.quran.com/api/v4/
 */
interface QuranApi {
	/** List surahs (chapters). */
	@GET("chapters")
	suspend fun getChapters(
		@Query("language") language: String = "en",
	): ChaptersResponse

	/**
	 * Fetch the Arabic verse text (Uthmani script) for a specific verse key, e.g. "1:1".
	 */
	@GET("quran/verses/uthmani")
	suspend fun getUthmaniVerseByKey(
		@Query("verse_key") verseKey: String,
	): UthmaniVersesResponse

	/**
	 * Fetch a single verse by key with word-by-word data.
	 *
	 * Note: Quran.com sometimes blocks requests without a User-Agent header, so we set one explicitly.
	 */
	@Headers(
		"Accept: application/json",
		"User-Agent: Mozilla/5.0 (Android) quran-app",
	)
	@GET("verses/by_key/{verse_key}")
	suspend fun getVerseByKeyWithWords(
		@Path("verse_key") verseKey: String,
		@Query("words") words: Boolean = true,
	): VerseByKeyWithWordsResponse

	/**
	 * Fetch a specific tafsir resource for a given Ayah (verse) ID.
	 *
	 * Example (Ibn Kathir abridged): /tafsirs/169/by_ayah/1
	 */
	@GET("tafsirs/{tafsir_id}/by_ayah/{ayah_id}")
	suspend fun getTafseerByAyahId(
		@Path("tafsir_id") tafsirId: Int,
		@Path("ayah_id") ayahId: Int,
	): TafseerByAyahResponse

	/**
	 * Fetch the English meaning (translation) for a specific verse.
	 *
	 * Use [DEFAULT_EN_TRANSLATION_ID] (Saheeh International) unless you prefer another.
	 * Example: /quran/translations/20?verse_key=1:1
	 */
	@GET("quran/translations/{translation_id}")
	suspend fun getEnglishMeaningByVerseKey(
		@Path("translation_id") translationId: Int = DEFAULT_EN_TRANSLATION_ID,
		@Query("verse_key") verseKey: String,
	): VerseTranslationResponse

	/** List available recitations (reciters) for audio playback. */
	@GET("resources/recitations")
	suspend fun getRecitations(): RecitationsResponse

	/** List available translation resources (language + translator) for translations. */
	@GET("resources/translations")
	suspend fun getTranslations(): TranslationsResponse

	/**
	 * Fetch the audio file for a given verse key (e.g. "1:1") from a specific recitation.
	 * Example: /recitations/7/by_ayah/1:1
	 */
	@GET("recitations/{recitation_id}/by_ayah/{verse_key}")
	suspend fun getRecitationAudioByVerseKey(
		@Path("recitation_id") recitationId: Int,
		@Path("verse_key") verseKey: String,
	): RecitationByAyahResponse

	companion object {
		const val BASE_URL: String = "https://api.quran.com/api/v4/"
		const val DEFAULT_EN_TRANSLATION_ID: Int = 20
	}
}

data class VerseByKeyWithWordsResponse(
	val verse: VerseWithWordsDto,
)

data class VerseWithWordsDto(
	val id: Int,
	@SerializedName("verse_key") val verseKey: String,
	@SerializedName("juz_number") val juzNumber: Int? = null,
	val words: List<VerseWordDto> = emptyList(),
)

data class VerseWordDto(
	val id: Int,
	val position: Int,
	val text: String,
	val translation: VerseWordTextDto? = null,
	val transliteration: VerseWordTextDto? = null,
)

data class VerseWordTextDto(
	val text: String? = null,
)

data class RecitationsResponse(
	val recitations: List<RecitationResourceDto>,
)

data class RecitationResourceDto(
	val id: Int,
	@SerializedName("reciter_name") val reciterName: String,
	val style: String? = null,
	@SerializedName("translated_name") val translatedName: TranslatedNameDto? = null,
)

data class TranslatedNameDto(
	val name: String,
	@SerializedName("language_name") val languageName: String? = null,
)

data class RecitationByAyahResponse(
	@SerializedName("audio_files") val audioFiles: List<AudioFileDto>,
	val pagination: PaginationDto? = null,
)

data class TranslationsResponse(
	val translations: List<TranslationResourceDto>,
)

data class TranslationResourceDto(
	val id: Int,
	@SerializedName("language_name") val languageName: String,
	val name: String,
	@SerializedName("author_name") val authorName: String? = null,
)

data class AudioFileDto(
	@SerializedName("verse_key") val verseKey: String,
	/**
	 * Relative audio URL returned by Quran.com API, e.g. "Alafasy/mp3/001001.mp3".
	 * Use a configurable base URL to turn this into an absolute URL.
	 */
	val url: String,
)

data class PaginationDto(
	@SerializedName("per_page") val perPage: Int,
	@SerializedName("current_page") val currentPage: Int,
	@SerializedName("next_page") val nextPage: Int?,
	@SerializedName("total_pages") val totalPages: Int,
	@SerializedName("total_records") val totalRecords: Int,
)

data class ChaptersResponse(
	val chapters: List<ChapterDto>,
)

data class ChapterDto(
	val id: Int,
	@SerializedName("name_simple") val nameSimple: String,
	@SerializedName("name_arabic") val nameArabic: String,
	@SerializedName("verses_count") val versesCount: Int,
	@SerializedName("revelation_place") val revelationPlace: String? = null,
	@SerializedName("translated_name") val translatedName: ChapterTranslatedNameDto,
)

data class ChapterTranslatedNameDto(
	val name: String,
)

data class UthmaniVersesResponse(
	val verses: List<UthmaniVerseDto>,
)

data class UthmaniVerseDto(
	val id: Int,
	@SerializedName("verse_key") val verseKey: String,
	@SerializedName("text_uthmani") val textUthmani: String,
)

data class VerseTranslationResponse(
	val translations: List<VerseTranslationDto>,
	val meta: VerseTranslationMetaDto? = null,
)

data class VerseTranslationDto(
	@SerializedName("resource_id") val resourceId: Int,
	val text: String,
)

data class VerseTranslationMetaDto(
	@SerializedName("translation_name") val translationName: String? = null,
	@SerializedName("author_name") val authorName: String? = null,
)

data class TafseerByAyahResponse(
	val tafsir: TafseerDto,
)

data class TafseerDto(
	val verses: Map<String, TafseerVerseRefDto>?,
	@SerializedName("resource_id") val resourceId: Int,
	@SerializedName("resource_name") val resourceName: String,
	@SerializedName("language_id") val languageId: Int,
	val slug: String,
	@SerializedName("translated_name") val translatedName: TafseerTranslatedNameDto,
	val text: String,
)

data class TafseerVerseRefDto(
	val id: Int,
)

data class TafseerTranslatedNameDto(
	val name: String,
	@SerializedName("language_name") val languageName: String,
)
