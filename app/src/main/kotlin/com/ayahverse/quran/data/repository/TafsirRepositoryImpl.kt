package com.ayahverse.quran.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.ayahverse.quran.BuildConfig
import com.ayahverse.quran.core.util.normalizeLanguageCode
import com.ayahverse.quran.core.util.sanitizeTafsirToPlainText
import com.ayahverse.quran.core.util.sha256Hex
import com.ayahverse.quran.data.QuranBackendApi
import com.ayahverse.quran.data.BackendTranslateRequest
import com.ayahverse.quran.data.remote.service.QuranApiService
import com.ayahverse.quran.data.remote.dto.qurancom.findTextForResource
import com.ayahverse.quran.data.remote.dto.tafsir.TafsirResponse
import com.ayahverse.quran.data.remote.service.GenericTafsirApiService
import com.ayahverse.quran.data.tafsir.local.db.TafsirCacheEntity
import com.ayahverse.quran.data.tafsir.local.db.TafsirDatabaseProvider
import com.ayahverse.quran.data.tafsir.local.db.TafsirSourceEntity
import com.ayahverse.quran.data.tafsir.remote.adapter.AltafsirHtmlExtractor
import com.ayahverse.quran.data.tafsir.remote.service.AltafsirApiService
import com.ayahverse.quran.domain.model.tafsir.TafsirAyahResult
import com.ayahverse.quran.domain.model.tafsir.TafsirContentOrigin
import com.ayahverse.quran.domain.model.tafsir.TafsirDropdownOption
import com.ayahverse.quran.domain.model.tafsir.TafsirError
import com.ayahverse.quran.domain.model.tafsir.TafsirErrorType
import com.ayahverse.quran.domain.model.tafsir.TafsirRequest
import com.ayahverse.quran.domain.model.tafsir.TafsirSource
import com.ayahverse.quran.domain.registry.TafsirSourceRegistry
import com.ayahverse.quran.domain.repository.TafsirRepository
import com.ayahverse.quran.domain.resolver.DirectPayloadStatus
import com.ayahverse.quran.domain.resolver.TafsirAvailabilityResolver
import com.ayahverse.quran.domain.resolver.TafsirResolutionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TafsirRepositoryImpl(
	context: Context,
	private val resolver: TafsirAvailabilityResolver = TafsirAvailabilityResolver(),
	private val gson: Gson = Gson(),
	private val quranComBaseUrl: String = "https://api.quran.com/api/v4/",
	private val altafsirBaseUrl: String = "https://www.altafsir.com/",
	private val backendBaseUrl: String = QuranBackendApi.BASE_URL,
	private val cacheTtlDirectMs: Long = 7L * 24L * 60L * 60L * 1000L,
	private val cacheTtlTranslatedMs: Long = 7L * 24L * 60L * 60L * 1000L,
) : TafsirRepository {
	private fun logD(message: String) {
		if (!BuildConfig.DEBUG) return
		Log.d(TAG, message)
	}

	private val dao = TafsirDatabaseProvider.get(context).tafsirDao()

	private val quranApiService: QuranApiService = Retrofit.Builder()
		.baseUrl(ensureTrailingSlash(quranComBaseUrl))
		.addConverterFactory(GsonConverterFactory.create(gson))
		.build()
		.create(QuranApiService::class.java)

	private val genericTafsirApi: GenericTafsirApiService = Retrofit.Builder()
		.baseUrl("https://example.com/")
		.client(
			OkHttpClient.Builder()
				.connectTimeout(15, TimeUnit.SECONDS)
				.readTimeout(25, TimeUnit.SECONDS)
				.writeTimeout(25, TimeUnit.SECONDS)
				.build(),
		)
		.addConverterFactory(GsonConverterFactory.create(gson))
		.build()
		.create(GenericTafsirApiService::class.java)

	private val fawazEditionCache: ConcurrentHashMap<String, List<String>> = ConcurrentHashMap()

	private val altafsirService: AltafsirApiService = Retrofit.Builder()
		.baseUrl(ensureTrailingSlash(altafsirBaseUrl))
		.client(
			OkHttpClient.Builder()
				.addInterceptor { chain ->
					val req = chain.request().newBuilder()
						.header(
							"User-Agent",
							"Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36",
						)
						.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
						.build()
					chain.proceed(req)
				}
				.build(),
		)
		.addConverterFactory(ScalarsConverterFactory.create())
		.build()
		.create(AltafsirApiService::class.java)

	private val backendApi: QuranBackendApi = Retrofit.Builder()
		.baseUrl(ensureTrailingSlash(backendBaseUrl))
		.addConverterFactory(GsonConverterFactory.create(gson))
		.build()
		.create(QuranBackendApi::class.java)

	private val sourcesSeeded = AtomicBoolean(false)

	override suspend fun getTafsirSourcesForLanguage(languageCode: String): List<TafsirDropdownOption> {
		seedSourcesIfNeeded()
		val code = normalizeLanguageCode(languageCode)
		val direct = dao.getSourcesByLanguage(code)
		val list = if (direct.isNotEmpty()) direct else if (code != "ar") dao.getSourcesByLanguage("ar") else direct
		logD("getTafsirSourcesForLanguage(lang=$languageCode norm=$code) -> direct=${direct.size} final=${list.size}")
		return list.map { it.toDropdown() }
	}

	override suspend fun getTafsirAyah(request: TafsirRequest): TafsirAyahResult = withContext(Dispatchers.IO) {
		seedSourcesIfNeeded()
		val now = System.currentTimeMillis()
		val targetLang = normalizeLanguageCode(request.selectedTranslationLanguageCode)
		val verseKey = "${request.surahNumber}:${request.ayahNumber}"
		logD(
			"getTafsirAyah(verse=$verseKey slug=${request.selectedTafsirSlug} targetLang=${request.selectedTranslationLanguageCode}->${targetLang} forceRefresh=${request.forceRefresh})",
		)

		if (request.surahNumber <= 0 || request.ayahNumber <= 0) {
			return@withContext missingResult(
				request = request,
				targetLang = targetLang,
				source = null,
				now = now,
				error = TafsirError(
					type = TafsirErrorType.INVALID_REQUEST,
					message = "Invalid surah/ayah in request.",
				),
			)
		}

		dao.deleteExpiredCache(now)
		val sourceEntity = dao.getSourceBySlug(request.selectedTafsirSlug)
		val source = sourceEntity?.toDomain()
			?: TafsirSourceRegistry.getBySlug(request.selectedTafsirSlug)
			?: return@withContext missingResult(
				request = request,
				targetLang = targetLang,
				source = null,
				now = now,
				error = TafsirError(
					type = TafsirErrorType.SOURCE_NOT_REGISTERED,
					message = "Unknown tafsir source slug: ${request.selectedTafsirSlug}",
				),
			)

		val sourceLang = normalizeLanguageCode(source.languageCode)
		val needsTranslation = targetLang != sourceLang
		logD("resolved source=${source.slug} provider=${source.providerType} sourceLang=${source.languageCode} norm=$sourceLang needsTranslation=$needsTranslation")
		if (needsTranslation && sourceLang != "ar") {
			return@withContext missingResult(
				request = request,
				targetLang = targetLang,
				source = source,
				now = now,
				error = resolver.languageMismatchError(request.copy(selectedTranslationLanguageCode = targetLang), source),
			)
		}

		if (!request.forceRefresh) {
			val cached = if (!needsTranslation) {
				dao.getCache(
					slug = source.slug,
					verseKey = verseKey,
					targetLanguageCode = targetLang,
					origin = ORIGIN_DIRECT,
				)
			} else {
				dao.getCache(
					slug = source.slug,
					verseKey = verseKey,
					targetLanguageCode = targetLang,
					origin = ORIGIN_TRANSLATED,
				)
			}
			if (cached != null && cached.expiresAtEpochMs > now && cached.content.isNotBlank()) {
				if (!needsTranslation && isSuspectAltafsirCache(source, cached.content)) {
					logD("cache SUSPECT (altafsir) -> refetching")
				} else if (!needsTranslation && isSuspectQuranComCache(source, cached.content)) {
					logD("cache SUSPECT (quran.com) -> refetching")
				} else {
					logD("cache HIT origin=${cached.origin} expiresInMs=${cached.expiresAtEpochMs - now}")
					return@withContext resultFromCache(request, source, cached, targetLang, now)
				}
			}
			logD("cache MISS")
		}

		val directFetch = runCatching { fetchDirect(source, request.surahNumber, request.ayahNumber, verseKey) }
		val directRaw = directFetch.getOrNull().orEmpty()
		val directSanitized = sanitizeTafsirToPlainText(directRaw)

		// Altafsir: some English resources are incomplete and return "No tafsir for this verse exists".
		// If English is missing but Arabic exists, fetch Arabic and translate it via our backend.
		if (
			source.providerType == com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.ALTAFSIR &&
			directFetch.isSuccess &&
			directSanitized.isBlank() &&
			targetLang != "ar"
		) {
			val arabicAttempt = runCatching {
				fetchFromAltafsir(
					tafsirNo = source.legacyId,
					surah = request.surahNumber,
					ayah = request.ayahNumber,
					languageId = 1,
				)
			}
			val arabicRaw = arabicAttempt.getOrNull().orEmpty()
			val arabicSanitized = sanitizeTafsirToPlainText(arabicRaw)
			if (arabicSanitized.isNotBlank()) {
				val translationAttempt = runCatching {
					backendApi.translateText(
						BackendTranslateRequest(
							text = arabicSanitized,
							targetLang = targetLang,
							sourceLang = "ar",
						),
					)
				}
				val translated = translationAttempt.getOrNull()?.translatedText.orEmpty()
				val translatedSanitized = sanitizeTafsirToPlainText(translated)
				if (translatedSanitized.isNotBlank()) {
					logD("altafsir en-missing -> ar+translate ok (arLen=${arabicSanitized.length} trLen=${translatedSanitized.length})")
					val cached = cacheDirect(source, verseKey, targetLang, translatedSanitized, now)
					return@withContext TafsirAyahResult(
						request = request,
						source = source,
						title = source.displayName,
						authorName = source.authorName,
						originalLanguageCode = "ar",
						finalLanguageCode = targetLang,
						content = translatedSanitized,
						contentOrigin = TafsirContentOrigin.TRANSLATED_FALLBACK,
						isTranslated = true,
						translationProvider = "quran-backend",
						translationReason = "altafsir_ar_fallback",
						sourceUrl = source.sourceUrl,
						originalRawContent = arabicRaw.takeIf { it.isNotBlank() },
						sanitizedContent = translatedSanitized,
						isHtml = arabicRaw.contains('<') && arabicRaw.contains('>'),
						error = null,
						fetchedAtEpochMs = now,
						cacheKey = cached.cacheKeyForReturn,
					)
				}
			}
		}
		val directStatus: DirectPayloadStatus = when {
			directFetch.isFailure -> {
				logD("direct fetch FAILED: ${directFetch.exceptionOrNull()?.javaClass?.simpleName}: ${directFetch.exceptionOrNull()?.message}")
				DirectPayloadStatus.Failed(
					TafsirError(
						type = TafsirErrorType.NETWORK,
						message = directFetch.exceptionOrNull()?.message ?: "Network error.",
						isRetryable = true,
					),
				)
			}
			directSanitized.isBlank() -> DirectPayloadStatus.Missing
			else -> DirectPayloadStatus.Present
		}
		logD("direct status=$directStatus rawLen=${directRaw.length} sanitizedLen=${directSanitized.length}")

		val strategy = resolver.resolveStrategy(
			request = request.copy(selectedTranslationLanguageCode = targetLang),
			source = source,
			directPayloadStatus = directStatus,
		)
		logD("strategy=$strategy")

		when (strategy) {
			TafsirResolutionStrategy.USE_DIRECT -> {
				logD("using DIRECT")
				val cached = cacheDirect(source, verseKey, targetLang, directSanitized, now)
				return@withContext TafsirAyahResult(
					request = request,
					source = source,
					title = source.displayName,
					authorName = source.authorName,
					originalLanguageCode = sourceLang,
					finalLanguageCode = targetLang,
					content = directSanitized,
					contentOrigin = TafsirContentOrigin.DIRECT_API,
					isTranslated = false,
					translationProvider = null,
					translationReason = null,
					sourceUrl = source.sourceUrl,
					originalRawContent = directRaw.takeIf { it.isNotBlank() },
					sanitizedContent = directSanitized,
					isHtml = directRaw.contains('<') && directRaw.contains('>'),
					error = null,
					fetchedAtEpochMs = now,
					cacheKey = cached.cacheKeyForReturn,
				)
			}
			TafsirResolutionStrategy.TRANSLATE_SELECTED_SOURCE -> {
				logD("translating via backend (sourceLang=$sourceLang -> targetLang=$targetLang)")
				if (directSanitized.isBlank()) {
					return@withContext missingResult(
						request = request,
						targetLang = targetLang,
						source = source,
						now = now,
						error = TafsirError(
							type = TafsirErrorType.EMPTY_CONTENT,
							message = "No content returned for $verseKey.",
						),
					)
				}

				val translationAttempt = runCatching {
					backendApi.translateText(
						BackendTranslateRequest(
							text = directSanitized,
							targetLang = targetLang,
							sourceLang = sourceLang,
						),
					)
				}
				if (translationAttempt.isFailure) {
					logD("translation FAILED: ${translationAttempt.exceptionOrNull()?.javaClass?.simpleName}: ${translationAttempt.exceptionOrNull()?.message}")
				}
				val translation = translationAttempt.getOrNull()?.translatedText.orEmpty()

				val translatedSanitized = sanitizeTafsirToPlainText(translation)
				logD("translation result len=${translation.length} sanitizedLen=${translatedSanitized.length}")
				if (translatedSanitized.isBlank()) {
					// Conservative fallback: return original (Arabic) with error.
					logD("translation empty -> fallback to original")
					cacheDirect(source, verseKey, sourceLang, directSanitized, now)
					return@withContext TafsirAyahResult(
						request = request,
						source = source,
						title = source.displayName,
						authorName = source.authorName,
						originalLanguageCode = sourceLang,
						finalLanguageCode = sourceLang,
						content = directSanitized,
						contentOrigin = TafsirContentOrigin.DIRECT_API,
						isTranslated = false,
						translationProvider = "quran-backend",
						translationReason = "translation_failed_fallback_to_original",
						sourceUrl = source.sourceUrl,
						originalRawContent = directRaw.takeIf { it.isNotBlank() },
						sanitizedContent = directSanitized,
						isHtml = directRaw.contains('<') && directRaw.contains('>'),
						error = TafsirError(
							type = TafsirErrorType.TRANSLATION_FAILED,
							message = "Translation failed; showing original.",
							isRetryable = true,
						),
						fetchedAtEpochMs = now,
						cacheKey = sha256Hex("${source.slug}|$verseKey|$sourceLang|$ORIGIN_DIRECT"),
					)
				}

				val cached = cacheTranslated(source, verseKey, targetLang, translatedSanitized, now)
				logD("using TRANSLATED (cached)")
				return@withContext TafsirAyahResult(
					request = request,
					source = source,
					title = source.displayName,
					authorName = source.authorName,
					originalLanguageCode = sourceLang,
					finalLanguageCode = targetLang,
					content = translatedSanitized,
					contentOrigin = TafsirContentOrigin.TRANSLATED_FALLBACK,
					isTranslated = true,
					translationProvider = "quran-backend",
					translationReason = "source_language_ar_to_target_$targetLang",
					sourceUrl = source.sourceUrl,
					originalRawContent = directRaw.takeIf { it.isNotBlank() },
					sanitizedContent = translatedSanitized,
					isHtml = directRaw.contains('<') && directRaw.contains('>'),
					error = null,
					fetchedAtEpochMs = now,
					cacheKey = cached.cacheKeyForReturn,
				)
			}
			TafsirResolutionStrategy.RETURN_MISSING -> {
				logD("returning MISSING (directStatus=$directStatus)")
				return@withContext missingResult(
					request = request,
					targetLang = targetLang,
					source = source,
					now = now,
					error = when (directStatus) {
						is DirectPayloadStatus.Failed -> directStatus.error
						else -> TafsirError(
							type = TafsirErrorType.NOT_FOUND,
							message = "No tafsir available in ${source.displayName} for $verseKey.",
						)
					},
				)
			}
			TafsirResolutionStrategy.USE_CACHED_DIRECT -> {
				// Not used by current resolver; keep for future.
				logD("USE_CACHED_DIRECT hit (unexpected with current resolver)")
				return@withContext missingResult(request, targetLang, source, now, null)
			}
			TafsirResolutionStrategy.FAIL -> {
				logD("FAIL (directStatus=$directStatus)")
				val err = (directStatus as? DirectPayloadStatus.Failed)?.error
				return@withContext missingResult(request, targetLang, source, now, err)
			}
		}
	}

	private suspend fun seedSourcesIfNeeded() {
		if (sourcesSeeded.get()) return
		if (!sourcesSeeded.compareAndSet(false, true)) return
		val now = System.currentTimeMillis()
		val entities = TafsirSourceRegistry.allSources().map { it.toEntity(now) }
		dao.upsertSources(entities)
		logD("seeded sources count=${entities.size}")
	}

	private suspend fun fetchDirect(source: TafsirSource, surah: Int, ayah: Int, verseKey: String): String {
		return when (source.providerType) {
			com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.QURAN_COM -> fetchFromQuranCom(source.legacyId, verseKey)
			com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.ALTAFSIR -> fetchFromAltafsir(source.legacyId, surah, ayah, languageId = 2)
			com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.QURAN_CLOUD,
			com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.QURAN_ENC,
			com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.FAWAZ_AHMED,
			-> fetchTafsirForSource(source = source, surah = surah, ayah = ayah)
			else -> ""
		}
	}

	private suspend fun fetchTafsirForSource(source: TafsirSource, surah: Int, ayah: Int): String {
		val slug = source.slug.trim().lowercase()
		val route = LanguageTafsirRoutes.bySlug[slug]
			?: LanguageTafsirRoutes.byCode[normalizeLanguageCode(source.languageCode)]
			?: return ""
		return fetchByRoute(route = route, surah = surah, ayah = ayah)
	}

	override suspend fun fetchTafsir(languageCode: String, surah: Int, ayah: Int): String {
		val code = normalizeLanguageCode(languageCode)
		val route = LanguageTafsirRoutes.byCode[code] ?: return ""
		return fetchByRoute(route = route, surah = surah, ayah = ayah)
	}

	private suspend fun fetchByRoute(route: LanguageTafsirRoute, surah: Int, ayah: Int): String {
		// FawazAhmed editions are big JSON files: download once per edition and reuse.
		if (route.provider == LanguageTafsirProvider.FAWAZ_AHMED) {
			val cached = fawazEditionCache[route.key]
			val list = cached ?: run {
				val url = route.editionUrl
				val resp = genericTafsirApi.fetch(url)
				if (!resp.isSuccessful) return ""
				val body: TafsirResponse = resp.body() ?: return ""
				val texts = body.quran
					?.sortedWith(compareBy({ it.chapter }, { it.verse }))
					?.map { it.text }
					?: return ""
				fawazEditionCache[route.key] = texts
				texts
			}
			val idx = globalAyahIndex0(surah, ayah)
			return list.getOrNull(idx).orEmpty()
		}

		val url = route.urlFor(surah = surah, ayah = ayah)
		val resp = genericTafsirApi.fetch(url)
		if (!resp.isSuccessful) return ""
		val body: TafsirResponse = resp.body() ?: return ""
		return body.extractDirectText().orEmpty()
	}

	private suspend fun fetchFromQuranCom(resourceId: Int, verseKey: String): String {
		val primary = quranApiService.getTafseerByAyahKey(tafsirId = resourceId.toString(), ayahKey = verseKey)
		if (primary.isSuccessful) {
			val body = primary.body()
			val text = body?.findTextForResource(resourceId)
			if (!text.isNullOrBlank()) return text
		}

		val verse = quranApiService.getUthmaniVerse(verseKey)
		val verseId = verse.body()?.verses?.firstOrNull()?.id
			?: return ""
		val fallback = quranApiService.getTafseerByAyahId(tafsirResourceId = resourceId, ayahId = verseId)
		if (!fallback.isSuccessful) return ""
		return fallback.body()?.tafsir?.text.orEmpty()
	}

	private suspend fun fetchFromAltafsir(tafsirNo: Int, surah: Int, ayah: Int, languageId: Int): String {
		val response = altafsirService.getTafsirHtml(
			tafsirNo = tafsirNo,
			surahNumber = surah,
			ayahNumber = ayah,
			languageId = languageId,
		)
		if (!response.isSuccessful) return ""
		val html = response.body().orEmpty()
		return AltafsirHtmlExtractor.extractPlainText(html)
	}

	private suspend fun cacheDirect(
		source: TafsirSource,
		verseKey: String,
		targetLang: String,
		content: String,
		now: Long,
	): CacheWriteResult {
		val cacheKey = sha256Hex("${source.slug}|$verseKey|$targetLang|$ORIGIN_DIRECT")
		val entry = TafsirCacheEntity(
			slug = source.slug,
			verseKey = verseKey,
			targetLanguageCode = targetLang,
			origin = ORIGIN_DIRECT,
			content = content,
			contentHash = sha256Hex(content),
			fetchedAtEpochMs = now,
			expiresAtEpochMs = now + cacheTtlDirectMs,
		)
		dao.upsertCache(entry)
		return CacheWriteResult(cacheKeyForReturn = cacheKey)
	}

	private fun isSuspectAltafsirCache(source: TafsirSource, cachedContent: String): Boolean {
		if (source.providerType != com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.ALTAFSIR) return false
		val trimmed = cachedContent.trim()
		if (trimmed.isBlank()) return true
		// Historical bug: heuristic HTML extraction sometimes cached only the header/picker fragment.
		// Treat short, non-content "Tafsir ..." payloads as invalid so we refetch and overwrite.
		val looksLikeHeaderOnly =
			Regex("^[*\\-\\s]*tafsir\\b.*$", RegexOption.IGNORE_CASE).matches(trimmed)
				|| trimmed.equals("-Tafsir", ignoreCase = true)
				|| trimmed.equals("Tafsir", ignoreCase = true)
		if (looksLikeHeaderOnly && trimmed.length <= 220) return true
		// Ultra-short Altafsir results are almost always wrong (real tafsir is usually a paragraph+).
		if (trimmed.length in 1..18 && trimmed.contains("tafsir", ignoreCase = true)) return true
		return false
	}

	private fun isSuspectQuranComCache(source: TafsirSource, cachedContent: String): Boolean {
		if (source.providerType != com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.QURAN_COM) return false
		val sourceLang = normalizeLanguageCode(source.languageCode)
		if (sourceLang != "bn") return false
		val trimmed = cachedContent.trim()
		if (trimmed.isBlank()) return true
		// If a Bengali tafsir is cached without any Bengali-script characters, it is almost certainly wrong.
		val hasBengaliChars = Regex("[\\u0980-\\u09FF]").containsMatchIn(trimmed)
		return !hasBengaliChars
	}

	private fun globalAyahIndex0(surah: Int, ayah: Int): Int {
		// 1-based inputs -> 0-based index into a flattened 6236-length list.
		if (surah !in 1..114 || ayah <= 0) return -1
		val prefix = prefixAyahCounts0Based
		val start = prefix[surah - 1]
		return start + (ayah - 1)
	}

	private val prefixAyahCounts0Based: IntArray by lazy {
		val counts = intArrayOf(
			7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128,
			111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30,
			73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29,
			18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18,
			12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
			29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19,
			5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4,
			5, 6,
		)
		val prefix = IntArray(114)
		var sum = 0
		for (i in 0 until 114) {
			prefix[i] = sum
			sum += counts[i]
		}
		prefix
	}


private enum class LanguageTafsirProvider { QURAN_CLOUD, QURAN_ENC, FAWAZ_AHMED }

private data class LanguageTafsirRoute(
	val code: String,
	val slug: String,
	val provider: LanguageTafsirProvider,
	val key: String,
	val urlTemplate: String,
) {
	fun urlFor(surah: Int, ayah: Int): String = urlTemplate
		.replace("{surah}", surah.toString())
		.replace("{ayah}", ayah.toString())

	val editionUrl: String get() = urlTemplate
}

private object LanguageTafsirRoutes {
	private val routes: List<LanguageTafsirRoute> = listOf(
		LanguageTafsirRoute(
			code = "ur",
			slug = "ur.tafsir-as-saadi",
			provider = LanguageTafsirProvider.QURAN_CLOUD,
			key = "ur.tafsir-as-saadi",
			urlTemplate = "https://api.alquran.cloud/v1/ayah/{surah}:{ayah}/ur.tafsir-as-saadi",
		),
		LanguageTafsirRoute(
			code = "tr",
			slug = "turkish_shaban",
			provider = LanguageTafsirProvider.QURAN_ENC,
			key = "turkish_shaban",
			urlTemplate = "https://quranenc.com/api/v1/translation/aya/turkish_shaban/{surah}/{ayah}",
		),
		LanguageTafsirRoute(
			code = "id",
			slug = "indonesian_complex",
			provider = LanguageTafsirProvider.QURAN_ENC,
			key = "indonesian_complex",
			urlTemplate = "https://quranenc.com/api/v1/translation/aya/indonesian_complex/{surah}/{ayah}",
		),
		LanguageTafsirRoute(
			code = "fr",
			slug = "fr.tafsir-abridged",
			provider = LanguageTafsirProvider.QURAN_CLOUD,
			key = "fr.tafsir-abridged",
			urlTemplate = "https://api.alquran.cloud/v1/ayah/{surah}:{ayah}/fr.tafsir-abridged",
		),
		LanguageTafsirRoute(
			code = "ru",
			slug = "russian_kuliev",
			provider = LanguageTafsirProvider.QURAN_ENC,
			key = "russian_kuliev",
			urlTemplate = "https://quranenc.com/api/v1/translation/aya/russian_kuliev/{surah}/{ayah}",
		),
		LanguageTafsirRoute(
			code = "es",
			slug = "spa-muhammadisagarc",
			provider = LanguageTafsirProvider.FAWAZ_AHMED,
			key = "spa-muhammadisagarc",
			urlTemplate = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/spa-muhammadisagarc.json",
		),
		LanguageTafsirRoute(
			code = "de",
			slug = "deu-aburidamuhammad",
			provider = LanguageTafsirProvider.FAWAZ_AHMED,
			key = "deu-aburidamuhammad",
			urlTemplate = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/deu-aburidamuhammad.json",
		),
		LanguageTafsirRoute(
			code = "zh",
			slug = "chinese_makin",
			provider = LanguageTafsirProvider.QURAN_ENC,
			key = "chinese_makin",
			urlTemplate = "https://quranenc.com/api/v1/translation/aya/chinese_makin/{surah}/{ayah}",
		),
		LanguageTafsirRoute(
			code = "zh",
			slug = "chinese_suliman",
			provider = LanguageTafsirProvider.QURAN_ENC,
			key = "chinese_suliman",
			urlTemplate = "https://quranenc.com/api/v1/translation/aya/chinese_suliman/{surah}/{ayah}",
		),
		LanguageTafsirRoute(
			code = "hi",
			slug = "hin-suhelfarooqkhan",
			provider = LanguageTafsirProvider.FAWAZ_AHMED,
			key = "hin-suhelfarooqkhan",
			urlTemplate = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/hin-suhelfarooqkhan.json",
		),
	)

	val bySlug: Map<String, LanguageTafsirRoute> = routes.associateBy { it.slug }
	val byCode: Map<String, LanguageTafsirRoute> = routes
		.groupBy { it.code }
		.mapValues { (_, list) -> list.first() }
}
	private suspend fun cacheTranslated(
		source: TafsirSource,
		verseKey: String,
		targetLang: String,
		content: String,
		now: Long,
	): CacheWriteResult {
		val cacheKey = sha256Hex("${source.slug}|$verseKey|$targetLang|$ORIGIN_TRANSLATED")
		val entry = TafsirCacheEntity(
			slug = source.slug,
			verseKey = verseKey,
			targetLanguageCode = targetLang,
			origin = ORIGIN_TRANSLATED,
			content = content,
			contentHash = sha256Hex(content),
			fetchedAtEpochMs = now,
			expiresAtEpochMs = now + cacheTtlTranslatedMs,
		)
		dao.upsertCache(entry)
		return CacheWriteResult(cacheKeyForReturn = cacheKey)
	}

	private fun resultFromCache(
		request: TafsirRequest,
		source: TafsirSource,
		cached: TafsirCacheEntity,
		targetLang: String,
		now: Long,
	): TafsirAyahResult {
		val origin = when (cached.origin) {
			ORIGIN_DIRECT -> TafsirContentOrigin.CACHED_DIRECT
			ORIGIN_TRANSLATED -> TafsirContentOrigin.CACHED_TRANSLATED
			else -> TafsirContentOrigin.MISSING
		}
		val isTranslated = cached.origin == ORIGIN_TRANSLATED
		return TafsirAyahResult(
			request = request,
			source = source,
			title = source.displayName,
			authorName = source.authorName,
			originalLanguageCode = source.languageCode,
			finalLanguageCode = if (isTranslated) targetLang else source.languageCode,
			content = cached.content,
			contentOrigin = origin,
			isTranslated = isTranslated,
			translationProvider = if (isTranslated) "quran-backend" else null,
			translationReason = if (isTranslated) "cached" else null,
			sourceUrl = source.sourceUrl,
			originalRawContent = null,
			sanitizedContent = cached.content,
			isHtml = false,
			error = null,
			fetchedAtEpochMs = now,
			cacheKey = sha256Hex("${source.slug}|${cached.verseKey}|${cached.targetLanguageCode}|${cached.origin}"),
		)
	}

	private fun missingResult(
		request: TafsirRequest,
		targetLang: String,
		source: TafsirSource?,
		now: Long,
		error: TafsirError?,
	): TafsirAyahResult {
		return TafsirAyahResult(
			request = request,
			source = source,
			title = source?.displayName ?: "Tafsir",
			authorName = source?.authorName ?: "",
			originalLanguageCode = source?.languageCode,
			finalLanguageCode = targetLang,
			content = null,
			contentOrigin = TafsirContentOrigin.MISSING,
			isTranslated = false,
			translationProvider = null,
			translationReason = null,
			sourceUrl = source?.sourceUrl,
			originalRawContent = null,
			sanitizedContent = null,
			isHtml = false,
			error = error,
			fetchedAtEpochMs = now,
			cacheKey = sha256Hex("missing|${request.selectedTafsirSlug}|${request.surahNumber}:${request.ayahNumber}|$targetLang"),
		)
	}
}

private data class CacheWriteResult(
	val cacheKeyForReturn: String,
)

private fun TafsirSource.toEntity(nowEpochMs: Long): TafsirSourceEntity {
	return TafsirSourceEntity(
		slug = slug,
		legacyId = legacyId,
		authorName = authorName,
		languageCode = normalizeLanguageCode(languageCode),
		languageDisplayName = languageDisplayName,
		displayName = displayName,
		sourceUrl = sourceUrl,
		providerType = providerType.name,
		defaultPriority = defaultPriority,
		isEnabled = isEnabled,
		updatedAtEpochMs = nowEpochMs,
	)
}

private fun TafsirSourceEntity.toDomain(): TafsirSource {
	return TafsirSource(
		slug = slug,
		legacyId = legacyId,
		authorName = authorName,
		languageCode = languageCode,
		languageDisplayName = languageDisplayName,
		displayName = displayName,
		sourceUrl = sourceUrl,
		providerType = runCatching { com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.valueOf(providerType) }.getOrDefault(
			com.ayahverse.quran.domain.model.tafsir.TafsirProviderType.OTHER,
		),
		defaultPriority = defaultPriority,
		isEnabled = isEnabled,
	)
}

private fun TafsirSourceEntity.toDropdown(): TafsirDropdownOption {
	return TafsirDropdownOption(
		slug = slug,
		displayName = displayName,
		authorName = authorName,
		languageCode = languageCode,
		defaultPriority = defaultPriority,
	)
}

private fun ensureTrailingSlash(url: String): String = if (url.endsWith('/')) url else "$url/"

private const val ORIGIN_DIRECT = "DIRECT"
private const val ORIGIN_TRANSLATED = "TRANSLATED"

private const val TAG = "TafsirRepo"
