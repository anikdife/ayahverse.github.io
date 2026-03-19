package com.ayahverse.quran.domain.usecase

import com.ayahverse.quran.domain.models.AyatReference
import com.ayahverse.quran.domain.models.Tafseer
import com.ayahverse.quran.domain.repository.TafseerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class GetMultiTafseerUseCase(
	private val repository: TafseerRepository,
) {
	suspend operator fun invoke(
		ayat: AyatReference,
		tafseerIds: List<String>,
	): List<Tafseer> = coroutineScope {
		val deferred = tafseerIds.map { tafseerId ->
			async { repository.getTafseer(ayat = ayat, tafseerId = tafseerId) }
		}
		deferred.map { it.await() }
	}
}
