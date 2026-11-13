package me.floow.app.push

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NotificationsKeepAliveService : Service() {

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		val restart = Intent(ACTION_RESTART_PUSH)
		restart.setPackage(packageName)
		sendBroadcast(restart)
	}

	internal companion object {
		const val ACTION_RESTART_PUSH = "me.floow.app.RESTART_PUSH"
	}
}
