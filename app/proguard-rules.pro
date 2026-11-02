# Keep kotlinx serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.world303.timewallpaper.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.world303.timewallpaper.** {
    kotlinx.serialization.KSerializer serializer(...);
}
