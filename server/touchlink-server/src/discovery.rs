use tracing;

/// mDNS service type for TouchLink discovery.
/// Android NSD scans for `_touchlink._tcp` services.
pub const SERVICE_TYPE: &str = "_touchlink._tcp.local.";

/// Publish this server via mDNS so Android clients can discover it.
/// Runs until the `shutdown` signal fires, then unregisters.
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
