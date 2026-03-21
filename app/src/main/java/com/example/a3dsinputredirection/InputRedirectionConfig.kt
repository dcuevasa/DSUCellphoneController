package com.example.a3dsinputredirection

import android.content.SharedPreferences
import android.view.KeyEvent

data class InputRedirectionConfig(
    var targetIp: String = "",
    var cpadBound: Int = 1488,
    var cppBound: Int = 127,
    var invertY: Boolean = false,
    var invertCppY: Boolean = false,
    var swapSticks: Boolean = false,
    var disableCStick: Boolean = false,
    var rightStickAsDPad: Boolean = false,
    var rightStickAsAbxy: Boolean = false,
    var rightStickAsSmash: Boolean = false,
    var mapA: Int = KeyEvent.KEYCODE_BUTTON_A,
    var mapB: Int = KeyEvent.KEYCODE_BUTTON_B,
    var mapX: Int = KeyEvent.KEYCODE_BUTTON_X,
    var mapY: Int = KeyEvent.KEYCODE_BUTTON_Y,
    var mapSelect: Int = KeyEvent.KEYCODE_BUTTON_SELECT,
    var mapStart: Int = KeyEvent.KEYCODE_BUTTON_START,
    var mapDpadRight: Int = KeyEvent.KEYCODE_DPAD_RIGHT,
    var mapDpadLeft: Int = KeyEvent.KEYCODE_DPAD_LEFT,
    var mapDpadUp: Int = KeyEvent.KEYCODE_DPAD_UP,
    var mapDpadDown: Int = KeyEvent.KEYCODE_DPAD_DOWN,
    var mapR: Int = KeyEvent.KEYCODE_BUTTON_R1,
    var mapL: Int = KeyEvent.KEYCODE_BUTTON_L1,
    var mapZr: Int = KeyEvent.KEYCODE_BUTTON_R2,
    var mapZl: Int = KeyEvent.KEYCODE_BUTTON_L2,
    var mapHome: Int = KeyEvent.KEYCODE_UNKNOWN,
    var mapPower: Int = KeyEvent.KEYCODE_UNKNOWN,
    var mapPowerLong: Int = KeyEvent.KEYCODE_UNKNOWN,
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString("targetIp", targetIp)
            .putInt("cpadBound", cpadBound)
            .putInt("cppBound", cppBound)
            .putBoolean("invertY", invertY)
            .putBoolean("invertCppY", invertCppY)
            .putBoolean("swapSticks", swapSticks)
            .putBoolean("disableCStick", disableCStick)
            .putBoolean("rightStickAsDPad", rightStickAsDPad)
            .putBoolean("rightStickAsAbxy", rightStickAsAbxy)
            .putBoolean("rightStickAsSmash", rightStickAsSmash)
            .putInt("mapA", mapA)
            .putInt("mapB", mapB)
            .putInt("mapX", mapX)
            .putInt("mapY", mapY)
            .putInt("mapSelect", mapSelect)
            .putInt("mapStart", mapStart)
            .putInt("mapDpadRight", mapDpadRight)
            .putInt("mapDpadLeft", mapDpadLeft)
            .putInt("mapDpadUp", mapDpadUp)
            .putInt("mapDpadDown", mapDpadDown)
            .putInt("mapR", mapR)
            .putInt("mapL", mapL)
            .putInt("mapZr", mapZr)
            .putInt("mapZl", mapZl)
            .putInt("mapHome", mapHome)
            .putInt("mapPower", mapPower)
            .putInt("mapPowerLong", mapPowerLong)
            .apply()
    }

    companion object {
        fun load(prefs: SharedPreferences): InputRedirectionConfig {
            return InputRedirectionConfig(
                targetIp = prefs.getString("targetIp", "") ?: "",
                cpadBound = prefs.getInt("cpadBound", 1488),
                cppBound = prefs.getInt("cppBound", 127),
                invertY = prefs.getBoolean("invertY", false),
                invertCppY = prefs.getBoolean("invertCppY", false),
                swapSticks = prefs.getBoolean("swapSticks", false),
                disableCStick = prefs.getBoolean("disableCStick", false),
                rightStickAsDPad = prefs.getBoolean("rightStickAsDPad", false),
                rightStickAsAbxy = prefs.getBoolean("rightStickAsAbxy", false),
                rightStickAsSmash = prefs.getBoolean("rightStickAsSmash", false),
                mapA = prefs.getInt("mapA", KeyEvent.KEYCODE_BUTTON_A),
                mapB = prefs.getInt("mapB", KeyEvent.KEYCODE_BUTTON_B),
                mapX = prefs.getInt("mapX", KeyEvent.KEYCODE_BUTTON_X),
                mapY = prefs.getInt("mapY", KeyEvent.KEYCODE_BUTTON_Y),
                mapSelect = prefs.getInt("mapSelect", KeyEvent.KEYCODE_BUTTON_SELECT),
                mapStart = prefs.getInt("mapStart", KeyEvent.KEYCODE_BUTTON_START),
                mapDpadRight = prefs.getInt("mapDpadRight", KeyEvent.KEYCODE_DPAD_RIGHT),
                mapDpadLeft = prefs.getInt("mapDpadLeft", KeyEvent.KEYCODE_DPAD_LEFT),
                mapDpadUp = prefs.getInt("mapDpadUp", KeyEvent.KEYCODE_DPAD_UP),
                mapDpadDown = prefs.getInt("mapDpadDown", KeyEvent.KEYCODE_DPAD_DOWN),
                mapR = prefs.getInt("mapR", KeyEvent.KEYCODE_BUTTON_R1),
                mapL = prefs.getInt("mapL", KeyEvent.KEYCODE_BUTTON_L1),
                mapZr = prefs.getInt("mapZr", KeyEvent.KEYCODE_BUTTON_R2),
                mapZl = prefs.getInt("mapZl", KeyEvent.KEYCODE_BUTTON_L2),
                mapHome = prefs.getInt("mapHome", KeyEvent.KEYCODE_UNKNOWN),
                mapPower = prefs.getInt("mapPower", KeyEvent.KEYCODE_UNKNOWN),
                mapPowerLong = prefs.getInt("mapPowerLong", KeyEvent.KEYCODE_UNKNOWN),
            )
        }
    }
}
