package com.ayahverse.quran.feature.tajweed.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ArabicVowelizeFromExpectedTest {
	@Test
	fun `vowelize copies harakat when base tokens match`() {
		val expected = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
		val recognized = "بسم الله الرحمن الرحيم"
		val out = ArabicVowelizeFromExpected.vowelize(expectedArabicWithHarakat = expected, recognizedArabic = recognized)
		assertEquals(expected, out)
	}

	@Test
	fun `vowelize keeps unknown tokens`() {
		val expected = "مَالِكِ يَوْمِ الدِّينِ"
		val recognized = "مالك يوم xyz الدين"
		val out = ArabicVowelizeFromExpected.vowelize(expectedArabicWithHarakat = expected, recognizedArabic = recognized)
		assertEquals("مَالِكِ يَوْمِ xyz الدِّينِ", out)
	}
}
