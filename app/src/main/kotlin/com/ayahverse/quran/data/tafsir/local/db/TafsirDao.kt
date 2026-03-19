package com.ayahverse.quran.data.tafsir.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TafsirDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertSources(sources: List<TafsirSourceEntity>)

	@Query("SELECT * FROM tafsir_sources WHERE isEnabled = 1 AND languageCode = :languageCode ORDER BY defaultPriority ASC, displayName ASC")
	suspend fun getSourcesByLanguage(languageCode: String): List<TafsirSourceEntity>

	@Query("SELECT * FROM tafsir_sources WHERE slug = :slug LIMIT 1")
	suspend fun getSourceBySlug(slug: String): TafsirSourceEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertCache(entry: TafsirCacheEntity)

	@Query(
		"SELECT * FROM tafsir_cache " +
			"WHERE slug = :slug AND verseKey = :verseKey AND targetLanguageCode = :targetLanguageCode AND origin = :origin " +
			"LIMIT 1",
	)
	suspend fun getCache(
		slug: String,
		verseKey: String,
		targetLanguageCode: String,
		origin: String,
	): TafsirCacheEntity?

	@Query("DELETE FROM tafsir_cache WHERE expiresAtEpochMs <= :nowEpochMs")
	suspend fun deleteExpiredCache(nowEpochMs: Long)

	@Query("DELETE FROM tafsir_cache WHERE slug = :slug")
	suspend fun deleteCacheBySlug(slug: String)
}
