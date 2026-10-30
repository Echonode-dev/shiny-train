# CleanDroid R8 rules.
# The app uses no reflection-based libraries (JSON is parsed with the platform
# org.json, no DI, no serialization frameworks), so almost nothing is kept.

# Keep line numbers in stack traces for crash reports.
-keepattributes SourceFile,LineNumberTable

# Coroutines debug metadata stripping is safe; keep default behavior.
