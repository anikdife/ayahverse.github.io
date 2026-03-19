package com.ayahverse.quran.linguistics.supabase

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ayahverse.quran.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/**
 * Minimal Supabase client using PostgREST + public Storage URLs.
 *
 * Expected BuildConfig fields:
 * - SUPABASE_URL
 * - SUPABASE_ANON_KEY
 * - SUPABASE_STORAGE_BUCKET
 * - SUPABASE_STORAGE_PREFIX
 * - SUPABASE_STORAGE_IMAGE_EXT
 */
class SupabaseLinguisticsClient(
	private val http: OkHttpClient = OkHttpClient(),
	private val gson: Gson = Gson(),
) {
	private val logTag = "SupabaseLinguistics"

	private data class CacheEntry(
		val value: String?,
		val expiresAtMs: Long,
	)

	private val baseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
	// Supports both legacy JWT keys (anon/service_role) and new publishable keys.
	private val apiKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
	private val storageBucket: String = BuildConfig.SUPABASE_STORAGE_BUCKET.trim().lowercase()
	private val storagePrefix: String = BuildConfig.SUPABASE_STORAGE_PREFIX.trim().trim('/').let { if (it.isBlank()) "" else "$it/" }
	private val imageExt: String = BuildConfig.SUPABASE_STORAGE_IMAGE_EXT.trim().ifBlank { ".png" }
	private val apiKeyLooksJwt: Boolean = apiKey.startsWith("eyJ") && apiKey.length > 100
	private val signedUrlCache = linkedMapOf<String, CacheEntry>()
	private val signedUrlCacheMax = 300
	private val signJsonMediaType = "application/json".toMediaType()

	private fun nowMs(): Long = System.currentTimeMillis()

	private fun getFreshEntry(key: String): CacheEntry? {
		val entry = signedUrlCache[key] ?: return null
		if (entry.expiresAtMs <= nowMs()) {
			signedUrlCache.remove(key)
			return null
		}
		return entry
	}

	init {
		if (BuildConfig.DEBUG) {
			Log.d(
				logTag,
				"Config: baseUrl=$baseUrl bucket=$storageBucket prefix=$storagePrefix keyLen=${apiKey.length} looksJwt=$apiKeyLooksJwt",
			)
		}
	}

	fun isConfigured(): Boolean {
		return baseUrl.isNotBlank() && apiKey.isNotBlank() && storageBucket.isNotBlank()
	}

	suspend fun fetchWordsJson(chapter: Int, verse: Int): List<SupabaseAyahWordDto> = withContext(Dispatchers.IO) {
		if (baseUrl.isBlank() || apiKey.isBlank()) return@withContext emptyList()

		val url = buildString {
			append(baseUrl)
			append("/rest/v1/quran_ayah_words")
			append("?chapter=eq.")
			append(chapter)
			append("&verse=eq.")
			append(verse)
			append("&select=words")
		}

			val builder = Request.Builder()
				.url(url)
				.get()
				.addHeader("apikey", apiKey)
				.addHeader("Accept", "application/json")

			// Only legacy API keys are JWTs that can be used as Bearer tokens.
			// New publishable keys should be sent as apikey only.
			if (apiKeyLooksJwt) {
				builder.addHeader("Authorization", "Bearer $apiKey")
			}

			val req = builder.build()

			val resp = http.newCall(req).execute()
		resp.use {
				val body = it.body?.string().orEmpty()
				if (!it.isSuccessful) {
					val msg = buildString {
						append("Supabase error: HTTP ")
						append(it.code)
						append(" url=")
						append(url)
						if (body.isNotBlank()) {
							append(" body=")
							append(body.take(300))
						}
					}
					throw IOException(msg)
				}
			if (body.isBlank()) return@withContext emptyList()

			val rows = gson.fromJson(body, JsonArray::class.java) ?: return@withContext emptyList()
			val firstRow = rows.firstOrNull()?.asJsonObject ?: return@withContext emptyList()
			val wordsEl: JsonElement? = firstRow.get("words")
			if (wordsEl == null || !wordsEl.isJsonArray) return@withContext emptyList()

			if (BuildConfig.DEBUG) {
				try {
					val firstWordObj = wordsEl.asJsonArray.firstOrNull()?.asJsonObject
					val keys = firstWordObj?.keySet()?.toList().orEmpty().sorted()
					Log.d(logTag, "Supabase words keys (chapter=$chapter verse=$verse): ${keys.joinToString(",")}")
				} catch (_: Throwable) {
					// Ignore logging errors.
				}
			}

			val type = object : TypeToken<List<SupabaseAyahWordDto>>() {}.type
			val parsed: List<SupabaseAyahWordDto> = gson.fromJson(wordsEl, type) ?: emptyList()
			if (BuildConfig.DEBUG && parsed.isNotEmpty()) {
				val p0 = parsed.first()
				Log.d(
					logTag,
					"Parsed[0]: wordNumber=${p0.wordNumber} arabic=${p0.arabic?.take(16)} meaning=${p0.meaning?.take(16)} pron=${p0.pronunciation?.take(16)} grammar=${p0.arabicGrammar?.take(16)} segSky=${p0.segSky?.take(16)} segRust=${p0.segRust?.take(16)} location=${p0.location?.take(16)} imageId=${p0.imageId?.take(24)}",
				)
			}
			return@withContext parsed
		}
	}

	fun publicImageUrl(imageIdRaw: String?): String? {
		val raw = imageIdRaw?.trim().orEmpty()
		if (raw.isBlank()) return null
		if (baseUrl.isBlank() || storageBucket.isBlank()) return null
		// Some datasets store a full URL already.
		if (raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true)) {
			return raw
		}

		val base = baseUrl.toHttpUrlOrNull() ?: return null

		// Allow raw values like "folder/file.png" or "prefix/folder/file".
		val rawPath = raw.trimStart('/')
		val hasExt = rawPath.substringAfterLast('/', rawPath).contains('.')
		val filePath = if (hasExt) rawPath else rawPath + imageExt
		val effectivePath = if (storagePrefix.isNotBlank() && !filePath.startsWith(storagePrefix)) {
			storagePrefix + filePath
		} else {
			filePath
		}

		return base
			.newBuilder()
			.addPathSegments("storage/v1/object/public")
			.addPathSegment(storageBucket)
			.addPathSegments(effectivePath)
			.build()
			.toString()
	}

	fun authenticatedImageUrlForPath(path: String): String? {
		val raw = path.trim().trimStart('/')
		if (raw.isBlank()) return null
		if (baseUrl.isBlank() || storageBucket.isBlank()) return null
		val base = baseUrl.toHttpUrlOrNull() ?: return null
		return base
			.newBuilder()
			.addPathSegments("storage/v1/object/authenticated")
			.addPathSegment(storageBucket)
			.addPathSegments(raw)
			.build()
			.toString()
	}

	private fun cacheSigned(key: String, value: String?, ttlMs: Long) {
		val expiresAt = nowMs() + ttlMs.coerceAtLeast(0L)
		val entry = CacheEntry(value = value, expiresAtMs = expiresAt)
		if (signedUrlCache.containsKey(key)) {
			signedUrlCache[key] = entry
			return
		}
		if (signedUrlCache.size >= signedUrlCacheMax) {
			// Drop oldest.
			val firstKey = signedUrlCache.entries.firstOrNull()?.key
			if (firstKey != null) signedUrlCache.remove(firstKey)
		}
		signedUrlCache[key] = entry
	}

	private fun resolveCandidatePaths(imageIdRaw: String): List<String> {
		val rawPath = imageIdRaw.trim().trimStart('/')
		val hasExt = rawPath.substringAfterLast('/', rawPath).contains('.')

		val extCandidates = linkedSetOf<String>()
		if (hasExt) {
			extCandidates.add("")
		} else {
			extCandidates.add(imageExt)
			extCandidates.add(".png")
			extCandidates.add(".jpg")
			extCandidates.add(".jpeg")
			extCandidates.add(".webp")
		}

		val baseCandidates = extCandidates.map { ext -> if (hasExt) rawPath else rawPath + ext }
		// Some buckets accidentally contain files like "1.png.png".
		// Try a double-extension variant as a fallback when the base id has no extension.
		val doubleExtCandidates = if (!hasExt) {
			val pngExt = imageExt.ifBlank { ".png" }
			listOf(rawPath + pngExt + pngExt, rawPath + ".png.png")
		} else {
			emptyList()
		}
		val prefix = storagePrefix
		val prefixed = if (prefix.isNotBlank()) baseCandidates.map { p -> if (p.startsWith(prefix)) p else prefix + p } else emptyList()
		val orderedBase = if (prefixed.isNotEmpty()) (prefixed + baseCandidates) else baseCandidates
		val ordered = orderedBase + doubleExtCandidates
		return ordered
			.map { it.trimStart('/') }
			.distinct()
	}

	private fun buildSignedUrlFromResponse(signedPath: String): String? {
		val trimmed = signedPath.trim()
		if (trimmed.isBlank()) return null
		if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
			return trimmed
		}
		val base = baseUrl.trimEnd('/')
		// Supabase may return relative paths in different shapes depending on API/version.
		// Normalize to a working Storage URL under `/storage/v1/...`.
		val normalizedPath = when {
			trimmed.startsWith("/storage/v1/", ignoreCase = true) -> trimmed
			trimmed.startsWith("storage/v1/", ignoreCase = true) -> "/" + trimmed
			trimmed.startsWith("/object/", ignoreCase = true) -> "/storage/v1" + trimmed
			trimmed.startsWith("object/", ignoreCase = true) -> "/storage/v1/" + trimmed
			trimmed.startsWith("/", ignoreCase = true) -> "/storage/v1" + trimmed
			else -> "/storage/v1/" + trimmed
		}
		return base + normalizedPath
	}

	private suspend fun signObjectPath(path: String, expiresInSec: Int = 60 * 60): String? = withContext(Dispatchers.IO) {
		if (!isConfigured()) return@withContext null
		val base = baseUrl.toHttpUrlOrNull() ?: return@withContext null

		val url = base
			.newBuilder()
			.addPathSegments("storage/v1/object/sign")
			.addPathSegment(storageBucket)
			.addPathSegments(path)
			.build()

		val cacheKey = "sign:$path"
		getFreshEntry(cacheKey)?.value?.let { return@withContext it }

		// Refresh a bit before actual expiry to avoid edge cases.
		val refreshSkewSec = 60
		val ttlMs = ((expiresInSec - refreshSkewSec).coerceAtLeast(30) * 1000L)

		val body = ("{\"expiresIn\":" + expiresInSec + "}")
			.toRequestBody(signJsonMediaType)

		val req = Request.Builder()
			.url(url)
			.post(body)
			.addHeader("apikey", apiKey)
			// Only JWT anon/service_role keys can be used as Bearer tokens.
			// Publishable keys should not be sent as Bearer.
			.apply {
				if (apiKeyLooksJwt) addHeader("Authorization", "Bearer $apiKey")
			}
			.addHeader("Accept", "application/json")
			.build()

		val resp = http.newCall(req).execute()
		resp.use {
			val raw = it.body?.string().orEmpty()
			if (!it.isSuccessful) {
				if (BuildConfig.DEBUG) {
					Log.d(logTag, "Sign failed HTTP ${it.code} path=$path body=${raw.take(300)}")
				}
				// Short negative cache to avoid request storms, but allow recovery.
				cacheSigned(cacheKey, null, ttlMs = 15_000L)
				return@withContext null
			}
			val jo = try {
				gson.fromJson(raw, JsonObject::class.java)
			} catch (_: Throwable) {
				null
			}
			val signed = jo?.get("signedURL")?.asString
				?: jo?.get("signedUrl")?.asString
				?: jo?.get("signed_url")?.asString
			val full = signed?.let { buildSignedUrlFromResponse(it) }
			cacheSigned(cacheKey, full, ttlMs = if (full.isNullOrBlank()) 15_000L else ttlMs)
			return@withContext full
		}
	}

	/**
	 * Resolve an image ID into an actual, fetchable URL.
	 *
	 * - If the bucket is public, [publicImageUrl] may work.
	 * - For private buckets (common), try Storage signed URLs with multiple path candidates.
	 */
	suspend fun resolveImageUrl(imageIdRaw: String?): String? {
		val raw = imageIdRaw?.trim().orEmpty()
		if (raw.isBlank()) return null
		if (raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true)) {
			return raw
		}

		val cacheKey = "img:$raw"
		getFreshEntry(cacheKey)?.let { entry ->
			return entry.value
		}

		val signedImgTtlMs = 55 * 60 * 1000L

		// Prefer signed URLs for private buckets.
		val candidates = resolveCandidatePaths(raw)
		for (p in candidates) {
			val signed = signObjectPath(p)
			if (!signed.isNullOrBlank()) {
				if (BuildConfig.DEBUG) {
					Log.d(logTag, "Resolved imageId=$raw via signed path=$p")
				}
				cacheSigned(cacheKey, signed, ttlMs = signedImgTtlMs)
				return signed
			}
		}

		// Fallback: authenticated URL (works when policies allow anon reads, and our Coil loader adds headers).
		val firstPath = candidates.firstOrNull() ?: return null
		val authUrl = authenticatedImageUrlForPath(firstPath)
		if (BuildConfig.DEBUG) {
			Log.d(logTag, "Resolved imageId=$raw via authenticated path=$firstPath url=$authUrl")
		}
		if (authUrl.isNullOrBlank()) {
			cacheSigned(cacheKey, null, ttlMs = 15_000L)
			return null
		}
		cacheSigned(cacheKey, authUrl, ttlMs = 24 * 60 * 60 * 1000L)
		return authUrl
	}

	fun toCardModels(
		chapter: Int,
		verse: Int,
		words: List<SupabaseAyahWordDto>,
		fallbackArabicWords: List<String>? = null,
	): List<LinguisticsWordCardModel> {
		val mapped = words
			.filter { (it.chapter ?: chapter) == chapter && (it.verse ?: verse) == verse }
			.sortedBy { it.wordNumber ?: Int.MAX_VALUE }
			.mapNotNull { w ->
				val num = w.wordNumber ?: return@mapNotNull null
				val arabicFallback = fallbackArabicWords?.getOrNull(num - 1).orEmpty()
				val arabic = w.arabic?.trim().orEmpty().ifBlank { arabicFallback }
				LinguisticsWordCardModel(
					chapter = chapter,
					verse = verse,
					wordNumber = num,
					arabic = arabic,
					meaning = w.meaning?.trim().orEmpty(),
					pronunciation = w.pronunciation?.trim().orEmpty(),
					arabicGrammar = w.arabicGrammar?.trim().orEmpty(),
					segSky = w.segSky?.trim().orEmpty(),
					segRust = w.segRust?.trim().orEmpty(),
					location = w.location?.trim().orEmpty(),
					imageId = w.imageId?.trim(),
					imageUrl = publicImageUrl(w.imageId),
				)
			}

		// Supabase JSON can sometimes contain duplicates per word_number.
		// Merge duplicates by keeping the first non-blank value for each field.
		fun merge(a: LinguisticsWordCardModel, b: LinguisticsWordCardModel): LinguisticsWordCardModel {
			fun pick(first: String, second: String): String = first.ifBlank { second }
			fun pickId(first: String?, second: String?): String? = first?.takeIf { it.isNotBlank() } ?: second
			fun pickUrl(first: String?, second: String?): String? = first?.takeIf { it.isNotBlank() } ?: second
			return a.copy(
				arabic = pick(a.arabic, b.arabic),
				meaning = pick(a.meaning, b.meaning),
				pronunciation = pick(a.pronunciation, b.pronunciation),
				arabicGrammar = pick(a.arabicGrammar, b.arabicGrammar),
				segSky = pick(a.segSky, b.segSky),
				segRust = pick(a.segRust, b.segRust),
				location = pick(a.location, b.location),
				imageId = pickId(a.imageId, b.imageId),
				imageUrl = pickUrl(a.imageUrl, b.imageUrl),
			)
		}

		val mergedByWord = linkedMapOf<Int, LinguisticsWordCardModel>()
		for (m in mapped) {
			val existing = mergedByWord[m.wordNumber]
			mergedByWord[m.wordNumber] = if (existing == null) m else merge(existing, m)
		}

		return mergedByWord.values.sortedBy { it.wordNumber }
	}

	suspend fun toCardModelsResolvedImages(
		chapter: Int,
		verse: Int,
		words: List<SupabaseAyahWordDto>,
		fallbackArabicWords: List<String>? = null,
	): List<LinguisticsWordCardModel> {
		val baseModels = toCardModels(chapter, verse, words, fallbackArabicWords)
		if (baseModels.isEmpty()) return baseModels
		val resolved = baseModels.map { m ->
			val url = try { resolveImageUrl(m.imageId) } catch (_: Throwable) { null }
			m.copy(imageUrl = url ?: m.imageUrl)
		}
		return resolved
	}
}

private fun JsonArray.firstOrNull(): JsonElement? = if (size() > 0) get(0) else null
private val JsonElement.asJsonObject: JsonObject? get() = if (isJsonObject) asJsonObject else null
