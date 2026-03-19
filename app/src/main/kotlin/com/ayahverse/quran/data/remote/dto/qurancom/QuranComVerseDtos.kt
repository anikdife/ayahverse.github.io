package com.ayahverse.quran.data.remote.dto.qurancom

import com.google.gson.annotations.SerializedName

data class QuranComUthmaniVersesResponseDto(
	val verses: List<QuranComUthmaniVerseDto> = emptyList(),
	val meta: QuranComMetaDto? = null,
)

data class QuranComUthmaniVerseDto(
	val id: Int,
	@SerializedName("verse_key") val verseKey: String,
	@SerializedName("text_uthmani") val textUthmani: String,
)

data class QuranComTranslationsResponseDto(
	val translations: List<QuranComTranslationDto> = emptyList(),
	val meta: QuranComTranslationMetaDto? = null,
)

data class QuranComTranslationDto(
	@SerializedName("resource_id") val resourceId: Int,
	val text: String,
)

data class QuranComTranslationMetaDto(
	@SerializedName("translation_name") val translationName: String? = null,
	@SerializedName("author_name") val authorName: String? = null,
	val filters: Map<String, Any?>? = null,
)

data class QuranComMetaDto(
	val filters: Map<String, Any?>? = null,
)
