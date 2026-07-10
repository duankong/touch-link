use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_0, MOUSEINPUT, MOUSEEVENTF_MOVE, MOUSEEVENTF_ABSOLUTE,
    MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP,
    MOUSEEVENTF_WHEEL, INPUT_MOUSE,
};
use windows_sys::Win32::UI::WindowsAndMessaging::GetSystemMetrics;

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

    /// Move mouse to normalized coordinates (0.0-1.0)
    pub fn move_to(&self, nx: f32, ny: f32) {
        let (sw, sh) = Self::screen_size();
        let x = (nx * sw as f32) as u32;
        let y = (ny * sh as f32) as u32;
        let abs_x = (x * 65535 / sw) as u32;
        let abs_y = (y * 65535 / sh) as u32;
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
