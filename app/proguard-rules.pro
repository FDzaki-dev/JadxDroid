# Keep line numbers so raw stack traces written by CrashLogger stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# logback-android resolves appenders/encoders from assets/logback.xml by class name.
-keep class ch.qos.logback.** { *; }
-dontwarn ch.qos.logback.**
