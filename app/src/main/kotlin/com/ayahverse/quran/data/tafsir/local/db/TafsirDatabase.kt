package com.ayahverse.quran.data.tafsir.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
	entities = [
		TafsirSourceEntity::class,
		TafsirCacheEntity::class,
	],
	version = 1,
	exportSchema = false,
)
abstract class TafsirDatabase : RoomDatabase() {
	abstract fun tafsirDao(): TafsirDao
}
