# ============================================================================
#  R8 / ProGuard configuration for Remiit
#
#  R8 full mode is on by default in AGP 8+. It renames fields, strips generic
#  signatures and removes anything it cannot see referenced. The dangerous part
#  for this app is that every failure mode is SILENT: the APK installs, the UI
#  works, and rules simply never fire. Everything reached reflectively or only
#  from the manifest therefore needs an explicit keep below.
# ============================================================================

# ── Attributes ──────────────────────────────────────────────────────────────
# Signature/InnerClasses/EnclosingMethod: kotlinx.serialization needs generic
# type information to resolve List<Trigger> and the sealed hierarchy.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*, RuntimeVisibleAnnotations, AnnotationDefault

# Readable stack traces from release builds. Deobfuscate anything else with
# app/build/outputs/mapping/release/mapping.txt
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── kotlinx.serialization ───────────────────────────────────────────────────
# The whole rule model (ReminderRule, the sealed Trigger hierarchy,
# DeliveryConfig, RuleConstraints) is stored in Room as JSON. The compiler
# plugin generates a `Companion.serializer()` and a `$$serializer` object per
# @Serializable class, both found reflectively at runtime. Losing them turns
# every saved rule into a deserialization crash on next launch.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rahulgorai.remiit.data.model.**$$serializer { *; }
-keep class com.rahulgorai.remiit.data.model.** { <fields>; <init>(...); }
-dontwarn kotlinx.serialization.**

# Sealed-subtype discriminators are matched by class name in the polymorphic
# JSON, so the subtypes themselves must not be renamed.
-keep class * implements com.rahulgorai.remiit.data.model.Trigger { *; }

# ── Room ────────────────────────────────────────────────────────────────────
# Entities and DAOs are wired by generated code, but field names become column
# names — renaming them breaks queries against an existing database.
-keep class com.rahulgorai.remiit.data.db.** { *; }
-keep @androidx.room.Entity class * { <fields>; <init>(...); }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ── WorkManager ─────────────────────────────────────────────────────────────
# Workers are instantiated reflectively from their class name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }

# ── Manifest entry points ───────────────────────────────────────────────────
# Referenced only as strings in AndroidManifest.xml / accessibility config, so
# R8 cannot see them being used. This is the set whose loss silently kills the
# app-launch, boot re-arm, Wi-Fi and geofence triggers.
-keep class * extends android.app.Application { <init>(); }
-keep class * extends android.app.Activity { <init>(); }
-keep class * extends android.app.Service { <init>(); }
-keep class * extends android.content.BroadcastReceiver { <init>(); }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# ── Koin ────────────────────────────────────────────────────────────────────
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ── Play Services location (geofencing) ─────────────────────────────────────
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ── Compose ─────────────────────────────────────────────────────────────────
# Compose ships its own consumer rules; these only silence known-absent
# desktop/JVM-only classes referenced from the shared runtime.
-dontwarn androidx.compose.**
