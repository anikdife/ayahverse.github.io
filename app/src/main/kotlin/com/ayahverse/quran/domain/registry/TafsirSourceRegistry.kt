package com.ayahverse.quran.domain.registry

import com.ayahverse.quran.core.util.detectProviderType
import com.ayahverse.quran.core.util.normalizeLanguageCode
import com.ayahverse.quran.domain.model.tafsir.TafsirSource

object TafsirSourceRegistry {
	private val sources: List<TafsirSource> = buildList {
		// Arabic
		add(source("ar-tafsir-ibn-kathir", 14, "Hafiz Ibn Kathir", "arabic", "Tafsir Ibn Kathir", "https://quran.com/", 10))
		add(source("ar-tafsir-al-tabari", 15, "Tabari", "arabic", "Tafsir al-Tabari", "https://quran.com/", 20))
		add(source("ar-tafseer-al-qurtubi", 90, "Qurtubi", "arabic", "Tafseer Al Qurtubi", "https://quran.com/", 30))
		add(source("ar-tafsir-muyassar", 16, "Al Muyassar", "arabic", "Tafsir Muyassar", "https://quran.com/", 40))
		add(source("ar-tafsir-al-wasit", 93, "Waseet", "arabic", "Tafsir Al Wasit", "https://quran.com/", 50))
		add(source("ar-tafsir-al-baghawi", 94, "Baghawy", "arabic", "Tafseer Al-Baghawi", "https://quran.com/", 60))
		add(source("ar-tafseer-tanwir-al-miqbas", 92, "Tanweer", "arabic", "Tafseer Tanwir al-Miqbas", "https://quran.com/", 70))
		add(source("ar-tafseer-al-saddi", 91, "Saddi", "arabic", "Tafseer Al Saddi", "https://quran.com/", 80))

		// Bengali
		add(source("bn-tafseer-ibn-e-kaseer", 164, "Tawheed Publication", "bengali", "Tafseer ibn Kathir", "https://quran.com/", 10))
		add(source("bn-tafsir-ahsanul-bayaan", 165, "Bayaan Foundation", "bengali", "Tafsir Ahsanul Bayaan", "https://quran.com/", 20))
		add(source("bn-tafsir-abu-bakr-zakaria", 166, "King Fahd Quran Printing Complex", "bengali", "Tafsir Abu Bakr Zakaria", "https://quran.com/", 30))
		add(source("bn-tafisr-fathul-majid", 381, "AbdulRahman Bin Hasan Al-Alshaikh", "bengali", "Tafsir Fathul Majid", "https://quran.com/", 40))

		// English (quran.com)
		add(source("en-tafisr-ibn-kathir", 169, "Hafiz Ibn Kathir", "english", "Tafsir Ibn Kathir (abridged)", "https://quran.com/", 10))
		add(source("en-tafsir-maarif-ul-quran", 168, "Mufti Muhammad Shafi", "english", "Maarif-ul-Quran", "https://quran.com/", 20))
		add(source("en-tazkirul-quran", 817, "Maulana Wahid Uddin Khan", "english", "Tazkirul Quran(Maulana Wahiduddin Khan)", "https://quran.com/", 60))

		// English (altafsir.com)
		add(source("en-al-jalalayn", 74, "Al-Jalalayn", "english", "Al-Jalalayn", "https://www.altafsir.com/", 30))
		add(source("en-tafsir-ibn-abbas", 73, "Tanwir al-Miqbas min Tafsir Ibn Abbas", "english", "Tanwir al-Miqbas min Tafsir Ibn Abbas", "https://www.altafsir.com/", 40))
		add(source("en-asbab-al-nuzul-by-al-wahidi", 86, "Asbab Al-Nuzul by Al-Wahidi", "english", "Asbab Al-Nuzul by Al-Wahidi", "https://www.altafsir.com/", 50))
		add(source("en-kashani-tafsir", 107, "Kashani Tafsir", "english", "Kashani Tafsir", "https://www.altafsir.com/", 70))
		add(source("en-al-qushairi-tafsir", 108, "Al Qushairi Tafsir", "english", "Al Qushairi Tafsir", "https://www.altafsir.com/", 80))
		add(source("en-kashf-al-asrar-tafsir", 109, "Kashf Al-Asrar Tafsir", "english", "Kashf Al-Asrar Tafsir", "https://www.altafsir.com/", 90))
		add(source("en-tafsir-al-tustari", 93, "Tafsir al-Tustari", "english", "Tafsir al-Tustari", "https://www.altafsir.com/", 100))

		// Kurdish
		add(source("kurd-tafsir-rebar", 804, "Rebar Kurdish Tafsir", "Kurdish", "Rebar Kurdish Tafsir", "https://quran.com/", 10))

		// Russian
		add(source("ru-tafseer-al-saddi", 170, "Saddi", "russian", "Tafseer Al Saddi", "https://quran.com/", 10))

		// Urdu
		add(source("ur-tafseer-ibn-e-kaseer", 160, "Hafiz Ibn Kathir", "urdu", "Tafsir Ibn Kathir", "https://quran.com/", 10))
		add(source("ur-tafsir-bayan-ul-quran", 159, "Dr. Israr Ahmad", "urdu", "Tafsir Bayan ul Quran", "https://quran.com/", 20))
		add(source("ur-tazkirul-quran", 818, "Maulana Wahid Uddin Khan", "urdu", "Tazkirul Quran(Maulana Wahiduddin Khan)", "https://quran.com/", 30))

		// Language-specific (non-quran.com) tafsir mappings
		// Urdu (AlQuranCloud)
		add(source("ur.tafsir-as-saadi", 0, "As-Saadi", "urdu", "Tafsir As-Saadi", "https://api.alquran.cloud/v1/", 40))
		// Turkish (QuranEnc)
		add(source("turkish_shaban", 0, "QuranEnc", "turkish", "Turkish Tafsir (Shaban)", "https://quranenc.com/api/v1/", 10))
		// Indonesian (QuranEnc)
		add(source("indonesian_complex", 0, "QuranEnc", "indonesian", "Indonesian Tafsir (Complex)", "https://quranenc.com/api/v1/", 10))
		// French (AlQuranCloud)
		add(source("fr.tafsir-abridged", 0, "Abridged", "french", "French Tafsir (Abridged)", "https://api.alquran.cloud/v1/", 10))
		// Spanish/German/Hindi (FawazAhmed editions)
		add(source("spa-muhammadisagarc", 0, "FawazAhmed", "spanish", "Spanish Tafsir", "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/", 10))
		add(source("deu-aburidamuhammad", 0, "FawazAhmed", "german", "German Tafsir", "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/", 10))
		add(source("hin-suhelfarooqkhan", 0, "FawazAhmed", "hindi", "Hindi Tafsir", "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/", 10))
		// Chinese (QuranEnc)
		add(source("chinese_makin", 0, "QuranEnc", "chinese", "Chinese Tafsir (Makin)", "https://quranenc.com/api/v1/", 10))
		add(source("chinese_suliman", 0, "QuranEnc", "chinese", "Chinese Tafsir (Sulaiman)", "https://quranenc.com/api/v1/", 20))
		// Russian (QuranEnc)
		add(source("russian_kuliev", 0, "QuranEnc", "russian", "Russian Tafsir (Kuliev)", "https://quranenc.com/api/v1/", 20))
	}

	private fun source(
		slug: String,
		legacyId: Int,
		authorName: String,
		languageName: String,
		displayName: String,
		sourceUrl: String,
		defaultPriority: Int,
		isEnabled: Boolean = true,
	): TafsirSource {
		val languageCode = normalizeLanguageCode(languageName)
		val provider = detectProviderType(sourceUrl)
		val languageDisplay = languageName.replaceFirstChar { it.uppercase() }
		return TafsirSource(
			slug = slug,
			legacyId = legacyId,
			authorName = authorName,
			languageCode = languageCode,
			languageDisplayName = languageDisplay,
			displayName = displayName,
			sourceUrl = sourceUrl,
			providerType = provider,
			defaultPriority = defaultPriority,
			isEnabled = isEnabled,
		)
	}

	fun allSources(): List<TafsirSource> = sources

	fun getBySlug(slug: String): TafsirSource? = sources.firstOrNull { it.slug.equals(slug, ignoreCase = true) }

	fun getByLanguage(languageCode: String): List<TafsirSource> {
		val code = normalizeLanguageCode(languageCode)
		return sources
			.filter { it.isEnabled }
			.filter { it.languageCode == code }
			.sortedWith(compareBy<TafsirSource> { it.defaultPriority }.thenBy { it.displayName })
	}
}
