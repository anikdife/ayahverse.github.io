package com.ayahverse.quran.data.tafsir.local.db

import androidx.room.Entity

@Entity(tableName = "tafsir_sources", primaryKeys = ["slug"])
data class TafsirSourceEntity(
	val slug: String,
	val legacyId: Int,
	val authorName: String,
	val languageCode: String,
	val languageDisplayName: String,
	val displayName: String,
	val sourceUrl: String,
	val providerType: String,
	val defaultPriority: Int,
	val isEnabled: Boolean,
	val updatedAtEpochMs: Long,
)

@Entity(
	tableName = "tafsir_cache",
	primaryKeys = ["slug", "verseKey", "targetLanguageCode", "origin"],
)
data class TafsirCacheEntity(
	val slug: String,
	val verseKey: String,
	val targetLanguageCode: String,
	/** DIRECT or TRANSLATED */
	val origin: String,
	val content: String,
	val contentHash: String,
	val fetchedAtEpochMs: Long,
	val expiresAtEpochMs: Long,
)
