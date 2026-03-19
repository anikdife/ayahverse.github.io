package com.ayahverse.quran.data.repository

import com.google.gson.Gson
import com.ayahverse.quran.data.local.registry.TafseerRegistryEntry
import com.ayahverse.quran.data.local.registry.TafseerRegistryLoader
import com.ayahverse.quran.data.local.registry.ClasspathTafseerRegistryLoader
import com.ayahverse.quran.data.mapper.toDomain
import com.ayahverse.quran.data.remote.service.AlQuranCloudApiService
import com.ayahverse.quran.data.remote.service.QuranEncApiService
import com.ayahverse.quran.data.remote.service.QuranApiService
import com.ayahverse.quran.data.remote.service.QuranTafseerApiService
import com.ayahverse.quran.domain.models.AyatReference
import com.ayahverse.quran.domain.models.Tafseer
import com.ayahverse.quran.domain.repository.TafseerRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap

class TafseerRepositoryImpl(
	private val registryLoader: TafseerRegistryLoader = ClasspathTafseerRegistryLoader(),
	private val gson: Gson = Gson(),
	private val defaultQuranComBaseUrl: String = "https://api.quran.com/api/v4/",
	private val defaultAlQuranCloudBaseUrl: String = "https://api.alquran.cloud/v1/",
) : TafseerRepository {
	private val registryCache = lazy { registryLoader.loadRegistry().associateBy { it.id } }
	private val quranComServices = ConcurrentHashMap<String, QuranApiService>()
	private val alQuranCloudServices = ConcurrentHashMap<String, AlQuranCloudApiService>()
	private val quranTafseerServices = ConcurrentHashMap<String, QuranTafseerApiService>()
	private val quranEncServices = ConcurrentHashMap<String, QuranEncApiService>()

	override suspend fun getTafseer(ayat: AyatReference, tafseerId: String): Tafseer {
		val entry = registryCache.value[tafseerId]
			?: throw IllegalArgumentException("Unknown tafseerId: $tafseerId")

		return when (entry.source.trim().lowercase()) {
			"quran.com", "quran_com", "qurancom", "quran" -> fetchFromQuranCom(entry, ayat)
			"alquran.cloud", "alquran_cloud", "alqurancloud", "al-quran-cloud", "al quran cloud" -> fetchFromAlQuranCloud(entry, ayat)
			"quran-tafseer", "qurantafseer", "quran_tafseer", "quran tafseer" -> fetchFromQuranTafseer(entry, ayat)
			"quranenc", "quran-enc", "quran_enc" -> fetchFromQuranEnc(entry, ayat)
			else -> throw IllegalStateException("Unsupported tafseer source: ${entry.source}")
		}
	}

	private suspend fun fetchFromQuranCom(entry: TafseerRegistryEntry, ayat: AyatReference): Tafseer {
		val baseUrl = (entry.baseUrl ?: defaultQuranComBaseUrl).ensureTrailingSlash()
		val service = quranComServices.getOrPut(baseUrl) { createQuranComService(baseUrl) }
		val verseKey = ayat.verseKey

		// Preferred: registry-style endpointPattern (currently returns empty tafsirs[] in practice).
		val primaryResponse = service.getTafseerByAyahKey(tafsirId = entry.id, ayahKey = verseKey)
		if (primaryResponse.isSuccessful) {
			val body = primaryResponse.body()
			if (body != null && body.tafsirs.isNotEmpty()) {
				return body.toDomain(
					id = entry.id,
					overrideName = entry.name,
					source = entry.source,
				)
			}
		}

		// Fallback: legacy endpoint that is known to work.
		val verseResponse = service.getUthmaniVerse(verseKey)
		if (!verseResponse.isSuccessful) {
			throw IllegalStateException("Quran.com verse lookup failed: HTTP ${verseResponse.code()}")
		}
		val verseId = verseResponse.body()?.verses?.firstOrNull()?.id
			?: throw IllegalStateException("Quran.com verse lookup returned no verse for $verseKey")

		val tafsirResourceId = entry.id.toIntOrNull()
			?: throw IllegalStateException(
				"Quran.com tafseer id must be numeric for fallback endpoint (got '${entry.id}')",
			)

		val fallbackResponse = service.getTafseerByAyahId(tafsirResourceId = tafsirResourceId, ayahId = verseId)
		if (!fallbackResponse.isSuccessful) {
			throw IllegalStateException("Quran.com tafseer fetch failed: HTTP ${fallbackResponse.code()}")
		}
		val dto = fallbackResponse.body()
			?: throw IllegalStateException("Quran.com tafseer response body was null")

		return dto.toDomain(
			id = entry.id,
			overrideName = entry.name,
			source = entry.source,
		)
	}

	private fun createQuranComService(baseUrl: String): QuranApiService {
		return Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(GsonConverterFactory.create(gson))
			.build()
			.create(QuranApiService::class.java)
	}

	private suspend fun fetchFromAlQuranCloud(entry: TafseerRegistryEntry, ayat: AyatReference): Tafseer {
		val baseUrl = (entry.baseUrl ?: defaultAlQuranCloudBaseUrl).ensureTrailingSlash()
		val service = alQuranCloudServices.getOrPut(baseUrl) { createAlQuranCloudService(baseUrl) }
		val edition = entry.id

		val response = service.getAyah(reference = ayat.verseKey, edition = edition)
		if (!response.isSuccessful) {
			throw IllegalStateException("Al Quran Cloud fetch failed: HTTP ${response.code()}")
		}
		val body = response.body() ?: throw IllegalStateException("Al Quran Cloud response body was null")
		return Tafseer(
			id = entry.id,
			name = entry.name,
			content = body.data.text,
			source = entry.source,
		)
	}

	private suspend fun fetchFromQuranTafseer(entry: TafseerRegistryEntry, ayat: AyatReference): Tafseer {
		val baseUrl = (entry.baseUrl ?: "http://api.quran-tafseer.com/").ensureTrailingSlash()
		val service = quranTafseerServices.getOrPut(baseUrl) { createQuranTafseerService(baseUrl) }
		val response = service.getTafseer(tafseerId = entry.id, surah = ayat.surahNumber, ayah = ayat.ayahNumber)
		if (!response.isSuccessful) {
			throw IllegalStateException("Quran-Tafseer fetch failed: HTTP ${response.code()}")
		}
		val body = response.body() ?: throw IllegalStateException("Quran-Tafseer response body was null")
		return Tafseer(
			id = entry.id,
			name = entry.name,
			content = body.text,
			source = entry.source,
		)
	}

	private fun createQuranTafseerService(baseUrl: String): QuranTafseerApiService {
		return Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(GsonConverterFactory.create(gson))
			.build()
			.create(QuranTafseerApiService::class.java)
	}

	private suspend fun fetchFromQuranEnc(entry: TafseerRegistryEntry, ayat: AyatReference): Tafseer {
		val baseUrl = (entry.baseUrl ?: "https://quranenc.com/api/v1/").ensureTrailingSlash()
		val service = quranEncServices.getOrPut(baseUrl) { createQuranEncService(baseUrl) }
		val response = service.getAyahTranslation(
			translationKey = entry.id,
			surah = ayat.surahNumber,
			ayah = ayat.ayahNumber,
		)
		if (!response.isSuccessful) {
			throw IllegalStateException("QuranEnc fetch failed: HTTP ${response.code()}")
		}
		val body = response.body() ?: throw IllegalStateException("QuranEnc response body was null")
		return Tafseer(
			id = entry.id,
			name = entry.name,
			content = body.result.translation,
			source = entry.source,
		)
	}

	private fun createQuranEncService(baseUrl: String): QuranEncApiService {
		return Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(GsonConverterFactory.create(gson))
			.build()
			.create(QuranEncApiService::class.java)
	}

	private fun createAlQuranCloudService(baseUrl: String): AlQuranCloudApiService {
		return Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(GsonConverterFactory.create(gson))
			.build()
			.create(AlQuranCloudApiService::class.java)
	}
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
