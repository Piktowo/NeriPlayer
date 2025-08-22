-keepattributes *Annotation*

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.runtime.internal.** { *; }
-keep class androidx.compose.runtime.saveable.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class **.R$* {
    public static <fields>;
}

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class coil.** { *; }
-keep class coil.decode.* { *; }
-keep class coil.fetch.* { *; }
-keep class coil.memory.* { *; }
-keep class coil.request.* { *; }
-keep class coil.target.* { *; }
-keep class coil.transition.* { *; }
-keep class coil.util.* { *; }
-dontwarn coil.util.CoilUtils

-keep class moe.ouom.neriplayer.data.** { *; }
-keep class moe.ouom.neriplayer.ui.viewmodel.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.Gson { *; }

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}

-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class org.burnoutcrew.reorderable.** { *; }

-keep class com.google.accompanist.** { *; }

-keep class moe.ouom.neriplayer.** { *; }