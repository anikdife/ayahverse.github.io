package com.ayahverse.quran.domain.repository

import com.ayahverse.quran.domain.models.AyatReference
import com.ayahverse.quran.domain.models.Tafseer

interface TafseerRepository {
	suspend fun getTafseer(
		ayat: AyatReference,
		tafseerId: String,
	): Tafseer
}
