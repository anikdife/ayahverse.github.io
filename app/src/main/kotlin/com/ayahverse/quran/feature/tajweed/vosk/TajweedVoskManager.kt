package com.ayahverse.quran.feature.tajweed.vosk

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Minimal Vosk wrapper.
 * Expects a model directory at app files: filesDir/vosk/model (unzipped).
 */
class TajweedVoskManager(
	private val appContext: Context,
) {
	sealed class InitResult {
		data object Ok : InitResult()
		data class MissingModel(val expectedDir: File) : InitResult()
		data class Error(val message: String) : InitResult()
	}

	@Volatile
	private var model: Model? = null

	suspend fun initIfNeeded(): InitResult = withContext(Dispatchers.IO) {
		if (model != null) return@withContext InitResult.Ok
		val modelDir = File(appContext.filesDir, "vosk/model")
		if (!modelDir.exists() || !modelDir.isDirectory) {
			runCatching { tryCopyModelFromAssets(targetDir = modelDir) }
		}
		val hasConf = File(modelDir, "conf").exists() || File(modelDir, "am").exists()
		if (!hasConf) {
			return@withContext InitResult.MissingModel(modelDir)
		}
		try {
			model = Model(modelDir.absolutePath)
			InitResult.Ok
		} catch (t: Throwable) {
			InitResult.Error(t.message ?: "Failed to init Vosk")
		}
	}

	private fun tryCopyModelFromAssets(targetDir: File) {
		// Optional: if you package a model under assets/vosk-model/..., we'll copy it on demand.
		val assetRootCandidates = listOf("vosk-model", "vosk/model")
		val assetManager = appContext.assets
		val root = assetRootCandidates.firstOrNull { candidate ->
			runCatching { !assetManager.list(candidate).isNullOrEmpty() }.getOrDefault(false)
		} ?: return

		copyAssetDirRecursively(assetPath = root, outDir = targetDir)
	}

	private fun copyAssetDirRecursively(assetPath: String, outDir: File) {
		val assetManager = appContext.assets
		val children = assetManager.list(assetPath).orEmpty()
		if (children.isEmpty()) {
			// It's a file.
			outDir.parentFile?.mkdirs()
			assetManager.open(assetPath).use { input ->
				outDir.outputStream().use { output ->
					input.copyTo(output)
				}
			}
			return
		}

		outDir.mkdirs()
		for (child in children) {
			val childAssetPath = "$assetPath/$child"
			val childOut = File(outDir, child)
			val grandChildren = assetManager.list(childAssetPath).orEmpty()
			if (grandChildren.isEmpty()) {
				assetManager.open(childAssetPath).use { input ->
					childOut.outputStream().use { output ->
						input.copyTo(output)
					}
				}
			} else {
				copyAssetDirRecursively(childAssetPath, childOut)
			}
		}
	}

	suspend fun recognizePcmFile(
		pcmFile: File,
		sampleRateHz: Float,
	): String = withContext(Dispatchers.IO) {
		val currentModel = model ?: error("Vosk not initialized")
		val recognizer = Recognizer(currentModel, sampleRateHz)
		pcmFile.inputStream().use { input ->
			val buffer = ByteArray(4096)
			while (true) {
				val read = input.read(buffer)
				if (read <= 0) break
				recognizer.acceptWaveForm(buffer, read)
			}
		}
		val finalJson = recognizer.finalResult
		recognizer.close()
		// Vosk returns JSON; keep it simple for Phase-1 and extract "text" best-effort.
		extractTextField(finalJson)
	}

	private fun extractTextField(json: String): String {
		// Expected shape: {"text" : "..."}
		val key = "\"text\""
		val idx = json.indexOf(key)
		if (idx < 0) return ""
		val colon = json.indexOf(':', idx)
		if (colon < 0) return ""
		val firstQuote = json.indexOf('"', colon + 1)
		if (firstQuote < 0) return ""
		val secondQuote = json.indexOf('"', firstQuote + 1)
		if (secondQuote < 0) return ""
		return json.substring(firstQuote + 1, secondQuote)
	}
}
