import java.io.FileInputStream
import java.io.File
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.kapt)
}

android {
	namespace = "com.ayahverse.quran"
    compileSdk = 35

    // Release signing (AAB/APK) loaded from rootProject/keystore.properties.
    // Debug builds are unaffected if the file is missing.
    val releaseKeystorePropsFile = rootProject.file("keystore.properties")
    val releaseKeystoreProps = Properties().apply {
        if (releaseKeystorePropsFile.exists()) {
            FileInputStream(releaseKeystorePropsFile).use { load(it) }
        }
    }
    fun releaseSigningProp(name: String): String = releaseKeystoreProps.getProperty(name)?.trim().orEmpty()

    signingConfigs {
        create("release") {
            val storeFilePath = releaseSigningProp("storeFile")
            storeFile = rootProject.file(storeFilePath.ifBlank { "keystore/ayahverse-release.jks" })
            storePassword = releaseSigningProp("storePassword")
            keyAlias = releaseSigningProp("keyAlias")
            keyPassword = releaseSigningProp("keyPassword")
        }
    }

    defaultConfig {
		applicationId = "com.ayahverse.quran"
        minSdk = 21
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.0"

        // Supabase (REST + Storage). These are expected to be provided via Gradle properties.
        // Example (in ~/.gradle/gradle.properties or project gradle.properties):
        // SUPABASE_URL=https://xxxx.supabase.co
        // SUPABASE_ANON_KEY=... (use your Supabase publishable key if legacy anon keys are disabled)
        // SUPABASE_STORAGE_BUCKET=word-images
        // SUPABASE_STORAGE_PREFIX=
        // SUPABASE_STORAGE_IMAGE_EXT=.png
        fun prop(name: String): String {
			val raw = (
				(project.findProperty(name) as String?)
					?: System.getenv(name)
					?: ""
			)
				.trim()
            return raw
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .replace("\r", "")
                .replace("\n", "")
                .trim()
        }

        val supabaseUrl = prop("SUPABASE_URL")
        val supabaseAnonKey = prop("SUPABASE_ANON_KEY")
        val supabaseBucket = prop("SUPABASE_STORAGE_BUCKET")
        val supabasePrefix = prop("SUPABASE_STORAGE_PREFIX")
        val supabaseImageExt = prop("SUPABASE_STORAGE_IMAGE_EXT")

		// Hadith API
		val hadithApiKey = prop("HADITH_API_KEY")
		val hadithApiBaseUrl = prop("HADITH_API_BASE_URL").ifBlank { "https://hadithapi.com/" }

        fun String.asBuildConfigString(): String = "\"" + this
            .replace("\\\\", "\\\\\\\\")
            .replace("\"", "\\\"") + "\""

        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", supabaseAnonKey.asBuildConfigString())
        buildConfigField("String", "SUPABASE_STORAGE_BUCKET", supabaseBucket.asBuildConfigString())
        buildConfigField("String", "SUPABASE_STORAGE_PREFIX", supabasePrefix.asBuildConfigString())
        buildConfigField("String", "SUPABASE_STORAGE_IMAGE_EXT", supabaseImageExt.asBuildConfigString())
		buildConfigField("String", "HADITH_API_KEY", hadithApiKey.asBuildConfigString())
		buildConfigField("String", "HADITH_API_BASE_URL", hadithApiBaseUrl.asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
		buildConfig = true
    }
}

abstract class ValidateReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val rootDirPath: Property<String>

    @get:Input
    abstract val keystorePropertiesPath: Property<String>

    @TaskAction
    fun validate() {
        val rootDir = File(rootDirPath.get())
        val propsFile = File(keystorePropertiesPath.get())
        if (!propsFile.exists()) {
            throw GradleException("Missing keystore.properties for release signing")
        }
        val props = Properties().apply {
            FileInputStream(propsFile).use { load(it) }
        }
        fun required(name: String): String {
            val v = props.getProperty(name)?.trim().orEmpty()
            if (v.isBlank()) throw GradleException("Missing required signing property: $name")
            return v
        }

        val storeFilePath = required("storeFile")
        val storeFile = File(rootDir, storeFilePath)
        if (!storeFile.exists()) {
            throw GradleException("Release keystore file not found at: ${storeFile.absolutePath}")
        }
        required("keyAlias")
        required("storePassword")
        required("keyPassword")
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning", ValidateReleaseSigningTask::class.java) {
    group = "verification"
    description = "Validates keystore.properties and keystore file for release signing."
    rootDirPath.set(rootProject.projectDir.absolutePath)
    keystorePropertiesPath.set(rootProject.file("keystore.properties").absolutePath)
}

// Make release builds fail early with clear messages. Debug tasks are unaffected.
tasks.matching { it.name == "validateSigningRelease" || it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    dependsOn(validateReleaseSigning)
}

kotlin {
    jvmToolchain(21)
}

// Room's annotation processor uses SQLite for query verification.
// On some Windows setups, the SQLite native lib extraction can fail if it tries to write into a protected directory.
// Force a writable, project-local temp dir for kapt worker processes.
val sqliteTmpDir = layout.buildDirectory.dir("tmp/sqlite")

abstract class PrepareSqliteTmpDirTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        outputDir.get().asFile.mkdirs()
    }
}

val prepareSqliteTmpDir = tasks.register("prepareSqliteTmpDir", PrepareSqliteTmpDirTask::class.java) {
    outputDir.set(sqliteTmpDir)
}

tasks.matching { it.name.startsWith("kapt", ignoreCase = true) }.configureEach {
    if (this is org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask) {
		dependsOn(prepareSqliteTmpDir)
		val sqliteTmpDirPath = sqliteTmpDir.get().asFile.absolutePath
        kaptProcessJvmArgs.addAll(
            listOf(
                "-Djava.io.tmpdir=$sqliteTmpDirPath",
                "-Dorg.sqlite.tmpdir=$sqliteTmpDirPath",
            ),
        )
    }
}

dependencies {
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

	// UI helpers
	implementation("androidx.recyclerview:recyclerview:1.4.0")
	implementation("io.coil-kt:coil:2.7.0")
    // Material icons (used for Tajweed previous/next controls)
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Offline speech recognition (Tajweed practice MVP)
    implementation("com.alphacephei:vosk-android:0.3.38") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    // Vosk uses JNA. The default jar variant does not bundle Android native
    // `libjnidispatch.so`, causing runtime crashes on-device.
    // Force the Android AAR artifact, which includes the required jni libs.
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // Unit tests
    testImplementation(kotlin("test"))

    // Room (tafsir sources + cache)
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")

    // Retrofit helpers
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")

    // HTML parsing/sanitization (Altafsir response adapter)
    implementation("org.jsoup:jsoup:1.17.2")
}
