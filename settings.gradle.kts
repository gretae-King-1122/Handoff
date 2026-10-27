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

rootProject.name = "Handoff"

include(":app")
include(":module-api")
include(":module-rules")
include(":module-executor")
include(":module-hook-lsposed")
include(":module-storage")
include(":module-ui")
