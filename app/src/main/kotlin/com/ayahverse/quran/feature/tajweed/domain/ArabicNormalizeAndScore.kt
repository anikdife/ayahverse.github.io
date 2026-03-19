package com.ayahverse.quran.feature.tajweed.domain

import kotlin.math.roundToInt

object TajweedArabicNormalizer {
	private val diacritics = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]")
	private val tatweel = "\u0640"
	private val punctuation = Regex("[\u060C\u061B\u061F\u066A-\u066D\u06D4\u0640\"'`~_–—.,:;!?()\\[\\]{}<>/\\\\|@#%^&*+=-]+")
	private val whitespace = Regex("\\s+")

	fun normalizeArabic(input: String): String {
		var s = input
		// Remove HTML artifacts if any (defensive).
		s = s.replace(Regex("<[^>]*>"), " ")
		// Remove diacritics/tashkeel.
		s = s.replace(diacritics, "")
		// Remove tatweel.
		s = s.replace(tatweel, "")
		// Normalize common alef/hamza forms conservatively.
		s = s
			.replace('أ', 'ا')
			.replace('إ', 'ا')
			.replace('آ', 'ا')
			.replace('ٱ', 'ا')
			.replace('ؤ', 'و')
			.replace('ئ', 'ي')
		// Normalize ya/alif maqsoora.
		s = s.replace('ى', 'ي')
		// Normalize taa marbuta.
		s = s.replace('ة', 'ه')
		// Remove punctuation.
		s = s.replace(punctuation, " ")
		// Normalize whitespace.
		s = s.trim().replace(whitespace, " ")
		return s
	}

	fun tokenize(normalizedArabic: String): List<String> {
		if (normalizedArabic.isBlank()) return emptyList()
		return normalizedArabic
			.split(' ')
			.map { it.trim() }
			.filter { it.isNotBlank() }
	}
}

object TajweedScorer {
	data class Comparison(
		val expectedTokens: List<String>,
		val recognizedTokens: List<String>,
		val matchedTokens: List<String>,
		val missingTokens: List<String>,
		val extraTokens: List<String>,
		val score0to100: Int,
		val rating: String,
		val notes: List<String>,
	)

	fun compare(expectedText: String, recognizedText: String): Comparison {
		val expectedNorm = TajweedArabicNormalizer.normalizeArabic(expectedText)
		val recogNorm = TajweedArabicNormalizer.normalizeArabic(recognizedText)
		val expected = TajweedArabicNormalizer.tokenize(expectedNorm)
		val recognized = TajweedArabicNormalizer.tokenize(recogNorm)

		if (expected.isEmpty() && recognized.isEmpty()) {
			return Comparison(
				expectedTokens = emptyList(),
				recognizedTokens = emptyList(),
				matchedTokens = emptyList(),
				missingTokens = emptyList(),
				extraTokens = emptyList(),
				score0to100 = 0,
				rating = "Needs Improvement",
				notes = listOf("No expected text loaded."),
			)
		}

		// Order-sensitive greedy alignment.
		val matched = ArrayList<String>(minOf(expected.size, recognized.size))
		val recognizedMatched = BooleanArray(recognized.size)
		var j = 0
		for (i in expected.indices) {
			val token = expected[i]
			while (j < recognized.size) {
				if (!recognizedMatched[j] && recognized[j] == token) {
					matched.add(token)
					recognizedMatched[j] = true
					j += 1
					break
				}
				j += 1
			}
		}
		val missing = expected.filterNot { matched.contains(it) }
		val extra = recognized.filterIndexed { idx, _ -> !recognizedMatched[idx] }

		val tokenAccuracy = if (expected.isEmpty()) 0f else matched.size.toFloat() / expected.size.toFloat()
		val completenessPenalty = if (expected.isEmpty()) 1f else (missing.size.toFloat() / expected.size.toFloat()).coerceIn(0f, 1f)
		val extraPenalty = if (recognized.isEmpty()) 0f else (extra.size.toFloat() / recognized.size.toFloat()).coerceIn(0f, 1f)

		// MVP: 50% token accuracy + 25% completeness + 25% extra penalty.
		val raw = (tokenAccuracy * 0.50f) + ((1f - completenessPenalty) * 0.25f) + ((1f - extraPenalty) * 0.25f)
		val score = (raw * 100f).coerceIn(0f, 100f).roundToInt()

		val rating = when {
			score >= 85 -> "Excellent"
			score >= 70 -> "Good"
			else -> "Needs Improvement"
		}

		val notes = buildList {
			if (recognized.isEmpty()) add("No speech recognized. Try speaking closer to the mic.")
			if (missing.isNotEmpty()) add("Some words were missed.")
			if (extra.isNotEmpty()) add("Extra words were detected.")
			if (score >= 70) add("Good match with the selected ayah.")
			if (score < 70) add("Try reciting more clearly and steadily.")
		}

		return Comparison(
			expectedTokens = expected,
			recognizedTokens = recognized,
			matchedTokens = matched,
			missingTokens = missing,
			extraTokens = extra,
			score0to100 = score,
			rating = rating,
			notes = notes,
		)
	}
}
