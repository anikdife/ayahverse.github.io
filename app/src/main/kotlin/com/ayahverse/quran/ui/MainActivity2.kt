package com.ayahverse.quran.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import com.ayahverse.quran.gl.CameraController
import com.ayahverse.quran.gl.SkyRenderer

/**
 * Previous launcher activity.
 *
 * The new requested OpenGL scene lives in [MainActivity].
 */
class MainActivity2 : ComponentActivity() {
	private lateinit var glView: SkyGLSurfaceView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val cameraController = CameraController()
		val renderer = SkyRenderer(context = this, cameraController = cameraController)

		glView = SkyGLSurfaceView(this, cameraController, renderer)
		setContentView(glView)
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

private class SkyGLSurfaceView(
	context: Context,
	private val cameraController: CameraController,
	private val renderer: SkyRenderer,
) : GLSurfaceView(context) {
	// No touch controls for this scene.

	init {
		setEGLContextClientVersion(3)
		setRenderer(renderer)
		renderMode = RENDERMODE_CONTINUOUSLY
		preserveEGLContextOnPause = true
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		return true
	}
}
