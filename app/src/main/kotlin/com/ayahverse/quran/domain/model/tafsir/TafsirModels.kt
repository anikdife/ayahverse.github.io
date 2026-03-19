package com.ayahverse.quran.domain.model.tafsir

import kotlin.math.max

data class TafsirSource(
	val slug: String,
	val legacyId: Int,
	val authorName: String,
	val languageCode: String,
	val languageDisplayName: String,
	val displayName: String,
	val sourceUrl: String,
	val providerType: TafsirProviderType,
	val defaultPriority: Int,
	val isEnabled: Boolean,
)

data class TafsirDropdownOption(
	val slug: String,
	val displayName: String,
	val authorName: String,
	val languageCode: String,
	val defaultPriority: Int,
)

data class TafsirRequest(
	val surahNumber: Int,
	val ayahNumber: Int,
	val selectedTranslationLanguageCode: String,
	val selectedTafsirSlug: String,
	val forceRefresh: Boolean = false,
)

enum class TafsirContentOrigin {
	DIRECT_API,
	TRANSLATED_FALLBACK,
	CACHED_DIRECT,
	CACHED_TRANSLATED,
	MISSING,
}

data class TafsirAyahResult(
	val request: TafsirRequest,
	val source: TafsirSource?,
	val title: String,
	val authorName: String,
	val originalLanguageCode: String?,
	val finalLanguageCode: String,
	val content: String?,
	val contentOrigin: TafsirContentOrigin,
	val isTranslated: Boolean,
	val translationProvider: String?,
	val translationReason: String?,
	val sourceUrl: String?,
	val originalRawContent: String?,
	val sanitizedContent: String?,
	val isHtml: Boolean,
	val error: TafsirError?,
	val fetchedAtEpochMs: Long,
	val cacheKey: String,
) {
	val isMissing: Boolean get() = contentOrigin == TafsirContentOrigin.MISSING || content.isNullOrBlank()
	val hasContent: Boolean get() = !content.isNullOrBlank()

	fun bestContent(): String? =
		sanitizedContent?.takeIf { it.isNotBlank() }
			?: content?.takeIf { it.isNotBlank() }
			?: originalRawContent?.takeIf { it.isNotBlank() }
}

data class TafsirError(
	val type: TafsirErrorType,
	val message: String,
	val httpCode: Int? = null,
	val isRetryable: Boolean = false,
)

enum class TafsirErrorType {
	NETWORK,
	NOT_FOUND,
	EMPTY_CONTENT,
	TRANSLATION_FAILED,
	SOURCE_NOT_REGISTERED,
	SOURCE_LANGUAGE_MISMATCH,
	INVALID_REQUEST,
	UNKNOWN,
}
