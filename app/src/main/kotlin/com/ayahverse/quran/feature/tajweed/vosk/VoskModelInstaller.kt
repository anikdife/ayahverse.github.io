package com.ayahverse.quran.feature.tajweed.vosk

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Downloads + installs the Arabic Vosk model into `filesDir/vosk/model` on-demand.
 *
 * Runs in a process-wide background scope so the user can leave Tajweed and use
 * other modes while the download continues.
 */
object VoskModelInstaller {
	private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip"
	private const val ZIP_NAME = "vosk-model-ar-mgb2-0.4.zip"
	private const val EXTRACTED_DIR_NAME = "vosk-model-ar-mgb2-0.4"

	sealed class State {
		data object Idle : State()
		data object Downloading : State()
		data object Installing : State()
		data object Ready : State()
		data class Error(val message: String) : State()
	}

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _state = MutableStateFlow<State>(State.Idle)
	val state: StateFlow<State> = _state.asStateFlow()

	private val started = AtomicBoolean(false)

	fun modelDir(appContext: Context): File = File(appContext.filesDir, "vosk/model")

	fun isModelPresent(appContext: Context): Boolean {
		val dir = modelDir(appContext)
		if (!dir.exists() || !dir.isDirectory) return false
		return File(dir, "conf").exists() || File(dir, "am").exists()
	}

	/**
	 * If the model is missing, start a background download+install.
	 * Safe to call multiple times.
	 */
	fun ensureInstalled(appContext: Context) {
		val ctx = appContext.applicationContext
		if (isModelPresent(ctx)) {
			_state.value = State.Ready
			return
		}
		if (!started.compareAndSet(false, true)) return

		scope.launch {
			runCatching {
				_state.value = State.Downloading
				val zipFile = downloadZip(ctx)
				_state.value = State.Installing
				installZip(ctx, zipFile)
				if (!isModelPresent(ctx)) error("Model install finished, but expected files were not found")
				_state.value = State.Ready
			}.onFailure { t ->
				_state.value = State.Error(t.message ?: "Failed to install Vosk model")
				started.set(false) // allow retry on next entry
			}
		}
	}

	private fun downloadZip(ctx: Context): File {
		val cache = ctx.cacheDir
		val tmp = File(cache, "$ZIP_NAME.tmp")
		val out = File(cache, ZIP_NAME)
		if (out.exists() && out.length() > 0) return out
		if (tmp.exists()) tmp.delete()

		val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
			instanceFollowRedirects = true
			connectTimeout = 15_000
			readTimeout = 30_000
			requestMethod = "GET"
		}
		conn.connect()
		if (conn.responseCode !in 200..299) {
			throw IllegalStateException("HTTP ${conn.responseCode} downloading model")
		}
		conn.inputStream.use { input ->
			FileOutputStream(tmp).use { output ->
				val buf = ByteArray(64 * 1024)
				while (true) {
					val read = input.read(buf)
					if (read <= 0) break
					output.write(buf, 0, read)
				}
			}
		}
		if (tmp.length() <= 0) throw IllegalStateException("Downloaded zip is empty")
		if (out.exists()) out.delete()
		if (!tmp.renameTo(out)) {
			// Fallback copy
			tmp.copyTo(out, overwrite = true)
			tmp.delete()
		}
		return out
	}

	private fun installZip(ctx: Context, zipFile: File) {
		val voskRoot = File(ctx.filesDir, "vosk").apply { mkdirs() }
		val targetModelDir = File(voskRoot, "model")
		val tempExtractRoot = File(voskRoot, "tmp_install_${System.currentTimeMillis()}")
		if (tempExtractRoot.exists()) tempExtractRoot.deleteRecursively()
		tempExtractRoot.mkdirs()

		ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
			var entry: ZipEntry? = zis.nextEntry
			while (entry != null) {
				val name = entry.name
				if (name.isNotBlank()) {
					val outFile = File(tempExtractRoot, name)
					ensureNoZipSlip(tempExtractRoot, outFile)
					if (entry.isDirectory) {
						outFile.mkdirs()
					} else {
						outFile.parentFile?.mkdirs()
						outFile.outputStream().use { out ->
							val buf = ByteArray(64 * 1024)
							while (true) {
								val read = zis.read(buf)
								if (read <= 0) break
								out.write(buf, 0, read)
							}
						}
					}
				}
				zis.closeEntry()
				entry = zis.nextEntry
			}
		}

		val extractedModelDir = File(tempExtractRoot, EXTRACTED_DIR_NAME).takeIf { it.exists() && it.isDirectory }
			?: tempExtractRoot.listFiles()?.firstOrNull { it.isDirectory }
			?: throw IllegalStateException("Unexpected zip layout: no model folder")

		// Replace the target model directory atomically-ish.
		val oldDir = targetModelDir
		if (oldDir.exists()) oldDir.deleteRecursively()
		if (!extractedModelDir.renameTo(targetModelDir)) {
			copyRecursively(extractedModelDir, targetModelDir)
			extractedModelDir.deleteRecursively()
		}
		tempExtractRoot.deleteRecursively()
	}

	private fun ensureNoZipSlip(root: File, outFile: File) {
		val rootCanonical = root.canonicalFile
		val outCanonical = outFile.canonicalFile
		val rootPath = rootCanonical.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
		val outPath = outCanonical.path
		if (!outPath.startsWith(rootPath)) {
			throw SecurityException("Zip entry is outside target dir")
		}
	}

	private fun copyRecursively(from: File, to: File) {
		if (from.isDirectory) {
			to.mkdirs()
			from.listFiles()?.forEach { child ->
				copyRecursively(child, File(to, child.name))
			}
		} else {
			to.parentFile?.mkdirs()
			from.inputStream().use { input ->
				to.outputStream().use { output ->
					input.copyTo(output)
				}
			}
		}
	}
}
