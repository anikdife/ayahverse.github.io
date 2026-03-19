package com.ayahverse.quran.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class SurahZLineField(
	private val atlas: TextAtlas,
	private val count: Int = 10,
) {
	val textureId: Int = atlas.textureId

	private val vao = IntArray(1)
	private val quadVbo = IntArray(1)
	private val instanceVbo = IntArray(1)
	private val instanceCount: Int
	private val instanceFloats: FloatArray

	init {
		instanceCount = count.coerceIn(1, 30)
		instanceFloats = FloatArray(instanceCount * INSTANCE_STRIDE_FLOATS)

		val quadVertices = floatArrayOf(
			// x, y, u, v
			-0.5f, -0.5f, 0f, 0f,
			0.5f, -0.5f, 1f, 0f,
			-0.5f, 0.5f, 0f, 1f,

			-0.5f, 0.5f, 0f, 1f,
			0.5f, -0.5f, 1f, 0f,
			0.5f, 0.5f, 1f, 1f,
		)

		GLES30.glGenVertexArrays(1, vao, 0)
		GLES30.glBindVertexArray(vao[0])

		GLES30.glGenBuffers(1, quadVbo, 0)
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo[0])
		GLES30.glBufferData(
			GLES30.GL_ARRAY_BUFFER,
			quadVertices.size * 4,
			quadVertices.toFloatBuffer(),
			GLES30.GL_STATIC_DRAW,
		)
		GLES30.glEnableVertexAttribArray(0)
		GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, 0)
		GLES30.glEnableVertexAttribArray(1)
		GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, 2 * 4)

		GLES30.glGenBuffers(1, instanceVbo, 0)
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo[0])
		GLES30.glBufferData(
			GLES30.GL_ARRAY_BUFFER,
			instanceFloats.size * 4,
			null,
			GLES30.GL_DYNAMIC_DRAW,
		)

		val strideBytes = INSTANCE_STRIDE_FLOATS * 4
		var offsetBytes = 0
		// iCenter.xyz loc2
		GLES30.glEnableVertexAttribArray(2)
		GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(2, 1)
		offsetBytes += 3 * 4
		// iSize loc3
		GLES30.glEnableVertexAttribArray(3)
		GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(3, 1)
		offsetBytes += 1 * 4
		// iRect vec4 loc4
		GLES30.glEnableVertexAttribArray(4)
		GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(4, 1)
		offsetBytes += 4 * 4
		// iAspect loc5
		GLES30.glEnableVertexAttribArray(5)
		GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(5, 1)

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
		GLES30.glBindVertexArray(0)
	}

	fun update(
		scroll: Float,
		spacing: Float,
		farZ: Float,
		baseX: Float = 0f,
		baseY: Float = 0f,
		labelSize: Float = 2.35f,
	) {
		val step = kotlin.math.floor(scroll / spacing).toInt()
		val frac = scroll - (step * spacing)
		var o = 0
		for (s in 0 until instanceCount) {
			val nameIndex = positiveMod(step + s, atlas.entries.size)
			val entry = atlas.entries[nameIndex]
			val z = farZ + (s * spacing) + frac

			instanceFloats[o++] = baseX
			instanceFloats[o++] = baseY
			instanceFloats[o++] = z
			instanceFloats[o++] = labelSize
			instanceFloats[o++] = entry.u0
			instanceFloats[o++] = entry.v0
			instanceFloats[o++] = entry.u1
			instanceFloats[o++] = entry.v1
			instanceFloats[o++] = entry.aspect
		}

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo[0])
		GLES30.glBufferSubData(
			GLES30.GL_ARRAY_BUFFER,
			0,
			instanceFloats.size * 4,
			instanceFloats.toFloatBuffer(),
		)
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
	}

	fun draw() {
		GLES30.glBindVertexArray(vao[0])
		GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, instanceCount)
		GLES30.glBindVertexArray(0)
	}

	companion object {
		private const val INSTANCE_STRIDE_FLOATS = 9
	}
}

private fun positiveMod(x: Int, m: Int): Int {
	val r = x % m
	return if (r < 0) r + m else r
}

private fun FloatArray.toFloatBuffer() =
	ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
		put(this@toFloatBuffer)
		position(0)
	}
