package com.ayahverse.quran.data.remote.service

import com.ayahverse.quran.data.remote.dto.qurantafseer.QuranTafseerResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface QuranTafseerApiService {
	/** Example: GET tafseer/1/1/1 */
	@GET("tafseer/{tafseer_id}/{surah}/{ayah}")
	suspend fun getTafseer(
		@Path("tafseer_id") tafseerId: String,
		@Path("surah") surah: Int,
		@Path("ayah") ayah: Int,
	): Response<QuranTafseerResponseDto>
}
