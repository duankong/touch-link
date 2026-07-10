use crate::packet::Packet;

pub struct UdpTransport {
    socket: tokio::net::UdpSocket,
    peer: Option<std::net::SocketAddr>,
}

impl UdpTransport {
    pub async fn bind(addr: &str) -> crate::error::Result<Self> {
        let socket = tokio::net::UdpSocket::bind(addr).await?;
        Ok(Self { socket, peer: None })
    }

    pub async fn recv_packet(&mut self) -> crate::error::Result<Packet> {
        let mut buf = [0u8; 65535];
        let (len, peer) = self.socket.recv_from(&mut buf).await?;
        self.peer = Some(peer);
        Packet::decode(&buf[..len])
    }

    pub async fn send_packet(&self, pkt: &Packet) -> crate::error::Result<()> {
        let data = pkt.encode();
        if let Some(peer) = &self.peer {
            self.socket.send_to(&data, peer).await?;
        }
        Ok(())
    }

    pub fn peer_addr(&self) -> Option<std::net::SocketAddr> {
        self.peer
    }
}
