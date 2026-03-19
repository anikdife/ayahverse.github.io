package com.ayahverse.quran.data.remote.dto.qurantafseer

import com.google.gson.annotations.SerializedName

data class QuranTafseerResponseDto(
	@SerializedName("tafseer_id") val tafseerId: Int? = null,
	@SerializedName("tafseer_name") val tafseerName: String? = null,
	val ayah_url: String? = null,
	val ayah_number: Int? = null,
	val text: String,
)
