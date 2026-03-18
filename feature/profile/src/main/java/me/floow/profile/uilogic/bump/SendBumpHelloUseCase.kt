package me.floow.profile.uilogic.bump

import java.util.UUID
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.utils.Logger

private const val BUMP_HELLO_TAG = "BumpHelloUseCase"
private const val BUMP_HELLO_MESSAGE = "Привет! 💥📱"

open class SendBumpHelloUseCase(
    private val chatsRepository: ChatsRepository,
    private val logger: Logger,
) {
    open suspend operator fun invoke(matchedUserId: String): BumpMatchResult {
        val normalizedUserId = matchedUserId.trim()
        if (normalizedUserId.isBlank()) {
            logger.d(BUMP_HELLO_TAG, "skip blank matchedUserId")
            return BumpMatchResult(
                matchedUserId = matchedUserId,
                conversationId = null,
                helloSent = false,
            )
        }

        return when (val conversationResponse = chatsRepository.getOrCreateDirectConversation(peerUserId = normalizedUserId)) {
            is GetDataResponse.Success -> {
                val conversationId = conversationResponse.data.id
                logger.d(
                    BUMP_HELLO_TAG,
                    "resolve_conversation success matchedUserId=$normalizedUserId conversationId=$conversationId"
                )
                when (
                    chatsRepository.sendMessage(
                        conversationId = conversationId,
                        text = BUMP_HELLO_MESSAGE,
                        clientMessageId = UUID.randomUUID().toString(),
                        replyToMessageId = null,
                    )
                ) {
                    is GetDataResponse.Success -> {
                        logger.d(
                            BUMP_HELLO_TAG,
                            "send_hello success matchedUserId=$normalizedUserId conversationId=$conversationId"
                        )
                        BumpMatchResult(
                            matchedUserId = normalizedUserId,
                            conversationId = conversationId,
                            helloSent = true,
                        )
                    }

                    is GetDataResponse.Error -> {
                        logger.d(
                            BUMP_HELLO_TAG,
                            "send_hello failure matchedUserId=$normalizedUserId conversationId=$conversationId"
                        )
                        BumpMatchResult(
                            matchedUserId = normalizedUserId,
                            conversationId = conversationId,
                            helloSent = false,
                        )
                    }
                }
            }

            is GetDataResponse.Error -> {
                logger.d(
                    BUMP_HELLO_TAG,
                    "resolve_conversation failure matchedUserId=$normalizedUserId conversationId=null"
                )
                BumpMatchResult(
                    matchedUserId = normalizedUserId,
                    conversationId = null,
                    helloSent = false,
                )
            }
        }
    }
}
