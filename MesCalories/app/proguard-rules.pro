# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class fr.perso.mescalories.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class fr.perso.mescalories.data.** { *; }
