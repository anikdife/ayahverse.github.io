package com.ayahverse.quran.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity3 : Activity() {
	private lateinit var glView: SphereNetworkGLSurfaceView
	private lateinit var overlay: NodeLabelsOverlayView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		overlay = NodeLabelsOverlayView(this)
		val renderer = SphereNetworkRenderer { projectedNodes ->
			overlay.updateProjectedNodes(projectedNodes)
		}
		glView = SphereNetworkGLSurfaceView(this, renderer)

		val root = FrameLayout(this)
		root.addView(
			glView,
			FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			),
		)
		root.addView(
			overlay,
			FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			),
		)
		setContentView(root)
	}

	override fun onResume() {
		super.onResume()
		glView.onResume()
	}

	override fun onPause() {
		glView.onPause()
		super.onPause()
	}
}

private class SphereNetworkGLSurfaceView(
	context: Context,
	private val renderer: SphereNetworkRenderer,
) : GLSurfaceView(context) {
	private var lastX = 0f
	private var lastY = 0f
	private var dragging = false
	private val rotationSensitivity = 0.25f // degrees per pixel

	init {
		setEGLContextClientVersion(3)
		setRenderer(renderer)
		renderMode = RENDERMODE_CONTINUOUSLY
		preserveEGLContextOnPause = true
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				dragging = true
				lastX = event.x
				lastY = event.y
				return true
			}
			MotionEvent.ACTION_MOVE -> {
				if (!dragging) return true
				val x = event.x
				val y = event.y
				val dx = x - lastX
				val dy = y - lastY
				lastX = x
				lastY = y
				val yawDelta = dx * rotationSensitivity
				val pitchDelta = dy * rotationSensitivity
				queueEvent {
					renderer.onUserDrag(yawDeltaDeg = yawDelta, pitchDeltaDeg = pitchDelta)
				}
				return true
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				dragging = false
				return true
			}
			else -> return true
		}
	}
}

private class NodeLabelsOverlayView(context: Context) : View(context) {
	private val lock = Any()
	private val projected = FloatArray(NODES_COUNT * 3) // xPx, yPx, visible(0/1)
	private val labels: Array<String> = Array(NODES_COUNT) { i -> "${i + 1}. ${SURA_NAMES[i]}" }

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		alpha = 230
		textSize = spToPx(10f)
	}

	fun updateProjectedNodes(projectedNodes: FloatArray) {
		// Called from GL thread; copy into local buffer for UI thread draw.
		synchronized(lock) {
			val n = minOf(projected.size, projectedNodes.size)
			for (i in 0 until n) projected[i] = projectedNodes[i]
		}
		postInvalidateOnAnimation()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val local = FloatArray(projected.size)
		synchronized(lock) {
			for (i in local.indices) local[i] = projected[i]
		}

		val xOff = dpToPx(6f)
		val yOff = dpToPx(4f)
		for (i in 0 until NODES_COUNT) {
			val o = i * 3
			if (local[o + 2] < 0.5f) continue
			val x = local[o] + xOff
			val y = local[o + 1] - yOff
			if (x < 0f || x > width.toFloat() || y < 0f || y > height.toFloat()) continue
			canvas.drawText(labels[i], x, y, paint)
		}
	}

	private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
	private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity

	private companion object {
		private const val NODES_COUNT = 114
	}
}

private class SphereNetworkRenderer(
	private val onProjectedNodes: ((FloatArray) -> Unit)? = null,
) : GLSurfaceView.Renderer {
	private val rand = Random(114)

	private val starsCount = 1800
	private val meteorsMax = 6
	private val nodesCount = 114
	private val sphereRadius = 2.9f
	private val cameraZ = 13.5f
	private val skyRotationDegPerSec = 0.06f
	private val sphereRotationDegPerSec = 0.7f

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
	private lateinit var nodesBuffer: FloatBuffer
	private lateinit var linesBuffer: FloatBuffer
	private var linesVertexCount = 0
	private lateinit var nodesModel: FloatArray
	private val projectedNodes = FloatArray(nodesCount * 3)
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
	private val temp = FloatArray(16)
	private val v4 = FloatArray(4)
	private val clip = FloatArray(4)

	private var width = 1
	private var height = 1
	private var t = 0f
	private var skyAngle = 0f
	private var startTimeMs: Long = 0L
	private var lastFrameMs: Long = 0L
	private var userYaw = 0f
	private var userPitch = 0f

	override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
		GLES30.glClearColor(0f, 0f, 0f, 1f)
		GLES30.glEnable(GLES30.GL_DEPTH_TEST)
		GLES30.glEnable(GLES30.GL_BLEND)
		GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

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
		GLES30.glViewport(0, 0, width, height)

		val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
		Matrix.perspectiveM(proj, 0, 55f, aspect, 0.1f, 100f)
		Matrix.setLookAtM(view, 0, 0f, 0f, cameraZ, 0f, 0f, 0f, 0f, 1f, 0f)
		Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
	}

	override fun onDrawFrame(gl: GL10?) {
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
		GLES30.glUseProgram(program)

		val nowMs = SystemClock.uptimeMillis()
		val dt = ((nowMs - lastFrameMs).coerceAtLeast(1L)) / 1000f
		lastFrameMs = nowMs
		val timeSec = (nowMs - startTimeMs) / 1000f
		GLES30.glUniform1f(uTime, timeSec)

		// Stars (background)
		skyAngle += dt * skyRotationDegPerSec
		Matrix.setIdentityM(model, 0)
		Matrix.rotateM(model, 0, skyAngle, 0f, 1f, 0f)
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

		// Sphere network (foreground)
		t += dt * sphereRotationDegPerSec
		Matrix.setIdentityM(model, 0)
		Matrix.rotateM(model, 0, userYaw + t, 0f, 1f, 0f)
		Matrix.rotateM(model, 0, userPitch + t * 0.35f, 1f, 0f, 0f)
		Matrix.multiplyMM(temp, 0, vp, 0, model, 0)

		// Lines
		drawLines(
			linesBuffer,
			linesVertexCount,
			temp,
			colorR = 0.8f,
			colorG = 0.95f,
			colorB = 1f,
			colorA = 0.9f,
			lineWidth = 3.0f,
			flickerAmount = 0f,
		)
		// Nodes
		drawPoints(
			nodesBuffer,
			nodesCount,
			temp,
			colorR = 1f,
			colorG = 1f,
			colorB = 1f,
			colorA = 1f,
			pointSize = 9.5f,
			flickerAmount = 0f,
		)

		updateProjectedNodes(temp)
	}

	fun onUserDrag(yawDeltaDeg: Float, pitchDeltaDeg: Float) {
		userYaw += yawDeltaDeg
		userPitch += pitchDeltaDeg
		userPitch = userPitch.coerceIn(-85f, 85f)
	}

	private fun updateProjectedNodes(mvpMatrix: FloatArray) {
		if (!::nodesModel.isInitialized) return
		if (width <= 0 || height <= 0) return
		for (i in 0 until nodesCount) {
			val o = i * 3
			v4[0] = nodesModel[o]
			v4[1] = nodesModel[o + 1]
			v4[2] = nodesModel[o + 2]
			v4[3] = 1f

			Matrix.multiplyMV(clip, 0, mvpMatrix, 0, v4, 0)
			val w = clip[3]
			val out = i * 3
			if (w <= 0.0001f) {
				projectedNodes[out + 2] = 0f
				continue
			}
			val ndcX = clip[0] / w
			val ndcY = clip[1] / w
			val ndcZ = clip[2] / w
			val visible = ndcX >= -1f && ndcX <= 1f && ndcY >= -1f && ndcY <= 1f && ndcZ >= -1f && ndcZ <= 1f
			if (!visible) {
				projectedNodes[out + 2] = 0f
				continue
			}
			projectedNodes[out] = (ndcX * 0.5f + 0.5f) * width.toFloat()
			projectedNodes[out + 1] = (1f - (ndcY * 0.5f + 0.5f)) * height.toFloat()
			projectedNodes[out + 2] = 1f
		}
		onProjectedNodes?.invoke(projectedNodes)
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
					val len = kotlin.math.sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.0001f)
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
			if (meteorLife[i] <= 0f || meteorHead[i * 3 + 1] < -26f || kotlin.math.abs(meteorHead[i * 3]) > 70f) {
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
			val len = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.0001f)
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
			// Scatter stars in a large volume behind the sphere.
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

		val nodesUnit = Array(nodesCount) { FloatArray(3) }
		val nodes = FloatArray(nodesCount * 3)
		for (i in 0 until nodesCount) {
			// Uniform-ish random direction on a sphere.
			val u = rand.nextFloat() * 2f - 1f
			val phi = rand.nextFloat() * (2f * PI.toFloat())
			val r = sqrt(1f - u * u)
			val x = r * cos(phi)
			val y = r * sin(phi)
			val z = u

			nodesUnit[i][0] = x
			nodesUnit[i][1] = y
			nodesUnit[i][2] = z

			val o = i * 3
			nodes[o] = x * sphereRadius
			nodes[o + 1] = y * sphereRadius
			nodes[o + 2] = z * sphereRadius
		}
		nodesBuffer = asFloatBuffer(nodes)
		nodesModel = nodes

		val edges = buildConnectedEdges(nodesUnit, neighborsPerNode = 3)
		val lineData = FloatArray(edges.size * 2 * 3)
		var out = 0
		for (e in edges) {
			val a = e.first
			val b = e.second
			lineData[out++] = nodes[a * 3]
			lineData[out++] = nodes[a * 3 + 1]
			lineData[out++] = nodes[a * 3 + 2]
			lineData[out++] = nodes[b * 3]
			lineData[out++] = nodes[b * 3 + 1]
			lineData[out++] = nodes[b * 3 + 2]
		}

		linesVertexCount = edges.size * 2
		linesBuffer = asFloatBuffer(lineData)
	}

	private fun buildConnectedEdges(nodesUnit: Array<FloatArray>, neighborsPerNode: Int): List<Pair<Int, Int>> {
		val n = nodesUnit.size
		val dist = Array(n) { FloatArray(n) }
		for (i in 0 until n) {
			for (j in i + 1 until n) {
				val dx = nodesUnit[i][0] - nodesUnit[j][0]
				val dy = nodesUnit[i][1] - nodesUnit[j][1]
				val dz = nodesUnit[i][2] - nodesUnit[j][2]
				val d = dx * dx + dy * dy + dz * dz
				dist[i][j] = d
				dist[j][i] = d
			}
		}

		val uf = UnionFind(n)
		val edges = LinkedHashSet<Long>()

		fun addEdge(i: Int, j: Int) {
			val a = minOf(i, j)
			val b = maxOf(i, j)
			val key = (a.toLong() shl 32) or b.toLong()
			if (edges.add(key)) {
				uf.union(a, b)
			}
		}

		// Local structure: connect each node to a few closest neighbors.
		for (i in 0 until n) {
			val best = IntArray(neighborsPerNode) { -1 }
			val bestD = FloatArray(neighborsPerNode) { Float.POSITIVE_INFINITY }

			for (j in 0 until n) {
				if (i == j) continue
				val d = dist[i][j]
				var k = 0
				while (k < neighborsPerNode) {
					if (d < bestD[k]) {
						for (m in neighborsPerNode - 1 downTo k + 1) {
							bestD[m] = bestD[m - 1]
							best[m] = best[m - 1]
						}
						bestD[k] = d
						best[k] = j
						break
					}
					k++
				}
			}

			for (k in 0 until neighborsPerNode) {
				val j = best[k]
				if (j >= 0) addEdge(i, j)
			}
		}

		// Ensure the whole graph is connected by bridging nearest components.
		while (uf.components > 1) {
			var bestI = -1
			var bestJ = -1
			var bestD = Float.POSITIVE_INFINITY
			for (i in 0 until n) {
				for (j in i + 1 until n) {
					if (uf.find(i) == uf.find(j)) continue
					val d = dist[i][j]
					if (d < bestD) {
						bestD = d
						bestI = i
						bestJ = j
					}
				}
			}
			if (bestI < 0 || bestJ < 0) break
			addEdge(bestI, bestJ)
		}

		return edges.map { key ->
			val a = (key shr 32).toInt()
			val b = (key and 0xffffffffL).toInt()
			Pair(a, b)
		}
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

	private class UnionFind(n: Int) {
		private val parent = IntArray(n) { it }
		private val rank = IntArray(n)
		var components: Int = n
			private set

		fun find(x: Int): Int {
			var v = x
			while (parent[v] != v) {
				parent[v] = parent[parent[v]]
				v = parent[v]
			}
			return v
		}

		fun union(a: Int, b: Int) {
			var ra = find(a)
			var rb = find(b)
			if (ra == rb) return
			if (rank[ra] < rank[rb]) {
				val t = ra
				ra = rb
				rb = t
			}
			parent[rb] = ra
			if (rank[ra] == rank[rb]) rank[ra]++
			components--
		}
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
