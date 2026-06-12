# Retrofit relies on runtime method annotations and generic signatures.
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, InnerClasses, EnclosingMethod
-keep interface com.codex.suishouledger.data.remote.ApiService { *; }
-keep class com.codex.suishouledger.data.remote.** { *; }

# Room and Gson touch model fields reflectively; keep the local data model stable.
-keep class com.codex.suishouledger.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# WorkManager instantiates workers by class name.
-keep class com.codex.suishouledger.work.** extends androidx.work.ListenableWorker { *; }

# Huawei ML Kit performs SDK-internal reflection.
-keep class com.huawei.** { *; }
-dontwarn com.huawei.**
