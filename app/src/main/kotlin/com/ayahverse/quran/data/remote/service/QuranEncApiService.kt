package com.ayahverse.quran.data.remote.service

import com.ayahverse.quran.data.remote.dto.quranenc.QuranEncAyahTranslationResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface QuranEncApiService {
	/** Example: GET translation/aya/english_rwwad/1/1 */
	@GET("translation/aya/{translation_key}/{surah}/{ayah}")
	suspend fun getAyahTranslation(
		@Path("translation_key") translationKey: String,
		@Path("surah") surah: Int,
		@Path("ayah") ayah: Int,
	): Response<QuranEncAyahTranslationResponseDto>
}
