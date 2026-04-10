package me.floow.shared.profile.auth

import kotlin.js.JsName

@JsName("flowWasmApiRequest")
external fun flowWasmApiRequest(
    method: String,
    path: String,
    apiUrl: String,
    authToken: String?,
    contentType: String?,
    body: String?,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowWasmUploadBase64")
external fun flowWasmUploadBase64(
    uploadUrl: String,
    contentType: String,
    base64: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowWasmPickImages")
external fun flowWasmPickImages(
    maxItems: Int,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowWasmPickSingleImage")
external fun flowWasmPickSingleImage(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowWasmReadPickedFile")
external fun flowWasmReadPickedFile(
    fileId: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowWasmChatsRealtimeOpen")
external fun flowWasmChatsRealtimeOpen(
    apiUrl: String,
    authQueryKey: String?,
    authQueryValue: String?,
    conversationId: Long?,
    afterSeq: Long,
    replayLimit: Int,
    onOpen: () -> Unit,
    onEvent: (String) -> Unit,
    onClosed: (String) -> Unit,
    onError: (String) -> Unit,
): String?

@JsName("flowWasmChatsRealtimeSend")
external fun flowWasmChatsRealtimeSend(
    handle: String,
    payloadJson: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowWasmChatsRealtimeClose")
external fun flowWasmChatsRealtimeClose(handle: String)

@JsName("flowWasmPresenceRealtimeOpen")
external fun flowWasmPresenceRealtimeOpen(
    apiUrl: String,
    authQueryKey: String?,
    authQueryValue: String?,
    onOpen: () -> Unit,
    onEvent: (String) -> Unit,
    onClosed: (String) -> Unit,
    onError: (String) -> Unit,
): String?

@JsName("flowWasmPresenceRealtimeSend")
external fun flowWasmPresenceRealtimeSend(
    handle: String,
    payloadJson: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowWasmPresenceRealtimeClose")
external fun flowWasmPresenceRealtimeClose(handle: String)

@JsName("flowWasmCopyText")
external fun flowWasmCopyText(
    text: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
)

external fun atob(value: String): String

external fun btoa(value: String): String
