mod command;
mod discovery;
mod error;
pub mod input;
mod packet;
mod session;
pub mod transport;

use std::sync::Arc;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    tracing::info!("TouchLink server starting...");

    let router = Arc::new(command::Router::new());
    let mut udp = transport::udp::UdpTransport::bind("0.0.0.0:42069").await?;

    tracing::info!("UDP transport bound to 0.0.0.0:42069");

    // Background mDNS publisher
    tokio::spawn(async {
        if let Err(e) = discovery::publish("TouchLink-Server", 42069).await {
            tracing::error!("mDNS publish error: {e}");
        }
    });

    // Main event loop — receive packets and dispatch to the router
    loop {
        tokio::select! {
            result = udp.recv_packet() => {
                match result {
                    Ok(pkt) => {
                        tracing::trace!(
                            "Received packet: opcode={:?}, seq={}, payload_len={}",
                            pkt.opcode,
                            pkt.seq,
                            pkt.payload.len(),
                        );
                        if let Err(e) = router.dispatch(&pkt) {
                            tracing::warn!("Dispatch error: {e}");
                        }
                    }
                    Err(e) => {
                        tracing::warn!("Receive error: {e}");
                    }
                }
            }
            _ = tokio::signal::ctrl_c() => {
                tracing::info!("Ctrl+C received, shutting down.");
                break;
            }
        }
    }

    tracing::info!("Shutdown complete.");
    Ok(())
}
