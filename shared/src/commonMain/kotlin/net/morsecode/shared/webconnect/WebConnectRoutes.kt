package net.morsecode.shared.webconnect

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import net.morsecode.shared.storage.ServiceLocator
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.readText
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.morsecode.shared.net.Crypto
import net.morsecode.shared.storage.HistoryEntry

@Serializable
data class WsChatMsg(
    val message_id: String,
    val text: String,
    val sent_at: Long,
    val direction: String = "received",
    val sender: String = "browser",
)

private val wsJson = Json { ignoreUnknownKeys = true }

fun resourceBytes(name: String): ByteArray =
    WebConnectServer::class.java.getResourceAsStream(name)?.readBytes()
        ?: "<h1>Morse Code Web Connect</h1><p>Frontend resource missing: $name</p>".toByteArray()

private fun jsonEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

suspend fun ApplicationCall.requireSession(server: WebConnectServer): Boolean {
    val cookie = request.cookies["morse_session"]
    if (server.pairing.isValidSession(cookie)) return true
    respondText("{\"ok\":false,\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
    return false
}

fun Application.webConnectModule(server: WebConnectServer) {
    install(io.ktor.server.websocket.WebSockets)
    routing {
        get("/") {
            call.respondBytes(resourceBytes("/webapp/index.html"), ContentType.Text.Html)
        }
        get("/style.css") {
            call.respondBytes(resourceBytes("/webapp/style.css"), ContentType.Text.CSS)
        }
        get("/app.js") {
            call.respondBytes(resourceBytes("/webapp/app.js"), ContentType.Text.JavaScript)
        }

        post("/api/pair") {
            val body = call.receiveText()
            val pin = Regex("\"(?:pin|token)\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
            val token = server.pairing.pair(pin)
            if (token == null) {
                call.respondText("{\"ok\":false}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            } else {
                call.response.cookies.append(Cookie("morse_session", token, path = "/", httpOnly = true))
                call.respondText("{\"ok\":true}", ContentType.Application.Json)
            }
        }

        get("/api/shared-files") {
            if (!call.requireSession(server)) return@get
            val files = server.sharedFiles.value
            val json = files.joinToString(",", "[", "]") {
                "{\"id\":\"${it.id}\",\"name\":\"${jsonEscape(it.displayName)}\",\"size\":${it.sizeBytes}}"
            }
            call.respondText(json, ContentType.Application.Json)
        }

        get("/api/download/{file_id}") {
            if (!call.requireSession(server)) return@get
            val id = call.parameters["file_id"] ?: return@get
            val file = server.sharedFiles.value.firstOrNull { it.id == id }
            if (file == null) {
                call.respondText("not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
                return@get
            }
            val bytes = ServiceLocator.deps.fileAdapter.open(file.uri).use { it.readBytes() }
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.displayName.replace("\"", "")}\"")
            call.respondBytes(bytes, ContentType.Application.OctetStream)
        }

        post("/api/upload") {
            if (!call.requireSession(server)) return@post
            val multipart = call.receiveMultipart()
            var name = "upload.bin"
            var savedPath: String? = null
            var savedSize = 0L
            multipart.forEachPart { part: PartData ->
                if (part is PartData.FileItem) {
                    name = part.originalFileName ?: name
                    val bytes = part.streamProvider().readBytes()
                    val sink = ServiceLocator.deps.fileAdapter.incomingSink(name, bytes.size.toLong(), "application/octet-stream")
                    try {
                        sink.writeAt(0, bytes)
                        sink.complete(true)
                        savedPath = sink.displayPath
                        savedSize = bytes.size.toLong()
                    } catch (e: Exception) {
                        sink.complete(false)
                    }
                }
                part.dispose()
            }
            if (savedPath == null) {
                call.respondText("{\"ok\":false}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            server.historyRepo.add(
                HistoryEntry(
                    id = Crypto.randomId(), batchId = null,
                    peerDeviceId = "web-browser", peerName = "Browser (Web Connect)",
                    filename = name, sizeBytes = savedSize, direction = "received",
                    kind = "file", mime = null, source = "via Web Connect", status = "completed",
                    ts = System.currentTimeMillis(),
                ),
            )
            server.onIncomingFile(savedPath!!, name, savedSize)
            call.respondText("{\"ok\":true}", ContentType.Application.Json)
        }

        webSocket("/ws/chat") {
            val session = this
            server.browserSessions.add(session)
            try {
                val collector = launch {
                    server.chatBroadcast.collect { msg ->
                        runCatching { session.send(Frame.Text(wsJson.encodeToString(WsChatMsg.serializer(), msg))) }
                    }
                }
                for (frame in session.incoming) {
                    if (frame is Frame.Text) {
                        val msg = runCatching { wsJson.decodeFromString(WsChatMsg.serializer(), frame.readText()) }.getOrNull() ?: continue
                        server.pushNativeChat(msg.text)
                    }
                }
                collector.cancel()
            } finally {
                server.browserSessions.remove(session)
            }
        }
    }
}

