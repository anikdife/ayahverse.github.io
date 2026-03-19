package com.ayahverse.quran.data.remote.dto.tafsir

import com.google.gson.annotations.SerializedName

/**
 * Unified response model for multiple tafsir providers.
 *
 * - AlQuranCloud: { data: { text: ... } }
 * - QuranEnc: { result: { translation: ... } }
 * - FawazAhmed editions: { quran: [ { chapter, verse, text }, ... ] }
 */
data class TafsirResponse(
	val data: TafsirCloudData? = null,
	val result: TafsirEncResult? = null,
	@SerializedName("quran") val quran: List<FawazAyahDto>? = null,
) {
	fun extractDirectText(): String? {
		return data?.text?.takeIf { it.isNotBlank() }
			?: result?.translation?.takeIf { it.isNotBlank() }
	}
}

data class TafsirCloudData(
	val text: String? = null,
)

data class TafsirEncResult(
	val translation: String? = null,
)

data class FawazAyahDto(
	val chapter: Int,
	val verse: Int,
	val text: String,
)
