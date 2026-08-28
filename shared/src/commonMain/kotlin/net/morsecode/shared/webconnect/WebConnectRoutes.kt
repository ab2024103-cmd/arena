package net.morsecode.shared.webconnect

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.core.readBytes
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

fun resourceBytes(name: String): ByteArray = when (name) {
    "/webapp/index.html" -> WebFrontend.indexHtml.toByteArray()
    "/webapp/style.css" -> WebFrontend.styleCss.toByteArray()
    "/webapp/app.js" -> WebFrontend.appJs.toByteArray()
    else -> WebFrontend.indexHtml.toByteArray()
}

/**
 * The Web Connect browser UI, embedded as source (no classpath resources, so
 * it packages identically in the APK and the desktop bundles).
 */
object WebFrontend {
    val styleCss = ""
    val appJs = ""
    val indexHtml = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Morse Code Web Connect</title>
<style>
:root { --bg:#121218; --card:#1d1d26; --fg:#eceaf2; --muted:#9e9ea7; --accent:#7c6ff0; --teal:#5eead4; --err:#ff6b6b; }
* { box-sizing:border-box; }
body { margin:0; font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; background:var(--bg); color:var(--fg); }
.wrap { max-width:720px; margin:0 auto; padding:20px 16px 60px; }
h1 { font-size:1.5rem; margin:0 0 4px; } h1 span { color:var(--accent); }
.sub { color:var(--muted); font-size:.9rem; margin-bottom:20px; }
.card { background:var(--card); border-radius:14px; padding:16px; margin-bottom:16px; }
.card h2 { font-size:1rem; margin:0 0 12px; }
input[type=password], input[type=text] { width:100%; padding:12px; border-radius:10px; border:1px solid #33333f; background:#26262f; color:var(--fg); font-size:1rem; letter-spacing:4px; }
button { background:var(--accent); border:none; color:#fff; padding:11px 18px; border-radius:10px; font-size:.95rem; cursor:pointer; }
button:disabled { opacity:.5; }
button.secondary { background:#33333f; }
.file { display:flex; align-items:center; gap:10px; padding:10px 4px; border-bottom:1px solid #2a2a33; }
.file:last-child { border-bottom:none; }
.file .name { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.file .size { color:var(--muted); font-size:.85rem; }
.file a { color:var(--teal); text-decoration:none; font-weight:600; }
.muted { color:var(--muted); font-size:.9rem; }
#pin-panel, #main { display:none; }
.err { color:var(--err); font-size:.9rem; margin-top:8px; min-height:1.2em; }
#chatlog { max-height:260px; overflow-y:auto; display:flex; flex-direction:column; gap:6px; margin-bottom:10px; }
.bubble { max-width:80%; padding:8px 12px; border-radius:12px; background:#2a2a35; word-wrap:break-word; }
.bubble.me { align-self:flex-end; background:var(--accent); }
#drop { border:2px dashed #3a3a4a; border-radius:12px; padding:22px; text-align:center; color:var(--muted); margin-bottom:10px; }
#drop.over { border-color:var(--accent); color:var(--accent); }
</style>
</head>
<body>
<div class="wrap">
  <h1>Morse Code <span>Web Connect</span></h1>
  <div class="sub">Browse, download and send files on this device - private, on your LAN.</div>

  <div id="pin-panel" class="card">
    <h2>Enter the pairing PIN shown in the app</h2>
    <input id="pin" type="password" inputmode="numeric" maxlength="6" placeholder="****">
    <div style="margin-top:12px"><button id="pair-btn" onclick="pair()">Connect</button></div>
    <div id="pin-err" class="err"></div>
  </div>

  <div id="main">
    <div class="card">
      <h2>Shared files</h2>
      <div id="files" class="muted">Loading...</div>
    </div>

    <div class="card">
      <h2>Send files to this device</h2>
      <div id="drop">Drop files here or <button class="secondary" onclick="fileInput.click()">choose</button></div>
      <input id="fileInput" type="file" multiple hidden>
      <div id="up-status" class="muted"></div>
    </div>

    <div class="card">
      <h2>Chat with the device</h2>
      <div id="chatlog"></div>
      <div style="display:flex; gap:8px">
        <input id="chat" type="text" placeholder="Type a message..." style="flex:1; padding:12px; border-radius:10px; border:1px solid #33333f; background:#26262f; color:var(--fg);">
        <button onclick="sendChat()">Send</button>
      </div>
    </div>
  </div>
</div>
<script>
const el = (id) => document.getElementById(id);
function fmt(b){ if(b>1e9) return (b/1e9).toFixed(2)+" GB"; if(b>1e6) return (b/1e6).toFixed(1)+" MB"; if(b>1e3) return (b/1e3).toFixed(0)+" KB"; return b+" B"; }

async function boot(){
  const r = await fetch("/api/shared-files");
  if (r.status === 401) { el("pin-panel").style.display="block"; return; }
  el("main").style.display="block"; loadFiles(); initChat();
}
async function pair(){
  el("pin-err").textContent = "";
  el("pair-btn").disabled = true;
  try {
    const r = await fetch("/api/pair", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({pin:el("pin").value.trim()})});
    if (r.ok) { el("pin-panel").style.display="none"; el("main").style.display="block"; loadFiles(); initChat(); }
    else el("pin-err").textContent = "Wrong PIN - check the app and try again.";
  } catch(e){ el("pin-err").textContent = "Connection error: "+e; }
  el("pair-btn").disabled = false;
}
async function loadFiles(){
  const r = await fetch("/api/shared-files");
  if (r.status === 401) { location.reload(); return; }
  const files = await r.json();
  el("files").innerHTML = files.length ? "" : "No files are shared yet. Pick files in the app under Home > Send.";
  for (const f of files) {
    const d = document.createElement("div"); d.className="file";
    d.innerHTML = '<span class="name"></span><span class="size"></span><a href="/api/download/'+f.id+'">Download</a>';
    d.querySelector(".name").textContent = f.name;
    d.querySelector(".size").textContent = fmt(f.size);
    el("files").appendChild(d);
  }
}
async function upload(file){
  el("up-status").textContent = "Sending "+file.name+" ...";
  const fd = new FormData(); fd.append("file", file, file.name);
  try {
    const r = await fetch("/api/upload", {method:"POST", body:fd});
    el("up-status").textContent = r.ok ? "Sent "+file.name+" OK" : "Failed to send "+file.name;
  } catch(e){ el("up-status").textContent = "Failed: "+e; }
}
el("fileInput").addEventListener("change", async e => { for (const f of e.target.files) await upload(f); e.target.value=""; });
const drop = el("drop");
["dragover","dragenter"].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add("over"); }));
["dragleave","drop"].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove("over"); }));
drop.addEventListener("drop", async e => { for (const f of e.dataTransfer.files) await upload(f); });

let ws = null, chatInit = false;
function initChat(){
  if (chatInit) return; chatInit = true;
  const proto = location.protocol === "https:" ? "wss" : "ws";
  ws = new WebSocket(proto+"://"+location.host+"/ws/chat");
  ws.onmessage = ev => { try { const m = JSON.parse(ev.data); addBubble(m.text, false); } catch(_){} };
}
function addBubble(text, me){ const b = document.createElement("div"); b.className="bubble"+(me?" me":""); b.textContent = text; el("chatlog").appendChild(b); el("chatlog").scrollTop = 1e9; }
function sendChat(){ const t = el("chat").value.trim(); if (!t || !ws || ws.readyState !== 1) return;
  ws.send(JSON.stringify({message_id: String(Date.now()), text: t, sent_at: Date.now()}));
  addBubble(t, true); el("chat").value = "";
}
el("chat").addEventListener("keydown", e => { if (e.key === "Enter") sendChat(); });
el("pin").addEventListener("keydown", e => { if (e.key === "Enter") pair(); });
boot();
</script>
</body>
</html>
""""
}

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
                    val bytes = part.provider().readBytes()
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

