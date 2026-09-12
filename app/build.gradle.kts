import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services) apply false
}

// The sanitised source intentionally does not commit Firebase configuration.
// Apply Google Services automatically when a legitimate per-environment
// google-services.json is supplied locally or decoded from a CI secret.
if (project.file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val releaseRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}

val releaseSigningProperties = Properties().apply {
    val signingPropertiesFile = rootProject.file("signing.properties")
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use(::load)
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }
        ?: releaseSigningProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningValue("storeFile", "RTC_ANDROID_KEYSTORE_PATH")
val releaseStorePassword = releaseSigningValue("storePassword", "RTC_ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "RTC_ANDROID_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "RTC_ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

if (releaseRequested) {
    require(hasReleaseSigning) {
        "Release builds require signing.properties or the RTC_ANDROID_KEYSTORE_PATH, " +
            "RTC_ANDROID_KEYSTORE_PASSWORD, RTC_ANDROID_KEY_ALIAS, and " +
            "RTC_ANDROID_KEY_PASSWORD environment variables."
    }
}

// Security boundary: a debug artifact may only be built with an explicitly supplied,
// ignored non-production Supabase configuration. Production values are not defaults.
val nonProductionRuntimeProperties = Properties().apply {
    val runtimePropertiesFile = rootProject.file("runtime.local.properties")
    if (runtimePropertiesFile.isFile) runtimePropertiesFile.inputStream().use(::load)
}

fun debugRuntimeValue(key: String): String {
    val value = providers.gradleProperty(key).orNull
        ?: nonProductionRuntimeProperties.getProperty(key)
    require(!value.isNullOrBlank()) {
        "Debug builds require the non-production runtime property '$key'."
    }
    return value.trim()
}

// Release runtime values are intentionally isolated from the debug properties. The file is
// gitignored and CI may alternatively supply the same values through masked environment vars.
val releaseRuntimeProperties = Properties().apply {
    val runtimePropertiesFile = rootProject.file("release.runtime.properties")
    if (runtimePropertiesFile.isFile) runtimePropertiesFile.inputStream().use(::load)
}

fun releaseRuntimeValue(key: String, environmentName: String): String {
    val value = providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
        ?: releaseRuntimeProperties.getProperty(key)?.takeIf { it.isNotBlank() }
    if (releaseRequested) {
        require(!value.isNullOrBlank()) {
            "Release builds require the production runtime property '$key' via " +
                "release.runtime.properties, Gradle properties, or $environmentName."
        }
    }
    return value?.trim().orEmpty()
}

fun quotedBuildConfigValue(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "za.org.rtc.community"
    compileSdk = 36

    defaultConfig {
        applicationId = "za.org.rtc.community"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "1.0.6-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // Deliberately fail closed outside the explicitly configured build type.
        buildConfigField("String", "SUPABASE_URL", "\"\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"\"")
        buildConfigField("String", "RECAPTCHA_SITE_KEY", "\"6LeC44UtAAAAAAgm0aKcyO8b3xoW3SpPFLY2IXe7\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "SUPABASE_URL",
                quotedBuildConfigValue(debugRuntimeValue("supabase.nonproduction.url")),
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                quotedBuildConfigValue(debugRuntimeValue("supabase.nonproduction.publishableKey")),
            )
        }
        release {
            buildConfigField(
                "String",
                "SUPABASE_URL",
                quotedBuildConfigValue(
                    releaseRuntimeValue("supabase.production.url", "RTC_PROD_SUPABASE_URL"),
                ),
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                quotedBuildConfigValue(
                    releaseRuntimeValue(
                        "supabase.production.publishableKey",
                        "RTC_PROD_SUPABASE_PUBLISHABLE_KEY",
                    ),
                ),
            )
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.zxing.core)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.firebase.messaging)
    implementation(libs.recaptcha.enterprise)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
