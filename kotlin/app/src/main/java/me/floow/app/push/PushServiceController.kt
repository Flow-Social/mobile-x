package me.floow.app.push

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PushServiceController {

	private const val PERIODIC_WORK_NAME = "push_keep_alive"

	fun start(context: Context) {
		ensureService(context)
		ensurePeriodicJob(context)
	}

	internal fun ensureService(context: Context) {
		val intent = Intent(context, NotificationsKeepAliveService::class.java)
		try {
			context.startService(intent)
		} catch (_: Exception) {
		}
	}

	private fun ensurePeriodicJob(context: Context) {
		val work = PeriodicWorkRequestBuilder<PushKeepAliveJob>(15, TimeUnit.MINUTES)
			.build()
		WorkManager.getInstance(context)
			.enqueueUniquePeriodicWork(
				PERIODIC_WORK_NAME,
				ExistingPeriodicWorkPolicy.KEEP,
				work
			)
	}
}
