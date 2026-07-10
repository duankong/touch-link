// This is the ONLY Rust implementation of the protocol. Android Packet.kt mirrors it 1:1.

use crate::error::Result;

/// Protocol magic bytes
pub const MAGIC: u8 = b'T'; // 0x54
pub const MAGIC2: u8 = b'L'; // 0x4C
pub const VERSION: u8 = 0x01;

/// Fixed header length (magic 2 + version 1 + opcode 2 + seq 4 + paylen 2 = 11)
pub const HEADER_LEN: usize = 11;

/// Operation codes
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u16)]
pub enum Opcode {
    // Touch
    TouchMove = 0x0001,
    TouchDown = 0x0002,
    TouchUp = 0x0003,
    Scroll = 0x0004,
    Pinch = 0x0005,
    TouchCancel = 0x0006,
    // Keyboard
    KeyDown = 0x0010,
    KeyUp = 0x0011,
    TextType = 0x0012,
    // Media
    MediaPlayPause = 0x0020,
    MediaNext = 0x0021,
    MediaPrev = 0x0022,
    VolumeUp = 0x0023,
    VolumeDown = 0x0024,
    // Control
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
            0x0006 => Some(Self::TouchCancel),
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

    pub fn to_u16(self) -> u16 {
        self as u16
    }
}

/// Unified packet
#[derive(Debug, Clone)]
pub struct Packet {
    pub opcode: Opcode,
    pub seq: u32,
    pub payload: Vec<u8>,
}

impl Packet {
    /// Decode a complete packet from byte slice
    pub fn decode(buf: &[u8]) -> Result<Self> {
        if buf.len() < HEADER_LEN {
            return Err(crate::error::Error::PacketTooShort { len: buf.len() });
        }
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
        if 11 + pay_len > buf.len() {
            return Err(crate::error::Error::PayloadTooShort {
                expected: pay_len,
                actual: buf.len() - 11,
            });
        }
        let payload = buf[11..11 + pay_len].to_vec();
        Ok(Self { opcode, seq, payload })
    }

    /// Encode to byte stream
    pub fn encode(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(HEADER_LEN + self.payload.len());
        out.push(MAGIC);
        out.push(MAGIC2);
        out.push(VERSION);
        out.extend_from_slice(&(self.opcode.to_u16()).to_be_bytes());
        out.extend_from_slice(&self.seq.to_be_bytes());
        let pay_len = self.payload.len() as u16;
        out.extend_from_slice(&pay_len.to_be_bytes());
        out.extend_from_slice(&self.payload);
        out
    }
}

// Touch event payload helpers
#[allow(dead_code)]
pub struct TouchPayload;

#[allow(dead_code)]
impl TouchPayload {
    pub fn encode(finger_id: u8, x: f32, y: f32) -> Vec<u8> {
        let mut buf = Vec::with_capacity(9);
        buf.push(finger_id);
        buf.extend_from_slice(&x.to_be_bytes());
        buf.extend_from_slice(&y.to_be_bytes());
        buf
    }

    pub fn decode(data: &[u8]) -> Result<(u8, f32, f32)> {
        if data.len() < 9 {
            return Err(crate::error::Error::PayloadTooShort {
                expected: 9,
                actual: data.len(),
            });
        }
        let finger_id = data[0];
        let x = f32::from_be_bytes([data[1], data[2], data[3], data[4]]);
        let y = f32::from_be_bytes([data[5], data[6], data[7], data[8]]);
        Ok((finger_id, x, y))
    }
}

#[allow(dead_code)]
pub struct ScrollPayload;

#[allow(dead_code)]
impl ScrollPayload {
    pub fn encode(dx: f32, dy: f32) -> Vec<u8> {
        let mut buf = Vec::with_capacity(8);
        buf.extend_from_slice(&dx.to_be_bytes());
        buf.extend_from_slice(&dy.to_be_bytes());
        buf
    }

    pub fn decode(data: &[u8]) -> Result<(f32, f32)> {
        if data.len() < 8 {
            return Err(crate::error::Error::PayloadTooShort {
                expected: 8,
                actual: data.len(),
            });
        }
        let dx = f32::from_be_bytes([data[0], data[1], data[2], data[3]]);
        let dy = f32::from_be_bytes([data[4], data[5], data[6], data[7]]);
        Ok((dx, dy))
    }
}

#[allow(dead_code)]
pub struct KeyPayload;

#[allow(dead_code)]
impl KeyPayload {
    pub fn encode(key_code: u16) -> Vec<u8> {
        key_code.to_be_bytes().to_vec()
    }

    pub fn decode(data: &[u8]) -> Result<u16> {
        if data.len() < 2 {
            return Err(crate::error::Error::PayloadTooShort {
                expected: 2,
                actual: data.len(),
            });
        }
        Ok(u16::from_be_bytes([data[0], data[1]]))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_opcode_roundtrip() {
        for op in &[
            Opcode::TouchMove,
            Opcode::TouchDown,
            Opcode::TouchUp,
            Opcode::Scroll,
            Opcode::Pinch,
            Opcode::TouchCancel,
            Opcode::KeyDown,
            Opcode::KeyUp,
            Opcode::TextType,
            Opcode::MediaPlayPause,
            Opcode::MediaNext,
            Opcode::MediaPrev,
            Opcode::VolumeUp,
            Opcode::VolumeDown,
            Opcode::PairRequest,
            Opcode::PairResponse,
            Opcode::Heartbeat,
        ] {
            let val = op.to_u16();
            let back = Opcode::from_u16(val).unwrap();
            assert_eq!(*op, back);
        }
    }

    #[test]
    fn test_opcode_from_u16_unknown() {
        assert!(Opcode::from_u16(0xFFFF).is_none());
    }

    #[test]
    fn test_packet_encode_decode_roundtrip() {
        let payload = vec![1, 2, 3, 4];
        let pkt = Packet {
            opcode: Opcode::TouchDown,
            seq: 42,
            payload: payload.clone(),
        };
        let encoded = pkt.encode();
        let decoded = Packet::decode(&encoded).unwrap();
        assert_eq!(decoded.opcode, pkt.opcode);
        assert_eq!(decoded.seq, pkt.seq);
        assert_eq!(decoded.payload, payload);
    }

    #[test]
    fn test_packet_empty_payload() {
        let pkt = Packet {
            opcode: Opcode::Heartbeat,
            seq: 0,
            payload: vec![],
        };
        let encoded = pkt.encode();
        let decoded = Packet::decode(&encoded).unwrap();
        assert_eq!(decoded.opcode, Opcode::Heartbeat);
        assert_eq!(decoded.seq, 0);
        assert!(decoded.payload.is_empty());
    }

    #[test]
    fn test_decode_packet_too_short() {
        let err = Packet::decode(&[0x54]).unwrap_err();
        assert!(matches!(err, crate::error::Error::PacketTooShort { .. }));
    }

    #[test]
    fn test_decode_invalid_magic() {
        let buf = vec![0x00; 11];
        let err = Packet::decode(&buf).unwrap_err();
        assert!(matches!(err, crate::error::Error::InvalidMagic { .. }));
    }

    #[test]
    fn test_decode_unsupported_version() {
        let mut buf = vec![0x54, 0x4C, 0x02, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00];
        let err = Packet::decode(&mut buf).unwrap_err();
        assert!(matches!(err, crate::error::Error::UnsupportedVersion(2)));
    }

    #[test]
    fn test_decode_unknown_opcode() {
        let mut buf = vec![0x54, 0x4C, 0x01, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00];
        let err = Packet::decode(&mut buf).unwrap_err();
        assert!(matches!(err, crate::error::Error::UnknownOpcode(0xFFFF)));
    }

    #[test]
    fn test_touch_payload_roundtrip() {
        let data = TouchPayload::encode(3, 100.5, 200.75);
        let (finger_id, x, y) = TouchPayload::decode(&data).unwrap();
        assert_eq!(finger_id, 3);
        assert!((x - 100.5).abs() < f32::EPSILON);
        assert!((y - 200.75).abs() < f32::EPSILON);
    }

    #[test]
    fn test_scroll_payload_roundtrip() {
        let data = ScrollPayload::encode(-5.25, 10.5);
        let (dx, dy) = ScrollPayload::decode(&data).unwrap();
        assert!((dx - (-5.25)).abs() < f32::EPSILON);
        assert!((dy - 10.5).abs() < f32::EPSILON);
    }

    #[test]
    fn test_key_payload_roundtrip() {
        let data = KeyPayload::encode(0x1B); // ESC key
        let key = KeyPayload::decode(&data).unwrap();
        assert_eq!(key, 0x1B);
    }

    #[test]
    fn test_touch_payload_too_short() {
        let err = TouchPayload::decode(&[0x00; 5]).unwrap_err();
        assert!(matches!(err, crate::error::Error::PayloadTooShort { .. }));
    }

    #[test]
    fn test_scroll_payload_too_short() {
        let err = ScrollPayload::decode(&[0x00; 4]).unwrap_err();
        assert!(matches!(err, crate::error::Error::PayloadTooShort { .. }));
    }

    #[test]
    fn test_key_payload_too_short() {
        let err = KeyPayload::decode(&[0x00]).unwrap_err();
        assert!(matches!(err, crate::error::Error::PayloadTooShort { .. }));
    }
}
