package com.ayahverse.quran.linguistics.supabase

import android.content.Context
import android.util.Log
import coil.ImageLoader
import com.ayahverse.quran.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

object SupabaseCoil {
	@Volatile
	private var cachedLoader: ImageLoader? = null
	private const val logTag = "SupabaseCoil"

	fun imageLoader(context: Context): ImageLoader {
		val existing = cachedLoader
		if (existing != null) return existing

		synchronized(this) {
			val again = cachedLoader
			if (again != null) return again

			val supabaseHost = BuildConfig.SUPABASE_URL.trim().toHttpUrlOrNull()?.host
			val apiKey = BuildConfig.SUPABASE_ANON_KEY.trim()
				val apiKeyLooksJwt = apiKey.startsWith("eyJ") && apiKey.length > 100

			val okHttp = OkHttpClient.Builder()
				.addInterceptor { chain ->
					val req = chain.request()
					val shouldAdd = !supabaseHost.isNullOrBlank() && req.url.host == supabaseHost && apiKey.isNotBlank()
					val next = if (shouldAdd) {
						val builder = req.newBuilder()
							.header("apikey", apiKey)
						// Only JWT anon/service_role keys can be used as Bearer tokens.
						// Publishable keys should NOT be sent as Bearer.
						if (apiKeyLooksJwt) {
							builder.header("Authorization", "Bearer $apiKey")
						}
						builder.build()
					} else {
						req
					}

					val urlStr = req.url.toString()
					val isStorage = urlStr.contains("/storage/v1/", ignoreCase = true)
					if (BuildConfig.DEBUG && shouldAdd && isStorage) {
						Log.d(logTag, "REQ ${req.method} $urlStr")
					}

					val resp = chain.proceed(next)
					if (BuildConfig.DEBUG && shouldAdd && isStorage) {
						val extra = if (resp.code >= 400) {
							val body = runCatching { resp.peekBody(2048).string() }.getOrNull().orEmpty()
							if (body.isNotBlank()) " body=${body.take(500)}" else ""
						} else {
							""
						}
						Log.d(logTag, "RESP ${resp.code} $urlStr$extra")
					}
					resp
				}
				.build()

			val loader = ImageLoader.Builder(context.applicationContext)
				.okHttpClient(okHttp)
				.build()

			cachedLoader = loader
			return loader
		}
	}
}
