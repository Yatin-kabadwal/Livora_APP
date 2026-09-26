# ---- General ----
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization ----
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.livora.corbett.**$$serializer { *; }
-keepclassmembers class com.livora.corbett.** { *** Companion; }
-keepclasseswithmembers class com.livora.corbett.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.livora.corbett.data.api.** { *; }
-dontwarn kotlinx.serialization.**

# ---- Retrofit / OkHttp / Okio ----
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Socket.IO / Engine.IO ----
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# ---- Coil ----
-dontwarn coil.**

# ---- Hilt / WorkManager entry points ----
-keep class com.livora.corbett.work.** { *; }
