package com.ayahverse.quran.domain.usecase

import com.ayahverse.quran.domain.model.tafsir.TafsirAyahResult
import com.ayahverse.quran.domain.model.tafsir.TafsirRequest
import com.ayahverse.quran.domain.repository.TafsirRepository

class GetTafsirAyahUseCase(
	private val repository: TafsirRepository,
) {
	suspend operator fun invoke(request: TafsirRequest): TafsirAyahResult {
		return repository.getTafsirAyah(request)
	}
}
