pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "PrivatePlayer"

include(":app")

// core
include(":core:common")
include(":core:design")
include(":core:database")
include(":core:datastore")
include(":core:storage")
include(":core:playback")

// domain / data
include(":domain")
include(":data")

// features
include(":feature:home")
include(":feature:detail")
include(":feature:importer")
include(":feature:player")
include(":feature:settings")
