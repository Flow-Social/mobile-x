package me.floow.shared.platform

@JsFun("(text) => { if (navigator.share) { navigator.share({title: 'Flow', url: text, text: text}); } else { navigator.clipboard.writeText(text); } }")
private external fun jsShareText(text: String)

actual fun systemShareText(text: String) {
    runCatching {
        jsShareText(text)
    }.onFailure {
        println("Share failed: ${it.message}")
    }
}
