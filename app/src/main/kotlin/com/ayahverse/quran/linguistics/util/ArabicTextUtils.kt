package com.ayahverse.quran.linguistics.util

/**
 * Arabic/Quran text helpers.
 *
 * Notes:
 * - These utilities are intentionally conservative: they avoid over-normalizing
 *   letters that could change meaning.
 * - Tokenization currently returns whole-word tokens; it can be upgraded later
 *   to split clitics/prefixes/suffixes.
 */
object ArabicTextUtils {
	// Common Arabic diacritics (harakat) + Quranic annotation marks.
	// This is not exhaustive for all Arabic blocks, but covers typical Quran text.
	private val diacriticsRegex = Regex(
		"[\u064B-\u065F\u0670\u06D6-\u06ED\u0610-\u061A]",
	)

	// Tatweel/kashida.
	private const val tatweel: Char = '\u0640'

	// A set of punctuation/separators that should not become tokens.
	// Includes Arabic/Latin punctuation and Quran stop/annotation marks.
	private val separatorRegex = Regex(
		"[\u060C\u061B\u061F\u066A\u066B\u066C\u066D\u06D4\u06D6-\u06ED\u0610-\u061A\\p{Punct}]",
	)

	/**
	 * Normalizes Arabic text for comparison/search.
	 *
	 * - Collapses whitespace
	 * - Removes tatweel
	 * - Normalizes a few alef variants to bare alef (ا)
	 *
	 * Does NOT remove diacritics; call [removeDiacritics] first if desired.
	 */
	fun normalizeArabicText(text: String): String {
		if (text.isBlank()) return ""
		val noTatweel = buildString(text.length) {
			for (ch in text) if (ch != tatweel) append(ch)
		}

		// Normalize a few common alef forms; keep this minimal.
		val normalizedAlef = noTatweel
			.replace('إ', 'ا')
			.replace('أ', 'ا')
			.replace('آ', 'ا')
			.replace('ٱ', 'ا')

		return normalizedAlef
			.replace(Regex("\\s+"), " ")
			.trim()
	}

	/** Removes Arabic diacritics (harakat) and common Quran annotation marks. */
	fun removeDiacritics(text: String): String {
		if (text.isBlank()) return ""
		return text.replace(diacriticsRegex, "")
	}

	/**
	 * Tokenizes an ayah into Arabic word tokens (indexed, in order).
	 *
	 * Behavior:
	 * - Ignores punctuation, Quran stop marks, and tatweel
	 * - Preserves original order
	 * - Returns both original (with diacritics) and normalized tokens
	 */
	fun tokenizeAyah(ayahText: String): List<IndexedToken> {
		val normalizedSpacing = normalizeArabicText(ayahText)
		if (normalizedSpacing.isBlank()) return emptyList()

		val tokens = mutableListOf<String>()
		val current = StringBuilder()

		fun flush() {
			if (current.isNotEmpty()) {
				tokens += current.toString()
				current.setLength(0)
			}
		}

		for (ch in normalizedSpacing) {
			when {
				ch == tatweel -> Unit
				ch.isWhitespace() -> flush()
				separatorRegex.matches(ch.toString()) -> flush()
				isTokenChar(ch) -> current.append(ch)
				else -> flush()
			}
		}
		flush()

		return tokens
			.filter { it.isNotBlank() }
			.mapIndexed { idx, original ->
				val normalized = normalizeArabicText(removeDiacritics(original))
				IndexedToken(
					index = idx,
					originalText = original,
					normalizedText = normalized,
				)
			}
	}

	/**
	 * Conservative check for characters that belong to Arabic words.
	 * Includes Arabic letters and combining marks commonly present in Quran text.
	 */
	private fun isTokenChar(ch: Char): Boolean {
		val code = ch.code
		// Arabic letters.
		val isArabicLetter = code in 0x0621..0x064A || code in 0x066E..0x066F || code in 0x0671..0x06D3
		// Combining marks (diacritics + Quran marks).
		val isCombiningMark = code in 0x064B..0x065F || code == 0x0670 || code in 0x06D6..0x06ED || code in 0x0610..0x061A
		return isArabicLetter || isCombiningMark
	}
}

/** Indexed token produced by [ArabicTextUtils.tokenizeAyah]. */
data class IndexedToken(
	val index: Int,
	val originalText: String,
	val normalizedText: String,
)
