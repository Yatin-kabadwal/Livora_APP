# =========================================================
# GENERAL
# =========================================================
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile


# =========================================================
# KOTLINX SERIALIZATION
# =========================================================

# Generated serializers
-keep,includedescriptorclasses class com.livora.corbett.**$$serializer { *; }

# Companion objects used by serialization
-keepclassmembers class com.livora.corbett.** {
    *** Companion;
}

# Generated serializer() methods
-keepclasseswithmembers class com.livora.corbett.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn kotlinx.serialization.**


# =========================================================
# RETROFIT
# =========================================================

-keepattributes Signature
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep Retrofit annotated API methods
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn retrofit2.**


# =========================================================
# OKHTTP / OKIO
# =========================================================

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# =========================================================
# SOCKET.IO / ENGINE.IO
# =========================================================

-keep class io.socket.** { *; }

-dontwarn io.socket.**


# =========================================================
# COIL
# =========================================================

-dontwarn coil.**


# =========================================================
# HILT
# =========================================================


# =========================================================
# WORKMANAGER
# =========================================================

# Your Worker classes
-keep class com.livora.corbett.work.** { *; }