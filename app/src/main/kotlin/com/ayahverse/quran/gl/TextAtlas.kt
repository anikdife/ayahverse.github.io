package com.ayahverse.quran.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlin.math.ceil

internal class TextAtlas private constructor(
	val textureId: Int,
	val entries: List<Entry>,
) {
	data class Entry(
		val u0: Float,
		val v0: Float,
		val u1: Float,
		val v1: Float,
		val aspect: Float,
	)

	companion object {
		fun create(context: Context, strings: List<String>): TextAtlas {
			val density = context.resources.displayMetrics.density
			val textSizePx = 64f * density
			val pad = (10f * density).toInt().coerceAtLeast(6)

			val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = Color.WHITE
				textSize = textSizePx
				typeface = Typeface.DEFAULT_BOLD
				setShadowLayer(14f * density, 0f, 0f, Color.argb(190, 140, 180, 255))
			}
			val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = Color.WHITE
				textSize = textSizePx
				typeface = Typeface.DEFAULT_BOLD
			}

			val fm = textPaint.fontMetrics
			val textHeight = ceil((fm.descent - fm.ascent).toDouble()).toInt().coerceAtLeast(1)
			val rowHeight = textHeight + pad * 2

			val sizesToTry = intArrayOf(2048, 4096)
			var packed: Packed? = null
			for (size in sizesToTry) {
				packed = pack(strings, size, pad, rowHeight, textPaint)
				if (packed != null) break
			}
			require(packed != null) { "Text atlas packing failed" }

			val atlasSize = packed.atlasSize
			val bitmap = Bitmap.createBitmap(atlasSize, atlasSize, Bitmap.Config.ARGB_8888)
			bitmap.eraseColor(Color.TRANSPARENT)
			val canvas = Canvas(bitmap)

			val entries = ArrayList<Entry>(strings.size)
			for (i in strings.indices) {
				val p = packed.placements[i]
				val x = p.x
				val y = p.y
				val w = p.w
				val h = p.h
				val baseline = y + pad - fm.ascent
				canvas.drawText(strings[i], x + pad.toFloat(), baseline, glowPaint)
				canvas.drawText(strings[i], x + pad.toFloat(), baseline, textPaint)

				val u0 = x.toFloat() / atlasSize
				val v0 = y.toFloat() / atlasSize
				val u1 = (x + w).toFloat() / atlasSize
				val v1 = (y + h).toFloat() / atlasSize
				val aspect = w.toFloat() / h.toFloat()
				entries.add(Entry(u0, v0, u1, v1, aspect))
			}

			val textureId = IntArray(1)
			GLES30.glGenTextures(1, textureId, 0)
			GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId[0])
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
			GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
			GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
			bitmap.recycle()

			return TextAtlas(textureId = textureId[0], entries = entries)
		}
	}
}

private data class Packed(val atlasSize: Int, val placements: List<Placement>)
private data class Placement(val x: Int, val y: Int, val w: Int, val h: Int)

private fun pack(
	strings: List<String>,
	atlasSize: Int,
	pad: Int,
	rowHeight: Int,
	paint: Paint,
): Packed? {
	var x = 0
	var y = 0
	val placements = ArrayList<Placement>(strings.size)

	for (s in strings) {
		val w = ceil(paint.measureText(s).toDouble()).toInt() + pad * 2
		val h = rowHeight
		if (w > atlasSize) return null
		if (x + w > atlasSize) {
			x = 0
			y += rowHeight
		}
		if (y + h > atlasSize) return null
		placements.add(Placement(x, y, w, h))
		x += w
	}

	return Packed(atlasSize, placements)
}
