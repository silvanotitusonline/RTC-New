pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "rtc-remediation"
if (providers.gradleProperty("includeTomTom").orNull == "true") {
    include(":tomtom")
    dependencyResolutionManagement.repositories {
        maven {
            url = uri("https://repositories.tomtom.com/artifactory/maven")
            credentials {
                username = providers.environmentVariable("TOMTOM_MAVEN_USERNAME").orNull
                password = providers.environmentVariable("TOMTOM_MAVEN_PASSWORD").orNull
            }
            content { includeGroupByRegex("com\\.tomtom.*") }
        }
    }
}
