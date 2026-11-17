-keep class com.thousandlabs.ohara.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
