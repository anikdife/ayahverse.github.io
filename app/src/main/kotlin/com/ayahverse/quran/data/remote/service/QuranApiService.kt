package com.ayahverse.quran.data.remote.service

import com.ayahverse.quran.data.remote.dto.qurancom.QuranComTranslationsResponseDto
import com.ayahverse.quran.data.remote.dto.qurancom.QuranComUthmaniVersesResponseDto
import com.ayahverse.quran.data.remote.dto.qurancom.QuranComTafseerByAyahResponseDto
import com.ayahverse.quran.data.remote.dto.qurancom.QuranComTafsirsResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Quran.com API v4
 * Base URL example: https://api.quran.com/api/v4/
 */
interface QuranApiService {
	@GET("quran/verses/uthmani")
	suspend fun getUthmaniVerseBySurahAndNumber(
		@Query("chapter_number") surahNumber: Int,
		@Query("verse_number") ayahNumber: Int,
	): Response<QuranComUthmaniVersesResponseDto>

	@GET("quran/verses/uthmani")
	suspend fun getUthmaniVerse(
		@Query("verse_key") verseKey: String,
	): Response<QuranComUthmaniVersesResponseDto>

	@GET("quran/translations/{translation_id}")
	suspend fun getTranslationByVerseKey(
		@Path("translation_id") translationId: Int,
		@Query("verse_key") verseKey: String,
	): Response<QuranComTranslationsResponseDto>

	@GET("tafsirs/{tafsir_id}/by_ayah/{ayah_id}")
	suspend fun getTafseerByAyahId(
		@Path("tafsir_id") tafsirResourceId: Int,
		@Path("ayah_id") ayahId: Int,
	): Response<QuranComTafseerByAyahResponseDto>

	/** Example: GET quran/tafsirs/169?verse_key=1:1 */
	@GET("quran/tafsirs/{tafsir_id}")
	suspend fun getTafseerByAyahKey(
		@Path("tafsir_id") tafsirId: String,
		@Query("verse_key") ayahKey: String,
	): Response<QuranComTafsirsResponseDto>
}
