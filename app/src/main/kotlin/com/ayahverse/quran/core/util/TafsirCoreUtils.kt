package com.ayahverse.quran.core.util

import com.ayahverse.quran.domain.model.tafsir.TafsirProviderType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.security.MessageDigest

private val supportedLanguageCodes = setOf(
	"ar",
	"bn",
	"en",
	"ku",
	"ru",
	"ur",
	"tr",
	"id",
	"fr",
	"es",
	"de",
	"zh",
	"hi",
)

fun normalizeLanguageCode(input: String?): String {
	val raw = input?.trim()?.lowercase().orEmpty()
	if (raw.isBlank()) return "en"

	return when (raw) {
		"ar", "arabic", "عربي", "العربية" -> "ar"
		"bn", "bangla", "bengali", "বাংলা" -> "bn"
		"en", "english" -> "en"
		"ku", "kurd", "kurdish" -> "ku"
		"ru", "russian", "русский" -> "ru"
		"ur", "urdu", "اردو" -> "ur"
		"tr", "turkish", "türkçe", "turkce" -> "tr"
		"id", "indonesian", "bahasa indonesia", "bahasa" -> "id"
		"fr", "french", "français", "francais" -> "fr"
		"es", "spanish", "español", "espanol" -> "es"
		"de", "german", "deutsch" -> "de"
		"zh", "chinese", "中文", "汉语", "漢語" -> "zh"
		"hi", "hindi", "हिन्दी", "हिंदी" -> "hi"
		else -> raw.takeIf { it in supportedLanguageCodes }
			?: raw.take(2).takeIf { it in supportedLanguageCodes }
			?: "en"
	}
}

fun detectProviderType(sourceUrl: String?): TafsirProviderType {
	val url = sourceUrl?.trim()?.lowercase().orEmpty()
	return when {
		url.contains("altafsir.com") -> TafsirProviderType.ALTAFSIR
		url.contains("quran.com") || url.contains("api.quran.com") -> TafsirProviderType.QURAN_COM
		url.contains("api.alquran.cloud") -> TafsirProviderType.QURAN_CLOUD
		url.contains("quranenc.com") -> TafsirProviderType.QURAN_ENC
		url.contains("fawazahmed0") && url.contains("quran-api") -> TafsirProviderType.FAWAZ_AHMED
		else -> TafsirProviderType.OTHER
	}
}

fun sha256Hex(input: String): String {
	val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
	return digest.joinToString(separator = "") { b -> "%02x".format(b) }
}

/**
 * Converts HTML-ish tafsir payloads to readable plain text while preserving paragraph/newline structure.
 */
fun sanitizeTafsirToPlainText(raw: String): String {
	if (raw.isBlank()) return ""

	// Fast path for already-plain text.
	val looksLikeHtml = raw.contains('<') && raw.contains('>')
	if (!looksLikeHtml) return raw.trim()

	val doc = Jsoup.parse(raw)
	doc.outputSettings().prettyPrint(false)
	stripNonContent(doc)

	// Preserve line breaks.
	doc.select("br").append("\\n")
	doc.select("p").prepend("\\n\\n")
	doc.select("div").prepend("\\n\\n")

	val text = doc.body()?.wholeText().orEmpty().ifBlank { doc.wholeText() }
	return text
		.replace("\u00A0", " ")
		.replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
		.replace(Regex("\\n{3,}"), "\\n\\n")
		.trim()
}

private fun stripNonContent(doc: Document) {
	doc.select("script,style,noscript").remove()
}
