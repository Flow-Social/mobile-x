package me.floow.shared.push

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSData
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localTimeZone

object IosPushNotificationHandler {
    @OptIn(ExperimentalForeignApi::class)
    fun deviceTokenToHexString(deviceToken: NSData): String {
        val length = deviceToken.length.toInt()
        val bytes = deviceToken.bytes ?: return ""
        val hexChars = StringBuilder(length * 2)
        val bytePtr = bytes.reinterpret<kotlinx.cinterop.ByteVar>()
        for (i in 0 until length) {
            val byte = bytePtr[i].toInt() and 0xFF
            val hex = byte.toString(16)
            if (hex.length < 2) {
                hexChars.append('0')
            }
            hexChars.append(hex)
        }
        return hexChars.toString()
    }

    suspend fun registerDeviceToken(
        registrar: PushTokenRegistrar,
        deviceToken: NSData,
        appVersion: String = "1.0.0",
    ): Boolean {
        val hexString = deviceTokenToHexString(deviceToken)
        if (hexString.isBlank()) return false
        val locale = NSLocale.currentLocale.languageCode
        val timezone = NSTimeZone.localTimeZone.name
        return registrar.register(
            token = hexString,
            platform = "ios",
            appVersion = appVersion,
            locale = locale,
            timezone = timezone,
        )
    }

    private var pendingDeviceToken: NSData? = null

    fun onTokenReceived(deviceToken: NSData) {
        val koin = KoinPlatformTools.defaultContext().getOrNull()
        if (koin == null) {
            pendingDeviceToken = deviceToken
            return
        }
        val registrar = koin.getOrNull<PushTokenRegistrar>()
        if (registrar == null) {
            pendingDeviceToken = deviceToken
            return
        }
        pendingDeviceToken = null
        CoroutineScope(Dispatchers.Main).launch {
            registerDeviceToken(registrar = registrar, deviceToken = deviceToken)
        }
    }

    fun flushPendingToken() {
        val token = pendingDeviceToken ?: return
        val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
        val registrar = koin.getOrNull<PushTokenRegistrar>() ?: return
        pendingDeviceToken = null
        CoroutineScope(Dispatchers.Main).launch {
            registerDeviceToken(registrar = registrar, deviceToken = token)
        }
    }
}

fun onIosPushTokenReceived(deviceToken: NSData) {
    IosPushNotificationHandler.onTokenReceived(deviceToken)
}

fun flushPendingIosPushToken() {
    IosPushNotificationHandler.flushPendingToken()
}
