package me.floow.shared.platform

/**
 * Invokes the system sharing dialog or a fallback variant (like copying to clipboard).
 */
expect fun systemShareText(text: String)
