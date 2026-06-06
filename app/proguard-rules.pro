# Wi-Fi Audit — ProGuard rules
# Gson / Retrofit — garder les DTOs
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.wifiaudit.app.data.remote.dto.** { *; }
