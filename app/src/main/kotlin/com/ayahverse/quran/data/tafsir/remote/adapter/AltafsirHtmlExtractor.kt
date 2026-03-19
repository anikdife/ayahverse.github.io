package com.ayahverse.quran.data.tafsir.remote.adapter

import com.ayahverse.quran.core.util.sanitizeTafsirToPlainText
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object AltafsirHtmlExtractor {
	fun extractPlainText(html: String): String {
		if (html.isBlank()) return ""
		val doc = Jsoup.parse(html).apply {
			outputSettings().prettyPrint(false)
		}
		doc.select("script,style,noscript").remove()

		// Some Altafsir pages explicitly state there's no tafsir for the verse.
		// In that case, avoid heuristics that might accidentally extract the tafsir picker UI.
		val dispFrame = doc.getElementById("DispFrame")
		if (dispFrame != null && dispFrame.selectFirst("#SearchResults") == null) {
			val msg = dispFrame.text().trim()
			if (msg.contains("No tafsir for this verse exists", ignoreCase = true)) {
				return ""
			}
		}

		// Altafsir payloads commonly include a stable content container.
		// Prefer it over heuristics to avoid accidentally extracting the tafsir picker UI.
		extractFromSearchResults(doc)?.let { extracted ->
			val plain = sanitizeTafsirToPlainText(extracted.html)
			return if (extracted.isSurahLevelAsbab) {
				SURAH_LEVEL_NOTE + "\n\n" + plain
			} else {
				plain
			}
		}

		// Fallback: remove common navigation clutter and pick a reasonable content block.
		doc.select("header,footer,nav,form").remove()
		doc.select("a").remove()
		// Drop down controls can contain short labels like "-Tafsir" and win the heuristic.
		doc.select("select,option").remove()

		val candidate = bestCandidate(doc.body())
		val candidateHtml = candidate?.html() ?: doc.body()?.html().orEmpty()
		return sanitizeTafsirToPlainText(candidateHtml)
	}

	private data class SearchResultsExtraction(
		val html: String,
		val isSurahLevelAsbab: Boolean,
	)

	private fun extractFromSearchResults(doc: Document): SearchResultsExtraction? {
		val searchResults = doc.getElementById("SearchResults") ?: doc.selectFirst("#SearchResults")
			?: return null

		val isAsbab = doc.text().contains("Asbab Al-Nuzul", ignoreCase = true) || doc.text().contains("Al-Wahidi", ignoreCase = true)
		val ayahText = searchResults.selectFirst("#AyahText")
		// On Altafsir, Asbab pages sometimes show a surah-level article while rendering multiple ayahs in the header.
		val isSurahLevelAsbab = isAsbab && (ayahText?.text()?.contains('*') == true)

		val cleaned = searchResults.clone()
		// Remove the ayah menu/links and the ayah text header.
		cleaned.select("#AyahMenu").remove()
		cleaned.select("#AyahText").remove()
		// Remove copyright/license block.
		cleaned.select(".CopyRight").remove()
		// Remove anchor tags to avoid including navigation link labels.
		cleaned.select("a").remove()

		val content = cleaned.selectFirst(".TextResultEnglish")
			?: cleaned.selectFirst("font.TextResultEnglish")
			?: cleaned
		val html = content.html().trim()
		return html.takeIf { it.isNotBlank() }?.let { SearchResultsExtraction(it, isSurahLevelAsbab) }
	}

	private fun bestCandidate(body: Element?): Element? {
		if (body == null) return null
		val blocks = body.select("td,div")
		if (blocks.isEmpty()) return null
		return blocks
			.asSequence()
			.map { it to score(it) }
			.filter { (_, s) -> s > 0 }
			.maxByOrNull { it.second }
			?.first
	}

	private fun score(el: Element): Int {
		val text = el.text().trim()
		if (text.length < 80) return 0
		// Prefer blocks that look like the tafsir header.
		val bonus = when {
			text.contains("Tafsir", ignoreCase = true) -> 500
			text.contains("تفسير") -> 500
			else -> 0
		}
		// Prefer long-ish content blocks but avoid pages of menus.
		return text.length.coerceAtMost(20000) + bonus
	}

	private const val SURAH_LEVEL_NOTE = "This is all this tafsir has on this surah."
}
