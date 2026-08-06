pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "InkTone"

include(":app")

include(":core:designsystem")
include(":core:ui")
include(":core:common")
include(":core:testing")

include(":domain")
include(":data")

include(":infrastructure:database")
include(":infrastructure:storage")
include(":infrastructure:parser")
include(":infrastructure:tts")
include(":infrastructure:media")
include(":infrastructure:worker")
include(":infrastructure:crashreporting")

include(":feature:library")
include(":feature:reader")
include(":feature:player")
include(":feature:search")
include(":feature:import")
include(":feature:settings")
include(":feature:statistics")
include(":feature:onboarding")

include(":benchmark")
