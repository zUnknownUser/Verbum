plugins {
    id("com.google.gms.google-services") version "4.5.0"
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nexussoft.verbum"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexussoft.verbum"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        // Languages the app ships. The system picks by device locale; English is the base.
        localeFilters += listOf("en", "pt-rBR")
    }

    // Where the Verbum backend is (api/openapi.yaml). Debug reads the VERBUM_API_BASE_URL Gradle
    // property — set it in android/gradle.properties or with -PVERBUM_API_BASE_URL=http://<ip>:8080 —
    // and falls back to the emulator's route to the host. Release is the production host. No key
    // ships in the app: the API is keyless (§45) and OpenAI is only ever reached by the server (§56).
    val debugApiBaseUrl = (project.findProperty("VERBUM_API_BASE_URL") as String?) ?: "http://10.0.2.2:8080"
    buildTypes {
        debug {
            buildConfigField("String", "VERBUM_API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "VERBUM_API_BASE_URL", "\"https://api.verbum.app\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:clients"))
    implementation(project(":core:audio"))
    implementation(project(":feature:scripture"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
