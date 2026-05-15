plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.mobileclaw"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mobileclaw"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true
        multiDexKeepFile = file("src/main/multidex-keep.txt")

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
