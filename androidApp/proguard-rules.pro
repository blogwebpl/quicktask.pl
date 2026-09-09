# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# JNA keep rules
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Cryptography / Opaque
-keep class dev.whyoleg.cryptography.** { *; }
-keep class pl.quicktask.todo.auth.** { *; }