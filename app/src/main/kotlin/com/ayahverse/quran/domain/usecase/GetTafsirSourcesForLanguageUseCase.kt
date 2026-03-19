package com.ayahverse.quran.domain.usecase

import com.ayahverse.quran.domain.model.tafsir.TafsirDropdownOption
import com.ayahverse.quran.domain.repository.TafsirRepository

class GetTafsirSourcesForLanguageUseCase(
	private val repository: TafsirRepository,
) {
	suspend operator fun invoke(languageCode: String): List<TafsirDropdownOption> {
		return repository.getTafsirSourcesForLanguage(languageCode)
	}
}
