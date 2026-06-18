# ProGuard rules for BatMUD CN
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Room
-keep class com.batmudcn.data.** { *; }
