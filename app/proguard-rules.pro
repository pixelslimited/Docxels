# OpenCV's Java classes are called back from native code, so their names must survive shrinking.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# ML Kit, CameraX, Room and Compose ship their own consumer rules.
-dontwarn com.google.errorprone.annotations.**

# Remove verbose / debug / info logging from release builds so nothing about documents can end up in logs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# PdfBox-Android: optional JPEG2000 decoder and desktop-only classes are not shipped.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
