package com.ayahverse.quran.gl

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CameraController {
	private val lock = Any()
	private var orbitYaw = 0f

	fun update(deltaSeconds: Float) {
		val dt = deltaSeconds.coerceIn(0f, 0.05f)
		synchronized(lock) {
			orbitYaw += dt * 0.03f
		}
	}

	private val eyeY = 10.5f
	private val eyeRadius = 38f

	data class Snapshot(
		val viewMatrix: FloatArray,
		val cameraRight: FloatArray,
		val cameraUp: FloatArray,
		val cameraForward: FloatArray,
	)

	fun snapshot(): Snapshot {
		val view = FloatArray(16)
		val right = FloatArray(3)
		val up = FloatArray(3)
		val yaw: Float = synchronized(lock) { orbitYaw }
		val ex = (sin(yaw) * eyeRadius)
		val ez = (cos(yaw) * eyeRadius)

		Matrix.setLookAtM(
			view,
			0,
			ex,
			eyeY,
			ez,
			0f,
			2.2f,
			0f,
			0f,
			1f,
			0f,
		)

		val forward = floatArrayOf(-ex, -eyeY, -ez)
		normalize3(forward)
		val worldUp = floatArrayOf(0f, 1f, 0f)
		val r = cross(forward, worldUp)
		normalize3(r)
		right[0] = r[0]
		right[1] = r[1]
		right[2] = r[2]
		val u = cross(r, forward)
		normalize3(u)
		up[0] = u[0]
		up[1] = u[1]
		up[2] = u[2]

		return Snapshot(viewMatrix = view, cameraRight = right, cameraUp = up, cameraForward = forward)
	}
}

private fun normalize3(v: FloatArray) {
	val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
	if (len <= 1e-6f) return
	v[0] /= len
	v[1] /= len
	v[2] /= len
}

private fun cross(a: FloatArray, b: FloatArray): FloatArray {
	return floatArrayOf(
		a[1] * b[2] - a[2] * b[1],
		a[2] * b[0] - a[0] * b[2],
		a[0] * b[1] - a[1] * b[0],
	)
}
