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
    #[error("Packet too short: {len} bytes")]
    PacketTooShort { len: usize },
    #[error("Payload too short: expected {expected}, got {actual}")]
    PayloadTooShort { expected: usize, actual: usize },
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}
