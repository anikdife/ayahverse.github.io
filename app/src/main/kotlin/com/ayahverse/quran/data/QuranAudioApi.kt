package com.ayahverse.quran.data

/**
 * Small helper around [QuranApi] that exposes audio/recitation functionality.
 *
 * Note: Quran.com API returns *relative* audio paths (e.g. "Alafasy/mp3/001001.mp3").
 * The [audioBaseUrl] is used to convert those into absolute URLs.
 */
class QuranAudioApi(
	private val api: QuranApi,
	private val audioBaseUrl: String = DEFAULT_AUDIO_BASE_URL,
) {
	suspend fun listReciters(): List<Reciter> {
		return api.getRecitations().recitations.map { dto ->
			Reciter(
				id = dto.id,
				name = dto.reciterName,
				style = dto.style,
			)
		}
	}

	/** Returns the first audio file returned for this verse key, or null if none is available. */
	suspend fun getAyahAudio(recitationId: Int, verseKey: String): AyahAudio? {
		val response = api.getRecitationAudioByVerseKey(recitationId = recitationId, verseKey = verseKey)
		val audio = response.audioFiles.firstOrNull { it.verseKey == verseKey } ?: response.audioFiles.firstOrNull()
		return audio?.let {
			AyahAudio(
				verseKey = it.verseKey,
				relativeUrl = it.url,
				absoluteUrl = resolveAudioUrl(it.url),
			)
		}
	}

	private fun resolveAudioUrl(relativeOrAbsolute: String): String {
		// Quran.com API sometimes returns protocol-relative URLs (e.g. "//mirrors.quranicaudio.com/..."),
		// which must be normalized to an absolute URL for MediaPlayer.
		if (relativeOrAbsolute.startsWith("//")) {
			return "https:$relativeOrAbsolute"
		}
		if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
			return relativeOrAbsolute
		}
		val base = if (audioBaseUrl.endsWith('/')) audioBaseUrl else "$audioBaseUrl/"
		return base + relativeOrAbsolute.trimStart('/')
	}

	data class Reciter(
		val id: Int,
		val name: String,
		val style: String?,
	)

	data class AyahAudio(
		val verseKey: String,
		val relativeUrl: String,
		val absoluteUrl: String,
	)

	companion object {
		/** Base used for Quran.com recitation MP3 files. Override if needed. */
		const val DEFAULT_AUDIO_BASE_URL: String = "https://verses.quran.com/"
	}
}
