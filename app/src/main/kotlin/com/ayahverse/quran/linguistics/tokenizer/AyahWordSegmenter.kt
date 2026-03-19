package com.ayahverse.quran.linguistics.tokenizer

import com.ayahverse.quran.linguistics.util.IndexedToken

interface AyahWordSegmenter {
	fun segment(ayahText: String): List<IndexedToken>
}
