package com.ayahverse.quran.feature.tajweed.domain

/**
 * Best-effort harakat "vowelization" for Vosk output.
 *
 * Vosk typically returns Arabic without diacritics. For easier playback via TTS,
 * we transfer harakat from the expected ayah onto recognized tokens when the
 * base letters match (after stripping harakat/tatweel).
 *
 * This is intentionally conservative: if we can't confidently match a token,
 * we keep it unchanged.
 */
object ArabicVowelizeFromExpected {
	fun vowelize(expectedArabicWithHarakat: String, recognizedArabic: String): String {
		val expectedTokens = tokenizeArabic(expectedArabicWithHarakat)
		val recognizedTokens = tokenizeArabic(recognizedArabic)
		if (expectedTokens.isEmpty() || recognizedTokens.isEmpty()) return recognizedArabic

		val lookup = buildLookup(expectedTokens)
		val out = ArrayList<String>(recognizedTokens.size)
		for (token in recognizedTokens) {
			val key = stripHarakatAndTatweel(token)
			if (key.isBlank()) {
				out.add(token)
				continue
			}
			val queue = lookup[key]
			val replacement = if (queue != null && queue.isNotEmpty()) queue.removeFirst() else null
			out.add(replacement ?: token)
		}
		return out.joinToString(" ")
	}

	private fun buildLookup(expectedTokens: List<String>): MutableMap<String, ArrayDeque<String>> {
		val map = LinkedHashMap<String, ArrayDeque<String>>()
		for (t in expectedTokens) {
			val key = stripHarakatAndTatweel(t)
			if (key.isBlank()) continue
			val q = map.getOrPut(key) { ArrayDeque() }
			q.addLast(t)
		}
		return map
	}

	private fun tokenizeArabic(text: String): List<String> =
		text
			.trim()
			.split(Regex("\\s+"))
			.filter { it.isNotBlank() }

	/**
	 * Removes Arabic harakat + common Quranic marks and tatweel.
	 */
	fun stripHarakatAndTatweel(s: String): String {
		if (s.isBlank()) return ""
		val sb = StringBuilder(s.length)
		for (ch in s) {
			if (ch == '\u0640') continue // tatweel
			if (isArabicDiacriticOrMark(ch)) continue
			sb.append(ch)
		}
		return sb.toString()
	}

	private fun isArabicDiacriticOrMark(ch: Char): Boolean {
		val code = ch.code
		// Harakat / tashkeel
		if (code in 0x064B..0x065F) return true
		// Superscript Alef (often seen in Quran text, e.g. الرَّحْمَٰن)
		if (code == 0x0670) return true
		// Quranic annotation marks (conservative range)
		if (code in 0x06D6..0x06ED) return true
		return false
	}
}
