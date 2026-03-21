package com.example.a3dsinputredirection

import android.view.KeyEvent

enum class GamepadButtonOption(val label: String, val keyCode: Int) {
    NONE("None", KeyEvent.KEYCODE_UNKNOWN),
    BUTTON_A("A", KeyEvent.KEYCODE_BUTTON_A),
    BUTTON_B("B", KeyEvent.KEYCODE_BUTTON_B),
    BUTTON_X("X", KeyEvent.KEYCODE_BUTTON_X),
    BUTTON_Y("Y", KeyEvent.KEYCODE_BUTTON_Y),
    DPAD_UP("DPad Up", KeyEvent.KEYCODE_DPAD_UP),
    DPAD_DOWN("DPad Down", KeyEvent.KEYCODE_DPAD_DOWN),
    DPAD_LEFT("DPad Left", KeyEvent.KEYCODE_DPAD_LEFT),
    DPAD_RIGHT("DPad Right", KeyEvent.KEYCODE_DPAD_RIGHT),
    L1("L1", KeyEvent.KEYCODE_BUTTON_L1),
    R1("R1", KeyEvent.KEYCODE_BUTTON_R1),
    L2("L2", KeyEvent.KEYCODE_BUTTON_L2),
    R2("R2", KeyEvent.KEYCODE_BUTTON_R2),
    START("Start", KeyEvent.KEYCODE_BUTTON_START),
    SELECT("Select", KeyEvent.KEYCODE_BUTTON_SELECT),
    MODE("Mode/Guide", KeyEvent.KEYCODE_BUTTON_MODE),
    THUMBL("L3", KeyEvent.KEYCODE_BUTTON_THUMBL),
    THUMBR("R3", KeyEvent.KEYCODE_BUTTON_THUMBR);

    companion object {
        fun fromKeyCode(keyCode: Int): GamepadButtonOption {
            return entries.firstOrNull { it.keyCode == keyCode } ?: NONE
        }
    }
}
