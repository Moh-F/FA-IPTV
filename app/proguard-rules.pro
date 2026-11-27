# Compose, AndroidX, Media3, OkHttp and Coil ship their own consumer rules. Keep stack traces readable in the log viewer.
-keepattributes SourceFile,LineNumberTable
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.mohdev.faiptv.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mohdev.faiptv.**$$serializer { *; }
-dontwarn org.slf4j.**
# libVLC: native code calls back into these classes by name
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**
# zxing (QR codes)
-dontwarn com.google.zxing.**
