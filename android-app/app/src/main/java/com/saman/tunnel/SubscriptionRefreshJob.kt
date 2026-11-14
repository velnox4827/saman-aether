package com.saman.tunnel

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.Future

class SubscriptionRefreshJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var work: Future<*>? = null
    private var generation = 0
    companion object {
        const val JOB_ID = 22006
        fun schedule(context: Context): Boolean {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (scheduler.getPendingJob(JOB_ID) != null) return true
            return scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, SubscriptionRefreshJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(6 * 60 * 60 * 1000L).build()) == JobScheduler.RESULT_SUCCESS
        }
    }
    override fun onStartJob(params: JobParameters): Boolean {
        val token = ++generation
        work = executor.submit {
            var retry = false
            try {
                val store = ConfigStore(this)
                val sources = store.snapshot().optJSONArray("subscriptions")
                if (sources != null) for (i in 0 until sources.length()) {
                    if (Thread.currentThread().isInterrupted) break
                    val source = sources.getJSONObject(i)
                    val url = source.optString("url")
                    if (url.isNotEmpty()) try { SubscriptionFetcher.refresh(store, source.getString("id"), url) }
                    catch (_: Exception) { retry = true }
                }
            } catch (_: Exception) { retry = true }
            android.os.Handler(mainLooper).post {
                if (generation == token) { work = null; jobFinished(params, retry) }
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        generation++
        work?.cancel(true)
        work = null
        return true
    }
    override fun onDestroy() {
        generation++
        work?.cancel(true)
        executor.shutdownNow()
        super.onDestroy()
    }
}
