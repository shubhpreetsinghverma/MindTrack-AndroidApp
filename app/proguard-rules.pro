# MindTrack release shrinking rules.
# Compose, Firebase, Kotlin coroutines and Ktor ship their own consumer rules, so only app-specific
# rules belong here. Classes named in AndroidManifest.xml (activity, receivers) are kept automatically.

# Keep line numbers in crash reports (Crashlytics maps them back with the mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
