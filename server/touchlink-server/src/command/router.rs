use std::collections::HashMap;
use crate::error::Result;
use crate::input::keyboard;
use crate::input::mouse::Mouse;
use crate::packet::{KeyPayload, Opcode, Packet, ScrollPayload, TouchPayload};

/// Routes incoming packets to the correct input handler based on opcode.
pub struct Router {
    mouse: Mouse,
    /// Track last reported position per finger for delta computation.
    last_positions: HashMap<u8, (f32, f32)>,
    /// Track initial touch-down positions for tap-vs-drag detection.
    down_positions: HashMap<u8, (f32, f32)>,
}

/// Maximum normalized distance for a touch to qualify as a tap (click).
const TAP_THRESHOLD: f32 = 0.03;

impl Router {
    pub fn new() -> Self {
        Self {
            mouse: Mouse::new(),
            last_positions: HashMap::new(),
            down_positions: HashMap::new(),
        }
    }

    /// Dispatch a decoded packet to its handler.
    /// Returns Ok(()) on success; non-fatal errors are logged internally.
    pub fn dispatch(&mut self, pkt: &Packet) -> Result<()> {
        match pkt.opcode {
            Opcode::TouchMove => self.handle_touch_move(&pkt.payload),
            Opcode::TouchDown => self.handle_touch_down(&pkt.payload),
            Opcode::TouchUp => self.handle_touch_up(&pkt.payload),
            Opcode::Scroll => self.handle_scroll(&pkt.payload),
            Opcode::Pinch => self.handle_pinch(&pkt.payload),
            Opcode::TouchCancel => self.handle_touch_cancel(),
            Opcode::KeyDown => self.handle_key_down(&pkt.payload),
            Opcode::KeyUp => self.handle_key_up(&pkt.payload),
            Opcode::TextType => self.handle_text_type(&pkt.payload),
            Opcode::MediaPlayPause
            | Opcode::MediaNext
            | Opcode::MediaPrev
            | Opcode::VolumeUp
            | Opcode::VolumeDown => {
                tracing::debug!("Media opcode {:?} not yet implemented", pkt.opcode);
                Ok(())
            }
            Opcode::Heartbeat => {
                tracing::trace!("Heartbeat received, seq={}", pkt.seq);
                Ok(())
            }
            Opcode::PairRequest | Opcode::PairResponse => {
                tracing::debug!("Pairing opcode {:?} — handled by session layer", pkt.opcode);
                Ok(())
            }
        }
    }

    // ── Touch / Cancel handlers ──────────────────────────────────

    fn handle_touch_cancel(&mut self) -> Result<()> {
        // Clear all touch state without triggering any click.
        // Used when a multi-finger gesture ends and the remaining
        // finger should not produce a tap.
        self.last_positions.clear();
        self.down_positions.clear();
        Ok(())
    }

    fn handle_touch_down(&mut self, payload: &[u8]) -> Result<()> {
        let (finger_id, x, y) = TouchPayload::decode(payload)?;
        // Record start position for delta computation and tap detection.
        self.last_positions.insert(finger_id, (x, y));
        self.down_positions.insert(finger_id, (x, y));
        Ok(())
    }

    fn handle_touch_move(&mut self, payload: &[u8]) -> Result<()> {
        let (finger_id, x, y) = TouchPayload::decode(payload)?;
        if let Some(&(last_x, last_y)) = self.last_positions.get(&finger_id) {
            let dx = x - last_x;
            let dy = y - last_y;
            self.mouse.move_by(dx, dy);
        }
        self.last_positions.insert(finger_id, (x, y));
        Ok(())
    }

    fn handle_touch_up(&mut self, payload: &[u8]) -> Result<()> {
        let (finger_id, x, y) = TouchPayload::decode(payload)?;

        // Detect tap: finger moved less than TAP_THRESHOLD since touch-down.
        let is_tap = self.down_positions.get(&finger_id)
            .is_some_and(|&(dx, dy)| {
                let dist = ((x - dx).powi(2) + (y - dy).powi(2)).sqrt();
                dist < TAP_THRESHOLD
            });

        // Only responsible for click when this is the sole active finger.
        // Multi-finger gestures (scroll) should not produce phantom clicks.
        let sole_finger = self.last_positions.len() <= 1;

        if is_tap && sole_finger {
            self.mouse.left_down();
            self.mouse.left_up();
        }

        self.last_positions.remove(&finger_id);
        self.down_positions.remove(&finger_id);
        Ok(())
    }

    // ── Scroll / pinch handlers ─────────────────────────────────

    fn handle_scroll(&self, payload: &[u8]) -> Result<()> {
        let (dx, dy) = ScrollPayload::decode(payload)?;
        let scroll_x = (dx * 2400.0) as i32;
        let scroll_y = (dy * 2400.0) as i32;
        tracing::debug!("Scroll dx={:.4} dy={:.4} → scroll_x={} scroll_y={}", dx, dy, scroll_x, scroll_y);
        self.mouse.scroll(scroll_x, scroll_y);
        Ok(())
    }

    fn handle_pinch(&self, _payload: &[u8]) -> Result<()> {
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
        tracing::debug!("TextType not yet implemented");
        Ok(())
    }
}

impl Default for Router {
    fn default() -> Self {
        Self::new()
    }
}
