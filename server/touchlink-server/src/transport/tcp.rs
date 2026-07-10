use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use crate::packet::Packet;

pub struct TcpTransport {
    stream: TcpStream,
}

impl TcpTransport {
    pub async fn bind(addr: &str) -> crate::error::Result<TcpListener> {
        Ok(TcpListener::bind(addr).await?)
    }

    pub async fn accept(listener: &TcpListener) -> crate::error::Result<Self> {
        let (stream, _) = listener.accept().await?;
        Ok(Self { stream })
    }

    pub async fn recv_packet(&mut self) -> crate::error::Result<Packet> {
        let mut header = [0u8; 11];
        self.stream.read_exact(&mut header).await?;
        let pay_len = u16::from_be_bytes([header[9], header[10]]) as usize;
        let mut payload = vec![0u8; pay_len];
        if pay_len > 0 {
            self.stream.read_exact(&mut payload).await?;
        }
        let mut full = header.to_vec();
        full.extend_from_slice(&payload);
        Packet::decode(&full)
    }

    pub async fn send_packet(&self, pkt: &Packet) -> crate::error::Result<()> {
        let data = pkt.encode();
        let mut stream = &self.stream;
        stream.write_all(&data).await?;
        Ok(())
    }
}
