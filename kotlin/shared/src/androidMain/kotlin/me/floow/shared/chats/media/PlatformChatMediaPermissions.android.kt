package me.floow.shared.chats.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberChatMediaPermissionRequester(
    onGranted: (ChatMediaPermissionType) -> Unit,
): (ChatMediaPermissionType) -> Boolean {
    val context = LocalContext.current
    val pendingType = remember { mutableStateOf<ChatMediaPermissionType?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val type = pendingType.value ?: return@rememberLauncherForActivityResult
        pendingType.value = null
        val allGranted = when (type) {
            ChatMediaPermissionType.VOICE -> results[Manifest.permission.RECORD_AUDIO] == true
            ChatMediaPermissionType.VIDEO -> (results[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
                (results[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        }
        if (allGranted) {
            onGranted(type)
        }
    }

    return remember(context, launcher) {
        { type ->
            val hasRecordAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

            when (type) {
                ChatMediaPermissionType.VOICE -> {
                    if (hasRecordAudio) {
                        true
                    } else {
                        pendingType.value = type
                        launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        false
                    }
                }
                ChatMediaPermissionType.VIDEO -> {
                    if (hasCamera && hasRecordAudio) {
                        true
                    } else {
                        pendingType.value = type
                        val missing = buildList {
                            if (!hasCamera) add(Manifest.permission.CAMERA)
                            if (!hasRecordAudio) add(Manifest.permission.RECORD_AUDIO)
                        }.toTypedArray()
                        launcher.launch(missing)
                        false
                    }
                }
            }
        }
    }
}
