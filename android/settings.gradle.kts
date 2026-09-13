pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Verbum"

// Module layering mirrors the iOS package (docs/PRODUCT.md §36, §60 Task 1):
//
//   :app ─▶ :feature:* ─▶ :core:clients ─▶ :core:models
//               │              │
//               ├─▶ :core:designsystem
//               └─▶ :core:common ──▶ :core:models
//
// :core:models and :core:common are plain JVM modules: no Android dependency,
// so domain tests run without an emulator.
include(":app")
include(":core:models")
include(":core:common")
include(":core:designsystem")
include(":core:clients")
include(":core:audio")
include(":core:auth")
include(":feature:scripture")
