package me.floow.shared.runtime

import me.floow.domain.utils.Logger

class WasmLogger : Logger {
    override fun d(tag: String?, message: String) {
        println("${tag ?: "WasmLogger"}: $message")
    }
}
