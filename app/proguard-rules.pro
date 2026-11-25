# Keep reflection-based mobile-data toggling working in release builds.
-keep class com.alwaysdata.app.MobileDataController { *; }
-dontwarn android.os.ServiceManager
