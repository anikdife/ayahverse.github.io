package com.ayahverse.quran.data.mapper

import com.ayahverse.quran.data.remote.dto.qurancom.QuranComTafseerByAyahResponseDto
import com.ayahverse.quran.data.remote.dto.qurancom.QuranComTafsirsResponseDto
import com.ayahverse.quran.data.remote.dto.qurancom.findTextForResource
import com.ayahverse.quran.domain.models.Tafseer

internal fun QuranComTafseerByAyahResponseDto.toDomain(
	id: String,
	overrideName: String?,
	source: String,
): Tafseer {
	val content = tafsir.text
	val name = overrideName ?: tafsir.resourceName
	return Tafseer(
		id = id,
		name = name,
		content = content,
		source = source,
	)
}

internal fun QuranComTafsirsResponseDto.toDomain(
	id: String,
	overrideName: String?,
	source: String,
): Tafseer {
	val requestedResourceId = id.toIntOrNull()
	val content = requestedResourceId?.let { findTextForResource(it) }
		?: tafsirs.firstOrNull()?.text
		?: throw IllegalStateException("Quran.com tafsirs response was empty")
	val name = overrideName ?: id
	return Tafseer(
		id = id,
		name = name,
		content = content,
		source = source,
	)
}
