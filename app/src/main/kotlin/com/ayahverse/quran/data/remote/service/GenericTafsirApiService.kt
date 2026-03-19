package com.ayahverse.quran.data.remote.service

import com.ayahverse.quran.data.remote.dto.tafsir.TafsirResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Generic Retrofit service for tafsir endpoints with differing JSON shapes.
 *
 * Uses a unified [TafsirResponse] model with optional fields.
 */
interface GenericTafsirApiService {
	@GET
	suspend fun fetch(@Url url: String): Response<TafsirResponse>
}
