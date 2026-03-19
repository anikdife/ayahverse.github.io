package com.ayahverse.quran.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.sqrt

class SkyRenderer(
	private val context: Context,
	private val cameraController: CameraController,
) : GLSurfaceView.Renderer {
	private var lastFrameNanos = 0L
	private var timeSeconds = 0f

	private val projection = FloatArray(16)
	private val viewProj = FloatArray(16)

	private var skyProgram = 0
	private var sky_uTime = 0

	private var solidProgram = 0
	private var solid_uMvp = 0
	private var solid_uModel = 0
	private var solid_uLightDir = 0
	private var solid_uBaseColor = 0
	private var solid_uTime = 0

	private var tileProgram = 0
	private var tile_uMvp = 0
	private var tile_uModel = 0
	private var tile_uLightDir = 0
	private var tile_uTex = 0
	private var tile_uUvScale = 0
	private var tile_uCenter = 0
	private var tileTextureId = 0

	private var groundVao = 0
	private var groundIndexCount = 0
	private var tileDiskVao = 0
	private var tileDiskIndexCount = 0
	private var mosqueVao = 0
	private var mosqueIndexCount = 0
	private var domeVao = 0
	private var domeIndexCount = 0
	private var minaretVao = 0
	private var minaretIndexCount = 0
	private var minaretSpireVao = 0
	private var minaretSpireIndexCount = 0

	private val model = FloatArray(16)
	private val mvp = FloatArray(16)

	override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
		GLES30.glClearColor(0.01f, 0.02f, 0.06f, 1f)
		GLES30.glDisable(GLES30.GL_DITHER)
		GLES30.glEnable(GLES30.GL_DEPTH_TEST)
		GLES30.glDepthFunc(GLES30.GL_LEQUAL)
		// Disable face culling to avoid winding/billboard basis issues on different devices.
		GLES30.glDisable(GLES30.GL_CULL_FACE)

		skyProgram = buildSkyBackgroundProgram()
		sky_uTime = GLES30.glGetUniformLocation(skyProgram, "uTime")

		solidProgram = buildSolidProgram()
		solid_uMvp = GLES30.glGetUniformLocation(solidProgram, "uMVP")
		solid_uModel = GLES30.glGetUniformLocation(solidProgram, "uModel")
		solid_uLightDir = GLES30.glGetUniformLocation(solidProgram, "uLightDir")
		solid_uBaseColor = GLES30.glGetUniformLocation(solidProgram, "uBaseColor")
		solid_uTime = GLES30.glGetUniformLocation(solidProgram, "uTime")

		tileProgram = buildTileProgram()
		tile_uMvp = GLES30.glGetUniformLocation(tileProgram, "uMVP")
		tile_uModel = GLES30.glGetUniformLocation(tileProgram, "uModel")
		tile_uLightDir = GLES30.glGetUniformLocation(tileProgram, "uLightDir")
		tile_uTex = GLES30.glGetUniformLocation(tileProgram, "uTex")
		tile_uUvScale = GLES30.glGetUniformLocation(tileProgram, "uUvScale")
		tile_uCenter = GLES30.glGetUniformLocation(tileProgram, "uCenter")
		tileTextureId = createTileTexture()

		val ground = buildGroundMesh(size = 70f, segments = 160)
		groundVao = ground.vao
		groundIndexCount = ground.indexCount

		val disk = buildDiskMesh(radius = 12.5f, segments = 120)
		tileDiskVao = disk.vao
		tileDiskIndexCount = disk.indexCount

		val mosque = buildBoxMesh(width = 12f, height = 6.0f, depth = 18f)
		mosqueVao = mosque.vao
		mosqueIndexCount = mosque.indexCount

		val dome = buildDomeMesh(radius = 4.2f, stacks = 14, slices = 28)
		domeVao = dome.vao
		domeIndexCount = dome.indexCount

		val minaret = buildBoxMesh(width = 2.2f, height = 14.0f, depth = 2.2f)
		minaretVao = minaret.vao
		minaretIndexCount = minaret.indexCount

		val spire = buildPyramidMesh(base = 2.4f, height = 3.2f)
		minaretSpireVao = spire.vao
		minaretSpireIndexCount = spire.indexCount

		lastFrameNanos = System.nanoTime()
	}

	override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
		GLES30.glViewport(0, 0, width, height)
		val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
		Matrix.perspectiveM(projection, 0, 50f, aspect, 0.1f, 500f)
	}

	override fun onDrawFrame(gl: GL10?) {
		val now = System.nanoTime()
		val delta = ((now - lastFrameNanos).coerceAtLeast(0L)).toFloat() / 1_000_000_000f
		lastFrameNanos = now

		cameraController.update(delta)
		timeSeconds += delta

		val snap = cameraController.snapshot()
		Matrix.multiplyMM(viewProj, 0, projection, 0, snap.viewMatrix, 0)

		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

		// Full-screen starry sky background (fills the whole sky area).
		GLES30.glDisable(GLES30.GL_DEPTH_TEST)
		GLES30.glDisable(GLES30.GL_BLEND)
		GLES30.glUseProgram(skyProgram)
		GLES30.glUniform1f(sky_uTime, timeSeconds)
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
		GLES30.glEnable(GLES30.GL_DEPTH_TEST)

		// Ground: green grass plane.
		GLES30.glUseProgram(solidProgram)
		GLES30.glUniform1f(solid_uTime, timeSeconds)
		GLES30.glBindVertexArray(groundVao)
		Matrix.setIdentityM(model, 0)
		Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
		GLES30.glUniformMatrix4fv(solid_uMvp, 1, false, mvp, 0)
		GLES30.glUniformMatrix4fv(solid_uModel, 1, false, model, 0)
		GLES30.glUniform3f(solid_uLightDir, -0.20f, -1.0f, -0.35f)
		GLES30.glUniform4f(solid_uBaseColor, 0.11f, 0.55f, 0.20f, 1f)
		GLES30.glDrawElements(GLES30.GL_TRIANGLES, groundIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
		GLES30.glBindVertexArray(0)

		// Mosque: a simple rectangular box + dome a little further away.
		val mosqueZ = -10.0f

		// Tiled circular area around the mosque (draw slightly above grass).
		GLES30.glUseProgram(tileProgram)
		GLES30.glBindVertexArray(tileDiskVao)
		Matrix.setIdentityM(model, 0)
		Matrix.translateM(model, 0, 0f, 0.03f, mosqueZ)
		Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
		GLES30.glUniformMatrix4fv(tile_uMvp, 1, false, mvp, 0)
		GLES30.glUniformMatrix4fv(tile_uModel, 1, false, model, 0)
		GLES30.glUniform3f(tile_uLightDir, -0.20f, -1.0f, -0.35f)
		// Tile density: larger value => more repeats.
		GLES30.glUniform1f(tile_uUvScale, 0.55f)
		GLES30.glUniform2f(tile_uCenter, 0f, mosqueZ)
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tileTextureId)
		GLES30.glUniform1i(tile_uTex, 0)
		GLES30.glDrawElements(GLES30.GL_TRIANGLES, tileDiskIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
		GLES30.glBindVertexArray(0)
		GLES30.glUseProgram(solidProgram)
		GLES30.glUniform1f(solid_uTime, timeSeconds)
		GLES30.glBindVertexArray(mosqueVao)
		Matrix.setIdentityM(model, 0)
		Matrix.translateM(model, 0, 0f, 3.0f, mosqueZ)
		Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
		GLES30.glUniformMatrix4fv(solid_uMvp, 1, false, mvp, 0)
		GLES30.glUniformMatrix4fv(solid_uModel, 1, false, model, 0)
		GLES30.glUniform3f(solid_uLightDir, -0.20f, -1.0f, -0.35f)
		GLES30.glUniform4f(solid_uBaseColor, 0.78f, 0.79f, 0.76f, 1f)
		GLES30.glDrawElements(GLES30.GL_TRIANGLES, mosqueIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
		GLES30.glBindVertexArray(0)

		// Minaret: tall thin tower beside the mosque.
		GLES30.glUseProgram(solidProgram)
		GLES30.glUniform1f(solid_uTime, timeSeconds)
		GLES30.glBindVertexArray(minaretVao)
		Matrix.setIdentityM(model, 0)
		// Minaret height is 14, so center y=7 to sit on ground.
		// Pull it closer to the dome/roof area.
		val minaretX = 5.0f
		val minaretZ = mosqueZ + 1.5f
		Matrix.translateM(model, 0, minaretX, 7.0f, minaretZ)
		Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
		GLES30.glUniformMatrix4fv(solid_uMvp, 1, false, mvp, 0)
		GLES30.glUniformMatrix4fv(solid_uModel, 1, false, model, 0)
		GLES30.glUniform3f(solid_uLightDir, -0.20f, -1.0f, -0.35f)
		GLES30.glUniform4f(solid_uBaseColor, 0.74f, 0.75f, 0.72f, 1f)
		GLES30.glDrawElements(GLES30.GL_TRIANGLES, minaretIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
		GLES30.glBindVertexArray(0)

		// Minaret spire: pointed pyramid cap.
		GLES30.glUseProgram(solidProgram)
		GLES30.glUniform1f(solid_uTime, timeSeconds)
		GLES30.glBindVertexArray(minaretSpireVao)
		Matrix.setIdentityM(model, 0)
		// Minaret top is at y = 14 (ground at y=0).
		Matrix.translateM(model, 0, minaretX, 14.0f, minaretZ)
		Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
		GLES30.glUniformMatrix4fv(solid_uMvp, 1, false, mvp, 0)
		GLES30.glUniformMatrix4fv(solid_uModel, 1, false, model, 0)
		GLES30.glUniform3f(solid_uLightDir, -0.20f, -1.0f, -0.35f)
		GLES30.glUniform4f(solid_uBaseColor, 0.68f, 0.69f, 0.66f, 1f)
		GLES30.glDrawElements(GLES30.GL_TRIANGLES, minaretSpireIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
		GLES30.glBindVertexArray(0)

		// Dome (hemisphere) sits on the roof.
		GLES30.glUseProgram(solidProgram)
		GLES30.glUniform1f(solid_uTime, timeSeconds)
		GLES30.glBindVertexArray(domeVao)
		Matrix.setIdentityM(model, 0)
		// Box top is at y = 3 + 3 = 6. Dome mesh base is at y=0.
		Matrix.translateM(model, 0, 0f, 6.0f, mosqueZ)
		Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
		GLES30.glUniformMatrix4fv(solid_uMvp, 1, false, mvp, 0)
		GLES30.glUniformMatrix4fv(solid_uModel, 1, false, model, 0)
		GLES30.glUniform3f(solid_uLightDir, -0.20f, -1.0f, -0.35f)
		GLES30.glUniform4f(solid_uBaseColor, 0.70f, 0.71f, 0.68f, 1f)
		GLES30.glDrawElements(GLES30.GL_TRIANGLES, domeIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
		GLES30.glBindVertexArray(0)

		// (Stars are now rendered as a full-screen background; no separate star field pass.)
	}

	private data class MeshHandle(val vao: Int, val indexCount: Int)

	private fun buildGroundMesh(size: Float, segments: Int): MeshHandle {
		val seg = segments.coerceIn(8, 200)
		val hs = size * 0.5f
		val vertsPerSide = seg + 1
		val vertexCount = vertsPerSide * vertsPerSide
		val vertices = FloatArray(vertexCount * 6)
		var vo = 0
		for (z in 0..seg) {
			val fz = z.toFloat() / seg.toFloat()
			val pz = (-hs + fz * size)
			for (x in 0..seg) {
				val fx = x.toFloat() / seg.toFloat()
				val px = (-hs + fx * size)
				// pos
				vertices[vo++] = px
				vertices[vo++] = 0f
				vertices[vo++] = pz
				// normal (will be refined in shader)
				vertices[vo++] = 0f
				vertices[vo++] = 1f
				vertices[vo++] = 0f
			}
		}

		val indices = ShortArray(seg * seg * 6)
		var io = 0
		fun idx(x: Int, z: Int): Int = z * vertsPerSide + x
		for (z in 0 until seg) {
			for (x in 0 until seg) {
				val i0 = idx(x, z)
				val i1 = idx(x + 1, z)
				val i2 = idx(x + 1, z + 1)
				val i3 = idx(x, z + 1)
				indices[io++] = i0.toShort()
				indices[io++] = i1.toShort()
				indices[io++] = i2.toShort()
				indices[io++] = i0.toShort()
				indices[io++] = i2.toShort()
				indices[io++] = i3.toShort()
			}
		}

		return buildMesh(vertices, indices)
	}

	private fun buildDiskMesh(radius: Float, segments: Int): MeshHandle {
		val seg = segments.coerceIn(12, 360)
		// Center + ring vertices.
		val vertexCount = 1 + (seg + 1)
		val vertices = FloatArray(vertexCount * 6)
		var vo = 0
		// Center
		vertices[vo++] = 0f
		vertices[vo++] = 0f
		vertices[vo++] = 0f
		vertices[vo++] = 0f
		vertices[vo++] = 1f
		vertices[vo++] = 0f
		// Ring
		for (i in 0..seg) {
			val a = (i.toFloat() / seg.toFloat()) * (Math.PI.toFloat() * 2f)
			val x = kotlin.math.cos(a) * radius
			val z = kotlin.math.sin(a) * radius
			vertices[vo++] = x
			vertices[vo++] = 0f
			vertices[vo++] = z
			vertices[vo++] = 0f
			vertices[vo++] = 1f
			vertices[vo++] = 0f
		}

		val indices = ShortArray(seg * 3)
		var io = 0
		for (i in 0 until seg) {
			indices[io++] = 0
			indices[io++] = (1 + i).toShort()
			indices[io++] = (1 + i + 1).toShort()
		}
		return buildMesh(vertices, indices)
	}

	private fun buildBoxMesh(width: Float, height: Float, depth: Float): MeshHandle {
		val hw = width * 0.5f
		val hh = height * 0.5f
		val hd = depth * 0.5f

		val v = FloatArray(24 * 6)
		var o = 0
		fun put(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float) {
			v[o++] = px; v[o++] = py; v[o++] = pz
			v[o++] = nx; v[o++] = ny; v[o++] = nz
		}

		// +Z
		put(-hw, -hh, hd, 0f, 0f, 1f)
		put(hw, -hh, hd, 0f, 0f, 1f)
		put(hw, hh, hd, 0f, 0f, 1f)
		put(-hw, hh, hd, 0f, 0f, 1f)
		// -Z
		put(hw, -hh, -hd, 0f, 0f, -1f)
		put(-hw, -hh, -hd, 0f, 0f, -1f)
		put(-hw, hh, -hd, 0f, 0f, -1f)
		put(hw, hh, -hd, 0f, 0f, -1f)
		// +X
		put(hw, -hh, hd, 1f, 0f, 0f)
		put(hw, -hh, -hd, 1f, 0f, 0f)
		put(hw, hh, -hd, 1f, 0f, 0f)
		put(hw, hh, hd, 1f, 0f, 0f)
		// -X
		put(-hw, -hh, -hd, -1f, 0f, 0f)
		put(-hw, -hh, hd, -1f, 0f, 0f)
		put(-hw, hh, hd, -1f, 0f, 0f)
		put(-hw, hh, -hd, -1f, 0f, 0f)
		// +Y
		put(-hw, hh, hd, 0f, 1f, 0f)
		put(hw, hh, hd, 0f, 1f, 0f)
		put(hw, hh, -hd, 0f, 1f, 0f)
		put(-hw, hh, -hd, 0f, 1f, 0f)
		// -Y
		put(-hw, -hh, -hd, 0f, -1f, 0f)
		put(hw, -hh, -hd, 0f, -1f, 0f)
		put(hw, -hh, hd, 0f, -1f, 0f)
		put(-hw, -hh, hd, 0f, -1f, 0f)

		val idx = shortArrayOf(
			0, 1, 2, 0, 2, 3,
			4, 5, 6, 4, 6, 7,
			8, 9, 10, 8, 10, 11,
			12, 13, 14, 12, 14, 15,
			16, 17, 18, 16, 18, 19,
			20, 21, 22, 20, 22, 23,
		)

		return buildMesh(v, idx)
	}

	private fun buildDomeMesh(radius: Float, stacks: Int, slices: Int): MeshHandle {
		val st = stacks.coerceIn(4, 32)
		val sl = slices.coerceIn(8, 64)
		val vertexCount = (st + 1) * (sl + 1)
		val vertices = FloatArray(vertexCount * 6)
		var vo = 0
		for (i in 0..st) {
			val v = i.toFloat() / st.toFloat() // 0..1
			val phi = (v * (Math.PI / 2.0)).toFloat() // 0..pi/2
			val sp = kotlin.math.sin(phi)
			val cp = kotlin.math.cos(phi)
			for (j in 0..sl) {
				val u = j.toFloat() / sl.toFloat() // 0..1
				val theta = (u * (Math.PI * 2.0)).toFloat()
				val ct = kotlin.math.cos(theta)
				val stn = kotlin.math.sin(theta)
				val x = (sp * ct) * radius
				val y = (cp) * radius
				val z = (sp * stn) * radius
				// position
				vertices[vo++] = x
				vertices[vo++] = y
				vertices[vo++] = z
				// normal (sphere)
				val invLen = 1f / radius
				vertices[vo++] = x * invLen
				vertices[vo++] = y * invLen
				vertices[vo++] = z * invLen
			}
		}

		val indexCount = st * sl * 6
		val indices = ShortArray(indexCount)
		var io = 0
		fun idx(i: Int, j: Int) = (i * (sl + 1) + j).toShort()
		for (i in 0 until st) {
			for (j in 0 until sl) {
				val i0 = idx(i, j)
				val i1 = idx(i + 1, j)
				val i2 = idx(i + 1, j + 1)
				val i3 = idx(i, j + 1)
				indices[io++] = i0
				indices[io++] = i1
				indices[io++] = i2
				indices[io++] = i0
				indices[io++] = i2
				indices[io++] = i3
			}
		}

		return buildMesh(vertices, indices)
	}

	private fun buildPyramidMesh(base: Float, height: Float): MeshHandle {
		val hb = base * 0.5f
		val p0 = floatArrayOf(-hb, 0f, -hb)
		val p1 = floatArrayOf(hb, 0f, -hb)
		val p2 = floatArrayOf(hb, 0f, hb)
		val p3 = floatArrayOf(-hb, 0f, hb)
		val tip = floatArrayOf(0f, height, 0f)

		// 4 side triangles + 2 base triangles = 18 vertices.
		val vertices = FloatArray(18 * 6)
		var vo = 0
		fun addTri(a: FloatArray, b: FloatArray, c: FloatArray) {
			val n = computeNormal(a, b, c)
			vo = putVertex(vertices, vo, a, n)
			vo = putVertex(vertices, vo, b, n)
			vo = putVertex(vertices, vo, c, n)
		}

		// Side faces.
		addTri(p0, p1, tip)
		addTri(p1, p2, tip)
		addTri(p2, p3, tip)
		addTri(p3, p0, tip)
		// Base.
		addTri(p0, p2, p1)
		addTri(p0, p3, p2)

		val indices = ShortArray(18) { it.toShort() }
		return buildMesh(vertices, indices)
	}

	private fun putVertex(dst: FloatArray, offset: Int, p: FloatArray, n: FloatArray): Int {
		var o = offset
		dst[o++] = p[0]
		dst[o++] = p[1]
		dst[o++] = p[2]
		dst[o++] = n[0]
		dst[o++] = n[1]
		dst[o++] = n[2]
		return o
	}

	private fun computeNormal(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
		val ux = b[0] - a[0]
		val uy = b[1] - a[1]
		val uz = b[2] - a[2]
		val vx = c[0] - a[0]
		val vy = c[1] - a[1]
		val vz = c[2] - a[2]
		var nx = uy * vz - uz * vy
		var ny = uz * vx - ux * vz
		var nz = ux * vy - uy * vx
		val len = sqrt(nx * nx + ny * ny + nz * nz)
		if (len > 1e-6f) {
			nx /= len
			ny /= len
			nz /= len
		} else {
			nx = 0f
			ny = 1f
			nz = 0f
		}
		return floatArrayOf(nx, ny, nz)
	}

	private fun buildMesh(vertices: FloatArray, indices: ShortArray): MeshHandle {
		val vaos = IntArray(1)
		val vbos = IntArray(1)
		val ebos = IntArray(1)
		GLES30.glGenVertexArrays(1, vaos, 0)
		GLES30.glGenBuffers(1, vbos, 0)
		GLES30.glGenBuffers(1, ebos, 0)

		val vao = vaos[0]
		val vbo = vbos[0]
		val ebo = ebos[0]

		GLES30.glBindVertexArray(vao)

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
		GLES30.glBufferData(
			GLES30.GL_ARRAY_BUFFER,
			vertices.size * 4,
			vertices.toFloatBuffer(),
			GLES30.GL_STATIC_DRAW,
		)

		GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
		GLES30.glBufferData(
			GLES30.GL_ELEMENT_ARRAY_BUFFER,
			indices.size * 2,
			indices.toShortBuffer(),
			GLES30.GL_STATIC_DRAW,
		)

		val stride = 6 * 4
		GLES30.glEnableVertexAttribArray(0)
		GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
		GLES30.glEnableVertexAttribArray(1)
		GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)

		GLES30.glBindVertexArray(0)
		return MeshHandle(vao = vao, indexCount = indices.size)
	}

	private fun buildSkyBackgroundProgram(): Int {
		val vs = GlUtil.compileShader(
			GLES30.GL_VERTEX_SHADER,
			"""
				#version 300 es
				out vec2 vUv;
				void main() {
					// Full-screen triangle.
					vec2 p;
					if (gl_VertexID == 0) p = vec2(-1.0, -1.0);
					else if (gl_VertexID == 1) p = vec2(3.0, -1.0);
					else p = vec2(-1.0, 3.0);
					gl_Position = vec4(p, 0.0, 1.0);
					vUv = p * 0.5 + 0.5;
				}
			""".trimIndent(),
		)

		val fs = GlUtil.compileShader(
			GLES30.GL_FRAGMENT_SHADER,
			"""
				#version 300 es
				precision highp float;
				uniform float uTime;
				in vec2 vUv;
				out vec4 frag;

				float hash21(vec2 p) {
					p = fract(p * vec2(123.34, 345.45));
					p += dot(p, p + 34.345);
					return fract(p.x * p.y);
				}

				vec3 starLayer(vec2 uv, float scale, float density, float brightness) {
					vec2 p = uv * scale;
					vec2 ip = floor(p);
					vec2 fp = fract(p);
					float rnd = hash21(ip);
					float star = step(1.0 - density, rnd);
					// Place star near cell center.
					vec2 c = vec2(hash21(ip + 1.3), hash21(ip + 2.1));
					vec2 d = fp - c;
					float r = length(d);
					float core = exp(-r * r * 90.0);
					float tw = 0.65 + 0.35 * sin(uTime * (0.35 + rnd * 1.4) + rnd * 20.0);
					float a = star * core * tw;
					return vec3(a) * brightness;
				}

				void main() {
					// Slow drift.
					vec2 uv = vUv;
					uv += vec2(uTime * 0.0025, uTime * 0.0018);

					// Base sky gradient.
					vec3 top = vec3(0.01, 0.02, 0.06);
					vec3 bot = vec3(0.00, 0.00, 0.02);
					vec3 col = mix(bot, top, smoothstep(0.0, 0.9, vUv.y));

					// Multi-scale star layers.
					col += starLayer(uv, 120.0, 0.012, 1.40) * vec3(0.90, 0.95, 1.00);
					col += starLayer(uv, 70.0, 0.020, 1.10) * vec3(0.80, 0.90, 1.00);
					col += starLayer(uv, 35.0, 0.030, 0.85) * vec3(1.00, 0.92, 0.85);

					col = clamp(col, 0.0, 1.0);
					frag = vec4(col, 1.0);
				}
			""".trimIndent(),
		)

		val program = GlUtil.linkProgram(vs, fs)
		GLES30.glDeleteShader(vs)
		GLES30.glDeleteShader(fs)
		return program
	}

	private fun buildSolidProgram(): Int {
		val vs = GlUtil.compileShader(
			GLES30.GL_VERTEX_SHADER,
			"""
				#version 300 es
				layout(location=0) in vec3 aPos;
				layout(location=1) in vec3 aNormal;
				uniform mat4 uMVP;
				uniform mat4 uModel;
				uniform float uTime;
				out vec3 vWorldPos;
				out vec3 vWorldNormal;
				out float vGrassH;

				float hash21(vec2 p) {
					p = fract(p * vec2(123.34, 345.45));
					p += dot(p, p + 34.345);
					return fract(p.x * p.y);
				}
				float noise(vec2 p) {
					vec2 i = floor(p);
					vec2 f = fract(p);
					float a = hash21(i);
					float b = hash21(i + vec2(1.0, 0.0));
					float c = hash21(i + vec2(0.0, 1.0));
					float d = hash21(i + vec2(1.0, 1.0));
					vec2 u = f * f * (3.0 - 2.0 * f);
					return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
				}
				float fbm(vec2 p) {
					float s = 0.0;
					float a = 0.5;
					for (int i = 0; i < 4; i++) {
						s += a * noise(p);
						p *= 2.02;
						a *= 0.5;
					}
					return s;
				}

				float grassHeight(vec2 xz) {
					// Gentle unevenness + wind ripples.
					float n = fbm(xz * 0.12);
					float w = sin(xz.x * 0.55 + uTime * 0.8) * sin(xz.y * 0.50 - uTime * 0.65);
					return 0.18 * (n - 0.5) + 0.08 * w;
				}
				void main() {
					// Only displace the ground plane (y=0 with an up normal).
					float isGround = step(0.95, aNormal.y) * (1.0 - step(0.001, abs(aPos.y)));
					vec3 p = aPos;
					float h = 0.0;
					vec3 nrm = aNormal;
					if (isGround > 0.5) {
						h = grassHeight(p.xz);
						p.y += h;

						// Approximate normal from height field.
						float e = 0.35;
						float hx = grassHeight(p.xz + vec2(e, 0.0));
						float hz = grassHeight(p.xz + vec2(0.0, e));
						vec3 tx = normalize(vec3(e, hx - h, 0.0));
						vec3 tz = normalize(vec3(0.0, hz - h, e));
						nrm = normalize(cross(tz, tx));
					}

					vec4 wp = uModel * vec4(p, 1.0);
					vWorldPos = wp.xyz;
					vWorldNormal = mat3(uModel) * nrm;
					vGrassH = h;
					gl_Position = uMVP * vec4(p, 1.0);
				}
			""".trimIndent(),
		)

		val fs = GlUtil.compileShader(
			GLES30.GL_FRAGMENT_SHADER,
			"""
				#version 300 es
				precision mediump float;
				uniform vec3 uLightDir;
				uniform vec4 uBaseColor;
				in vec3 vWorldPos;
				in vec3 vWorldNormal;
				in float vGrassH;
				out vec4 frag;
				float hash21(vec2 p) {
					p = fract(p * vec2(123.34, 345.45));
					p += dot(p, p + 34.345);
					return fract(p.x * p.y);
				}
				float noise(vec2 p) {
					vec2 i = floor(p);
					vec2 f = fract(p);
					float a = hash21(i);
					float b = hash21(i + vec2(1.0, 0.0));
					float c = hash21(i + vec2(0.0, 1.0));
					float d = hash21(i + vec2(1.0, 1.0));
					vec2 u = f * f * (3.0 - 2.0 * f);
					return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
				}
				float fbm(vec2 p) {
					float s = 0.0;
					float a = 0.5;
					for (int i = 0; i < 5; i++) {
						s += a * noise(p);
						p *= 2.03;
						a *= 0.5;
					}
					return s;
				}
				void main() {
					vec3 n = normalize(vWorldNormal);
					vec3 l = normalize(-uLightDir);
					float ndl = max(dot(n, l), 0.0);
					float ambient = 0.35;
					vec3 color = uBaseColor.rgb;

					// More realistic grass variation when surface is mostly facing up.
					if (abs(n.y) > 0.86) {
						vec2 xz = vWorldPos.xz;
						float patchN = fbm(xz * 0.10);
						float fine = fbm(xz * 0.65);
						float blades = 0.5 + 0.5 * sin(xz.x * 7.5 + fine * 3.0) * sin(xz.y * 8.2 + fine * 2.0);
						float heightShade = clamp(0.5 + vGrassH * 2.4, 0.0, 1.0);

						vec3 deep = vec3(0.06, 0.30, 0.10);
						vec3 bright = vec3(0.16, 0.62, 0.22);
						color = mix(deep, bright, patchN);
						color = mix(color, color + vec3(0.06, 0.08, 0.02), blades * 0.35);
						color *= mix(0.85, 1.10, heightShade);
					}

					vec3 lit = color * (ambient + 0.75 * ndl);
					frag = vec4(lit, uBaseColor.a);
				}
			""".trimIndent(),
		)

		val program = GlUtil.linkProgram(vs, fs)
		GLES30.glDeleteShader(vs)
		GLES30.glDeleteShader(fs)
		return program
	}

	private fun buildTileProgram(): Int {
		val vs = GlUtil.compileShader(
			GLES30.GL_VERTEX_SHADER,
			"""
				#version 300 es
				layout(location=0) in vec3 aPos;
				layout(location=1) in vec3 aNormal;
				uniform mat4 uMVP;
				uniform mat4 uModel;
				out vec3 vWorldPos;
				out vec3 vWorldNormal;
				void main() {
					vec4 wp = uModel * vec4(aPos, 1.0);
					vWorldPos = wp.xyz;
					vWorldNormal = mat3(uModel) * aNormal;
					gl_Position = uMVP * vec4(aPos, 1.0);
				}
			""".trimIndent(),
		)

		val fs = GlUtil.compileShader(
			GLES30.GL_FRAGMENT_SHADER,
			"""
				#version 300 es
				precision mediump float;
				uniform vec3 uLightDir;
				uniform sampler2D uTex;
				uniform float uUvScale;
				uniform vec2 uCenter;
				in vec3 vWorldPos;
				in vec3 vWorldNormal;
				out vec4 frag;
				void main() {
					vec3 n = normalize(vWorldNormal);
					vec3 l = normalize(-uLightDir);
					float ndl = max(dot(n, l), 0.0);
					float ambient = 0.30;

					vec2 uv = (vWorldPos.xz - uCenter) * uUvScale;
					vec3 tex = texture(uTex, uv).rgb;
					vec3 lit = tex * (ambient + 0.80 * ndl);
					frag = vec4(lit, 1.0);
				}
			""".trimIndent(),
		)

		val program = GlUtil.linkProgram(vs, fs)
		GLES30.glDeleteShader(vs)
		GLES30.glDeleteShader(fs)
		return program
	}

	private fun createTileTexture(): Int {
		// Simple procedural tiled texture (no external assets).
		val size = 256
		val tiles = 8
		val tileSize = size / tiles
		val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bmp)
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		val grout = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5B5E63.toInt() }

		canvas.drawColor(0xFF505257.toInt())
		for (y in 0 until tiles) {
			for (x in 0 until tiles) {
				val even = ((x + y) and 1) == 0
				paint.color = if (even) 0xFF9FA4AB.toInt() else 0xFF7F848C.toInt()
				val left = x * tileSize
				val top = y * tileSize
				canvas.drawRect(
					(left + 2).toFloat(),
					(top + 2).toFloat(),
					(left + tileSize - 2).toFloat(),
					(top + tileSize - 2).toFloat(),
					paint,
				)
			}
		}
		// Grout lines
		for (i in 0..tiles) {
			val p = (i * tileSize).toFloat()
			canvas.drawRect(p - 1f, 0f, p + 1f, size.toFloat(), grout)
			canvas.drawRect(0f, p - 1f, size.toFloat(), p + 1f, grout)
		}

		val tex = IntArray(1)
		GLES30.glGenTextures(1, tex, 0)
		val id = tex[0]
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
		GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
		GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
		bmp.recycle()
		return id
	}
}

private fun FloatArray.toFloatBuffer() =
	ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
		put(this@toFloatBuffer)
		position(0)
	}

private fun ShortArray.toShortBuffer() =
	ByteBuffer.allocateDirect(size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
		put(this@toShortBuffer)
		position(0)
	}
