# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase$Callback
-keep class * extends androidx.room.migration.Migration

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep data models
-keep class com.eventmanager.app.data.models.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Google Sheets API classes
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }
-keep class com.google.apis.** { *; }

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep WorkManager classes
-keep class androidx.work.** { *; }

# Keep ZXing QR code classes
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# Keep Apache POI classes
-keep class org.apache.poi.** { *; }

# Ignore missing classes that POI references but don't exist on Android
# These are desktop-only classes (AWT, JAXB, BouncyCastle, etc.)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**
-dontwarn javax.naming.**
-dontwarn javax.xml.bind.**
-dontwarn javax.xml.crypto.**
-dontwarn javax.xml.crypto.dsig.**
-dontwarn javax.xml.crypto.dsig.keyinfo.**
-dontwarn javax.xml.crypto.dsig.spec.**
-dontwarn javax.xml.crypto.dsig.dom.**
-dontwarn org.apache.jcp.xml.dsig.internal.dom.**
-dontwarn org.apache.xml.security.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.junit.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.w3c.dom.events.**
-dontwarn org.etsi.uri.x01903.v13.**
-dontwarn com.graphbuilder.curve.**
-dontwarn com.graphbuilder.geom.**
-dontwarn com.microsoft.schemas.office.visio.x2012.main.**
-dontwarn org.apache.poi.hslf.**
-dontwarn org.apache.poi.hsmf.**
-dontwarn org.apache.poi.hwpf.**
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
# Google Play Services classes - not used (app uses service account auth, not user auth)
-dontwarn com.google.android.gms.**

# Ignore missing service classes referenced by POI
-dontwarn org.codehaus.stax2.validation.XMLValidationSchemaFactory

# Keep Vico chart library
-keep class com.patrykandpatrick.vico.** { *; }

# Remove logging in release builds (optional optimization)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep R class
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep application class
-keep class com.eventmanager.app.MainActivity { *; }

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose



