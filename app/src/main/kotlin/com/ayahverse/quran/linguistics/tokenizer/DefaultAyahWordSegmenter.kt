package com.ayahverse.quran.linguistics.tokenizer

import com.ayahverse.quran.linguistics.util.ArabicTextUtils
import com.ayahverse.quran.linguistics.util.IndexedToken

/**
 * Basic segmentation: returns whole Arabic word tokens in order.
 *
 * TODO: Upgrade later to split clitics/prefixes/suffixes (e.g., وَ, فَ, بِ, لِ, الـ).
 */
class DefaultAyahWordSegmenter : AyahWordSegmenter {
	override fun segment(ayahText: String): List<IndexedToken> = ArabicTextUtils.tokenizeAyah(ayahText)
}
