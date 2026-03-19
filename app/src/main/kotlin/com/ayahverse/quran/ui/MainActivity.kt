package com.ayahverse.quran.ui

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Camera
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.BlurMaskFilter
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.provider.Settings
import java.util.Locale
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.InputType
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import com.ayahverse.quran.data.BackendTranslateRequest
import com.ayahverse.quran.ui.LinguisticsGraphView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ayahverse.quran.feature.tajweed.ui.TajweedPanel
import com.ayahverse.quran.feature.tajweed.ui.TajweedReciterItem
import com.ayahverse.quran.feature.tajweed.ui.TajweedSurahItem
import com.ayahverse.quran.data.ChapterDto
import com.ayahverse.quran.data.QuranBackendApi
import com.ayahverse.quran.data.QuranAudioApi
import com.ayahverse.quran.data.QuranApi
import com.ayahverse.quran.R
import com.ayahverse.quran.data.TranslationResourceDto
import com.ayahverse.quran.linguistics.supabase.SupabaseLinguisticsClient
import com.ayahverse.quran.linguistics.util.ArabicTextUtils
import com.ayahverse.quran.playback.PlaybackKeepAliveService
import com.ayahverse.quran.playback.MiniListenOverlayService
import com.ayahverse.quran.data.repository.TafsirRepositoryImpl
import com.ayahverse.quran.domain.models.AyatReference
import com.ayahverse.quran.domain.model.tafsir.TafsirDropdownOption
import com.ayahverse.quran.domain.model.tafsir.TafsirRequest
import com.ayahverse.quran.domain.repository.TafsirRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enterFullscreen()

		setContent {
			MaterialTheme {
				val context = LocalContext.current
				val glHolder = remember(context) { SurahWheelGlHolder(context) }
				var chapters by remember { mutableStateOf<List<ChapterDto>>(emptyList()) }

				LaunchedEffect(Unit) {
					try {
						val api = Retrofit.Builder()
							.baseUrl(QuranApi.BASE_URL)
							.addConverterFactory(GsonConverterFactory.create())
							.build()
							.create(QuranApi::class.java)
						chapters = api.getChapters().chapters
						glHolder.updateChapters(chapters)
					} catch (_: Throwable) {
						// If metadata fails to load, the book will fall back to the embedded surah names.
					}
				}

				// Keep GLSurfaceView lifecycle in sync with Activity lifecycle.
				DisposableEffect(glHolder) {
					glHolder.onResume()
					onDispose {
						glHolder.onPause()
						glHolder.dispose()
					}
				}

				Box(modifier = Modifier.fillMaxSize()) {
					AndroidView(
						factory = { glHolder.root },
						modifier = Modifier.fillMaxSize(),
					)
				}
			}
		}
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus) {
			enterFullscreen()
		}
	}

	private fun enterFullscreen() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
		window.navigationBarColor = Color.TRANSPARENT
		WindowInsetsControllerCompat(window, window.decorView).apply {
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			hide(WindowInsetsCompat.Type.systemBars())
		}
	}
}

private class SurahWheelGlHolder(private val context: Context) {
	val root: FrameLayout
	private val starBackground: StarryBackgroundView
	private val topMenuOverlay: FrameLayout
	private val topMenuDismissLayer: View
	private val globeMenuView: GlobeMenuView
	private val globeFooterView: LinearLayout
	private val hadithMenuContainer: FrameLayout
	private val listenMenuContainer: FrameLayout
	private val tajweedMenuContainer: FrameLayout
	private val listenComposeView: ComposeView
	private val tajweedComposeView: ComposeView
	private val listenCornerLogoButton: ImageView
	private val tajweedCornerLogoButton: ImageView
	private val listenMiniOverlay: FrameLayout
	private val listenMiniLogo: ImageView
	private var listenMiniModeEnabled: Boolean = false
	private var listenMiniWasInListenPanel: Boolean = false
	private var listenMiniHiddenViews: List<Pair<View, Int>> = emptyList()
	private var listenMiniHiddenGl: GLSurfaceView? = null
	private val hadithBooksView: HadithBooksStackView
	private val hadithChipView: HadithSelectionChipView
	private val hadithChipHost: FrameLayout
	private var hadithNamesPopupWindow: PopupWindow? = null
	private var hadithTopicsPopupWindow: PopupWindow? = null
	private val hadithReadingRoot: LinearLayout
	private val hadithCornerLogoButton: ImageView
	private val hadithTextScroll: ScrollView
	private val hadithTextScrollHost: FrameLayout
	private val hadithTranslationScroll: ScrollView
	private val hadithTranslationScrollHost: FrameLayout
	private val hadithArabicContainer: LinearLayout
	private val hadithTranslationContainer: LinearLayout
	private val hadithCoverImageView: ImageView
	private val hadithArabicTextView: TextView
	private val hadithTranslationTextView: TextView
	private val hadithLanguageChip: LinearLayout
	private val hadithLanguageLabel: TextView
	private val hadithLanguagePopup: ListPopupWindow
	private val hadithNavRow: LinearLayout
	private val hadithPrevButton: ImageButton
	private val hadithNextButton: ImageButton
	private val hadithNumberInput: EditText
	private var hadithLanguageRow: View
	private var hadithTopInsetPx: Int = 0
	private var hadithReadingMode: Boolean = false
	private var hadithActiveBookSlug: String? = null
	private var hadithCurrentNumber: Int = 1
	private var hadithSelectedLangCode: String = "en"
	private var hadithCurrentPayload: HadithPayload? = null
	private var hadithTranslateJob: Job? = null
	private val hadithTranslationCache: MutableMap<String, String> = linkedMapOf()
	private val hadithCoverBitmapCache: MutableMap<String, Bitmap> = linkedMapOf()
	private val hadithLangOptions: List<HadithLangOption> = listOf(
		HadithLangOption(code = "en", label = "English"),
		HadithLangOption(code = "ur", label = "Urdu"),
		HadithLangOption(code = "bn", label = "Bengali"),
		HadithLangOption(code = "tr", label = "Turkish"),
		HadithLangOption(code = "id", label = "Indonesian"),
		HadithLangOption(code = "fr", label = "French"),
		HadithLangOption(code = "es", label = "Spanish"),
		HadithLangOption(code = "de", label = "German"),
		HadithLangOption(code = "ru", label = "Russian"),
		HadithLangOption(code = "zh", label = "Chinese"),
		HadithLangOption(code = "hi", label = "Hindi"),
	)
	private val listenSurahItemsState = mutableStateOf<List<ListenSurahItem>>(emptyList())
	private val listenRecitersState = mutableStateOf<List<QuranAudioApi.Reciter>>(emptyList())
	private val listenIsPlayingState = mutableStateOf(false)
	private var listenConfig: ListenPlaybackConfig? = null
	private var listenPlaybackToken: Int = 0
	private var listenCurrentAyah: Int = 1
	private var listenRangeRepeatsRemaining: Int = 0
	private var listenAyahRepeatsRemaining: Int = 0
	private var listenTts: TextToSpeech? = null
	private var listenTtsInit: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
	private val listenPendingUtterances: MutableMap<String, kotlinx.coroutines.CompletableDeferred<Unit>> = linkedMapOf()
	private val labelsOverlay: SurahLabelsOverlayView
	private val ayahCardsOverlay: AyahCardsOverlayView
	private val audioControls: AyahAudioControlsView
	private val translationContainer: LinearLayout
	private val translationRow: AyahTranslationRowView
	private val meaningTabsRow: AyahMeaningTabsRowView
	private val meaningExpandedContainer: LinearLayout
	private val linguisticsGraphView: LinguisticsGraphView
	private val linguisticsWordsCarouselView: LinguisticsWordsCarouselView
	private val tafseerScroll: ScrollView
	private val tafseerText: TextView
	private val tafseerFocusContainer: LinearLayout
	private val tafseerFocusHeader: LinearLayout
	private val tafseerFocusTitle: TextView
	private val tafseerFocusDropdownButton: ImageButton
	private val tafseerFocusCloseButton: ImageButton
	private val tafseerFocusScroll: ScrollView
	private val tafseerFocusText: TextView
	private val tafseerDropdown: ListPopupWindow
	private var tafsirOptions: List<TafsirDropdownOption> = emptyList()
	private val tafsirRepository: TafsirRepository
	private val tafsirDropdownAdapter: TafsirDropdownAdapter
	private val bookView: OpenBookView
	private val renderer: SurahWheelRenderer
	private val glView: SurahWheelGLSurfaceView
	private val density = context.resources.displayMetrics.density
	private val bottomCardCollapsedHeightPx = dpToPx(44f)
	@Volatile private var bottomCardCurrentHeightPx: Int = bottomCardCollapsedHeightPx
	private var bottomCardAnimator: ValueAnimator? = null
	private val bottomCard: FrameLayout
	private val bottomCardExpandedContent: LinearLayout
	private val bottomCardCollapsedBar: FrameLayout
	private val bottomCardCollapsedSurahText: TextView
	private val bottomCardCollapsedAyahBadge: SparklingAyahBadgeView
	private val bottomCardCollapsedTotalVersesText: TextView
	private val bottomCardExpandedBackground: GradientDrawable
	private val bottomCardExpandedMarginPx: Int = 0
	private val topMenuButton: ArabicCircularCalligraphyButtonView
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val api: QuranApi = Retrofit.Builder()
		.baseUrl(QuranApi.BASE_URL)
		.addConverterFactory(GsonConverterFactory.create())
		.build()
		.create(QuranApi::class.java)
	private val backendApi: QuranBackendApi = Retrofit.Builder()
		.baseUrl(QuranBackendApi.BASE_URL)
		.addConverterFactory(GsonConverterFactory.create())
		.build()
		.create(QuranBackendApi::class.java)
	private val supabaseLinguisticsClient: SupabaseLinguisticsClient = SupabaseLinguisticsClient()
	private val audioApi = QuranAudioApi(api)
	private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	private val mainHandler = Handler(Looper.getMainLooper())
	private val hadithHttp = OkHttpClient()
	private var hadithFetchJob: Job? = null
	private val hadithBookLogOnce: MutableSet<String> = linkedSetOf()
	private val hadithBookTitleBySlug: MutableMap<String, String> = linkedMapOf()
	private val hadithBookCountsBySlug: MutableMap<String, HadithBookCounts> = linkedMapOf()
	private data class HadithTopicEntry(
		val topic: String,
		val startHadith: Int,
		val endHadith: Int,
	)
	private var hadithTopicsLoaded: Boolean = false
	private var hadithTopicsFetchJob: Job? = null
	private val hadithTopicsWaiters: MutableList<() -> Unit> = mutableListOf()
	private val hadithTopicsByBookCode: MutableMap<String, List<HadithTopicEntry>> = linkedMapOf()
	private var hadithTopicsSourceRowCount: Int = 0
	private var hadithTopicsLastError: String? = null
	private var audioFocusRequest: AudioFocusRequest? = null
	@Volatile private var pausedForFocusLoss: Boolean = false
	private var mediaPlayer: MediaPlayer? = null
	@Volatile private var isPlaying: Boolean = false
	@Volatile private var autoPlayEnabled: Boolean = false
	private var lowPowerGlRendering: Boolean = false
	@Volatile private var activeSurahNumber: Int = 1
	@Volatile private var selectedReciterId: Int = -1
	private val selectedReciterIdState = mutableStateOf(-1)
	@Volatile private var selectedTranslationId: Int = QuranApi.DEFAULT_EN_TRANSLATION_ID
	@Volatile private var selectedMeaningLang: String = "en"
	@Volatile private var lastAyahSurahIndex: Int = -1
	private var ayahFetchJob: Job? = null
	@Volatile private var ayahFetchToken: Int = 0
	@Volatile private var lastTranslationSurahIndex: Int = -1
	private var translationFetchJob: Job? = null
	@Volatile private var translationFetchToken: Int = 0
	@Volatile private var chapterArabic = emptyArray<String>()
	@Volatile private var chapterEnglish = emptyArray<String>()
	@Volatile private var chapterMeaning = emptyArray<String>()
	@Volatile private var chapterVerses = IntArray(0)
	@Volatile private var chapterRevelationPlace = emptyArray<String?>()
	private val juzStartBySurah = IntArray(SURA_NAMES.size) { 0 }
	private val juzEndBySurah = IntArray(SURA_NAMES.size) { 0 }
	private var juzFetchJob: Job? = null
	@Volatile private var juzFetchToken: Int = 0
	@Volatile private var juzInFlightSurahIndex: Int = -1
	@Volatile private var activeAyahTextUthmani: String = ""
	@Volatile private var activeAyahVerseKey: String = "1:1"
	@Volatile private var activeAyahNumber: Int = 1
	@Volatile private var expandedTab: AyahMeaningTabsRowView.Tab? = null
	private var linguisticsFetchJob: Job? = null
	@Volatile private var linguisticsFetchToken: Int = 0
	private var tafseerFetchJob: Job? = null
	@Volatile private var tafseerFetchToken: Int = 0
	@Volatile private var selectedTafsirSlug: String? = null
	@Volatile private var selectedTafsirDisplayName: String? = null
	@Volatile private var tafsirFocusMode: Boolean = false

	private fun setExpandedMeaningTab(tab: AyahMeaningTabsRowView.Tab?, collapseWheel: Boolean) {
		// Keep wheel/book and meaning expansion mutually exclusive.
		if (collapseWheel && tab != null && bottomCardExpandedContent.visibility == View.VISIBLE) {
			// Collapse immediately so the expanded meaning area has room (and dropdown sizing is correct).
			setBottomCardCollapsed(collapsed = true, animate = false)
		}

		expandedTab = tab
		meaningTabsRow.setSelectedTab(tab)
		when (tab) {
			AyahMeaningTabsRowView.Tab.Linguistics -> {
				setTafsirFocusMode(false)
				meaningExpandedContainer.visibility = View.VISIBLE
				linguisticsGraphView.visibility = View.VISIBLE
				linguisticsWordsCarouselView.visibility = View.VISIBLE
				tafseerScroll.visibility = View.GONE
				tafseerDropdown.dismiss()
				linguisticsGraphView.setAyahText(activeAyahTextUthmani)
				requestLinguisticsRefresh()
			}
			AyahMeaningTabsRowView.Tab.Tafseer -> {
				meaningExpandedContainer.visibility = View.VISIBLE
				linguisticsGraphView.visibility = View.GONE
				linguisticsWordsCarouselView.visibility = View.GONE
				tafseerScroll.visibility = View.VISIBLE
				refreshTafsirOptions()
				if (!tafsirFocusMode) {
					showTafseerDropdown()
				}
				val currentId = selectedTafsirSlug
				if (currentId.isNullOrBlank()) {
					tafseerText.text = "Select a Tafseer from the dropdown."
					tafseerFocusText.text = "Select a Tafseer from the dropdown."
					tafseerFocusTitle.text = "Tafseer"
				}
			}
			null -> {
				setTafsirFocusMode(false)
				meaningExpandedContainer.visibility = View.GONE
				linguisticsFetchJob?.cancel()
				tafseerFetchJob?.cancel()
				tafseerDropdown.dismiss()
			}
		}
	}

	init {
		starBackground = StarryBackgroundView(context).apply {
			// Full-screen stars.
		}

		// Top-level overlay menu (hidden by default).
		topMenuOverlay = FrameLayout(context).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)
			visibility = View.GONE
			isClickable = true
			isFocusable = true
			// Starfield behind the globe.
			addView(
				StarryBackgroundView(context),
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				),
			)
			// Tap outside to dismiss.
			topMenuDismissLayer = View(context).apply {
				setOnClickListener {
					stopListenPlayback()
					listenMenuContainer.visibility = View.GONE
					tajweedMenuContainer.visibility = View.GONE
					visibility = View.GONE
					// Reset to globe view for next open.
					globeMenuView.visibility = View.VISIBLE
					hadithMenuContainer.visibility = View.GONE
					resetHadithMenuState()
							globeFooterView.visibility = View.VISIBLE
				}
			}
			addView(
				topMenuDismissLayer,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				),
			)

			globeMenuView = GlobeMenuView(context).apply {
				setOnItemClickListener { key ->
					when {
						key.equals("quran", ignoreCase = true) -> {
							// Return to the existing surah wheel menu.
							topMenuOverlay.visibility = View.GONE
							globeMenuView.visibility = View.VISIBLE
							hadithMenuContainer.visibility = View.GONE
							listenMenuContainer.visibility = View.GONE
							tajweedMenuContainer.visibility = View.GONE
							stopListenPlayback()
							resetHadithMenuState()
							globeFooterView.visibility = View.VISIBLE
							setBottomCardCollapsed(collapsed = false, animate = true)
						}
						key.equals("hadith", ignoreCase = true) -> {
							// Show the hadith books menu panel.
							globeMenuView.visibility = View.GONE
							globeFooterView.visibility = View.GONE
							hadithMenuContainer.visibility = View.VISIBLE
							listenMenuContainer.visibility = View.GONE
							tajweedMenuContainer.visibility = View.GONE
							stopListenPlayback()
							resetHadithMenuState()
						}
						key.equals("listen", ignoreCase = true) -> {
							globeMenuView.visibility = View.GONE
							globeFooterView.visibility = View.GONE
							hadithMenuContainer.visibility = View.GONE
							tajweedMenuContainer.visibility = View.GONE
							resetHadithMenuState()
							listenMenuContainer.visibility = View.VISIBLE
						}
						key.equals("markaz", ignoreCase = true) -> {
							// Tajweed practice panel.
							globeMenuView.visibility = View.GONE
							globeFooterView.visibility = View.GONE
							hadithMenuContainer.visibility = View.GONE
							listenMenuContainer.visibility = View.GONE
							stopListenPlayback()
							resetHadithMenuState()
							tajweedMenuContainer.visibility = View.VISIBLE
						}
						else -> Unit
					}
				}
			}
			addView(
				globeMenuView,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.WRAP_CONTENT,
					FrameLayout.LayoutParams.WRAP_CONTENT,
				).apply {
					gravity = Gravity.CENTER
				},
			)

			globeFooterView = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				gravity = Gravity.CENTER
				isClickable = false
				isFocusable = false
				background = null
				val neon = Color.argb(255, 0, 255, 210)
				fun mkLine(text: String, sizeSp: Float, bold: Boolean): TextView {
					return TextView(context).apply {
						this.text = text
						setTextColor(neon)
						textSize = sizeSp
						typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
						setSingleLine(true)
						setShadowLayer(dpToPx(14f).toFloat(), 0f, 0f, Color.argb(210, 0, 255, 210))
						letterSpacing = 0.02f
					}
				}
				addView(mkLine("Version 1.0", sizeSp = 14f, bold = true))
				addView(mkLine("Many more features coming soon", sizeSp = 13f, bold = false))
				addView(mkLine("by anik.dife@gmail.com", sizeSp = 12.5f, bold = false))
			}
			addView(
				globeFooterView,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.WRAP_CONTENT,
					FrameLayout.LayoutParams.WRAP_CONTENT,
				).apply {
					gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
					bottomMargin = dpToPx(18f)
				},
			)

			hadithBooksView = HadithBooksStackView(context).apply {
				setOnBookClickListener { book ->
					onHadithBookSelected(book)
				}
			}
			hadithCoverImageView = ImageView(context).apply {
				visibility = View.GONE
				alpha = 1f
				adjustViewBounds = true
				scaleType = ImageView.ScaleType.FIT_CENTER
				background = null
				isClickable = false
				isFocusable = false
			}
			hadithArabicTextView = TextView(context).apply {
				setTextColor(Color.argb(235, 255, 255, 255))
				textSize = 20f
				typeface = Typeface.DEFAULT_BOLD
				setLineSpacing(0f, 1.22f)
				setSingleLine(false)
				maxLines = Int.MAX_VALUE
				ellipsize = null
				setHorizontallyScrolling(false)
				background = null
				// Glowing white
				setShadowLayer(dpToPx(10f).toFloat(), 0f, 0f, Color.argb(170, 255, 255, 255))
				textDirection = View.TEXT_DIRECTION_RTL
				gravity = Gravity.END
			}
			hadithTranslationTextView = TextView(context).apply {
				setTextColor(Color.argb(235, 255, 255, 255))
				textSize = 16f
				typeface = Typeface.DEFAULT
				setLineSpacing(0f, 1.22f)
				setSingleLine(false)
				maxLines = Int.MAX_VALUE
				ellipsize = null
				setHorizontallyScrolling(false)
				background = null
				// Glowing white
				setShadowLayer(dpToPx(10f).toFloat(), 0f, 0f, Color.argb(170, 255, 255, 255))
				textDirection = View.TEXT_DIRECTION_FIRST_STRONG
				gravity = Gravity.START
			}

			val chipBg = GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(
					Color.argb(70, 255, 255, 255),
					Color.argb(35, 255, 255, 255),
				),
			).apply {
				cornerRadius = dpToPx(16f).toFloat()
				setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
			}
			hadithLanguageChip = LinearLayout(context).apply {
				orientation = LinearLayout.HORIZONTAL
				gravity = Gravity.CENTER_VERTICAL
				setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
				background = null
				isClickable = true
				isFocusable = true
			}
			hadithLanguageLabel = TextView(context).apply {
				setTextColor(Color.argb(235, 212, 175, 55))
				textSize = 14f
				text = "English"
				setSingleLine(true)
			}
			val hadithLangArrow = ImageView(context).apply {
				setImageResource(android.R.drawable.arrow_down_float)
				setColorFilter(Color.argb(235, 212, 175, 55))
			}
			hadithLanguageChip.addView(hadithLanguageLabel)
			hadithLanguageChip.addView(
				hadithLangArrow,
				LinearLayout.LayoutParams(dpToPx(18f), dpToPx(18f)).apply { leftMargin = dpToPx(6f) },
			)
			hadithLanguagePopup = ListPopupWindow(context).apply {
				anchorView = hadithLanguageChip
				isModal = true
				setOnItemClickListener { _, _, position, _ ->
					val opt = hadithLangOptions.getOrNull(position) ?: return@setOnItemClickListener
					hadithSelectedLangCode = opt.code
					hadithLanguageLabel.text = opt.label
					dismiss()
					refreshHadithTranslation()
				}
				setBackgroundDrawable(
					GradientDrawable(
						GradientDrawable.Orientation.TOP_BOTTOM,
						intArrayOf(
							Color.argb(235, 20, 25, 35),
							Color.argb(235, 10, 12, 18),
						),
					).apply {
						cornerRadius = dpToPx(18f).toFloat()
						setStroke(dpToPx(1f), Color.argb(90, 255, 255, 255))
					},
				)
			}
			hadithLanguageChip.setOnClickListener {
				if (hadithLangOptions.isNotEmpty()) {
					val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, hadithLangOptions.map { it.label }) {
						override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
							val v = super.getView(position, convertView, parent)
							(v as? TextView)?.apply {
								setTextColor(Color.WHITE)
								textSize = 16f
								setPadding(dpToPx(18f), dpToPx(12f), dpToPx(18f), dpToPx(12f))
							}
							return v
						}
					}
					hadithLanguagePopup.setAdapter(adapter)
					hadithLanguagePopup.width = dpToPx(220f)
					hadithLanguagePopup.verticalOffset = dpToPx(10f)
					hadithLanguagePopup.show()
				}
			}

			hadithChipView = HadithSelectionChipView(context).apply {
				visibility = View.GONE
				alpha = 0f
				setOnLeftClickListener {
					showHadithBooksPopup()
				}
				setOnRightClickListener {
					showHadithTopicsPopup()
				}
			}
			hadithChipHost = FrameLayout(context).apply {
				background = null
				addView(
					hadithChipView,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.WRAP_CONTENT,
						FrameLayout.LayoutParams.WRAP_CONTENT,
					).apply {
						gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
						bottomMargin = dpToPx(18f)
					},
				)
			}

			hadithArabicContainer = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				setPadding(dpToPx(18f), dpToPx(18f), dpToPx(18f), dpToPx(14f))
				background = null
				addView(
					hadithCoverImageView,
					LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						dpToPx(180f),
					).apply {
						bottomMargin = dpToPx(12f)
					},
				)
				addView(
					hadithArabicTextView,
					LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
				)
			}
			hadithTranslationContainer = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				setPadding(dpToPx(18f), dpToPx(14f), dpToPx(18f), dpToPx(18f))
				background = null
				addView(
					hadithTranslationTextView,
					LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
				)
			}
			hadithTextScroll = ScrollView(context).apply {
				visibility = View.VISIBLE
				isFillViewport = true
				clipToPadding = false
				background = null
				addView(
					hadithArabicContainer,
					ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT,
					),
				)
			}
			hadithTextScrollHost = FrameLayout(context).apply {
				background = null
				addView(
					hadithTextScroll,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT,
					),
				)
			}
			hadithTranslationScroll = ScrollView(context).apply {
				visibility = View.VISIBLE
				isFillViewport = true
				clipToPadding = false
				background = null
				addView(
					hadithTranslationContainer,
					ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT,
					),
				)
			}
			hadithTranslationScrollHost = FrameLayout(context).apply {
				background = null
				addView(
					hadithTranslationScroll,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT,
					),
				)
			}

			hadithLanguageRow = FrameLayout(context).apply {
				background = null
				setPadding(dpToPx(18f), dpToPx(8f), dpToPx(18f), dpToPx(8f))
				addView(
					hadithLanguageChip,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.WRAP_CONTENT,
						FrameLayout.LayoutParams.WRAP_CONTENT,
					).apply {
						gravity = Gravity.CENTER
					},
				)
			}
			hadithReadingRoot = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				visibility = View.GONE
				background = null
				addView(
					hadithTextScrollHost,
					LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(220f)).apply {
						leftMargin = dpToPx(18f)
						rightMargin = dpToPx(18f)
					},
				)
				addView(
					hadithLanguageRow,
					LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
						leftMargin = dpToPx(18f)
						rightMargin = dpToPx(18f)
						topMargin = dpToPx(6f)
						bottomMargin = dpToPx(6f)
					},
				)
				addView(
					hadithTranslationScrollHost,
					LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(220f)).apply {
						leftMargin = dpToPx(18f)
						rightMargin = dpToPx(18f)
					},
				)
				addView(
					hadithChipHost,
					LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
				)
			}

			val neon = Color.argb(255, 0, 255, 210)
			attachNeonScrollbar(hadithTextScroll, hadithTextScrollHost, neon)
			attachNeonScrollbar(hadithTranslationScroll, hadithTranslationScrollHost, neon)
			hadithMenuContainer = FrameLayout(context).apply {
				visibility = View.GONE
				isClickable = true
				isFocusable = true
				background = null
				addView(
					hadithReadingRoot,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
							FrameLayout.LayoutParams.MATCH_PARENT,
					).apply {
						gravity = Gravity.TOP
					},
				)
				addView(
					hadithBooksView,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT,
					).apply {
						gravity = Gravity.CENTER
					},
				)
			}
			hadithCornerLogoButton = ImageView(context).apply {
				contentDescription = "Menu"
				isClickable = true
				isFocusable = true
				scaleType = ImageView.ScaleType.FIT_CENTER
				background = null
				setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
				val bmp = runCatching {
					val candidates = listOf("global_menu_logo.png", "logo.png")
					for (name in candidates) {
						val b = runCatching {
							context.assets.open(name).use { input ->
								BitmapFactory.decodeStream(input)
							}
						}.getOrNull()
						if (b != null) return@runCatching b
					}
					null
				}.getOrNull()
				if (bmp != null) {
					setImageBitmap(bmp)
				} else {
					// If no logo asset is present, hide the button to avoid a blank touch target.
					visibility = View.GONE
				}
				setOnClickListener {
					// Bring back the globe menu inside the top menu overlay.
					globeMenuView.visibility = View.VISIBLE
						globeFooterView.visibility = View.VISIBLE
					hadithMenuContainer.visibility = View.GONE
					resetHadithMenuState()
				}
			}
			hadithMenuContainer.addView(
				hadithCornerLogoButton,
				FrameLayout.LayoutParams(dpToPx(56f), dpToPx(56f)).apply {
					gravity = Gravity.BOTTOM or Gravity.END
					rightMargin = dpToPx(14f)
					bottomMargin = dpToPx(14f)
				},
			)
			addView(
				hadithMenuContainer,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				).apply {
					gravity = Gravity.TOP
				},
			)

			// Listen mode overlay (Compose UI) — uses the existing starfield background in this overlay.
			listenComposeView = ComposeView(context).apply {
				setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
				setContent {
					MaterialTheme {
						val surahItems = listenSurahItemsState.value.ifEmpty {
							List(SURA_NAMES.size) { i ->
								val n = i + 1
								val verses = versesCountForSurah(n)
								ListenSurahItem(
									surahNumber = n,
									label = "$n. ${SURA_NAMES[i]}",
									ayahCount = verses,
								)
							}
						}
						ListenModePanel(
							surahItems = surahItems,
							reciterItems = listenRecitersState.value.map { r ->
								val style = r.style?.trim().orEmpty().takeUnless { it.isBlank() }
								ListenReciterItem(
									id = r.id,
									label = if (style == null) r.name else "${r.name} · $style",
								)
							},
							selectedReciterId = selectedReciterIdState.value,
							onReciterSelected = { rid ->
								selectedReciterId = rid
								selectedReciterIdState.value = rid
								audioControls.setSelectedReciterId(rid)
							},
							languageLabels = hadithLangOptions.map { it.label },
							initialSurahNumber = activeSurahNumber,
							isPlaying = listenIsPlayingState.value,
							onPlayToggle = { play, settings ->
								if (play) startListenPlayback(settings) else stopListenPlayback()
							},
						)
					}
				}
			}

			// Tajweed practice overlay (Compose UI) — isolated feature.
			tajweedComposeView = ComposeView(context).apply {
				setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
				setContent {
					MaterialTheme {
						TajweedPanel(
							surahItems = List(SURA_NAMES.size) { i ->
								val n = i + 1
								TajweedSurahItem(
									surahNumber = n,
									label = "$n. ${SURA_NAMES[i]}",
									ayahCount = versesCountForSurah(n),
								)
							},
							reciters = listenRecitersState.value.map { r ->
								val style = r.style?.trim().orEmpty().takeUnless { it.isBlank() }
								TajweedReciterItem(
									id = r.id,
									label = if (style == null) r.name else "${r.name} · $style",
								)
							},
							loadExpectedAyahText = { s, a ->
								withContext(Dispatchers.IO) {
									runCatching {
										api.getUthmaniVerseByKey(verseKey = "$s:$a").verses.firstOrNull()?.textUthmani
									}.getOrNull()?.trim().orEmpty()
								}
							},
							resolveReferenceUrl = { reciterId, s, a ->
								withContext(Dispatchers.IO) {
									audioApi.getAyahAudio(recitationId = reciterId, verseKey = "$s:$a")?.absoluteUrl
								}
							},
						)
					}
				}
			}
			tajweedMenuContainer = FrameLayout(context).apply {
				visibility = View.GONE
				isClickable = true
				isFocusable = true
				background = null
				addView(
					tajweedComposeView,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT,
					).apply {
						gravity = Gravity.TOP
					},
				)
			}
			tajweedCornerLogoButton = ImageView(context).apply {
				contentDescription = "Menu"
				isClickable = true
				isFocusable = true
				scaleType = ImageView.ScaleType.FIT_CENTER
				background = null
				setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
				val bmp = runCatching {
					val candidates = listOf("global_menu_logo.png", "logo.png")
					for (name in candidates) {
						val b = runCatching {
							context.assets.open(name).use { input ->
								BitmapFactory.decodeStream(input)
							}
						}.getOrNull()
						if (b != null) return@runCatching b
					}
					null
				}.getOrNull()
				if (bmp != null) {
					setImageBitmap(bmp)
				} else {
					visibility = View.GONE
				}
				setOnClickListener {
					tajweedMenuContainer.visibility = View.GONE
					globeMenuView.visibility = View.VISIBLE
					globeFooterView.visibility = View.VISIBLE
					hadithMenuContainer.visibility = View.GONE
					listenMenuContainer.visibility = View.GONE
					stopListenPlayback()
					resetHadithMenuState()
				}
			}
			tajweedMenuContainer.addView(
				tajweedCornerLogoButton,
				FrameLayout.LayoutParams(dpToPx(56f), dpToPx(56f)).apply {
					gravity = Gravity.BOTTOM or Gravity.END
					rightMargin = dpToPx(14f)
					bottomMargin = dpToPx(14f)
				},
			)
			addView(
				tajweedMenuContainer,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				).apply {
					gravity = Gravity.TOP
				},
			)
			listenMenuContainer = FrameLayout(context).apply {
				visibility = View.GONE
				isClickable = true
				isFocusable = true
				background = null
				addView(
					listenComposeView,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT,
					).apply {
						gravity = Gravity.TOP
					},
				)
			}
			listenCornerLogoButton = ImageView(context).apply {
				contentDescription = "Menu"
				isClickable = true
				isFocusable = true
				scaleType = ImageView.ScaleType.FIT_CENTER
				background = null
				setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
				val bmp = runCatching {
					val candidates = listOf("global_menu_logo.png", "logo.png")
					for (name in candidates) {
						val b = runCatching {
							context.assets.open(name).use { input ->
								BitmapFactory.decodeStream(input)
							}
						}.getOrNull()
						if (b != null) return@runCatching b
					}
					null
				}.getOrNull()
				if (bmp != null) {
					setImageBitmap(bmp)
				} else {
					visibility = View.GONE
				}
				setOnClickListener {
					stopListenPlayback()
					listenMenuContainer.visibility = View.GONE
					globeMenuView.visibility = View.VISIBLE
					globeFooterView.visibility = View.VISIBLE
					hadithMenuContainer.visibility = View.GONE
					resetHadithMenuState()
				}
			}
			listenMenuContainer.addView(
				listenCornerLogoButton,
				FrameLayout.LayoutParams(dpToPx(56f), dpToPx(56f)).apply {
					gravity = Gravity.BOTTOM or Gravity.END
					rightMargin = dpToPx(14f)
					bottomMargin = dpToPx(14f)
				},
			)
			addView(
				listenMenuContainer,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				).apply {
					gravity = Gravity.TOP
				},
			)

			val arrowTint = Color.argb(235, 212, 175, 55)
			hadithPrevButton = ImageButton(context).apply {
				setImageResource(android.R.drawable.ic_media_previous)
				setColorFilter(arrowTint)
				background = null
				setOnClickListener {
					val slug = hadithActiveBookSlug ?: return@setOnClickListener
					val target = (hadithCurrentNumber - 1).coerceAtLeast(1)
					if (target != hadithCurrentNumber) requestHadith(slug, target)
				}
			}
			hadithNextButton = ImageButton(context).apply {
				setImageResource(android.R.drawable.ic_media_next)
				setColorFilter(arrowTint)
				background = null
				setOnClickListener {
					val slug = hadithActiveBookSlug ?: return@setOnClickListener
					requestHadith(slug, hadithCurrentNumber + 1)
				}
			}
			hadithNumberInput = EditText(context).apply {
				setTextColor(Color.argb(235, 255, 255, 255))
				textSize = 15f
				setHintTextColor(Color.argb(150, 255, 255, 255))
				hint = "#"
				setSingleLine(true)
				gravity = Gravity.CENTER
				inputType = InputType.TYPE_CLASS_NUMBER
				imeOptions = EditorInfo.IME_ACTION_SEARCH
				setText("1")
				setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
				background = GradientDrawable(
					GradientDrawable.Orientation.LEFT_RIGHT,
					intArrayOf(
						Color.argb(55, 255, 255, 255),
						Color.argb(25, 255, 255, 255),
					),
				).apply {
					cornerRadius = dpToPx(16f).toFloat()
					setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
				}
				setOnEditorActionListener { v, actionId, event ->
					val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
					val isAction = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
					if (!isEnter && !isAction) return@setOnEditorActionListener false
					val slug = hadithActiveBookSlug ?: return@setOnEditorActionListener false
					val n = text?.toString()?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return@setOnEditorActionListener false
					hideKeyboard(v)
					requestHadith(bookSlug = slug, hadithNumber = n)
					true
				}
			}
			hadithNavRow = LinearLayout(context).apply {
				visibility = View.GONE
				orientation = LinearLayout.HORIZONTAL
				gravity = Gravity.CENTER
				setPadding(0, dpToPx(6f), 0, dpToPx(6f))
				addView(hadithPrevButton, LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)))
				addView(hadithNumberInput, LinearLayout.LayoutParams(dpToPx(92f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
					leftMargin = dpToPx(8f)
					rightMargin = dpToPx(8f)
				})
				addView(hadithNextButton, LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f)))
			}
			// Hadith nav row is part of the reading layout (1st row).
			hadithReadingRoot.addView(
				hadithNavRow,
				0,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT,
				).apply {
					leftMargin = dpToPx(18f)
					rightMargin = dpToPx(18f)
					topMargin = dpToPx(10f)
					bottomMargin = dpToPx(6f)
				},
			)
			ViewCompat.setOnApplyWindowInsetsListener(hadithReadingRoot) { _: View, insets: WindowInsetsCompat ->
				val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
				hadithTopInsetPx = top
				applyHadithNavTopInset()
				insets
			}
			hadithReadingRoot.requestApplyInsets()

		}
		labelsOverlay = SurahLabelsOverlayView(context)
		ayahCardsOverlay = AyahCardsOverlayView(
			context = context,
			onNavigate = { delta ->
				navigateActiveAyah(delta)
			},
			onTogglePlayback = {
				toggleContinuousPlayback()
			},
		)
		audioControls = AyahAudioControlsView(
			context = context,
			onMicClick = { toggleActiveAyahPlayback() },
			onReciterSelected = { reciterId ->
				selectedReciterId = reciterId
				selectedReciterIdState.value = reciterId
			},
		)
		translationRow = AyahTranslationRowView(
			context = context,
			onTranslationSelected = { option ->
				selectedTranslationId = option.translationId
				selectedMeaningLang = languageCodeForLabel(option.label)
				requestTranslationRefresh()
				refreshTafsirOptions()
				if (expandedTab == AyahMeaningTabsRowView.Tab.Tafseer) {
					selectedTafsirSlug?.let { slug -> requestTafsirRefresh(slug) }
				}
			},
		)

		linguisticsGraphView = LinguisticsGraphView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				// Taller so cards can hang into the lower screen area.
				dpToPx(360f),
			).apply {
				// Keep a clean gap under the tab row.
				topMargin = 0
			}
		}
		linguisticsWordsCarouselView = LinguisticsWordsCarouselView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT,
			).apply {
				topMargin = dpToPx(8f)
			}
			visibility = View.GONE
		}
		tafseerText = TextView(context).apply {
			setTextColor(Color.WHITE)
			textSize = 14f
			text = "Select a Tafseer from the dropdown."
			setPadding(dpToPx(14f), dpToPx(14f), dpToPx(14f), dpToPx(14f))
		}
		tafseerFocusTitle = TextView(context).apply {
			setTextColor(Color.argb(235, 212, 175, 55))
			textSize = 18f
			text = "Tafseer"
			setSingleLine(true)
			ellipsize = android.text.TextUtils.TruncateAt.END
			setPadding(dpToPx(14f), dpToPx(10f), dpToPx(14f), dpToPx(8f))
		}
		tafseerFocusDropdownButton = ImageButton(context).apply {
			setImageResource(android.R.drawable.arrow_down_float)
			setColorFilter(Color.argb(235, 212, 175, 55))
			background = null
			setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
			setOnClickListener {
				refreshTafsirOptions()
				// Anchor to the full header row so the popup isn't constrained to icon width.
				showTafseerDropdown(anchor = tafseerFocusHeader)
			}
		}
		tafseerFocusCloseButton = ImageButton(context).apply {
			setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
			setColorFilter(Color.argb(235, 212, 175, 55))
			background = null
			setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
			setOnClickListener { exitTafsirMode() }
		}
		tafseerFocusText = TextView(context).apply {
			setTextColor(Color.WHITE)
			textSize = 14f
			text = "Select a Tafseer from the dropdown."
			setPadding(dpToPx(14f), dpToPx(14f), dpToPx(14f), dpToPx(14f))
		}
		tafseerScroll = ScrollView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				dpToPx(230f),
			)
			isFillViewport = true
			background = GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(
					Color.argb(85, 255, 255, 255),
					Color.argb(40, 255, 255, 255),
				),
			).apply {
				cornerRadius = dpToPx(18f).toFloat()
				setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
			}
			addView(
				tafseerText,
				FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
			)
		}
		tafseerFocusScroll = ScrollView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				0,
				1f,
			)
			isFillViewport = true
			background = GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(
					Color.argb(85, 255, 255, 255),
					Color.argb(40, 255, 255, 255),
				),
			).apply {
				cornerRadius = dpToPx(18f).toFloat()
				setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
			}
			addView(
				tafseerFocusText,
				FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
			)
		}
		tafseerFocusHeader = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			addView(
				tafseerFocusTitle,
				LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
			)
			addView(
				tafseerFocusDropdownButton,
				LinearLayout.LayoutParams(dpToPx(34f), dpToPx(34f)).apply { rightMargin = dpToPx(6f) },
			)
			addView(
				tafseerFocusCloseButton,
				LinearLayout.LayoutParams(dpToPx(34f), dpToPx(34f)),
			)
		}
		tafseerFocusContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			visibility = View.GONE
			addView(tafseerFocusHeader)
			addView(tafseerFocusScroll)
		}

		tafsirRepository = TafsirRepositoryImpl(context)
		tafsirDropdownAdapter = TafsirDropdownAdapter(context, tafsirOptions)
		tafseerDropdown = ListPopupWindow(context).apply {
			isModal = true
			setAdapter(tafsirDropdownAdapter)
			setAnimationStyle(R.style.TafseerDropdownAnimation)
			setBackgroundDrawable(
				GradientDrawable(
					GradientDrawable.Orientation.TOP_BOTTOM,
					intArrayOf(
						Color.argb(230, 16, 16, 18),
						Color.argb(215, 10, 10, 12),
					),
				).apply {
					cornerRadius = dpToPx(18f).toFloat()
				},
			)
			setOnItemClickListener { _, _, position, _ ->
				val entry = tafsirOptions.getOrNull(position) ?: return@setOnItemClickListener
				selectedTafsirSlug = entry.slug
				selectedTafsirDisplayName = entry.displayName
				dismiss()
				setTafsirFocusMode(true)
				requestTafsirRefresh(entry.slug, forceRefresh = true)
			}
		}
		refreshTafsirOptions()
		meaningExpandedContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			visibility = View.GONE
			// No card background for the linguistics graph.
			background = null
			addView(linguisticsGraphView)
			addView(linguisticsWordsCarouselView)
			addView(tafseerScroll)
		}
		tafseerScroll.visibility = View.GONE

		meaningTabsRow = AyahMeaningTabsRowView(
			context = context,
			onTabToggled = { tab ->
				setExpandedMeaningTab(tab, collapseWheel = true)
			},
		)
		bookView = OpenBookView(context)
		renderer = SurahWheelRenderer { projectedCenters, centerIndex, turnProgress, turnDir ->
			labelsOverlay.updateProjectedCenters(projectedCenters)
			val i = centerIndex.coerceIn(0, SURA_NAMES.size - 1)
			labelsOverlay.setActiveIndex(i)
			val surahNumber = i + 1
			if (surahNumber != activeSurahNumber) {
				activeSurahNumber = surahNumber
				activeAyahNumber = 1
				activeAyahVerseKey = "$surahNumber:1"
				activeAyahTextUthmani = ""
				setAutoPlayEnabled(false)
				bookView.updateAyahIndicator(ayahNumber = 1, playModeEnabled = false)
				updateBottomCollapsedBar()
				// If the user scrolls while audio is playing, stop to avoid mismatch.
				if (isPlaying) stopPlayback()
			}
			val fallbackName = SURA_NAMES[i]
			val ar = chapterArabic
			val en = chapterEnglish
			val meaning = chapterMeaning
			val verses = chapterVerses
			val revelationType = revelationTypeForIndex(i)
			val juzRange = juzRangeForIndex(i)
			if (ar.size == SURA_NAMES.size && en.size == SURA_NAMES.size && meaning.size == SURA_NAMES.size && verses.size == SURA_NAMES.size) {
				bookView.updateSurahInfo(
					surahNumber = surahNumber,
					nameEnglish = en[i],
					nameArabic = ar[i],
					meaningEnglish = meaning[i],
					versesCount = verses[i],
					revelationType = revelationType,
					juzRange = juzRange,
				)
			} else {
				bookView.updateSurahInfo(
					surahNumber = surahNumber,
					nameEnglish = fallbackName,
					nameArabic = null,
					meaningEnglish = null,
					versesCount = null,
					revelationType = revelationType,
					juzRange = juzRange,
				)
			}

			// Fetch and cache Juz range lazily (start/end Juz based on first/last verse).
			if (juzRange == null) {
				requestJuzRangeIfNeeded(surahNumber = surahNumber, surahIndex = i)
			}

			// Top cards: show 2 ayahs from the active/central surah.
			if (i != lastAyahSurahIndex) {
				lastAyahSurahIndex = i
				ayahFetchJob?.cancel()
				val token = ayahFetchToken + 1
				ayahFetchToken = token
				ayahCardsOverlay.setLoading(surahNumber)
				ayahFetchJob = scope.launch {
					// Small debounce so fast scrolling doesn't spam network calls.
					delay(180L)
					val (a1, a2) = fetchTwoAyahs(surahNumber)
					// Guard against stale results arriving after the center surah has changed.
					if (ayahFetchToken == token && lastAyahSurahIndex == i) {
							activeAyahNumber = 1
							activeAyahTextUthmani = a1
							activeAyahVerseKey = "$surahNumber:1"
							bookView.updateAyahIndicator(ayahNumber = 1, playModeEnabled = autoPlayEnabled)
							ayahCardsOverlay.setAyahs(surahNumber, a1, a2)
							ayahCardsOverlay.setNavAvailability(
								canPrev = false,
								canNext = 1 < versesCountForSurah(surahNumber),
							)
						if (expandedTab == AyahMeaningTabsRowView.Tab.Linguistics) {
							linguisticsGraphView.post { linguisticsGraphView.setAyahText(a1) }
							requestLinguisticsRefresh()
						} else if (expandedTab == AyahMeaningTabsRowView.Tab.Tafseer) {
							selectedTafsirSlug?.let { slug -> requestTafsirRefresh(slug) }
						}
					}
				}
			}

			// Translation row: fetch translation for the active ayah.
			if (i != lastTranslationSurahIndex) {
				lastTranslationSurahIndex = i
				requestTranslationRefresh()
			}
			bookView.updateTurn(turnProgress, turnDir)
		}
		glView = SurahWheelGLSurfaceView(context, renderer)

		topMenuButton = ArabicCircularCalligraphyButtonView(context).apply {
			setLogoFromAssets("global_menu_logo.png")
			contentDescription = "Top menu"
			setOnClickListener {
				showGlobeMenuOverlay(animateBottomCard = true)
			}
		}

		// Bottom card: single container for both wheel + book.
		val handleLine = View(context).apply {
			background = GradientDrawable().apply {
				cornerRadius = dpToPx(2f).toFloat()
				setColor(Color.argb(120, 255, 255, 255))
			}
		}
		val handleContainer = FrameLayout(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				dpToPx(26f),
			)
			setOnClickListener { setBottomCardCollapsed(collapsed = true, animate = true) }
			addView(
				handleLine,
				FrameLayout.LayoutParams(dpToPx(42f), dpToPx(4f)).apply {
					gravity = Gravity.CENTER
				},
			)
		}

		val wheelFrame = FrameLayout(context).apply {
			layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
			addView(
				glView,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				),
			)
			addView(
				labelsOverlay,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				),
			)
		}

		bookView.isClickable = true
		bookView.setOnTouchListener { _, _ -> true }
		val rightHalf = FrameLayout(context).apply {
			layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
			// Match wheel background: GL starfield behind the book.
			addView(
				StarfieldGLSurfaceView(context),
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				),
			)
			addView(
				bookView,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				).apply {
					gravity = Gravity.CENTER
				},
			)
		}

		val wheelAndBookRow = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			addView(wheelFrame)
			addView(rightHalf)
		}

		bottomCardExpandedContent = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)
			addView(handleContainer)
			addView(
				wheelAndBookRow,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					0,
					1f,
				),
			)
		}

		bottomCardCollapsedSurahText = TextView(context).apply {
			setTextColor(Color.WHITE)
			textSize = 16f
			isSingleLine = true
			ellipsize = android.text.TextUtils.TruncateAt.END
			maxWidth = dpToPx(210f)
			typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.ITALIC)
			textAlignment = View.TEXT_ALIGNMENT_CENTER
			gravity = Gravity.CENTER_VERTICAL
		}
		bottomCardCollapsedAyahBadge = SparklingAyahBadgeView(context).apply {
			isClickable = false
			isFocusable = false
		}
		bottomCardCollapsedTotalVersesText = TextView(context).apply {
			setTextColor(Color.WHITE)
			textSize = 15f
			isSingleLine = true
			typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.BOLD)
			textAlignment = View.TEXT_ALIGNMENT_CENTER
			gravity = Gravity.CENTER_VERTICAL
		}

		bottomCardCollapsedBar = FrameLayout(context).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)
			setPadding(dpToPx(14f), 0, dpToPx(14f), 0)
			val collapsedRow = LinearLayout(context).apply {
				orientation = LinearLayout.HORIZONTAL
				gravity = Gravity.CENTER_VERTICAL
				layoutParams = FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.WRAP_CONTENT,
					FrameLayout.LayoutParams.WRAP_CONTENT,
				).apply {
					gravity = Gravity.CENTER
				}
			}
			collapsedRow.addView(
				bottomCardCollapsedSurahText,
				LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
					rightMargin = dpToPx(6f)
				},
			)
			collapsedRow.addView(
				bottomCardCollapsedAyahBadge,
				LinearLayout.LayoutParams(dpToPx(30f), dpToPx(30f)).apply {
					leftMargin = dpToPx(6f)
					rightMargin = dpToPx(6f)
				},
			)
			collapsedRow.addView(
				bottomCardCollapsedTotalVersesText,
				LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
					leftMargin = dpToPx(6f)
				},
			)
			addView(collapsedRow)
			setOnClickListener { setBottomCardCollapsed(collapsed = false, animate = true) }
		}
		updateBottomCollapsedBar()

		// Let the global starfield show through the expanded card (match wheel's starfield vibe).
		bottomCardExpandedBackground = GradientDrawable().apply {
			setColor(Color.TRANSPARENT)
			cornerRadius = dpToPx(18f).toFloat()
			setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
		}

		bottomCard = FrameLayout(context).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				dpToPx(320f),
			).apply {
				gravity = Gravity.BOTTOM
				leftMargin = bottomCardExpandedMarginPx
				rightMargin = bottomCardExpandedMarginPx
				bottomMargin = bottomCardExpandedMarginPx
			}
			background = bottomCardExpandedBackground
			addView(bottomCardExpandedContent)
			addView(bottomCardCollapsedBar)
			addView(
				topMenuButton,
				FrameLayout.LayoutParams(dpToPx(54f), dpToPx(54f)).apply {
					gravity = Gravity.END or Gravity.BOTTOM
					rightMargin = dpToPx(12f)
					bottomMargin = dpToPx(10f)
				},
			)
		}
		// Start expanded.
		bottomCardCurrentHeightPx = dpToPx(320f)
		bottomCardExpandedContent.visibility = View.VISIBLE
		bottomCardCollapsedBar.visibility = View.GONE

		root = FrameLayout(context).apply {
			starBackground.layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)
			addView(starBackground)

			ayahCardsOverlay.layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)
			addView(ayahCardsOverlay)

			audioControls.layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				dpToPx(48f),
			).apply {
				gravity = Gravity.TOP
				val m = dpToPx(16f)
				// Place under the single top card. Card is ~86dp min height with 16dp top margin.
				topMargin = dpToPx(16f + 86f + 12f)
				leftMargin = m
				rightMargin = m
			}
			addView(audioControls)

			translationContainer = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
			}
			translationContainer.layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT,
			).apply {
				gravity = Gravity.TOP
				val m = dpToPx(16f)
				// Under the recitation row.
				topMargin = dpToPx(16f + 86f + 12f + 48f + 10f)
				leftMargin = m
				rightMargin = m
			}
			translationContainer.addView(
				translationRow,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT,
				),
			)
			translationContainer.addView(
				meaningTabsRow,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT,
				).apply {
					topMargin = dpToPx(8f)
				},
			)
			translationContainer.addView(
				meaningExpandedContainer,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT,
				).apply {
					topMargin = 0
				},
			)
			addView(translationContainer)

			tafseerFocusContainer.layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			).apply {
				gravity = Gravity.TOP
				val m = dpToPx(16f)
				leftMargin = m
				rightMargin = m
				// Immediately under the Arabic ayah card.
				topMargin = dpToPx(16f + 86f + 12f)
				// Leave space above the bottom card.
				bottomMargin = bottomCardCurrentHeightPx + dpToPx(10f)
			}
			addView(tafseerFocusContainer)

			addView(bottomCard)
			addView(topMenuOverlay)

			// Mini Listen mode: floating draggable logo overlay (hidden by default).
			listenMiniLogo = ImageView(context).apply {
				contentDescription = "Listen mini player"
				isClickable = true
				isFocusable = true
				scaleType = ImageView.ScaleType.FIT_CENTER
				background = GradientDrawable(
					GradientDrawable.Orientation.TOP_BOTTOM,
					intArrayOf(
						Color.argb(210, 15, 18, 26),
						Color.argb(210, 8, 10, 16),
					),
				).apply {
					cornerRadius = dpToPx(22f).toFloat()
					setStroke(dpToPx(1f), Color.argb(90, 255, 255, 255))
				}
				setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
				val bmp = runCatching {
					val candidates = listOf("global_menu_logo.png", "logo.png")
					for (name in candidates) {
						val b = runCatching {
							context.assets.open(name).use { input ->
								BitmapFactory.decodeStream(input)
							}
						}.getOrNull()
						if (b != null) return@runCatching b
					}
					null
				}.getOrNull()
				if (bmp != null) {
					setImageBitmap(bmp)
				} else {
					setImageResource(android.R.drawable.ic_media_play)
					setColorFilter(Color.argb(235, 212, 175, 55))
				}
				setOnClickListener {
					exitListenMiniMode()
				}
				// Drag behavior.
				var downRawX = 0f
				var downRawY = 0f
				var startX = 0f
				var startY = 0f
				setOnTouchListener { v, event ->
					when (event.actionMasked) {
						MotionEvent.ACTION_DOWN -> {
							downRawX = event.rawX
							downRawY = event.rawY
							startX = v.x
							startY = v.y
							true
						}
						MotionEvent.ACTION_MOVE -> {
							val dx = event.rawX - downRawX
							val dy = event.rawY - downRawY
							val parentW = (v.parent as? View)?.width ?: 0
							val parentH = (v.parent as? View)?.height ?: 0
							val newX = (startX + dx).coerceIn(0f, (parentW - v.width).toFloat().coerceAtLeast(0f))
							val newY = (startY + dy).coerceIn(0f, (parentH - v.height).toFloat().coerceAtLeast(0f))
							v.x = newX
							v.y = newY
							true
						}
						MotionEvent.ACTION_UP -> {
							// Treat a tiny move as a click.
							val dist = kotlin.math.abs(event.rawX - downRawX) + kotlin.math.abs(event.rawY - downRawY)
							if (dist < dpToPx(6f)) v.performClick()
							true
						}
						else -> false
					}
				}
			}
			listenMiniOverlay = FrameLayout(context).apply {
				visibility = View.GONE
				isClickable = false
				isFocusable = false
				background = null
				addView(
					listenMiniLogo,
					FrameLayout.LayoutParams(dpToPx(68f), dpToPx(68f)).apply {
						gravity = Gravity.TOP or Gravity.START
						leftMargin = dpToPx(16f)
						topMargin = dpToPx(54f)
					},
				)
			}
			addView(
				listenMiniOverlay,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT,
				),
			)

			// Keep the bottom card expanded height at half screen when expanded.
			addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
				val rootH = height.takeIf { it > 0 } ?: return@addOnLayoutChangeListener
				val expandedH = (rootH / 2).coerceAtLeast(dpToPx(240f))
				if (bottomCardExpandedContent.visibility == View.VISIBLE) {
					val lp = bottomCard.layoutParams
					if (lp != null && lp.height != expandedH) {
						lp.height = expandedH
						bottomCard.layoutParams = lp
						bottomCardCurrentHeightPx = expandedH
					}
				}
				val newBottom = (bottomCardCurrentHeightPx + dpToPx(10f)).coerceAtLeast(dpToPx(54f))
				val focusLp = tafseerFocusContainer.layoutParams
				if (focusLp is ViewGroup.MarginLayoutParams && focusLp.bottomMargin != newBottom) {
					focusLp.bottomMargin = newBottom
					tafseerFocusContainer.layoutParams = focusLp
				}
			}
		}

		refreshListenSurahItems()

		// App start: open the globe page first so the user can choose where to go.
		showGlobeMenuOverlay(animateBottomCard = false)

		scope.launch {
			try {
				val reciters = withContext(Dispatchers.IO) { audioApi.listReciters() }
				audioControls.setReciters(reciters)
				listenRecitersState.value = reciters
				if (selectedReciterId <= 0) {
					selectedReciterId = reciters.firstOrNull()?.id ?: -1
					selectedReciterIdState.value = selectedReciterId
					audioControls.setSelectedReciterId(selectedReciterId)
				} else {
					selectedReciterIdState.value = selectedReciterId
				}
			} catch (_: Throwable) {
				// Ignore; controls will show empty list.
				listenRecitersState.value = emptyList()
			}
		}

		scope.launch {
			try {
				val translations = withContext(Dispatchers.IO) { api.getTranslations().translations }
				val options = buildPopularLanguageOptions(translations)
				translationRow.setOptions(options)
				// Default: English
				translationRow.setSelectedTranslationId(selectedTranslationId)
				selectedMeaningLang = languageCodeForLabel(
					options.firstOrNull { it.translationId == selectedTranslationId }?.label ?: "English",
				)
				requestTranslationRefresh()
			} catch (_: Throwable) {
				translationRow.setOptions(
					listOf(
						AyahTranslationRowView.TranslationOption(
							translationId = QuranApi.DEFAULT_EN_TRANSLATION_ID,
							label = "English",
						),
					),
				)
				translationRow.setSelectedTranslationId(selectedTranslationId)
				selectedMeaningLang = "en"
				requestTranslationRefresh()
			}
		}
	}

	private fun languageCodeForLabel(label: String): String {
		return when (label.trim().lowercase()) {
			"english" -> "en"
			"urdu" -> "ur"
			"bengali" -> "bn"
			"turkish" -> "tr"
			"indonesian" -> "id"
			"french" -> "fr"
			"spanish" -> "es"
			"german" -> "de"
			"russian" -> "ru"
			"chinese" -> "zh"
			"hindi" -> "hi"
			"arabic" -> "ar"
			else -> "en"
		}
	}

	private fun setTafsirFocusMode(enabled: Boolean) {
		if (tafsirFocusMode == enabled) return
		tafsirFocusMode = enabled
		if (enabled) {
			ayahCardsOverlay.setPlayButtonVisible(false)
			audioControls.visibility = View.GONE
			translationContainer.visibility = View.GONE
			meaningExpandedContainer.visibility = View.GONE
			tafseerDropdown.dismiss()
			val title = selectedTafsirDisplayName?.trim().orEmpty().ifBlank { "Tafseer" }
			tafseerFocusTitle.text = title
			tafseerFocusText.text = tafseerText.text
			tafseerFocusContainer.visibility = View.VISIBLE
		} else {
			ayahCardsOverlay.setPlayButtonVisible(true)
			audioControls.visibility = View.VISIBLE
			translationContainer.visibility = View.VISIBLE
			meaningExpandedContainer.visibility = if (expandedTab != null) View.VISIBLE else View.GONE
			tafseerFocusContainer.visibility = View.GONE
		}
	}

	private fun exitTafsirMode() {
		// Collapse the tafseer/linguistics expansion and restore the normal cards.
		expandedTab = null
		meaningTabsRow.setSelectedTab(null)
		setTafsirFocusMode(false)
		meaningExpandedContainer.visibility = View.GONE
		linguisticsFetchJob?.cancel()
		tafseerFetchJob?.cancel()
		tafseerDropdown.dismiss()
	}

	private fun requestTranslationRefresh() {
		val surah = activeSurahNumber
		val ayah = activeAyahNumber
		val verseKey = "$surah:$ayah"
		val translationId = selectedTranslationId
		translationFetchJob?.cancel()
		val token = translationFetchToken + 1
		translationFetchToken = token
		translationRow.setLoading()
		translationFetchJob = scope.launch {
			delay(180L)
			val text = withContext(Dispatchers.IO) {
				try {
					if (translationId == QuranApi.DEFAULT_EN_TRANSLATION_ID) {
						api.getEnglishMeaningByVerseKey(translationId = translationId, verseKey = verseKey)
							.translations
							.firstOrNull()
							?.text
							?.stripHtmlTags()
							?.trim()
							.orEmpty()
					} else {
						backendApi.getVerseTranslation(translationId = translationId, verseKey = verseKey)
							.text
							.stripHtmlTags()
							.trim()
					}
				} catch (_: Throwable) {
					""
				}
			}
			if (translationFetchToken == token && activeSurahNumber == surah && activeAyahNumber == ayah && selectedTranslationId == translationId) {
				translationRow.setTranslation(text)
			}
		}
	}

	private fun versesCountForSurah(surahNumber: Int): Int {
		val verses = chapterVerses
		if (verses.size == SURA_NAMES.size) {
			return verses.getOrNull(surahNumber - 1)?.coerceAtLeast(1) ?: 1
		}
		return 2
	}

	private fun refreshListenSurahItems() {
		val en = chapterEnglish
		val verses = chapterVerses
		listenSurahItemsState.value = List(SURA_NAMES.size) { i ->
			val n = i + 1
			val name = en.getOrNull(i)?.trim().takeUnless { it.isNullOrBlank() } ?: SURA_NAMES[i]
			val count = if (verses.size == SURA_NAMES.size) verses.getOrNull(i) ?: 2 else 2
			ListenSurahItem(
				surahNumber = n,
				label = "$n. $name",
				ayahCount = count.coerceAtLeast(1),
			)
		}
	}

	private fun navigateActiveAyah(delta: Int, autoPlayAfterNavigation: Boolean = false) {
		if (delta == 0) return
		val surah = activeSurahNumber
		val versesCount = versesCountForSurah(surah)
		val current = activeAyahNumber.coerceIn(1, versesCount)
		val next = (current + delta).coerceIn(1, versesCount)
		if (next == current) {
			if (autoPlayAfterNavigation) setAutoPlayEnabled(false)
			return
		}

		activeAyahNumber = next
		activeAyahVerseKey = "$surah:$next"
		updateBottomCollapsedBar()
		bookView.updateAyahIndicator(ayahNumber = next, playModeEnabled = autoPlayEnabled)
		if (isPlaying) stopPlayback()

		ayahFetchJob?.cancel()
		val token = ayahFetchToken + 1
		ayahFetchToken = token
		ayahCardsOverlay.setLoading(surah)
		ayahCardsOverlay.setNavAvailability(
			canPrev = next > 1,
			canNext = next < versesCount,
		)
		ayahFetchJob = scope.launch {
			// Small debounce to avoid spam on rapid taps.
			delay(80L)
			val (active, upcoming) = fetchActiveAndNextAyah(surahNumber = surah, ayahNumber = next)
			if (ayahFetchToken != token) return@launch
			if (activeSurahNumber != surah) return@launch
			if (activeAyahNumber != next) return@launch

			activeAyahTextUthmani = active
			ayahCardsOverlay.setAyahs(surah, active, upcoming)
			ayahCardsOverlay.setNavAvailability(
				canPrev = next > 1,
				canNext = next < versesCount,
			)
			requestTranslationRefresh()
			when (expandedTab) {
				AyahMeaningTabsRowView.Tab.Linguistics -> {
					linguisticsGraphView.post { linguisticsGraphView.setAyahText(active) }
					requestLinguisticsRefresh()
				}
				AyahMeaningTabsRowView.Tab.Tafseer -> {
					selectedTafsirSlug?.let { slug -> requestTafsirRefresh(slug) }
				}
				null -> Unit
			}
			if (autoPlayAfterNavigation || autoPlayEnabled) {
				playActiveAyah()
			}
		}
	}

	private fun requestLinguisticsRefresh() {
		val verseKey = activeAyahVerseKey
		val ayat = activeAyatReference()
		linguisticsFetchJob?.cancel()
		val token = linguisticsFetchToken + 1
		linguisticsFetchToken = token
		linguisticsFetchJob = scope.launch {
			delay(120L)
			val (wordsForGraph, detailsForGraph, cards) = withContext(Dispatchers.IO) {
				val arabicWords = ArabicTextUtils.tokenizeAyah(activeAyahTextUthmani).map { it.originalText }
				val supabaseConfigured = supabaseLinguisticsClient.isConfigured()
				if (!supabaseConfigured) {
					Log.w(
						"SupabaseLinguistics",
						"Supabase not configured; set SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_STORAGE_BUCKET",
					)
				}

				// 1) Preferred: Supabase (your curated JSON + storage imageIds)
				try {
					if (supabaseConfigured) {
						val supabaseWords = supabaseLinguisticsClient.fetchWordsJson(
							chapter = ayat.surahNumber,
							verse = ayat.ayahNumber,
						)
						// Resolve Storage images (signed URLs for private buckets) before handing off to UI.
						val cardModelsResolved = supabaseLinguisticsClient.toCardModelsResolvedImages(
							chapter = ayat.surahNumber,
							verse = ayat.ayahNumber,
							words = supabaseWords,
							fallbackArabicWords = arabicWords,
						)
						if (cardModelsResolved.isEmpty()) {
							Log.w(
								"SupabaseLinguistics",
								"Supabase returned 0 cards for chapter=${ayat.surahNumber} verse=${ayat.ayahNumber}",
							)
						}

						if (cardModelsResolved.isNotEmpty()) {
							val words = cardModelsResolved.map { it.arabic }
							val details = cardModelsResolved.map {
								val s = it.meaning.trim()
								if (s.length > 18) s.take(17) + "…" else s
							}
							return@withContext Triple(words, details, cardModelsResolved)
						}
					}
				} catch (t: Throwable) {
					// Fall back below.
					Log.w(
						"SupabaseLinguistics",
						"Supabase linguistics fetch failed (chapter=${ayat.surahNumber} verse=${ayat.ayahNumber})",
						t,
					)
				}

				// 2) Fallback: backend/quran.com words (no images)
				val fallbackPairs: List<Pair<String, String>> = try {
					val backend = backendApi.getAyahWords(verseKey = verseKey)
					backend.words.map {
						it.text to (it.transliteration?.trim().orEmpty().ifBlank { it.translation?.trim().orEmpty() })
					}
				} catch (_: Throwable) {
					try {
						val upstream = api.getVerseByKeyWithWords(verseKey = verseKey)
						upstream.verse.words.map { w ->
							w.text to (w.transliteration?.text?.trim().orEmpty().ifBlank { w.translation?.text?.trim().orEmpty() })
						}
					} catch (_: Throwable) {
						emptyList()
					}
				}
				val labels = fallbackPairs.map { it.first }
				val details = fallbackPairs.map { it.second.let { s -> if (s.length > 18) s.take(17) + "…" else s } }
				Triple(labels, details, emptyList())
			}
			if (linguisticsFetchToken != token) return@launch
			if (expandedTab != AyahMeaningTabsRowView.Tab.Linguistics) return@launch
			if (cards.isNotEmpty()) {
				linguisticsGraphView.setCards(cards, seed = verseKey.hashCode())
				linguisticsWordsCarouselView.setWords(emptyList())
			} else {
				if (wordsForGraph.isNotEmpty()) {
					linguisticsGraphView.setWordsWithDetails(wordsForGraph, detailsForGraph, seed = verseKey.hashCode())
				}
				linguisticsWordsCarouselView.setWords(emptyList())
			}
		}
	}

	private fun showTafseerDropdown(anchor: View = meaningTabsRow) {
		if (tafsirOptions.isEmpty()) {
			val msg = "No Tafseer sources available for this language."
			tafseerText.text = msg
			tafseerFocusText.text = msg
			return
		}
		tafseerDropdown.anchorView = anchor
		// Use available screen/root width so long source names remain readable.
		val rootW = root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
		val contentW = (rootW - dpToPx(32f)).coerceAtLeast(dpToPx(260f))
		tafseerDropdown.setContentWidth(contentW)
		// Prefer opening downwards by constraining height to available space below the anchor.
		val loc = IntArray(2)
		anchor.getLocationOnScreen(loc)
		val anchorBottomY = loc[1] + anchor.height
		val screenH = context.resources.displayMetrics.heightPixels
		val bottomOccupied = bottomCardCurrentHeightPx.coerceAtLeast(bottomCardCollapsedHeightPx)
		val availableBelow = (screenH - anchorBottomY - bottomOccupied - dpToPx(18f)).coerceAtLeast(dpToPx(160f))
		val maxH = (screenH * 0.46f).toInt().coerceAtLeast(dpToPx(260f))
		tafseerDropdown.height = minOf(maxH, availableBelow)
		tafseerDropdown.verticalOffset = dpToPx(10f)
		if (!tafseerDropdown.isShowing) tafseerDropdown.show()
	}

	private fun updateBottomCollapsedBar() {
		val surahIdx = (activeSurahNumber - 1).coerceIn(0, SURA_NAMES.size - 1)
		val en = chapterEnglish
		val surahName = en.getOrNull(surahIdx)?.trim().takeUnless { it.isNullOrBlank() } ?: SURA_NAMES[surahIdx]
		bottomCardCollapsedSurahText.text = surahName
		bottomCardCollapsedAyahBadge.setAyahNumber(activeAyahNumber.coerceAtLeast(1))
		val totalVerses = chapterVerses.getOrNull(surahIdx)
		bottomCardCollapsedTotalVersesText.text = totalVerses?.toString().orEmpty()
	}

	private fun showGlobeMenuOverlay(animateBottomCard: Boolean) {
		// Ensure other expanded panels are closed so the menu is truly "top-level".
		setExpandedMeaningTab(tab = null, collapseWheel = false)
		// Collapse the wheel container so the overlay reads as the main menu.
		setBottomCardCollapsed(collapsed = true, animate = animateBottomCard)
		// Always start from the globe.
		globeMenuView.visibility = View.VISIBLE
		globeFooterView.visibility = View.VISIBLE
		hadithMenuContainer.visibility = View.GONE
		listenMenuContainer.visibility = View.GONE
		tajweedMenuContainer.visibility = View.GONE
		stopListenPlayback()
		resetHadithMenuState()
		topMenuOverlay.visibility = View.VISIBLE
		topMenuOverlay.bringToFront()
	}

	private fun setBottomCardCollapsed(collapsed: Boolean, animate: Boolean) {
		bottomCardAnimator?.cancel()
		val rootH = root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
		val expandedH = (rootH / 2).coerceAtLeast(dpToPx(240f))
		val targetH = if (collapsed) bottomCardCollapsedHeightPx else expandedH
		val lp = bottomCard.layoutParams as FrameLayout.LayoutParams
		val startH = lp.height

		fun applyChrome(isCollapsed: Boolean) {
			val params = bottomCard.layoutParams as FrameLayout.LayoutParams
			if (isCollapsed) {
				bottomCard.background = null
				params.leftMargin = 0
				params.rightMargin = 0
				params.bottomMargin = 0
			} else {
				bottomCard.background = bottomCardExpandedBackground
				params.leftMargin = bottomCardExpandedMarginPx
				params.rightMargin = bottomCardExpandedMarginPx
				params.bottomMargin = bottomCardExpandedMarginPx
			}
			bottomCard.layoutParams = params
		}

		// Ensure expanded state looks like a card immediately.
		if (!collapsed) {
			// Expanding the wheel/book area collapses any expanded meaning tabs.
			setExpandedMeaningTab(tab = null, collapseWheel = false)
			applyChrome(isCollapsed = false)
		}

		if (!animate || startH == targetH) {
			lp.height = targetH
			bottomCard.layoutParams = lp
			bottomCardCurrentHeightPx = targetH
			bottomCardExpandedContent.visibility = if (collapsed) View.GONE else View.VISIBLE
			bottomCardCollapsedBar.visibility = if (collapsed) View.VISIBLE else View.GONE
			applyChrome(isCollapsed = collapsed)
			updateBottomCollapsedBar()
			return
		}

		if (!collapsed) {
			bottomCardExpandedContent.visibility = View.VISIBLE
			bottomCardCollapsedBar.visibility = View.GONE
		}

		bottomCardAnimator = ValueAnimator.ofInt(startH, targetH).apply {
			duration = 230L
			interpolator = DecelerateInterpolator()
			addUpdateListener { anim ->
				val h = (anim.animatedValue as Int).coerceAtLeast(1)
				val params = bottomCard.layoutParams as FrameLayout.LayoutParams
				params.height = h
				bottomCard.layoutParams = params
				bottomCardCurrentHeightPx = h
			}
			addListener(object : android.animation.AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: android.animation.Animator) {
					bottomCardExpandedContent.visibility = if (collapsed) View.GONE else View.VISIBLE
					bottomCardCollapsedBar.visibility = if (collapsed) View.VISIBLE else View.GONE
					// Apply collapsed chrome at the end so the card shrinks naturally.
					applyChrome(isCollapsed = collapsed)
					updateBottomCollapsedBar()
				}
			})
			start()
		}
	}

	private fun activeAyatReference(): AyatReference {
		val key = activeAyahVerseKey
		val parts = key.split(':')
		val surah = parts.getOrNull(0)?.toIntOrNull() ?: activeSurahNumber
		val ayah = parts.getOrNull(1)?.toIntOrNull() ?: 1
		return AyatReference(surahNumber = surah, ayahNumber = ayah)
	}

	private fun requestTafsirRefresh(slug: String, forceRefresh: Boolean = false) {
		tafseerFetchJob?.cancel()
		val token = tafseerFetchToken + 1
		tafseerFetchToken = token
		val ayat = activeAyatReference()
		val option = tafsirOptions.firstOrNull { it.slug == slug }
		val title = option?.displayName ?: "Tafsir"
		selectedTafsirDisplayName = title
		tafseerFocusTitle.text = title
		val loadingText = "Loading $title…"
		tafseerText.text = loadingText
		tafseerFocusText.text = loadingText
		tafseerFetchJob = scope.launch {
			val text = withContext(Dispatchers.IO) {
				try {
					val result = tafsirRepository.getTafsirAyah(
						TafsirRequest(
							surahNumber = ayat.surahNumber,
							ayahNumber = ayat.ayahNumber,
							selectedTranslationLanguageCode = selectedMeaningLang,
							selectedTafsirSlug = slug,
							forceRefresh = forceRefresh,
						),
					)
					result.bestContent()
						?: result.error?.message
						.orEmpty()
				} catch (_: Throwable) {
					""
				}
			}
			if (tafseerFetchToken != token) return@launch
			if (expandedTab != AyahMeaningTabsRowView.Tab.Tafseer && !tafsirFocusMode) return@launch
			val cleaned = text
				.stripHtmlTags()
				.replace("\\r\\n", "\n")
				.replace("\\n", "\n")
				.trim()
				.ifBlank { "No Tafseer found for $title at ${ayat.verseKey}." }
			tafseerText.text = cleaned
			tafseerFocusText.text = cleaned
		}
	}

	private fun refreshTafsirOptions() {
		val lang = selectedMeaningLang
		scope.launch {
			val options = withContext(Dispatchers.IO) {
				runCatching { tafsirRepository.getTafsirSourcesForLanguage(lang) }.getOrDefault(emptyList())
			}
			tafsirOptions = options
			tafsirDropdownAdapter.update(options)

			val current = selectedTafsirSlug
			if (current != null && options.none { it.slug == current }) {
				selectedTafsirSlug = null
				if (expandedTab == AyahMeaningTabsRowView.Tab.Tafseer) {
					tafseerText.text = "Select a Tafseer from the dropdown."
				}
			}
		}
	}

	private fun buildPopularLanguageOptions(all: List<TranslationResourceDto>): List<AyahTranslationRowView.TranslationOption> {
		val wanted = listOf(
			"English",
			"Urdu",
			"Bengali",
			"Turkish",
			"Indonesian",
			"French",
			"Spanish",
			"German",
			"Russian",
			"Chinese",
			"Hindi",
		)
		val byLang = all.groupBy { it.languageName.trim() }
		val options = mutableListOf<AyahTranslationRowView.TranslationOption>()
		for (lang in wanted) {
			val candidate = byLang.entries.firstOrNull { (k, _) -> k.equals(lang, ignoreCase = true) }?.value?.firstOrNull()
			if (candidate != null) {
				options += AyahTranslationRowView.TranslationOption(
					translationId = candidate.id,
					label = lang,
				)
			}
		}
		if (options.none { it.translationId == QuranApi.DEFAULT_EN_TRANSLATION_ID } && options.none { it.label.equals("English", true) }) {
			options.add(
				0,
				AyahTranslationRowView.TranslationOption(
					translationId = QuranApi.DEFAULT_EN_TRANSLATION_ID,
					label = "English",
				),
			)
		}
		return options.distinctBy { it.translationId }
	}

	private fun toggleActiveAyahPlayback() {
		if (isPlaying) {
			setAutoPlayEnabled(false)
			stopPlayback()
			return
		}
		setAutoPlayEnabled(false)
		playActiveAyah()
	}

	private fun toggleContinuousPlayback() {
		if (autoPlayEnabled) {
			setAutoPlayEnabled(false)
			if (isPlaying) stopPlayback()
			return
		}
		setAutoPlayEnabled(true)
		if (isPlaying) stopPlayback()
		playActiveAyah()
	}

	private fun setAutoPlayEnabled(enabled: Boolean) {
		autoPlayEnabled = enabled
		meaningTabsRow.setTabsEnabled(!enabled)
		bookView.updateAyahIndicator(ayahNumber = activeAyahNumber, playModeEnabled = enabled)
		setLowPowerGlRendering(enabled)
		if (!enabled) stopKeepAliveService()
		if (enabled) {
			expandedTab = null
			meaningTabsRow.setSelectedTab(null)
			meaningExpandedContainer.visibility = View.GONE
			linguisticsFetchJob?.cancel()
			tafseerFetchJob?.cancel()
			tafseerDropdown.dismiss()
		}
	}

	private fun setLowPowerGlRendering(enabled: Boolean) {
		if (lowPowerGlRendering == enabled) return
		lowPowerGlRendering = enabled
		runCatching { glView.setLowPowerModeEnabled(enabled) }
	}

	private fun startKeepAliveService() {
		val appCtx = context.applicationContext
		val intent = Intent(appCtx, PlaybackKeepAliveService::class.java).apply {
			action = PlaybackKeepAliveService.ACTION_START
		}
		try {
			if (android.os.Build.VERSION.SDK_INT >= 26) {
				appCtx.startForegroundService(intent)
			} else {
				appCtx.startService(intent)
			}
		} catch (_: Throwable) {
		}
	}

	private fun stopKeepAliveService() {
		val appCtx = context.applicationContext
		val intent = Intent(appCtx, PlaybackKeepAliveService::class.java).apply {
			action = PlaybackKeepAliveService.ACTION_STOP
		}
		try {
			appCtx.startService(intent)
		} catch (_: Throwable) {
			runCatching { appCtx.stopService(Intent(appCtx, PlaybackKeepAliveService::class.java)) }
		}
	}

	private fun stopPlayback() {
		try {
			mediaPlayer?.run {
				try { stop() } catch (_: Throwable) {}
				release()
			}
		} catch (_: Throwable) {
		} finally {
			mediaPlayer = null
			isPlaying = false
			pausedForFocusLoss = false
			audioControls.setPlaying(false)
			ayahCardsOverlay.setPlaying(false)
			abandonAudioFocus()
			if (!autoPlayEnabled) stopKeepAliveService()
		}
	}

	private fun pausePlaybackForFocusLoss() {
		val mp = mediaPlayer ?: return
		try {
			if (isPlaying) {
				mp.pause()
			}
		} catch (_: Throwable) {
			// If pause fails, fall back to stop.
			stopPlayback()
			return
		}
		isPlaying = false
		pausedForFocusLoss = true
		audioControls.setPlaying(false)
		ayahCardsOverlay.setPlaying(false)
		stopKeepAliveService()
	}

	private fun resumePlaybackAfterFocusGain() {
		if (!pausedForFocusLoss) return
		pausedForFocusLoss = false
		val mp = mediaPlayer
		if (mp == null) {
			// Player might have been released by the system; restart from current ayah.
			playActiveAyah()
			return
		}
		try {
			if (autoPlayEnabled) startKeepAliveService()
			mp.start()
			isPlaying = true
			audioControls.setPlaying(true)
			ayahCardsOverlay.setPlaying(true)
		} catch (_: Throwable) {
			stopPlayback()
		}
	}

	private fun requestAudioFocusForPlayback(): Boolean {
		return try {
			val listener = AudioManager.OnAudioFocusChangeListener { change ->
				when (change) {
					AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
					AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
					-> {
						// Calls typically trigger LOSS_TRANSIENT. Pause and auto-resume on gain.
						mainHandler.post { pausePlaybackForFocusLoss() }
					}
					AudioManager.AUDIOFOCUS_LOSS -> {
						// Permanent loss: stop and disable continuous play mode.
						mainHandler.post {
							pausedForFocusLoss = false
							setAutoPlayEnabled(false)
							if (isPlaying || mediaPlayer != null) stopPlayback()
						}
					}
					AudioManager.AUDIOFOCUS_GAIN -> {
						mainHandler.post { resumePlaybackAfterFocusGain() }
					}
					else -> Unit
				}
			}

			val result = if (android.os.Build.VERSION.SDK_INT >= 26) {
				val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
					.setOnAudioFocusChangeListener(listener)
					.setAudioAttributes(
						AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_MEDIA)
							.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
							.build(),
					)
					.setAcceptsDelayedFocusGain(false)
					.build()
				also { audioFocusRequest = req }
				audioManager.requestAudioFocus(req)
			} else {
				@Suppress("DEPRECATION")
				audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
			}
			result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
		} catch (_: Throwable) {
			false
		}
	}

	private fun abandonAudioFocus() {
		try {
			if (android.os.Build.VERSION.SDK_INT >= 26) {
				audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
			} else {
				// Best effort; listener reference is not retained for pre-26.
			}
		} catch (_: Throwable) {
		}
	}

	private fun playActiveAyah() {
		val reciterId = selectedReciterId
		if (reciterId <= 0) return
		if (!requestAudioFocusForPlayback()) {
			setAutoPlayEnabled(false)
			return
		}
		val verseKey = activeAyahVerseKey
		scope.launch {
			val audioUrl = withContext(Dispatchers.IO) {
				audioApi.getAyahAudio(recitationId = reciterId, verseKey = verseKey)?.absoluteUrl
			}
			if (audioUrl.isNullOrBlank()) return@launch
			try {
				if (autoPlayEnabled) startKeepAliveService()
				stopPlayback()
				mediaPlayer = MediaPlayer().apply {
					// Keep CPU running during playback so audio continues when screen locks.
					setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
					setAudioAttributes(
						AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_MEDIA)
							.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
							.build(),
					)
					setDataSource(audioUrl)
					setOnCompletionListener { mp ->
						try { mp.release() } catch (_: Throwable) {}
						if (mediaPlayer === mp) mediaPlayer = null
						this@SurahWheelGlHolder.isPlaying = false
						audioControls.post { audioControls.setPlaying(false) }
						ayahCardsOverlay.post { ayahCardsOverlay.setPlaying(false) }
						if (autoPlayEnabled) {
							navigateActiveAyah(delta = 1, autoPlayAfterNavigation = true)
						} else {
							stopKeepAliveService()
						}
					}
					prepareAsync()
					setOnPreparedListener {
						this@SurahWheelGlHolder.isPlaying = true
						audioControls.setPlaying(true)
						ayahCardsOverlay.setPlaying(true)
						it.start()
					}
				}
			} catch (_: Throwable) {
				try {
					mediaPlayer?.release()
				} catch (_: Throwable) {
				} finally {
					mediaPlayer = null
					isPlaying = false
					audioControls.setPlaying(false)
					ayahCardsOverlay.setPlaying(false)
					stopKeepAliveService()
				}
			}
		}
	}

	private data class ListenPlaybackConfig(
		val surahNumber: Int,
		val reciterId: Int,
		val startAyah: Int,
		val endAyah: Int,
		val rangeRepetition: Int,
		val ayahRepetition: Int,
		val translatedAudioEnabled: Boolean,
		val translatedLanguageCode: String,
	)

	private fun startListenPlayback(settings: ListenModeSettings) {
		val surah = settings.surahNumber.coerceIn(1, SURA_NAMES.size)
		val maxAyah = versesCountForSurah(surah)
		val start = settings.startAyah.coerceIn(1, maxAyah)
		val end = settings.endAyah.coerceIn(start, maxAyah)
		val rangeRep = settings.rangeRepetition.coerceAtLeast(1)
		val ayahRep = settings.ayahRepetition.coerceAtLeast(1)
		val langCode = languageCodeForLabel(settings.translatedLanguageLabel)
		val reciterId = settings.reciterId
		listenConfig = ListenPlaybackConfig(
			surahNumber = surah,
			reciterId = reciterId,
			startAyah = start,
			endAyah = end,
			rangeRepetition = rangeRep,
			ayahRepetition = ayahRep,
			translatedAudioEnabled = settings.translatedAudioEnabled,
			translatedLanguageCode = langCode,
		)
		listenPlaybackToken += 1
		listenRangeRepeatsRemaining = rangeRep
		listenCurrentAyah = start
		listenAyahRepeatsRemaining = ayahRep
		listenIsPlayingState.value = true
		playListenCurrentAyah(token = listenPlaybackToken)
		enterListenMiniMode(fromListenPanel = true)
	}

	private fun stopListenPlayback() {
		listenConfig = null
		listenPlaybackToken += 1
		listenIsPlayingState.value = false
		// Stop system overlay mini player if it's showing.
		runCatching {
			context.applicationContext.startService(
				Intent(context.applicationContext, MiniListenOverlayService::class.java).apply {
					action = MiniListenOverlayService.ACTION_HIDE
				},
			)
		}
		// If playback is stopped while mini-mode is active, restore full UI.
		if (listenMiniModeEnabled) {
			exitListenMiniMode()
		}
		listenRangeRepeatsRemaining = 0
		listenAyahRepeatsRemaining = 0
		stopListenTts()
		stopPlayback()
		stopKeepAliveService()
	}

	private fun stopListenTts() {
		try {
			listenPendingUtterances.values.forEach { it.cancel() }
			listenPendingUtterances.clear()
			listenTts?.stop()
		} catch (_: Throwable) {
		}
	}

	private fun enterListenMiniMode(fromListenPanel: Boolean) {
		if (listenMiniModeEnabled) return
		listenMiniModeEnabled = true
		listenMiniWasInListenPanel = fromListenPanel

		// If the user granted overlay permission, use a true floating icon over other apps.
		if (Settings.canDrawOverlays(context)) {
			runCatching {
				val appCtx = context.applicationContext
				appCtx.startService(
					Intent(appCtx, MiniListenOverlayService::class.java).apply {
						action = MiniListenOverlayService.ACTION_SHOW
					},
				)
			}
			// Send the app to background so only the floating icon remains.
			(context as? android.app.Activity)?.moveTaskToBack(true)
			return
		}

		// No overlay permission yet: send user to the permission screen.
		runCatching {
			val intent = Intent(
				Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
				Uri.parse("package:${context.packageName}"),
			).apply {
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			context.startActivity(intent)
		}

		// Show floating overlay logo.
		listenMiniOverlay.visibility = View.VISIBLE
		listenMiniOverlay.bringToFront()

		// Best-effort reduce GPU/CPU load (we keep audio running).
		listenMiniHiddenGl = glView
		runCatching { glView.onPause() }

		// Hide the full app UI (everything in root) except the mini overlay.
		val toHide = ArrayList<Pair<View, Int>>(root.childCount)
		for (i in 0 until root.childCount) {
			val child = root.getChildAt(i) ?: continue
			if (child === listenMiniOverlay) continue
			// Keep the starfield visible so mini-mode isn't a black screen.
			if (child === starBackground) continue
			toHide.add(child to child.visibility)
			child.visibility = View.GONE
		}
		listenMiniHiddenViews = toHide
		starBackground.visibility = View.VISIBLE
	}

	private fun exitListenMiniMode() {
		if (!listenMiniModeEnabled) return
		listenMiniModeEnabled = false

		// Restore views.
		for ((v, vis) in listenMiniHiddenViews) {
			v.visibility = vis
		}
		listenMiniHiddenViews = emptyList()

		// Resume GL if we paused it.
		listenMiniHiddenGl?.let { gl ->
			runCatching { gl.onResume() }
		}
		listenMiniHiddenGl = null

		listenMiniOverlay.visibility = View.GONE
		listenMiniWasInListenPanel = false
	}

	private fun playListenCurrentAyah(token: Int) {
		val cfg = listenConfig ?: return
		if (token != listenPlaybackToken) return
		val reciterId = cfg.reciterId
		if (reciterId <= 0) {
			stopListenPlayback()
			return
		}
		if (!requestAudioFocusForPlayback()) {
			stopListenPlayback()
			return
		}
		val verseKey = "${cfg.surahNumber}:${listenCurrentAyah.coerceIn(cfg.startAyah, cfg.endAyah)}"
		scope.launch {
			val audioUrl = withContext(Dispatchers.IO) {
				audioApi.getAyahAudio(recitationId = reciterId, verseKey = verseKey)?.absoluteUrl
			}
			if (token != listenPlaybackToken) return@launch
			if (listenConfig == null) return@launch
			if (audioUrl.isNullOrBlank()) {
				stopListenPlayback()
				return@launch
			}
			playListenAudioUrl(audioUrl = audioUrl, token = token, verseKey = verseKey)
		}
	}

	private fun playListenAudioUrl(audioUrl: String, token: Int, verseKey: String) {
		if (token != listenPlaybackToken) return
		val cfg = listenConfig ?: return
		try {
			// Replace any currently playing track.
			stopPlayback()
			mediaPlayer = MediaPlayer().apply {
				setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(AudioAttributes.USAGE_MEDIA)
						.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
						.build(),
				)
				setDataSource(audioUrl)
				setOnCompletionListener { mp ->
					try { mp.release() } catch (_: Throwable) {}
					if (mediaPlayer === mp) mediaPlayer = null
					this@SurahWheelGlHolder.isPlaying = false
					audioControls.post { audioControls.setPlaying(false) }
					ayahCardsOverlay.post { ayahCardsOverlay.setPlaying(false) }
					if (token != listenPlaybackToken) return@setOnCompletionListener
					if (listenConfig == null) return@setOnCompletionListener
					scope.launch {
						if (cfg.translatedAudioEnabled && cfg.translatedLanguageCode == "en") {
							speakEnglishTranslationIfPossible(token = token, verseKey = verseKey)
						}
						advanceListenAndContinue(token = token)
					}
				}
				prepareAsync()
				setOnPreparedListener {
					this@SurahWheelGlHolder.isPlaying = true
					audioControls.setPlaying(true)
					ayahCardsOverlay.setPlaying(true)
					startKeepAliveService()
					it.start()
				}
			}
		} catch (_: Throwable) {
			stopListenPlayback()
		}
	}

	private suspend fun speakEnglishTranslationIfPossible(token: Int, verseKey: String) {
		if (token != listenPlaybackToken) return
		val tts = ensureListenTts() ?: return
		val text = withContext(Dispatchers.IO) {
			try {
				api.getEnglishMeaningByVerseKey(
					translationId = QuranApi.DEFAULT_EN_TRANSLATION_ID,
					verseKey = verseKey,
				)
					.translations
					.firstOrNull()
					?.text
					?.stripHtmlTags()
					?.trim()
					.orEmpty()
			} catch (_: Throwable) {
				""
			}
		}
		if (token != listenPlaybackToken) return
		if (text.isBlank()) return
		// Slight pause so translation feels calmer after recitation.
		delay(250L)
		speakListenText(tts = tts, text = text)
	}

	private fun configureListenTts(tts: TextToSpeech) {
		// Slow + deeper voice for a calmer "Quran vibe" translation.
		runCatching { tts.setSpeechRate(0.82f) }
		runCatching { tts.setPitch(0.80f) }
		runCatching { tts.language = Locale.US }

		if (android.os.Build.VERSION.SDK_INT < 21) return
		val voices = runCatching { tts.voices }.getOrNull().orEmpty()
		val candidates = voices
			.asSequence()
			.filter { v -> v.locale?.language.equals("en", ignoreCase = true) }
			.filter { v -> runCatching { !v.isNetworkConnectionRequired }.getOrDefault(true) }
			.toList()
		if (candidates.isEmpty()) return
		val male = candidates.firstOrNull { v ->
			val name = v.name.orEmpty()
			val feats = runCatching { v.features }.getOrNull().orEmpty()
			name.contains("male", ignoreCase = true) || feats.any { it.contains("male", ignoreCase = true) }
		}
		val preferred = male
			?: candidates.firstOrNull { it.name.orEmpty().contains("en-us", ignoreCase = true) }
			?: candidates.firstOrNull()
		runCatching { if (preferred != null) tts.voice = preferred }
	}

	private suspend fun ensureListenTts(): TextToSpeech? {
		val existingTts = listenTts
		val existingInit = listenTtsInit
		if (existingTts != null) {
			if (existingInit == null) return existingTts
			val ok = runCatching { existingInit.await() }.getOrNull() == true
			return if (ok) existingTts else null
		}
		if (existingInit != null) {
			val ok = runCatching { existingInit.await() }.getOrNull() == true
			return if (ok) listenTts else null
		}
		val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
		listenTtsInit = deferred
		listenTts = TextToSpeech(context.applicationContext) { status ->
			deferred.complete(status == TextToSpeech.SUCCESS)
		}
		val ok = runCatching { deferred.await() }.getOrNull() == true
		if (!ok) return null
		listenTts?.let { configureListenTts(it) }
		listenTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(utteranceId: String?) = Unit

			override fun onDone(utteranceId: String?) {
				val id = utteranceId ?: return
				listenPendingUtterances.remove(id)?.complete(Unit)
			}

			override fun onError(utteranceId: String?) {
				val id = utteranceId ?: return
				listenPendingUtterances.remove(id)?.complete(Unit)
			}
		})
		return listenTts
	}

	private suspend fun speakListenText(tts: TextToSpeech, text: String) {
		val utteranceId = "listen_${SystemClock.uptimeMillis()}_${text.hashCode()}"
		val done = kotlinx.coroutines.CompletableDeferred<Unit>()
		listenPendingUtterances[utteranceId] = done
		try {
			tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
		} catch (_: Throwable) {
			listenPendingUtterances.remove(utteranceId)
			return
		}
		kotlinx.coroutines.withTimeoutOrNull(15_000L) {
			done.await()
		}
		listenPendingUtterances.remove(utteranceId)
	}

	private fun advanceListenAndContinue(token: Int) {
		if (token != listenPlaybackToken) return
		val cfg = listenConfig ?: return
		if (listenAyahRepeatsRemaining > 1) {
			listenAyahRepeatsRemaining -= 1
			playListenCurrentAyah(token = token)
			return
		}
		listenAyahRepeatsRemaining = cfg.ayahRepetition.coerceAtLeast(1)
		if (listenCurrentAyah < cfg.endAyah) {
			listenCurrentAyah += 1
			playListenCurrentAyah(token = token)
			return
		}
		// Range finished.
		if (listenRangeRepeatsRemaining > 1) {
			listenRangeRepeatsRemaining -= 1
			listenCurrentAyah = cfg.startAyah
			playListenCurrentAyah(token = token)
			return
		}
		stopListenPlayback()
	}

	private suspend fun fetchTwoAyahs(surahNumber: Int): Pair<String, String> {
		return fetchActiveAndNextAyah(surahNumber = surahNumber, ayahNumber = 1)
	}

	private suspend fun fetchActiveAndNextAyah(surahNumber: Int, ayahNumber: Int): Pair<String, String> {
		return withContext(Dispatchers.IO) {
			try {
				val v1 = api.getUthmaniVerseByKey("$surahNumber:$ayahNumber").verses.firstOrNull()?.textUthmani ?: ""
				val v2 = api.getUthmaniVerseByKey("$surahNumber:${ayahNumber + 1}").verses.firstOrNull()?.textUthmani ?: ""
				v1 to v2
			} catch (_: Throwable) {
				"" to ""
			}
		}
	}

	fun updateChapters(chapters: List<ChapterDto>) {
		if (chapters.size != SURA_NAMES.size) return
		val arabic = Array(chapters.size) { "" }
		val english = Array(chapters.size) { "" }
		val meaning = Array(chapters.size) { "" }
		val verses = IntArray(chapters.size)
		val revelation = arrayOfNulls<String>(chapters.size)
		for (i in chapters.indices) {
			val ch = chapters[i]
			arabic[i] = ch.nameArabic
			english[i] = ch.nameSimple
			meaning[i] = ch.translatedName.name
			verses[i] = ch.versesCount
			revelation[i] = ch.revelationPlace
		}
		chapterArabic = arabic
		chapterEnglish = english
		chapterMeaning = meaning
		chapterVerses = verses
		refreshListenSurahItems()
		chapterRevelationPlace = revelation
	}

	private fun revelationTypeForIndex(surahIdx: Int): String? {
		val place = chapterRevelationPlace.getOrNull(surahIdx)?.trim()?.lowercase()
		return when (place) {
			"makkah", "mecca" -> "Makki"
			"madinah", "medina" -> "Madani"
			else -> null
		}
	}

	private fun juzRangeForIndex(surahIdx: Int): String? {
		val start = juzStartBySurah.getOrNull(surahIdx) ?: 0
		val end = juzEndBySurah.getOrNull(surahIdx) ?: 0
		if (start <= 0 || end <= 0) return null
		return if (start == end) start.toString() else "$start-$end"
	}

	private fun requestJuzRangeIfNeeded(surahNumber: Int, surahIndex: Int) {
		if (surahIndex !in 0 until SURA_NAMES.size) return
		val known = juzStartBySurah[surahIndex] > 0 && juzEndBySurah[surahIndex] > 0
		if (known) return
		if (juzInFlightSurahIndex == surahIndex && juzFetchJob?.isActive == true) return

		juzFetchJob?.cancel()
		val token = juzFetchToken + 1
		juzFetchToken = token
		juzInFlightSurahIndex = surahIndex
		juzFetchJob = scope.launch {
			try {
				val versesCount = chapterVerses.getOrNull(surahIndex)
				if (versesCount == null || versesCount <= 0) return@launch
				val startKey = "$surahNumber:1"
				val endKey = "$surahNumber:$versesCount"
				val (startJuz, endJuz) = withContext(Dispatchers.IO) {
					try {
						val start = api.getVerseByKeyWithWords(verseKey = startKey, words = false).verse.juzNumber
						val end = api.getVerseByKeyWithWords(verseKey = endKey, words = false).verse.juzNumber
						(start ?: 0) to (end ?: 0)
					} catch (_: Throwable) {
						0 to 0
					}
				}
				if (juzFetchToken != token) return@launch
				if (activeSurahNumber != surahNumber) return@launch
				if (startJuz > 0 && endJuz > 0) {
					juzStartBySurah[surahIndex] = minOf(startJuz, endJuz)
					juzEndBySurah[surahIndex] = maxOf(startJuz, endJuz)
					// Book is continuously redrawn during scroll; no extra invalidate required.
				}
			} finally {
				if (juzFetchToken == token && juzInFlightSurahIndex == surahIndex) {
					juzInFlightSurahIndex = -1
				}
			}
		}
	}

	private fun resetHadithMenuState() {
		hadithFetchJob?.cancel()
		hadithTranslateJob?.cancel()
		hadithTranslateJob = null
		runCatching { hadithLanguagePopup.dismiss() }
		dismissHadithQuickPopups()
		hideHadithBookCover(animated = false)
		hadithCoverImageView.setImageDrawable(null)
		hadithReadingMode = false
		(hadithMenuContainer.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
			if (lp.height != FrameLayout.LayoutParams.MATCH_PARENT) {
				lp.height = FrameLayout.LayoutParams.MATCH_PARENT
				hadithMenuContainer.layoutParams = lp
			}
		}
		hadithActiveBookSlug = null
		hadithCurrentNumber = 1
		hadithSelectedLangCode = "en"
		hadithLanguageLabel.text = "English"
		hadithCurrentPayload = null
		hadithBooksView.apply {
			visibility = View.VISIBLE
			alpha = 1f
			scaleX = 1f
			scaleY = 1f
			translationX = 0f
			translationY = 0f
			pivotX = width * 0.5f
			pivotY = height.toFloat()
		}
		hadithArabicTextView.text = ""
		hadithTranslationTextView.text = ""
		hadithNumberInput.setText("1")
		hadithTextScroll.scrollTo(0, 0)
		hadithTranslationScroll.scrollTo(0, 0)
		hadithReadingRoot.visibility = View.GONE
		hadithChipView.visibility = View.GONE
		hadithChipView.alpha = 0f
		hadithNavRow.visibility = View.GONE
		hadithReadingRoot.setPadding(0, 0, 0, 0)
		applyHadithReadingRowHeights()
	}

	private fun applyHadithReadingRowHeights() {
		if (!hadithReadingMode) return
		val overlayH = hadithMenuContainer.height.takeIf { it > 0 }
			?: topMenuOverlay.height.takeIf { it > 0 }
			?: return
		val desired = (overlayH * 0.35f).toInt()
		// Ensure nav row + language row + bottom chip area still fit.
		val minOther = dpToPx(150f)
		val maxPane = ((overlayH - minOther) / 2f).toInt().coerceAtLeast(dpToPx(120f))
		val paneH = desired.coerceAtMost(maxPane).coerceAtLeast(dpToPx(140f))

		(hadithTextScrollHost.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
			if (lp.height != paneH || lp.weight != 0f) {
				lp.height = paneH
				lp.weight = 0f
				hadithTextScrollHost.layoutParams = lp
			}
		}
		(hadithTranslationScrollHost.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
			if (lp.height != paneH || lp.weight != 0f) {
				lp.height = paneH
				lp.weight = 0f
				hadithTranslationScrollHost.layoutParams = lp
			}
		}
		(hadithChipHost.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
			// Bottom area consumes the remainder so the collapsed name hugs the bottom.
			if (lp.height != 0 || lp.weight != 1f) {
				lp.height = 0
				lp.weight = 1f
				hadithChipHost.layoutParams = lp
			}
		}
	}

	private fun applyHadithNavTopInset() {
		val lp = hadithNavRow.layoutParams as? LinearLayout.LayoutParams ?: return
		val base = dpToPx(22f)
		val target = base + hadithTopInsetPx
		if (lp.topMargin != target) {
			lp.topMargin = target
			hadithNavRow.layoutParams = lp
		}
	}

	private fun createNeonScrollbarThumb(neonColor: Int): View {
		return View(context).apply {
			val w = dpToPx(6f)
			val h = dpToPx(56f)
			layoutParams = FrameLayout.LayoutParams(w, h).apply {
				gravity = Gravity.END or Gravity.TOP
				rightMargin = dpToPx(10f)
				topMargin = dpToPx(10f)
			}
			background = NeonStickDrawable(neonColor)
			alpha = 1f
			isClickable = false
			isFocusable = false
			// Required for BlurMaskFilter glow on some devices.
			setLayerType(View.LAYER_TYPE_SOFTWARE, null)
		}
	}

	private fun attachNeonScrollbar(scrollView: ScrollView, host: FrameLayout, neonColor: Int) {
		scrollView.isVerticalScrollBarEnabled = false
		host.clipToPadding = false
		host.clipChildren = false
		val thumb = createNeonScrollbarThumb(neonColor)
		host.addView(thumb)
		thumb.bringToFront()

		fun updateThumb() {
			if (host.height <= 0 || scrollView.height <= 0) {
				host.post { updateThumb() }
				return
			}
			val child = scrollView.getChildAt(0) ?: run {
				thumb.visibility = View.GONE
				return
			}
			val range = (child.height - scrollView.height).coerceAtLeast(0)
			if (range <= 0) {
				thumb.visibility = View.GONE
				return
			}
			thumb.visibility = View.VISIBLE
			val available = (host.height - thumb.height - dpToPx(20f)).coerceAtLeast(1)
			val t = (scrollView.scrollY.toFloat() / range.toFloat()).coerceIn(0f, 1f)
			thumb.translationY = dpToPx(10f) + (available * t)
		}

		host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateThumb() }
		scrollView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateThumb() }
		scrollView.viewTreeObserver.addOnScrollChangedListener { updateThumb() }
		// Update when the scroll content height changes (e.g., after text is set).
		scrollView.post {
			scrollView.getChildAt(0)?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
				updateThumb()
			}
			updateThumb()
		}
	}

	private class NeonStickDrawable(private val neonColor: Int) : android.graphics.drawable.Drawable() {
		private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
			color = neonColor
			strokeWidth = 10f
			maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
			alpha = 180
		}
		private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
			color = neonColor
			strokeWidth = 5f
			alpha = 255
		}
		private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
			color = Color.WHITE
			strokeWidth = 2f
			alpha = 210
		}

		override fun draw(canvas: Canvas) {
			val b = bounds
			val cx = b.exactCenterX()
			val top = b.top + 6f
			val bottom = b.bottom - 6f
			canvas.drawLine(cx, top, cx, bottom, glowPaint)
			canvas.drawLine(cx, top, cx, bottom, corePaint)
			canvas.drawLine(cx, top + 2f, cx, bottom - 2f, highlightPaint)
		}

		override fun setAlpha(alpha: Int) {
			corePaint.alpha = alpha
			glowPaint.alpha = (alpha * 0.7f).toInt().coerceIn(0, 255)
			highlightPaint.alpha = (alpha * 0.82f).toInt().coerceIn(0, 255)
		}

		override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
			corePaint.colorFilter = colorFilter
			glowPaint.colorFilter = colorFilter
			highlightPaint.colorFilter = colorFilter
		}

		@Deprecated("Deprecated in Android")
		override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
	}

	private fun hadithCoverAssetForBookSlug(bookSlug: String): String? {
		return when (bookSlug.trim().lowercase()) {
			"sahih-bukhari" -> "bukhari cover.png"
			"sahih-muslim" -> "muslim cover.png"
			"abu-dawood" -> "abu dawud cover.png"
			"al-tirmidhi" -> "tirmidhi cover.png"
			else -> null
		}
	}

	private fun showHadithBookCover(bookSlug: String) {
		val asset = hadithCoverAssetForBookSlug(bookSlug)
		if (asset.isNullOrBlank()) {
			hadithCoverImageView.animate().cancel()
			hadithCoverImageView.setImageDrawable(null)
			hadithCoverImageView.visibility = View.GONE
			return
		}

		val bmp = hadithCoverBitmapCache[asset] ?: runCatching {
			context.assets.open(asset).use { input ->
				BitmapFactory.decodeStream(input)
			}
		}.getOrNull()?.also { decoded ->
			hadithCoverBitmapCache[asset] = decoded
		}

		if (bmp == null) {
			hadithCoverImageView.animate().cancel()
			hadithCoverImageView.setImageDrawable(null)
			hadithCoverImageView.visibility = View.GONE
			return
		}

		hadithCoverImageView.animate().cancel()
		hadithCoverImageView.alpha = 1f
		hadithCoverImageView.setImageBitmap(bmp)
		hadithCoverImageView.visibility = View.VISIBLE
	}

	private fun hideHadithBookCover(animated: Boolean) {
		if (hadithCoverImageView.visibility != View.VISIBLE) return
		hadithCoverImageView.animate().cancel()
		if (!animated) {
			hadithCoverImageView.visibility = View.GONE
			hadithCoverImageView.alpha = 1f
			return
		}
		hadithCoverImageView.animate()
			.alpha(0f)
			.setDuration(200L)
			.setInterpolator(DecelerateInterpolator())
			.withEndAction {
				hadithCoverImageView.visibility = View.GONE
				hadithCoverImageView.alpha = 1f
			}
			.start()
	}

	private fun onHadithBookSelected(book: HadithBooksStackView.BookMeta) {
		hadithReadingMode = true
		hadithArabicTextView.text = "Loading…"
		hadithTranslationTextView.text = "Loading…"
		hadithReadingRoot.visibility = View.VISIBLE
		hadithReadingRoot.alpha = 1f
		if (com.ayahverse.quran.BuildConfig.HADITH_API_KEY.isBlank()) {
			Log.w("HadithApi", "HADITH_API_KEY is blank; cannot fetch hadiths")
			hadithArabicTextView.text = ""
			hadithTranslationTextView.text = "(Hadith API key not configured)"
			return
		}
		hadithActiveBookSlug = book.slug
		showHadithBookCover(bookSlug = book.slug)
		hadithBookTitleBySlug[book.slug] = book.title
		logHadithBookMetadataOnce(book)
		prefetchHadithTopicsIfNeeded()
		hadithCurrentNumber = 1
		hadithSelectedLangCode = "en"
		hadithLanguageLabel.text = "English"
		updateHadithControls(visible = true)
		(hadithMenuContainer.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
			if (lp.height != FrameLayout.LayoutParams.MATCH_PARENT) {
				lp.height = FrameLayout.LayoutParams.MATCH_PARENT
				hadithMenuContainer.layoutParams = lp
			}
		}
		// Apply row heights as soon as overlay has a size.
		topMenuOverlay.post { applyHadithReadingRowHeights() }

		// Collapse books down to bottom-center and leave only the name.
		updateHadithCollapsedChipText(bookSlug = book.slug, fallbackTitle = book.title)
		hadithChipView.visibility = View.VISIBLE
		hadithChipView.alpha = 0f
		val booksV = hadithBooksView
		booksV.pivotX = booksV.width * 0.5f
		booksV.pivotY = booksV.height.toFloat()
		booksV.animate().cancel()
		hadithChipView.animate().cancel()
		booksV.animate()
			.alpha(0f)
			.scaleX(0.06f)
			.scaleY(0.06f)
			.setDuration(260L)
			.setInterpolator(DecelerateInterpolator())
			.withEndAction {
				booksV.visibility = View.GONE
			}
			.start()
		hadithChipView.animate()
			.alpha(1f)
			.setDuration(220L)
			.setInterpolator(DecelerateInterpolator())
			.start()

		requestHadith(bookSlug = book.slug, hadithNumber = 1)
	}

	private fun prefetchHadithTopicsIfNeeded() {
		// Fire-and-forget so the popup feels instant.
		ensureHadithTopicsLoaded(forceRefresh = false, onLoaded = {})
	}

	private data class HadithBookCounts(
		val chaptersCount: Int,
		val hadithsCount: Int,
	)

	private fun updateHadithCollapsedChipText(bookSlug: String, fallbackTitle: String? = null) {
		val title = hadithBookTitleBySlug[bookSlug].orEmpty().ifBlank { fallbackTitle.orEmpty() }.ifBlank { bookSlug }
		val counts = hadithBookCountsBySlug[bookSlug]
		if (counts == null) {
			hadithChipView.setParts(left = title, right = "")
			return
		}
		hadithChipView.setParts(
			left = title,
			right = "chap:${counts.chaptersCount} <${counts.hadithsCount}>",
		)
	}

	private fun dismissHadithQuickPopups() {
		hadithNamesPopupWindow?.dismiss()
		hadithNamesPopupWindow = null
		hadithTopicsPopupWindow?.dismiss()
		hadithTopicsPopupWindow = null
	}

	private fun makeIosStylePopupBackground(): GradientDrawable {
		return GradientDrawable(
			GradientDrawable.Orientation.TOP_BOTTOM,
			intArrayOf(
				Color.argb(246, 18, 22, 32),
				Color.argb(246, 10, 12, 18),
			),
		).apply {
			cornerRadius = dpToPx(18f).toFloat()
			setStroke(dpToPx(1f), Color.argb(85, 255, 255, 255))
		}
	}

	private fun showAnimatedPopup(popup: PopupWindow, anchor: View, content: View) {
		// iPhone-ish: twisted (3D) opening into flat.
		content.alpha = 0f
		content.scaleX = 0.96f
		content.scaleY = 0.86f
		content.rotationX = -22f
		content.rotationY = 8f
		content.translationY = dpToPx(10f).toFloat()
		content.pivotX = 0f
		content.pivotY = 0f
		content.cameraDistance = dpToPx(320f).toFloat()

		// Ensure the popup is visible even though the chip is bottom-aligned.
		val desiredWidth = popup.width.takeIf { it > 0 } ?: dpToPx(260f)
		content.measure(
			View.MeasureSpec.makeMeasureSpec(desiredWidth, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
		)
		val contentH = content.measuredHeight.coerceAtLeast(dpToPx(120f))
		val frame = Rect()
		anchor.getWindowVisibleDisplayFrame(frame)
		val loc = IntArray(2)
		anchor.getLocationOnScreen(loc)
		val anchorTop = loc[1]
		val anchorBottom = anchorTop + anchor.height
		val margin = dpToPx(10f)
		val belowBottomY = anchorBottom + margin + contentH
		val canShowBelow = belowBottomY <= frame.bottom
		val canShowAbove = (anchorTop - margin - contentH) >= frame.top
		val yOff = if (!canShowBelow && canShowAbove) {
			-(contentH + anchor.height + margin)
		} else {
			margin
		}

		popup.showAsDropDown(anchor, 0, yOff)
		content.post {
			content.animate().cancel()
			content.animate()
				.alpha(1f)
				.scaleX(1f)
				.scaleY(1f)
				.rotationX(0f)
				.rotationY(0f)
				.translationY(0f)
				.setDuration(260L)
				.setInterpolator(DecelerateInterpolator())
				.start()
		}
	}

	private fun switchToHadithBook(book: HadithBooksStackView.BookMeta) {
		dismissHadithQuickPopups()
		hadithReadingMode = true
		hadithReadingRoot.visibility = View.VISIBLE
		hadithReadingRoot.alpha = 1f
		hadithArabicTextView.text = "Loading…"
		hadithTranslationTextView.text = "Loading…"
		hadithActiveBookSlug = book.slug
		showHadithBookCover(bookSlug = book.slug)
		hadithBookTitleBySlug[book.slug] = book.title
		logHadithBookMetadataOnce(book)
		hadithCurrentNumber = 1
		hadithSelectedLangCode = "en"
		hadithLanguageLabel.text = "English"
		updateHadithControls(visible = true)
		updateHadithCollapsedChipText(bookSlug = book.slug, fallbackTitle = book.title)
		hadithChipView.visibility = View.VISIBLE
		hadithChipView.alpha = 1f
		hadithNumberInput.setText("1")
		hadithTextScroll.scrollTo(0, 0)
		hadithTranslationScroll.scrollTo(0, 0)
		requestHadith(bookSlug = book.slug, hadithNumber = 1)
	}

	private fun showHadithBooksPopup() {
		dismissHadithQuickPopups()
		val currentSlug = hadithActiveBookSlug ?: return
		val allBooks = hadithBooksView.getBooks()
		if (allBooks.isEmpty()) return
		val idx = allBooks.indexOfFirst { it.slug == currentSlug }
		val otherBooks = buildList<HadithBooksStackView.BookMeta> {
			if (allBooks.size <= 1) return@buildList
			val start = if (idx >= 0) (idx + 1) else 0
			var i = 0
			while (size < 3 && i < allBooks.size * 2) {
				val b = allBooks[(start + i) % allBooks.size]
				if (b.slug != currentSlug && none { it.slug == b.slug }) add(b)
				i += 1
			}
		}
		if (otherBooks.isEmpty()) return

		val root = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
			minimumWidth = dpToPx(280f)
		}
		val title = TextView(context).apply {
			text = "Other books"
			setTextColor(Color.argb(210, 212, 175, 55))
			textSize = 14f
			typeface = Typeface.DEFAULT_BOLD
			setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(8f))
			setShadowLayer(dpToPx(10f).toFloat(), 0f, 0f, Color.argb(120, 255, 255, 255))
		}
		root.addView(title)
		otherBooks.forEach { book ->
			val row = TextView(context).apply {
				text = book.title
				setTextColor(Color.WHITE)
				textSize = 16f
				typeface = Typeface.DEFAULT_BOLD
				setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(12f))
				background = GradientDrawable(
					GradientDrawable.Orientation.LEFT_RIGHT,
					intArrayOf(
						Color.argb(28, 255, 255, 255),
						Color.argb(10, 255, 255, 255),
					),
				).apply {
					cornerRadius = dpToPx(14f).toFloat()
				}
				setShadowLayer(dpToPx(12f).toFloat(), 0f, 0f, Color.argb(140, 140, 180, 255))
				setOnClickListener {
					switchToHadithBook(book)
				}
			}
			root.addView(row)
			root.addView(View(context).apply {
				layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8f))
				alpha = 0f
			})
		}

		val popup = PopupWindow(
			root,
			dpToPx(320f),
			ViewGroup.LayoutParams.WRAP_CONTENT,
			true,
		).apply {
			isOutsideTouchable = true
			isFocusable = true
			setBackgroundDrawable(makeIosStylePopupBackground())
			elevation = dpToPx(22f).toFloat()
			setOnDismissListener { hadithNamesPopupWindow = null }
		}
		hadithNamesPopupWindow = popup
		showAnimatedPopup(popup, hadithChipView, root)
	}

	private fun showHadithTopicsPopup() {
		dismissHadithQuickPopups()
		val slug = hadithActiveBookSlug ?: return
		val code = hadithBookCodeForSlug(slug) ?: return
		if (!isSupabaseTopicsConfigured()) {
			showSimpleInfoPopup(titleText = "Topics", message = "Supabase not configured")
			return
		}

		// Show a small loading popup immediately; then replace it with real topics.
		val loadingRoot = makePopupRoot(minWidthDp = 220f)
		loadingRoot.addView(makePopupTitle("Topics"))
		loadingRoot.addView(makePopupMessage("Loading…"))
		val loadingPopup = makePopupWindow(loadingRoot, widthDp = 260f) {
			hadithTopicsPopupWindow = null
		}
		hadithTopicsPopupWindow = loadingPopup
		showAnimatedPopup(loadingPopup, hadithChipView, loadingRoot)

		val shouldForceRefresh = hadithTopicsLoaded && hadithTopicsSourceRowCount == 0 && hadithTopicsLastError == null
		ensureHadithTopicsLoaded(forceRefresh = shouldForceRefresh) {
			val topics = hadithTopicsByBookCode[code].orEmpty()
			runCatching { loadingPopup.dismiss() }
			hadithTopicsLastError?.let { err ->
				showSimpleInfoPopup(titleText = "Topics", message = err)
				return@ensureHadithTopicsLoaded
			}
			if (topics.isEmpty()) {
				val extra = if (com.ayahverse.quran.BuildConfig.DEBUG) " (rows=$hadithTopicsSourceRowCount)" else ""
				showSimpleInfoPopup(titleText = "Topics", message = "No topics for this book$extra")
				return@ensureHadithTopicsLoaded
			}
			showTopicsListPopup(topics)
		}
	}

	private fun showTopicsListPopup(topics: List<HadithTopicEntry>) {
		dismissHadithQuickPopups()
		val slug = hadithActiveBookSlug
		val root = makePopupRoot(minWidthDp = 260f)
		root.addView(makePopupTitle("Topics"))

		val listContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(0, 0, 0, 0)
		}
		topics.forEach { t ->
			val row = makePopupRow(
				text = "${t.topic}  (${t.startHadith}–${t.endHadith})",
				onClick = {
					val s = slug ?: hadithActiveBookSlug
					if (s.isNullOrBlank()) {
						dismissHadithQuickPopups()
						return@makePopupRow
					}
					dismissHadithQuickPopups()
					// Jump to the first hadith of the selected topic range.
					requestHadith(bookSlug = s, hadithNumber = t.startHadith)
				},
			)
			listContainer.addView(row)
			listContainer.addView(makePopupSpacer())
		}

		val maxPopupH = dpToPx(420f)
		val scroll = ScrollView(context).apply {
			isFillViewport = true
			clipToPadding = false
			setPadding(0, 0, 0, dpToPx(2f))
			addView(
				listContainer,
				ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
			)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				maxPopupH,
			)
		}
		root.addView(scroll)

		val popup = makePopupWindow(root, widthDp = 330f) { hadithTopicsPopupWindow = null }
		hadithTopicsPopupWindow = popup
		showAnimatedPopup(popup, hadithChipView, root)
	}

	private fun makePopupRoot(minWidthDp: Float): LinearLayout {
		return LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
			minimumWidth = dpToPx(minWidthDp)
		}
	}

	private fun makePopupTitle(text: String): TextView {
		return TextView(context).apply {
			this.text = text
			setTextColor(Color.argb(210, 212, 175, 55))
			textSize = 14f
			typeface = Typeface.DEFAULT_BOLD
			setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(8f))
			setShadowLayer(dpToPx(10f).toFloat(), 0f, 0f, Color.argb(120, 255, 255, 255))
		}
	}

	private fun makePopupMessage(text: String): TextView {
		return TextView(context).apply {
			this.text = text
			setTextColor(Color.argb(225, 255, 255, 255))
			textSize = 15f
			typeface = Typeface.DEFAULT
			setPadding(dpToPx(14f), dpToPx(10f), dpToPx(14f), dpToPx(12f))
		}
	}

	private fun makePopupRow(text: String, onClick: () -> Unit): TextView {
		return TextView(context).apply {
			this.text = text
			setTextColor(Color.WHITE)
			textSize = 15.5f
			typeface = Typeface.DEFAULT_BOLD
			setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(12f))
			background = GradientDrawable(
				GradientDrawable.Orientation.LEFT_RIGHT,
				intArrayOf(
					Color.argb(24, 255, 255, 255),
					Color.argb(10, 255, 255, 255),
				),
			).apply {
				cornerRadius = dpToPx(14f).toFloat()
			}
			setShadowLayer(dpToPx(12f).toFloat(), 0f, 0f, Color.argb(140, 140, 180, 255))
			setOnClickListener { onClick() }
		}
	}

	private fun makePopupSpacer(): View {
		return View(context).apply {
			layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8f))
			alpha = 0f
		}
	}

	private fun makePopupWindow(content: View, widthDp: Float, onDismiss: () -> Unit): PopupWindow {
		return PopupWindow(
			content,
			dpToPx(widthDp),
			ViewGroup.LayoutParams.WRAP_CONTENT,
			true,
		).apply {
			isOutsideTouchable = true
			isFocusable = true
			setBackgroundDrawable(makeIosStylePopupBackground())
			elevation = dpToPx(22f).toFloat()
			setOnDismissListener { onDismiss() }
		}
	}

	private fun showSimpleInfoPopup(titleText: String, message: String) {
		dismissHadithQuickPopups()
		val root = makePopupRoot(minWidthDp = 220f)
		root.addView(makePopupTitle(titleText))
		root.addView(makePopupMessage(message))
		val popup = makePopupWindow(root, widthDp = 280f) { hadithTopicsPopupWindow = null }
		hadithTopicsPopupWindow = popup
		showAnimatedPopup(popup, hadithChipView, root)
	}

	private fun isSupabaseTopicsConfigured(): Boolean {
		val base = com.ayahverse.quran.BuildConfig.SUPABASE_URL.trim().trimEnd('/')
		val key = com.ayahverse.quran.BuildConfig.SUPABASE_ANON_KEY.trim()
		return base.isNotBlank() && key.isNotBlank()
	}

	private fun hadithBookCodeForSlug(slug: String): String? {
		return when (slug.trim().lowercase()) {
			"abu-dawood" -> "a"
			"al-tirmidhi" -> "t"
			"sahih-bukhari" -> "b"
			"sahih-muslim" -> "m"
			else -> null
		}
	}

	private fun ensureHadithTopicsLoaded(forceRefresh: Boolean, onLoaded: () -> Unit) {
		if (forceRefresh) {
			hadithTopicsLoaded = false
			hadithTopicsLastError = null
			hadithTopicsSourceRowCount = 0
			hadithTopicsByBookCode.clear()
		}
		if (hadithTopicsLoaded && !forceRefresh) {
			onLoaded()
			return
		}
		hadithTopicsWaiters.add(onLoaded)
		if (hadithTopicsFetchJob != null) return
		if (!isSupabaseTopicsConfigured()) {
			hadithTopicsLoaded = true
			val waiters = hadithTopicsWaiters.toList()
			hadithTopicsWaiters.clear()
			waiters.forEach { it.invoke() }
			return
		}

		hadithTopicsFetchJob = scope.launch {
			val rows = withContext(Dispatchers.IO) {
				fetchHadithTopicRowsFromSupabase()
			}
			hadithTopicsSourceRowCount = rows.length()
			hadithTopicsByBookCode.clear()
			hadithTopicsByBookCode.putAll(buildTopicsByBookCode(rows))
			if (com.ayahverse.quran.BuildConfig.DEBUG) {
				val counts = hadithTopicsByBookCode.entries.joinToString { (k, v) -> "$k=${v.size}" }
				Log.d("SupabaseTopics", "Loaded topics rows=${rows.length()} parsed={$counts}")
			}
			hadithTopicsLoaded = true
			hadithTopicsFetchJob = null
			val waiters = hadithTopicsWaiters.toList()
			hadithTopicsWaiters.clear()
			waiters.forEach { it.invoke() }
		}
	}

	private fun fetchHadithTopicRowsFromSupabase(): JSONArray {
		val baseUrl = com.ayahverse.quran.BuildConfig.SUPABASE_URL.trim().trimEnd('/')
		val apiKey = com.ayahverse.quran.BuildConfig.SUPABASE_ANON_KEY.trim()
		val looksJwt = apiKey.startsWith("eyJ") && apiKey.length > 100
		val url = "$baseUrl/rest/v1/hadith_topics?select=topic,hadith&limit=5000"
		hadithTopicsLastError = null
		val builder = Request.Builder()
			.url(url)
			.get()
			.addHeader("apikey", apiKey)
			.addHeader("Accept", "application/json")
		if (looksJwt) builder.addHeader("Authorization", "Bearer $apiKey")
		val req = builder.build()
		val resp = hadithHttp.newCall(req).execute()
		resp.use {
			val body = it.body?.string().orEmpty()
			if (!it.isSuccessful) {
				val msg = "Supabase topics error HTTP ${it.code}: ${body.take(160)}"
				hadithTopicsLastError = msg
				Log.w("SupabaseTopics", "url=$url $msg")
				return JSONArray()
			}
			if (com.ayahverse.quran.BuildConfig.DEBUG) {
				Log.d("SupabaseTopics", "HTTP ${it.code} url=$url bodyHead=${body.take(160)}")
			}
			return runCatching { JSONArray(body) }.getOrElse { JSONArray() }
		}
	}

	private fun buildTopicsByBookCode(rows: JSONArray): Map<String, List<HadithTopicEntry>> {
		val out: MutableMap<String, MutableList<HadithTopicEntry>> = linkedMapOf(
			"a" to mutableListOf(),
			"t" to mutableListOf(),
			"b" to mutableListOf(),
			"m" to mutableListOf(),
		)
		for (i in 0 until rows.length()) {
			val obj = rows.optJSONObject(i) ?: continue
			val topic = firstNonBlank(obj, listOf("topic", "name", "title", "label"))
			if (topic.isBlank()) continue
			val hadithArr = extractHadithTriplesArray(obj) ?: continue
			val triples = parseRangesFromTriples(hadithArr)
			triples.forEach { (code, range) ->
				if (!out.containsKey(code)) return@forEach
				out[code]?.add(HadithTopicEntry(topic = topic, startHadith = range.first, endHadith = range.second))
			}
		}
		return out.mapValues { (_, list) ->
			list.sortedWith(compareBy<HadithTopicEntry> { it.startHadith }.thenBy { it.endHadith }.thenBy { it.topic })
		}
	}

	private fun extractHadithTriplesArray(obj: JSONObject): JSONArray? {
		val raw: Any? = obj.opt("hadith")
			?: obj.opt("hadith_row")
			?: obj.opt("hadiths")
			?: obj.opt("row")
			?: return null
		return when (raw) {
			is JSONArray -> raw
			is String -> parseFlexibleArrayString(raw)
			else -> null
		}
	}

	private fun parseFlexibleArrayString(raw: String): JSONArray? {
		val s = raw.trim()
		if (s.isBlank()) return null
		// JSON array encoded as text.
		if (s.startsWith("[") && s.endsWith("]")) {
			return runCatching { JSONArray(s) }.getOrNull()
		}
		// Postgres array text like {4601,4800,b,2299,2438,t}
		if (s.startsWith("{") && s.endsWith("}")) {
			return parsePostgresArrayText(s)
		}
		return null
	}

	private fun parsePostgresArrayText(s: String): JSONArray {
		val inner = s.trim().removePrefix("{").removeSuffix("}")
		if (inner.isBlank()) return JSONArray()
		val out = JSONArray()
		inner.split(',').forEach { tokenRaw ->
			val token = tokenRaw.trim().trim('"', '\'')
			val asInt = token.toIntOrNull()
			if (asInt != null) out.put(asInt) else out.put(token)
		}
		return out
	}

	private fun parseRangesFromTriples(arr: JSONArray): Map<String, Pair<Int, Int>> {
		val out = linkedMapOf<String, Pair<Int, Int>>()
		var i = 0
		while (i + 2 < arr.length()) {
			val start = parseIntFlexible(arr.opt(i))
			val end = parseIntFlexible(arr.opt(i + 1))
			val code = arr.opt(i + 2)?.toString()?.trim()?.lowercase().orEmpty()
			if (start > 0 && end > 0 && code.length == 1) {
				out[code] = start to end
			}
			i += 3
		}
		return out
	}

	private fun parseIntFlexible(v: Any?): Int {
		return when (v) {
			is Int -> v
			is Long -> v.toInt()
			is Double -> v.toInt()
			is Float -> v.toInt()
			is String -> v.trim().toIntOrNull() ?: 0
			else -> 0
		}
	}

	private fun logHadithBookMetadataOnce(book: HadithBooksStackView.BookMeta) {
		val slug = book.slug.trim()
		if (slug.isBlank()) return
		val shouldFetch = hadithBookLogOnce.add(slug)

		val key = com.ayahverse.quran.BuildConfig.HADITH_API_KEY
		if (key.isBlank()) {
			Log.w("HadithBook", "Cannot log book metadata; HADITH_API_KEY is blank")
			return
		}

		Log.d(
			"HadithBook",
			"Selected book meta: title='${book.title}', slug='${book.slug}', dark=${book.dark}",
		)
		// If we've already fetched counts for this slug, update the chip immediately.
		updateHadithCollapsedChipText(bookSlug = slug, fallbackTitle = book.title)
		if (!shouldFetch) return

		scope.launch {
			val baseUrl = com.ayahverse.quran.BuildConfig.HADITH_API_BASE_URL.ifBlank { "https://hadithapi.com/" }
			val (bookJson, chaptersInfo) = withContext(Dispatchers.IO) {
				val bookInfo = fetchHadithApiBookJson(baseUrl = baseUrl, apiKey = key, bookSlug = slug)
				val chapterInfo = fetchHadithApiChaptersSummary(baseUrl = baseUrl, apiKey = key, bookSlug = slug)
				bookInfo to chapterInfo
			}
			if (bookJson != null) {
				val chaptersCount = parseIntFirstNonBlank(bookJson, listOf("chapters_count", "chaptersCount"))
				val hadithsCount = parseIntFirstNonBlank(bookJson, listOf("hadiths_count", "hadithsCount"))
				if (chaptersCount > 0 || hadithsCount > 0) {
					hadithBookCountsBySlug[slug] = HadithBookCounts(
						chaptersCount = chaptersCount.coerceAtLeast(0),
						hadithsCount = hadithsCount.coerceAtLeast(0),
					)
					updateHadithCollapsedChipText(bookSlug = slug)
				}
				Log.d("HadithBook", "HadithAPI book JSON for '$slug':\n${bookJson.toString(2)}")
			} else {
				Log.w("HadithBook", "HadithAPI did not return a matching book entry for '$slug'")
			}
			if (chaptersInfo.isNotBlank()) {
				Log.d("HadithBook", chaptersInfo)
			}
		}
	}

	private fun fetchHadithApiBookJson(
		baseUrl: String,
		apiKey: String,
		bookSlug: String,
	): JSONObject? {
		val httpUrl = baseUrl.toHttpUrlOrNull()
			?.newBuilder()
			?.addPathSegments("api/books")
			?.addQueryParameter("apiKey", apiKey)
			?.build()
			?: return null
		val req = Request.Builder().url(httpUrl).get().build()
		val resp = hadithHttp.newCall(req).execute()
		resp.use {
			if (!it.isSuccessful) {
				Log.w("HadithBook", "GET /api/books failed (${it.code})")
				return null
			}
			val body = it.body?.string().orEmpty()
			if (body.isBlank()) return null
			val root = JSONObject(body)
			val books = extractHadithApiDataArray(root, primaryKey = "books") ?: return null
			val normTarget = bookSlug.trim().lowercase()
			var match: JSONObject? = null
			val slugs = ArrayList<String>(minOf(books.length(), 25))
			for (i in 0 until books.length()) {
				val obj = books.optJSONObject(i) ?: continue
				val slug = firstNonBlank(
					obj,
					listOf("bookSlug", "book_slug", "slug", "book", "bookName", "book_name"),
				).trim().lowercase()
				if (slug.isNotBlank() && slugs.size < 25) slugs.add(slug)
				if (slug == normTarget) {
					match = obj
					break
				}
			}
			if (match == null) {
				Log.d("HadithBook", "Books returned=${books.length()} (sample slugs=${slugs.joinToString()})")
			}
			return match
		}
	}

	private fun fetchHadithApiChaptersSummary(
		baseUrl: String,
		apiKey: String,
		bookSlug: String,
	): String {
		val httpUrl = baseUrl.toHttpUrlOrNull()
			?.newBuilder()
			?.addPathSegments("api/$bookSlug/chapters")
			?.addQueryParameter("apiKey", apiKey)
			?.build()
			?: return ""
		val req = Request.Builder().url(httpUrl).get().build()
		val resp = hadithHttp.newCall(req).execute()
		resp.use {
			if (!it.isSuccessful) {
				return "HadithAPI chapters fetch failed (${it.code}) for '$bookSlug'"
			}
			val body = it.body?.string().orEmpty()
			if (body.isBlank()) return ""
			val root = JSONObject(body)
			val chapters = extractHadithApiDataArray(root, primaryKey = "chapters")
				?: extractHadithApiDataArray(root, primaryKey = "data")
				?: return "HadithAPI chapters: no data array for '$bookSlug'"
			val first = chapters.optJSONObject(0)
			val last = chapters.optJSONObject(maxOf(0, chapters.length() - 1))
			val firstName = first?.optString("chapterEnglish", first?.optString("chapter", ""))?.trim().orEmpty()
			val lastName = last?.optString("chapterEnglish", last?.optString("chapter", ""))?.trim().orEmpty()
			return buildString {
				append("HadithAPI chapters for '").append(bookSlug).append("': count=").append(chapters.length())
				if (firstName.isNotBlank()) append(", first='").append(firstName).append("'")
				if (lastName.isNotBlank()) append(", last='").append(lastName).append("'")
			}
		}
	}

	private fun extractHadithApiDataArray(root: JSONObject, primaryKey: String): JSONArray? {
		// Common shapes:
		// { "data": [ ... ] }
		// { "books": { "data": [ ... ] } }
		// { "chapters": { "data": [ ... ] } }
		val direct = root.optJSONArray(primaryKey)
		if (direct != null) return direct
		val nested = root.optJSONObject(primaryKey)
		if (nested != null) {
			nested.optJSONArray("data")?.let { return it }
			nested.optJSONArray(primaryKey)?.let { return it }
		}
		root.optJSONArray("data")?.let { return it }
		val booksObj = root.optJSONObject("books")
		booksObj?.optJSONArray("data")?.let { return it }
		val chaptersObj = root.optJSONObject("chapters")
		chaptersObj?.optJSONArray("data")?.let { return it }
		return null
	}

	private fun firstNonBlank(obj: JSONObject, keys: List<String>): String {
		for (k in keys) {
			val v = obj.optString(k, "").trim()
			if (v.isNotBlank()) return v
		}
		return ""
	}

	private fun parseIntFirstNonBlank(obj: JSONObject, keys: List<String>): Int {
		for (k in keys) {
			val raw = obj.opt(k)
			when (raw) {
				is Number -> return raw.toInt()
				is String -> {
					val v = raw.trim()
					if (v.isNotBlank()) return v.toIntOrNull() ?: 0
				}
			}
		}
		return 0
	}

	private fun updateHadithControls(visible: Boolean) {
		hadithNavRow.visibility = if (visible) View.VISIBLE else View.GONE
		if (visible) {
			val n = hadithCurrentNumber.coerceAtLeast(1)
			hadithNumberInput.setText(n.toString())
			hadithNumberInput.setSelection(hadithNumberInput.text?.length ?: 0)
			hadithPrevButton.isEnabled = n > 1
			hadithPrevButton.alpha = if (n > 1) 1f else 0.35f
		} else {
			hadithReadingRoot.setPadding(0, 0, 0, 0)
		}
	}

	private fun requestHadith(bookSlug: String, hadithNumber: Int) {
		val target = hadithNumber.coerceAtLeast(1)
		hadithActiveBookSlug = bookSlug
		hadithCurrentNumber = target
		updateHadithControls(visible = true)
		hadithCurrentPayload = null
		hadithTranslateJob?.cancel()
		hadithArabicTextView.text = "Loading…"
		hadithTranslationTextView.text = "Loading…"
		hadithFetchJob?.cancel()
		hadithFetchJob = scope.launch {
			val payload = withContext(Dispatchers.IO) {
				fetchHadithPayload(bookSlug = bookSlug, hadithNumber = target)
			}
			if (payload != null) {
				Log.d("HadithApi", "Loaded hadith for $bookSlug #$target (arLen=${payload.arabic.length} enLen=${payload.translationsByLang["en"]?.length ?: 0})")
				hadithCurrentPayload = payload
				hadithArabicTextView.text = payload.arabic.ifBlank { "(No Arabic text returned)" }
				hideHadithBookCover(animated = true)
				refreshHadithTranslation()
				updateHadithControls(visible = true)
				hadithTextScroll.post { hadithTextScroll.scrollTo(0, 0) }
				hadithTranslationScroll.post { hadithTranslationScroll.scrollTo(0, 0) }
			} else {
				Log.w("HadithApi", "No hadith payload returned for $bookSlug #$target")
				hadithArabicTextView.text = "(No hadith text returned)"
				hadithTranslationTextView.text = ""
				updateHadithControls(visible = true)
			}
		}
	}

	private fun fetchHadithPayload(bookSlug: String, hadithNumber: Int): HadithPayload? {
		val key = com.ayahverse.quran.BuildConfig.HADITH_API_KEY
		val base = com.ayahverse.quran.BuildConfig.HADITH_API_BASE_URL
		if (key.isBlank()) {
			Log.w("HadithApi", "HADITH_API_KEY is blank; configure it in ~/.gradle/gradle.properties")
			return null
		}
		val baseUrl = base.ifBlank { "https://hadithapi.com/" }
		val httpUrl = baseUrl.toHttpUrlOrNull()
			?.newBuilder()
			?.addPathSegments("api/hadiths/")
			?.addQueryParameter("apiKey", key)
			?.addQueryParameter("book", bookSlug)
			?.addQueryParameter("hadithNumber", hadithNumber.toString())
			?.addQueryParameter("paginate", "1")
			?.addQueryParameter("page", "1")
			?.build()
			?: return null
		val req = Request.Builder().url(httpUrl).get().build()
		val resp = hadithHttp.newCall(req).execute()
		resp.use {
			if (!it.isSuccessful) {
				Log.w("HadithApi", "Hadith fetch failed (${it.code}) for book=$bookSlug")
				return null
			}
			val body = it.body?.string().orEmpty()
			if (body.isBlank()) return null
			return extractHadithPayloadFromJson(body)
		}
	}

	private fun refreshHadithTranslation() {
		val payload = hadithCurrentPayload
		if (payload == null) {
			hadithTranslationTextView.text = ""
			return
		}
		val lang = hadithSelectedLangCode.trim().ifBlank { "en" }
		val direct = payload.translationsByLang[lang].orEmpty().trim()
		if (direct.isNotBlank()) {
			hadithTranslationTextView.text = direct
			applyHadithTranslationLayout(lang)
			return
		}

		val sourceText = payload.arabic.trim().ifBlank { payload.translationsByLang["en"].orEmpty().trim() }
		if (sourceText.isBlank()) {
			hadithTranslationTextView.text = ""
			applyHadithTranslationLayout(lang)
			return
		}

		val slug = hadithActiveBookSlug.orEmpty()
		val n = hadithCurrentNumber.coerceAtLeast(1)
		val cacheKey = "hadith:$slug:$n:$lang"
		val cached = hadithTranslationCache[cacheKey]
		if (!cached.isNullOrBlank()) {
			hadithTranslationTextView.text = cached
			applyHadithTranslationLayout(lang)
			return
		}

		hadithTranslateJob?.cancel()
		hadithTranslationTextView.text = "Translating…"
		applyHadithTranslationLayout(lang)
		hadithTranslateJob = scope.launch {
			val result = runCatching {
				withContext(Dispatchers.IO) {
					val sourceLang = if (payload.arabic.isNotBlank()) "ar" else "en"
					backendApi.translateText(
						BackendTranslateRequest(
							text = sourceText,
							targetLang = lang,
							sourceLang = sourceLang,
						),
					)
				}
			}.getOrNull()
			// Ensure we're still showing the same hadith.
			if (hadithActiveBookSlug != slug || hadithCurrentNumber != n || hadithSelectedLangCode != lang) return@launch
			val translated = result?.translatedText?.trim().orEmpty()
			if (translated.isNotBlank()) {
				hadithTranslationCache[cacheKey] = translated
				hadithTranslationTextView.text = translated
			} else {
				hadithTranslationTextView.text = "(No translation returned)"
			}
			applyHadithTranslationLayout(lang)
		}
	}

	private fun applyHadithTranslationLayout(langCode: String) {
		val rtl = isRtlLang(langCode)
		hadithTranslationTextView.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
		hadithTranslationTextView.gravity = if (rtl) Gravity.END else Gravity.START
	}

	private fun isRtlLang(code: String): Boolean {
		return when (code.trim().lowercase()) {
			"ar", "ur" -> true
			else -> false
		}
	}

	private fun hideKeyboard(view: View) {
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
		imm.hideSoftInputFromWindow(view.windowToken, 0)
	}

	private data class HadithLangOption(val code: String, val label: String)
	private data class HadithPayload(
		val arabic: String,
		val translationsByLang: Map<String, String>,
	)

	private fun extractHadithPayloadFromJson(raw: String): HadithPayload? {
		return runCatching {
			val root = JSONObject(raw)
			// Common shapes:
			// { "hadiths": { "data": [ {"hadithEnglish": "...", "hadithArabic": "..."} ] } }
			// { "data": [ {...} ] }
			val first = when {
				root.has("hadiths") -> {
					val h = root.opt("hadiths")
					when (h) {
						is JSONObject -> {
							val arr = h.optJSONArray("data") ?: h.optJSONArray("hadiths")
							arr?.optJSONObject(0)
						}
						else -> null
					}
				}
				root.has("data") -> root.optJSONArray("data")?.optJSONObject(0)
				else -> null
			} ?: return@runCatching null

			fun getFirstNonBlank(keys: List<String>): String {
				for (k in keys) {
					val v = first.optString(k, "").trim()
					if (v.isNotBlank()) return v
				}
				return ""
			}

			val arabic = getFirstNonBlank(
				listOf(
					"hadithArabic",
					"hadith_arabic",
					"arabic",
				),
			)

			val translations = linkedMapOf<String, String>()
			val english = getFirstNonBlank(
				listOf(
					"hadithEnglish",
					"hadith_english",
					"text",
					"hadith",
					"english",
				),
			)
			if (english.isNotBlank()) translations["en"] = english

			val urdu = getFirstNonBlank(listOf("hadithUrdu", "hadith_urdu", "urdu"))
			if (urdu.isNotBlank()) translations["ur"] = urdu
			val bengali = getFirstNonBlank(listOf("hadithBengali", "hadith_bengali", "bengali", "bangla", "hadithBangla"))
			if (bengali.isNotBlank()) translations["bn"] = bengali
			val turkish = getFirstNonBlank(listOf("hadithTurkish", "hadith_turkish", "turkish"))
			if (turkish.isNotBlank()) translations["tr"] = turkish
			val indonesian = getFirstNonBlank(listOf("hadithIndonesian", "hadith_indonesian", "indonesian"))
			if (indonesian.isNotBlank()) translations["id"] = indonesian
			val french = getFirstNonBlank(listOf("hadithFrench", "hadith_french", "french"))
			if (french.isNotBlank()) translations["fr"] = french
			val spanish = getFirstNonBlank(listOf("hadithSpanish", "hadith_spanish", "spanish"))
			if (spanish.isNotBlank()) translations["es"] = spanish
			val german = getFirstNonBlank(listOf("hadithGerman", "hadith_german", "german"))
			if (german.isNotBlank()) translations["de"] = german
			val russian = getFirstNonBlank(listOf("hadithRussian", "hadith_russian", "russian"))
			if (russian.isNotBlank()) translations["ru"] = russian
			val chinese = getFirstNonBlank(listOf("hadithChinese", "hadith_chinese", "chinese"))
			if (chinese.isNotBlank()) translations["zh"] = chinese
			val hindi = getFirstNonBlank(listOf("hadithHindi", "hadith_hindi", "hindi"))
			if (hindi.isNotBlank()) translations["hi"] = hindi

			return@runCatching HadithPayload(arabic = arabic, translationsByLang = translations)
		}.getOrNull()
	}

	private fun dpToPx(dp: Float): Int = (dp * density).toInt()

	fun onResume() = glView.onResume()
	fun onPause() = glView.onPause()
	fun dispose() {
		setAutoPlayEnabled(false)
		stopListenPlayback()
		runCatching { listenTts?.shutdown() }
		listenTts = null
		stopPlayback()
		runCatching { tafseerDropdown.dismiss() }
		runCatching { hadithLanguagePopup.dismiss() }
		dismissHadithQuickPopups()
		linguisticsFetchJob?.cancel()
		tafseerFetchJob?.cancel()
		scope.cancel()
	}
}

private class AyahTranslationRowView(
	context: Context,
	private val onTranslationSelected: (TranslationOption) -> Unit,
) : LinearLayout(context) {
	data class TranslationOption(
		val translationId: Int,
		val label: String,
	)

	private val density = resources.displayMetrics.density
	private var options: List<TranslationOption> = emptyList()
	private var selectedId: Int = QuranApi.DEFAULT_EN_TRANSLATION_ID
	private val translationText: TextView
	private val langChip: LinearLayout
	private val langLabel: TextView
	private val popup: ListPopupWindow

	init {
		orientation = HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
		background = GradientDrawable(
			GradientDrawable.Orientation.TOP_BOTTOM,
			intArrayOf(
				Color.argb(90, 255, 255, 255),
				Color.argb(45, 255, 255, 255),
			),
		).apply {
			cornerRadius = dpToPxF(18f)
			setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
		}

		translationText = TextView(context).apply {
			setTextColor(Color.WHITE)
			textSize = 14f
			// Allow the card to grow to fit the full translation.
			maxLines = Int.MAX_VALUE
			ellipsize = null
			setHorizontallyScrolling(false)
			text = "Loading translation…"
		}
		addView(translationText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

		langChip = LinearLayout(context).apply {
			orientation = HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			setPadding(dpToPx(12f), dpToPx(8f), dpToPx(10f), dpToPx(8f))
			background = GradientDrawable(
				GradientDrawable.Orientation.LEFT_RIGHT,
				intArrayOf(
					Color.argb(75, 255, 255, 255),
					Color.argb(35, 255, 255, 255),
				),
			).apply {
				cornerRadius = dpToPxF(16f)
				setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
			}
		}
		langLabel = TextView(context).apply {
			setTextColor(Color.argb(235, 212, 175, 55))
			textSize = 14f
			text = "English"
			setSingleLine(true)
		}
		val arrow = ImageView(context).apply {
			setImageResource(android.R.drawable.arrow_down_float)
			setColorFilter(Color.argb(235, 212, 175, 55))
		}
		langChip.addView(langLabel)
		langChip.addView(arrow, LayoutParams(dpToPx(18f), dpToPx(18f)).apply { leftMargin = dpToPx(6f) })
		addView(langChip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dpToPx(10f) })

		popup = ListPopupWindow(context).apply {
			anchorView = langChip
			isModal = true
			setOnItemClickListener { _, _, position, _ ->
				val opt = options.getOrNull(position) ?: return@setOnItemClickListener
				selectedId = opt.translationId
				langLabel.text = opt.label
				dismiss()
				onTranslationSelected(opt)
			}
			setBackgroundDrawable(
				GradientDrawable(
					GradientDrawable.Orientation.TOP_BOTTOM,
					intArrayOf(
						Color.argb(235, 20, 25, 35),
						Color.argb(235, 10, 12, 18),
					),
				).apply {
					cornerRadius = dpToPxF(18f)
					setStroke(dpToPx(1f), Color.argb(90, 255, 255, 255))
				},
			)
		}

		langChip.setOnClickListener {
			if (options.isNotEmpty()) {
				showPopup()
			}
		}
	}

	fun setOptions(options: List<TranslationOption>) {
		this.options = options
		val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, options.map { it.label }) {
			override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
				val v = super.getView(position, convertView, parent)
				(v as? TextView)?.apply {
					setTextColor(Color.WHITE)
					textSize = 16f
					setPadding(dpToPx(18f), dpToPx(12f), dpToPx(18f), dpToPx(12f))
				}
				return v
			}
		}
		popup.setAdapter(adapter)
	}

	fun setSelectedTranslationId(id: Int) {
		selectedId = id
		val opt = options.firstOrNull { it.translationId == id }
		langLabel.text = opt?.label ?: "English"
	}

	fun setLoading() {
		translationText.text = "Loading translation…"
	}

	fun setTranslation(text: String) {
		translationText.text = if (text.isBlank()) "" else text
	}

	private fun showPopup() {
		popup.width = dpToPx(240f)
		popup.verticalOffset = dpToPx(10f)
		popup.show()
	}

	private fun dpToPx(dp: Float): Int = (dp * density).toInt()
	private fun dpToPxF(dp: Float): Float = dp * density
}

private class AyahMeaningTabsRowView(
	context: Context,
	private val onTabToggled: (Tab?) -> Unit,
) : LinearLayout(context) {
	enum class Tab { Linguistics, Tafseer }
	private val density = resources.displayMetrics.density
	private var selectedTab: Tab? = null
	private var tabsEnabled: Boolean = true

	private val linguisticsTab: TextView
	private val tafseerTab: TextView
	private fun tabBg(selected: Boolean): GradientDrawable {
		return GradientDrawable(
			GradientDrawable.Orientation.LEFT_RIGHT,
			intArrayOf(
				Color.argb(if (selected) 85 else 45, 255, 255, 255),
				Color.argb(if (selected) 55 else 25, 255, 255, 255),
			),
		).apply {
			cornerRadius = dpToPxF(14f)
			setStroke(dpToPx(1f), Color.argb(if (selected) 85 else 55, 255, 255, 255))
		}
	}

	init {
		orientation = HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		setPadding(dpToPx(10f), dpToPx(8f), dpToPx(10f), dpToPx(8f))
		background = GradientDrawable(
			GradientDrawable.Orientation.TOP_BOTTOM,
			intArrayOf(
				Color.argb(70, 255, 255, 255),
				Color.argb(35, 255, 255, 255),
			),
		).apply {
			cornerRadius = dpToPxF(18f)
			setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
		}

		linguisticsTab = TextView(context).apply {
			text = "Linguistics"
			setTextColor(Color.WHITE)
			textSize = 14f
			gravity = Gravity.CENTER
			setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
			background = tabBg(selectedTab == Tab.Linguistics)
			setOnClickListener {
				if (!tabsEnabled) return@setOnClickListener
				selectedTab = if (selectedTab == Tab.Linguistics) null else Tab.Linguistics
				updateSelection()
				onTabToggled(selectedTab)
			}
			isClickable = true
			isFocusable = true
		}

		tafseerTab = TextView(context).apply {
			text = "Tafseer"
			setTextColor(Color.WHITE)
			textSize = 14f
			gravity = Gravity.CENTER
			setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
			background = tabBg(selectedTab == Tab.Tafseer)
			setOnClickListener {
				if (!tabsEnabled) return@setOnClickListener
				selectedTab = if (selectedTab == Tab.Tafseer) null else Tab.Tafseer
				updateSelection()
				onTabToggled(selectedTab)
			}
			isClickable = true
			isFocusable = true
		}

		addView(
			linguisticsTab,
			LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
		)
		addView(
			tafseerTab,
			LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dpToPx(8f) },
		)
	}

	fun setSelectedTab(tab: Tab?) {
		selectedTab = tab
		updateSelection()
	}

	fun setTabsEnabled(enabled: Boolean) {
		tabsEnabled = enabled
		linguisticsTab.isEnabled = enabled
		tafseerTab.isEnabled = enabled
		linguisticsTab.isClickable = enabled
		tafseerTab.isClickable = enabled
		updateSelection()
	}

	private fun updateSelection() {
		linguisticsTab.background = tabBg(selectedTab == Tab.Linguistics)
		tafseerTab.background = tabBg(selectedTab == Tab.Tafseer)
		if (!tabsEnabled) {
			linguisticsTab.alpha = 0.45f
			tafseerTab.alpha = 0.45f
		} else {
			linguisticsTab.alpha = if (selectedTab == Tab.Linguistics) 1f else 0.78f
			tafseerTab.alpha = if (selectedTab == Tab.Tafseer) 1f else 0.78f
		}
	}

	private fun dpToPx(dp: Float): Int = (dp * density).toInt()
	private fun dpToPxF(dp: Float): Float = dp * density
}

private fun String.stripHtmlTags(): String {
	return this
		.replace(Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE), "\n")
		.replace(Regex("<\\s*/\\s*p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
		.replace(Regex("<\\s*/\\s*div\\s*>", RegexOption.IGNORE_CASE), "\n\n")
		.replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
		.replace(Regex("<[^>]*>"), "")
}

private class TafsirDropdownAdapter(
	private val context: Context,
	entries: List<TafsirDropdownOption>,
) : BaseAdapter() {
	private val density = context.resources.displayMetrics.density
	private var entries: List<TafsirDropdownOption> = entries

	fun update(newEntries: List<TafsirDropdownOption>) {
		entries = newEntries
		notifyDataSetChanged()
	}

	override fun getCount(): Int = entries.size
	override fun getItem(position: Int): Any = entries[position]
	override fun getItemId(position: Int): Long = position.toLong()

	override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
		val entry = entries[position]
		val root = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
			background = GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(
					Color.argb(85, 255, 255, 255),
					Color.argb(35, 255, 255, 255),
				),
			).apply {
				cornerRadius = dpToPx(14f).toFloat()
				setStroke(dpToPx(1f), Color.argb(55, 255, 255, 255))
			}
		}

		val title = (root.getChildAt(0) as? TextView) ?: TextView(context).also { tv ->
			tv.setTextColor(Color.argb(240, 212, 175, 55))
			tv.textSize = 17f
			tv.maxLines = 1
			root.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
		}
		// Ensure we don't keep any older extra lines if a view gets recycled.
		while (root.childCount > 1) root.removeViewAt(root.childCount - 1)

		title.text = entry.displayName
		return root
	}

	private fun dpToPx(dp: Float): Int = (dp * density).toInt()
}

private class AyahAudioControlsView(
	context: Context,
	private val onMicClick: () -> Unit,
	private val onReciterSelected: (Int) -> Unit,
) : LinearLayout(context) {
	private val density = resources.displayMetrics.density
	private val reciters = mutableListOf<QuranAudioApi.Reciter>()
	private val spinner: Spinner
	private val micButton: ImageButton

	init {
		orientation = HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))

		background = GradientDrawable(
			GradientDrawable.Orientation.TOP_BOTTOM,
			intArrayOf(
				Color.argb(90, 255, 255, 255),
				Color.argb(45, 255, 255, 255),
			),
		).apply {
			cornerRadius = dpToPxF(18f)
			setStroke(dpToPx(1f), Color.argb(70, 255, 255, 255))
		}

		micButton = ImageButton(context).apply {
			setImageResource(android.R.drawable.ic_btn_speak_now)
			setBackgroundColor(Color.TRANSPARENT)
			setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
			setOnClickListener { onMicClick() }
			contentDescription = "Play ayah"
		}
		addView(
			micButton,
			LayoutParams(dpToPx(44f), dpToPx(44f)).apply {
				rightMargin = dpToPx(10f)
			},
		)

		spinner = Spinner(context, Spinner.MODE_DROPDOWN)
		addView(
			spinner,
			LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
		)

		spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				val reciterId = reciters.getOrNull(position)?.id ?: return
				onReciterSelected(reciterId)
			}
			override fun onNothingSelected(parent: AdapterView<*>?) = Unit
		}

		// Default adapter while loading.
		setReciters(emptyList())
	}

	fun setPlaying(playing: Boolean) {
		micButton.setImageResource(
			if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_btn_speak_now,
		)
		micButton.contentDescription = if (playing) "Stop" else "Play ayah"
	}

	fun setReciters(list: List<QuranAudioApi.Reciter>) {
		reciters.clear()
		reciters.addAll(list)
		val names = if (list.isEmpty()) listOf("Loading reciters…") else list.map { it.name }
		val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, names) {
			override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
				val v = super.getView(position, convertView, parent)
				(v as? android.widget.TextView)?.apply {
					setTextColor(Color.argb(235, 212, 175, 55))
					textSize = 16f
					setSingleLine(true)
				}
				return v
			}

			override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
				val v = super.getDropDownView(position, convertView, parent)
				(v as? android.widget.TextView)?.apply {
					setTextColor(Color.WHITE)
					textSize = 16f
					setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(12f))
				}
				return v
			}
		}.apply {
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		}
		spinner.adapter = adapter
		spinner.isEnabled = list.isNotEmpty()
	}

	fun setSelectedReciterId(reciterId: Int) {
		val idx = reciters.indexOfFirst { it.id == reciterId }
		if (idx >= 0) spinner.setSelection(idx)
	}

	private fun dpToPx(dp: Float): Int = (dp * density).toInt()
	private fun dpToPxF(dp: Float): Float = dp * density
}

private class SurahWheelGLSurfaceView(
	context: Context,
	private val renderer: SurahWheelRenderer,
) : GLSurfaceView(context) {
	private val uiHandler = Handler(Looper.getMainLooper())
	private var lowPowerModeEnabled: Boolean = false
	private var lastY = 0f
	private var lastT = 0L
	private var velocityIndexPerSec = 0f
	private var dragging = false
	private val indexPerPixel = 1f / 42f
	private val returnToLowPowerRunnable = Runnable {
		runCatching {
			if (lowPowerModeEnabled && !dragging) {
				renderMode = RENDERMODE_WHEN_DIRTY
				requestRender()
			}
		}
	}

	init {
		setEGLContextClientVersion(3)
		setRenderer(renderer)
		renderMode = RENDERMODE_CONTINUOUSLY
		preserveEGLContextOnPause = true
	}

	fun setLowPowerModeEnabled(enabled: Boolean) {
		if (lowPowerModeEnabled == enabled) return
		lowPowerModeEnabled = enabled
		uiHandler.removeCallbacks(returnToLowPowerRunnable)
		runCatching {
			renderMode = if (enabled) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY
			requestRender()
		}
	}

	private fun enterInteractiveRenderingMode() {
		if (!lowPowerModeEnabled) return
		uiHandler.removeCallbacks(returnToLowPowerRunnable)
		runCatching {
			renderMode = RENDERMODE_CONTINUOUSLY
			requestRender()
		}
	}

	private fun scheduleReturnToLowPowerAfterTouchStop() {
		if (!lowPowerModeEnabled) return
		uiHandler.removeCallbacks(returnToLowPowerRunnable)
		uiHandler.postDelayed(returnToLowPowerRunnable, 60_000L)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				enterInteractiveRenderingMode()
				// Allow dragging anywhere inside the wheel view (it may be resized inside the bottom card).
				dragging = true
				lastY = event.y
				lastT = event.eventTime
				velocityIndexPerSec = 0f
				queueEvent { renderer.setUserDragging(true) }
				if (renderMode == RENDERMODE_WHEN_DIRTY) requestRender()
				return true
			}
			MotionEvent.ACTION_MOVE -> {
				if (!dragging) return true
				enterInteractiveRenderingMode()
				val y = event.y
				val dy = y - lastY
				lastY = y
				val nowT = event.eventTime
				val dt = ((nowT - lastT).coerceAtLeast(1L)).toFloat() / 1000f
				lastT = nowT
				val deltaIndex = dy * indexPerPixel
				val instVel = deltaIndex / dt
				// Low-pass filter to keep fling smooth.
				velocityIndexPerSec = velocityIndexPerSec * 0.85f + instVel * 0.15f
				queueEvent {
					renderer.onWheelDrag(deltaIndex)
				}
				if (renderMode == RENDERMODE_WHEN_DIRTY) requestRender()
				return true
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				dragging = false
				val v = velocityIndexPerSec
				velocityIndexPerSec = 0f
				queueEvent {
					renderer.setUserDragging(false)
					renderer.onWheelRelease(v)
				}
				if (renderMode == RENDERMODE_WHEN_DIRTY) requestRender()
				scheduleReturnToLowPowerAfterTouchStop()
				return true
			}
			else -> return true
		}
	}
}

private class StarfieldGLSurfaceView(context: Context) : GLSurfaceView(context) {
	private val renderer = SurahWheelRenderer(
		onProjectedCenters = null,
		drawStarfield = true,
		drawWheel = false,
	)

	init {
		setEGLContextClientVersion(3)
		setRenderer(renderer)
		renderMode = RENDERMODE_CONTINUOUSLY
		preserveEGLContextOnPause = true
		isClickable = false
		isFocusable = false
	}
}

private class SurahLabelsOverlayView(context: Context) : View(context) {
	private val lock = Any()
	private val projected = FloatArray(SURA_NAMES.size * 3) // xPx, yPx, alpha(0..1)
	private val labels: Array<String> = Array(SURA_NAMES.size) { i -> "${i + 1}. ${SURA_NAMES[i]}" }
	private val baseTextSizePx = spToPx(15f)
	private val leftMarginPx = dpToPx(18f)
	@Volatile private var activeIndex: Int = 0

	private val leafFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(235, 60, 214, 120) // vivid green
	}
	private val leafStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dpToPx(1.2f)
		color = Color.argb(210, 20, 120, 60)
	}

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		alpha = 230
		textSize = baseTextSizePx
		textSize = dpToPx(22f)
	}

	fun updateProjectedCenters(projectedCenters: FloatArray) {
		// Called from GL thread; copy into local buffer for UI thread draw.
		synchronized(lock) {
			val n = minOf(projected.size, projectedCenters.size)
			for (i in 0 until n) projected[i] = projectedCenters[i]
		}
		postInvalidateOnAnimation()
	}

	fun setActiveIndex(index: Int) {
		activeIndex = index.coerceIn(0, labels.size - 1)
		postInvalidateOnAnimation()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val local = FloatArray(projected.size)
		synchronized(lock) {
			for (i in local.indices) local[i] = projected[i]
		}

		for (i in labels.indices) {
			val o = i * 3
			val a = local[o + 2].coerceIn(0f, 1f)
			if (a <= 0.01f) continue
			val y = local[o + 1]
			val x = leftMarginPx
			if (y < 0f || y > height.toFloat()) continue
			paint.alpha = (55f + a * 200f).toInt().coerceIn(0, 255)
			paint.textSize = baseTextSizePx * (0.82f + a * 0.42f)
			canvas.drawText(labels[i], x, y, paint)
		}

		// Draw a small green bookmark leaf pointing to the active surah.
		val ai = activeIndex.coerceIn(0, labels.size - 1)
		val o = ai * 3
		val a = local[o + 2].coerceIn(0f, 1f)
		if (a > 0.08f) {
			val y = local[o + 1]
			if (y >= 0f && y <= height.toFloat()) {
				val textSize = baseTextSizePx * (0.82f + a * 0.42f)
				val leafW = dpToPx(14f)
				val leafH = dpToPx(22f)
				val tipX = (leftMarginPx - dpToPx(4f)).coerceAtLeast(dpToPx(8f))
				val cx = tipX - leafW * 0.55f
				val cy = y - textSize * 0.35f

				val left = cx - leafW * 0.55f
				val right = cx + leafW * 0.45f
				val top = cy - leafH * 0.5f
				val bottom = cy + leafH * 0.5f

				leafFillPaint.alpha = (110 + a * 130f).toInt().coerceIn(120, 240)
				leafStrokePaint.alpha = (90 + a * 130f).toInt().coerceIn(110, 230)

				val path = android.graphics.Path().apply {
					// Tip points to the active label.
					moveTo(tipX, cy)
					quadTo(right, top, cx, top + leafH * 0.14f)
					quadTo(left, cy, cx, bottom - leafH * 0.14f)
					quadTo(right, bottom, tipX, cy)
					close()
				}
				canvas.drawPath(path, leafFillPaint)
				canvas.drawPath(path, leafStrokePaint)
			}
		}
	}

	private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
	private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}

private class StarryBackgroundView(context: Context) : View(context) {
	private data class Star(
		val x: Float,
		val y: Float,
		val z: Float,
		val rPx: Float,
		val baseA: Float,
		val phase: Float,
		val freq: Float,
	)

	private val density = resources.displayMetrics.density
	private val stars = ArrayList<Star>(900)
	private val rand = java.util.Random(114)
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.WHITE
	}
	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		postInvalidateOnAnimation()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		stars.clear()
		if (w <= 0 || h <= 0) return

		// Keep this light: enough stars to cover the full screen, but cheap to draw.
		val area = (w.toLong() * h.toLong()).coerceAtLeast(1L)
		val count = ((area / 5200L).toInt()).coerceIn(450, 1100)
		val minR = 0.7f * density
		val maxR = 1.8f * density
		for (i in 0 until count) {
			// 3D positions in a unit-ish cube.
			val x = (rand.nextFloat() * 2f) - 1f
			val y = (rand.nextFloat() * 2f) - 1f
			val z = 0.35f + (2.2f - 0.35f) * rand.nextFloat()
			val r = minR + (maxR - minR) * rand.nextFloat()
			val baseA = 0.30f + 0.70f * rand.nextFloat()
			val phase = (rand.nextFloat() * (2f * kotlin.math.PI.toFloat()))
			val freq = 0.35f + 0.65f * rand.nextFloat()
			stars.add(Star(x = x, y = y, z = z, rPx = r, baseA = baseA, phase = phase, freq = freq))
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(Color.BLACK)
		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		val minDim = kotlin.math.min(w, h)
		val t = (android.os.SystemClock.uptimeMillis() % 60_000L) / 1000f
		val cx = w * 0.5f
		val cy = h * 0.5f
		val rot = t * 0.10f
		val sinR = kotlin.math.sin(rot)
		val cosR = kotlin.math.cos(rot)
		val f = minDim * 0.62f

		for (s in stars) {
			// Rotate in XZ for parallax.
			val xzX = (s.x * cosR) + (s.z * sinR)
			val xzZ = (s.z * cosR) - (s.x * sinR)
			val z = xzZ.coerceAtLeast(0.18f)
			val persp = 1.0f / z
			val px = cx + xzX * persp * f
			val py = cy + s.y * persp * f
			if (px < -8f || px > w + 8f || py < -8f || py > h + 8f) continue

			val flicker = 0.72f + 0.28f * kotlin.math.sin(s.phase + t * (2f * kotlin.math.PI.toFloat()) * s.freq)
			val depthA = (2.0f / (z + 0.35f)).coerceIn(0.25f, 1.0f)
			val a = (s.baseA * flicker * depthA).coerceIn(0f, 1f)
			paint.alpha = (18f + a * 235f).toInt().coerceIn(0, 255)
			val r = (s.rPx * (1.55f / (z + 0.10f))).coerceIn(0.5f * density, 2.6f * density)
			canvas.drawCircle(px, py, r, paint)
		}

		// Keep subtle motion.
		postInvalidateOnAnimation()
	}
}

private class ArabicCircularCalligraphyButtonView(context: Context) : View(context) {
	private val density = resources.displayMetrics.density
	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		isFilterBitmap = true
	}
	private val logoDstRect = Rect()
	private var logoBitmap: Bitmap? = null
	private var logoAssetName: String? = null
	private var logoLoadAttempted: Boolean = false
	private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.4f)
		color = Color.argb(210, 212, 175, 55)
	}
	private val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(0.9f)
		color = Color.argb(120, 255, 255, 255)
	}
	private val calligraphyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(235, 212, 175, 55)
		textSize = dp(14f)
		typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.BOLD)
		setShadowLayer(dp(10f), 0f, 0f, Color.argb(120, 255, 255, 255))
	}
	private val centerGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(210, 255, 255, 255)
		textAlign = Paint.Align.CENTER
		textSize = dp(24f)
		typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.BOLD)
		setShadowLayer(dp(12f), 0f, 0f, Color.argb(150, 212, 175, 55))
	}
	private val circlePath = Path()

	init {
		isClickable = true
		isFocusable = true
	}

	fun setLogoFromAssets(assetName: String) {
		logoAssetName = assetName.trim().ifBlank { null }
		logoLoadAttempted = false
		logoBitmap = null
		invalidate()
	}

	private fun ensureLogoLoaded() {
		if (logoLoadAttempted) return
		logoLoadAttempted = true
		val primary = logoAssetName ?: return
		val candidates = linkedSetOf(primary, "logo.png")
		for (name in candidates) {
			val bmp = runCatching {
				context.assets.open(name).use { input ->
					BitmapFactory.decodeStream(input)
				}
			}.getOrNull()
			if (bmp != null) {
				logoBitmap = bmp
				return
			}
		}
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		updateLogoRect()
	}

	private fun updateLogoRect() {
		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		val cx = w * 0.5f
		val cy = h * 0.5f
		val r = (minOf(w, h) * 0.5f) - dp(1.0f)
		val maxSide = ((r - dp(9.5f)) * 2f).coerceAtLeast(1f) * 0.82f
		val half = maxSide * 0.5f
		logoDstRect.set(
			(cx - half).toInt(),
			(cy - half).toInt(),
			(cx + half).toInt(),
			(cy + half).toInt(),
		)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		val cx = w * 0.5f
		val cy = h * 0.5f
		val r = (minOf(w, h) * 0.5f) - dp(1.0f)
		if (r <= 1f) return

		fillPaint.shader = RadialGradient(
			cx,
			cy,
			r,
			intArrayOf(
				Color.argb(55, 255, 255, 255),
				Color.argb(18, 255, 255, 255),
				Color.argb(6, 255, 255, 255),
			),
			floatArrayOf(0f, 0.65f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx, cy, r, fillPaint)

		// Rim.
		canvas.drawCircle(cx, cy, r, rimPaint)
		canvas.drawCircle(cx, cy, (r - dp(2.2f)).coerceAtLeast(1f), innerRimPaint)

		ensureLogoLoaded()
		val logo = logoBitmap
		if (logo != null) {
			if (logoDstRect.isEmpty) updateLogoRect()
			canvas.drawBitmap(logo, null, logoDstRect, logoPaint)
		} else {
			// Circular calligraphy text.
			val textR = (r - dp(7.5f)).coerceAtLeast(1f)
			circlePath.reset()
			circlePath.addCircle(cx, cy, textR, Path.Direction.CW)
			val base = "القرآن"
			val sep = "  •  "
			val circumference = (2f * kotlin.math.PI.toFloat() * textR).coerceAtLeast(1f)
			var text = base
			while (calligraphyPaint.measureText(text) < circumference * 1.18f) {
				text += sep + base
			}
			canvas.drawTextOnPath(text, circlePath, 0f, dp(3.0f), calligraphyPaint)

			// Center glyph.
			val fm = centerGlyphPaint.fontMetrics
			val baseline = cy - (fm.ascent + fm.descent) * 0.5f
			canvas.drawText("ق", cx, baseline, centerGlyphPaint)
		}
	}

	private fun dp(v: Float): Float = v * density
}

private class GlobeMenuView(context: Context) : View(context) {
	private data class MenuItem(
		val key: String,
		val latDeg: Float,
		val lonDeg: Float,
	)

	private val density = resources.displayMetrics.density
	private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		isFilterBitmap = true
	}
	private var centerLogo: Bitmap? = null
	private var centerLogoLoadAttempted: Boolean = false
	private val edgeVignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val edgeSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val deepFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val terminatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.15f)
		color = Color.argb(150, 255, 255, 255)
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
		pathEffect = android.graphics.DiscretePathEffect(dp(2.4f), dp(0.9f))
	}
	private val mapGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.65f)
		color = Color.argb(130, 212, 175, 55)
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
		maskFilter = BlurMaskFilter(dp(3.4f), BlurMaskFilter.Blur.NORMAL)
	}
	private val landFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(42, 255, 255, 255)
	}
	private val landSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(34, 212, 175, 55)
		maskFilter = BlurMaskFilter(dp(2.2f), BlurMaskFilter.Blur.NORMAL)
	}
	private val landStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(0.9f)
		color = Color.argb(85, 255, 255, 255)
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
	}
	private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val wordEmbossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(120, 0, 0, 0)
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
	}
	private val wordGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(235, 212, 175, 55)
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
	}
	private val wordMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
	}
	private val circlePath = Path()
	private val hitRects = HashMap<String, RectF>()
	private var onItemClick: ((String) -> Unit)? = null
	private val tmpRect = RectF()

	private val items = listOf(
		MenuItem(key = "quran", latDeg = 12f, lonDeg = 25f),
		MenuItem(key = "hadith", latDeg = -18f, lonDeg = 95f),
		MenuItem(key = "markaz", latDeg = 38f, lonDeg = -70f),
		MenuItem(key = "listen", latDeg = -42f, lonDeg = -25f),
		MenuItem(key = "feedback", latDeg = 5f, lonDeg = 155f),
	)

	init {
		isClickable = true
		isFocusable = true
	}

	private fun ensureCenterLogoLoaded() {
		if (centerLogoLoadAttempted) return
		centerLogoLoadAttempted = true
		val candidates = listOf("global_menu_logo.png", "logo.png")
		for (name in candidates) {
			val bmp = runCatching {
				context.assets.open(name).use { input ->
					BitmapFactory.decodeStream(input)
				}
			}.getOrNull()
			if (bmp != null) {
				centerLogo = bmp
				return
			}
		}
	}

	fun setOnItemClickListener(listener: (String) -> Unit) {
		onItemClick = listener
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val w = MeasureSpec.getSize(widthMeasureSpec)
		val h = MeasureSpec.getSize(heightMeasureSpec)
		val size = (minOf(w, h) * 0.78f).toInt().coerceAtLeast(dpToPx(240f))
		setMeasuredDimension(size, size)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		val cx = w * 0.5f
		val cy = h * 0.5f
		val r = (minOf(w, h) * 0.44f).coerceAtLeast(1f)
		val t = (android.os.SystemClock.uptimeMillis() % 120_000L) / 1000f
		val spin = t * 0.35f
		val cosY = kotlin.math.cos(spin)
		val sinY = kotlin.math.sin(spin)
		val tilt = 0.30f
		val cosX = kotlin.math.cos(tilt)
		val sinX = kotlin.math.sin(tilt)

		hitRects.clear()
		circlePath.reset()
		circlePath.addCircle(cx, cy, r, Path.Direction.CW)
		ensureCenterLogoLoaded()

		// Deep sphere shading for 3D vibe.
		deepFillPaint.shader = RadialGradient(
			cx - r * 0.34f,
			cy - r * 0.36f,
			r * 1.35f,
			intArrayOf(
				Color.argb(36, 255, 255, 255),
				Color.argb(18, 140, 180, 255),
				Color.argb(10, 0, 0, 0),
			),
			floatArrayOf(0f, 0.62f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx, cy, r, deepFillPaint)

		// Glassy sphere base.
		glassFillPaint.shader = RadialGradient(
			cx - r * 0.25f,
			cy - r * 0.30f,
			r * 1.25f,
			intArrayOf(
				Color.argb(38, 255, 255, 255),
				Color.argb(10, 255, 255, 255),
				Color.argb(0, 255, 255, 255),
			),
			floatArrayOf(0f, 0.55f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx, cy, r, glassFillPaint)

		// Soft terminator shadow for depth.
		terminatorPaint.shader = RadialGradient(
			cx + r * 0.35f,
			cy + r * 0.15f,
			r * 1.05f,
			intArrayOf(
				Color.argb(0, 0, 0, 0),
				Color.argb(12, 0, 0, 0),
				Color.argb(38, 0, 0, 0),
			),
			floatArrayOf(0f, 0.55f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx, cy, r, terminatorPaint)

		// Glassy world map clipped to sphere.
		val saved = canvas.save()
		canvas.clipPath(circlePath)
		drawGlassyWorldMap(canvas, cx, cy, r, cosY, sinY, cosX, sinX)
		canvas.restoreToCount(saved)

		// Edge shading (avoid a hard rim circle that reads 2D).
		edgeVignettePaint.shader = RadialGradient(
			cx,
			cy,
			r * 1.05f,
			intArrayOf(
				Color.argb(0, 0, 0, 0),
				Color.argb(0, 0, 0, 0),
				Color.argb(52, 0, 0, 0),
			),
			floatArrayOf(0f, 0.78f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx, cy, r, edgeVignettePaint)
		edgeSpecularPaint.shader = RadialGradient(
			cx - r * 0.35f,
			cy - r * 0.40f,
			r * 0.95f,
			intArrayOf(
				Color.argb(30, 255, 255, 255),
				Color.argb(0, 255, 255, 255),
			),
			floatArrayOf(0f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx, cy, r, edgeSpecularPaint)
		highlightPaint.shader = LinearGradient(
			cx - r,
			cy - r,
			cx,
			cy,
			intArrayOf(
				Color.argb(85, 255, 255, 255),
				Color.argb(0, 255, 255, 255),
			),
			floatArrayOf(0f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx - r * 0.22f, cy - r * 0.22f, r * 0.52f, highlightPaint)

		// Center logo.
		centerLogo?.let { bmp ->
			val logoR = (r * 0.42f).coerceAtLeast(dp(12f))
			val logoCx = cx
			val logoCy = cy
			val bgR = (logoR + dp(9f)).coerceAtMost(r - dp(10f))
			val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				shader = RadialGradient(
					logoCx - bgR * 0.2f,
					logoCy - bgR * 0.25f,
					bgR * 1.15f,
					intArrayOf(
						Color.argb(22, 255, 255, 255),
						Color.argb(10, 255, 255, 255),
						Color.argb(6, 0, 0, 0),
					),
					floatArrayOf(0f, 0.65f, 1f),
					Shader.TileMode.CLAMP,
				)
			}
			canvas.drawCircle(logoCx, logoCy, bgR, bgPaint)
			canvas.drawCircle(logoCx, logoCy, bgR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
				style = Paint.Style.STROKE
				strokeWidth = dp(1.1f)
				color = Color.argb(90, 212, 175, 55)
				maskFilter = BlurMaskFilter(dp(3.0f), BlurMaskFilter.Blur.NORMAL)
			})
			val dst = RectF(logoCx - logoR, logoCy - logoR, logoCx + logoR, logoCy + logoR)
			val clipPath = Path().apply { addCircle(logoCx, logoCy, logoR, Path.Direction.CW) }
			val s2 = canvas.save()
			canvas.clipPath(clipPath)
			canvas.drawBitmap(bmp, null, dst, logoPaint)
			canvas.restoreToCount(s2)
		}

		// Words scattered over the sphere surface.
		val baseText = dp(18f)
		for (item in items) {
			val label = when (item.key.lowercase()) {
				"listen" -> "Listen"
				"markaz" -> "Tajweed"
				else -> item.key
			}
			val p = projectOnSphere(item.latDeg, item.lonDeg, cosY, sinY, cosX, sinX)
			val z = p[2]
			val front = ((z + 1f) * 0.5f).coerceIn(0f, 1f)
			val scale = (0.70f + front * 0.55f)
			val alpha = (0.20f + front * 0.80f)
			val px = cx + p[0] * r * 0.92f
			val py = cy + p[1] * r * 0.92f
			if ((px - cx) * (px - cx) + (py - cy) * (py - cy) > r * r * 0.98f) continue

			val textSize = baseText * scale
			val a = (40f + alpha * 215f).toInt().coerceIn(0, 255)
			wordEmbossPaint.textSize = textSize
			wordGlowPaint.textSize = textSize
			wordMainPaint.textSize = textSize
			wordEmbossPaint.alpha = (a * 0.65f).toInt().coerceIn(0, 255)
			wordGlowPaint.alpha = (a * 0.95f).toInt().coerceIn(0, 255)
			wordMainPaint.alpha = a

			val fm = wordMainPaint.fontMetrics
			val baseline = py - (fm.ascent + fm.descent) * 0.5f

			// Glow + emboss gives a 3D/pro look.
			wordGlowPaint.setShadowLayer(dp(16f) * scale, 0f, 0f, Color.argb((110f + front * 120f).toInt().coerceIn(0, 255), 140, 180, 255))
			canvas.drawText(label, px + dp(0.85f), baseline + dp(0.95f), wordEmbossPaint)
			canvas.drawText(label, px, baseline, wordGlowPaint)

			// Main gradient text.
			wordMainPaint.shader = LinearGradient(
				px,
				baseline - textSize,
				px,
				baseline + textSize * 0.35f,
				intArrayOf(
					Color.argb(a, 212, 175, 55),
					Color.argb(a, 255, 255, 255),
					Color.argb(a, 210, 235, 255),
				),
				floatArrayOf(0f, 0.55f, 1f),
				Shader.TileMode.CLAMP,
			)
			canvas.drawText(label, px, baseline, wordMainPaint)
			wordMainPaint.shader = null

			val textW = wordMainPaint.measureText(label)
			val rect = RectF(
				(px - textW * 0.5f) - dp(8f),
				(py + fm.ascent) - dp(10f),
				(px + textW * 0.5f) + dp(8f),
				(py + fm.descent) + dp(10f),
			)
			hitRects[item.key] = rect
		}

		postInvalidateOnAnimation()
	}

	private fun drawGlassyWorldMap(
		canvas: Canvas,
		cx: Float,
		cy: Float,
		r: Float,
		cosY: Float,
		sinY: Float,
		cosX: Float,
		sinX: Float,
	) {
		// Approximate continent shapes (lat, lon pairs). We draw as glassy filled shapes + subtle outline.
		val continents = listOf(
			floatArrayOf(
				72f, -165f,
				55f, -150f,
				40f, -125f,
				22f, -105f,
				15f, -90f,
				28f, -80f,
				45f, -75f,
				58f, -95f,
				70f, -130f,
				72f, -165f,
			),
			floatArrayOf(
				12f, -82f,
				-5f, -78f,
				-20f, -70f,
				-35f, -60f,
				-50f, -67f,
				-55f, -73f,
				-35f, -50f,
				-10f, -48f,
				10f, -60f,
				12f, -82f,
			),
			floatArrayOf(
				70f, -10f,
				58f, 10f,
				45f, 35f,
				38f, 55f,
				30f, 70f,
				18f, 85f,
				5f, 100f,
				-10f, 115f,
				-25f, 135f,
				-35f, 120f,
				-10f, 55f,
				10f, 25f,
				30f, 10f,
				55f, -15f,
				70f, -10f,
			),
			floatArrayOf(
				-10f, 112f,
				-20f, 114f,
				-30f, 120f,
				-35f, 132f,
				-28f, 150f,
				-18f, 153f,
				-10f, 145f,
				-10f, 112f,
			),
		)

		// Soft sheen direction for the glassy land.
		val sheenCx = cx - r * 0.30f
		val sheenCy = cy - r * 0.35f
		for (pts in continents) {
			drawLatLonGlassyPolygon(
				canvas,
				cx,
				cy,
				r,
				pts,
				cosY,
				sinY,
				cosX,
				sinX,
				sheenCx,
				sheenCy,
			)
		}
	}

	private fun drawLatLonGlassyPolygon(
		canvas: Canvas,
		cx: Float,
		cy: Float,
		r: Float,
		pts: FloatArray,
		cosY: Float,
		sinY: Float,
		cosX: Float,
		sinX: Float,
		sheenCx: Float,
		sheenCy: Float,
	) {
		val path = Path()
		var i = 0
		var started = false
		var minZ = 1f
		var sumZ = 0f
		var count = 0
		while (i + 1 < pts.size) {
			val lat = pts[i]
			val lon = pts[i + 1]
			val p = projectOnSphere(lat, lon, cosY, sinY, cosX, sinX)
			val z = p[2]
			minZ = kotlin.math.min(minZ, z)
			sumZ += z
			count += 1
			val x = cx + p[0] * r * 0.92f
			val y = cy + p[1] * r * 0.92f
			if (!started) {
				path.moveTo(x, y)
				started = true
			} else {
				path.lineTo(x, y)
			}
			i += 2
		}
		if (!started || count <= 2) return
		val avgZ = sumZ / count.toFloat()
		val front = ((avgZ + 1f) * 0.5f).coerceIn(0f, 1f)

		// Fill only when the bulk of the polygon is on the front hemisphere.
		if (minZ > -0.12f) {
			val a = (18f + 62f * front).toInt().coerceIn(14, 92)
			landFillPaint.alpha = a
			landFillPaint.shader = LinearGradient(
				sheenCx,
				sheenCy,
				cx + r * 0.55f,
				cy + r * 0.60f,
				intArrayOf(
					Color.argb((a * 0.85f).toInt().coerceIn(0, 255), 255, 255, 255),
					Color.argb((a * 0.55f).toInt().coerceIn(0, 255), 140, 180, 255),
					Color.argb((a * 0.35f).toInt().coerceIn(0, 255), 0, 0, 0),
				),
				floatArrayOf(0f, 0.55f, 1f),
				Shader.TileMode.CLAMP,
			)
			canvas.drawPath(path, landFillPaint)
			landFillPaint.shader = null
			landSheenPaint.alpha = (10f + 48f * front).toInt().coerceIn(10, 70)
			canvas.drawPath(path, landSheenPaint)
			landStrokePaint.alpha = (22f + 85f * front).toInt().coerceIn(18, 130)
			canvas.drawPath(path, landStrokePaint)
		}

		// Always draw an outline so some map is visible even when rotating away.
		drawLatLonPolyline(canvas, cx, cy, r, pts, cosY, sinY, cosX, sinX, mapGlowPaint)
		drawLatLonPolyline(canvas, cx, cy, r, pts, cosY, sinY, cosX, sinX, mapPaint)
	}

	private fun drawLatLonPolyline(
		canvas: Canvas,
		cx: Float,
		cy: Float,
		r: Float,
		pts: FloatArray,
		cosY: Float,
		sinY: Float,
		cosX: Float,
		sinX: Float,
		paint: Paint,
	) {
		// Draw segment-by-segment with depth fade so the map is always perceivable.
		val baseA = paint.alpha.coerceIn(0, 255)
		var i = 0
		var prev: FloatArray? = null
		var prevX = 0f
		var prevY = 0f
		while (i + 1 < pts.size) {
			val lat = pts[i]
			val lon = pts[i + 1]
			val p = projectOnSphere(lat, lon, cosY, sinY, cosX, sinX)
			val x = cx + p[0] * r * 0.92f
			val y = cy + p[1] * r * 0.92f
			val z = p[2]
			if (prev != null) {
				val zAvg = (prev[2] + z) * 0.5f
				// Fade back-facing lines but don't fully drop them.
				val front = ((zAvg + 0.35f) / 1.35f).coerceIn(0f, 1f)
				val a = (baseA * (0.12f + 0.88f * front)).toInt().coerceIn(0, 255)
				val dx = x - prevX
				val dy = y - prevY
				if ((dx * dx + dy * dy) <= (r * r * 0.20f)) {
					paint.alpha = a
					canvas.drawLine(prevX, prevY, x, y, paint)
				}
			}
			prev = p
			prevX = x
			prevY = y
			i += 2
		}
		paint.alpha = baseA
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> return true
			MotionEvent.ACTION_UP -> {
				val x = event.x
				val y = event.y
				val hit = hitRects.entries.firstOrNull { it.value.contains(x, y) }?.key
				if (!hit.isNullOrBlank()) {
					performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
					onItemClick?.invoke(hit)
					return true
				}
				return true
			}
			MotionEvent.ACTION_CANCEL -> return false
		}
		return super.onTouchEvent(event)
	}

	private fun drawLatitude(
		canvas: Canvas,
		cx: Float,
		cy: Float,
		r: Float,
		latDeg: Float,
		steps: Int,
		cosY: Float,
		sinY: Float,
		cosX: Float,
		sinX: Float,
	) {
		val path = Path()
		var started = false
		for (i in 0..steps) {
			val lon = (i.toFloat() / steps.toFloat()) * 360f
			val p = projectOnSphere(latDeg, lon, cosY, sinY, cosX, sinX)
			val z = p[2]
			if (z < -0.02f) {
				started = false
				continue
			}
			val x = cx + p[0] * r * 0.92f
			val y = cy + p[1] * r * 0.92f
			if (!started) {
				path.reset()
				path.moveTo(x, y)
				started = true
			} else {
				path.lineTo(x, y)
			}
		}
		mapPaint.alpha = 75
		canvas.drawPath(path, mapPaint)
	}

	private fun drawLongitude(
		canvas: Canvas,
		cx: Float,
		cy: Float,
		r: Float,
		lonDeg: Float,
		steps: Int,
		cosY: Float,
		sinY: Float,
		cosX: Float,
		sinX: Float,
	) {
		val path = Path()
		var started = false
		for (i in 0..steps) {
			val lat = -80f + (i.toFloat() / steps.toFloat()) * 160f
			val p = projectOnSphere(lat, lonDeg, cosY, sinY, cosX, sinX)
			val z = p[2]
			if (z < -0.02f) {
				started = false
				continue
			}
			val x = cx + p[0] * r * 0.92f
			val y = cy + p[1] * r * 0.92f
			if (!started) {
				path.reset()
				path.moveTo(x, y)
				started = true
			} else {
				path.lineTo(x, y)
			}
		}
		mapPaint.alpha = 65
		canvas.drawPath(path, mapPaint)
	}

	private fun projectOnSphere(
		latDeg: Float,
		lonDeg: Float,
		cosY: Float,
		sinY: Float,
		cosX: Float,
		sinX: Float,
	): FloatArray {
		val lat = (latDeg * (kotlin.math.PI.toFloat() / 180f))
		val lon = (lonDeg * (kotlin.math.PI.toFloat() / 180f))
		val cl = kotlin.math.cos(lat)
		val sl = kotlin.math.sin(lat)
		val cLon = kotlin.math.cos(lon)
		val sLon = kotlin.math.sin(lon)
		var x = cl * cLon
		var y = sl
		var z = cl * sLon

		// Rotate around Y.
		val xr = x * cosY + z * sinY
		val zr = z * cosY - x * sinY
		x = xr
		z = zr

		// Tilt around X.
		val yr = y * cosX - z * sinX
		val zr2 = z * cosX + y * sinX
		y = yr
		z = zr2
		return floatArrayOf(x, y, z)
	}

	private fun dp(v: Float): Float = v * density
	private fun dpToPx(dp: Float): Int = (dp * density).toInt()
}

private class HadithBooksStackView(context: Context) : View(context) {
	data class BookMeta(val title: String, val slug: String, val dark: Boolean)

	private val density = resources.displayMetrics.density
	private val books = listOf(
		BookMeta("Sahih al-Bukhari", slug = "sahih-bukhari", dark = true),
		BookMeta("Sahih Muslim", slug = "sahih-muslim", dark = false),
		// HadithAPI uses these canonical slugs (see hadithapi.com/docs/hadiths).
		BookMeta("Sunan Abu Dawood", slug = "abu-dawood", dark = true),
		BookMeta("Jami' at-Tirmidhi", slug = "al-tirmidhi", dark = false),
	)
	private var onBookClick: ((BookMeta) -> Unit)? = null

	fun getBooks(): List<BookMeta> = books

	fun setOnBookClickListener(listener: ((BookMeta) -> Unit)?) {
		onBookClick = listener
		isClickable = listener != null
		isFocusable = false
	}

	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.0f)
		color = Color.argb(90, 255, 255, 255)
	}
	private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(42, 0, 0, 0)
	}
	private val shadowSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(24, 0, 0, 0)
	}
	private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(22, 255, 255, 255)
	}
	private val facePaintDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(32, 255, 255, 255)
	}
	private val spineFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(44, 255, 255, 255)
	}
	private val pagesFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(46, 255, 255, 255)
	}
	private val pagesLinesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(0.9f)
		color = Color.argb(60, 255, 255, 255)
	}
	private val coverHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.2f)
		color = Color.argb(55, 255, 255, 255)
	}
	private val edgeRoughPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.15f)
		color = Color.argb(44, 255, 255, 255)
		pathEffect = android.graphics.DiscretePathEffect(dp(3.8f), dp(1.15f))
	}
	private val coverInnerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.0f)
		color = Color.argb(42, 255, 255, 255)
	}
	private val coverSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.4f)
		color = Color.argb(26, 255, 255, 255)
		strokeCap = Paint.Cap.ROUND
	}
	private val coverTexturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(0.8f)
		color = Color.argb(14, 255, 255, 255)
	}
	private val coverTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(235, 212, 175, 55)
		textSize = dp(14f)
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
		setShadowLayer(dp(9f), 0f, 0f, Color.argb(110, 255, 255, 255))
	}
	private val coverTextEmbossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(88, 0, 0, 0)
		textSize = dp(14f)
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
	}

	private val coverPath = Path()
	private val rightFacePath = Path()
	private val frontFacePath = Path()
	private val leftFacePath = Path()
	private val tmpPath = Path()
	private val tmpRect = RectF()
	private val coverMatrix = android.graphics.Matrix()
	private val coverBitmapCache: MutableMap<String, Bitmap> = linkedMapOf()
	private val coverBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		isFilterBitmap = true
		isDither = true
	}
	private val coverSrcRect = Rect()

	init {
		isClickable = true
		isFocusable = false
	}

	private fun coverAssetForSlug(bookSlug: String): String? {
		return when (bookSlug.trim().lowercase()) {
			"sahih-bukhari" -> "bukhari cover.png"
			"sahih-muslim" -> "muslim cover.png"
			"abu-dawood" -> "abu dawud cover.png"
			"al-tirmidhi" -> "tirmidhi cover.png"
			else -> null
		}
	}

	private fun coverBitmapForSlug(bookSlug: String): Bitmap? {
		val asset = coverAssetForSlug(bookSlug) ?: return null
		coverBitmapCache[asset]?.let { return it }
		val bmp = runCatching {
			context.assets.open(asset).use { input ->
				BitmapFactory.decodeStream(input)
			}
		}.getOrNull() ?: return null
		coverBitmapCache[asset] = bmp
		return bmp
	}

	override fun performClick(): Boolean {
		super.performClick()
		return true
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (onBookClick == null) return false
		when (event.actionMasked) {
			MotionEvent.ACTION_UP -> {
				val idx = hitTestIndex(event.x, event.y)
				if (idx >= 0) {
					performClick()
					onBookClick?.invoke(books[idx])
					return true
				}
			}
		}
		return true
	}

	private fun hitTestIndex(x: Float, y: Float): Int {
		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		val pad = dp(18f)
		val gap = dp(16f)
		val availW = w - pad * 2f
		val availH = h - pad * 2f
		if (availW <= 1f || availH <= 1f) return -1
		if (x < pad || x > w - pad || y < pad || y > h - pad) return -1
		val cellW = (availW - gap) * 0.5f
		val cellH = (availH - gap) * 0.5f
		val relX = x - pad
		val relY = y - pad
		val col = when {
			relX < cellW -> 0
			relX > cellW + gap -> 1
			else -> return -1
		}
		val row = when {
			relY < cellH -> 0
			relY > cellH + gap -> 1
			else -> return -1
		}
		val idx = row * 2 + col
		return if (idx in 0 until books.size) idx else -1
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		if (w < dp(260f) || h < dp(220f)) return

		// 2x2 layout.
		val pad = dp(18f)
		val gap = dp(16f)
		val availW = (w - pad * 2f).coerceAtLeast(1f)
		val availH = (h - pad * 2f).coerceAtLeast(1f)
		val cellW = (availW - gap) * 0.5f
		val cellH = (availH - gap) * 0.5f

		val coverW = (cellW * 0.82f).coerceAtLeast(dp(120f))
		val coverH = (cellH * 0.54f).coerceAtLeast(dp(72f))
		val skewX = (coverW * 0.18f).coerceIn(dp(22f), dp(64f))
		val skewY = (coverH * 0.10f).coerceIn(dp(6f), dp(16f))
		val thickX = dp(6f)
		val thickY = dp(16f)

		for (i in 0 until 4) {
			val b = books[i]
			val row = i / 2
			val col = i % 2
			val cellX = pad + col * (cellW + gap)
			val cellY = pad + row * (cellH + gap)
			val x = cellX + (cellW - (coverW + skewX + thickX)) * 0.5f
			val y = cellY + (cellH - (coverH + skewY + thickY)) * 0.5f

			drawBook3D(
				canvas = canvas,
				book = b,
				ax = x,
				ay = y,
				coverW = coverW,
				coverH = coverH,
				skewX = if (col == 0) skewX else skewX * 0.88f,
				skewY = if (row == 0) skewY else skewY * 1.1f,
				thickX = thickX,
				thickY = thickY,
				textTiltDeg = if (col == 0) -10f else -6f,
			)
		}
	}

	private fun drawBook3D(
		canvas: Canvas,
		book: BookMeta,
		ax: Float,
		ay: Float,
		coverW: Float,
		coverH: Float,
		skewX: Float,
		skewY: Float,
		thickX: Float,
		thickY: Float,
		textTiltDeg: Float,
	) {
		val bx = ax + coverW
		val by = ay
		val dx = ax + skewX
		val dy = ay + coverH + skewY
		val cx = bx + skewX
		val cy = by + coverH + skewY

		val a2x = ax + thickX
		val a2y = ay + thickY
		val b2x = bx + thickX
		val b2y = by + thickY
		val c2x = cx + thickX
		val c2y = cy + thickY
		val d2x = dx + thickX
		val d2y = dy + thickY

		// Local-to-world transform for cover plane so details and text align with the cover perspective.
		// Local cover coords: (0,0)=(A), (coverW,0)=(B), (0,coverH) maps to (skewX, coverH+skewY).
		val shx = if (coverH > 0.001f) (skewX / coverH) else 0f
		val sy = if (coverH > 0.001f) ((coverH + skewY) / coverH) else 1f
		val shy = if (coverW > 0.001f) (skewY / coverW) else 0f
		coverMatrix.reset()
		coverMatrix.setValues(
			floatArrayOf(
				1f, shx, 0f,
				shy, sy, 0f,
				0f, 0f, 1f,
			),
		)

		// Shadow under the book (soft + core).
		tmpRect.set(ax - dp(10f), d2y + dp(6f), c2x + dp(16f), d2y + dp(30f))
		canvas.drawRoundRect(tmpRect, dp(18f), dp(18f), shadowSoftPaint)
		tmpRect.set(ax - dp(6f), d2y + dp(4f), c2x + dp(12f), d2y + dp(22f))
		canvas.drawRoundRect(tmpRect, dp(14f), dp(14f), shadowPaint)

		// Cover (top face).
		coverPath.reset()
		coverPath.moveTo(ax, ay)
		coverPath.lineTo(bx, by)
		coverPath.lineTo(cx, cy)
		coverPath.lineTo(dx, dy)
		coverPath.close()

		// Side faces.
		rightFacePath.reset()
		rightFacePath.moveTo(bx, by)
		rightFacePath.lineTo(b2x, b2y)
		rightFacePath.lineTo(c2x, c2y)
		rightFacePath.lineTo(cx, cy)
		rightFacePath.close()

		frontFacePath.reset()
		frontFacePath.moveTo(dx, dy)
		frontFacePath.lineTo(cx, cy)
		frontFacePath.lineTo(c2x, c2y)
		frontFacePath.lineTo(d2x, d2y)
		frontFacePath.close()

		leftFacePath.reset()
		leftFacePath.moveTo(ax, ay)
		leftFacePath.lineTo(dx, dy)
		leftFacePath.lineTo(d2x, d2y)
		leftFacePath.lineTo(a2x, a2y)
		leftFacePath.close()

		val baseA = if (book.dark) 92 else 62
		fillPaint.shader = LinearGradient(
			ax,
			ay,
			cx,
			cy,
			intArrayOf(
				Color.argb(baseA + 18, 255, 255, 255),
				Color.argb(baseA - 12, 255, 255, 255),
			),
			floatArrayOf(0f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawPath(coverPath, fillPaint)

		// Faces shading: pages on the right, dark-ish cover thickness on the front, spine/back on the left.
		val face = if (book.dark) facePaintDark else facePaint
		pagesFacePaint.shader = LinearGradient(
			bx,
			by,
			c2x,
			c2y,
			intArrayOf(
				Color.argb(64, 255, 255, 255),
				Color.argb(28, 255, 255, 255),
			),
			floatArrayOf(0f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawPath(rightFacePath, pagesFacePaint)
		canvas.drawPath(frontFacePath, face)
		spineFacePaint.shader = LinearGradient(
			ax,
			ay,
			d2x,
			d2y,
			intArrayOf(
				Color.argb(baseA + 22, 255, 255, 255),
				Color.argb(baseA - 6, 255, 255, 255),
			),
			floatArrayOf(0f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawPath(leftFacePath, spineFacePaint)

		// Page edge lines on the right face.
		run {
			val lines = 7
			for (k in 1 until lines) {
				val t = k.toFloat() / lines.toFloat()
				val x1 = bx + (b2x - bx) * t
				val y1 = by + (b2y - by) * t
				val x2 = cx + (c2x - cx) * t
				val y2 = cy + (c2y - cy) * t
				canvas.drawLine(x1, y1, x2, y2, pagesLinesPaint)
			}
		}

		// Outline + highlight.
		canvas.drawPath(coverPath, edgePaint)
		canvas.drawPath(rightFacePath, edgePaint)
		canvas.drawPath(frontFacePath, edgePaint)
		canvas.drawPath(leftFacePath, edgePaint)
		canvas.drawPath(coverPath, coverHighlightPaint)
		// Slightly roughen edges so it doesn't look too "perfect".
		canvas.drawPath(coverPath, edgeRoughPaint)
		canvas.drawPath(rightFacePath, edgeRoughPaint)
		canvas.drawPath(frontFacePath, edgeRoughPaint)

		// Cover details + title drawn in cover-local coordinates, then transformed onto the cover plane.
		canvas.save()
		canvas.translate(ax, ay)
		canvas.concat(coverMatrix)
		canvas.clipRect(0f, 0f, coverW, coverH)

		val coverBmp = coverBitmapForSlug(book.slug)
		if (coverBmp != null && coverBmp.width > 0 && coverBmp.height > 0) {
			coverSrcRect.set(0, 0, coverBmp.width, coverBmp.height)
			tmpRect.set(0f, 0f, coverW, coverH)
			canvas.drawBitmap(coverBmp, coverSrcRect, tmpRect, coverBitmapPaint)
		}

		// Inner border on cover (local coords).
		run {
			val inset = dp(8f)
			tmpPath.reset()
			tmpPath.moveTo(inset, inset * 0.55f)
			tmpPath.lineTo(coverW - inset, inset * 0.55f)
			tmpPath.lineTo(coverW - inset, coverH - inset * 0.55f)
			tmpPath.lineTo(inset, coverH - inset * 0.55f)
			tmpPath.close()
			canvas.drawPath(tmpPath, coverInnerStrokePaint)
		}

		// Subtle cover texture (diagonal micro lines).
		if (coverBmp == null) {
			run {
				val step = dp(9f)
				var x = -coverW
				while (x < coverW * 2f) {
					canvas.drawLine(x, 0f, x + coverW, coverH, coverTexturePaint)
					x += step
				}
			}
		}

		// Specular sheen streak.
		run {
			val sx1 = coverW * 0.20f
			val sy1 = coverH * 0.18f
			val sx2 = coverW * 0.72f
			val sy2 = coverH * 0.64f
			canvas.drawLine(sx1, sy1, sx2, sy2, coverSheenPaint)
			canvas.drawLine(sx1 + dp(6f), sy1 + dp(4f), sx2 + dp(6f), sy2 + dp(4f), coverSheenPaint)
		}

		// Title on the cover (golden), aligned with cover plane.
		if (coverBmp == null) {
			run {
				val cxm = coverW * 0.5f
				val cym = coverH * 0.52f
				val maxTextW = (coverW * 0.78f).coerceAtLeast(dp(60f))
				coverTextPaint.textSize = dp(14f)
				coverTextEmbossPaint.textSize = coverTextPaint.textSize
				var drawText = book.title
				while (coverTextPaint.measureText(drawText) > maxTextW && drawText.length > 6) {
					drawText = drawText.dropLast(1)
				}
				if (drawText != book.title) drawText = drawText.trimEnd() + "…"
				val fm = coverTextPaint.fontMetrics
				val baseline = cym - (fm.ascent + fm.descent) * 0.5f
				if (kotlin.math.abs(textTiltDeg) > 0.01f) {
					canvas.save()
					canvas.rotate(textTiltDeg, cxm, cym)
					canvas.drawText(drawText, cxm + dp(0.9f), baseline + dp(0.9f), coverTextEmbossPaint)
					canvas.drawText(drawText, cxm - dp(0.6f), baseline - dp(0.6f), coverInnerStrokePaint)
					canvas.drawText(drawText, cxm, baseline, coverTextPaint)
					canvas.restore()
				} else {
					canvas.drawText(drawText, cxm + dp(0.9f), baseline + dp(0.9f), coverTextEmbossPaint)
					canvas.drawText(drawText, cxm - dp(0.6f), baseline - dp(0.6f), coverInnerStrokePaint)
					canvas.drawText(drawText, cxm, baseline, coverTextPaint)
				}
			}
		}

		canvas.restore()
	}

	private fun dp(v: Float): Float = v * density
}

private class HadithSelectionChipView(context: Context) : View(context) {
	private val density = resources.displayMetrics.density
	private var leftText: String = ""
	private var rightText: String = ""
	private var onLeftClick: (() -> Unit)? = null
	private var onRightClick: (() -> Unit)? = null
	private val leftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(235, 212, 175, 55)
		textSize = dp(15f)
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
		setShadowLayer(dp(10f), 0f, 0f, Color.argb(110, 255, 255, 255))
	}
	private val rightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(225, 255, 255, 255)
		textSize = dp(13f)
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
		setShadowLayer(dp(10f), 0f, 0f, Color.argb(110, 140, 180, 255))
	}
	private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.2f)
		color = Color.argb(120, 255, 255, 255)
		strokeCap = Paint.Cap.ROUND
	}
	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(62, 20, 30, 50)
	}
	private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.1f)
		color = Color.argb(75, 255, 255, 255)
	}
	private val padX = dp(18f)
	private val padY = dp(10f)
	private val gap = dp(10f)
	private val minW = dp(140f)
	private val minH = dp(42f)
	private val rect = RectF()
	private var splitX: Float = 0f

	init {
		isClickable = true
		isFocusable = true
	}

	fun setParts(left: String, right: String) {
		leftText = left
		rightText = right
		requestLayout()
		invalidate()
	}

	fun setOnLeftClickListener(listener: (() -> Unit)?) {
		onLeftClick = listener
	}

	fun setOnRightClickListener(listener: (() -> Unit)?) {
		onRightClick = listener
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val lw = leftPaint.measureText(leftText).coerceAtLeast(0f)
		val rw = rightPaint.measureText(rightText).coerceAtLeast(0f)
		val barW = if (rightText.isNotBlank()) dp(2f) else 0f
		val inner = if (rightText.isNotBlank()) (lw + gap + barW + gap + rw) else lw
		val fmL = leftPaint.fontMetrics
		val fmR = rightPaint.fontMetrics
		val th = maxOf((fmL.descent - fmL.ascent), (fmR.descent - fmR.ascent)).coerceAtLeast(0f)
		val desiredW = (inner + padX * 2f).coerceAtLeast(minW).toInt()
		val desiredH = (th + padY * 2f).coerceAtLeast(minH).toInt()
		val mw = resolveSize(desiredW, widthMeasureSpec)
		val mh = resolveSize(desiredH, heightMeasureSpec)
		setMeasuredDimension(mw, mh)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> return true
			MotionEvent.ACTION_UP -> {
				performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
				val left = onLeftClick
				val right = onRightClick
				if (rightText.isBlank() || right == null) {
					left?.invoke()
					return true
				}
				if (event.x < splitX) left?.invoke() else right.invoke()
				return true
			}
			MotionEvent.ACTION_CANCEL -> return false
		}
		return super.onTouchEvent(event)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val w = width.toFloat()
		val h = height.toFloat()
		rect.set(0f, 0f, w, h)
		val r = (h * 0.5f).coerceAtMost(dp(24f))
		canvas.drawRoundRect(rect, r, r, fillPaint)
		canvas.drawRoundRect(rect, r, r, strokePaint)

		val hasRight = rightText.isNotBlank()
		val lw = leftPaint.measureText(leftText).coerceAtLeast(0f)
		val rw = rightPaint.measureText(rightText).coerceAtLeast(0f)
		val barW = if (hasRight) dp(2f) else 0f
		val inner = if (hasRight) (lw + gap + barW + gap + rw) else lw
		val startX = (w - inner) * 0.5f
		val cxL = startX + lw * 0.5f
		val cxR = startX + inner - rw * 0.5f
		val barCx = startX + lw + gap + barW * 0.5f
		splitX = barCx

		val fmL = leftPaint.fontMetrics
		val baselineL = h * 0.5f - (fmL.ascent + fmL.descent) * 0.5f
		val fmR = rightPaint.fontMetrics
		val baselineR = h * 0.5f - (fmR.ascent + fmR.descent) * 0.5f
		canvas.drawText(leftText, cxL, baselineL, leftPaint)
		if (hasRight) {
			val top = h * 0.5f - dp(10f)
			val bottom = h * 0.5f + dp(10f)
			canvas.drawLine(barCx, top, barCx, bottom, barPaint)
			canvas.drawText(rightText, cxR, baselineR, rightPaint)
		}
	}

	private fun dp(v: Float): Float = v * density
}

private class AyahCardsOverlayView(
	context: Context,
	private val onNavigate: (Int) -> Unit,
	private val onTogglePlayback: () -> Unit,
) : View(context) {
	private data class ControlsRects(
		val left: RectF,
		val play: RectF?,
		val right: RectF,
	)

	private val density = resources.displayMetrics.density
	private val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dpToPx(1.0f)
		color = Color.argb(70, 255, 255, 255)
	}
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(235, 212, 175, 55)
		textSize = dpToPx(22f)
	}
	private val buttonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dpToPx(1.0f)
		color = Color.argb(85, 255, 255, 255)
	}
	private val buttonHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dpToPx(1.2f)
		color = Color.argb(70, 255, 255, 255)
	}
	private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
		strokeWidth = dpToPx(2.8f)
		color = Color.argb(230, 212, 175, 55)
	}
	private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(95, 212, 175, 55)
	}
	private val playIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(220, 212, 175, 55)
	}
	private val overlayDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(0, 0, 0, 0)
	}

	private val corner = dpToPx(18f)
	private val cardPadX = dpToPx(18f)
	private val cardPadY = dpToPx(14f)
	private val outerMargin = dpToPx(16f)
	private val minCardH = dpToPx(86f)
	private val controlsButtonSize = dpToPx(46f)
	private val controlsGap = dpToPx(12f)
	private val hitPad = dpToPx(8f)
	private val controlsTimeoutMs = 2400L
	private val fadeOutMs = 260f

	private var lastCardRect: RectF? = null
	private var leftRectHit: RectF? = null
	private var playRectHit: RectF? = null
	private var rightRectHit: RectF? = null
	private var pressedKind: Int = 0 // -1 prev, 0 none, 1 next, 2 play
	private var pressedPointerId: Int = -1
	@Volatile private var controlsVisibleUntilMs: Long = 0L

	@Volatile private var surahNumber: Int = 1
	@Volatile private var ayah1: String = ""
	@Volatile private var ayah2: String = ""
	@Volatile private var loading: Boolean = true
	@Volatile private var canPrev: Boolean = false
	@Volatile private var canNext: Boolean = true
	@Volatile private var playing: Boolean = false
	@Volatile private var playButtonVisible: Boolean = true

	fun setPlayButtonVisible(visible: Boolean) {
		playButtonVisible = visible
		if (!visible) {
			playRectHit = null
			if (pressedKind == 2) pressedKind = 0
		}
		postInvalidateOnAnimation()
	}

	fun setPlaying(playing: Boolean) {
		this.playing = playing
		postInvalidateOnAnimation()
	}

	fun setNavAvailability(canPrev: Boolean, canNext: Boolean) {
		this.canPrev = canPrev
		this.canNext = canNext
		postInvalidateOnAnimation()
	}

	fun setLoading(surahNumber: Int) {
		this.surahNumber = surahNumber
		loading = true
		ayah1 = ""
		ayah2 = ""
		canPrev = false
		canNext = false
		lastCardRect = null
		leftRectHit = null
		playRectHit = null
		rightRectHit = null
		pressedKind = 0
		postInvalidateOnAnimation()
	}

	fun setAyahs(surahNumber: Int, ayah1: String, ayah2: String) {
		this.surahNumber = surahNumber
		this.ayah1 = ayah1
		this.ayah2 = ayah2
		loading = false
		postInvalidateOnAnimation()
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val card = lastCardRect ?: return false
		if (event.actionMasked == MotionEvent.ACTION_DOWN && !card.contains(event.x, event.y)) return false

		val now = SystemClock.uptimeMillis()
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				showControls(now)
				computeControlsRects(card)
				pressedPointerId = event.getPointerId(0)
				pressedKind = hitTestKind(event.x, event.y)
				postInvalidateOnAnimation()
				return true
			}
			MotionEvent.ACTION_MOVE -> {
				showControls(now)
				computeControlsRects(card)
				val idx = event.findPointerIndex(pressedPointerId)
				if (idx < 0) return true
				val kind = hitTestKind(event.getX(idx), event.getY(idx))
				if (kind != pressedKind) {
					pressedKind = kind
					postInvalidateOnAnimation()
				}
				return true
			}
			MotionEvent.ACTION_UP -> {
				showControls(now)
				val kind = pressedKind
				pressedKind = 0
				pressedPointerId = -1
				postInvalidateOnAnimation()
				when (kind) {
					-1 -> if (canPrev && !loading) onNavigate(-1)
					1 -> if (canNext && !loading) onNavigate(1)
					2 -> if (!loading) onTogglePlayback()
				}
				return true
			}
			MotionEvent.ACTION_CANCEL -> {
				pressedKind = 0
				pressedPointerId = -1
				postInvalidateOnAnimation()
				return false
			}
		}
		return super.onTouchEvent(event)
	}

	private fun hitTestKind(x: Float, y: Float): Int {
		val play = playRectHit
		if (play != null && play.contains(x, y)) return 2
		val left = leftRectHit
		if (left != null && left.contains(x, y)) return -1
		val right = rightRectHit
		if (right != null && right.contains(x, y)) return 1
		return 0
	}

	private fun showControls(nowMs: Long) {
		controlsVisibleUntilMs = nowMs + controlsTimeoutMs
		postInvalidateOnAnimation()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		if (w <= 1f || h <= 1f) return

		val cardW = (w - outerMargin * 2f).coerceAtLeast(1f)
		val maxTextW = (cardW - cardPadX * 2f).coerceAtLeast(1f).toInt()
		val topY = outerMargin

		val text = if (loading) "Loading…" else ayah1
		val layout = buildArabicLayout(text, maxTextW)
		val cardH = (cardPadY * 2f + layout.height).coerceAtLeast(minCardH)
		val rect = RectF(outerMargin, topY, outerMargin + cardW, topY + cardH)
		lastCardRect = rect
		drawGlassyCard(canvas, rect)
		drawCardText(canvas, rect, layout)
		drawControlsOverlay(canvas, rect)
	}

	private fun drawControlsOverlay(canvas: Canvas, cardRect: RectF) {
		val now = SystemClock.uptimeMillis()
		val remaining = controlsVisibleUntilMs - now
		if (remaining <= 0L) {
			leftRectHit = null
			playRectHit = null
			rightRectHit = null
			return
		}

		val a = (if (remaining < fadeOutMs) remaining / fadeOutMs else 1f).coerceIn(0f, 1f)
		overlayDimPaint.color = Color.argb((55f * a).toInt().coerceIn(0, 55), 0, 0, 0)
		canvas.drawRoundRect(cardRect, corner, corner, overlayDimPaint)

		val rects = computeControlsRects(cardRect)
		drawNavButton(canvas, rects.left, dir = -1, enabled = canPrev && !loading, pressed = pressedKind == -1, alpha = a)
		rects.play?.let { playRect ->
			drawPlayButton(canvas, playRect, enabled = !loading, pressed = pressedKind == 2, alpha = a)
		}
		drawNavButton(canvas, rects.right, dir = 1, enabled = canNext && !loading, pressed = pressedKind == 1, alpha = a)

		postInvalidateOnAnimation()
	}

	private fun computeControlsRects(cardRect: RectF): ControlsRects {
		val button = controlsButtonSize
		val gap = controlsGap
		val showPlay = playButtonVisible
		val totalW = if (showPlay) (button * 3f + gap * 2f) else (button * 2f + gap)
		val cx = cardRect.centerX()
		val cy = cardRect.centerY()
		val left = cx - totalW * 0.5f
		val top = cy - button * 0.5f
		val rLeft = RectF(left, top, left + button, top + button)
		if (showPlay) {
			val rPlay = RectF(rLeft.right + gap, top, rLeft.right + gap + button, top + button)
			val rRight = RectF(rPlay.right + gap, top, rPlay.right + gap + button, top + button)
			leftRectHit = RectF(rLeft.left - hitPad, rLeft.top - hitPad, rLeft.right + hitPad, rLeft.bottom + hitPad)
			playRectHit = RectF(rPlay.left - hitPad, rPlay.top - hitPad, rPlay.right + hitPad, rPlay.bottom + hitPad)
			rightRectHit = RectF(rRight.left - hitPad, rRight.top - hitPad, rRight.right + hitPad, rRight.bottom + hitPad)
			return ControlsRects(rLeft, rPlay, rRight)
		}
		val rRight = RectF(rLeft.right + gap, top, rLeft.right + gap + button, top + button)
		leftRectHit = RectF(rLeft.left - hitPad, rLeft.top - hitPad, rLeft.right + hitPad, rLeft.bottom + hitPad)
		playRectHit = null
		rightRectHit = RectF(rRight.left - hitPad, rRight.top - hitPad, rRight.right + hitPad, rRight.bottom + hitPad)
		return ControlsRects(rLeft, null, rRight)
	}

	private fun drawCardText(canvas: Canvas, rect: RectF, layout: StaticLayout) {
		canvas.save()
		val x = rect.left + (rect.width() - layout.width.toFloat()) * 0.5f
		val innerTop = rect.top + cardPadY
		val innerH = (rect.height() - 2f * cardPadY).coerceAtLeast(0f)
		val y = innerTop + ((innerH - layout.height.toFloat()) * 0.5f).coerceAtLeast(0f)
		canvas.translate(x, y)
		layout.draw(canvas)
		canvas.restore()
	}

	private fun drawNavButton(canvas: Canvas, rect: RectF, dir: Int, enabled: Boolean, pressed: Boolean, alpha: Float) {
		drawGlassyCircleButton(canvas, rect, enabled = enabled, pressed = pressed, alpha = alpha)
		drawChevron(canvas, rect, dir = dir, enabled = enabled, pressed = pressed, alpha = alpha)
	}

	private fun drawPlayButton(canvas: Canvas, rect: RectF, enabled: Boolean, pressed: Boolean, alpha: Float) {
		drawGlassyCircleButton(canvas, rect, enabled = enabled, pressed = pressed, alpha = alpha)
		drawPlayPauseIcon(canvas, rect, enabled = enabled, pressed = pressed, alpha = alpha)
	}

	private fun drawGlassyCircleButton(canvas: Canvas, rect: RectF, enabled: Boolean, pressed: Boolean, alpha: Float) {
		val cx = rect.centerX()
		val cy = rect.centerY()
		val radius = (minOf(rect.width(), rect.height()) * 0.5f).coerceAtLeast(1f)
		val r = (radius - dpToPx(2.0f)).coerceAtLeast(1f)

		val baseA = ((if (enabled) 255 else 105) * alpha).toInt().coerceIn(0, 255)
		val pressBoost = if (pressed) 1 else 0

		buttonFillPaint.shader = RadialGradient(
			cx,
			cy - r * 0.25f,
			r,
			intArrayOf(
				Color.argb((105 + 15 * pressBoost).coerceAtMost(130) * baseA / 255, 255, 255, 255),
				Color.argb((65 + 10 * pressBoost).coerceAtMost(95) * baseA / 255, 255, 255, 255),
				Color.argb((35 + 10 * pressBoost).coerceAtMost(60) * baseA / 255, 255, 255, 255),
			),
			floatArrayOf(0f, 0.62f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(cx, cy, r, buttonFillPaint)

		buttonStrokePaint.alpha = (90 * baseA / 255).coerceIn(0, 255)
		canvas.drawCircle(cx, cy, r, buttonStrokePaint)

		buttonHighlightPaint.alpha = (70 * baseA / 255).coerceIn(0, 255)
		val highlightR = r * 0.72f
		val highlightRect = RectF(cx - highlightR, cy - highlightR, cx + highlightR, cy + highlightR)
		canvas.drawArc(highlightRect, 210f, 70f, false, buttonHighlightPaint)
	}

	private fun drawChevron(canvas: Canvas, rect: RectF, dir: Int, enabled: Boolean, pressed: Boolean, alpha: Float) {
		val cx = rect.centerX()
		val cy = rect.centerY()
		val r = (minOf(rect.width(), rect.height()) * 0.5f - dpToPx(8f)).coerceAtLeast(1f)
		val a = ((if (enabled) 255 else 130) * alpha).toInt().coerceIn(0, 255)
		val inset = r * 0.10f
		val iconW = r * 0.95f
		val iconH = r * 0.76f
		val x0 = cx - iconW * 0.5f
		val x1 = cx + iconW * 0.5f
		val y0 = cy - iconH * 0.5f
		val y1 = cy + iconH * 0.5f
		val tipX = if (dir < 0) x0 else x1
		val tailX = if (dir < 0) x1 else x0

		val chevron = Path().apply {
			moveTo(tailX - dir * inset, y0)
			lineTo(tipX, cy)
			lineTo(tailX - dir * inset, y1)
			lineTo(tailX - dir * inset * 0.25f, y1)
			lineTo(tipX + dir * inset, cy)
			lineTo(tailX - dir * inset * 0.25f, y0)
			close()
		}

		iconFillPaint.alpha = ((if (pressed) 125 else 95) * a / 255).coerceIn(0, 255)
		canvas.drawPath(chevron, iconFillPaint)
		iconStrokePaint.alpha = ((if (pressed) 255 else 235) * a / 255).coerceIn(0, 255)
		canvas.drawPath(chevron, iconStrokePaint)
	}

	private fun drawPlayPauseIcon(canvas: Canvas, rect: RectF, enabled: Boolean, pressed: Boolean, alpha: Float) {
		val a = ((if (enabled) 255 else 140) * alpha).toInt().coerceIn(0, 255)
		playIconPaint.alpha = ((if (pressed) 245 else 220) * a / 255).coerceIn(0, 255)
		val cx = rect.centerX()
		val cy = rect.centerY()
		val r = (minOf(rect.width(), rect.height()) * 0.5f - dpToPx(10f)).coerceAtLeast(1f)

		if (playing) {
			val barW = dpToPx(5.0f)
			val gap = dpToPx(5.5f)
			val barH = r * 1.15f
			val top = cy - barH * 0.5f
			val bottom = cy + barH * 0.5f
			val leftBar = RectF(cx - gap * 0.5f - barW, top, cx - gap * 0.5f, bottom)
			val rightBar = RectF(cx + gap * 0.5f, top, cx + gap * 0.5f + barW, bottom)
			canvas.drawRoundRect(leftBar, barW * 0.5f, barW * 0.5f, playIconPaint)
			canvas.drawRoundRect(rightBar, barW * 0.5f, barW * 0.5f, playIconPaint)
		} else {
			val w = r * 1.20f
			val h = r * 1.05f
			val left = cx - w * 0.35f
			val right = cx + w * 0.62f
			val top = cy - h * 0.5f
			val bottom = cy + h * 0.5f
			val playPath = Path().apply {
				moveTo(left, top)
				lineTo(right, cy)
				lineTo(left, bottom)
				close()
			}
			canvas.drawPath(playPath, playIconPaint)
		}
	}

	private fun drawGlassyCard(canvas: Canvas, rect: RectF) {
		cardFillPaint.shader = LinearGradient(
			rect.left,
			rect.top,
			rect.left,
			rect.bottom,
			intArrayOf(
				Color.argb(95, 255, 255, 255),
				Color.argb(55, 255, 255, 255),
				Color.argb(35, 255, 255, 255),
			),
			floatArrayOf(0f, 0.55f, 1f),
			Shader.TileMode.CLAMP,
		)
		canvas.drawRoundRect(rect, corner, corner, cardFillPaint)
		canvas.drawRoundRect(rect, corner, corner, cardStrokePaint)
	}

	private fun buildArabicLayout(text: String, maxWidth: Int): StaticLayout {
		return StaticLayout.Builder
			.obtain(text, 0, text.length, textPaint, maxWidth)
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.setIncludePad(false)
			.setLineSpacing(0f, 1.12f)
			.setTextDirection(TextDirectionHeuristics.RTL)
			.build()
	}

	private fun dpToPx(dp: Float): Float = dp * density
}

private class OpenBookView(context: Context) : View(context) {
	@Volatile private var turnProgress = 0f // 0..1
	@Volatile private var turnDir = 1f // +1 or -1
	@Volatile private var surahNumber: Int = 1
	@Volatile private var surahNameEnglish: String = ""
	@Volatile private var surahNameArabic: String? = null
	@Volatile private var surahMeaning: String? = null
	@Volatile private var surahVerses: Int? = null
	@Volatile private var surahRevelationType: String? = null
	@Volatile private var surahJuzRange: String? = null
	@Volatile private var activeAyahNumber: Int = 1
	@Volatile private var playModeEnabled: Boolean = false

	fun updateAyahIndicator(ayahNumber: Int, playModeEnabled: Boolean) {
		activeAyahNumber = ayahNumber.coerceAtLeast(1)
		this.playModeEnabled = playModeEnabled
		postInvalidateOnAnimation()
	}

	fun updateTurn(progress: Float, direction: Float) {
		turnProgress = progress.coerceIn(0f, 1f)
		turnDir = if (direction >= 0f) 1f else -1f
		postInvalidateOnAnimation()
	}

	fun updateSurahInfo(
		surahNumber: Int,
		nameEnglish: String,
		nameArabic: String?,
		meaningEnglish: String?,
		versesCount: Int?,
		revelationType: String?,
		juzRange: String?,
	) {
		this.surahNumber = surahNumber
		surahNameEnglish = nameEnglish
		surahNameArabic = nameArabic
		surahMeaning = meaningEnglish
		surahVerses = versesCount
		surahRevelationType = revelationType
		surahJuzRange = juzRange
		// No invalidate here; updateTurn() is already driving per-frame invalidation while scrolling.
	}

	private val pageFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.FILL
		color = android.graphics.Color.argb(240, 255, 255, 255)
	}
	private val turningFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.FILL
		color = android.graphics.Color.argb(185, 255, 255, 255)
	}
	private val turningStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.STROKE
		strokeWidth = dpToPx(1.0f)
		color = android.graphics.Color.argb(120, 25, 25, 25)
	}
	private val turningBackFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.FILL
		// Slightly darker to imply the back of the page.
		color = android.graphics.Color.argb(165, 245, 245, 245)
	}
	private val foldShadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.FILL
	}
	private val foldHighlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.STROKE
		strokeWidth = dpToPx(1.4f)
		color = android.graphics.Color.argb(90, 40, 40, 40)
	}
	private val pageStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.STROKE
		strokeWidth = dpToPx(2.1f)
		color = android.graphics.Color.argb(235, 25, 25, 25)
	}
	private val spinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.STROKE
		strokeWidth = dpToPx(3.0f)
		color = android.graphics.Color.argb(235, 25, 25, 25)
	}
	private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.FILL
		color = android.graphics.Color.argb(40, 0, 0, 0)
	}
	private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.argb(235, 20, 20, 20)
		textAlign = android.graphics.Paint.Align.LEFT
		textSize = dpToPx(13f)
	}
	private val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.argb(245, 10, 10, 10)
		textAlign = android.graphics.Paint.Align.LEFT
		textSize = dpToPx(17f)
		isFakeBoldText = true
	}
	private val ayahBadgeFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.FILL
	}
	private val ayahBadgeOuterStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.STROKE
		strokeWidth = dpToPx(2.2f)
	}
	private val ayahBadgeStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		style = android.graphics.Paint.Style.STROKE
		strokeWidth = dpToPx(1.1f)
		color = android.graphics.Color.argb(150, 255, 255, 255)
	}
	private val ayahBadgeText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.argb(235, 255, 255, 255)
		textAlign = android.graphics.Paint.Align.CENTER
		textSize = dpToPx(13.5f)
		isFakeBoldText = true
	}

	private val leftPage = android.graphics.Path()
	private val rightPage = android.graphics.Path()
	private val turningPage = android.graphics.Path()
	private val turningBack = android.graphics.Path()
	private val foldPath = android.graphics.Path()
	private val spineShadow = android.graphics.RectF()

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val viewW = width.toFloat().coerceAtLeast(1f)
		val viewH = height.toFloat().coerceAtLeast(1f)

		// Center the drawn book inside its view so it appears centered in the right half.
			val desiredRatio = 1.22f // bookW / bookH (smaller => taller book => bigger pages)
		val maxBookW = viewW
			val maxBookH = viewH * 0.99f
		val bookW = kotlin.math.min(maxBookW, maxBookH * desiredRatio).coerceAtLeast(1f)
		val bookH = (bookW / desiredRatio).coerceAtLeast(1f)
		val bookLeft = (viewW - bookW) * 0.5f
		val bookTop = (viewH - bookH) * 0.5f

		canvas.save()
		canvas.translate(bookLeft, bookTop)
		val w = bookW
		val h = bookH

			// Use more of the available surface for the pages.
			val pad = w * 0.05f
			val top = h * 0.085f
			val bottom = h * 0.915f
		val spineX = w * 0.52f
		val tilt = w * 0.045f
		val bottomCurve = h * 0.07f

		leftPage.reset()
		leftPage.moveTo(pad, top)
		leftPage.lineTo(spineX, top + tilt)
		leftPage.lineTo(spineX, bottom - tilt)
		leftPage.quadTo((pad + spineX) * 0.5f, bottom + bottomCurve, pad, bottom)
		leftPage.close()

		rightPage.reset()
		rightPage.moveTo(spineX, top + tilt)
		rightPage.lineTo(w - pad, top)
		rightPage.lineTo(w - pad, bottom)
		rightPage.quadTo((spineX + (w - pad)) * 0.5f, bottom + bottomCurve, spineX, bottom - tilt)
		rightPage.close()

		// Subtle shadow near the spine to read as a 3D open book.
		val spineW = (w * 0.020f).coerceAtLeast(dpToPx(5f))
		spineShadow.set(spineX - spineW, top, spineX + spineW, bottom)
		canvas.drawRect(spineShadow, shadowPaint)

		canvas.drawPath(leftPage, pageFill)
		canvas.drawPath(rightPage, pageFill)

		// Basic info for the centered surah (drawn inside each page, inclined with the page).
		drawSurahInfo(canvas, w, pad, top, bottom, spineX)

		// Page turn while scrolling: a curl-like flap + fold shadows.
		val t = turnProgress
		if (t in 0.02f..0.98f) {
			// Ease to avoid a robotic linear motion.
			val te = (0.5f - 0.5f * cos(PI.toFloat() * t)).coerceIn(0f, 1f)
			val curlAmt = sin(PI.toFloat() * te).coerceIn(0f, 1f)
			val curl = curlAmt * w * 0.06f

			val foldStrokeA = (60f + 80f * curlAmt).toInt().coerceIn(0, 140)
			foldHighlightPaint.alpha = foldStrokeA

			if (turnDir >= 0f) {
				// Turning a right-hand page toward the left (fold moves from outer edge to the spine).
				val hingeX = spineX
				val outerX = w - pad
				val foldX = (outerX - (outerX - hingeX) * te).coerceIn(hingeX, outerX)
				val topFoldX = (foldX - curl * 0.45f).coerceIn(hingeX, outerX)
				val botFoldX = (foldX + curl * 0.45f).coerceIn(hingeX, outerX)
				val midY = (top + bottom) * 0.5f
				// Bulge outward (toward outerX) for a more natural curl.
				val ctrlX = (foldX + curl * 1.20f).coerceIn(hingeX, outerX)

				turningBack.reset()
				turningBack.moveTo(topFoldX, top)
				turningBack.lineTo(outerX, top)
				turningBack.lineTo(outerX, bottom)
				turningBack.lineTo(botFoldX, bottom)
				turningBack.quadTo(ctrlX, midY, topFoldX, top)
				turningBack.close()

				foldPath.reset()
				foldPath.moveTo(topFoldX, top)
				foldPath.quadTo(ctrlX, midY, botFoldX, bottom)

				// Shadow across the lifted flap (darker near fold, fades to transparent).
				foldShadowPaint.shader = android.graphics.LinearGradient(
					topFoldX,
					0f,
					outerX,
					0f,
					android.graphics.Color.argb((70f * curlAmt).toInt().coerceIn(0, 90), 0, 0, 0),
					android.graphics.Color.argb(0, 0, 0, 0),
					android.graphics.Shader.TileMode.CLAMP,
				)

				canvas.drawPath(turningBack, turningBackFill)
				canvas.drawPath(turningBack, foldShadowPaint)

				// A subtle bright fold curve.
				canvas.drawPath(foldPath, foldHighlightPaint)
			} else {
				// Turning a left-hand page toward the right.
				val hingeX = spineX
				val outerX = pad
				val foldX = (outerX + (hingeX - outerX) * te).coerceIn(outerX, hingeX)
				val topFoldX = (foldX + curl * 0.45f).coerceIn(outerX, hingeX)
				val botFoldX = (foldX - curl * 0.45f).coerceIn(outerX, hingeX)
				val midY = (top + bottom) * 0.5f
				// Bulge outward (toward outerX) for a more natural curl.
				val ctrlX = (foldX - curl * 1.20f).coerceIn(outerX, hingeX)

				turningBack.reset()
				turningBack.moveTo(outerX, top)
				turningBack.lineTo(topFoldX, top)
				turningBack.quadTo(ctrlX, midY, botFoldX, bottom)
				turningBack.lineTo(outerX, bottom)
				turningBack.close()

				foldPath.reset()
				foldPath.moveTo(topFoldX, top)
				foldPath.quadTo(ctrlX, midY, botFoldX, bottom)

				foldShadowPaint.shader = android.graphics.LinearGradient(
					outerX,
					0f,
					topFoldX,
					0f,
					android.graphics.Color.argb(0, 0, 0, 0),
					android.graphics.Color.argb((70f * curlAmt).toInt().coerceIn(0, 90), 0, 0, 0),
					android.graphics.Shader.TileMode.CLAMP,
				)

				canvas.drawPath(turningBack, turningBackFill)
				canvas.drawPath(turningBack, foldShadowPaint)
				canvas.drawPath(foldPath, foldHighlightPaint)
			}
		}

		canvas.drawPath(leftPage, pageStroke)
		canvas.drawPath(rightPage, pageStroke)
		canvas.drawLine(spineX, top, spineX, bottom, spinePaint)

		// Page stack lines on the left edge (sketch-like thickness).
		val edgeStep = dpToPx(2.2f)
		val edgeInset = dpToPx(3.0f)
		for (i in 1..4) {
			val x = (pad - i * edgeStep).coerceAtLeast(0f)
			canvas.drawLine(x, top + edgeInset + i * 0.6f * edgeStep, x, bottom - edgeInset - i * 0.6f * edgeStep, pageStroke)
		}

		canvas.restore()
	}

	private fun drawAyahIndicator(
		canvas: Canvas,
		w: Float,
		pad: Float,
		top: Float,
		bottom: Float,
		spineX: Float,
	) {
		val ayah = activeAyahNumber.coerceAtLeast(1)
		val label = ayah.toString()
		val leftCenterX = (pad + spineX) * 0.5f
		val yCenter = bottom - dpToPx(28f)

		// Keep the fill neutral and only switch the OUTER ring color.
		ayahBadgeFill.color = android.graphics.Color.argb(95, 0, 0, 0)
		val ringColor = if (playModeEnabled) {
			android.graphics.Color.argb(235, 46, 204, 113) // green
		} else {
			android.graphics.Color.argb(235, 212, 175, 55) // golden
		}
		ayahBadgeOuterStroke.color = ringColor

		val textW = ayahBadgeText.measureText(label)
		val r = maxOf(dpToPx(16f), textW * 0.5f + dpToPx(10f))
		val circleRect = android.graphics.RectF(leftCenterX - r, yCenter - r, leftCenterX + r, yCenter + r)
		val fm = ayahBadgeText.fontMetrics
		val baseline = yCenter - (fm.ascent + fm.descent) * 0.5f

		canvas.save()
		canvas.clipPath(leftPage)
		canvas.drawOval(circleRect, ayahBadgeFill)
		canvas.drawOval(circleRect, ayahBadgeOuterStroke)
		canvas.drawOval(circleRect, ayahBadgeStroke)
		canvas.drawText(label, leftCenterX, baseline, ayahBadgeText)
		canvas.restore()
	}

	private fun drawSurahInfo(
		canvas: Canvas,
		w: Float,
		pad: Float,
		top: Float,
		bottom: Float,
		spineX: Float,
	) {
		val infoPadX = w * 0.08f
		// Keep a clear padding at the top of the book pages.
		// drawText() uses baseline Y, so account for ascent to prevent glyphs from crossing the top edge.
		val topInset = dpToPx(16f)
		val infoTop = top + topInset - titlePaint.fontMetrics.ascent
		val titleLineH = titlePaint.textSize * 1.15f
		val bodyLineH = textPaint.textSize * 1.15f
		val pageTextBottom = bottom - (bottom - top) * 0.06f

		val num = surahNumber
		val enName = surahNameEnglish
		val arName = surahNameArabic
		val meaningName = surahMeaning
		val versesCount = surahVerses
		val revelationType = surahRevelationType
		val juzRange = surahJuzRange
		val currentAyah = activeAyahNumber.coerceAtLeast(1)
		val pageMidY = (top + bottom) * 0.5f
		// Match the page edge angle so text runs parallel to the page.
		val pageTilt = w * 0.045f
		val leftEdgeAngleDeg = (kotlin.math.atan2(pageTilt, (spineX - pad).coerceAtLeast(1f)) * (180f / kotlin.math.PI.toFloat()))

		// Center text within each page.
		titlePaint.textAlign = android.graphics.Paint.Align.CENTER
		textPaint.textAlign = android.graphics.Paint.Align.CENTER
		val savedTitleSkew = titlePaint.textSkewX
		val savedTextSkew = textPaint.textSkewX
		titlePaint.textSkewX = 0f
		textPaint.textSkewX = 0f

		fun drawLeftText(
			clipPath: android.graphics.Path,
			centerX: Float,
			skewX: Float,
			maxW: Float,
		) {
			canvas.save()
			canvas.clipPath(clipPath)
			// Skew around the page center so the text aligns with page orientation.
			canvas.translate(centerX, infoTop)
			canvas.skew(skewX, 0f)
			canvas.translate(-centerX, -infoTop)
			// Compensate for skew drift so text stays horizontally centered.
			canvas.translate(-skewX * (pageMidY - infoTop), 0f)
			// Rotate so baselines are parallel to the page edge.
			canvas.rotate(leftEdgeAngleDeg, centerX, pageMidY)

			val savedTitleSize = titlePaint.textSize
			val savedTextAlign = textPaint.textAlign
			val savedTextSize = textPaint.textSize
			val savedTitleBold = titlePaint.isFakeBoldText
			val savedTextBold = textPaint.isFakeBoldText
			val savedTextTypeface = textPaint.typeface
			val savedStrokeAlpha = turningStroke.alpha
			val savedStrokeWidth = turningStroke.strokeWidth

			// 1) Surah number
			titlePaint.textSize = dpToPx(18f)
			titlePaint.isFakeBoldText = false
			canvas.drawText(num.toString(), centerX, infoTop, titlePaint)
			val numFm = titlePaint.fontMetrics
			// Place the line directly under the number glyphs (baseline + descent).
			var y = infoTop + numFm.descent + dpToPx(2f)

			// 2) Horizontal line across the page
			val linePad = maxW * 0.08f
			turningStroke.alpha = 120
			turningStroke.strokeWidth = dpToPx(1.0f)
			canvas.drawLine(centerX - maxW * 0.5f + linePad, y, centerX + maxW * 0.5f - linePad, y, turningStroke)
			// Move down by a fixed gap plus the ascent of the next text line so it never intersects the separator line.
			y += dpToPx(6f)

			// 3) English name in bold
			titlePaint.textSize = dpToPx(15f)
			titlePaint.isFakeBoldText = true
			y += -titlePaint.fontMetrics.ascent
			y = drawWrappedText(
				canvas = canvas,
				text = enName,
				x = centerX,
				y = y,
				maxWidth = maxW,
				maxY = pageTextBottom,
				paint = titlePaint,
				lineH = titlePaint.textSize * 1.15f,
				maxLines = 2,
			)

			// 4) Arabic name
			textPaint.textSize = dpToPx(12.5f)
			textPaint.isFakeBoldText = false
			arName?.let {
				y = drawWrappedText(
					canvas = canvas,
					text = it,
					x = centerX,
					y = y + dpToPx(1f),
					maxWidth = maxW,
					maxY = pageTextBottom,
					paint = textPaint,
					lineH = textPaint.textSize * 1.15f,
					maxLines = 2,
				)
			}
			y += dpToPx(3f)

			// 5) Total verses number at bottom + current ayah inside a circle (centered)
			val totalStr = (versesCount?.toString() ?: "-")
			textPaint.textAlign = android.graphics.Paint.Align.LEFT
			textPaint.textSize = dpToPx(11.5f)
			textPaint.isFakeBoldText = true
			textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
			val gap = dpToPx(8f)
			val circleR = dpToPx(11.5f)
			val textW = textPaint.measureText(totalStr)
			val groupW = textW + gap + circleR * 2f
			val startX = centerX - groupW * 0.5f
			val baseline = bottom - dpToPx(14f)
			canvas.drawText(totalStr, startX, baseline, textPaint)
			val cy = baseline + (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) * 0.5f
			val cx = startX + textW + gap + circleR

			ayahBadgeFill.color = android.graphics.Color.argb(95, 0, 0, 0)
			val ringColor = if (playModeEnabled) {
				android.graphics.Color.argb(235, 46, 204, 113)
			} else {
				android.graphics.Color.argb(235, 212, 175, 55)
			}
			ayahBadgeOuterStroke.color = ringColor
			val circleRect = android.graphics.RectF(cx - circleR, cy - circleR, cx + circleR, cy + circleR)
			val fm = ayahBadgeText.fontMetrics
			val badgeBaseline = cy - (fm.ascent + fm.descent) * 0.5f
			canvas.drawOval(circleRect, ayahBadgeFill)
			canvas.drawOval(circleRect, ayahBadgeOuterStroke)
			canvas.drawOval(circleRect, ayahBadgeStroke)
			canvas.drawText(currentAyah.toString(), cx, badgeBaseline, ayahBadgeText)

			// Restore paints
			titlePaint.textSize = savedTitleSize
			textPaint.textAlign = savedTextAlign
			textPaint.textSize = savedTextSize
			titlePaint.isFakeBoldText = savedTitleBold
			textPaint.isFakeBoldText = savedTextBold
			textPaint.typeface = savedTextTypeface
			turningStroke.alpha = savedStrokeAlpha
			turningStroke.strokeWidth = savedStrokeWidth
			canvas.restore()
		}

		fun drawRightInfo(
			clipPath: android.graphics.Path,
			centerX: Float,
			skewX: Float,
			maxW: Float,
		) {
			canvas.save()
			val savedTextTypeface = textPaint.typeface
			val savedTextSize = textPaint.textSize
			textPaint.typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.ITALIC)
			textPaint.textSize = dpToPx(16.5f)
			val rightBodyLineH = textPaint.textSize * 1.18f
			canvas.translate(centerX, infoTop)
			canvas.skew(skewX, 0f)
			canvas.translate(-centerX, -infoTop)
			// Compensate for skew drift so text stays horizontally centered.
			canvas.translate(-skewX * (pageMidY - infoTop), 0f)

			// Right page: draw horizontally (no rotation) for readability.
			val pivotX = centerX
			val pivotY = pageMidY
			canvas.clipPath(clipPath)
			val maxWidthHorizontal = maxW.coerceAtLeast(1f)

			fun countWrappedLines(text: String, maxLines: Int): Int {
				if (text.isBlank()) return 0
				var lines = 0
				val paragraphs = text.split('\n')
				for (p in paragraphs) {
					var start = 0
					val s = p.trim()
					if (s.isEmpty()) continue
					while (start < s.length && lines < maxLines) {
						var count = textPaint.breakText(s, start, s.length, true, maxWidthHorizontal, null)
						if (count <= 0) break
						var end = (start + count).coerceAtMost(s.length)
						if (end < s.length) {
							val lastSpace = s.lastIndexOf(' ', end - 1)
							if (lastSpace > start + 3) end = lastSpace
						}
						lines++
						start = end
						while (start < s.length && s[start] == ' ') start++
					}
					if (lines >= maxLines) break
				}
				return lines
			}

			val meaningText = meaningName
			val revText = revelationType
			val juzText = juzRange?.let { "Juz range: $it" }

			val totalLines =
				(meaningText?.let { countWrappedLines(it, maxLines = 6) } ?: 0) +
				(revText?.let { countWrappedLines(it, maxLines = 2) } ?: 0) +
				(juzText?.let { countWrappedLines(it, maxLines = 2) } ?: 0)

			val fm = textPaint.fontMetrics
			val blockH = (totalLines.coerceAtLeast(1)) * rightBodyLineH
			// drawWrappedText() uses baseline Y. Center the whole block vertically around pivotY.
			var y = (pivotY - blockH * 0.5f) - fm.ascent
			meaningName?.let {
				y = drawWrappedText(
					canvas = canvas,
					text = it,
					x = pivotX,
					y = y,
					maxWidth = maxWidthHorizontal,
					maxY = Float.POSITIVE_INFINITY,
					paint = textPaint,
					lineH = rightBodyLineH,
					maxLines = 6,
				)
			}
			revelationType?.let {
				y = drawWrappedText(
					canvas = canvas,
					text = it,
					x = pivotX,
					y = y,
					maxWidth = maxWidthHorizontal,
					maxY = Float.POSITIVE_INFINITY,
					paint = textPaint,
					lineH = rightBodyLineH,
					maxLines = 2,
				)
			}
			juzRange?.let {
				drawWrappedText(
					canvas = canvas,
					text = "Juz range: $it",
					x = pivotX,
					y = y,
					maxWidth = maxWidthHorizontal,
					maxY = Float.POSITIVE_INFINITY,
					paint = textPaint,
					lineH = rightBodyLineH,
					maxLines = 2,
				)
			}
			canvas.restore()
			textPaint.textSize = savedTextSize
			textPaint.typeface = savedTextTypeface
		}

		val leftCenterX = (pad + spineX) * 0.5f
		val leftPageW = (spineX - pad).coerceAtLeast(1f)
		val leftMaxW = (leftPageW - infoPadX * 1.3f).coerceAtLeast(1f)
		drawLeftText(leftPage, leftCenterX, skewX = -0.18f, maxW = leftMaxW)

		val rightCenterX = (spineX + (w - pad)) * 0.5f
		val rightPageW = ((w - pad) - spineX).coerceAtLeast(1f)
		val rightMaxW = (rightPageW - infoPadX * 1.3f).coerceAtLeast(1f)
		drawRightInfo(rightPage, rightCenterX, skewX = 0.18f, maxW = rightMaxW)

		titlePaint.textSkewX = savedTitleSkew
		textPaint.textSkewX = savedTextSkew
	}

	private fun drawWrappedText(
		canvas: Canvas,
		text: String,
		x: Float,
		y: Float,
		maxWidth: Float,
		maxY: Float,
		paint: android.graphics.Paint,
		lineH: Float,
		maxLines: Int,
	): Float {
		if (text.isBlank()) return y
		var cursorY = y
		var lines = 0
		val paragraphs = text.split('\n')
		for (p in paragraphs) {
			var start = 0
			val s = p.trim()
			if (s.isEmpty()) continue
			while (start < s.length && lines < maxLines) {
				if (cursorY > maxY) return cursorY
				var count = paint.breakText(s, start, s.length, true, maxWidth, null)
				if (count <= 0) break
				var end = (start + count).coerceAtMost(s.length)
				if (end < s.length) {
					// Prefer breaking on whitespace.
					val lastSpace = s.lastIndexOf(' ', end - 1)
					if (lastSpace > start + 3) end = lastSpace
				}
				var line = s.substring(start, end).trim()
				val hasMore = end < s.length
				val isLastLine = (lines == maxLines - 1)
				if (isLastLine && hasMore) {
					// Ellipsize the last line.
					val ell = "…"
					var cut = line.length
					while (cut > 0 && paint.measureText(line.substring(0, cut) + ell) > maxWidth) {
						cut--
					}
					line = if (cut > 0) line.substring(0, cut) + ell else ell
				}
				canvas.drawText(line, x, cursorY, paint)
				cursorY += lineH
				lines++
				start = end
				while (start < s.length && s[start] == ' ') start++
			}
			if (lines >= maxLines) break
		}
		return cursorY
	}

	private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}

	private class SparklingAyahBadgeView(context: Context) : View(context) {
		private var ayahNumber: Int = 1
		private var phase: Float = 0f
		private var animator: ValueAnimator? = null

		private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeWidth = dp(1.3f)
			color = Color.argb(210, 255, 255, 255)
		}
		private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.FILL
			color = Color.argb(38, 255, 255, 255)
		}
		private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.argb(235, 255, 255, 255)
			textAlign = Paint.Align.CENTER
			typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
		}
		private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.FILL
			color = Color.WHITE
		}

		fun setAyahNumber(value: Int) {
			val v = value.coerceAtLeast(1)
			if (v == ayahNumber) return
			ayahNumber = v
			invalidate()
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			ensureAnimator()
			if (visibility == VISIBLE) animator?.start()
		}

		override fun onDetachedFromWindow() {
			animator?.cancel()
			super.onDetachedFromWindow()
		}

		override fun onVisibilityChanged(changedView: View, visibility: Int) {
			super.onVisibilityChanged(changedView, visibility)
			if (changedView !== this) return
			if (visibility == VISIBLE) {
				ensureAnimator()
				animator?.start()
			} else {
				animator?.cancel()
			}
		}

		private fun ensureAnimator() {
			if (animator != null) return
			animator = ValueAnimator.ofFloat(0f, 1f).apply {
				duration = 1250L
				repeatCount = ValueAnimator.INFINITE
				repeatMode = ValueAnimator.RESTART
				interpolator = android.view.animation.LinearInterpolator()
				addUpdateListener { a ->
					phase = (a.animatedValue as Float)
					invalidate()
				}
			}
		}

		private fun dp(v: Float): Float = v * resources.displayMetrics.density

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			val w = width.toFloat().coerceAtLeast(1f)
			val h = height.toFloat().coerceAtLeast(1f)
			val cx = w * 0.5f
			val cy = h * 0.5f
			val baseR = (minOf(w, h) * 0.5f) - strokePaint.strokeWidth
			val pulse = 1f + 0.03f * sin(phase * 2f * PI.toFloat())
			val r = (baseR * pulse).coerceAtLeast(1f)

			// Base circle.
			canvas.drawCircle(cx, cy, r, fillPaint)
			strokePaint.alpha = (175 + 55 * ((sin((phase + 0.12f) * 2f * PI.toFloat()) + 1f) * 0.5f)).toInt().coerceIn(120, 235)
			canvas.drawCircle(cx, cy, r, strokePaint)

			// Sparkles around the rim.
			val sparkCount = 6
			val rimR = (r + dp(0.6f)).coerceAtLeast(1f)
			val dotR = dp(1.6f)
			for (i in 0 until sparkCount) {
				val ang = (phase * 2f * PI.toFloat()) + (i.toFloat() * (2f * PI.toFloat() / sparkCount.toFloat()))
				val sx = cx + cos(ang) * rimR
				val sy = cy + sin(ang) * rimR
				val glow = ((sin(ang * 2.1f + phase * 5f) + 1f) * 0.5f)
				sparklePaint.alpha = (60 + glow * 185f).toInt().coerceIn(35, 245)
				canvas.drawCircle(sx, sy, dotR, sparklePaint)
			}

			// Center number.
			textPaint.textSize = (h * 0.46f).coerceIn(dp(10f), dp(16f))
			val fm = textPaint.fontMetrics
			val baseline = cy - (fm.ascent + fm.descent) * 0.5f
			canvas.drawText(ayahNumber.toString(), cx, baseline, textPaint)
		}
	}

private class SurahWheelRenderer(
	private val onProjectedCenters: ((FloatArray, Int, Float, Float) -> Unit)? = null,
) : GLSurfaceView.Renderer {
	constructor(
		onProjectedCenters: ((FloatArray, Int, Float, Float) -> Unit)? = null,
		drawStarfield: Boolean,
		drawWheel: Boolean,
	) : this(onProjectedCenters) {
		this.drawStarfield = drawStarfield
		this.drawWheel = drawWheel
	}

	private var drawStarfield: Boolean = true
	private var drawWheel: Boolean = true
	private val rand = Random(114)

	private val starsCount = 1800
	private val meteorsMax = 6

	private val cameraZ = 13.5f
	private val skyRotationDegPerSec = 0.06f

	private var wheelRadius = 4.35f
	private var wheelCenterY = -7.35f
	private val wheelCenterZ = 0.2f
	private val fadeCosMin = 0.33f
	private val itemAngleStep = (2f * PI.toFloat()) / SURA_NAMES.size.toFloat()
	private var currentIndex = 0f
	private var targetIndex = 0f
	private var flingVelocity = 0f // index/sec
	private var userDragging = false
	private val easeSpeed = 14.0f
	private val flingFriction = 8.5f
	private val snapVelocityThreshold = 0.18f

	private var program = 0
	private var aPosition = 0
	private var uMvp = 0
	private var uColor = 0
	private var uPointSize = 0
	private var uTime = 0
	private var uFlickerAmount = 0

	private lateinit var starsBuffer: FloatBuffer
	private lateinit var meteorsBuffer: FloatBuffer
	private lateinit var meteorsData: FloatArray

	private val projectedCenters = FloatArray(SURA_NAMES.size * 3)
	private val meteorActive = BooleanArray(meteorsMax)
	private val meteorHead = FloatArray(meteorsMax * 3)
	private val meteorVel = FloatArray(meteorsMax * 3)
	private val meteorLife = FloatArray(meteorsMax)
	private val meteorTailLen = FloatArray(meteorsMax)

	private val proj = FloatArray(16)
	private val view = FloatArray(16)
	private val model = FloatArray(16)
	private val vp = FloatArray(16)
	private val mvp = FloatArray(16)
	private val v4 = FloatArray(4)
	private val clip = FloatArray(4)

	private var width = 1
	private var height = 1
	private var listCenterYPx = 0f
	private var skyAngle = 0f
	private var startTimeMs: Long = 0L
	private var lastFrameMs: Long = 0L
	private var lastTurnIndex = 0f
	private var lastTurnDir = 1f

	override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
		GLES30.glClearColor(0f, 0f, 0f, 1f)
		GLES30.glEnable(GLES30.GL_DEPTH_TEST)
		GLES30.glEnable(GLES30.GL_BLEND)
		GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

		// Start with Surah 1 centered.
		currentIndex = 0f
		targetIndex = 0f
		flingVelocity = 0f
		userDragging = false
		lastTurnIndex = 0f
		lastTurnDir = 1f

		program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
		aPosition = GLES30.glGetAttribLocation(program, "aPosition")
		uMvp = GLES30.glGetUniformLocation(program, "uMvp")
		uColor = GLES30.glGetUniformLocation(program, "uColor")
		uPointSize = GLES30.glGetUniformLocation(program, "uPointSize")
		uTime = GLES30.glGetUniformLocation(program, "uTime")
		uFlickerAmount = GLES30.glGetUniformLocation(program, "uFlickerAmount")

		generateSceneBuffers()
		startTimeMs = SystemClock.uptimeMillis()
		lastFrameMs = startTimeMs
	}

	override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
		this.width = width
		this.height = height
		// Center the wheel vertically within the available view.
		listCenterYPx = height.toFloat() * 0.5f
		GLES30.glViewport(0, 0, width, height)

		val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
		Matrix.perspectiveM(proj, 0, 55f, aspect, 0.1f, 100f)
		Matrix.setLookAtM(view, 0, 0f, 0f, cameraZ, 0f, 0f, 0f, 0f, 1f, 0f)
		Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

		// Scale the wheel so it occupies (almost) the full height of the view.
		// This makes the wheel fill the expanded container vertically.
		fitWheelToViewportHeight(fillNdc = 0.94f)
	}

	private fun fitWheelToViewportHeight(fillNdc: Float) {
		// We fit using two points that share the same depth (cos = 0 => z == wheelCenterZ),
		// so the mapping from world Y -> NDC Y is effectively linear.
		val zFit = wheelCenterZ
		val ndc0 = ndcYForWorldY(yWorld = 0f, zWorld = zFit)
		val ndc1 = ndcYForWorldY(yWorld = 1f, zWorld = zFit)
		val a = ndc1 - ndc0
		if (abs(a) < 1e-5f) return
		val b = ndc0

		val top = fillNdc.coerceIn(0.1f, 0.99f)
		val bottom = -top
		val centerNdc = (top + bottom) * 0.5f
		val radiusNdc = (top - bottom) * 0.5f

		wheelCenterY = (centerNdc - b) / a
		wheelRadius = abs(radiusNdc / a).coerceIn(1.0f, 50.0f)
	}

	private fun ndcYForWorldY(yWorld: Float, zWorld: Float): Float {
		v4[0] = 0f
		v4[1] = yWorld
		v4[2] = zWorld
		v4[3] = 1f
		Matrix.multiplyMV(clip, 0, vp, 0, v4, 0)
		val w = clip[3]
		if (abs(w) < 1e-6f) return 0f
		return clip[1] / w
	}

	override fun onDrawFrame(gl: GL10?) {
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
		GLES30.glUseProgram(program)

		val nowMs = SystemClock.uptimeMillis()
		val dt = ((nowMs - lastFrameMs).coerceAtLeast(1L)) / 1000f
		lastFrameMs = nowMs
		val n = SURA_NAMES.size.toFloat()
		val timeSec = (nowMs - startTimeMs) / 1000f
		GLES30.glUniform1f(uTime, timeSec)

		// Transition-like scrolling: ease toward target, add inertial fling, then snap.
		if (!userDragging) {
			if (abs(flingVelocity) > 0.0001f) {
				targetIndex = wrapIndex(targetIndex + flingVelocity * dt, n)
				val decay = kotlin.math.exp(-flingFriction * dt)
				flingVelocity *= decay
			}
			if (abs(flingVelocity) < snapVelocityThreshold) {
				// Snap to nearest integer index.
				val snapped = kotlin.math.round(targetIndex)
				targetIndex = wrapIndex(snapped, n)
			}
		}
		currentIndex = easeIndexToward(currentIndex, targetIndex, n, dt)

		// Derive a turning progress from the fractional index while moving.
		val delta = shortestDelta(lastTurnIndex, currentIndex, n)
		if (abs(delta) > 0.00001f) lastTurnDir = if (delta >= 0f) 1f else -1f
		lastTurnIndex = currentIndex
		val phase = (currentIndex - floor(currentIndex)).coerceIn(0f, 1f)
		// Flip direction so the visible page turn matches scroll direction better.
		val outDir = -lastTurnDir
		val turnProgress = if (outDir >= 0f) phase else (1f - phase)

		if (drawStarfield) {
			// Stars (background) - matches the current background.
			val skyAngleNow = timeSec * skyRotationDegPerSec
			Matrix.setIdentityM(model, 0)
			Matrix.rotateM(model, 0, skyAngleNow, 0f, 1f, 0f)
			Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
			drawPoints(
				starsBuffer,
				starsCount,
				mvp,
				colorR = 1f,
				colorG = 1f,
				colorB = 1f,
				colorA = 0.9f,
				pointSize = 2.2f,
				flickerAmount = 1f,
			)
			updateMeteors(dt)
			updateMeteorsBuffer()
			drawLines(
				meteorsBuffer,
				meteorsMax * 2,
				mvp,
				colorR = 0.95f,
				colorG = 0.98f,
				colorB = 1f,
				colorA = 0.95f,
				lineWidth = 2.6f,
				flickerAmount = 0f,
			)
		}

		// 3D wheel at the bottom: 114 names wrapped around a cylinder.
		if (!drawWheel) return
		for (i in projectedCenters.indices step 3) {
			projectedCenters[i] = 0f
			projectedCenters[i + 1] = 0f
			projectedCenters[i + 2] = 0f
		}
		var bestIndex = -1
		var bestDist = Float.POSITIVE_INFINITY
		for (i in 0 until SURA_NAMES.size) {
			val angle = (i.toFloat() - currentIndex) * itemAngleStep
			val cosA = cos(angle)
			if (cosA <= fadeCosMin) continue
			val sinA = sin(angle)
			val alpha = ((cosA - fadeCosMin) / (1f - fadeCosMin)).coerceIn(0f, 1f)
			v4[1] = wheelCenterY + sinA * wheelRadius
			v4[2] = wheelCenterZ + cosA * wheelRadius
			v4[3] = 1f

			Matrix.multiplyMV(clip, 0, vp, 0, v4, 0)
			val w = clip[3]
			val out = i * 3
			if (w <= 0.0001f) continue
			val ndcX = clip[0] / w
			val ndcY = clip[1] / w
			val ndcZ = clip[2] / w
			val visible = ndcX >= -1f && ndcX <= 1f && ndcY >= -1f && ndcY <= 1f && ndcZ >= -1f && ndcZ <= 1f
			if (!visible) continue
			val xPx = (ndcX * 0.5f + 0.5f) * width.toFloat()
			val yPx = (1f - (ndcY * 0.5f + 0.5f)) * height.toFloat()
			projectedCenters[out] = xPx
			projectedCenters[out + 1] = yPx
			projectedCenters[out + 2] = alpha

			// Central surah = item closest to the vertical center of the visible list strip.
			if (alpha > 0.08f) {
				val d = abs(yPx - listCenterYPx)
				if (d < bestDist) {
					bestDist = d
					bestIndex = i
				}
			}
		}
		val centerIndex = if (bestIndex >= 0) bestIndex else wrapIndex(kotlin.math.round(currentIndex), n).toInt().coerceIn(0, SURA_NAMES.size - 1)

		onProjectedCenters?.invoke(projectedCenters, centerIndex, turnProgress, outDir)
	}

	fun onWheelDrag(deltaIndex: Float) {
		val n = SURA_NAMES.size.toFloat()
		// While dragging, we move the target; easing makes it feel like a transition.
		targetIndex = wrapIndex(targetIndex + deltaIndex, n)
	}

	fun onWheelRelease(velocityIndexPerSec: Float) {
		// Carry momentum after release.
		flingVelocity = velocityIndexPerSec.coerceIn(-12f, 12f)
	}

	fun setUserDragging(dragging: Boolean) {
		userDragging = dragging
		if (dragging) {
			flingVelocity = 0f
			val n = SURA_NAMES.size.toFloat()
			targetIndex = wrapIndex(currentIndex, n)
		}
	}

	private fun wrapIndex(x: Float, n: Float): Float {
		var v = x % n
		if (v < 0f) v += n
		return v
	}

	private fun shortestDelta(from: Float, to: Float, n: Float): Float {
		// Return delta in (-n/2, n/2]
		var d = (to - from) % n
		if (d > n * 0.5f) d -= n
		if (d < -n * 0.5f) d += n
		return d
	}

	private fun easeIndexToward(current: Float, target: Float, n: Float, dt: Float): Float {
		val a = 1f - kotlin.math.exp(-easeSpeed * dt)
		val d = shortestDelta(current, target, n)
		return wrapIndex(current + d * a, n)
	}

	private fun drawPoints(
		buffer: FloatBuffer,
		count: Int,
		mvpMatrix: FloatArray,
		colorR: Float,
		colorG: Float,
		colorB: Float,
		colorA: Float,
		pointSize: Float,
		flickerAmount: Float,
	) {
		buffer.position(0)
		GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
		GLES30.glUniform4f(uColor, colorR, colorG, colorB, colorA)
		GLES30.glUniform1f(uPointSize, pointSize)
		GLES30.glUniform1f(uFlickerAmount, flickerAmount)
		GLES30.glEnableVertexAttribArray(aPosition)
		GLES30.glVertexAttribPointer(aPosition, 3, GLES30.GL_FLOAT, false, 3 * 4, buffer)
		GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
		GLES30.glDisableVertexAttribArray(aPosition)
	}

	private fun drawLines(
		buffer: FloatBuffer,
		vertexCount: Int,
		mvpMatrix: FloatArray,
		colorR: Float,
		colorG: Float,
		colorB: Float,
		colorA: Float,
		lineWidth: Float,
		flickerAmount: Float,
	) {
		buffer.position(0)
		GLES30.glLineWidth(lineWidth)
		GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
		GLES30.glUniform4f(uColor, colorR, colorG, colorB, colorA)
		GLES30.glUniform1f(uPointSize, 1f)
		GLES30.glUniform1f(uFlickerAmount, flickerAmount)
		GLES30.glEnableVertexAttribArray(aPosition)
		GLES30.glVertexAttribPointer(aPosition, 3, GLES30.GL_FLOAT, false, 3 * 4, buffer)
		GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
		GLES30.glDisableVertexAttribArray(aPosition)
	}

	private fun updateMeteors(dt: Float) {
		// Spawn a meteor sometimes (rate per second).
		val spawnRatePerSec = 0.08f
		for (i in 0 until meteorsMax) {
			if (!meteorActive[i]) {
				if (rand.nextFloat() < dt * spawnRatePerSec) {
					meteorActive[i] = true
					meteorLife[i] = 1.2f + rand.nextFloat() * 0.6f
					meteorTailLen[i] = 3.0f + rand.nextFloat() * 2.5f
					meteorHead[i * 3] = (rand.nextFloat() * 2f - 1f) * 22f
					meteorHead[i * 3 + 1] = (rand.nextFloat() * 2f - 1f) * 10f + 10f
					meteorHead[i * 3 + 2] = -18f - rand.nextFloat() * 28f
					val dirX = (0.6f + rand.nextFloat() * 0.7f) * (if (rand.nextBoolean()) 1f else -1f)
					val dirY = -(1.0f + rand.nextFloat() * 0.8f)
					val len = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.0001f)
					val speed = 16f + rand.nextFloat() * 10f
					meteorVel[i * 3] = (dirX / len) * speed
					meteorVel[i * 3 + 1] = (dirY / len) * speed
					meteorVel[i * 3 + 2] = 0f
				}
				continue
			}

			meteorLife[i] -= dt
			meteorHead[i * 3] += meteorVel[i * 3] * dt
			meteorHead[i * 3 + 1] += meteorVel[i * 3 + 1] * dt
			if (meteorLife[i] <= 0f || meteorHead[i * 3 + 1] < -26f || abs(meteorHead[i * 3]) > 70f) {
				meteorActive[i] = false
			}
		}
	}

	private fun updateMeteorsBuffer() {
		var out = 0
		for (i in 0 until meteorsMax) {
			if (!meteorActive[i]) {
				for (k in 0 until 6) meteorsData[out++] = 0f
				continue
			}
			val hx = meteorHead[i * 3]
			val hy = meteorHead[i * 3 + 1]
			val hz = meteorHead[i * 3 + 2]
			val vx = meteorVel[i * 3]
			val vy = meteorVel[i * 3 + 1]
			val len = sqrt(vx * vx + vy * vy).coerceAtLeast(0.0001f)
			val tx = -vx / len
			val ty = -vy / len
			val tl = meteorTailLen[i]
			val txp = hx + tx * tl
			val typ = hy + ty * tl
			meteorsData[out++] = hx
			meteorsData[out++] = hy
			meteorsData[out++] = hz
			meteorsData[out++] = txp
			meteorsData[out++] = typ
			meteorsData[out++] = hz
		}
		meteorsBuffer.position(0)
		meteorsBuffer.put(meteorsData)
		meteorsBuffer.position(0)
	}

	private fun generateSceneBuffers() {
		val stars = FloatArray(starsCount * 3)
		for (i in 0 until starsCount) {
			// Scatter stars in a large volume behind the spheres.
			val x = (rand.nextFloat() * 2f - 1f) * 40f
			val y = (rand.nextFloat() * 2f - 1f) * 24f
			val z = -15f - rand.nextFloat() * 65f
			val o = i * 3
			stars[o] = x
			stars[o + 1] = y
			stars[o + 2] = z
		}
		starsBuffer = asFloatBuffer(stars)
		meteorsData = FloatArray(meteorsMax * 2 * 3)
		meteorsBuffer = asFloatBuffer(meteorsData)
	}

	private fun asFloatBuffer(data: FloatArray): FloatBuffer {
		return ByteBuffer
			.allocateDirect(data.size * 4)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer()
			.apply {
				put(data)
				position(0)
			}
	}

	private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
		val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
		val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
		val program = GLES30.glCreateProgram()
		GLES30.glAttachShader(program, vs)
		GLES30.glAttachShader(program, fs)
		GLES30.glLinkProgram(program)

		val linkStatus = IntArray(1)
		GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
		if (linkStatus[0] == 0) {
			val log = GLES30.glGetProgramInfoLog(program)
			GLES30.glDeleteProgram(program)
			throw RuntimeException("Program link failed: $log")
		}
		GLES30.glDeleteShader(vs)
		GLES30.glDeleteShader(fs)
		return program
	}

	private fun compileShader(type: Int, src: String): Int {
		val shader = GLES30.glCreateShader(type)
		GLES30.glShaderSource(shader, src)
		GLES30.glCompileShader(shader)
		val status = IntArray(1)
		GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
		if (status[0] == 0) {
			val log = GLES30.glGetShaderInfoLog(shader)
			GLES30.glDeleteShader(shader)
			throw RuntimeException("Shader compile failed: $log")
		}
		return shader
	}

	private companion object {
		private const val VERTEX_SHADER = """
			uniform mat4 uMvp;
			uniform float uPointSize;
			uniform float uTime;
			uniform float uFlickerAmount;
			attribute vec3 aPosition;
			varying float vSeed;
			void main() {
				gl_Position = uMvp * vec4(aPosition, 1.0);
				gl_PointSize = uPointSize;
				vSeed = fract(sin(dot(aPosition, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
			}
		"""

		private const val FRAGMENT_SHADER = """
			precision mediump float;
			uniform vec4 uColor;
			uniform float uTime;
			uniform float uFlickerAmount;
			varying float vSeed;
			void main() {
				float twinkle = 0.78 + 0.22 * sin(uTime * (2.0 + vSeed * 6.0) + vSeed * 6.2831);
				float k = mix(1.0, twinkle, clamp(uFlickerAmount, 0.0, 1.0));
				gl_FragColor = vec4(uColor.rgb * k, uColor.a);
			}
		"""
	}
}

private val SURA_NAMES = arrayOf(
	"Al-Fatihah",
	"Al-Baqarah",
	"Aal-e-Imran",
	"An-Nisa",
	"Al-Ma'idah",
	"Al-An'am",
	"Al-A'raf",
	"Al-Anfal",
	"At-Tawbah",
	"Yunus",
	"Hud",
	"Yusuf",
	"Ar-Ra'd",
	"Ibrahim",
	"Al-Hijr",
	"An-Nahl",
	"Al-Isra",
	"Al-Kahf",
	"Maryam",
	"Ta-Ha",
	"Al-Anbiya",
	"Al-Hajj",
	"Al-Mu'minun",
	"An-Nur",
	"Al-Furqan",
	"Ash-Shu'ara",
	"An-Naml",
	"Al-Qasas",
	"Al-Ankabut",
	"Ar-Rum",
	"Luqman",
	"As-Sajdah",
	"Al-Ahzab",
	"Saba",
	"Fatir",
	"Ya-Sin",
	"As-Saffat",
	"Sad",
	"Az-Zumar",
	"Ghafir",
	"Fussilat",
	"Ash-Shura",
	"Az-Zukhruf",
	"Ad-Dukhan",
	"Al-Jathiyah",
	"Al-Ahqaf",
	"Muhammad",
	"Al-Fath",
	"Al-Hujurat",
	"Qaf",
	"Adh-Dhariyat",
	"At-Tur",
	"An-Najm",
	"Al-Qamar",
	"Ar-Rahman",
	"Al-Waqi'ah",
	"Al-Hadid",
	"Al-Mujadila",
	"Al-Hashr",
	"Al-Mumtahanah",
	"As-Saff",
	"Al-Jumu'ah",
	"Al-Munafiqun",
	"At-Taghabun",
	"At-Talaq",
	"At-Tahrim",
	"Al-Mulk",
	"Al-Qalam",
	"Al-Haqqah",
	"Al-Ma'arij",
	"Nuh",
	"Al-Jinn",
	"Al-Muzzammil",
	"Al-Muddaththir",
	"Al-Qiyamah",
	"Al-Insan",
	"Al-Mursalat",
	"An-Naba",
	"An-Nazi'at",
	"Abasa",
	"At-Takwir",
	"Al-Infitar",
	"Al-Mutaffifin",
	"Al-Inshiqaq",
	"Al-Buruj",
	"At-Tariq",
	"Al-A'la",
	"Al-Ghashiyah",
	"Al-Fajr",
	"Al-Balad",
	"Ash-Shams",
	"Al-Layl",
	"Ad-Duha",
	"Ash-Sharh",
	"At-Tin",
	"Al-'Alaq",
	"Al-Qadr",
	"Al-Bayyinah",
	"Az-Zalzalah",
	"Al-'Adiyat",
	"Al-Qari'ah",
	"At-Takathur",
	"Al-'Asr",
	"Al-Humazah",
	"Al-Fil",
	"Quraysh",
	"Al-Ma'un",
	"Al-Kawthar",
	"Al-Kafirun",
	"An-Nasr",
	"Al-Masad",
	"Al-Ikhlas",
	"Al-Falaq",
	"An-Nas",
)
