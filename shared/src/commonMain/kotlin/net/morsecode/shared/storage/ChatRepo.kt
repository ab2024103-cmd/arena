package net.morsecode.shared.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.morsecode.db.MorseDb
import net.morsecode.shared.chat.ChatMessage

class ChatRepo(private val db: MorseDb) {
    private val _threads = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val threads: StateFlow<Map<String, List<ChatMessage>>> = _threads

    init { reloadAll() }

    fun reloadAll() {
        val peers = db.chatQueries.chatPeers().executeAsList().map { it.peer_device_id }
        val map = HashMap<String, List<ChatMessage>>()
        for (p in peers) map[p] = thread(p)
        _threads.value = map
    }

    fun thread(peerDeviceId: String): List<ChatMessage> =
        db.chatQueries.chatForPeer(peerDeviceId).executeAsList().map {
            ChatMessage(it.message_id, it.peer_device_id, it.text, it.direction, it.sent_at, it.delivered == 1L)
        }

    fun insert(msg: ChatMessage) {
        db.chatQueries.insertChat(
            msg.messageId, msg.peerDeviceId, msg.text, msg.direction, msg.sentAt,
            if (msg.delivered) 1L else 0L,
        ).execute()
        reloadAll()
    }

    fun markDelivered(messageId: String) {
        db.chatQueries.markDelivered(messageId).execute()
        reloadAll()
    }
}
