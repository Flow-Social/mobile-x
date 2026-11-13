package me.floow.app.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PushKeepAliveJob(
	appContext: Context,
	workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

	override suspend fun doWork(): Result {
		PushServiceController.ensureService(applicationContext)
		return Result.success()
	}
}
