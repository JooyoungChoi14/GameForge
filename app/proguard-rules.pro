# GameForge ProGuard Rules

# ── Room ──────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# ── GeckoView ─────────────────────────────────────────────────
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep class com.gameforge.engine.** { *; }

# ── Kotlin & Coroutines ───────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Compose ───────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Gson ──────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── GameForge Data Models ─────────────────────────────────────
-keep class com.gameforge.data.** { *; }
-keep class com.gameforge.llm.** { *; }

# ── OkHttp ─────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**