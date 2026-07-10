use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_0, KEYBDINPUT, KEYEVENTF_KEYUP, INPUT_KEYBOARD,
};

/// Simulate a key press (down then up)
pub fn press(vk: u16) {
    down(vk);
    up(vk);
}

/// Simulate key down
pub fn down(vk: u16) {
    unsafe { send_key(vk, 0); }
}

/// Simulate key up
pub fn up(vk: u16) {
    unsafe { send_key(vk, KEYEVENTF_KEYUP); }
}

unsafe fn send_key(vk: u16, flags: u32) {
    let ki = KEYBDINPUT {
        wVk: vk,
        wScan: 0,
        dwFlags: flags,
        time: 0,
        dwExtraInfo: 0,
    };
    let mut input = INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 { ki },
    };
    SendInput(1, &mut input, std::mem::size_of::<INPUT>() as i32);
}
