use crate::error::Result;
use crate::input::keyboard;
use crate::input::mouse::Mouse;
use crate::packet::{KeyPayload, Opcode, Packet, ScrollPayload, TouchPayload};

/// Routes incoming packets to the correct input handler based on opcode.
pub struct Router {
    mouse: Mouse,
}

impl Router {
    pub fn new() -> Self {
        Self {
            mouse: Mouse::new(),
        }
    }

    /// Dispatch a decoded packet to its handler.
    /// Returns Ok(()) on success; non-fatal errors are logged internally.
    pub fn dispatch(&self, pkt: &Packet) -> Result<()> {
        match pkt.opcode {
            Opcode::TouchMove => self.handle_touch_move(&pkt.payload),
            Opcode::TouchDown => self.handle_touch_down(),
            Opcode::TouchUp => self.handle_touch_up(),
            Opcode::Scroll => self.handle_scroll(&pkt.payload),
            Opcode::Pinch => self.handle_pinch(&pkt.payload),
            Opcode::KeyDown => self.handle_key_down(&pkt.payload),
            Opcode::KeyUp => self.handle_key_up(&pkt.payload),
            Opcode::TextType => self.handle_text_type(&pkt.payload),
            Opcode::MediaPlayPause
            | Opcode::MediaNext
            | Opcode::MediaPrev
            | Opcode::VolumeUp
            | Opcode::VolumeDown => {
                // Media keys — not yet implemented; silently accepted
                tracing::debug!("Media opcode {:?} not yet implemented", pkt.opcode);
                Ok(())
            }
            Opcode::Heartbeat => {
                tracing::trace!("Heartbeat received, seq={}", pkt.seq);
                Ok(())
            }
            Opcode::PairRequest | Opcode::PairResponse => {
                // Pairing is handled at the session layer
                tracing::debug!("Pairing opcode {:?} — handled by session layer", pkt.opcode);
                Ok(())
            }
        }
    }

    // ── Touch handlers ──────────────────────────────────────────

    fn handle_touch_move(&self, payload: &[u8]) -> Result<()> {
        let (_finger_id, x, y) = TouchPayload::decode(payload)?;
        self.mouse.move_to(x, y);
        Ok(())
    }

    fn handle_touch_down(&self) -> Result<()> {
        self.mouse.left_down();
        Ok(())
    }

    fn handle_touch_up(&self) -> Result<()> {
        self.mouse.left_up();
        Ok(())
    }

    // ── Scroll / pinch handlers ─────────────────────────────────

    fn handle_scroll(&self, payload: &[u8]) -> Result<()> {
        let (_dx, dy) = ScrollPayload::decode(payload)?;
        // WHEEL_DELTA = 120 per notch; dy is in notches (positive = up)
        self.mouse.scroll((dy * 120.0) as i32);
        Ok(())
    }

    fn handle_pinch(&self, _payload: &[u8]) -> Result<()> {
        // Pinch: send Ctrl + scroll for zoom
        // TODO: scale factor → scroll delta conversion
        tracing::debug!("Pinch not yet implemented");
        Ok(())
    }

    // ── Keyboard handlers ───────────────────────────────────────

    fn handle_key_down(&self, payload: &[u8]) -> Result<()> {
        let vk = KeyPayload::decode(payload)?;
        keyboard::down(vk);
        Ok(())
    }

    fn handle_key_up(&self, payload: &[u8]) -> Result<()> {
        let vk = KeyPayload::decode(payload)?;
        keyboard::up(vk);
        Ok(())
    }

    fn handle_text_type(&self, _payload: &[u8]) -> Result<()> {
        // TextType — needs per-character key simulation
        // TODO: iterate UTF-8 chars and send keystrokes
        tracing::debug!("TextType not yet implemented");
        Ok(())
    }
}

impl Default for Router {
    fn default() -> Self {
        Self::new()
    }
}
