# ===========================================================================
# VaultBrain — Production ProGuard & R8 Optimization Rules
# ===========================================================================

# 1. Core Common Domain Models & Enums (Critical for Room & Serialization)
-keep class com.vaultbrain.core.common.model.** { *; }
-keepclassmembers enum com.vaultbrain.core.common.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.vaultbrain.core.common.preferences.** { *; }

# 2. SQLCipher & Native Database
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep interface net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# 3. ObjectBox Vector Store & Generated Entities
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep class com.vaultbrain.core.vectorstore.entity.** { *; }
-keepclassmembers class com.vaultbrain.core.vectorstore.entity.** { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.Entity <fields>;
    @io.objectbox.annotation.Entity <methods>;
}
-dontwarn io.objectbox.**

# 4. Room & Database Entities
-keep class androidx.room.** { *; }
-keep class com.vaultbrain.core.database.** { *; }
-keep class com.vaultbrain.core.database.entity.** { *; }
-keep class com.vaultbrain.core.database.dao.** { *; }
-keepclassmembers class com.vaultbrain.core.database.util.RoomTypeConverters { *; }
-dontwarn androidx.room.**

# 5. Kotlinx Serialization
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <methods>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    *** serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# 6. ML Kit Text Recognition & Vision
-keep class com.google.mlkit.vision.** { *; }
-keep interface com.google.mlkit.vision.** { *; }
-dontwarn com.google.mlkit.vision.**

# 7. TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# 8. AndroidX Security & Biometrics
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }

# 9. Jetpack Glance App Widgets & TileService
-keep class androidx.glance.** { *; }
-keep class com.vaultbrain.app.widgets.** { *; }
-keep class com.vaultbrain.app.tiles.** { *; }

# 10. Dagger / Hilt & ViewModels
-keep class * extends androidx.hilt.work.HiltWorker
-keep class dagger.hilt.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 11. Google Play Billing
-keep class com.android.billingclient.api.** { *; }
-keep interface com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# 12. User Messaging Platform (UMP) Consent
-keep class com.google.android.ump.** { *; }
-keep interface com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# 13. Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# 14. ML Kit GenAI Prompt
-keep class com.google.mlkit.genai.prompt.** { *; }
-keep class com.google.mlkit.genai.common.** { *; }
-keep interface com.google.mlkit.genai.** { *; }
-dontwarn com.google.mlkit.genai.**

# 15. Google Play Services Auth / Drive / Guava
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.common.** { *; }
-dontwarn com.google.common.**
-dontwarn com.google.api.client.**

# Build-time AutoValue types and optional TensorFlow GPU backend.
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options
