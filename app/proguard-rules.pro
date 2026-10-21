# libplayer.so (JNI) looks up these classes/methods by name
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.MPVLib$* { *; }
-keepclasseswithmembernames class * { native <methods>; }
