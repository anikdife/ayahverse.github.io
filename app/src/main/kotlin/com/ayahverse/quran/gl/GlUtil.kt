package com.ayahverse.quran.gl

import android.opengl.GLES30

internal object GlUtil {
	fun compileShader(type: Int, source: String): Int {
		val shader = GLES30.glCreateShader(type)
		require(shader != 0) { "glCreateShader failed" }
		GLES30.glShaderSource(shader, source)
		GLES30.glCompileShader(shader)
		val status = IntArray(1)
		GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
		if (status[0] == 0) {
			val log = GLES30.glGetShaderInfoLog(shader)
			GLES30.glDeleteShader(shader)
			error("Shader compile failed: $log")
		}
		return shader
	}

	fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
		val program = GLES30.glCreateProgram()
		require(program != 0) { "glCreateProgram failed" }
		GLES30.glAttachShader(program, vertexShader)
		GLES30.glAttachShader(program, fragmentShader)
		GLES30.glLinkProgram(program)
		val status = IntArray(1)
		GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
		if (status[0] == 0) {
			val log = GLES30.glGetProgramInfoLog(program)
			GLES30.glDeleteProgram(program)
			error("Program link failed: $log")
		}
		return program
	}
}
