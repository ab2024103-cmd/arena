# Morse Code desktop packaging rules (v1.0 reliability).
# The default shrinker removed/renamed the application entrypoint from the
# packaged jars (ClassNotFoundException: net.morsecode.desktop.MainKt at
# launch). Keep all project code untouched; shrink only third-party libs.
-keep class net.morsecode.** { *; }
-keepclassmembers class net.morsecode.** { *; }
-dontwarn net.morsecode.**
