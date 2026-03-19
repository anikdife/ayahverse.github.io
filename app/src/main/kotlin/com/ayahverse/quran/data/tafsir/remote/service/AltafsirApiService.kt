package com.ayahverse.quran.data.tafsir.remote.service

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Altafsir is mostly HTML pages. We fetch the page then extract the tafsir portion.
 * Base URL: https://www.altafsir.com/
 */
interface AltafsirApiService {
	@GET("Tafasir.asp")
	suspend fun getTafsirHtml(
		@Query("tMadhNo") madhNo: Int = 0,
		@Query("tTafsirNo") tafsirNo: Int,
		@Query("tSoraNo") surahNumber: Int,
		@Query("tAyahNo") ayahNumber: Int,
		@Query("tDisplay") display: String = "yes",
		@Query("UserProfile") userProfile: Int = 0,
		@Query("LanguageId") languageId: Int = 2,
	): Response<String>
}
