# TouchLink 实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 Android App 通过局域网控制 Windows 电脑的完整 MVP（鼠标 + 基础键盘 + 配对连接）

**Architecture:** Android Kotlin 端负责触控捕获和手势识别 → 二进制协议包 → UDP/TCP → Rust 服务端接收 → Win32 SendInput 模拟输入。两端通过 mDNS 自动发现。协议层 strict 分层，单向依赖。

**Tech Stack:** Android (Kotlin, coroutines), Windows (Rust, tokio, windows-sys), 网络 (UDP, TCP, mDNS)

---

## 项目结构概览

```
/workspace/
├── android/                     # Android App (Kotlin)
│   └── TouchLink/
└── server/                      # Windows 服务端 (Rust)
    └── touchlink-server/
```

---

### Task 1: Rust 项目骨架 + Packet 协议定义

**Files:**
- Create: `server/touchlink-server/Cargo.toml`
- Create: `server/touchlink-server/src/main.rs`
- Create: `server/touchlink-server/src/packet.rs`
- Create: `server/touchlink-server/src/error.rs`

**Step 1: 创建 Cargo.toml**

```toml
[package]
name = "touchlink-server"
version = "0.1.0"
edition = "2021"

[dependencies]
tokio = { version = "1", features = ["full"] }
windows-sys = "0.59"
libmdns = "0.8"
thiserror = "2"
tracing = "0.1"
tracing-subscriber = "0.3"
```

**Step 2: 实现 packet.rs — 协议编解码（核心契约）**

```rust
// server/touchlink-server/src/packet.rs
// 这是两端协议的唯一 Rust 端实现。Android 端 Packet.kt 与其 1:1 对应。

use crate::error::Result;

/// 协议魔数
pub const MAGIC: u8 = b'T'; // 0x54
pub const MAGIC2: u8 = b'L'; // 0x4C
pub const VERSION: u8 = 0x01;

/// 固定包头长度
pub const HEADER_LEN: usize = 10;

/// 操作码
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u16)]
pub enum Opcode {
    // 触控
    TouchMove = 0x0001,
    TouchDown = 0x0002,
    TouchUp = 0x0003,
    Scroll = 0x0004,
    Pinch = 0x0005,
    // 键盘
    KeyDown = 0x0010,
    KeyUp = 0x0011,
    TextType = 0x0012,
    // 媒体
    MediaPlayPause = 0x0020,
    MediaNext = 0x0021,
    MediaPrev = 0x0022,
    VolumeUp = 0x0023,
    VolumeDown = 0x0024,
    // 控制
    PairRequest = 0x0080,
    PairResponse = 0x0081,
    Heartbeat = 0x00FF,
}

impl Opcode {
    pub fn from_u16(v: u16) -> Option<Self> {
        match v {
            0x0001 => Some(Self::TouchMove),
            0x0002 => Some(Self::TouchDown),
            0x0003 => Some(Self::TouchUp),
            0x0004 => Some(Self::Scroll),
            0x0005 => Some(Self::Pinch),
            0x0010 => Some(Self::KeyDown),
            0x0011 => Some(Self::KeyUp),
            0x0012 => Some(Self::TextType),
            0x0020 => Some(Self::MediaPlayPause),
            0x0021 => Some(Self::MediaNext),
            0x0022 => Some(Self::MediaPrev),
            0x0023 => Some(Self::VolumeUp),
            0x0024 => Some(Self::VolumeDown),
            0x0080 => Some(Self::PairRequest),
            0x0081 => Some(Self::PairResponse),
            0x00FF => Some(Self::Heartbeat),
            _ => None,
        }
    }
}

/// 统一数据包
#[derive(Debug, Clone)]
pub struct Packet {
    pub opcode: Opcode,
    pub seq: u32,
    pub payload: Vec<u8>,
}

impl Packet {
    /// 从字节流解码一个完整包（已去除头部）
    pub fn decode(buf: &[u8]) -> Result<Self> {
        let magic = buf[0];
        let magic2 = buf[1];
        if magic != MAGIC || magic2 != MAGIC2 {
            return Err(crate::error::Error::InvalidMagic { magic, magic2 });
        }
        let ver = buf[2];
        if ver != VERSION {
            return Err(crate::error::Error::UnsupportedVersion(ver));
        }
        let opcode_val = u16::from_be_bytes([buf[3], buf[4]]);
        let opcode = Opcode::from_u16(opcode_val)
            .ok_or(crate::error::Error::UnknownOpcode(opcode_val))?;
        let seq = u32::from_be_bytes([buf[5], buf[6], buf[7], buf[8]]);
        let pay_len = u16::from_be_bytes([buf[9], buf[10]]) as usize;
        let payload = buf[11..11 + pay_len].to_vec();
        Ok(Self { opcode, seq, payload })
    }

    /// 编码为字节流
    pub fn encode(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(HEADER_LEN + 2 + self.payload.len());
        out.push(MAGIC);
        out.push(MAGIC2);
        out.push(VERSION);
        out.extend_from_slice(&(self.opcode as u16).to_be_bytes());
        out.extend_from_slice(&self.seq.to_be_bytes());
        let pay_len = self.payload.len() as u16;
        out.extend_from_slice(&pay_len.to_be_bytes());
        out.extend_from_slice(&self.payload);
        out
    }
}

// 触摸事件负载构建辅助
pub struct TouchPayload;

impl TouchPayload {
    pub fn encode(finger_id: u8, x: f32, y: f32) -> Vec<u8> {
        let mut buf = Vec::with_capacity(9);
        buf.push(finger_id);
        buf.extend_from_slice(&x.to_be_bytes());
        buf.extend_from_slice(&y.to_be_bytes());
        buf
    }
}

pub struct ScrollPayload;

impl ScrollPayload {
    pub fn encode(dx: f32, dy: f32) -> Vec<u8> {
        let mut buf = Vec::with_capacity(8);
        buf.extend_from_slice(&dx.to_be_bytes());
        buf.extend_from_slice(&dy.to_be_bytes());
        buf
    }
}

pub struct KeyPayload;

impl KeyPayload {
    pub fn encode(key_code: u16) -> Vec<u8> {
        key_code.to_be_bytes().to_vec()
    }
}
```

**Step 3: 实现 error.rs**

```rust
// server/touchlink-server/src/error.rs
use thiserror::Error;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Error, Debug)]
pub enum Error {
    #[error("Invalid magic bytes: {magic:#x} {magic2:#x}")]
    InvalidMagic { magic: u8, magic2: u8 },
    #[error("Unsupported protocol version: {0}")]
    UnsupportedVersion(u8),
    #[error("Unknown opcode: {0:#x}")]
    UnknownOpcode(u16),
    #[error("Payload too short: expected {expected}, got {actual}")]
    PayloadTooShort { expected: usize, actual: usize },
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}
```

**Step 4: 实现 main.rs — 最小骨架**

```rust
// server/touchlink-server/src/main.rs
mod error;
mod packet;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    tracing::info!("TouchLink server starting...");
    // 后续 Task 在此启动各模块
    tokio::signal::ctrl_c().await?;
    tracing::info!("Shutdown.");
    Ok(())
}
```

**Step 5: 编译验证**

```bash
cd server/touchlink-server
cargo build
# 预期: 编译成功，无 warning
```

**Step 6: 提交**

```bash
git add server/touchlink-server/
git commit -m "feat: rust project skeleton with packet protocol"
```

---

### Task 2: Rust Transport Layer — UDP/TCP 收发

**Files:**
- Create: `server/touchlink-server/src/transport/mod.rs`
- Create: `server/touchlink-server/src/transport/udp.rs`
- Create: `server/touchlink-server/src/transport/tcp.rs`

**Step 1: 实现 transport/mod.rs**

```rust
// 传输层接口 — UDP 和 TCP 的公共 trait
pub trait Transport: Send {
    fn send(&self, data: &[u8]) -> crate::error::Result<()>;
    fn local_addr(&self) -> std::net::SocketAddr;
}
```

**Step 2: 实现 udp.rs**

```rust
use tokio::net::UdpSocket;
use crate::packet::Packet;
use super::Transport;

pub struct UdpTransport {
    socket: UdpSocket,
    peer: std::net::SocketAddr,
}

impl UdpTransport {
    pub async fn bind(addr: &str) -> crate::error::Result<Self> {
        let socket = UdpSocket::bind(addr).await?;
        // 不 connect — 等待第一个包来识别 peer
        Ok(Self {
            socket,
            peer: "0.0.0.0:0".parse().unwrap(),
        })
    }

    pub async fn recv_packet(&mut self) -> crate::error::Result<Packet> {
        let mut buf = [0u8; 65535];
        let (len, peer) = self.socket.recv_from(&mut buf).await?;
        self.peer = peer;
        Packet::decode(&buf[..len])
    }

    pub async fn send_packet(&self, pkt: &Packet) -> crate::error::Result<()> {
        let data = pkt.encode();
        self.socket.send_to(&data, self.peer).await?;
        Ok(())
    }
}

impl Transport for UdpTransport {
    fn send(&self, data: &[u8]) -> crate::error::Result<()> {
        // 同步包装 — 实际使用 send_packet 异步版本
        todo!()
    }

    fn local_addr(&self) -> std::net::SocketAddr {
        self.socket.local_addr().unwrap()
    }
}
```

**Step 3: 实现 tcp.rs**

```rust
use tokio::net::{TcpListener, TcpStream};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

pub struct TcpTransport {
    stream: TcpStream,
}

impl TcpTransport {
    pub async fn listen(addr: &str) -> crate::error::Result<TcpListener> {
        Ok(TcpListener::bind(addr).await?)
    }

    pub async fn accept(listener: &TcpListener) -> crate::error::Result<Self> {
        let (stream, _) = listener.accept().await?;
        Ok(Self { stream })
    }

    pub async fn recv_packet(&mut self) -> crate::error::Result<Packet> {
        let mut header = [0u8; 12]; // magic(2) + ver(1) + op(2) + seq(4) + paylen(2) = 11, 读12字节前部
        self.stream.read_exact(&mut header).await?;
        // 重新组装完整包
        let pay_len = u16::from_be_bytes([header[9], header[10]]) as usize;
        let mut payload = vec![0u8; pay_len];
        if pay_len > 0 {
            self.stream.read_exact(&mut payload).await?;
        }
        let mut full = header[..11].to_vec();
        full.extend_from_slice(&payload);
        Packet::decode(&full)
    }

    pub async fn send_packet(&self, pkt: &Packet) -> crate::error::Result<()> {
        let data = pkt.encode();
        // write_exact the data
        Ok(())
    }
}
```

**Step 4: 编译验证**

```bash
cargo build
```

**Step 5: 提交**

```bash
git commit -m "feat: transport layer with udp and tcp"
```

---

### Task 3: Rust Session + Discovery 层

**Files:**
- Create: `server/touchlink-server/src/session/mod.rs`
- Create: `server/touchlink-server/src/session/session.rs`
- Create: `server/touchlink-server/src/discovery.rs`

**Step 1: session/mod.rs**

```rust
pub mod session;
pub use session::*;
```

**Step 2: session.rs — 会话状态机**

```rust
#[derive(Debug, Clone, PartialEq)]
pub enum SessionState {
    Listening,     // 等待连接
    Paired,        // 已配对
    Disconnected,  // 断开
}

pub struct Session {
    pub state: SessionState,
    pub device_name: Option<String>,
    pub protocol: Protocol,
}

pub enum Protocol {
    Tcp { stream: tokio::net::TcpStream },
    Udp { addr: std::net::SocketAddr },
}
```

**Step 3: discovery.rs — mDNS**

```rust
use std::collections::HashMap;
use tokio::sync::mpsc;

pub const SERVICE_TYPE: &str = "_touchlink._tcp.local.";

pub async fn respond_mdns(service_name: &str, port: u16) -> crate::error::Result<()> {
    let responder = libmdns::Responder::new()?;
    let _svc = responder.register(
        SERVICE_TYPE,
        service_name,
        port,
        &["version=1"],
    );
    // 保持运行直到被取消
    futures::future::pending::<()>().await;
    Ok(())
}
```

**Step 4: 编译验证 + 提交**

---

### Task 4: Rust Input Layer — Mouse + Keyboard

**Files:**
- Create: `server/touchlink-server/src/input/mod.rs`
- Create: `server/touchlink-server/src/input/mouse.rs`
- Create: `server/touchlink-server/src/input/keyboard.rs`

**Step 1: mouse.rs — SendInput 封装**

```rust
use windows_sys::Win32::UI::Input::{SendInput, INPUT, INPUT_0, MOUSEINPUT, MOUSEEVENTF_MOVE, MOUSEEVENTF_ABSOLUTE, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_WHEEL};

pub struct Mouse {
    screen_w: u32,
    screen_h: u32,
}

impl Mouse {
    pub fn new() -> Self {
        let screen_w = unsafe { windows_sys::Win32::Graphics::Gdi::GetSystemMetrics(0) } as u32;
        let screen_h = unsafe { windows_sys::Win32::Graphics::Gdi::GetSystemMetrics(1) } as u32;
        Self { screen_w, screen_h }
    }

    pub fn move_to(&self, nx: f32, ny: f32) {
        let x = (nx * self.screen_w as f32) as u32;
        let y = (ny * self.screen_h as f32) as u32;
        let abs_x = (x * 65535 / self.screen_w) as u32;
        let abs_y = (y * 65535 / self.screen_h) as u32;
        let mi = MOUSEINPUT {
            dx: abs_x as i32,
            dy: abs_y as i32,
            mouseData: 0,
            dwFlags: MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE,
            time: 0,
            dwExtraInfo: 0,
        };
        unsafe { send_input_raw(mi); }
    }

    pub fn left_down(&self) { unsafe { send_mouse(MOUSEEVENTF_LEFTDOWN); } }
    pub fn left_up(&self)   { unsafe { send_mouse(MOUSEEVENTF_LEFTUP); } }
    pub fn right_down(&self) { unsafe { send_mouse(MOUSEEVENTF_RIGHTDOWN); } }
    pub fn right_up(&self)  { unsafe { send_mouse(MOUSEEVENTF_RIGHTUP); } }
    pub fn scroll(&self, delta: i32) { unsafe { send_mouse_data(MOUSEEVENTF_WHEEL, delta as u32); } }
}

unsafe fn send_mouse(flags: u32) {
    let mi = MOUSEINPUT { dx: 0, dy: 0, mouseData: 0, dwFlags: flags, time: 0, dwExtraInfo: 0 };
    send_input_raw(mi);
}

unsafe fn send_mouse_data(flags: u32, data: u32) {
    let mi = MOUSEINPUT { dx: 0, dy: 0, mouseData: data, dwFlags: flags, time: 0, dwExtraInfo: 0 };
    send_input_raw(mi);
}

unsafe fn send_input_raw(mi: MOUSEINPUT) {
    let mut input = INPUT {
        type_: 0, // INPUT_MOUSE
        Anonymous: INPUT_0 { mi },
    };
    SendInput(1, &mut input, std::mem::size_of::<INPUT>() as i32);
}
```

**Step 2: keyboard.rs**

```rust
use windows_sys::Win32::UI::Input::{SendInput, INPUT, INPUT_0, KEYBDINPUT, KEYEVENTF_KEYUP, KEYEVENTF_SCANCODE};

pub fn key_down(vk: u16) {
    unsafe { send_key(vk, 0); }
}

pub fn key_up(vk: u16) {
    unsafe { send_key(vk, KEYEVENTF_KEYUP); }
}

unsafe fn send_key(vk: u16, flags: u32) {
    let ki = KEYBDINPUT {
        wVk: vk,
        wScan: 0,
        dwFlags: flags,
        time: 0,
        dwExtraInfo: 0,
    };
    let mut input = INPUT {
        type_: 1, // INPUT_KEYBOARD
        Anonymous: INPUT_0 { ki },
    };
    SendInput(1, &mut input, std::mem::size_of::<INPUT>() as i32);
}
```

**Step 3: 编译 + 提交**

---

### Task 5: Rust Command Router

**Files:**
- Create: `server/touchlink-server/src/command/mod.rs`
- Create: `server/touchlink-server/src/command/router.rs`
- Modify: `server/touchlink-server/src/main.rs`

**Step 1: router.rs — opcode → handler**

```rust
use crate::packet::{Packet, Opcode};
use crate::input::mouse::Mouse;
use crate::input::keyboard;
use crate::error::Result;

pub struct Router {
    mouse: Mouse,
}

impl Router {
    pub fn new() -> Self {
        Self { mouse: Mouse::new() }
    }

    pub fn dispatch(&self, pkt: &Packet) -> Result<()> {
        match pkt.opcode {
            Opcode::TouchMove => self.handle_touch_move(&pkt.payload),
            Opcode::TouchDown => self.handle_touch_down(&pkt.payload),
            Opcode::TouchUp => self.handle_touch_up(&pkt.payload),
            Opcode::Scroll => self.handle_scroll(&pkt.payload),
            Opcode::KeyDown => self.handle_key_down(&pkt.payload),
            Opcode::KeyUp => self.handle_key_up(&pkt.payload),
            Opcode::Heartbeat => Ok(()), // 心跳无操作
            _ => Ok(()), // 暂未实现的 opcode 静默忽略
        }
    }

    fn handle_touch_move(&self, payload: &[u8]) -> Result<()> {
        // finger_id(1) + x(f32,4) + y(f32,4) = 9 bytes
        let x = f32::from_be_bytes([payload[1], payload[2], payload[3], payload[4]]);
        let y = f32::from_be_bytes([payload[5], payload[6], payload[7], payload[8]]);
        self.mouse.move_to(x, y);
        Ok(())
    }

    fn handle_touch_down(&self, _payload: &[u8]) -> Result<()> {
        // 单指 = 左键
        self.mouse.left_down();
        Ok(())
    }

    fn handle_touch_up(&self, _payload: &[u8]) -> Result<()> {
        self.mouse.left_up();
        Ok(())
    }

    fn handle_scroll(&self, payload: &[u8]) -> Result<()> {
        let dy = f32::from_be_bytes([payload[4], payload[5], payload[6], payload[7]]);
        self.mouse.scroll((dy * 120.0) as i32);
        Ok(())
    }

    fn handle_key_down(&self, payload: &[u8]) -> Result<()> {
        let vk = u16::from_be_bytes([payload[0], payload[1]]);
        keyboard::key_down(vk);
        Ok(())
    }

    fn handle_key_up(&self, payload: &[u8]) -> Result<()> {
        let vk = u16::from_be_bytes([payload[0], payload[1]]);
        keyboard::key_up(vk);
        Ok(())
    }
}
```

**Step 2: 整合到 main.rs**

```rust
// 在 main 中添加事件循环
mod command;
mod input;
mod discovery;
mod session;

use tokio::sync::mpsc;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    tracing::info!("TouchLink server starting...");

    let router = std::sync::Arc::new(command::Router::new());
    let mut udp = transport::UdpTransport::bind("0.0.0.0:42069").await?;

    // mDNS 后台任务
    tokio::spawn(async {
        if let Err(e) = discovery::respond_mdns("TouchLink-Server", 42069).await {
            tracing::error!("mDNS error: {e}");
        }
    });

    // 主事件循环
    loop {
        tokio::select! {
            Ok(pkt) = udp.recv_packet() => {
                if let Err(e) = router.dispatch(&pkt) {
                    tracing::warn!("Dispatch error: {e}");
                }
            }
            _ = tokio::signal::ctrl_c() => break,
        }
    }

    tracing::info!("Shutdown.");
    Ok(())
}
```

**Step 3: 编译 + 提交**

---

### Task 6: Android 项目骨架 + Packet 协议

**Files:**
- Create: `android/TouchLink/app/build.gradle.kts`
- Create: `android/TouchLink/build.gradle.kts`
- Create: `android/TouchLink/settings.gradle.kts`
- Create: `android/TouchLink/app/src/main/AndroidManifest.xml`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/Packet.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/Opcode.kt`

**Step 1: Opcode.kt** — 与 Rust 端 Opcode 1:1 对应

```kotlin
// 与 server/src/packet.rs 中的 Opcode 保持同步
@JvmInline
value class Opcode(val value: UShort) {
    companion object {
        val TouchMove = Opcode(0x0001u)
        val TouchDown = Opcode(0x0002u)
        val TouchUp = Opcode(0x0003u)
        val Scroll = Opcode(0x0004u)
        val Pinch = Opcode(0x0005u)
        val KeyDown = Opcode(0x0010u)
        val KeyUp = Opcode(0x0011u)
        val TextType = Opcode(0x0012u)
        val MediaPlayPause = Opcode(0x0020u)
        val MediaNext = Opcode(0x0021u)
        val MediaPrev = Opcode(0x0022u)
        val VolumeUp = Opcode(0x0023u)
        val VolumeDown = Opcode(0x0024u)
        val PairRequest = Opcode(0x0080u)
        val PairResponse = Opcode(0x0081u)
        val Heartbeat = Opcode(0x00FFu)
    }
}
```

**Step 2: Packet.kt**

```kotlin
data class Packet(
    val opcode: Opcode,
    val seq: UInt,
    val payload: ByteArray
) {
    companion object {
        private const val MAGIC: Byte = 'T'.code.toByte()
        private const val MAGIC2: Byte = 'L'.code.toByte()
        private const val VERSION: Byte = 0x01

        fun decode(data: ByteArray): Packet {
            // 与 Rust 端 packet.rs decode 逻辑对称
            require(data.size >= 11) { "Packet too short: ${data.size}" }
            require(data[0] == MAGIC && data[1] == MAGIC2) { "Invalid magic" }
            require(data[2] == VERSION) { "Unsupported version" }
            val opcode = Opcode((data[3].toUByte() shl 8 or (data[4].toUByte())).toUShort())
            val seq = (data[5].toUByte().toUInt() shl 24) or
                      (data[6].toUByte().toUInt() shl 16) or
                      (data[7].toUByte().toUInt() shl 8) or
                      (data[8].toUByte().toUInt())
            val payLen = (data[9].toUByte().toInt() shl 8) or data[10].toUByte().toInt()
            val payload = data.copyOfRange(11, 11 + payLen)
            return Packet(opcode, seq, payload)
        }

        fun encode(pkt: Packet): ByteArray {
            val buf = ByteArray(11 + pkt.payload.size)
            buf[0] = MAGIC
            buf[1] = MAGIC2
            buf[2] = VERSION
            buf[3] = (pkt.opcode.value.toInt() shr 8).toByte()
            buf[4] = pkt.opcode.value.toInt().toByte()
            buf[5] = (pkt.seq.toInt() shr 24).toByte()
            buf[6] = (pkt.seq.toInt() shr 16).toByte()
            buf[7] = (pkt.seq.toInt() shr 8).toByte()
            buf[8] = pkt.seq.toInt().toByte()
            buf[9] = (pkt.payload.size shr 8).toByte()
            buf[10] = pkt.payload.size.toByte()
            pkt.payload.copyInto(buf, 11)
            return buf
        }
    }
}
```

**Step 3: 编译验证 + 提交**

---

### Task 7: Android Transport + Discovery

**Files:**
- Create: `android/TouchLink/app/src/main/java/com/touchlink/transport/Transport.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/transport/UdpTransport.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/transport/Discovery.kt`

**Step 1: Transport.kt**

```kotlin
interface Transport {
    suspend fun send(data: ByteArray)
    fun localAddress(): java.net.InetSocketAddress
}
```

**Step 2: UdpTransport.kt**

```kotlin
class UdpTransport(private val host: String, private val port: Int) : Transport {
    private val socket = DatagramSocket()
    private val addr = InetSocketAddress(host, port)

    override suspend fun send(data: ByteArray) {
        withContext(Dispatchers.IO) {
            socket.send(DatagramPacket(data, data.size, addr))
        }
    }

    override fun localAddress() = socket.localSocketAddress as InetSocketAddress

    fun close() { socket.close() }
}
```

**Step 3: Discovery.kt**

```kotlin
// 使用 Android NSD API 扫描 _touchlink._tcp 服务
class Discovery(private val context: Context) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun startDiscovery(callback: (String, String, Int) -> Unit) { // name, host, port
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        callback(info.serviceName, info.host.hostAddress, info.port)
                    }
                })
            }
        }
        manager.discoverServices("_touchlink._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
    }
}
```

---

### Task 8: Android Gesture Layer

**Files:**
- Create: `android/TouchLink/app/src/main/java/com/touchlink/gesture/TouchEvent.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/gesture/GestureRecognizer.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/gesture/CoordinateMapper.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/command/Command.kt`

**Step 1: TouchEvent.kt — 不可变值对象**

```kotlin
data class TouchEvent(
    val fingerId: Int,
    val action: Action,
    val x: Float,  // 归一化 0.0~1.0
    val y: Float,
    val pressure: Float = 0f
) {
    enum class Action { Down, Move, Up }
}
```

**Step 2: CoordinateMapper.kt — 纯函数**

```kotlin
object CoordinateMapper {
    fun normalize(x: Float, y: Float, viewWidth: Int, viewHeight: Int): Pair<Float, Float> {
        return (x / viewWidth).coerceIn(0f, 1f) to (y / viewHeight).coerceIn(0f, 1f)
    }
}
```

**Step 3: GestureRecognizer.kt**

```kotlin
class GestureRecognizer {
    private val activeFingers = mutableMapOf<Int, TouchEvent>()

    fun process(event: TouchEvent): List<Command> {
        return when (event.action) {
            TouchEvent.Action.Down -> {
                activeFingers[event.fingerId] = event
                listOf(Command.TouchDown(event.fingerId, event.x, event.y))
            }
            TouchEvent.Action.Move -> {
                activeFingers[event.fingerId] = event
                if (activeFingers.size >= 2) {
                    // 双指 → Scroll
                    listOf(Command.Scroll(0f, event.y - (activeFingers[0]?.y ?: event.y)))
                } else {
                    listOf(Command.TouchMove(event.fingerId, event.x, event.y))
                }
            }
            TouchEvent.Action.Up -> {
                activeFingers.remove(event.fingerId)
                listOf(Command.TouchUp(event.fingerId, event.x, event.y))
            }
        }
    }
}
```

**Step 4: Command.kt**

```kotlin
sealed class Command {
    data class TouchDown(val fingerId: Int, val x: Float, val y: Float) : Command()
    data class TouchMove(val fingerId: Int, val x: Float, val y: Float) : Command()
    data class TouchUp(val fingerId: Int, val x: Float, val y: Float) : Command()
    data class Scroll(val dx: Float, val dy: Float) : Command()
    data class Key(val vk: UShort, val down: Boolean) : Command()
    data object Heartbeat : Command()
}
```

---

### Task 9: Android UI — Touchpad + Discovery Screen

**Files:**
- Create: `android/TouchLink/app/src/main/java/com/touchlink/ui/ConnectionScreen.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/ui/TouchpadView.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/MainActivity.kt`
- Create: `android/TouchLink/app/src/main/java/com/touchlink/AppState.kt`
- Modify: `android/TouchLink/app/src/main/res/layout/activity_main.xml`

**Step 1: ConnectionScreen — 自动发现设备列表**

```kotlin
@Composable
fun ConnectionScreen(onConnected: (String, Int) -> Unit) {
    // 显示发现的设备列表
    // 点击连接
}
```

**Step 2: TouchpadView**

```kotlin
class TouchpadView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val recognizer = GestureRecognizer()
    var onCommand: ((Command) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val fingerId = event.getPointerId(pointerIndex)
        val (nx, ny) = CoordinateMapper.normalize(
            event.getX(pointerIndex), event.getY(pointerIndex), width, height
        )
        val touchEvent = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                TouchEvent(fingerId, TouchEvent.Action.Down, nx, ny)
            MotionEvent.ACTION_MOVE -> {
                // 遍历所有活跃手指
                for (i in 0 until event.pointerCount) {
                    val fid = event.getPointerId(i)
                    val (nx2, ny2) = CoordinateMapper.normalize(
                        event.getX(i), event.getY(i), width, height
                    )
                    recognizer.process(TouchEvent(fid, TouchEvent.Action.Move, nx2, ny2))
                        .forEach { onCommand?.invoke(it) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                TouchEvent(fingerId, TouchEvent.Action.Up, nx, ny)
            else -> return false
        }
        recognizer.process(touchEvent).forEach { onCommand?.invoke(it) }
        return true
    }
}
```

**Step 3: AppState**

```kotlin
data class AppState(
    val discoveredDevices: List<DeviceInfo> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isTouchpadActive: Boolean = false,
)

enum class ConnectionState { Disconnected, Connecting, Connected }

data class DeviceInfo(val name: String, val host: String, val port: Int)
```

---

### Task 10: Android Session + 端到端集成

**Files:**
- Create: `android/TouchLink/app/src/main/java/com/touchlink/session/Session.kt`

```kotlin
class Session(
    private val transport: UdpTransport,
    private val scope: CoroutineScope
) {
    private var seq = 0u

    fun send(command: Command) {
        val pkt = when (command) {
            is Command.TouchMove -> Packet(Opcode.TouchMove, seq++, encodeTouch(command))
            is Command.TouchDown -> Packet(Opcode.TouchDown, seq++, encodeTouch(command))
            is Command.TouchUp -> Packet(Opcode.TouchUp, seq++, encodeTouch(command))
            is Command.Scroll -> Packet(Opcode.Scroll, seq++, encodeScroll(command))
            is Command.Key -> Packet(
                if (command.down) Opcode.KeyDown else Opcode.KeyUp,
                seq++, KeyPayload.encode(command.vk)
            )
            Command.Heartbeat -> Packet(Opcode.Heartbeat, 0u, ByteArray(0))
        }
        scope.launch {
            try {
                transport.send(Packet.encode(pkt))
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }
}
```

---

## 执行顺序总结

| # | Task | 依赖 | 预估 |
|---|------|------|------|
| 1 | Rust 项目骨架 + Packet 协议 | — | 15min |
| 2 | Rust Transport (UDP/TCP) | 1 | 15min |
| 3 | Rust Session + Discovery | 2 | 10min |
| 4 | Rust Input (Mouse + Keyboard) | 1 | 15min |
| 5 | Rust Command Router + main 集成 | 2,3,4 | 15min |
| 6 | Android 项目骨架 + Packet 协议 | 1 (协议对齐) | 15min |
| 7 | Android Transport + Discovery | 6 | 10min |
| 8 | Android Gesture Layer | 6 | 15min |
| 9 | Android UI (Touchpad + Connection) | 7, 8 | 20min |
| 10 | Android Session + 端到端集成 | 7, 8, 9 | 15min |

总计约 **2.5 小时** 实现时间。
