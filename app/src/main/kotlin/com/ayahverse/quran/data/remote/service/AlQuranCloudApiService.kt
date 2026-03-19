package com.ayahverse.quran.data.remote.service

import com.ayahverse.quran.data.remote.dto.alqurancloud.AlQuranCloudAyahEditionsResponseDto
import com.ayahverse.quran.data.remote.dto.alqurancloud.AlQuranCloudAyahResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Al Quran Cloud API
 * Base URL example: https://api.alquran.cloud/v1/
 */
interface AlQuranCloudApiService {
	@GET("ayah/{reference}/{edition}")
	suspend fun getAyah(
		@Path("reference") reference: String,
		@Path("edition") edition: String,
	): Response<AlQuranCloudAyahResponseDto>

	@GET("ayah/{reference}/editions/{editions}")
	suspend fun getAyahEditions(
		@Path("reference") reference: String,
		@Path("editions") editionsCsv: String,
	): Response<AlQuranCloudAyahEditionsResponseDto>
}
