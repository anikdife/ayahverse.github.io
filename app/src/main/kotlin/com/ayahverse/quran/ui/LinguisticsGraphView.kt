package com.ayahverse.quran.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import coil.ImageLoader
import coil.request.ImageRequest
import com.ayahverse.quran.linguistics.util.ArabicTextUtils
import com.ayahverse.quran.linguistics.supabase.SupabaseCoil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import android.util.LruCache
import com.ayahverse.quran.linguistics.supabase.LinguisticsWordCardModel
import kotlin.math.max
import kotlin.math.pow

/**
 * Linguistic visualization:
 * - One node per word (count matches the data source)
 * - Nodes are positioned on a 3D ring; exactly one node is closest to the viewer (front)
 * - User scrolls left/right to bring the next/previous node to the front (snaps)
 * - Each node has an "imaginary hanging card" showing its data (incl. small image)
 */
class LinguisticsGraphView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : View(context, attrs) {
	private data class NodeSpec(
		val angleRad: Float,
		val yJitter: Float,
	)

	private data class Projected(
		val idx: Int,
		val px: Float,
		val py: Float,
		val pr: Float,
		val pz: Float,
		val scale: Float,
	)

	private var nodes: List<NodeSpec> = emptyList()
	private var words: List<String> = emptyList()
	private var wordDetails: List<String> = emptyList()
	private var cards: List<LinguisticsWordCardModel> = emptyList()

	private var rotationRad: Float = 0f
	private var baseRotRad: Float = 0f
	private var snapAnimator: ValueAnimator? = null
	private var isDragging: Boolean = false
	private var dragStarted: Boolean = false
	private var downX: Float = 0f
	private var downY: Float = 0f
	private var lastTouchX: Float = 0f
	private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

	private val imageLoader: ImageLoader by lazy { SupabaseCoil.imageLoader(context) }
	private val imageCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
		override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
	}
	private val imageInFlight = mutableSetOf<String>()
	private val imageFailed = mutableSetOf<String>()
	private val logTag = "LinguisticsGraph"

	private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.WHITE
	}
	private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(3.5f)
		color = Color.argb(190, 255, 255, 255)
	}
	private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		textSize = dp(13f)
		// Subtle shadow for readability on bright backgrounds.
		setShadowLayer(dp(2f), 0f, dp(1f), Color.argb(160, 0, 0, 0))
	}
	private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.argb(110, 0, 0, 0)
	}
	private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(1.0f)
		color = Color.argb(120, 255, 255, 255)
	}
	private val cardTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		textSize = dp(13.5f)
		setShadowLayer(dp(2f), 0f, dp(1f), Color.argb(170, 0, 0, 0))
	}
	private val cardTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.LEFT
		textSize = dp(11.5f)
		setShadowLayer(dp(1.5f), 0f, dp(1f), Color.argb(140, 0, 0, 0))
	}

	fun setAyahText(ayahText: String) {
		val tokens = ArabicTextUtils.tokenizeAyah(ayahText)
		setWords(tokens.map { it.originalText }, seed = ayahText.hashCode())
	}

	fun setWordsWithDetails(words: List<String>, details: List<String?>, seed: Int = 1) {
		cards = emptyList()
		val normalizedDetails = List(words.size) { i -> details.getOrNull(i).orEmpty() }
		wordDetails = normalizedDetails
		setWords(words, seed = seed)
	}

	fun setCards(cards: List<LinguisticsWordCardModel>, seed: Int = 1) {
		this.cards = cards
		wordDetails = cards.map { it.meaning }
		setWords(cards.map { it.arabic.ifBlank { "${it.wordNumber}" } }, seed = seed)
	}

	fun setWordCount(wordCount: Int, seed: Int = 1) {
		cards = emptyList()
		setWords(List(wordCount) { "" }, seed = seed)
	}

	fun setWords(words: List<String>, seed: Int = 1) {
		snapAnimator?.cancel()
		snapAnimator = null
		this.words = words
		if (wordDetails.size != words.size) {
			wordDetails = List(words.size) { "" }
		}
		val wordCount = words.size
		if (wordCount <= 0) {
			nodes = emptyList()
			invalidate()
			return
		}

		val rng = Random(seed)
		baseRotRad = rng.nextFloat() * (2.0 * PI).toFloat()
		nodes = List(wordCount) { i ->
			val a = baseRotRad + (2.0 * PI * i.toDouble() / wordCount.toDouble()).toFloat()
			NodeSpec(
				angleRad = a,
				yJitter = (rng.nextFloat() - 0.5f) * 0.06f,
			)
		}

		// Ensure the first node is at the front/center.
		rotationRad = -nodes.first().angleRad
		invalidate()
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (nodes.isEmpty()) return super.onTouchEvent(event)

		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				snapAnimator?.cancel()
				snapAnimator = null
				isDragging = true
				dragStarted = false
				downX = event.x
				downY = event.y
				lastTouchX = event.x
				parent?.requestDisallowInterceptTouchEvent(false)
				return true
			}
			MotionEvent.ACTION_MOVE -> {
				if (!isDragging) return false
				val dxFromDown = event.x - downX
				val dyFromDown = event.y - downY
				if (!dragStarted) {
					val movedEnough = abs(dxFromDown) > touchSlop || abs(dyFromDown) > touchSlop
					if (movedEnough && abs(dxFromDown) >= abs(dyFromDown)) {
						dragStarted = true
						parent?.requestDisallowInterceptTouchEvent(true)
					}
				}
				if (dragStarted) {
					val dx = event.x - lastTouchX
					lastTouchX = event.x

					val w = width.toFloat().coerceAtLeast(1f)
					val radiansPerPixel = ((2.0 * PI) / w).toFloat() * 1.15f
					rotationRad = (rotationRad + dx * radiansPerPixel) % (2.0 * PI).toFloat()
					invalidate()
				}
				return true
			}
			MotionEvent.ACTION_UP -> {
				val wasDrag = dragStarted
				isDragging = false
				dragStarted = false
				parent?.requestDisallowInterceptTouchEvent(false)
				if (!wasDrag) {
					performClick()
				} else {
					snapToNearestNode()
				}
				return true
			}
			MotionEvent.ACTION_CANCEL -> {
				isDragging = false
				dragStarted = false
				parent?.requestDisallowInterceptTouchEvent(false)
				snapToNearestNode()
				return true
			}
		}

		return super.onTouchEvent(event)
	}

	override fun performClick(): Boolean {
		super.performClick()
		return true
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (nodes.isEmpty()) return

		val w = width.toFloat().coerceAtLeast(1f)
		val h = height.toFloat().coerceAtLeast(1f)
		val minDim = min(w, h)
			val cx = w * 0.5f
			// Keep the wheel close to the top of this (tall) view so there isn't a large blank gap
			// under the tab row, while still leaving room below for hanging cards.
			val cy = max(dp(92f), h * 0.28f)

		// 3D ring parameters.
		val ringRadiusX = 1.0f
		val ringRadiusZ = 1.0f
		val spreadX = w * 0.42f
		val spreadY = h * 0.22f
		val cameraZ = 2.65f
		val baseNodeRadius = (minDim * 0.020f).coerceAtLeast(dp(4.5f))

		fun nodeColor(idx: Int, count: Int): Int {
			val n = count.coerceAtLeast(1)
			// Evenly spaced hues, high saturation/value for bright distinct nodes.
			val hue = ((idx.toFloat() / n.toFloat()) * 360f) % 360f
			val hsv = floatArrayOf(hue, 0.85f, 1.0f)
			return Color.HSVToColor(hsv)
		}

		fun project(i: Int): Projected {
			val spec = nodes[i]
			val a = spec.angleRad + rotationRad
			val x = sin(a) * ringRadiusX
			val z = cos(a) * ringRadiusZ
			// Tilt the ring in screen-space so nodes are not on a single line.
			// Positive Y is "down" the screen. Make the front (z>0, toward the viewer) tilt down,
			// and the back (z<0, away) tilt up.
			val y = (x * 0.20f) + (z * 0.12f) + spec.yJitter
			val depth = max(0.35f, cameraZ - z)
			val scale = (cameraZ / depth).coerceIn(0.55f, 1.35f)
			val px = cx + x * spreadX * scale
			val py = cy + y * spreadY * scale
			val pr = baseNodeRadius * scale
			return Projected(idx = i, px = px, py = py, pr = pr, pz = z, scale = scale)
		}

		val projected = List(nodes.size) { i -> project(i) }
		val drawOrder = projected.sortedBy { it.pz }

		// Ring edges (draw behind to front).
		for (k in drawOrder.indices) {
			val i = drawOrder[k].idx
			val next = (i + 1) % nodes.size
			val p1 = projected[i]
			val p2 = projected[next]
			val t = ((p1.scale - 0.55f) / (1.35f - 0.55f)).coerceIn(0f, 1f)
			val alpha = (175 + (t * 80f)).toInt().coerceIn(160, 255)
			linePaint.alpha = alpha
			canvas.drawLine(p1.px, p1.py, p2.px, p2.py, linePaint)
		}
		linePaint.alpha = 255

		for (p in drawOrder) {
			val i = p.idx

			// Draw card first so the bright node + label always stay visible on top.
			drawHangingCard(canvas, p)

			val alpha = (120 + ((p.scale - 0.55f) / (1.35f - 0.55f) * 135f)).toInt().coerceIn(120, 255)
			nodePaint.color = nodeColor(i, nodes.size)
			nodePaint.alpha = alpha
			canvas.drawCircle(p.px, p.py, p.pr, nodePaint)

		}
	}

	private fun drawHangingCard(canvas: Canvas, proj: Projected) {
		val i = proj.idx
		val nodeScale = proj.scale
		val depthT = ((nodeScale - 0.55f) / (1.35f - 0.55f)).coerceIn(0f, 1f)
		// Smooth "frontness" so size/position transitions don't pop.
		// High exponent keeps most cards near base size, with only the closest card approaching 2×.
		var frontness = depthT.toDouble().pow(10.0).toFloat().coerceIn(0f, 1f)
		if (frontness < 0.002f) frontness = 0f
		// Base cards are half size; the closest/front card approaches double that (i.e., 2× others).
		val globalCardScale = 0.5f
		val cardScale = globalCardScale * (1.0f + frontness)

		val pad = dp(8f) * cardScale
		// Keep cards compact so they don't overlap the lower surah wheel.
		val maxWFrac = 0.72f + (0.92f - 0.72f) * frontness
		val cardW = (dp(168f) * cardScale).coerceAtMost(width * maxWFrac)
		val baseCardH = dp(96f) * cardScale
		// Give a 3D feel: front card lower, side/back cards slightly higher.
		// Keep a clear "hanging" gap so the node doesn't sit near the card center.
		val yOffset = (-dp(6f) * cardScale * (1f - depthT)) + (dp(16f) * cardScale * depthT * depthT)
		val hangGap = dp(26f) * cardScale

		val model = cards.getOrNull(i)
		val title = model?.arabic?.trim().orEmpty().ifBlank { words.getOrNull(i).orEmpty().trim() }
			.ifBlank { model?.wordNumber?.toString().orEmpty() }

		// Text sizing (used for both measuring and drawing).
		cardTitlePaint.textSize = (dp(13.5f) * cardScale).coerceIn(dp(10.5f), dp(22f))
		cardTitlePaint.alpha = (150 + (depthT * 105f)).toInt().coerceIn(150, 255)
		cardTextPaint.textSize = (dp(11.5f) * cardScale).coerceIn(dp(8.5f), dp(14.5f))
		cardTextPaint.alpha = (120 + (depthT * 120f)).toInt().coerceIn(120, 255)

		fun buildLayout(text: String, maxWidthPx: Int): StaticLayout {
			val safeWidth = maxWidthPx.coerceAtLeast(1)
			return if (Build.VERSION.SDK_INT >= 23) {
				StaticLayout.Builder
					.obtain(text, 0, text.length, cardTextPaint, safeWidth)
					.setAlignment(Layout.Alignment.ALIGN_CENTER)
					.setIncludePad(false)
					.setLineSpacing(0f, 1.05f)
					.build()
			} else {
				@Suppress("DEPRECATION")
				StaticLayout(
					text,
					cardTextPaint,
					safeWidth,
					Layout.Alignment.ALIGN_CENTER,
					1.05f,
					0f,
					false,
				)
			}
		}

		val availableW = (cardW - pad * 2f).toInt().coerceAtLeast(1)
		val titleHeight = if (title.isNotBlank()) {
			val fm = cardTitlePaint.fontMetrics
			(-fm.ascent + fm.descent)
		} else {
			0f
		}
		val titleGap = if (title.isNotBlank()) dp(8f) * cardScale else 0f

		// Thumbnail (if present). Center it under the node.
		val thumbSize = dp(32f) * cardScale
		val thumbGap = dp(6f) * cardScale
		val thumbAfterGap = dp(10f) * cardScale
		val hasThumbSlot = !model?.imageUrl.isNullOrBlank()
		val thumbBlockH = if (hasThumbSlot) (thumbSize + thumbGap + thumbAfterGap) else 0f

		// Smoothly expand height as a card approaches the front.
		val dynamicCardH = if (frontness == 0f) {
			baseCardH
		} else run {
			var needed = pad
			needed += titleHeight
			needed += titleGap
			needed += thumbBlockH

			fun addWrapped(label: String, value: String) {
				if (value.isBlank()) return
				val layout = buildLayout("$label: $value", availableW)
				needed += layout.height.toFloat()
				needed += dp(6f) * cardScale
			}

			if (model != null) {
				addWrapped("Meaning", model.meaning)
				addWrapped("Pron", model.pronunciation)
				addWrapped("Grammar", model.arabicGrammar)
				val segs = listOf(model.segRust.trim(), model.segSky.trim()).filter { it.isNotBlank() }.joinToString(" · ")
				addWrapped("Seg", segs)
				addWrapped("Loc", model.location)
			} else {
				addWrapped("Info", wordDetails.getOrNull(i).orEmpty())
			}

			needed += pad
			val expanded = max(baseCardH, needed)
			// Interpolate (smoothly) so non-front cards stay compact.
			baseCardH + (expanded - baseCardH) * frontness
		}

		val top = proj.py + proj.pr + hangGap + yOffset
		val left = (proj.px - cardW / 2f).coerceIn(dp(10f), width - cardW - dp(10f))
		val rect = RectF(left, top, left + cardW, top + dynamicCardH)

		// Intentionally allow overlap with the bottom wheel; do not clamp to a safe bottom.
		if (rect.top < dp(8f)) {
			rect.offset(0f, dp(8f) - rect.top)
		}
		val radius = dp(10f) * cardScale

		// Hanging line.
		val lineAlpha = (175 + (depthT * 80f)).toInt().coerceIn(160, 255)
		linePaint.alpha = lineAlpha
		canvas.drawLine(proj.px, proj.py + proj.pr, rect.centerX(), rect.top, linePaint)
		linePaint.alpha = 255

		// Card background.
		cardBgPaint.alpha = (70 + (depthT * 90f)).toInt().coerceIn(70, 160)
		canvas.drawRoundRect(rect, radius, radius, cardBgPaint)
		cardStrokePaint.alpha = (70 + (depthT * 110f)).toInt().coerceIn(70, 190)
		canvas.drawRoundRect(rect, radius, radius, cardStrokePaint)

		val centerX = rect.centerX()
		var cursorY = rect.top + pad

		// Title: Arabic word at the start of the card (not above the node).
		if (title.isNotBlank()) {
			val fm = cardTitlePaint.fontMetrics
			val baseline = cursorY - fm.ascent
			canvas.drawText(title, centerX, baseline, cardTitlePaint)
			cursorY = baseline + fm.descent + dp(8f) * cardScale
		}

		if (model != null) {
			val url = model.imageUrl
			val bmp = url?.let { imageCache.get(it) }
			if (!url.isNullOrBlank()) {
				if (bmp != null) {
					val dstLeft = centerX - thumbSize / 2f
					val dst = RectF(dstLeft, cursorY, dstLeft + thumbSize, cursorY + thumbSize)
					canvas.drawBitmap(bmp, null, dst, null)
				} else {
					if (!imageFailed.contains(url)) {
						requestThumb(url)
					}
				}
				// Reserve space for the thumbnail slot (stable layout even while loading).
				cursorY += thumbSize + thumbGap + dp(10f) * cardScale
			}
		}

		fun drawWrappedLine(label: String, value: String) {
			if (value.isBlank()) return
			val text = "$label: $value"
			val layout = buildLayout(text, availableW)
			val remaining = rect.bottom - pad - cursorY
			if (remaining <= dp(2f)) return
			val drawHeight = min(layout.height.toFloat(), remaining)
			canvas.save()
			canvas.clipRect(rect.left + pad, cursorY, rect.right - pad, cursorY + drawHeight)
			canvas.translate(rect.left + pad, cursorY)
			layout.draw(canvas)
			canvas.restore()
			cursorY += layout.height.toFloat() + dp(6f) * cardScale
		}

		if (model != null) {
			drawWrappedLine("Meaning", model.meaning)
			drawWrappedLine("Pron", model.pronunciation)
			drawWrappedLine("Grammar", model.arabicGrammar)
			val segs = listOf(model.segRust.trim(), model.segSky.trim()).filter { it.isNotBlank() }.joinToString(" · ")
			drawWrappedLine("Seg", segs)
			drawWrappedLine("Loc", model.location)
		} else {
			val detail = wordDetails.getOrNull(i).orEmpty()
			drawWrappedLine("Info", detail)
		}
	}

	private fun requestThumb(url: String) {
		if (url.isBlank()) return
		if (imageCache.get(url) != null) return
		if (imageFailed.contains(url)) return
		if (imageInFlight.contains(url)) return
		imageInFlight.add(url)

		val sizePx = (dp(42f)).toInt().coerceAtLeast(24)
		val req = ImageRequest.Builder(context)
			.data(url)
			.size(sizePx)
			.allowHardware(false)
			.listener(
				onError = { _, result ->
					imageFailed.add(url)
					Log.w(logTag, "Thumb load failed url=$url", result.throwable)
				},
			)
			.target(
				onSuccess = { drawable ->
					val bmp = drawableToBitmap(drawable, sizePx, sizePx)
					if (bmp != null) {
						imageCache.put(url, bmp)
					}
					imageInFlight.remove(url)
					postInvalidateOnAnimation()
				},
				onError = {
					imageInFlight.remove(url)
					postInvalidateOnAnimation()
				},
			)
			.build()

		imageLoader.enqueue(req)
	}

	private fun drawableToBitmap(drawable: Drawable, w: Int, h: Int): Bitmap? {
		return try {
			when (drawable) {
				is BitmapDrawable -> {
					val src = drawable.bitmap
					if (src.width == w && src.height == h) src else Bitmap.createScaledBitmap(src, w, h, true)
				}
				else -> {
					val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
					val c = Canvas(bmp)
					drawable.setBounds(0, 0, w, h)
					drawable.draw(c)
					bmp
				}
			}
		} catch (_: Throwable) {
			null
		}
	}

	private fun snapToNearestNode() {
		if (nodes.isEmpty()) return

		// Find which node is currently closest to the viewer (max z => angle closest to 0).
		var bestIdx = 0
		var bestZ = Float.NEGATIVE_INFINITY
		for (i in nodes.indices) {
			val a = nodes[i].angleRad + rotationRad
			val z = cos(a)
			if (z > bestZ) {
				bestZ = z
				bestIdx = i
			}
		}

		val target = -nodes[bestIdx].angleRad
		animateRotationTo(target)
	}

	private fun animateRotationTo(targetRad: Float) {
		val twoPi = (2.0 * PI).toFloat()
		val start = rotationRad

		fun wrapToPi(x: Float): Float {
			var v = x
			while (v > PI) v -= twoPi
			while (v < -PI) v += twoPi
			return v
		}

		val delta = wrapToPi(targetRad - start)
		val end = start + delta

		snapAnimator?.cancel()
		snapAnimator = ValueAnimator.ofFloat(start, end).apply {
			duration = 260L
			addUpdateListener {
				rotationRad = (it.animatedValue as Float) % twoPi
				postInvalidateOnAnimation()
			}
			start()
		}
	}

	private fun dp(dp: Float): Float = dp * resources.displayMetrics.density
}
