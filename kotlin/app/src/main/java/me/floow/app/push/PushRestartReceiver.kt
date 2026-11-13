package me.floow.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PushRestartReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent?) {
		val action = intent?.action ?: return
		if (action == Intent.ACTION_BOOT_COMPLETED || action == NotificationsKeepAliveService.ACTION_RESTART_PUSH) {
			PushServiceController.start(context)
		}
	}
}
