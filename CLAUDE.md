# TouchLink

Android phone as a wireless touchpad/keyboard for a Windows PC over LAN.

## Tech Stack

| Component | Language/Framework | Build |
|-----------|-------------------|-------|
| Server (Windows) | Rust, tokio, windows-sys | `cargo build --release` |
| App (Android) | Kotlin, Jetpack Compose, coroutines | Gradle 8.9, JDK 17 |

## Project Layout

```
android/TouchLink/          # Android app
  app/src/main/java/com/touchlink/
    command/                # Protocol command encoding
    gesture/                # Touch gesture recognition
    session/                # Connection session management
    transport/              # UDP transport + NSD + UDP discovery
    ui/                     # Compose UI (connection screen + touchpad)
    MainActivity.kt         # App entry point
    Packet.kt / Opcode.kt   # Binary protocol
    AppState.kt             # Shared state models
server/touchlink-server/    # Rust server
  src/
    command/                # Packet router → input dispatch
    input/                  # Mouse/keyboard via SendInput
    packet.rs               # Binary protocol encode/decode
    transport/              # UDP/TCP transport
    discovery.rs            # mDNS + UDP broadcast
    session/                # Session state machine
    main.rs                 # Entry point
```

## Binary Protocol

Fixed header (11 bytes): magic `0x54 0x4C`, version 1, opcode u16 LE, seq u32 LE, paylen u16 LE.
Touch: (finger_id u8, x f32 LE, y f32 LE). Scroll: (dx f32, dy f32). Key: (vk u32 LE).

## Build & Run

```bash
# Server (Windows)
cd server/touchlink-server
cargo build --release
./target/release/touchlink-server.exe

# Android
cd android/TouchLink
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Versioning

Version format: `0.1.X` where X auto-increments on each build.
The build number is stored in `android/TouchLink/version.properties`.
APK versionName appears in the app footer and in Settings → Apps.

## Usage

1. Run `touchlink-server.exe` on the Windows PC
2. Open TouchLink app on Android phone (same WiFi)
3. Tap "扫描设备" to discover the server
4. Tap the server name to connect
5. Touchpad: single finger moves cursor, tap to click, two fingers scroll
6. Tap "断开" to disconnect and return to device list
