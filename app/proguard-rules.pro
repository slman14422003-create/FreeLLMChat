-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes SourceFile, LineNumberTable

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn kotlinx.serialization.**

-keep class okhttp3.internal.platform.** { *; }

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.freellm.chat.**$$serializer { *; }
-keepclassmembers class com.freellm.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.freellm.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

-keep class com.freellm.chat.FreeLlmApp { *; }

-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }

# ---- R8 full-mode tuning ----
-allowaccessmodification
-repackageclasses ''
-optimizationpasses 5
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
-keep class com.freellm.chat.data.Attachment { *; }
-keep class com.freellm.chat.data.Attachment$$serializer { *; }

-keep class androidx.biometric.** { *; }

-dontwarn org.mariuszgromada.**
-keep class org.mariuszgromada.math.mxparser.** { *; }

-keepattributes JavascriptInterface
-keepclassmembers class com.freellm.chat.data.JsRunner$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
