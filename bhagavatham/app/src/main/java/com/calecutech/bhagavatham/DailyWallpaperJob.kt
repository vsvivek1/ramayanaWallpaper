package com.calecutech.bhagavatham

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs every few hours. It changes the wallpaper once per day (the first run after
 * midnight) and does nothing on the other runs, so the change happens soon after the
 * date changes without needing an exact alarm.
 */
class DailyWallpaperJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartJob(params: JobParameters): Boolean {
        executor.execute {
            try {
                WallpaperSetter.applyTodayIfNeeded(applicationContext)
            } catch (_: Exception) {
                // Try again on the next run.
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 1001

        fun schedule(c: Context) {
            val js = c.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(c, DailyWallpaperJob::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(2), TimeUnit.MINUTES.toMillis(30))
                .setPersisted(true)
                .build()
            js.schedule(job)
        }

        fun cancel(c: Context) {
            c.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}

/** Re-creates the schedule after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.dailyEnabled(context)) DailyWallpaperJob.schedule(context)
    }
}
