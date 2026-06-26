import java.util.Properties
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.chaquopy)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localBuildConfigString(key: String): String {
    val value = localProperties.getProperty(key).orEmpty()
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun gitOutput(vararg args: String): String = try {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", *args)
        standardOutput = stdout
        isIgnoreExitValue = true
    }
    stdout.toString().trim()
} catch (_: Throwable) {
    ""
}

val gitVersionName = gitOutput("describe", "--tags", "--always", "--dirty")
    .ifBlank { "0.0.0-local" }
    .replace(Regex("""[^0-9A-Za-z._-]"""), "-")
val gitVersionCode = gitOutput("rev-list", "--count", "HEAD").toIntOrNull()?.coerceAtLeast(1) ?: 1
val gitCommit = gitOutput("rev-parse", "--short", "HEAD").ifBlank { "unknown" }
val gitBranch = gitOutput("branch", "--show-current").ifBlank { "unknown" }

fun installChaquopyPipProxyGuard(variantName: String) {
    val envDir = layout.buildDirectory.dir("python/env/$variantName").get().asFile
    val sitePackagesDirs = buildList {
        add(File(envDir, "Lib/site-packages"))
        File(envDir, "lib").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("python") }
            ?.forEach { add(File(it, "site-packages")) }
    }.filter { it.isDirectory }

    if (sitePackagesDirs.isEmpty()) {
        logger.warn("Chaquopy Python site-packages not found under ${envDir.path}; pip proxy guard was not installed.")
        return
    }

    sitePackagesDirs.forEach { dir ->
        val pipInstall = File(dir, "chaquopy/pip_install.py")
        if (!pipInstall.isFile) {
            logger.warn("Chaquopy pip_install.py not found at ${pipInstall.path}; pip proxy guard was not installed.")
            return@forEach
        }

        val original = pipInstall.readText()
        if ("MobileClaw pip proxy guard" in original) return@forEach

        val marker = "            os.environ[\"PIP_CONFIG_FILE\"] = os.devnull\n"
        val guarded = original.replace(
            marker,
            marker +
                "            # MobileClaw pip proxy guard: Android Studio can pass a stale\n" +
                "            # local proxy into Gradle. pip is launched below in isolated\n" +
                "            # mode, but requests still honors NO_PROXY for proxy bypasses.\n" +
                "            os.environ.setdefault(\"NO_PROXY\", \"*\")\n" +
                "            os.environ.setdefault(\"no_proxy\", \"*\")\n"
        )
        if (guarded == original) {
            logger.warn("Could not patch ${pipInstall.path}; pip proxy guard marker was not found.")
        } else {
            pipInstall.writeText(guarded)
        }
    }
}

tasks.configureEach {
    val match = Regex("""generate(.+)PythonRequirements""").matchEntire(name)
    if (match != null) {
        val variantName = match.groupValues[1].replaceFirstChar { it.lowercaseChar() }
        doFirst {
            installChaquopyPipProxyGuard(variantName)
        }
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

android {
    namespace = "com.mobileclaw"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mobileclaw"
        minSdk = 30
        targetSdk = 35
        versionCode = gitVersionCode
        versionName = gitVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true
        multiDexKeepFile = file("src/main/multidex-keep.txt")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", localBuildConfigString("cloudinary.cloud_name"))
        buildConfigField("String", "CLOUDINARY_API_KEY", localBuildConfigString("cloudinary.api_key"))
        buildConfigField("String", "CLOUDINARY_API_SECRET", localBuildConfigString("cloudinary.api_secret"))
        buildConfigField("String", "GIT_VERSION", buildConfigString(gitVersionName))
        buildConfigField("String", "GIT_COMMIT", buildConfigString(gitCommit))
        buildConfigField("String", "GIT_BRANCH", buildConfigString(gitBranch))

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        val configuredBuildPython = providers.gradleProperty("chaquopy.buildPython")
            .orElse(providers.environmentVariable("CHAQUOPY_BUILD_PYTHON"))
            .orNull
        if (!configuredBuildPython.isNullOrBlank()) {
            buildPython(configuredBuildPython)
        }
        pip {
            // Keep requests' transitive dependency stable across CI images.
            // Newer urllib3 releases may require Python >= 3.10, while some
            // Chaquopy build environments still expose Python 3.9.x.
            install("urllib3==1.26.18")
            install("requests==2.31.0")
            install("beautifulsoup4")
            install("numpy")
            install("pillow")
            install("pandas")
            install("lxml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.gson)
    implementation(libs.jsoup)

    // Local on-device LLM runtime for .litertlm models.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // VPN / Proxy
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
