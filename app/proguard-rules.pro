# Keep Kotlinx Serialization generated classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.zerowater.map.**$$serializer { *; }
-keepclassmembers class app.zerowater.map.** {
    *** Companion;
}
-keepclasseswithmembers class app.zerowater.map.** {
    kotlinx.serialization.KSerializer serializer(...);
}
