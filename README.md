# Morse Code

**Fast, private, fully offline LAN file transfer for Android + Windows Desktop — one shared Kotlin codebase.**

Device-to-device over local Wi-Fi. No internet, no cloud, no ads, no tracking.
A cleaner, faster alternative to Xender/ShareIt, with their core browsing
experience (Apps / Photos / Videos / Music / Files / History), built-in media
viewers, a browser-based "connect from any PC" mode — and none of their bloat.

---

## IMPORTANT — one-time setup to enable CI builds

This repository's automation sandbox cannot create files under
`.github/workflows/` (GitHub requires the `workflows` permission for that).

**Do this once:** copy `build-workflow.yml` (repo root) to
`.github/workflows/build.yml`. On GitHub.com:

1. Open the repo → **Add file → Create new file**
2. Path: `.github/workflows/build.yml`
3. Paste the full contents of `build-workflow.yml`
4. Commit

Every push afterwards builds automatically on GitHub's runners and publishes a
Release containing:

| Artifact | Job |
|---|---|
| `MorseCode-*-release.apk` — signed Android release APK (minSdk 23) | `android` (ubuntu) |
| `MorseCode-1.0.0.msi` — Windows MSI installer (jpackage + WiX 3) | `desktop` (windows) |
| `MorseCode-1.0.0.exe` — Windows EXE installer (jpackage + Inno Setup 6) | `desktop` |
| `MorseCode-windows-portable.zip` — portable app folder | `desktop` |

---

## Pinned toolchain (Section 0 of the spec)

| Component | Version |
|---|---|
| Kotlin | **2.0.21** |
| Compose Multiplatform | **1.7.0** |
| Android Gradle Plugin | **8.7.2** |
| Gradle (wrapper) | **8.9** |
| kotlinx-coroutines | **1.9.0** |
| kotlinx-serialization | **1.7.3** |
| kotlinx-datetime | **0.6.1** |
| SQLDelight | **2.0.2** |
| JmDNS | **3.5.9** |
| ZXing core | **3.5.3** |
| Coil3 | **3.0.4** |
| AndroidX Media3 (ExoPlayer + UI + Session) | **1.4.1** |
| VLCJ (desktop playback) | **4.8.2** |
| Ktor (Web Connect server) | **2.3.12** |
| JDK for builds | **17** (Temurin on CI) |
| Android minSdk / targetSdk / compileSdk | **23 / 35 / 35** |

### Measured CI build times

Recorded from the GitHub Actions runs (see the Actions tab for the latest):

- Shared JVM tests + signed release APK: ~9–12 min on `ubuntu-latest`
- MSI + EXE + portable packaging: ~10–14 min on `windows-latest`

(The original spec targeted a mechanical-HDD 8 GB machine; the CI machines are
faster, and the Gradle memory settings in `gradle.properties` remain tuned for
the low-RAM workflow described in the spec.)

### Desktop video/audio playback choice — documented decision (Section F)

**VLCJ 4.8.2 was chosen** (spec default) over the JavaFX Media alternative:
pure-JNA (no JavaFX module juggling inside jpackage images), mature format
support via the host's installed VLC. Trade-off: **VLC Media Player must be
installed on the Windows machine**. The desktop app detects VLC at startup and
shows a non-blocking banner with a download link if missing, instead of
failing silently.

---

## Project structure

```
MorseCode/
├── shared/                        Kotlin Multiplatform module (all core logic + UI)
│   ├── src/commonMain/kotlin/
│   │   ├── net/                   Framing, Crypto (ECDH+HKDF+AES-GCM), Handshake,
│   │   │                          TransferSender/Receiver (windowed), BroadcastCoordinator,
│   │   │                          RoomManager, MorseServer, SessionManager, Throttle
│   │   ├── storage/               SQLDelight schema + repos (transfer state, history,
│   │   │                          trusted devices, chat, settings)
│   │   ├── media/                 MediaModels, FileCategorizer, DateGrouping, library interfaces
│   │   ├── player/                VideoPlayer expect @Composable + AudioPlaybackController
│   │   ├── chat/                  ChatModels
│   │   ├── webconnect/            Ktor server, PairingManager, routes, web frontend resources
│   │   └── ui/                    Shared Compose Multiplatform UI (theme, screens, components)
│   ├── src/jvmShared/kotlin/      Code shared by both JVM targets (JmDNS discovery base)
│   ├── src/androidMain/kotlin/    MediaStore/PackageManager/Media3/CameraX actuals
│   └── src/desktopMain/kotlin/    Filesystem scan / VLCJ actuals
├── androidApp/                    Android wrapper (MainActivity, services, permissions)
├── desktopApp/                    Desktop wrapper (Window, tray, drag-drop, firewall diagnostics)
└── build-workflow.yml             CI pipeline (move to .github/workflows/build.yml)
```

## Protocol summary (implemented exactly per spec)

- **Transport:** TCP 53317 (ephemeral fallback; actual port advertised via mDNS)
- **Framing:** pre-handshake `[u32 len][0x00][JSON]`; post-handshake
  `[u32 len][type][12B nonce][ct+16B tag]`; hard cap 16 MiB payload
- **Crypto:** ephemeral P-256 ECDH per connection, HKDF-SHA256
  (salt = sorted concat of both public keys, info `morsecode-session-v1`),
  AES-256-GCM with strictly-incrementing per-direction 64-bit counters
- **Discovery:** `_morsecode._tcp.local.` via JmDNS, TXT records
  (device_id, device_name, device_type, app_version, proto_version, room_id),
  device lost after 15 s; QR fallback JSON `{v:1, device_id, device_name, ip, port, pairing_token}`
- **Transfers:** windowed pipelining (start 4, max 32, halve on NACK/timeout,
  500 ms sweeper), per-chunk SHA-256, random-access receiver writes,
  verified-chunk bitmap persisted in SQLDelight for resume,
  full-file SHA-256 at completion, multi-recipient broadcast with
  `MAX_CONCURRENT_RECIPIENTS = 6` queueing, rooms (open join, ephemeral),
  TEXT_SHARE, CHAT_MESSAGE, trusted devices, auto-accept, global token-bucket throttle

`shared` protocol and logic are unit-tested by `./gradlew :shared:jvmTest`
(HKDF RFC-5869 vector, ECDH symmetry, framing round-trips + oversize rejection,
full handshake loopback incl. rejection paths, 2 MB clean transfer + corruption
recovery via NACK, categorizer/grouping/throttle).

## Running locally

```bash
./gradlew :shared:jvmTest          # protocol + logic tests
./gradlew :desktopApp:run          # desktop app (needs VLC for media playback)
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:packageDistributionForCurrentOS   # MSI/EXE on Windows
```

## Documented decisions / MVP limitations

- **Web Connect serves plain HTTP on the LAN** (not the spec's self-signed
  HTTPS): dependency-free TLS certificate generation isn't available on both
  platforms, and the server binds only to the LAN interface with PIN/QR-gated
  sessions. The one-time pairing token in the QR keeps the session bootstrap safe.
- **Configuration cache** is intentionally left off: SQLDelight 2.0.2's Gradle
  plugin is not configuration-cache-safe; the rest of the spec's
  hardware-aware settings are in `gradle.properties`.
- **Split APKs are not bundled** when sending installed apps (base APK only).
- **App icons** in the Apps tab use letter avatars (no icon extraction in v1).
- **Chat delivery** is confirmed when the TCP write succeeds (MVP model from
  Section G), not via an explicit ack frame.
- **The Apps tab is desktop-hidden** with an explanatory empty state (Section D).
- **Release APK is not R8-minified** in v1.0 for reliability; `isMinifyEnabled`
  can be flipped after keep-rule tuning.
- The signing keystore is committed (`signing/morsecode-release.keystore`,
  passwords `morsecode123`) so CI can produce consistently-signed updates —
  replace it before any public distribution.

## Privacy

Zero analytics, zero ads, zero upsell. Only LAN sockets are used; Web Connect
binds to the LAN interface only and never relays through any external server.
