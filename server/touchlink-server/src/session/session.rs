use std::net::SocketAddr;
use tokio::net::TcpStream;

/// Session lifecycle state machine
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SessionState {
    /// Waiting for a peer to connect or pair
    Listening,
    /// Paired with a peer, ready to receive commands
    Paired,
    /// Connection lost or intentionally disconnected
    Disconnected,
}

/// Protocol variant active for this session
#[derive(Debug)]
pub enum Protocol {
    Tcp { stream: TcpStream },
    Udp { addr: SocketAddr },
}

/// Manages the lifecycle of a single peer connection
#[derive(Debug)]
pub struct Session {
    pub state: SessionState,
    pub device_name: Option<String>,
    pub protocol: Option<Protocol>,
    pub peer_addr: Option<SocketAddr>,
}

impl Session {
    /// Create a new session in the Listening state
    pub fn new() -> Self {
        Self {
            state: SessionState::Listening,
            device_name: None,
            protocol: None,
            peer_addr: None,
        }
    }

    /// Transition to Paired state with the given device name
    pub fn pair(&mut self, device_name: String) {
        self.device_name = Some(device_name);
        self.state = SessionState::Paired;
    }

    /// Transition to Disconnected state
    pub fn disconnect(&mut self) {
        self.state = SessionState::Disconnected;
        self.protocol = None;
    }

    /// Returns true if the session is ready to process commands
    pub fn is_active(&self) -> bool {
        self.state == SessionState::Paired
    }
}

impl Default for Session {
    fn default() -> Self {
        Self::new()
    }
}
