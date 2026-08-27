package net.morsecode.shared.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.morsecode.shared.chat.ChatMessage
import net.morsecode.shared.media.FileCategorizer
import net.morsecode.shared.net.Crypto
import net.morsecode.shared.net.DEFAULT_CHUNK_SIZE
import net.morsecode.shared.net.DeviceInfo
import net.morsecode.shared.net.FileManifest
import net.morsecode.shared.net.Handshake
import net.morsecode.shared.net.IncomingMessage
import net.morsecode.shared.net.MdnsDiscovery
import net.morsecode.shared.net.MorseConnection
import net.morsecode.shared.net.MorseServer
import net.morsecode.shared.net.OutgoingFile
import net.morsecode.shared.net.PROTO_VERSION
import net.morsecode.shared.net.BroadcastCoordinator
import net.morsecode.shared.net.QrPairPayload
import net.morsecode.shared.net.RoomManager
import net.morsecode.shared.net.SessionManager
import net.morsecode.shared.net.ChatMsgPayload
import net.morsecode.shared.net.TextShareMsg
import net.morsecode.shared.net.TransferRequest
import net.morsecode.shared.net.TransferResponse
import net.morsecode.shared.net.MsgType
import net.morsecode.shared.net.HandshakeRejectedException
import net.morsecode.shared.net.TokenBucket
import net.morsecode.shared.net.TransferReceiver
import net.morsecode.shared.net.ReceiverDelegate
import net.morsecode.shared.net.ChunkSink
import net.morsecode.shared.platform.FileAdapter
import net.morsecode.shared.platform.IncomingSink
import net.morsecode.shared.platform.PickedFile
import net.morsecode.shared.storage.ChatRepo
import net.morsecode.shared.storage.HistoryEntry
import net.morsecode.shared.storage.HistoryRepo
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.storage.TransferStateRepo
import net.morsecode.shared.storage.TrustedDeviceRepo
import net.morsecode.shared.ui.theme.ThemeMode
import net.morsecode.shared.webconnect.SharedSessionFile
import net.morsecode.shared.webconnect.WebConnectServer
import net.morsecode.shared.webconnect.WsChatMsg
import kotlinx.coroutines.delay
import net.morsecode.shared.net.toHex

/** A transfer awaiting the user's accept/reject decision. */
data class IncomingRequestState(
    val transferId: String,
    val peerId: String,
    val peerName: String,
    val files: List<FileManifest>,
    val totalBytes: Long,
)

/** Active send aggregate for the transfer overlay. */
data class SendProgressState(
    val batchId: String,
    val overallPercent: Float,
    val recipients: List<net.morsecode.shared.net.RecipientTransferState>,
)

/** A text/link shared TO us. */
data class IncomingTextState(val id: String, val from: String, val text: String, val ts: Long)

class AppViewModel(val scope: CoroutineScope) {
    private val deps = ServiceLocator.deps
    val profile get() = ServiceLocator.profile
    val fileAdapter: FileAdapter = deps.fileAdapter
    val audio get() = deps.audioController

    val trustedRepo = TrustedDeviceRepo(ServiceLocator.db)
    val historyRepo = HistoryRepo(ServiceLocator.db)
    val chatRepo = ChatRepo(ServiceLocator.db)
    val transferStateRepo = TransferStateRepo(ServiceLocator.db)

    private val sessions = SessionManager(scope)
    private val server = MorseServer(scope, profile, sessions)
    val discovery = MdnsDiscovery(scope)
    val devices: StateFlow<List<DeviceInfo>> get() = discovery.devices
    val roomManager = RoomManager(scope, profile, sessions, discovery)

    // ---- settings ----
    private val _deviceName = MutableStateFlow(profile.name)
    val deviceName: StateFlow<String> = _deviceName
    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(ServiceLocator.settings.get("theme") ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode
    private val _autoAccept = MutableStateFlow(ServiceLocator.settings.getBool("auto_accept", false))
    val autoAccept: StateFlow<Boolean> = _autoAccept
    private val _autoAcceptAll = MutableStateFlow(ServiceLocator.settings.getBool("auto_accept_all", false))
    val autoAcceptAll: StateFlow<Boolean> = _autoAcceptAll
    private val _speedLimitKbps = MutableStateFlow(ServiceLocator.settings.getInt("speed_limit_kbps", 0))
    val speedLimitKbps: StateFlow<Int> = _speedLimitKbps
    private val _includeSystemApps = MutableStateFlow(ServiceLocator.settings.getBool("include_system_apps", false))
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps

    // ---- runtime state ----
    private val _incomingRequest = MutableStateFlow<IncomingRequestState?>(null)
    val incomingRequest: StateFlow<IncomingRequestState?> = _incomingRequest
    private val _incomingText = MutableStateFlow<IncomingTextState?>(null)
    val incomingText: StateFlow<IncomingTextState?> = _incomingText

    fun clearIncomingText() {
        _incomingText.value = null
    }
    private val _sendProgress = MutableStateFlow<SendProgressState?>(null)
    val sendProgress: StateFlow<SendProgressState?> = _sendProgress
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    private val _pairingQr = MutableStateFlow<String?>(null)
    val pairingQr: StateFlow<String?> = _pairingQr

    /** Files queued by Library tabs / APK extraction, consumed by SendScreen. */
    val pendingSendFiles = MutableStateFlow<List<PickedFile>>(emptyList())

    private val throttler: TokenBucket
        get() = TokenBucket(_speedLimitKbps.value)

    val webServer = WebConnectServer(
        chatRepo, historyRepo, fileAdapter,
        onIncomingFile = { path, name, size -> onWebFileReceived(path, name, size) },
        onIncomingChat = { text -> onWebChatReceived(text) },
    )

    private var activePairingToken: String? = null
    private val pendingDecisions = HashMap<String, CompletableDeferred<TransferResponse>>()
    private val activeCoordinators = HashMap<String, BroadcastCoordinator>()

    init {
        server.validateHello = { hello -> decideHello(hello) }
        val port = server.start()
        discovery.start(profile, port, null)
        sessions.onMessage { conn, msg -> routeAppMessage(conn, msg) }
    }

    // =============== discovery / connection ===============

    fun refreshDevices() = discovery.refreshNow()

    fun connect(device: DeviceInfo, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch(Dispatchers.IO) {
            try {
                val conn = Handshake.initiate(
                    scope, device.ip, device.port, profile,
                    pairingToken = null,
                    isTrustedRequest = trustedRepo.isTrusted(device.deviceId),
                )
                sessions.register(conn)
                withContext(Dispatchers.Main) { onDone(true, null) }
            } catch (e: HandshakeRejectedException) {
                withContext(Dispatchers.Main) { onDone(false, e.reason) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onDone(false, e.message) }
            }
        }
    }

    /** Connect by scanned QR payload (join flow). */
    fun connectByQr(qrContent: String, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        val payload = runCatching {
            net.morsecode.shared.net.MsgJson.json.decodeFromString(QrPairPayload.serializer(), qrContent)
        }.getOrNull() ?: run { onDone(false, "invalid_qr"); return }
        val device = DeviceInfo(
            deviceId = payload.device_id, name = payload.device_name,
            deviceType = "unknown", ip = payload.ip, port = payload.port,
        )
        scope.launch(Dispatchers.IO) {
            try {
                val conn = Handshake.initiate(
                    scope, payload.ip, payload.port, profile,
                    pairingToken = payload.pairing_token,
                    isTrustedRequest = trustedRepo.isTrusted(payload.device_id),
                )
                sessions.register(conn)
                withContext(Dispatchers.Main) { onDone(true, null) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onDone(false, e.message ?: "connect_failed") }
            }
        }
    }

    /** Generates a fresh pairing token and returns the QR payload JSON to show. */
    fun newPairingQr(): String? {
        val ip = localIp() ?: return null
        val token = Crypto.randomBytes(8).toHex()
        activePairingToken = token
        val payload = QrPairPayload(1, profile.deviceId, profile.name, ip, server.port, token)
        _pairingQr.value = net.morsecode.shared.net.MsgJson.json.encodeToString(QrPairPayload.serializer(), payload)
        scope.launch {
            kotlinx.coroutines.delay(5 * 60 * 1000)
            if (activePairingToken == token) activePairingToken = null
        }
        return _pairingQr.value
    }

    fun clearPairingQr() {
        activePairingToken = null
        _pairingQr.value = null
    }

    private fun decideHello(hello: net.morsecode.shared.net.Hello): Handshake.HelloAcceptance {
        if (hello.proto_version != PROTO_VERSION) return Handshake.HelloAcceptance(false, "protocol_version_mismatch")
        val token = hello.pairing_token
        if (token != null && token != activePairingToken) {
            return Handshake.HelloAcceptance(false, "invalid_pairing_token")
        }
        return Handshake.HelloAcceptance(true, null)
    }

    private fun localIp(): String? = WebConnectServer.lanAddress()

    // =============== sending ===============

    /** Sends picked files to the given recipients (Sections 5 & 7). */
    fun sendFiles(targets: List<DeviceInfo>, files: List<PickedFile>, viaRoom: Boolean = false) {
        if (targets.isEmpty() || files.isEmpty()) return
        val throttle = throttler
        val outgoing = files.map { f ->
            OutgoingFile(
                displayName = f.displayName,
                sizeBytes = f.sizeBytes,
                mime = f.mime,
                openChunk = { fileId, chunkIndex, chunkSize ->
                    fileAdapter.open(f.uri).use { s ->
                        val skipped = s.skip(chunkIndex.toLong() * chunkSize)
                        val buf = ByteArray(chunkSize)
                        var total = 0
                        while (total < chunkSize) {
                            val n = s.read(buf, total, chunkSize - total)
                            if (n < 0) break
                            total += n
                        }
                        buf.copyOf(total)
                    }
                },
            )
        }
        val coordinator = BroadcastCoordinator(
            scope, profile, targets, outgoing,
            pairingToken = null,
            isTrusted = { trustedRepo.isTrusted(it.deviceId) },
            throttle = throttle,
        )
        activeCoordinators[coordinator.batchId] = coordinator
        _sendProgress.value = SendProgressState(coordinator.batchId, 0f, coordinator.states.value)
        scope.launch {
            coordinator.states.collect { states ->
                _sendProgress.value = _sendProgress.value?.copy(
                    overallPercent = states.map { it.percent }.average().toFloat(),
                    recipients = states,
                )
                if (states.all { it.state == "completed" || it.state == "failed" || it.state == "rejected" }) {
                    // record one history entry per file (batch)
                    val ts = System.currentTimeMillis()
                    for (f in files) {
                        historyRepo.add(
                            HistoryEntry(
                                id = Crypto.randomId(), batchId = coordinator.batchId,
                                peerDeviceId = states.joinToString(",") { it.deviceId },
                                peerName = states.joinToString(", ") { it.deviceName },
                                filename = f.displayName, sizeBytes = f.sizeBytes,
                                direction = "sent", kind = "file", mime = f.mime, source = null,
                                status = if (states.any { it.state == "completed" }) "completed" else "failed",
                                ts = ts,
                            ),
                        )
                    }
                    kotlinx.coroutines.delay(4000)
                    _sendProgress.value = null
                    activeCoordinators.remove(coordinator.batchId)
                }
            }
        }
        coordinator.start()
    }

    /** Open join from a discovered room-advertising device (Section 8). */
    fun joinRoomOpen(advertiser: DeviceInfo) {
        val roomId = advertiser.roomId ?: run { toast("Device is not advertising a room"); return }
        scope.launch(Dispatchers.IO) {
            try {
                roomManager.joinRoom(roomId, "", advertiser)
                toast("Joined ${advertiser.name}'s room")
            } catch (e: Exception) {
                toast("Join failed: ${e.message}")
            }
        }
    }

    fun dismissSendProgress() {
        _sendProgress.value = null
    }

    fun sendText(target: DeviceInfo, text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val conn = ensureConnection(target)
                conn.send(TextShareMsg(text, System.currentTimeMillis()))
                historyRepo.add(
                    HistoryEntry(
                        id = Crypto.randomId(), batchId = null,
                        peerDeviceId = target.deviceId, peerName = target.name,
                        filename = text.take(60), sizeBytes = text.length.toLong(),
                        direction = "sent", kind = "text", mime = "text/plain", source = null,
                        status = "completed", ts = System.currentTimeMillis(),
                    ),
                )
                toast("Text sent to ${target.name}")
            } catch (e: Exception) {
                toast("Text send failed: ${e.message}")
            }
        }
    }

    // =============== receiving (delegate) ===============

    private val receiverDelegate = object : ReceiverDelegate {
        private val resumeHelper: TransferStateRepo get() = transferStateRepo

        override suspend fun decide(request: TransferRequest): TransferResponse {
            // 1. Files already fully received in a previous session are accepted with resume.
            val acceptedIds = ArrayList<String>()
            val resumeOffsets = HashMap<String, Int>()
            val needsUser = ArrayList<FileManifest>()
            for (f in request.files) {
                val last = resumeHelper.lastContiguousVerified(request.transfer_id, f.file_id)
                if (last >= f.total_chunks - 1) {
                    acceptedIds.add(f.file_id)
                    resumeOffsets[f.file_id] = last
                } else {
                    needsUser.add(f)
                }
            }

            if (needsUser.isNotEmpty()) {
                val autoOk = _autoAccept.value && (_autoAcceptAll.value || isTrustedPeer())
                if (autoOk) {
                    acceptedIds += needsUser.map { it.file_id }
                } else {
                    val deferred = CompletableDeferred<TransferResponse>()
                    synchronized(pendingDecisions) { pendingDecisions[request.transfer_id] = deferred }
                    _incomingRequest.value = IncomingRequestState(
                        transferId = request.transfer_id,
                        peerId = currentPeerDeviceId ?: "unknown",
                        peerName = currentPeerName ?: "device",
                        files = request.files,
                        totalBytes = request.files.sumOf { it.size_bytes },
                    )
                    val userResponse = try {
                        deferred.await()
                    } finally {
                        synchronized(pendingDecisions) { pendingDecisions.remove(request.transfer_id) }
                        _incomingRequest.value = null
                    }
                    // merge: keep already-complete files, apply user's choice to the rest
                    val finalAccepted = LinkedHashSet(acceptedIds)
                    val finalResume = HashMap(resumeOffsets)
                    for (f in needsUser) {
                        if (f.file_id in userResponse.accepted_file_ids) finalAccepted.add(f.file_id)
                    }
                    return TransferResponse(
                        transfer_id = request.transfer_id,
                        decision = when {
                            finalAccepted.size == request.files.size -> "accept_all"
                            finalAccepted.isEmpty() -> "reject_all"
                            else -> "partial"
                        },
                        accepted_file_ids = finalAccepted.toList(),
                        rejected_file_ids = request.files.map { it.file_id }.filter { it !in finalAccepted },
                        resume_offsets = finalResume,
                    )
                }
            }
            return TransferResponse(
                transfer_id = request.transfer_id,
                decision = if (acceptedIds.size == request.files.size) "accept_all" else "partial",
                accepted_file_ids = acceptedIds,
                rejected_file_ids = request.files.map { it.file_id }.filter { it !in acceptedIds.toSet() },
                resume_offsets = resumeOffsets,
            )
        }

        private fun isTrustedPeer(): Boolean =
            currentPeerDeviceId?.let { trustedRepo.isTrusted(it) } ?: false

        override suspend fun openSink(
            manifest: FileManifest,
            transferId: String,
        ): ChunkSink = SyncChunkSink(fileAdapter, manifest)

        override suspend fun onFileFinished(manifest: FileManifest, ok: Boolean, path: String?, reason: String?) {
            historyRepo.add(
                HistoryEntry(
                    id = Crypto.randomId(), batchId = null,
                    peerDeviceId = currentPeerDeviceId ?: "unknown",
                    peerName = currentPeerName ?: "device", filename = manifest.filename,
                    sizeBytes = manifest.size_bytes, direction = "received", kind = "file",
                    mime = manifest.mime_type, source = null,
                    path = path,
                    status = if (ok) "completed" else "failed",
                    ts = System.currentTimeMillis(),
                ),
            )
            if (ok) toast("Received ${manifest.filename}")
            resumeHelper.setStatus(currentTransferId ?: "", manifest.file_id, if (ok) "completed" else "failed")
        }

        override suspend fun persistVerified(transferId: String, fileId: String, chunkIndex: Int, totalChunks: Int) {
            resumeHelper.markVerified(transferId, fileId, chunkIndex, totalChunks)
        }

        override suspend fun resumeOffset(transferId: String, fileId: String): Int =
            resumeHelper.lastContiguousVerified(transferId, fileId)

        override suspend fun verifyFullFile(path: String, expectedSha256: String): Boolean {
            if (expectedSha256.isBlank()) return true
            return try {
                val file = if (path.startsWith("file:")) java.io.File(java.net.URI(path)) else java.io.File(path)
                if (file.exists()) Crypto.sha256Hex(file.inputStream()) == expectedSha256 else true
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Synchronous random-access sink bridging FileAdapter (receiver runs on IO). */
    private class SyncChunkSink(
        private val adapter: FileAdapter,
        private val manifest: FileManifest,
    ) : ChunkSink {
        private val sink = adapter.incomingSink(manifest.filename, manifest.size_bytes, manifest.mime_type)
        override val displayPath: String = sink.displayPath

        override fun writeAt(chunkIndex: Int, bytes: ByteArray) {
            sink.writeAt(chunkIndex.toLong() * manifest.chunk_size, bytes)
        }

        override fun complete(ok: Boolean) {
            runCatching { sink.complete(ok) }
        }
    }

    private var currentPeerDeviceId: String? = null
    private var currentPeerName: String? = null
    private var currentTransferId: String? = null

    /** User answered the incoming-transfer dialog. */
    fun resolveIncomingRequest(acceptAll: Boolean, rejectedIds: Set<String> = emptySet()) {
        val state = _incomingRequest.value ?: return
        val accepted = if (acceptAll) state.files.map { it.file_id } else state.files.map { it.file_id } - rejectedIds
        val response = TransferResponse(
            transfer_id = state.transferId,
            decision = if (acceptAll) "accept_all" else if (accepted.isEmpty()) "reject_all" else "partial",
            accepted_file_ids = accepted,
            rejected_file_ids = state.files.map { it.file_id } - accepted.toSet(),
            resume_offsets = emptyMap(),
        )
        synchronized(pendingDecisions) { pendingDecisions[state.transferId] }?.complete(response)
            ?: run {
                // decision arrived after receiver timed out; nothing to do
            }
    }

    // =============== app-level message routing ===============

    private fun routeAppMessage(conn: MorseConnection, msg: IncomingMessage) {
        when (msg.type) {
            MsgType.CHAT_MESSAGE -> {
                val chat = msg.decodeAs<ChatMsgPayload>()
                chatRepo.insert(
                    ChatMessage(
                        messageId = chat.message_id, peerDeviceId = conn.peer.deviceId, text = chat.text,
                        direction = "received", sentAt = chat.sent_at, delivered = true,
                    ),
                )
                // MVP delivery model: confirmed once the TCP write of the message succeeded
                // on the sender side (LAN reliability); no ack frame is sent back.
            }
            MsgType.TEXT_SHARE -> {
                val share = msg.decodeAs<TextShareMsg>()
                _incomingText.value = IncomingTextState(
                    id = Crypto.randomId(), from = conn.peer.name, text = share.text, ts = share.sent_at,
                )
                historyRepo.add(
                    HistoryEntry(
                        id = Crypto.randomId(), batchId = null, peerDeviceId = conn.peer.deviceId,
                        peerName = conn.peer.name, filename = share.text.take(60),
                        sizeBytes = share.text.length.toLong(), direction = "received", kind = "text",
                        mime = "text/plain", source = null, status = "completed", ts = share.sent_at,
                    ),
                )
            }
            MsgType.TRANSFER_REQUEST -> {
                val request = msg.decodeAs<TransferRequest>()
                currentPeerDeviceId = conn.peer.deviceId
                currentPeerName = conn.peer.name
                currentTransferId = request.transfer_id
                scope.launch(Dispatchers.IO) {
                    val incoming = sessions.subscribe(conn.peer.deviceId)
                    try {
                        val receiver = TransferReceiver(request, conn, incoming, receiverDelegate)
                        receiver.run()
                    } catch (e: Exception) {
                        toast("Transfer error: ${e.message}")
                    } finally {
                        sessions.unsubscribe(conn.peer.deviceId, incoming)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun onWebFileReceived(path: String, name: String, size: Long) {
        toast("Received $name via Web Connect")
    }

    private fun onWebChatReceived(text: String) {
        // chatRepo already persisted by WebConnectServer.pushNativeChat
    }

    // =============== chat ===============

    fun ensureConnection(target: DeviceInfo): MorseConnection {
        sessions.get(target.deviceId)?.let { return it }
        return runBlocking {
            val conn = Handshake.initiate(
                scope, target.ip, target.port, profile, null,
                isTrustedRequest = trustedRepo.isTrusted(target.deviceId),
            )
            sessions.register(conn)
            conn
        }
    }

    fun sendChat(peer: DeviceInfo, text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val conn = ensureConnection(peer)
                val msg = ChatMsgPayload(
                    message_id = Crypto.randomId(), text = text.take(1_000_000),
                    sent_at = System.currentTimeMillis(), sender_device_id = profile.deviceId,
                )
                conn.send(msg)
                chatRepo.insert(
                    ChatMessage(
                        messageId = msg.message_id, peerDeviceId = peer.deviceId, text = msg.text,
                        direction = "sent", sentAt = msg.sent_at, delivered = true,
                    ),
                )
            } catch (e: Exception) {
                toast("Chat send failed: ${e.message}")
            }
        }
    }

    fun chatWith(peerId: String): List<ChatMessage> = chatRepo.thread(peerId)

    // =============== settings ===============

    fun setDeviceName(name: String) {
        ServiceLocator.setDeviceName(name)
        _deviceName.value = name
        discovery.start(profile, server.port, roomManager.room.value?.roomId)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        ServiceLocator.settings.put("theme", mode.name)
    }

    fun setAutoAccept(enabled: Boolean) {
        _autoAccept.value = enabled
        ServiceLocator.settings.putBool("auto_accept", enabled)
    }

    fun setAutoAcceptAll(all: Boolean) {
        _autoAcceptAll.value = all
        ServiceLocator.settings.putBool("auto_accept_all", all)
    }

    fun setSpeedLimit(kbps: Int) {
        _speedLimitKbps.value = kbps
        ServiceLocator.settings.putInt("speed_limit_kbps", kbps)
    }

    fun setIncludeSystemApps(v: Boolean) {
        _includeSystemApps.value = v
        ServiceLocator.settings.putBool("include_system_apps", v)
    }

    fun installReceivedApk(entry: net.morsecode.shared.storage.HistoryEntry) {
        val path = entry.path
        if (path == null) {
            toast("File path unavailable")
            return
        }
        net.morsecode.shared.platform.installApk(path)
    }

    /** Extract base APKs for the selected apps and queue a send (E.4, MVP: base APK only). */
    suspend fun extractApps(apps: List<net.morsecode.shared.media.AppInfo>): List<PickedFile> =
        apps.mapNotNull { net.morsecode.shared.platform.extractAppApk(it) }

    fun trustDevice(deviceId: String, name: String) {
        trustedRepo.trust(deviceId, name)
        toast("Trusted $name")
    }

    fun forgetDevice(deviceId: String) = trustedRepo.forget(deviceId)

    fun toast(message: String?) {
        _toast.value = message
        scope.launch {
            kotlinx.coroutines.delay(3500)
            if (_toast.value == message) _toast.value = null
        }
    }

    fun shutdown() {
        server.stop()
        discovery.stop()
        webServer.stop()
        sessions.closeAll()
        roomManager.leaveRoom()
    }
}
