package me.floow.profile.ui.profile.bump

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val BUMP_MANUFACTURER_ID = 0x02E5

@Composable
fun BumpBleProximityEffect(
    enabled: Boolean,
    advertiseToken: String?,
    onPeerTokenDetected: (token: String, rssi: Int) -> Unit,
) {
    val context = LocalContext.current
    val bluetoothAdapter = remember(context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    DisposableEffect(enabled, advertiseToken, bluetoothAdapter) {
        if (!enabled || advertiseToken.isNullOrBlank() || bluetoothAdapter == null) {
            onDispose { }
        } else if (!hasBlePermission(context) || !bluetoothAdapter.isEnabled) {
            onDispose { }
        } else {
            val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (advertiser == null || scanner == null) {
                onDispose { }
            } else {
                val tokenBytes = tokenToBlePayload(advertiseToken)
                val advertiseSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(false)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build()
                val advertiseData = AdvertiseData.Builder()
                    // Keep payload tiny, otherwise ADVERTISE_FAILED_DATA_TOO_LARGE on some devices.
                    .addManufacturerData(BUMP_MANUFACTURER_ID, tokenBytes)
                    .setIncludeDeviceName(false)
                    .build()
                val advertiseCallback = object : AdvertiseCallback() {
                    override fun onStartFailure(errorCode: Int) {
                        Log.w("BumpBleProximity", "BLE advertise start failed code=$errorCode")
                    }
                }

                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                val scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        val record = result?.scanRecord ?: return
                        val manufacturerData = record.getManufacturerSpecificData(BUMP_MANUFACTURER_ID) ?: return
                        val token = blePayloadToToken(manufacturerData)
                        if (token.isBlank() || token == advertiseToken) return
                        onPeerTokenDetected(token, result.rssi)
                    }
                }

                runCatching {
                    advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
                    scanner.startScan(null, scanSettings, scanCallback)
                }

                onDispose {
                    runCatching { advertiser.stopAdvertising(advertiseCallback) }
                    runCatching { scanner.stopScan(scanCallback) }
                }
            }
        }
    }
}

private fun tokenToBlePayload(token: String): ByteArray {
    val normalized = token.trim().lowercase()
    if (normalized.length >= 2 && normalized.length % 2 == 0 && normalized.all { it in '0'..'9' || it in 'a'..'f' }) {
        val output = ByteArray(normalized.length / 2)
        var i = 0
        while (i < normalized.length) {
            output[i / 2] = normalized.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return output
    }
    return normalized.take(8).toByteArray(Charsets.UTF_8)
}

private fun blePayloadToToken(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    val builder = StringBuilder(payload.size * 2)
    payload.forEach { byte ->
        val v = byte.toInt() and 0xFF
        if (v < 16) builder.append('0')
        builder.append(v.toString(16))
    }
    return builder.toString()
}

private fun hasBlePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val hasAdvertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        hasScan && hasAdvertise && hasConnect
    } else {
        val hasBluetooth = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        val hasBluetoothAdmin = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        hasBluetooth && hasBluetoothAdmin
    }
}
