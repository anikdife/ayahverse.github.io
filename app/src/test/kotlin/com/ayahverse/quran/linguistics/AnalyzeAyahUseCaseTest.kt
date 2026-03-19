package com.ayahverse.quran.linguistics

import com.ayahverse.quran.linguistics.api.FakeLinguisticApiService
import com.ayahverse.quran.linguistics.repository.DefaultLinguisticRepository
import com.ayahverse.quran.linguistics.tokenizer.DefaultAyahWordSegmenter
import com.ayahverse.quran.linguistics.usecase.AnalyzeAyahUseCase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyzeAyahUseCaseTest {
	@Test
	fun `segments sample ayah into 4 words`() = runBlocking {
		val segmenter = DefaultAyahWordSegmenter()
		val repo = DefaultLinguisticRepository(FakeLinguisticApiService())
		val useCase = AnalyzeAyahUseCase(segmenter, repo)

		val ayah = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"
		val result = useCase.analyzeAyah(ayahText = ayah)

		assertEquals(4, result.words.size)
		assertEquals(
			listOf("الْحَمْدُ", "لِلَّهِ", "رَبِّ", "الْعَالَمِينَ"),
			result.words.map { it.originalText },
		)

		// Demo output (visible in test logs):
		println("Ayah: ${result.ayahText}")
		result.words.forEach {
			println("#${it.index}: orig='${it.originalText}' norm='${it.normalizedText}' apiStatus=${it.apiStatus}")
		}
	}
}
