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
        // Public Complete SDK flavor; no API key or private Maven credentials belong here.
        maven {
            url = uri("https://repositories.tomtom.com/artifactory/maven")
            content { includeGroupByRegex("com\\.tomtom\\..*") }
        }
    }
}

rootProject.name = "RTCCommunity"
include(":app")
