# Shrinks the installer without renaming anything: names stay readable in the log, nothing reflective breaks.
-dontobfuscate
-dontoptimize

# JNA (desktop pinning, window styles, the Run key) builds native interfaces and structures by reflection.
-keep class com.sun.jna.** { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keepclassmembers class * extends com.sun.jna.Structure { <fields>; <init>(...); }
-dontwarn com.sun.jna.**

# Coroutines find the Swing main dispatcher through ServiceLoader.
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }

-keep class org.json.** { *; }
-keep class com.yonidev.ydeck.** { *; }
