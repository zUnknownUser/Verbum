plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nexussoft.verbum.audio"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:clients"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
