package com.ayahverse.quran.domain.repository

import com.ayahverse.quran.domain.model.tafsir.TafsirAyahResult
import com.ayahverse.quran.domain.model.tafsir.TafsirDropdownOption
import com.ayahverse.quran.domain.model.tafsir.TafsirRequest

interface TafsirRepository {
	suspend fun getTafsirSourcesForLanguage(languageCode: String): List<TafsirDropdownOption>
	suspend fun getTafsirAyah(request: TafsirRequest): TafsirAyahResult

	/**
	 * Fetch a default language-specific tafsir for a given ayah.
	 *
	 * This is used by non-quran.com providers (AlQuranCloud, QuranEnc, FawazAhmed editions).
	 */
	suspend fun fetchTafsir(languageCode: String, surah: Int, ayah: Int): String
}
