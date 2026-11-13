package me.floow.api.util

import android.content.Context

object AndroidApiContext {
    private var appContext: Context? = null

    fun set(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context? = appContext
}
