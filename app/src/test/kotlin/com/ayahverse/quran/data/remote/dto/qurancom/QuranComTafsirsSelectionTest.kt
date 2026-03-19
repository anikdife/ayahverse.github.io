package com.ayahverse.quran.data.remote.dto.qurancom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuranComTafsirsSelectionTest {
	@Test
	fun `findTextForResource returns matching item text`() {
		val dto = QuranComTafsirsResponseDto(
			tafsirs = listOf(
				QuranComTafsirItemDto(resourceId = 171, text = "english"),
				QuranComTafsirItemDto(resourceId = 166, text = "bengali"),
			),
		)

		assertEquals("bengali", dto.findTextForResource(166))
	}

	@Test
	fun `findTextForResource returns null when missing or blank`() {
		val dto = QuranComTafsirsResponseDto(
			tafsirs = listOf(
				QuranComTafsirItemDto(resourceId = 171, text = "english"),
				QuranComTafsirItemDto(resourceId = 166, text = "\n  \t"),
			),
		)

		assertNull(dto.findTextForResource(164))
		assertNull(dto.findTextForResource(166))
	}
}
