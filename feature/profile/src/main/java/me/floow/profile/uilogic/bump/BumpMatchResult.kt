package me.floow.profile.uilogic.bump

data class BumpMatchResult(
    val matchedUserId: String,
    val conversationId: Long?,
    val helloSent: Boolean,
)
