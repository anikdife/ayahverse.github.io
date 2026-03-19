package com.ayahverse.quran.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class StarField(
	seed: Int = 7,
	starCount: Int = 12000,
) {
	private val quadVbo = IntArray(1)
	private val instanceVbo = IntArray(1)
	private val vao = IntArray(1)
	private val instanceCount: Int

	init {
		val random = Random(seed)
		val count = starCount.coerceIn(1000, 40000)
		instanceCount = count

		val instances = FloatArray(count * INSTANCE_STRIDE_FLOATS)
		var o = 0
		for (i in 0 until count) {
			val theta = (random.nextFloat() * 2f * PI).toFloat()
			val u = random.nextFloat() * 2f - 1f
			val phi = kotlin.math.acos(u.toDouble()).toFloat()
			val radius = 12f + random.nextFloat() * 38f

			val sx = (radius * sin(phi) * cos(theta))
			val sy = (radius * cos(phi))
			val sz = (radius * sin(phi) * sin(theta))

			// Much bigger stars so they read clearly on mobile.
			val size = 0.55f + random.nextFloat() * 1.65f
			val phase = random.nextFloat() * (2f * PI).toFloat()
			val speed = 0.4f + random.nextFloat() * 1.6f
			val brightness = 0.7f + random.nextFloat() * 1.1f
			val halo = 1.1f + random.nextFloat() * 1.6f

			instances[o++] = sx
			instances[o++] = sy
			instances[o++] = sz
			instances[o++] = size
			instances[o++] = phase
			instances[o++] = speed
			instances[o++] = brightness
			instances[o++] = halo
		}

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
			instances.size * 4,
			instances.toFloatBuffer(),
			GLES30.GL_STATIC_DRAW,
		)
		val strideBytes = INSTANCE_STRIDE_FLOATS * 4
		var offsetBytes = 0
		// iCenter.xyz
		GLES30.glEnableVertexAttribArray(2)
		GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(2, 1)
		offsetBytes += 3 * 4
		// iSize
		GLES30.glEnableVertexAttribArray(3)
		GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(3, 1)
		offsetBytes += 1 * 4
		// iPhase
		GLES30.glEnableVertexAttribArray(4)
		GLES30.glVertexAttribPointer(4, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(4, 1)
		offsetBytes += 1 * 4
		// iSpeed
		GLES30.glEnableVertexAttribArray(5)
		GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(5, 1)
		offsetBytes += 1 * 4
		// iBrightness
		GLES30.glEnableVertexAttribArray(6)
		GLES30.glVertexAttribPointer(6, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(6, 1)
		offsetBytes += 1 * 4
		// iHalo
		GLES30.glEnableVertexAttribArray(7)
		GLES30.glVertexAttribPointer(7, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(7, 1)

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
		GLES30.glBindVertexArray(0)
	}

	fun draw() {
		GLES30.glBindVertexArray(vao[0])
		GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, instanceCount)
		GLES30.glBindVertexArray(0)
	}

	companion object {
		private const val INSTANCE_STRIDE_FLOATS = 8
	}
}

private fun FloatArray.toFloatBuffer() =
	ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
		put(this@toFloatBuffer)
		position(0)
	}
