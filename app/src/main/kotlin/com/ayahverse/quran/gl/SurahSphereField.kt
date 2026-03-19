package com.ayahverse.quran.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class SurahSphereField(
	private val atlas: TextAtlas,
	sphereRadius: Float = 18.0f,
	latSteps: Int = 18,
	lonSteps: Int = 36,
) {
	val textureId: Int = atlas.textureId

	private val vao = IntArray(1)
	private val quadVbo = IntArray(1)
	private val instanceVbo = IntArray(1)
	private val instanceCount: Int

	init {
		val lat = latSteps.coerceIn(8, 64)
		val lon = lonSteps.coerceIn(12, 96)
		instanceCount = lat * lon

		val instances = FloatArray(instanceCount * INSTANCE_STRIDE_FLOATS)
		var o = 0
		var idx = 0
		for (i in 0 until lat) {
			val v = (i + 0.5f) / lat.toFloat()
			val phi = (v * PI).toFloat()
			val sp = sin(phi)
			val cp = cos(phi)
			for (j in 0 until lon) {
				val u = (j + 0.5f) / lon.toFloat()
				val theta = (u * 2f * PI).toFloat()
				val st = sin(theta)
				val ct = cos(theta)

				val cx = sphereRadius * sp * ct
				val cy = sphereRadius * (cp * 0.85f)
				val cz = sphereRadius * sp * st

				val nameIndex = idx % atlas.entries.size
				val entry = atlas.entries[nameIndex]
				val size = 1.35f

				instances[o++] = cx
				instances[o++] = cy
				instances[o++] = cz
				instances[o++] = size
				instances[o++] = entry.u0
				instances[o++] = entry.v0
				instances[o++] = entry.u1
				instances[o++] = entry.v1
				instances[o++] = entry.aspect

				idx++
			}
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

	fun draw() {
		GLES30.glBindVertexArray(vao[0])
		GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, instanceCount)
		GLES30.glBindVertexArray(0)
	}

	companion object {
		private const val INSTANCE_STRIDE_FLOATS = 9
	}
}

private fun FloatArray.toFloatBuffer() =
	ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
		put(this@toFloatBuffer)
		position(0)
	}
