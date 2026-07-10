use windows_sys::Win32::Foundation::POINT;
use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_0, MOUSEINPUT, MOUSEEVENTF_MOVE, MOUSEEVENTF_ABSOLUTE,
    MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP,
    MOUSEEVENTF_WHEEL, INPUT_MOUSE,
};
use windows_sys::Win32::UI::WindowsAndMessaging::{GetCursorPos, GetSystemMetrics};

/// Sensitivity multiplier for touch-to-mouse movement.
/// A value of 2.0 means a full-screen finger swipe moves the cursor 2× across the screen.
const SENSITIVITY: f32 = 2.0;

pub struct Mouse;

impl Mouse {
    pub fn new() -> Self {
        Self
    }

    fn screen_size() -> (u32, u32) {
        let w = unsafe { GetSystemMetrics(0) } as u32;
        let h = unsafe { GetSystemMetrics(1) } as u32;
        (w, h)
    }

    /// Move mouse by a normalized delta (trackpad behavior).
    /// (dx, dy) are in 0.0–1.0 normalized space.
    /// Gets current cursor position, adds delta × screen size × sensitivity,
    /// then sends absolute position to avoid mouse-acceleration interference.
    pub fn move_by(&self, dx: f32, dy: f32) {
        if dx == 0.0 && dy == 0.0 {
            return;
        }
        let mut pos = POINT { x: 0, y: 0 };
        unsafe {
            let _ = GetCursorPos(&mut pos);
        }
        let (sw, sh) = Self::screen_size();
        let new_x = (pos.x as f32 + dx * sw as f32 * SENSITIVITY) as i32;
        let new_y = (pos.y as f32 + dy * sh as f32 * SENSITIVITY) as i32;
        // Clamp to screen bounds
        let new_x = new_x.max(0).min((sw - 1) as i32);
        let new_y = new_y.max(0).min((sh - 1) as i32);
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

    /// Scroll wheel. Positive = up, negative = down.
    pub fn scroll(&self, delta: i32) {
        let mi = MOUSEINPUT {
            dx: 0,
            dy: 0,
            mouseData: delta as u32,
            dwFlags: MOUSEEVENTF_WHEEL,
            time: 0,
            dwExtraInfo: 0,
        };
        unsafe { send_input_mouse(mi); }
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
