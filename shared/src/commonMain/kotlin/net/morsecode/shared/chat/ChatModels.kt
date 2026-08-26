package net.morsecode.shared.chat

data class ChatMessage(
    val messageId: String,
    val peerDeviceId: String,
    val text: String,
    val direction: String, // sent | received
    val sentAt: Long,
    val delivered: Boolean,
)

data class ChatThreadSummary(
    val peerDeviceId: String,
    val peerName: String,
    val lastText: String,
    val lastAt: Long,
)
