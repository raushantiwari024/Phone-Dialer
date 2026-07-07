# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\ca\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# Add any project specific keep options here:

# Optimization is enabled in build.gradle.kts, but we should ensure
# standard Android classes aren't over-optimized.
-keep class com.raushan.phone.telecom.MyInCallService { *; }

# Keep data models used for serialization or Reflection
-keep class com.raushan.phone.data.models.** { *; }

# Jetpack Compose specific rules (usually handled by R8 but good to have)
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    *** onCheckIsTextEditor(...);
}

# Keep the ViewModel constructor for the viewModel() delegate
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Maintain line numbers for easier debugging of crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
