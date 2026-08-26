package net.morsecode.shared.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Room / group coordination (Section 8). Rooms are ephemeral: in-memory +
 * mDNS presence only; the creator leaving ends the room.
 */
class RoomManager(
    private val scope: CoroutineScope,
    private val self: SelfProfile,
    private val sessions: SessionManager,
    private val discovery: MdnsDiscovery?,
) {
    data class RoomState(
        val roomId: String,
        val roomToken: String,
        val isCreator: Boolean,
        val members: List<RoomMember>,
    )

    private val _room = MutableStateFlow<RoomState?>(null)
    val room: StateFlow<RoomState?> = _room
    private val knownMembers = CopyOnWriteArrayList<RoomMember>()

    fun createRoom(): RoomState {
        val state = RoomState(Crypto.randomId(), Crypto.randomBytes(8).toHex(), true, listOf(RoomMember(self.deviceId, self.name)))
        knownMembers.clear()
        knownMembers.add(RoomMember(self.deviceId, self.name))
        _room.value = state
        discovery?.updateRoom(state.roomId)
        return state
    }

    /** OPEN JOIN (no approval step): announce ourselves to the room creator. */
    suspend fun joinRoom(roomId: String, roomToken: String, creator: DeviceInfo) {
        knownMembers.clear()
        knownMembers.add(RoomMember(self.deviceId, self.name))
        _room.value = RoomState(roomId, roomToken, false, knownMembers.toList())
        discovery?.updateRoom(roomId)
        val conn = Handshake.initiate(scope, creator.ip, creator.port, self, null, isTrustedRequest = false)
        sessions.register(conn)
        conn.send(RoomAnnounceMsg(roomId, roomToken, self.deviceId, self.name))
    }

    /** Send-to-room (Section 8): returns member targets excluding self. */
    fun memberTargets(discovered: List<DeviceInfo>): List<DeviceInfo> {
        val room = _room.value ?: return emptyList()
        val memberIds = room.members.map { it.device_id }.toSet()
        return discovered.filter { it.deviceId in memberIds && it.deviceId != self.deviceId }
    }

    init {
        sessions.onMessage { conn, msg ->
            when (msg.type) {
                MsgType.ROOM_ANNOUNCE -> {
                    val ann = msg.decodeAs<RoomAnnounceMsg>()
                    val room = _room.value ?: return@onMessage
                    // Open join: room_id match is sufficient on the LAN; token is advisory.
                    if (ann.room_id != room.roomId) return@onMessage
                    if (knownMembers.none { it.device_id == ann.member_device_id }) {
                        knownMembers.add(RoomMember(ann.member_device_id, ann.member_name))
                        publishMembers(conn)
                    }
                }
                MsgType.ROOM_MEMBER_LIST -> {
                    val list = msg.decodeAs<RoomMemberListMsg>()
                    val room = _room.value ?: return@onMessage
                    if (list.room_id != room.roomId) return@onMessage
                    knownMembers.clear()
                    knownMembers.addAll(list.members)
                    _room.value = room.copy(members = knownMembers.toList())
                }
                else -> Unit
            }
        }
    }

    private fun publishMembers(conn: MorseConnection? = null) {
        val room = _room.value ?: return
        val list = RoomMemberListMsg(room.roomId, knownMembers.toList())
        _room.value = room.copy(members = knownMembers.toList())
        runCatching { conn?.send(list) }
        // relay to all other members
        sessions.activePeers.value.forEach { meta ->
            sessions.get(meta.deviceId)?.let { runCatching { it.send(list) } }
        }
    }

    fun leaveRoom() {
        val room = _room.value
        _room.value = null
        knownMembers.clear()
        discovery?.updateRoom(null)
        if (room?.isCreator == true) {
            // creator leaving ends the room
            sessions.closeAll()
        }
    }
}
