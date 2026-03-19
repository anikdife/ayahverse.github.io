package com.ayahverse.quran.domain.resolver

import com.ayahverse.quran.domain.model.tafsir.TafsirError
import com.ayahverse.quran.domain.model.tafsir.TafsirErrorType
import com.ayahverse.quran.domain.model.tafsir.TafsirRequest
import com.ayahverse.quran.domain.model.tafsir.TafsirSource

sealed class DirectPayloadStatus {
	data object Present : DirectPayloadStatus()
	data object Missing : DirectPayloadStatus()
	data class Failed(val error: TafsirError) : DirectPayloadStatus()
}

enum class TafsirResolutionStrategy {
	USE_DIRECT,
	USE_CACHED_DIRECT,
	TRANSLATE_SELECTED_SOURCE,
	RETURN_MISSING,
	FAIL,
}

class TafsirAvailabilityResolver {
	fun resolveStrategy(
		request: TafsirRequest,
		source: TafsirSource,
		directPayloadStatus: DirectPayloadStatus,
	): TafsirResolutionStrategy {
		// Validate language rule. Normally translation language == source language due to dropdown filtering.
		val targetLang = request.selectedTranslationLanguageCode.trim().lowercase()
		val sourceLang = source.languageCode.trim().lowercase()

		return when (directPayloadStatus) {
			is DirectPayloadStatus.Present -> {
				// If content exists in source language.
				if (targetLang == sourceLang) TafsirResolutionStrategy.USE_DIRECT
				else TafsirResolutionStrategy.TRANSLATE_SELECTED_SOURCE
			}
			is DirectPayloadStatus.Missing -> {
				// Conservative: do NOT translate when direct content is missing.
				TafsirResolutionStrategy.RETURN_MISSING
			}
			is DirectPayloadStatus.Failed -> TafsirResolutionStrategy.FAIL
		}
	}

	fun languageMismatchError(request: TafsirRequest, source: TafsirSource): TafsirError {
		return TafsirError(
			type = TafsirErrorType.SOURCE_LANGUAGE_MISMATCH,
			message = "Selected tafsir language (${source.languageCode}) does not match translation language (${request.selectedTranslationLanguageCode}).",
			httpCode = null,
			isRetryable = false,
		)
	}
}
