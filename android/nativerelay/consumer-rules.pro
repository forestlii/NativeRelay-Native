# NativeRelay JNI relay — consumer ProGuard/R8 rules (packaged into the .aar).
#
# Unity's C# side (AndroidChannel.cs) reaches this Java code purely by REFLECTION via
# AndroidJavaObject / AndroidJavaProxy: it resolves the class by name, the inner callback
# interface by name, and calls send/dispose/onResult by name. If the consuming app enables
# R8/ProGuard (minifyEnabled true) and these symbols get renamed or stripped, the relay
# breaks at runtime with no compile error. These keep rules ship inside the .aar so the
# Unity project applies them automatically.

-keep class com.likeon.nativerelay.NativeRelayChannel { *; }
-keep interface com.likeon.nativerelay.NativeRelayChannel$ResultCallback { *; }
