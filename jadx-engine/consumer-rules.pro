# jadx is reflective (plugin API, option classes, enums) - keep it whole.
-keep class jadx.** { *; }
-keepclassmembers enum jadx.** { *; }

# Desktop-only / annotation-only types referenced by jadx or its libraries; absent on Android by design.
-dontwarn javax.swing.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.intellij.lang.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.slf4j.impl.**
