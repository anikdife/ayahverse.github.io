package com.ayahverse.quran.feature.tajweed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicNormalizeAndScoreTest {
	@Test
	fun normalizeArabic_removesDiacriticsAndPunctuation() {
		val input = "الْحَمْدُ لِلَّهِ، رَبِّ الْعَالَمِينَ!"
		val norm = TajweedArabicNormalizer.normalizeArabic(input)
		assertEquals("الحمد لله رب العالمين", norm)
	}

	@Test
	fun compare_perfectMatch_scores100() {
		val expected = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"
		val recognized = "الحمد لله رب العالمين"
		val cmp = TajweedScorer.compare(expectedText = expected, recognizedText = recognized)
		assertEquals(100, cmp.score0to100)
		assertTrue(cmp.missingTokens.isEmpty())
		assertTrue(cmp.extraTokens.isEmpty())
	}

	@Test
	fun compare_missingTokens_reducesScore() {
		val expected = "الحمد لله رب العالمين"
		val recognized = "الحمد لله"
		val cmp = TajweedScorer.compare(expectedText = expected, recognizedText = recognized)
		assertTrue(cmp.score0to100 < 100)
		assertTrue(cmp.missingTokens.isNotEmpty())
	}
}
