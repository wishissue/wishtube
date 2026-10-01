# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Media3 & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn androidx.media3.**
