package com.ayahverse.quran.data.remote.dto.quranenc

import com.google.gson.annotations.SerializedName

data class QuranEncAyahTranslationResponseDto(
	val result: QuranEncAyahTranslationResultDto,
)

data class QuranEncAyahTranslationResultDto(
	val id: Int? = null,
	val sura: Int? = null,
	val aya: Int? = null,
	@SerializedName("arabic_text") val arabicText: String? = null,
	val translation: String,
	val footnotes: String? = null,
)
