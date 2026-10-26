# Gson (de)serialises these classes by reflection: keep their fields and constructors.
-keep class com.calorietracker.app.data.model.** { *; }
-keep class com.calorietracker.app.data.local.*Entity { *; }
-keep class com.calorietracker.app.domain.model.** { *; }

# Keep generic signatures and annotations Gson and Room rely on.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# OkHttp / Okio ship their own consumer rules; silence optional-platform warnings.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
