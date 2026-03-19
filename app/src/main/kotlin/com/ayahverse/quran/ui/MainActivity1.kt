package com.ayahverse.quran.ui

import android.os.Bundle
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ayahverse.quran.data.ChapterDto
import com.ayahverse.quran.data.QuranApi
import com.ayahverse.quran.data.QuranAudioApi
import com.ayahverse.quran.data.local.registry.ClasspathTafseerRegistryLoader
import com.ayahverse.quran.data.local.registry.TafseerRegistryEntry
import com.ayahverse.quran.data.repository.TafseerRepositoryImpl
import com.ayahverse.quran.domain.models.AyatReference
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Previous Compose-based screen, kept as MainActivity1.
 * The new OpenGL ES sky experience is now in MainActivity.
 */
class MainActivity1 : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			QuranDarkTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					AyatScreen()
				}
			}
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AyatScreen() {
	val api = remember {
		Retrofit.Builder()
			.baseUrl(QuranApi.BASE_URL)
			.addConverterFactory(GsonConverterFactory.create())
			.build()
			.create(QuranApi::class.java)
	}

	val audioApi = remember { QuranAudioApi(api) }
	val scope = rememberCoroutineScope()

	val mediaPlayer = remember { MediaPlayer() }
	DisposableEffect(Unit) {
		mediaPlayer.setAudioAttributes(
			AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
				.build(),
		)
		onDispose {
			try {
				mediaPlayer.reset()
				mediaPlayer.release()
			} catch (_: Throwable) {
			}
		}
	}

	val tafseerRegistryLoader = remember { ClasspathTafseerRegistryLoader() }
	val tafseerRepository = remember { TafseerRepositoryImpl(registryLoader = tafseerRegistryLoader) }

	var chapters by remember { mutableStateOf<List<ChapterDto>>(emptyList()) }
	var selectedSurahId by rememberSaveable { mutableStateOf<Int?>(null) }
	var selectedAyahNumber by rememberSaveable { mutableStateOf<Int?>(null) }
	var tafseers by remember { mutableStateOf<List<TafseerRegistryEntry>>(emptyList()) }
	var selectedTafseerId by rememberSaveable { mutableStateOf<String?>(null) }

	var reciters by remember { mutableStateOf<List<QuranAudioApi.Reciter>>(emptyList()) }
	var selectedReciterId by rememberSaveable { mutableStateOf<Int?>(null) }
	var autoPlayAudio by rememberSaveable { mutableStateOf(false) }
	var isAudioPlaying by remember { mutableStateOf(false) }
	var isAudioLoading by remember { mutableStateOf(false) }
	var audioErrorMessage by remember { mutableStateOf<String?>(null) }
	var preparedAudioUrl by remember { mutableStateOf<String?>(null) }
	var currentAudioUrl by remember { mutableStateOf<String?>(null) }
	var currentAudioVerseKey by remember { mutableStateOf<String?>(null) }
	var currentAudioReciterId by remember { mutableStateOf<Int?>(null) }

	var arabicAyahText by remember { mutableStateOf<String?>(null) }
	var englishMeaningText by remember { mutableStateOf<String?>(null) }
	var tafseerText by remember { mutableStateOf<String?>(null) }
	var isTafseerLoading by remember { mutableStateOf(false) }
	var tafseerErrorMessage by remember { mutableStateOf<String?>(null) }
	var isLoading by remember { mutableStateOf(false) }
	var errorMessage by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(Unit) {
		isLoading = true
		errorMessage = null
		try {
			chapters = api.getChapters().chapters
			tafseers = tafseerRegistryLoader.loadRegistry()
			reciters = audioApi.listReciters()
		} catch (t: Throwable) {
			errorMessage = t.message ?: "Failed to load surahs"
		} finally {
			isLoading = false
		}
	}

	fun stopAudio() {
		try {
			mediaPlayer.reset()
		} catch (_: Throwable) {
		}
		isAudioPlaying = false
		isAudioLoading = false
		preparedAudioUrl = null
	}

	suspend fun playAudioUrl(url: String) {
		isAudioLoading = true
		audioErrorMessage = null

		if (preparedAudioUrl == url) {
			try {
				mediaPlayer.start()
				isAudioPlaying = true
				isAudioLoading = false
				return
			} catch (_: Throwable) {
			}
		}

		stopAudio()
		preparedAudioUrl = null

		mediaPlayer.setOnCompletionListener {
			isAudioPlaying = false
		}
		mediaPlayer.setOnErrorListener { _, _, _ ->
			isAudioPlaying = false
			isAudioLoading = false
			preparedAudioUrl = null
			audioErrorMessage = "Failed to play audio"
			true
		}
		mediaPlayer.setOnPreparedListener {
			preparedAudioUrl = url
			try {
				it.start()
				isAudioPlaying = true
				isAudioLoading = false
			} catch (_: Throwable) {
				isAudioPlaying = false
				isAudioLoading = false
				preparedAudioUrl = null
				audioErrorMessage = "Failed to start audio"
			}
		}

		try {
			mediaPlayer.setDataSource(url)
			mediaPlayer.prepareAsync()
		} catch (_: Throwable) {
			isAudioPlaying = false
			isAudioLoading = false
			preparedAudioUrl = null
			audioErrorMessage = "Failed to load audio"
			try {
				mediaPlayer.reset()
			} catch (_: Throwable) {
			}
		}
	}

	val selectedSurah = chapters.firstOrNull { it.id == selectedSurahId }
	val verseKey = selectedSurahId?.let { s ->
		selectedAyahNumber?.let { a -> "$s:$a" }
	}

	LaunchedEffect(verseKey) {
		if (verseKey == null) return@LaunchedEffect

		isLoading = true
		errorMessage = null
		arabicAyahText = null
		englishMeaningText = null
		try {
			val arabicDeferred = async { api.getUthmaniVerseByKey(verseKey) }
			val englishDeferred = async { api.getEnglishMeaningByVerseKey(verseKey = verseKey) }

			val arabic = arabicDeferred.await().verses.firstOrNull()?.textUthmani
			val english = englishDeferred.await().translations.firstOrNull()?.text

			arabicAyahText = arabic?.trim()?.ifBlank { null }
			englishMeaningText = english?.stripHtmlTags()?.trim()?.ifBlank { null }
		} catch (t: Throwable) {
			errorMessage = t.message ?: "Failed to load ayah"
		} finally {
			isLoading = false
		}
	}

	LaunchedEffect(verseKey, selectedTafseerId) {
		val tafseerId = selectedTafseerId
		if (verseKey == null || tafseerId.isNullOrBlank() || selectedSurahId == null || selectedAyahNumber == null) {
			tafseerText = null
			tafseerErrorMessage = null
			isTafseerLoading = false
			return@LaunchedEffect
		}

		isTafseerLoading = true
		tafseerErrorMessage = null
		tafseerText = null
		try {
			val tafseer = tafseerRepository.getTafseer(
				ayat = AyatReference(surahNumber = selectedSurahId!!, ayahNumber = selectedAyahNumber!!),
				tafseerId = tafseerId,
			)
			tafseerText = tafseer.content.stripHtmlTags().trim().ifBlank { null }
		} catch (t: Throwable) {
			tafseerErrorMessage = t.message ?: "Failed to load tafseer"
		} finally {
			isTafseerLoading = false
		}
	}

	LaunchedEffect(verseKey, selectedReciterId, autoPlayAudio) {
		val vk = verseKey
		val rid = selectedReciterId
		if (vk == null || rid == null) {
			currentAudioUrl = null
			currentAudioVerseKey = null
			currentAudioReciterId = null
			audioErrorMessage = null
			stopAudio()
			return@LaunchedEffect
		}

		if (!autoPlayAudio && isAudioPlaying && currentAudioVerseKey != null && currentAudioVerseKey != vk) {
			stopAudio()
		}

		if (!autoPlayAudio) {
			currentAudioUrl = null
			currentAudioVerseKey = vk
			currentAudioReciterId = rid
			return@LaunchedEffect
		}

		isAudioLoading = true
		audioErrorMessage = null
		try {
			val audio = audioApi.getAyahAudio(recitationId = rid, verseKey = vk)
			currentAudioUrl = audio?.absoluteUrl
			currentAudioVerseKey = vk
			currentAudioReciterId = rid
			val url = currentAudioUrl
			if (!url.isNullOrBlank()) {
				playAudioUrl(url)
			} else {
				isAudioLoading = false
				audioErrorMessage = "No audio available"
			}
		} catch (_: Throwable) {
			currentAudioUrl = null
			currentAudioVerseKey = vk
			currentAudioReciterId = rid
			isAudioLoading = false
			audioErrorMessage = "Failed to load audio"
			stopAudio()
		}
	}

	Scaffold { innerPadding ->
		val scrollState = rememberScrollState()
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.padding(16.dp)
				.verticalScroll(scrollState),
		) {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(),
			) {
				Row(modifier = Modifier.padding(16.dp)) {
					SurahDropdown(
						chapters = chapters,
						selectedSurahId = selectedSurahId,
						modifier = Modifier.weight(0.7f),
						onSelected = { newSurahId ->
							selectedSurahId = newSurahId
							selectedAyahNumber = null
							selectedTafseerId = null
							arabicAyahText = null
							englishMeaningText = null
							tafseerText = null
							tafseerErrorMessage = null
							errorMessage = null
						},
					)

					Spacer(modifier = Modifier.width(12.dp))

					AyahDropdown(
						versesCount = selectedSurah?.versesCount,
						selectedAyahNumber = selectedAyahNumber,
						modifier = Modifier.weight(0.3f),
						onSelected = { selectedAyahNumber = it },
					)
				}
			}

			Spacer(modifier = Modifier.height(12.dp))

			if (isLoading) {
				CircularProgressIndicator()
				Spacer(modifier = Modifier.height(16.dp))
			}

			errorMessage?.let { msg ->
				Text(
					text = msg,
					style = MaterialTheme.typography.bodyMedium,
				)
				Spacer(modifier = Modifier.height(16.dp))
			}

			if (verseKey != null) {
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(),
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
							Text(
								text = arabicAyahText ?: "",
								modifier = Modifier.fillMaxWidth(),
								style = MaterialTheme.typography.headlineSmall.merge(
									TextStyle(textDirection = TextDirection.Rtl),
								),
								textAlign = TextAlign.Start,
							)
						}
					}
				}

				Spacer(modifier = Modifier.height(12.dp))

				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(),
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Row(modifier = Modifier.fillMaxWidth()) {
							ReciterDropdown(
								reciters = reciters,
								selectedReciterId = selectedReciterId,
								modifier = Modifier.fillMaxWidth(),
								onSelected = { newId ->
									selectedReciterId = newId
								},
							)
						}

						Spacer(modifier = Modifier.height(12.dp))

						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
						) {
							if (selectedReciterId != null) {
								val rid = selectedReciterId!!
								val vk = verseKey
								Button(
									onClick = {
										if (isAudioPlaying) {
											try {
												mediaPlayer.pause()
											} catch (_: Throwable) {
											}
											isAudioPlaying = false
											return@Button
										}

										scope.launch {
											isAudioLoading = true
											audioErrorMessage = null
											var url = currentAudioUrl
											if (currentAudioVerseKey != vk || currentAudioReciterId != rid || url.isNullOrBlank()) {
												val audio = try {
													audioApi.getAyahAudio(recitationId = rid, verseKey = vk)
												} catch (_: Throwable) {
													null
												}
												url = audio?.absoluteUrl
												currentAudioUrl = url
												currentAudioVerseKey = vk
												currentAudioReciterId = rid
											}

											if (!url.isNullOrBlank()) {
												playAudioUrl(url)
											} else {
												isAudioLoading = false
												audioErrorMessage = "No audio available"
											}
										}
								},
									enabled = !isAudioLoading,
								) {
									Text(
										when {
											isAudioLoading -> "Loading…"
											isAudioPlaying -> "Pause"
											else -> "Play"
									},
									)
								}

								Spacer(modifier = Modifier.width(12.dp))
							}

							Row(
								verticalAlignment = Alignment.CenterVertically,
							) {
								Checkbox(
									checked = autoPlayAudio,
									onCheckedChange = { autoPlayAudio = it },
								)
								Text("Auto")
							}
						}

						audioErrorMessage?.let { msg ->
							Text(
								text = msg,
								modifier = Modifier
									.fillMaxWidth()
									.padding(top = 4.dp),
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.error,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}

				Spacer(modifier = Modifier.height(12.dp))

				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(),
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = "English translation",
							style = MaterialTheme.typography.titleMedium,
						)
						Spacer(modifier = Modifier.height(6.dp))
						Text(
							text = englishMeaningText ?: "",
							style = MaterialTheme.typography.bodyMedium,
						)
					}
				}

				Spacer(modifier = Modifier.height(12.dp))

				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(),
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = "Tafsir",
							style = MaterialTheme.typography.titleMedium,
						)
						Spacer(modifier = Modifier.height(8.dp))
						TafseerDropdown(
							tafseers = tafseers,
							selectedTafseerId = selectedTafseerId,
							onSelected = { selectedTafseerId = it },
						)
					}
				}

				if (selectedTafseerId != null) {
					Spacer(modifier = Modifier.height(12.dp))
					Card(
						modifier = Modifier.fillMaxWidth(),
						colors = CardDefaults.cardColors(),
					) {
						Column(modifier = Modifier.padding(16.dp)) {
							val currentTafseerError = tafseerErrorMessage
							val currentTafseerText = tafseerText
							when {
								isTafseerLoading -> CircularProgressIndicator()
								!currentTafseerError.isNullOrBlank() -> Text(
									text = currentTafseerError,
									style = MaterialTheme.typography.bodyMedium,
								)
								!currentTafseerText.isNullOrBlank() -> Text(
									text = currentTafseerText,
									style = MaterialTheme.typography.bodyMedium,
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SurahDropdown(
	chapters: List<ChapterDto>,
	selectedSurahId: Int?,
	modifier: Modifier = Modifier,
	onSelected: (Int) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	val selected = chapters.firstOrNull { it.id == selectedSurahId }
	val label = "Surah"
	val enabled = chapters.isNotEmpty()

	ExposedDropdownMenuBox(
		modifier = modifier,
		expanded = expanded,
		onExpandedChange = { if (enabled) expanded = !expanded },
	) {
		OutlinedTextField(
			value = selected?.let { "${it.id}. ${it.nameSimple}" } ?: "",
			onValueChange = {},
			readOnly = true,
			enabled = enabled,
			label = { Text(label) },
			placeholder = { Text(if (enabled) "Select a surah" else "Loading surahs…") },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier
				.menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
				.fillMaxWidth(),
		)

		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			chapters.forEach { chapter ->
				DropdownMenuItem(
					text = { Text("${chapter.id}. ${chapter.nameSimple} (${chapter.nameArabic})") },
					onClick = {
						expanded = false
						onSelected(chapter.id)
					},
				)
			}
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AyahDropdown(
	versesCount: Int?,
	selectedAyahNumber: Int?,
	modifier: Modifier = Modifier,
	onSelected: (Int) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	val enabled = versesCount != null

	ExposedDropdownMenuBox(
		modifier = modifier,
		expanded = expanded,
		onExpandedChange = { if (enabled) expanded = !expanded },
	) {
		OutlinedTextField(
			value = selectedAyahNumber?.toString() ?: "",
			onValueChange = {},
			readOnly = true,
			enabled = enabled,
			label = { Text("Ayah") },
			placeholder = { Text(if (enabled) "Select an ayah" else "Select surah first") },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier
				.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = enabled)
				.fillMaxWidth(),
		)

		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			val max = versesCount ?: 0
			for (i in 1..max) {
				DropdownMenuItem(
					text = { Text(i.toString()) },
					onClick = {
						expanded = false
						onSelected(i)
					},
				)
			}
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TafseerDropdown(
	tafseers: List<TafseerRegistryEntry>,
	selectedTafseerId: String?,
	onSelected: (String?) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	val enabled = tafseers.isNotEmpty()
	val selected = tafseers.firstOrNull { it.id == selectedTafseerId }

	ExposedDropdownMenuBox(
		expanded = expanded,
		onExpandedChange = { if (enabled) expanded = !expanded },
	) {
		OutlinedTextField(
			value = selected?.name ?: "",
			onValueChange = {},
			readOnly = true,
			enabled = enabled,
			label = { Text("Tafseer") },
			placeholder = { Text(if (enabled) "Select tafseer" else "Loading tafseers…") },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier
				.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = enabled)
				.fillMaxWidth(),
		)

		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			DropdownMenuItem(
				text = { Text("Select tafseer") },
				onClick = {
					expanded = false
					onSelected(null)
				},
			)
			tafseers.forEach { entry ->
				val subtitle = listOfNotNull(entry.source.takeIf { it.isNotBlank() }, entry.lang?.takeIf { it.isNotBlank() })
					.joinToString(" • ")
				DropdownMenuItem(
					text = {
						Text(if (subtitle.isBlank()) entry.name else "${entry.name} ($subtitle)")
					},
					onClick = {
						expanded = false
						onSelected(entry.id)
					},
				)
			}
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReciterDropdown(
	reciters: List<QuranAudioApi.Reciter>,
	selectedReciterId: Int?,
	modifier: Modifier = Modifier,
	onSelected: (Int?) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	val enabled = reciters.isNotEmpty()
	val selected = reciters.firstOrNull { it.id == selectedReciterId }
	val label = "Reciter"

	ExposedDropdownMenuBox(
		modifier = modifier,
		expanded = expanded,
		onExpandedChange = { if (enabled) expanded = !expanded },
	) {
		OutlinedTextField(
			value = selected?.name ?: "",
			onValueChange = {},
			readOnly = true,
			enabled = enabled,
			label = { Text(label) },
			placeholder = { Text(if (enabled) "Select reciter" else "Loading reciters…") },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier
				.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = enabled)
				.fillMaxWidth(),
		)

		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			DropdownMenuItem(
				text = { Text("Select reciter") },
				onClick = {
					expanded = false
					onSelected(null)
				},
			)
			reciters.forEach { reciter ->
				val subtitle = reciter.style?.takeIf { it.isNotBlank() }
				DropdownMenuItem(
					text = {
						Text(if (subtitle.isNullOrBlank()) reciter.name else "${reciter.name} (${subtitle})")
					},
					onClick = {
						expanded = false
						onSelected(reciter.id)
					},
				)
			}
		}
	}
}

private fun String.stripHtmlTags(): String {
	return replace(Regex("<[^>]*>"), "")
}

@Composable
private fun QuranDarkTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = darkColorScheme(),
		content = content,
	)
}
