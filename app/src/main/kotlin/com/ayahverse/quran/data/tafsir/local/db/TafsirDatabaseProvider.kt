package com.ayahverse.quran.data.tafsir.local.db

import android.content.Context
import androidx.room.Room

object TafsirDatabaseProvider {
	@Volatile private var instance: TafsirDatabase? = null

	fun get(context: Context): TafsirDatabase {
		return instance ?: synchronized(this) {
			instance ?: Room.databaseBuilder(
				context.applicationContext,
				TafsirDatabase::class.java,
				"tafsir.db",
			)
				.fallbackToDestructiveMigration()
				.build()
				.also { instance = it }
		}
	}
}
