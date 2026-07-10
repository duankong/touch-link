# TouchLink: Android 触控遥控 Windows 电脑 — 设计文档

## 概述

通过 Android App 控制同一 WiFi 下配对的 Windows 电脑。手机滑动屏幕，鼠标在电脑上同步移动；支持多点触控手势、键盘输入、媒体控制。追求最低延迟（目标 <10ms 端到端）。

## 技术栈

| 端 | 语言/平台 | 理由 |
|---|-----------|------|
| 手机端 | Android (Kotlin) | 目标平台 |
| 电脑端 | Rust | 最低延迟，直接调用 Win32 `SendInput` API，原生二进制无运行时开销 |

## 连接方式

- **自动发现**：mDNS 局域网广播，App 自动扫描并列出可用电脑
- **配对**：发起配对 → 校验 PIN 或设备名 → 建立安全会话
- **传输**：UDP（触控流，丢包不影响连续性）+ TCP（配对/指令确认）

## 核心约束

- **DRY**：协议定义是唯一真相来源，两端各自实现解析
- **单向依赖**：每层只依赖下层接口，上层不感知下层实现
- **不可变数据**：触控事件为值对象，创建后不修改
- **纯函数化**：手势识别、坐标映射无副作用，可单元测试

---

## 整体架构

```
┌─────────────────────────────┐         局域网 WiFi         ┌──────────────────────────┐
│     Android App (Kotlin)    │  ◄──────────────────────►   │  Windows 服务 (Rust)     │
│                             │    UDP (触控流)             │                          │
│  ┌───────────────────────┐  │    TCP (配对/指令)          │  ┌────────────────────┐  │
│  │  UI Layer             │  │                             │  │  Transport Layer   │  │
│  │  TouchpadView         │  │                             │  │  (tokio)           │  │
│  │  KeyboardView         │  │                             │  ├────────────────────┤  │
│  │  MediaControlView     │  │                             │  │  Session Layer     │  │
│  ├───────────────────────┤  │                             │  ├────────────────────┤  │
│  │  Gesture Layer        │  │                             │  │  Command Layer     │  │
│  │  TouchEvent           │  │                             │  ├────────────────────┤  │
│  │  GestureRecognizer    │  │                             │  │  Input Layer       │  │
│  │  GestureMapper        │  │                             │  │  Mouse (SendInput) │  │
│  │  CoordinateMapper     │  │                             │  │  Keyboard          │  │
│  ├───────────────────────┤  │                             │  │  Media             │  │
│  │  Session Layer        │  │                             │  └────────────────────┘  │
│  │  Session (状态机)     │  │                             └──────────────────────────┘
│  │  Heartbeat            │  │
│  ├───────────────────────┤  │
│  │  Transport Layer      │  │
│  │  UdpTransport         │  │
│  │  TcpTransport         │  │
│  │  Discovery (mDNS)     │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
```

### 层级说明

**Transport Layer（传输层）** — 唯一知道网络细节的模块。封装 UDP/TCP 收发，对外暴露统一的 `Transport` 接口。上层不感知 UDP 还是 TCP。

**Session Layer（会话层）** — 连接生命周期管理。状态机：`Disconnected → Pairing → Connected → Disconnected`。负责心跳保活、自动重连。

**Gesture Layer（Android 特有）** — 原始触控事件 → 归一化坐标 → 手势识别 → 生成指令。纯函数设计。

**Input Layer（Rust 特有）** — 指令 → Win32 `SendInput` API 调用。不关心数据来源。

**Command Layer（Rust 特有）** — 接收端指令路由器。反序列化 → 按 opcode 分发到对应 Input 模块。

---

## 网络协议

### 统一数据包格式

每个消息一个包，固定长度包头 + 变长负载：

```
 0      1       2-3      4-7      8-9      10+
├──────┼──────┼────────┼────────┼────────┼──────────────┤
│ magic│vers  │ opcode │  seq   │ paylen │   payload    │
│ 1B   │ 1B   │  2B    │  4B    │  2B    │  0-65535B    │
└──────┴──────┴────────┴────────┴────────┴──────────────┘
```

- `magic`: 固定 `0xTL`
- `seq`: 发送端递增序列号，用于去重和排序

### Opcode 表

| Opcode | 指令 | 方向 | Payload 格式 |
|--------|------|------|--------------|
| `0x0001` | TouchMove | → | `finger_id(1B) \| x(f32,4B) \| y(f32,4B)` |
| `0x0002` | TouchDown | → | 同上 |
| `0x0003` | TouchUp | → | 同上 |
| `0x0004` | Scroll | → | `finger_id(1B) \| dx(f32,4B) \| dy(f32,4B)` |
| `0x0005` | Pinch | → | `finger_id(1B) \| scale(f32,4B)` |
| `0x0010` | KeyDown | → | `key_code(u16,2B)` |
| `0x0011` | KeyUp | → | 同上 |
| `0x0012` | TextType | → | `length(u16,2B) \| utf8_text(length B)` |
| `0x0020` | MediaPlayPause | → | 无 payload |
| `0x0021` | MediaNext | → | 无 |
| `0x0022` | MediaPrev | → | 无 |
| `0x0023` | VolumeUp | → | 无 |
| `0x0024` | VolumeDown | → | 无 |
| `0x0080` | PairRequest | ↔ | `name_len(1B) \| device_name \| pin(u32,4B)` |
| `0x0081` | PairResponse | ↔ | `status(1B, 0=ok/1=reject/2=busy)` |
| `0x00FF` | Heartbeat | ↔ | 无 payload |

**DRY**: `packet.rs` (Rust) 和 `Packet.kt` (Android) 各自实现一次解析/组装，但字段定义以本设计文档为准。

---

## Android 端模块结构

```
app/src/main/java/com/touchlink/
├── transport/
│   ├── Transport.kt           # interface Transport
│   ├── UdpTransport.kt        # DatagramSocket 封装
│   ├── TcpTransport.kt        # Socket 封装
│   ├── Packet.kt              # 数据包定义 + 序列化/反序列化
│   └── Discovery.kt           # mDNS 扫描（JmDNS 或 NSD）
├── session/
│   ├── Session.kt             # 会话状态机
│   ├── SessionConfig.kt       # 配对信息持久化
│   └── Heartbeat.kt           # 定时发送心跳
├── gesture/
│   ├── TouchEvent.kt          # 不可变值对象
│   ├── GestureRecognizer.kt   # 原始事件流 → 手势事件
│   ├── GestureMapper.kt       # 手势 → 指令 opcode + payload
│   └── CoordinateMapper.kt    # 手机像素坐标 → 归一化 f32
├── ui/
│   ├── TouchpadView.kt        # 自定义 View，捕获触控事件
│   ├── KeyboardView.kt        # 软键盘布局
│   ├── MediaControlView.kt    # 播放控制按钮
│   └── ConnectionScreen.kt    # 设备列表 + 配对界面
├── command/
│   └── Command.kt             # 指令值对象（UI层到传输层的中间体）
├── AppState.kt                # 不可变全局状态 StateFlow
└── TouchLinkApp.kt            # Application 入口
```

### 模块间依赖关系

```
UI ──→ Gesture ──→ Session ──→ Transport
 │                               ↑
 └─────── Command ───────────────┘
```

关键在于：
- **`GestureMapper` 输出 `Command`**（一个轻量值对象，含 opcode + payload 字节）
- **`Session` 把 `Command` 交给 `Transport` 发送**
- UI 不直接使用 Transport，Gesture 不直接发送数据包

---

## Rust 端模块结构

```
touchlink-server/
├── Cargo.toml
└── src/
    ├── main.rs
    ├── transport/
    │   ├── mod.rs
    │   ├── transport.rs        # trait Transport
    │   ├── udp.rs              # tokio::net::UdpSocket
    │   ├── tcp.rs              # tokio::net::TcpListener/Stream
    │   ├── packet.rs           # Packet 结构体 + 编解码
    │   └── discovery.rs        # mDNS 响应（libmdns）
    ├── session/
    │   ├── mod.rs
    │   ├── session.rs          # Session 状态机
    │   ├── config.rs           # 配置文件（toml）
    │   └── heartbeat.rs        # 心跳超时检测
    ├── command/
    │   ├── mod.rs
    │   └── router.rs           # opcode → handler 路由
    ├── input/
    │   ├── mod.rs
    │   ├── mouse.rs            # SendInput 鼠标
    │   ├── keyboard.rs         # SendInput 键盘
    │   └── media.rs            # WM_APPCOMMAND 媒体键
    └── error.rs                # 统一错误类型（thiserror）
```

### 数据流

```
UDP/TCP ─→ packet::decode ─→ router::route ─→ input::mouse/keyboard/media
                           ↑
                    packet::Packet 对上层透明
```

---

## 错误处理策略

| 层 | 错误类型 | 处理方式 |
|----|---------|---------|
| Transport | 网络断开、超时 | 触发 Session 层状态变迁（Disconnected） |
| Session | 心跳超时、配对失败 | UI 层订阅状态流，显示对应界面 |
| Input | `SendInput` 失败 | 记录日志 + 返回错误码，由 router 决定是否重试 |
| 反序列化 | 非法数据包 | 丢弃并计数，不崩溃 |

---

## 性能优化设计

1. **触控路径零拷贝**：`TouchEvent → byte[]` 直接写入 UDP 发送缓冲区，无中间 JSON 序列化
2. **UDP 优先**：触控数据统一走 UDP，TCP 仅用于配对和键盘/媒体指令
3. **归一化坐标**：浮动 `f32` 精度（0.0~1.0），Rust 端按目标屏幕分辨率缩放
4. **tokio 异步**：Rust 端用 tokio 多路复用，不阻塞主线程
5. **无锁状态**：Android 端 `AppState` 通过 `StateFlow` 发布，单一订阅者

---

## 手势 → 鼠标映射规则

| 手机手势 | 电脑端行为 |
|---------|-----------|
| 单指滑动 | 鼠标移动 |
| 单指轻点 | 左键单击 |
| 单指双击 | 左键双击 |
| 双指轻点 | 右键单击 |
| 双指上下滑动 | 垂直滚动 |
| 双指左右滑动 | 水平滚动 |
| 双指捏合/展开 | 缩放（Ctrl+滚轮） |
| 三指上滑 | 任务视图 (Win+Tab) |
| 三指下滑 | 显示桌面 (Win+D) |
| 四指左右滑 | 切换虚拟桌面 |

---

## 第一阶段范围（MVP）

1. Android App 基本界面：连接发现 + 触控板视图 + 基本键盘
2. Rust 服务端：mDNS 响应 + TCP/UDP 收发 + 鼠标/键盘输入
3. 配对流程
4. 基础手势：单指移动/点击、双指滚动

后续阶段：媒体控制、更多手势、多屏支持、配置持久化。

---

## 附录：关键依赖

### Rust (Cargo.toml)
- `tokio` — 异步运行时
- `windows-sys` — Win32 API 绑定
- `libmdns` — mDNS 响应
- `thiserror` — 错误类型
- `tracing` — 日志

### Android (build.gradle)
- `androidx.core` — 基础组件
- `kotlinx.coroutines` — 协程
- `kotlinx.serialization` — 仅用于配置持久化（非网络数据）
