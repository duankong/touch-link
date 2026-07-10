# AGENTS.md

## Project overview

TouchLink: Android phone as a wireless touchpad/keyboard for a Windows PC over LAN. Android app (Kotlin + Compose) discovers and sends touch/gesture/input events to a Rust server that injects them via Win32 `SendInput`.

Two independent components, one shared binary protocol.

## Repository layout

```
android/TouchLink/    # Android app (Gradle, Kotlin, Jetpack Compose)
server/touchlink-server/  # Rust server (tokio, windows-sys)
docs/plans/           # Protocol spec + design doc (source of truth for wire format)
```

## Build & run

**Android** (from `android/TouchLink/`):
```
gradlew.bat assembleDebug        # build debug APK
gradlew.bat installDebug         # install on connected device
```

**Rust server** (from `server/touchlink-server/`):
```
cargo build                       # build
cargo test                        # run unit tests (12 tests in packet.rs)
cargo run                         # run server (binds UDP 0.0.0.0:42069, publishes mDNS)
```

No linter, formatter, or CI is configured for either component.

## Protocol (critical — cross-cutting concern)

Binary protocol defined in `docs/plans/2026-07-11-touchlink-design.md` and implemented in:
- **Rust**: `server/touchlink-server/src/packet.rs` (canonical)
- **Kotlin**: `android/TouchLink/app/src/main/java/com/touchlink/Packet.kt` (mirror)

Wire format: `[magic:0x54 0x4C][version:0x01][opcode:u16 BE][seq:u32 BE][paylen:u16 BE][payload]`

**When modifying opcodes, payload layouts, or packet encoding, update BOTH `packet.rs` and `Packet.kt`.** The design doc has the opcode table.

## Key architecture facts

- **Discovery**: mDNS service type `_touchlink._tcp`. Rust publishes via `libmdns`; Android scans via `NsdManager`.
- **Transport**: UDP for touch/gesture streams (low latency). TCP transport exists but is not wired into the main loop yet.
- **Gesture recognition**: Android-side `GestureRecognizer` converts raw touch events into `Command` sealed class, which maps to opcodes+payload.
- **Input injection**: Rust `Router` dispatches opcodes to `input::mouse` / `input::keyboard` which call Win32 `SendInput` via `windows-sys`.
- **Session state machine**: `Disconnected → Connecting → Connected`. MVP assumes connection succeeds immediately (no pairing yet).

## Incomplete / stubs

- Pinch gesture: recognized but not sent (router logs debug message, no Win32 implementation)
- TextType: recognized but not sent
- Media keys: recognized but silently dropped
- Pairing flow: `PairRequest`/`PairResponse` opcodes defined but not implemented
- TCP transport: struct exists, not used in main loop
- No Android unit tests

## Gotchas

- The Rust server requires Windows (uses `windows-sys` crate for Win32 API). Cannot build on Linux/macOS.
- Android requires `ACCESS_FINE_LOCATION` permission for mDNS discovery (Android NSD requirement).
- `local.properties` is gitignored — contains SDK path, won't be present on fresh clone.
- Protocol version is `0x01`. Changing it requires updating both sides and the design doc.
- Scroll payload on Android side omits `finger_id` byte (8 bytes: dx+dy only), but the design doc says `finger_id(1B) | dx | dy` for Scroll. Verify before modifying.
