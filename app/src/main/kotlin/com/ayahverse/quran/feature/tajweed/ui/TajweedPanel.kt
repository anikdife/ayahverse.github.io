package com.ayahverse.quran.feature.tajweed.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayahverse.quran.feature.tajweed.domain.TajweedStatus
import com.ayahverse.quran.feature.tajweed.viewmodel.TajweedViewModel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * Phase-1 MVP UI: selection + reference play + record/stop + result panel.
 * The parent container provides the starfield backdrop.
 */
data class TajweedReciterItem(
	val id: Int,
	val label: String,
)

data class TajweedSurahItem(
	val surahNumber: Int,
	val label: String,
	val ayahCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TajweedPanel(
	modifier: Modifier = Modifier,
	viewModel: TajweedViewModel = viewModel(),
	surahItems: List<TajweedSurahItem>,
	reciters: List<TajweedReciterItem>,
	loadExpectedAyahText: suspend (surahNumber: Int, ayahNumber: Int) -> String?,
	resolveReferenceUrl: suspend (reciterId: Int, surahNumber: Int, ayahNumber: Int) -> String?,
) {
	val uiState by viewModel.uiState.collectAsState()
	val scope = rememberCoroutineScope()
	val ctx = LocalContext.current
	val gold = Color(0xFFD4AF37)

	val resolvedSurahItems = surahItems.ifEmpty {
		List(114) { i ->
			TajweedSurahItem(
				surahNumber = i + 1,
				label = (i + 1).toString(),
				ayahCount = 300,
			)
		}
	}

	var surah by remember(uiState.selectedSurahNumber) { mutableIntStateOf(uiState.selectedSurahNumber) }
	var ayah by remember(uiState.selectedAyahNumber) { mutableIntStateOf(uiState.selectedAyahNumber) }
	val maxAyah = resolvedSurahItems.firstOrNull { it.surahNumber == surah }?.ayahCount?.coerceAtLeast(1) ?: 1
	if (ayah > maxAyah) ayah = maxAyah

	val selectedReciterId = uiState.selectedReciterId
	val selectedReciterLabel = reciters.firstOrNull { it.id == selectedReciterId }?.label
		?: reciters.firstOrNull()?.label
		?: ""
	val effectiveReciterId = uiState.selectedReciterId.takeIf { it > 0 } ?: reciters.firstOrNull()?.id ?: -1
	var reciterExpanded by remember { mutableStateOf(false) }
	var surahExpanded by remember { mutableStateOf(false) }
	var ayahExpanded by remember { mutableStateOf(false) }
	val isBusy = uiState.status == TajweedStatus.RECORDING || uiState.status == TajweedStatus.ANALYZING
	val isModelLoading = uiState.status == TajweedStatus.MODEL_LOADING || uiState.isLoadingModel || !uiState.isReady

	fun commitSelection(newSurah: Int, newAyah: Int) {
		val reciterId = effectiveReciterId
		if (reciterId <= 0) return
		surah = newSurah.coerceIn(1, 114)
		ayah = newAyah.coerceAtLeast(1)
		viewModel.setSelection(surahNumber = surah, ayahNumber = ayah, reciterId = reciterId)
	}

	fun goNextAyah() {
		if (isBusy) return
		val next = if (ayah < maxAyah) {
			surah to (ayah + 1)
		} else if (surah < 114) {
			(surah + 1) to 1
		} else {
			surah to ayah
		}
		commitSelection(next.first, next.second)
	}

	fun goPrevAyah() {
		if (isBusy) return
		val prev = if (ayah > 1) {
			surah to (ayah - 1)
		} else if (surah > 1) {
			val prevSurah = surah - 1
			val prevMaxAyah = resolvedSurahItems.firstOrNull { it.surahNumber == prevSurah }?.ayahCount?.coerceAtLeast(1) ?: 1
			prevSurah to prevMaxAyah
		} else {
			surah to ayah
		}
		commitSelection(prev.first, prev.second)
	}

	// Ensure we always have a reciter selected (so reference play works immediately).
	LaunchedEffect(reciters) {
		if (uiState.selectedReciterId <= 0 && reciters.isNotEmpty()) {
			viewModel.setSelection(surahNumber = surah, ayahNumber = ayah, reciterId = reciters.first().id)
		}
	}

	// Auto-load expected Arabic whenever surah/ayah changes.
	LaunchedEffect(surah, ayah) {
		val text = runCatching { loadExpectedAyahText(surah, ayah) }.getOrNull()
		if (!text.isNullOrBlank()) viewModel.setExpectedAyahText(text)
	}

	// If score is 100%, auto-advance to the next ayah.
	var lastAutoAdvanceKey by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(uiState.status, uiState.result?.overallScore, surah, ayah, uiState.recognizedText) {
		val score = uiState.result?.overallScore
		if (uiState.status != TajweedStatus.COMPLETED || score != 100) return@LaunchedEffect
		val key = "$surah:$ayah:${uiState.recognizedText}"
		if (lastAutoAdvanceKey == key) return@LaunchedEffect
		lastAutoAdvanceKey = key
		goNextAyah()
	}

	Column(
		modifier = modifier
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text("Tajweed Practice", style = MaterialTheme.typography.headlineSmall, color = Color.White)

		if (isModelLoading) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				CircularProgressIndicator(color = gold, strokeWidth = 3.dp)
				Text("Downloading Vosk model…", color = Color.White)
			}
		} else {
			when (uiState.status) {
				TajweedStatus.RECORDING -> {
					Text("Recording…", color = Color.White)
				}
				TajweedStatus.ANALYZING -> {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						CircularProgressIndicator(color = gold, strokeWidth = 3.dp)
						Text("Analyzing…", color = Color.White)
					}
				}
				else -> Unit
			}
		}

		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
			ExposedDropdownMenuBox(
				expanded = surahExpanded,
				onExpandedChange = { surahExpanded = !surahExpanded },
				modifier = Modifier.weight(1f),
			) {
				val surahLabel = resolvedSurahItems.firstOrNull { it.surahNumber == surah }?.label
					?: resolvedSurahItems.firstOrNull()?.label.orEmpty()
				OutlinedTextField(
					value = surahLabel,
					onValueChange = {},
					readOnly = true,
					singleLine = true,
					label = { Text("Surah") },
					trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = surahExpanded) },
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
				ExposedDropdownMenu(expanded = surahExpanded, onDismissRequest = { surahExpanded = false }) {
					resolvedSurahItems.forEach { item ->
						DropdownMenuItem(
							text = { Text(item.label) },
							onClick = {
								surahExpanded = false
								surah = item.surahNumber
								ayah = 1
								viewModel.setSelection(surahNumber = surah, ayahNumber = ayah, reciterId = selectedReciterId)
							},
						)
					}
				}
			}

			ExposedDropdownMenuBox(
				expanded = ayahExpanded,
				onExpandedChange = { ayahExpanded = !ayahExpanded },
				modifier = Modifier.weight(1f),
			) {
				OutlinedTextField(
					value = ayah.toString(),
					onValueChange = {},
					readOnly = true,
					singleLine = true,
					label = { Text("Ayah") },
					trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ayahExpanded) },
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
				ExposedDropdownMenu(expanded = ayahExpanded, onDismissRequest = { ayahExpanded = false }) {
					for (n in 1..maxAyah) {
						DropdownMenuItem(
							text = { Text(n.toString()) },
							onClick = {
								ayahExpanded = false
								ayah = n
								viewModel.setSelection(surahNumber = surah, ayahNumber = ayah, reciterId = selectedReciterId)
							},
						)
					}
				}
			}
		}

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Button(
				onClick = { goPrevAyah() },
				enabled = !isBusy,
				modifier = Modifier.weight(1f),
			) {
				Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = "Previous")
				Spacer(modifier = Modifier.width(8.dp))
				Text("Previous")
			}
			Button(
				onClick = { goNextAyah() },
				enabled = !isBusy,
				modifier = Modifier.weight(1f),
			) {
				Text("Next")
				Spacer(modifier = Modifier.width(8.dp))
				Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next")
			}
		}

		ExposedDropdownMenuBox(
			expanded = reciterExpanded,
			onExpandedChange = { reciterExpanded = it },
		) {
			OutlinedTextField(
				value = selectedReciterLabel,
				onValueChange = {},
				readOnly = true,
				label = { Text("Reciter") },
				trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reciterExpanded) },
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
			ExposedDropdownMenu(
				expanded = reciterExpanded,
				onDismissRequest = { reciterExpanded = false },
			) {
				reciters.forEach { item ->
					DropdownMenuItem(
						text = { Text(item.label) },
						onClick = {
							reciterExpanded = false
							viewModel.setSelection(surahNumber = surah, ayahNumber = ayah, reciterId = item.id)
						},
					)
				}
			}
		}

		OutlinedTextField(
			value = uiState.expectedAyahText,
			onValueChange = viewModel::setExpectedAyahText,
			label = { Text("Expected Arabic") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.White,
				unfocusedTextColor = Color.White,
				focusedBorderColor = gold,
				unfocusedBorderColor = Color(0x55FFFFFF),
				cursorColor = gold,
			),
			modifier = Modifier.fillMaxWidth(),
			minLines = 2,
		)

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Button(
				onClick = {
					scope.launch {
						val reciterId = effectiveReciterId
						if (reciterId <= 0) return@launch
						val url = runCatching { resolveReferenceUrl(reciterId, surah, ayah) }.getOrNull()
						if (!url.isNullOrBlank()) viewModel.playReference(url)
					}
				},
				enabled = !isBusy,
			) {
				Text("Play reference")
			}
			Button(
				onClick = viewModel::stopReference,
				enabled = uiState.status == TajweedStatus.PLAYING_REFERENCE,
			) {
				Text("Stop")
			}
			Spacer(modifier = Modifier.weight(1f))

			if (uiState.status == TajweedStatus.RECORDING) {
				Button(
					onClick = viewModel::stopRecordingAndRecognize,
					enabled = !isModelLoading,
				) {
					Text("Stop record")
				}
			} else {
				Button(
					onClick = {
					val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
					if (!granted) {
						ctx.findActivity()?.let { act ->
							ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.RECORD_AUDIO), 7001)
						}
						// Continue anyway; ViewModel will surface errors.
					}
					viewModel.startRecording()
				},
					enabled = !isBusy && !isModelLoading,
					colors = ButtonDefaults.buttonColors(),
				) {
					Text("Record")
				}
			}
		}

		when (uiState.status) {
			TajweedStatus.ANALYZING -> Text("Analyzing…", color = Color.White)
			TajweedStatus.COMPLETED -> {
				val r = uiState.result
				if (r != null) {
					Text("Score: ${r.overallScore}% (${r.rating})", color = Color.White)
					Text(
						"Matched: ${r.matchedWordCount}/${r.totalWordCount} | Missing: ${r.missingWords.size} | Extra: ${r.extraWords.size}",
						color = Color.White,
					)
				}
			}
			TajweedStatus.ERROR -> {
				Text(uiState.errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
			}
			else -> Unit
		}

		if (uiState.recognizedText.isNotBlank()) {
			Spacer(modifier = Modifier.height(8.dp))
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text("Recognized:", color = Color.White)
				Button(
					onClick = { viewModel.playRecognized() },
					enabled = uiState.status != TajweedStatus.RECORDING && uiState.status != TajweedStatus.ANALYZING,
				) {
					Text("Play recognized")
				}
			}
			Text(uiState.recognizedText, color = Color.White)
		}
	}
}

private fun Context.findActivity(): Activity? {
	var current: Context? = this
	while (current is ContextWrapper) {
		if (current is Activity) return current
		current = current.baseContext
	}
	return current as? Activity
}
