# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.claudewidget.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Glance
-keep class androidx.glance.** { *; }
-keep class com.claudewidget.widget.** { *; }

# WorkManager
-keep class com.claudewidget.worker.** { *; }

# EncryptedSharedPreferences / security-crypto
-keep class androidx.security.crypto.** { *; }

# Tink (used internally by security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.**
-dontwarn com.google.crypto.tink.util.KeysDownloader
-keep class com.google.crypto.tink.** { *; }
