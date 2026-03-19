package com.ayahverse.quran.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ayahverse.quran.linguistics.supabase.LinguisticsWordCardModel
import com.ayahverse.quran.linguistics.supabase.SupabaseCoil
import kotlin.math.abs

/**
 * Horizontal word scroller for Linguistics.
 * Center item is prominent; off-center items fade/scale to mimic Z-depth.
 */
class LinguisticsWordsCarouselView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
	private val logTag = "LinguisticsCarousel"

	private val recyclerView: RecyclerView
	private val adapter = WordCardAdapter()
	private val snapHelper = LinearSnapHelper()

	init {
		orientation = VERTICAL

		recyclerView = RecyclerView(context).apply {
			layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
			adapter = this@LinguisticsWordsCarouselView.adapter
			clipToPadding = false
			setPadding(dp(18f), dp(6f), dp(18f), dp(10f))
			overScrollMode = View.OVER_SCROLL_NEVER
			itemAnimator = null

			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
					applyDepthTransforms()
				}

				override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
					applyDepthTransforms()
				}
			})
		}

		addView(
			recyclerView,
			LayoutParams(LayoutParams.MATCH_PARENT, dp(230f)),
		)

		snapHelper.attachToRecyclerView(recyclerView)

		// Ensure initial transform after first layout.
		recyclerView.post { applyDepthTransforms() }
	}

	fun setWords(words: List<LinguisticsWordCardModel>) {
		adapter.submit(words)
		recyclerView.post {
			applyDepthTransforms()
			// Snap to first item by default.
			if (words.isNotEmpty()) recyclerView.scrollToPosition(0)
		}
		visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
	}

	private fun applyDepthTransforms() {
		val centerX = recyclerView.width / 2f
		if (centerX <= 0f) return
		for (i in 0 until recyclerView.childCount) {
			val child = recyclerView.getChildAt(i)
			val childCenter = (child.left + child.right) / 2f
			val dist = abs(centerX - childCenter)
			val norm = (dist / centerX).coerceIn(0f, 1f)

			val scale = (1f - 0.10f * norm).coerceIn(0.90f, 1f)
			val alpha = (1f - 0.55f * norm).coerceIn(0.35f, 1f)
			val rotateY = 10f * (if (childCenter < centerX) 1f else -1f) * norm
			val tz = dp(10f) * (1f - norm)

			child.alpha = alpha
			child.scaleX = scale
			child.scaleY = scale
			child.rotationY = rotateY
			child.translationZ = tz
			child.cameraDistance = dp(1200f).toFloat()
		}
	}

	private fun dp(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

	private inner class WordCardAdapter : RecyclerView.Adapter<WordCardVH>() {
		private var items: List<LinguisticsWordCardModel> = emptyList()

		fun submit(newItems: List<LinguisticsWordCardModel>) {
			items = newItems
			notifyDataSetChanged()
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordCardVH {
			val card = buildCardView(parent.context)
			return WordCardVH(card)
		}

		override fun onBindViewHolder(holder: WordCardVH, position: Int) {
			holder.bind(items[position])
		}

		override fun getItemCount(): Int = items.size
	}

	private inner class WordCardVH(root: View) : RecyclerView.ViewHolder(root) {
		private val image: ImageView
		private val title: TextView
		private val meaning: TextView
		private val pronunciation: TextView
		private val grammar: TextView
		private val segments: TextView
		private val location: TextView

		init {
			val container = root as LinearLayout
			image = container.findViewWithTag("img")
			title = container.findViewWithTag("title")
			meaning = container.findViewWithTag("meaning")
			pronunciation = container.findViewWithTag("pron")
			grammar = container.findViewWithTag("grammar")
			segments = container.findViewWithTag("segs")
			location = container.findViewWithTag("loc")
		}

		fun bind(m: LinguisticsWordCardModel) {
			title.text = m.arabic.ifBlank { "Word ${m.wordNumber}" }
			meaning.text = m.meaning.ifBlank { "" }
			pronunciation.text = m.pronunciation.ifBlank { "" }
			grammar.text = m.arabicGrammar.ifBlank { "" }

			val segA = m.segRust.trim()
			val segB = m.segSky.trim()
			segments.text = listOf(segA, segB).filter { it.isNotBlank() }.joinToString(" · ")
			location.text = m.location

			val url = m.imageUrl?.trim().orEmpty()
			if (url.isNotBlank()) {
				image.visibility = View.VISIBLE
				image.load(url, SupabaseCoil.imageLoader(image.context)) {
					crossfade(false)
					allowHardware(true)
					listener(
						onError = { _, result ->
							Log.w(logTag, "Image load failed (word=${m.wordNumber}) url=$url", result.throwable)
						},
					)
				}
			} else {
				image.setImageDrawable(null)
				image.visibility = View.GONE
			}
		}
	}

	private fun buildCardView(ctx: Context): LinearLayout {
		val bg = GradientDrawable(
			GradientDrawable.Orientation.TOP_BOTTOM,
			intArrayOf(
				Color.argb(92, 255, 255, 255),
				Color.argb(40, 255, 255, 255),
			),
		).apply {
			cornerRadius = dp(18f).toFloat()
			setStroke(dp(1f), Color.argb(70, 255, 255, 255))
		}

		return LinearLayout(ctx).apply {
			orientation = VERTICAL
			gravity = Gravity.CENTER_HORIZONTAL
			background = bg
			setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
			layoutParams = RecyclerView.LayoutParams(dp(278f), ViewGroup.LayoutParams.MATCH_PARENT).apply {
				rightMargin = dp(12f)
			}

			addView(
				ImageView(ctx).apply {
					tag = "img"
					scaleType = ImageView.ScaleType.FIT_CENTER
					setBackgroundColor(Color.argb(20, 0, 0, 0))
					layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(112f)).apply {
						bottomMargin = dp(10f)
					}
				},
			)

			addView(
				TextView(ctx).apply {
					tag = "title"
					setTextColor(Color.argb(235, 212, 175, 55))
					textSize = 14f
					typeface = Typeface.DEFAULT_BOLD
					setSingleLine(true)
				},
			)

			addView(
				TextView(ctx).apply {
					tag = "meaning"
					setTextColor(Color.WHITE)
					textSize = 15f
					typeface = Typeface.DEFAULT_BOLD
					setPadding(0, dp(6f), 0, 0)
				},
			)

			addView(label(ctx, "pron", 13f, Color.argb(215, 255, 255, 255)))
			addView(label(ctx, "grammar", 12.5f, Color.argb(210, 255, 255, 255)))
			addView(label(ctx, "segs", 12f, Color.argb(200, 255, 255, 255)))
			addView(label(ctx, "loc", 12f, Color.argb(180, 255, 255, 255)))
		}
	}

	private fun label(ctx: Context, tag: String, size: Float, color: Int): TextView {
		return TextView(ctx).apply {
			this.tag = tag
			setTextColor(color)
			textSize = size
			setPadding(0, dp(6f), 0, 0)
			maxLines = 2
		}
	}
}
