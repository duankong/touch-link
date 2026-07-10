mod error;
mod packet;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    tracing::info!("TouchLink server starting...");
    // Subsequent tasks will launch modules here
    tokio::signal::ctrl_c().await?;
    tracing::info!("Shutdown.");
    Ok(())
}
