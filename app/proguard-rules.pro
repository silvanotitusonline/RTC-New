# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Firebase (Cloud Messaging)
-keep class com.google.firebase.** { *; }

# --- kotlinx.serialization -------------------------------------------------
# This app's actual serialization stack. Room, Media3 and Supabase-kt/Ktor
# each ship correct consumer ProGuard rules inside their own AARs, so no
# manual rules are needed for them beyond keeping the serializers below,
# which covers Supabase-kt's own request/response DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# This app's own @Serializable models (za.org.rtc.community.**).
-keep,includedescriptorclasses class za.org.rtc.community.**$$serializer { *; }
-keepclassmembers class za.org.rtc.community.** {
    *** Companion;
}
-keepclasseswithmembers class za.org.rtc.community.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Supabase-kt's own internal @Serializable DTOs (auth session, postgrest
# request/response envelopes, etc.) — these are the models most likely to
# break silently in release only if stripped, since they parse network
# responses this app doesn't control the shape of.
-keep,includedescriptorclasses class io.github.jan.supabase.**$$serializer { *; }
-keepclassmembers class io.github.jan.supabase.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.jan.supabase.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Android Credential Manager / Google Sign-In ---------------------------
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
