# R8 rules for Bhagavatham Wallpapers. The app uses no reflection, so only the
# components Android instantiates by name need to keep their names.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.job.JobService
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**
