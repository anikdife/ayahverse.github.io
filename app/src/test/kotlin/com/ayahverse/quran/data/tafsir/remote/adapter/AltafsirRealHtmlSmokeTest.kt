package com.ayahverse.quran.data.tafsir.remote.adapter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AltafsirRealHtmlSmokeTest {
	@Test
	fun `jalalayn 15 2 extraction returns tafsir text`() {
		val file = File("app/build/tmp/jalalayn_15_2.html")
		if (!file.exists()) return
		val html = file.readText(Charsets.UTF_8)
		val extracted = AltafsirHtmlExtractor.extractPlainText(html)
		assertTrue(extracted.length > 80, "Expected extracted text to be long, got len=${extracted.length}")
		assertTrue(extracted.contains("It may be", ignoreCase = true))
		assertFalse(extracted.trim().equals("-Tafsir", ignoreCase = true))
	}
}
