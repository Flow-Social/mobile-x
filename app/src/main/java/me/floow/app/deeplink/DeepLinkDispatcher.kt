package me.floow.app.deeplink

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeepLinkDispatcher {
    private val _intentFlow = MutableStateFlow<Intent?>(null)
    val intentFlow: StateFlow<Intent?> = _intentFlow.asStateFlow()

    fun push(intent: Intent) {
        _intentFlow.value = intent
    }

    fun clear() {
        _intentFlow.value = null
    }
}
