# HomeGuard R8 / ProGuard rules
#
# Room, WorkManager, and the Supabase auth flow all resolve classes reflectively or by name.
# R8 cannot see those references, so without these rules a minified release build fails at
# runtime rather than at compile time.

# --- Room -----------------------------------------------------------------
# Room generates an implementation per @Database and reads its identity hash reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- WorkManager ----------------------------------------------------------
# Workers are instantiated by name from the WorkManager database.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }
-keepnames class androidx.work.Worker
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# --- kotlinx.serialization / org.json -------------------------------------
# JSONObject field names are read reflectively by name.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# --- Kotlin coroutines ----------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Retrofit-free HTTP layer --------------------------------------------
# Kept intentionally minimal: networking is raw HttpURLConnection + org.json, so nothing here
# depends on OkHttp or Retrofit annotations.

# Supabase auth responses are parsed into anonymous JSON objects; keep member names intact.
-keepclassmembers class org.json.** { *; }