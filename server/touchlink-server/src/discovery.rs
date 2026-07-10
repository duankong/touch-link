use tracing;

/// mDNS service type for TouchLink discovery.
/// Android NSD scans for `_touchlink._tcp` services.
pub const SERVICE_TYPE: &str = "_touchlink._tcp.local.";

/// UDP broadcast discovery port.
/// The server announces itself on this port and Android listens for it.
/// This is a fallback when mDNS is unavailable (e.g. Windows firewall blocks it).
const BROADCAST_PORT: u16 = 42070;

/// Publish this server via mDNS so Android clients can discover it.
/// Runs until the future is dropped.
pub async fn publish(service_name: &str, port: u16) -> crate::error::Result<()> {
    let responder = libmdns::Responder::new().map_err(|e| {
        crate::error::Error::Io(std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))
    })?;
    let _svc = responder.register(
        SERVICE_TYPE.to_string(),
        service_name.to_string(),
        port,
        &["version=1"],
    );

    tracing::info!(
        "mDNS published: {} on port {} (service: {})",
        service_name,
        port,
        SERVICE_TYPE
    );

    // Keep the responder alive indefinitely — it unregisters on drop
    std::future::pending::<()>().await;
    Ok(())
}

/// Broadcast the server presence via UDP broadcast every 3 seconds.
/// This is a reliable fallback when mDNS is blocked by firewalls.
/// The Android app listens on BROADCAST_PORT for these announcements.
pub async fn broadcast(service_name: &str, port: u16) -> crate::error::Result<()> {
    let socket = tokio::net::UdpSocket::bind("0.0.0.0:0").await?;
    socket.set_broadcast(true)?;

    let msg = format!("TOUCHLINK v1 {} {}\n", service_name, port);
    let buf = msg.as_bytes();
    let dest = format!("255.255.255.255:{}", BROADCAST_PORT);

    tracing::info!(
        "UDP broadcast enabled: sending to {} every 3s",
        dest
    );

    loop {
        if let Err(e) = socket.send_to(buf, &dest).await {
            tracing::warn!("Broadcast send error: {e}");
        }
        tokio::time::sleep(std::time::Duration::from_secs(3)).await;
    }
}
