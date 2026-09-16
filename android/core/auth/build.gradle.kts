plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.nexussoft.verbum.auth"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api(project(":core:clients"))
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
