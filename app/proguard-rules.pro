# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# jcifs-ng (SMB) gets its crypto from BouncyCastle. The provider loads its algorithms by class name,
# so those packages must stay, but the rest of BouncyCastle (post-quantum, x509, ...) can be shrunk.
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

-dontwarn jcifs.**
-keep class jcifs.** { *; }

# NewPipe Extractor (YouTube): Rhino references desktop-only classes and builds scripts by reflection,
# and the extractor loads its time-ago patterns and protobuf messages by name.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
