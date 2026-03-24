# Retrofit reflects on generic signatures, enclosing classes and runtime annotations.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepattributes *Annotation*

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# App-specific model keeps for Moshi reflection on release builds.
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class kotlin.Metadata { *; }
-keep class com.carlauncher.data.models.** { *; }
-keep interface com.carlauncher.data.WeatherApiService { *; }
