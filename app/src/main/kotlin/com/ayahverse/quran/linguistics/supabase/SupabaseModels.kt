package com.ayahverse.quran.linguistics.supabase

import com.google.gson.annotations.SerializedName

data class SupabaseAyahWordDto(
	@SerializedName("chapter", alternate = ["surah", "surah_number", "chapter_number"])
	val chapter: Int? = null,
	@SerializedName("verse", alternate = ["ayah", "ayah_number", "verse_number"])
	val verse: Int? = null,
	@SerializedName("wordNumber", alternate = ["word_number", "wordIndex", "word_index", "position"])
	val wordNumber: Int? = null,
	@SerializedName("arabic", alternate = ["arabic_text", "word", "text", "uthmani"])
	val arabic: String? = null,
	@SerializedName(
		"meaning",
		alternate = [
			"translation",
			"gloss",
			"meaning_en",
			"meaning_english",
			"english_meaning",
			"translation_en",
			"english_translation",
		],
	)
	val meaning: String? = null,
	@SerializedName(
		"pronunciation",
		alternate = [
			"transliteration",
			"translit",
			"pronunciation_en",
			"transliteration_en",
			"latin",
		],
	)
	val pronunciation: String? = null,
	@SerializedName(
		"arabicGrammar",
		alternate = [
			"arabic_grammar",
			"grammar",
			"grammar_ar",
			"pos",
			"part_of_speech",
			"tag",
		],
	)
	val arabicGrammar: String? = null,
	@SerializedName(
		"segSky",
		alternate = [
			"seg_sky",
			"segment_sky",
			"segments_sky",
			"sky",
			"seg1",
		],
	)
	val segSky: String? = null,
	@SerializedName(
		"segRust",
		alternate = [
			"seg_rust",
			"segment_rust",
			"segments_rust",
			"rust",
			"seg2",
		],
	)
	val segRust: String? = null,
	@SerializedName(
		"location",
		alternate = [
			"loc",
			"mushaf_location",
			"page",
			"line",
			"location_hint",
		],
	)
	val location: String? = null,
	@SerializedName(
		"imageId",
		alternate = [
			"image_id",
			"image",
			"image_key",
			"image_path",
			"image_name",
			"image_file",
			"img",
		],
	)
	val imageId: String? = null,
)

data class LinguisticsWordCardModel(
	val chapter: Int,
	val verse: Int,
	val wordNumber: Int,
	val arabic: String,
	val meaning: String,
	val pronunciation: String,
	val arabicGrammar: String,
	val segSky: String,
	val segRust: String,
	val location: String,
	val imageId: String?,
	val imageUrl: String?,
)
