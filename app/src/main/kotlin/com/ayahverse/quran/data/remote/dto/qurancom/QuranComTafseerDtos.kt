package com.ayahverse.quran.data.remote.dto.qurancom

import com.google.gson.annotations.SerializedName

data class QuranComTafseerByAyahResponseDto(
	val tafsir: QuranComTafseerDto,
)

/** Response for Quran.com: GET quran/tafsirs/{id}?ayah_key=1:1 */
data class QuranComTafsirsResponseDto(
	val tafsirs: List<QuranComTafsirItemDto> = emptyList(),
	val meta: QuranComMetaDto? = null,
)

data class QuranComTafsirItemDto(
	val id: Int? = null,
	@SerializedName("resource_id") val resourceId: Int? = null,
	@SerializedName("verse_id") val verseId: Int? = null,
	@SerializedName("verse_key") val verseKey: String? = null,
	val text: String,
)

data class QuranComTafseerDto(
	val verses: Map<String, QuranComVerseRefDto>? = null,
	@SerializedName("resource_id") val resourceId: Int,
	@SerializedName("resource_name") val resourceName: String,
	@SerializedName("language_id") val languageId: Int,
	val slug: String,
	@SerializedName("translated_name") val translatedName: QuranComTranslatedNameDto,
	val text: String,
)

data class QuranComVerseRefDto(
	val id: Int,
)

data class QuranComTranslatedNameDto(
	val name: String,
	@SerializedName("language_name") val languageName: String,
)

internal fun QuranComTafsirsResponseDto.findTextForResource(resourceId: Int): String? {
	return tafsirs
		.firstOrNull { it.resourceId == resourceId }
		?.text
		?.takeIf { it.isNotBlank() }
}
