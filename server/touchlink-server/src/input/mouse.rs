use windows_sys::Win32::Foundation::POINT;
use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_0, MOUSEINPUT, MOUSEEVENTF_MOVE, MOUSEEVENTF_ABSOLUTE,
    MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP,
    MOUSEEVENTF_WHEEL, MOUSEEVENTF_HWHEEL, INPUT_MOUSE,
};
use windows_sys::Win32::UI::WindowsAndMessaging::{GetCursorPos, GetSystemMetrics};

// ── Pointer acceleration ────────────────────────────────────────────
//
// Models Mac trackpad behavior: slow deliberate movements are precise
// (cursor barely moves), fast flicks cover more ground quickly.
//
//   acceleration = 1.0 + ACCEL_RATE × distance^ACCEL_POWER
//   effective_gain = BASE_SENSITIVITY × acceleration
//
// A finger-drag across the full phone screen (~0.8 normalized distance)
// should feel like a natural arm movement on a desk — not a teleport.

/// Base sensitivity at very slow speeds. Lower = more precise for fine work.
const BASE_SENSITIVITY: f32 = 1.2;

/// How aggressively finger velocity boosts cursor speed.
const ACCEL_RATE: f32 = 60.0;

/// Power curve exponent. 1.0 = linear boost; >1.0 = less boost at slow speeds,
/// more at high speeds (smoother feel).
const ACCEL_POWER: f32 = 1.2;

/// Residual sub-pixel accumulator. Tiny deltas accumulate until they
/// cross a pixel boundary, preventing micro-jitter.
const SUB_PIXEL_THRESHOLD: f32 = 0.5;

// ── Mouse state ────────────────────────────────────────────────────

pub struct Mouse {
    /// Accumulated fractional pixel residuals (dx, dy).
    residual: (f32, f32),
}

impl Mouse {
    pub fn new() -> Self {
        Self { residual: (0.0, 0.0) }
    }

    fn screen_size() -> (u32, u32) {
        let w = unsafe { GetSystemMetrics(0) } as u32;
        let h = unsafe { GetSystemMetrics(1) } as u32;
        (w, h)
    }

    /// Move mouse by a normalized delta with Mac-style pointer acceleration.
    ///
    /// (dx, dy) are in 0.0–1.0 normalized touch space.
    /// Slower finger movement → precise cursor; faster movement → more ground.
    pub fn move_by(&mut self, dx: f32, dy: f32) {
        if dx == 0.0 && dy == 0.0 {
            return;
        }

        let mut pos = POINT { x: 0, y: 0 };
        unsafe {
            let _ = GetCursorPos(&mut pos);
        }
        let (sw, sh) = Self::screen_size();

        // Use the raw normalised distance as a proxy for finger velocity.
        // Larger deltas per frame = faster finger movement.
        let dist = (dx * dx + dy * dy).sqrt();

        // Pointer acceleration curve
        let accel = 1.0 + ACCEL_RATE * dist.powf(ACCEL_POWER);
        let gain = BASE_SENSITIVITY * accel;

        let raw_dx = dx * sw as f32 * gain;
        let raw_dy = dy * sh as f32 * gain;

        // Sub-pixel accumulation — prevents micro-jitter while keeping
        // tiny movements responsive.
        let res_x = self.residual.0 + raw_dx;
        let res_y = self.residual.1 + raw_dy;

        let (applied_dx, new_residual_x) = if res_x.abs() >= SUB_PIXEL_THRESHOLD {
            let rounded = if res_x > 0.0 { res_x.floor() } else { res_x.ceil() };
            (rounded as i32, res_x - rounded)
        } else {
            (0i32, res_x)
        };

        let (applied_dy, new_residual_y) = if res_y.abs() >= SUB_PIXEL_THRESHOLD {
            let rounded = if res_y > 0.0 { res_y.floor() } else { res_y.ceil() };
            (rounded as i32, res_y - rounded)
        } else {
            (0i32, res_y)
        };

        self.residual = (new_residual_x, new_residual_y);

        if applied_dx == 0 && applied_dy == 0 {
            return;
        }

        let new_x = (pos.x + applied_dx).max(0).min((sw - 1) as i32);
        let new_y = (pos.y + applied_dy).max(0).min((sh - 1) as i32);
        let abs_x = (new_x as u32 * 65535 / sw) as u32;
        let abs_y = (new_y as u32 * 65535 / sh) as u32;

        let mi = MOUSEINPUT {
            dx: abs_x as i32,
            dy: abs_y as i32,
            mouseData: 0,
            dwFlags: MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE,
            time: 0,
            dwExtraInfo: 0,
        };
        unsafe { send_input_mouse(mi); }
    }

    pub fn left_down(&self) {
        unsafe { send_mouse_event(MOUSEEVENTF_LEFTDOWN); }
    }

    pub fn left_up(&self) {
        unsafe { send_mouse_event(MOUSEEVENTF_LEFTUP); }
    }

    pub fn right_down(&self) {
        unsafe { send_mouse_event(MOUSEEVENTF_RIGHTDOWN); }
    }

    pub fn right_up(&self) {
        unsafe { send_mouse_event(MOUSEEVENTF_RIGHTUP); }
    }

    /// Scroll wheel. dy positive = up, dy negative = down, dx positive = right, dx negative = left.
    pub fn scroll(&self, dx: i32, dy: i32) {
        if dy != 0 {
            let mi = MOUSEINPUT {
                dx: 0, dy: 0,
                mouseData: dy as u32,
                dwFlags: MOUSEEVENTF_WHEEL,
                time: 0, dwExtraInfo: 0,
            };
            unsafe { send_input_mouse(mi); }
        }
        if dx != 0 {
            let mi = MOUSEINPUT {
                dx: 0, dy: 0,
                mouseData: dx as u32,
                dwFlags: MOUSEEVENTF_HWHEEL,
                time: 0, dwExtraInfo: 0,
            };
            unsafe { send_input_mouse(mi); }
        }
    }
}

unsafe fn send_mouse_event(flags: u32) {
    let mi = MOUSEINPUT {
        dx: 0,
        dy: 0,
        mouseData: 0,
        dwFlags: flags,
        time: 0,
        dwExtraInfo: 0,
    };
    send_input_mouse(mi);
}

unsafe fn send_input_mouse(mi: MOUSEINPUT) {
    let mut input = INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: INPUT_0 { mi },
    };
    SendInput(1, &mut input, std::mem::size_of::<INPUT>() as i32);
}
