package com.ayahverse.quran.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

internal data class ListenSurahItem(
	val surahNumber: Int,
	val label: String,
	val ayahCount: Int,
)

internal data class ListenModeSettings(
	val surahNumber: Int,
	val reciterId: Int,
	val startAyah: Int,
	val endAyah: Int,
	val rangeRepetition: Int,
	val ayahRepetition: Int,
	val translatedAudioEnabled: Boolean,
	val translatedLanguageLabel: String,
)

internal data class ListenReciterItem(
	val id: Int,
	val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListenModePanel(
	surahItems: List<ListenSurahItem>,
	reciterItems: List<ListenReciterItem>,
	selectedReciterId: Int,
	onReciterSelected: (Int) -> Unit,
	languageLabels: List<String>,
	initialSurahNumber: Int,
	isPlaying: Boolean,
	onPlayToggle: (Boolean, ListenModeSettings) -> Unit,
	modifier: Modifier = Modifier,
) {
	val gold = Color(0xFFD4AF37) // matches existing app palette (212,175,55)
	val panelShape = RoundedCornerShape(22.dp)

	var selectedSurahNumber by rememberSaveable { mutableIntStateOf(initialSurahNumber.coerceAtLeast(1)) }
	val selectedSurah = surahItems.firstOrNull { it.surahNumber == selectedSurahNumber } ?: surahItems.firstOrNull()
	val ayahMax = (selectedSurah?.ayahCount ?: 1).coerceAtLeast(1)

	var rangeStart by rememberSaveable { mutableIntStateOf(1) }
	var rangeEnd by rememberSaveable { mutableIntStateOf(ayahMax) }

	LaunchedEffect(selectedSurahNumber, ayahMax) {
		if (rangeStart < 1) rangeStart = 1
		if (rangeStart > ayahMax) rangeStart = 1
		if (rangeEnd < 1) rangeEnd = ayahMax
		if (rangeEnd > ayahMax) rangeEnd = ayahMax
		if (rangeEnd < rangeStart) rangeEnd = rangeStart
	}

	var rangeRepetitionValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
		mutableStateOf(TextFieldValue("1", selection = TextRange(1)))
	}
	var ayahRepetitionValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
		mutableStateOf(TextFieldValue("1", selection = TextRange(1)))
	}
	var translatedAudioEnabled by rememberSaveable { mutableStateOf(false) }
	var selectedLanguageLabel by rememberSaveable { mutableStateOf("English") }

	val safeLanguageLabels = remember(languageLabels) {
		if (languageLabels.any { it.equals("English", ignoreCase = true) }) {
			languageLabels
		} else {
			listOf("English") + languageLabels
		}
	}

	fun parsePositiveInt(text: String, fallback: Int = 1): Int {
		return text.trim().toIntOrNull()?.coerceAtLeast(1) ?: fallback
	}

	val settings = ListenModeSettings(
		surahNumber = selectedSurahNumber,
		reciterId = selectedReciterId,
		startAyah = rangeStart.coerceIn(1, ayahMax),
		endAyah = rangeEnd.coerceIn(1, ayahMax),
		rangeRepetition = parsePositiveInt(rangeRepetitionValue.text, 1),
		ayahRepetition = parsePositiveInt(ayahRepetitionValue.text, 1),
		translatedAudioEnabled = translatedAudioEnabled,
		translatedLanguageLabel = selectedLanguageLabel,
	)

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 18.dp, vertical = 22.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Card(
			shape = panelShape,
			colors = CardDefaults.cardColors(containerColor = Color(0x33000000)),
			modifier = Modifier
				.fillMaxWidth()
				.border(width = 1.dp, color = Color(0x55FFFFFF), shape = panelShape),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Text(
					text = "Listen",
					style = MaterialTheme.typography.titleLarge,
					color = Color.White,
				)

				// 1) Surah dropdown
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
					Text(
						text = "Surah",
						color = Color.White,
						modifier = Modifier.width(92.dp),
					)
					SurahDropdown(
						surahItems = surahItems,
						selectedSurahNumber = selectedSurahNumber,
						onSelectSurahNumber = { selectedSurahNumber = it },
						modifier = Modifier.weight(1f),
					)
				}

				// 1b) Reciter dropdown
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
					Text(
						text = "Reciter",
						color = Color.White,
						modifier = Modifier.width(92.dp),
					)
					ReciterDropdown(
						reciterItems = reciterItems,
						selectedReciterId = selectedReciterId,
						onSelectReciterId = onReciterSelected,
						modifier = Modifier.weight(1f),
					)
				}

				// 2) Ayah range slider
				Column(modifier = Modifier.fillMaxWidth()) {
					Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
						Text(
							text = "Ayah range",
							color = Color.White,
							modifier = Modifier.width(92.dp),
						)
						Text(
							text = "$rangeStart – $rangeEnd / $ayahMax",
							color = gold,
						)
					}
					RangeSlider(
						value = rangeStart.toFloat()..rangeEnd.toFloat(),
						onValueChange = { r ->
							rangeStart = r.start.toInt().coerceIn(1, ayahMax)
							rangeEnd = r.endInclusive.toInt().coerceIn(1, ayahMax)
							if (rangeEnd < rangeStart) rangeEnd = rangeStart
						},
						valueRange = 1f..ayahMax.toFloat(),
						steps = (ayahMax - 2).coerceAtLeast(0),
					)
				}

				// 3) Range repetition
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
					Text("Range repetition", color = Color.White, modifier = Modifier.width(140.dp))
					OutlinedTextField(
						value = rangeRepetitionValue,
						onValueChange = { next ->
							val filtered = next.text.filter { ch -> ch.isDigit() }.take(4)
							rangeRepetitionValue = TextFieldValue(filtered, selection = TextRange(filtered.length))
						},
						singleLine = true,
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
							focusedTextColor = Color.White,
							unfocusedTextColor = Color.White,
							focusedBorderColor = gold,
							unfocusedBorderColor = Color(0x55FFFFFF),
							cursorColor = gold,
						),
						modifier = Modifier
							.weight(1f)
							.onFocusChanged { state ->
								if (!state.isFocused && rangeRepetitionValue.text.isBlank()) {
									rangeRepetitionValue = TextFieldValue("1", selection = TextRange(1))
								}
							},
					)
				}

				// 4) Ayah repetition
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
					Text("Ayah repetition", color = Color.White, modifier = Modifier.width(140.dp))
					OutlinedTextField(
						value = ayahRepetitionValue,
						onValueChange = { next ->
							val filtered = next.text.filter { ch -> ch.isDigit() }.take(4)
							ayahRepetitionValue = TextFieldValue(filtered, selection = TextRange(filtered.length))
						},
						singleLine = true,
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
							focusedTextColor = Color.White,
							unfocusedTextColor = Color.White,
							focusedBorderColor = gold,
							unfocusedBorderColor = Color(0x55FFFFFF),
							cursorColor = gold,
						),
						modifier = Modifier
							.weight(1f)
							.onFocusChanged { state ->
								if (!state.isFocused && ayahRepetitionValue.text.isBlank()) {
									ayahRepetitionValue = TextFieldValue("1", selection = TextRange(1))
								}
							},
					)
				}

				// 5) Translated audio toggle + language
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("Translated audio", color = Color.White, modifier = Modifier.weight(1f))
					Switch(checked = translatedAudioEnabled, onCheckedChange = { translatedAudioEnabled = it })
				}

				if (translatedAudioEnabled) {
					Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
						Text("Language", color = Color.White, modifier = Modifier.width(92.dp))
						LanguageDropdown(
							languageLabels = safeLanguageLabels,
							selectedLabel = selectedLanguageLabel,
							onSelectLabel = { selectedLanguageLabel = it },
							modifier = Modifier.weight(1f),
						)
					}
				}

				Spacer(modifier = Modifier.height(2.dp))

				// 6) Play toggle button (stunning icon)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically,
				) {
					val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
					val hint = if (isPlaying) "Stop" else "Play"
					androidx.compose.material3.IconButton(
						onClick = { onPlayToggle(!isPlaying, settings) },
						modifier = Modifier
							.width(76.dp)
							.height(76.dp)
							.border(width = 1.dp, color = Color(0x66FFFFFF), shape = RoundedCornerShape(22.dp)),
					) {
						androidx.compose.material3.Icon(
							painter = painterResource(id = iconRes),
							contentDescription = hint,
							tint = if (isPlaying) Color.White else gold,
							modifier = Modifier
								.width(44.dp)
								.height(44.dp),
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahDropdown(
	surahItems: List<ListenSurahItem>,
	selectedSurahNumber: Int,
	onSelectSurahNumber: (Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	val gold = Color(0xFFD4AF37)
	var expanded by remember { mutableStateOf(false) }
	val selectedLabel = surahItems.firstOrNull { it.surahNumber == selectedSurahNumber }?.label
		?: surahItems.firstOrNull()?.label.orEmpty()

	ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
		OutlinedTextField(
			value = selectedLabel,
			onValueChange = {},
			readOnly = true,
			singleLine = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
				focusedBorderColor = gold,
				unfocusedBorderColor = Color(0x55FFFFFF),
				focusedTextColor = Color.White,
				unfocusedTextColor = Color.White,
			),
			modifier = Modifier
				.menuAnchor()
				.fillMaxWidth(),
		)
		ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			surahItems.forEach { item ->
				androidx.compose.material3.DropdownMenuItem(
					text = { Text(item.label) },
					onClick = {
						onSelectSurahNumber(item.surahNumber)
						expanded = false
					},
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
	languageLabels: List<String>,
	selectedLabel: String,
	onSelectLabel: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val gold = Color(0xFFD4AF37)
	var expanded by remember { mutableStateOf(false) }
	val label = languageLabels.firstOrNull { it.equals(selectedLabel, ignoreCase = true) } ?: selectedLabel

	ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
		OutlinedTextField(
			value = label,
			onValueChange = {},
			readOnly = true,
			singleLine = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
				focusedBorderColor = gold,
				unfocusedBorderColor = Color(0x55FFFFFF),
				focusedTextColor = Color.White,
				unfocusedTextColor = Color.White,
			),
			modifier = Modifier
				.menuAnchor()
				.fillMaxWidth(),
		)
		ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			languageLabels.forEach { item ->
				androidx.compose.material3.DropdownMenuItem(
					text = { Text(item) },
					onClick = {
						onSelectLabel(item)
						expanded = false
					},
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReciterDropdown(
	reciterItems: List<ListenReciterItem>,
	selectedReciterId: Int,
	onSelectReciterId: (Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	val gold = Color(0xFFD4AF37)
	var expanded by remember { mutableStateOf(false) }
	val selectedLabel = reciterItems.firstOrNull { it.id == selectedReciterId }?.label
		?: reciterItems.firstOrNull()?.label.orEmpty()

	ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
		OutlinedTextField(
			value = selectedLabel.ifBlank { "Select" },
			onValueChange = {},
			readOnly = true,
			singleLine = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
				focusedBorderColor = gold,
				unfocusedBorderColor = Color(0x55FFFFFF),
				focusedTextColor = Color.White,
				unfocusedTextColor = Color.White,
			),
			modifier = Modifier
				.menuAnchor()
				.fillMaxWidth(),
		)
		ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			reciterItems.forEach { item ->
				androidx.compose.material3.DropdownMenuItem(
					text = { Text(item.label) },
					onClick = {
						onSelectReciterId(item.id)
						expanded = false
					},
				)
			}
		}
	}
}
