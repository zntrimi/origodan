package com.kazumaproject.custom_keyboard.view

/** Input surface policy; custom layouts retain their intentional untouchable gaps by default. */
enum class KeyHitTestMode {
    KEY_BOUNDS,
    NEAREST_KEY,

    /** Fill visual key margins while preserving explicit spacer cells as dead zones. */
    NEAREST_KEY_EXCLUDING_SPACERS
}
