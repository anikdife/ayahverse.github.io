package com.ayahverse.quran.data.tafsir.remote.adapter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AltafsirHtmlExtractorTest {
	@Test
	fun `extracts english tafsir text from SearchResults and strips noise`() {
		val html = """
			<html><body>
				<div id='DispFrame'>
					<div id='SearchResults'>
						<h2 id='AyahMenu'>
							<a href='javascript:ReadAyah_onclick()'>Recite Verse</a>
							<a href='javascript:Open_QurPage(1, 1)'>Open in new window</a>
						</h2>
						<h2 id='AyahText' class='TextAyah'>
							<b>{</b> <a class='TextAyah' href='JavaScript:Open_Menu()'>بِسمِ الله</a> <b>}</b>
						</h2>
						<div align='left' dir='ltr'>
							<font class='TextResultEnglish'><font color='black'>In the Name of God the Compassionate the Merciful</font></font>
							<br><br>
							<hr size='1'>
							<span class='CopyRight'>© 2021 Example (<a href='http://example.com'>link</a>)</span>
						</div>
					</div>
				</div>
			</body></html>
		""".trimIndent()

		val text = AltafsirHtmlExtractor.extractPlainText(html)

		assertTrue(text.contains("In the Name of God", ignoreCase = true))
		assertFalse(text.contains("Recite Verse", ignoreCase = true))
		assertFalse(text.contains("Open in new window", ignoreCase = true))
		assertFalse(text.contains("All Rights Reserved", ignoreCase = true))
	}

	@Test
	fun `adds surah-level note for Asbab when header contains multiple ayahs`() {
		val html = """
			<html><body>
				<div id='DispFrame'>
					<h1>* تفسير Asbab Al-Nuzul by Al-Wahidi</h1>
					<div id='SearchResults'>
						<h2 id='AyahText' class='TextAyah'>{a} * {b} * {c}</h2>
						<div align='left' dir='ltr'>
							<font class='TextResultEnglish'><font color='black'>Surah-level intro text.</font></font>
						</div>
					</div>
				</div>
			</body></html>
		""".trimIndent()

		val text = AltafsirHtmlExtractor.extractPlainText(html)
		assertTrue(text.startsWith("This is all this tafsir has on this surah."))
		assertTrue(text.contains("Surah-level intro text"))
	}

	@Test
	fun `returns empty when Altafsir says no tafsir exists and does not extract dropdown`() {
		val html = """
			<html><body>
				<!-- Simulate the tafsir picker UI that can contain '-Tafsir' -->
				<select id='Tafsir'><option>-Tafsir</option></select>
				<div id='DispFrame'>
					<font class='Msg'><center>No tafsir for this verse exists</center></font>
				</div>
			</body></html>
		""".trimIndent()

		val text = AltafsirHtmlExtractor.extractPlainText(html)
		assertTrue(text.isBlank(), "Expected blank text, got: '$text'")
	}

	@Test
	fun `extracts longer Jalalayn-style body from SearchResults`() {
		val html = """
			<html><body>
				<div id='SearchResults'>
					<h2 id='AyahText'>Ayah header</h2>
					<div align='left' dir='ltr'>
						<font class='TextResultEnglish'>
							<font color='black'>God there is no god that is there is none worthy of being worshipped except Him.</font>
							<br><br>
							<font color='black'>His throne subsumes the heavens and the earth.</font>
						</font>
						<hr size='1'>
						<span class='CopyRight'>© 2021 Example</span>
					</div>
				</div>
			</body></html>
		""".trimIndent()

		val text = AltafsirHtmlExtractor.extractPlainText(html)
		assertTrue(text.contains("God there is no god", ignoreCase = true))
		assertTrue(text.contains("throne subsumes", ignoreCase = true))
		assertFalse(text.contains("© 2021", ignoreCase = true))
	}
}
