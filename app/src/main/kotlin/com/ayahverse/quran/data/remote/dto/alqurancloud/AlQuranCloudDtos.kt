package com.ayahverse.quran.data.remote.dto.alqurancloud

import com.google.gson.annotations.SerializedName

/**
 * DTOs for https://api.alquran.cloud/v1/
 */

data class AlQuranCloudAyahResponseDto(
	val code: Int,
	val status: String,
	val data: AlQuranCloudAyahDto,
)

data class AlQuranCloudAyahEditionsResponseDto(
	val code: Int,
	val status: String,
	val data: List<AlQuranCloudAyahDto> = emptyList(),
)

data class AlQuranCloudAyahDto(
	val number: Int,
	val numberInSurah: Int,
	val surah: AlQuranCloudSurahDto,
	val text: String,
	val edition: AlQuranCloudEditionDto,
	val audio: String? = null,
)

data class AlQuranCloudSurahDto(
	val number: Int,
	val name: String,
	@SerializedName("englishName") val englishName: String,
	@SerializedName("englishNameTranslation") val englishNameTranslation: String,
	val numberOfAyahs: Int,
)

data class AlQuranCloudEditionDto(
	val identifier: String,
	val language: String,
	val name: String,
	val englishName: String,
	val format: String,
	val type: String,
	val direction: String? = null,
)
