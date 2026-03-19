package com.ayahverse.quran.gl

import android.content.Context
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal class SurahLabelField(
	context: Context,
	seed: Int = 11,
) {
	val textureId: Int
	private val vao = IntArray(1)
	private val quadVbo = IntArray(1)
	private val instanceVbo = IntArray(1)
	private val instanceCount: Int

	init {
		val names = SurahNames.arabic
		val atlas = TextAtlas.create(context, names)
		textureId = atlas.textureId
		instanceCount = names.size

		val random = Random(seed)
		val instances = FloatArray(instanceCount * INSTANCE_STRIDE_FLOATS)
		var o = 0
		for (i in 0 until instanceCount) {
			val theta = (random.nextFloat() * 2f * PI).toFloat()
			val u = random.nextFloat() * 2f - 1f
			val phi = kotlin.math.acos(u.toDouble()).toFloat()
			val radius = 8f + random.nextFloat() * 22f

			val cx = (radius * sin(phi) * cos(theta))
			val cy = (radius * cos(phi) * 0.6f)
			val cz = (radius * sin(phi) * sin(theta))

			val axisTheta = (random.nextFloat() * 2f * PI).toFloat()
			val ax = cos(axisTheta)
			val ay = 0.4f + random.nextFloat() * 0.6f
			val az = sin(axisTheta)
			val driftAmp = 0.15f + random.nextFloat() * 0.25f
			val driftPhase = random.nextFloat() * (2f * PI).toFloat()
			val size = 0.55f + random.nextFloat() * 0.65f

			val entry = atlas.entries[i]

			// baseCenter.xyz
			instances[o++] = cx
			instances[o++] = cy
			instances[o++] = cz
			// driftAxis.xyz
			instances[o++] = ax
			instances[o++] = ay
			instances[o++] = az
			// driftAmp
			instances[o++] = driftAmp
			// driftPhase
			instances[o++] = driftPhase
			// size
			instances[o++] = size
			// rect u0 v0 u1 v1
			instances[o++] = entry.u0
			instances[o++] = entry.v0
			instances[o++] = entry.u1
			instances[o++] = entry.v1
			// aspect
			instances[o++] = entry.aspect
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
		// baseCenter.xyz (loc 2)
		GLES30.glEnableVertexAttribArray(2)
		GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(2, 1)
		offsetBytes += 3 * 4
		// driftAxis.xyz (loc 3)
		GLES30.glEnableVertexAttribArray(3)
		GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(3, 1)
		offsetBytes += 3 * 4
		// driftAmp (loc 4)
		GLES30.glEnableVertexAttribArray(4)
		GLES30.glVertexAttribPointer(4, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(4, 1)
		offsetBytes += 1 * 4
		// driftPhase (loc 5)
		GLES30.glEnableVertexAttribArray(5)
		GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(5, 1)
		offsetBytes += 1 * 4
		// size (loc 6)
		GLES30.glEnableVertexAttribArray(6)
		GLES30.glVertexAttribPointer(6, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(6, 1)
		offsetBytes += 1 * 4
		// rect vec4 (loc 7)
		GLES30.glEnableVertexAttribArray(7)
		GLES30.glVertexAttribPointer(7, 4, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(7, 1)
		offsetBytes += 4 * 4
		// aspect (loc 8)
		GLES30.glEnableVertexAttribArray(8)
		GLES30.glVertexAttribPointer(8, 1, GLES30.GL_FLOAT, false, strideBytes, offsetBytes)
		GLES30.glVertexAttribDivisor(8, 1)

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
		GLES30.glBindVertexArray(0)
	}

	fun draw() {
		GLES30.glBindVertexArray(vao[0])
		GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, instanceCount)
		GLES30.glBindVertexArray(0)
	}

	companion object {
		private const val INSTANCE_STRIDE_FLOATS = 14
	}
}

private fun FloatArray.toFloatBuffer() =
	ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
		put(this@toFloatBuffer)
		position(0)
	}
