package me.floow.app

import android.app.Activity
import me.floow.domain.app.AppController

class AppControllerImpl(
	private val activity: Activity
) : AppController {
	
	override fun closeApp() {
		activity.finishAffinity()
	}
}