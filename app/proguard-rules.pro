# OpenCV's Java classes are called back from native code, so their names must survive shrinking.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# ML Kit, CameraX, Room and Compose ship their own consumer rules.
-dontwarn com.google.errorprone.annotations.**
