package com.example.a3dsinputredirection

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

class InputRedirectionClient(
    private val configProvider: () -> InputRedirectionConfig,
) {
    companion object {
        private const val UDP_PORT = 4950
        private const val TOUCH_MAX = 0xFFF

        private const val SPECIAL_HOME = 1
        private const val SPECIAL_POWER = 2
        private const val SPECIAL_POWER_LONG = 4
    }

    private data class State(
        val pressedKeys: MutableSet<Int> = mutableSetOf(),
        val uiPressedKeys: MutableSet<Int> = mutableSetOf(),
        var leftX: Float = 0f,
        var leftY: Float = 0f,
        var rightX: Float = 0f,
        var rightY: Float = 0f,
        var uiLeftActive: Boolean = false,
        var uiLeftX: Float = 0f,
        var uiLeftY: Float = 0f,
        var uiRightActive: Boolean = false,
        var uiRightX: Float = 0f,
        var uiRightY: Float = 0f,
        var touchActive: Boolean = false,
        var touchX: Int = 0,
        var touchY: Int = 0,
        var uiSpecialMask: Int = 0,
    )

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val state = State()
    private var sendJob: Job? = null
    private var socket: DatagramSocket? = null

    fun isRunning(): Boolean = sendJob?.isActive == true

    fun start() {
        if (isRunning()) return
        sendJob = scope.launch {
            socket = DatagramSocket()
            while (isActive) {
                sendFrame()
                delay(20)
            }
        }
    }

    fun stop() {
        sendJob?.cancel()
        sendJob = null
        socket?.close()
        socket = null
        synchronized(stateLock) {
            state.pressedKeys.clear()
            state.uiPressedKeys.clear()
            state.leftX = 0f
            state.leftY = 0f
            state.rightX = 0f
            state.rightY = 0f
            state.uiLeftActive = false
            state.uiLeftX = 0f
            state.uiLeftY = 0f
            state.uiRightActive = false
            state.uiRightX = 0f
            state.uiRightY = 0f
            state.touchActive = false
            state.uiSpecialMask = 0
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isGamepadKey(event.keyCode)) return false
        synchronized(stateLock) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> state.pressedKeys.add(event.keyCode)
                KeyEvent.ACTION_UP -> state.pressedKeys.remove(event.keyCode)
            }
        }
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK ||
            event.action != MotionEvent.ACTION_MOVE
        ) {
            return false
        }

        val lx = centeredAxis(event, event.device, MotionEvent.AXIS_X)
        val ly = centeredAxis(event, event.device, MotionEvent.AXIS_Y)
        val rx = centeredAxis(event, event.device, MotionEvent.AXIS_Z)
        val ry = centeredAxis(event, event.device, MotionEvent.AXIS_RZ)

        synchronized(stateLock) {
            state.leftX = lx
            state.leftY = ly
            state.rightX = rx
            state.rightY = ry
        }
        return true
    }

    fun updateTouch(active: Boolean, x: Int, y: Int) {
        synchronized(stateLock) {
            state.touchActive = active
            state.touchX = x.coerceIn(0, TOUCH_MAX)
            state.touchY = y.coerceIn(0, TOUCH_MAX)
        }
    }

    fun updateUiLeftStick(active: Boolean, x: Float, y: Float) {
        synchronized(stateLock) {
            state.uiLeftActive = active
            state.uiLeftX = x.coerceIn(-1f, 1f)
            state.uiLeftY = y.coerceIn(-1f, 1f)
        }
    }

    fun updateUiRightStick(active: Boolean, x: Float, y: Float) {
        synchronized(stateLock) {
            state.uiRightActive = active
            state.uiRightX = x.coerceIn(-1f, 1f)
            state.uiRightY = y.coerceIn(-1f, 1f)
        }
    }

    fun setUiKeyPressed(keyCode: Int, pressed: Boolean) {
        if (!isGamepadKey(keyCode)) return
        synchronized(stateLock) {
            if (pressed) {
                state.uiPressedKeys.add(keyCode)
            } else {
                state.uiPressedKeys.remove(keyCode)
            }
        }
    }

    fun setUiSpecial(bitMask: Int, pressed: Boolean) {
        synchronized(stateLock) {
            state.uiSpecialMask = if (pressed) {
                state.uiSpecialMask or bitMask
            } else {
                state.uiSpecialMask and bitMask.inv()
            }
        }
    }

    fun pressHome(pressed: Boolean) = setUiSpecial(SPECIAL_HOME, pressed)
    fun pressPower(pressed: Boolean) = setUiSpecial(SPECIAL_POWER, pressed)
    fun pressPowerLong(pressed: Boolean) = setUiSpecial(SPECIAL_POWER_LONG, pressed)

    private fun sendFrame() {
        val cfg = configProvider()
        if (cfg.targetIp.isBlank()) return

        val snapshot = synchronized(stateLock) {
            State(
                pressedKeys = state.pressedKeys.toMutableSet(),
                uiPressedKeys = state.uiPressedKeys.toMutableSet(),
                leftX = state.leftX,
                leftY = state.leftY,
                rightX = state.rightX,
                rightY = state.rightY,
                uiLeftActive = state.uiLeftActive,
                uiLeftX = state.uiLeftX,
                uiLeftY = state.uiLeftY,
                uiRightActive = state.uiRightActive,
                uiRightX = state.uiRightX,
                uiRightY = state.uiRightY,
                touchActive = state.touchActive,
                touchX = state.touchX,
                touchY = state.touchY,
                uiSpecialMask = state.uiSpecialMask,
            )
        }

        val pressedKeys = snapshot.pressedKeys
        val uiPressedKeys = snapshot.uiPressedKeys
        val lRawX = if (snapshot.uiLeftActive) snapshot.uiLeftX else snapshot.leftX
        val lRawY = if (snapshot.uiLeftActive) snapshot.uiLeftY else snapshot.leftY
        val rRawX = if (snapshot.uiRightActive) snapshot.uiRightX else snapshot.rightX
        val rRawY = if (snapshot.uiRightActive) snapshot.uiRightY else snapshot.rightY
        val touchActive = snapshot.touchActive
        val touchX = snapshot.touchX
        val touchY = snapshot.touchY
        val uiSpecialMask = snapshot.uiSpecialMask

        val leftX: Float
        var leftY = -lRawY
        var rightX = rRawX
        var rightY = -rRawY

        var tempLeftX = lRawX
        if (cfg.swapSticks) {
            tempLeftX = rRawX
            leftY = -rRawY
            rightX = lRawX
            rightY = -lRawY
        }

        leftX = tempLeftX

        if (cfg.invertY) leftY = -leftY
        if (cfg.invertCppY) rightY = -rightY

        val pressed = pressedKeys.toMutableSet().apply { addAll(uiPressedKeys) }

        val rightThreshold = 0.5f
        if (cfg.rightStickAsDPad) {
            if (rightX < -rightThreshold) pressed += cfg.mapDpadLeft
            if (rightX > rightThreshold) pressed += cfg.mapDpadRight
            if (rightY < -rightThreshold) pressed += cfg.mapDpadDown
            if (rightY > rightThreshold) pressed += cfg.mapDpadUp
        }

        if (cfg.rightStickAsAbxy) {
            if (rightX < -rightThreshold) pressed += cfg.mapY
            if (rightX > rightThreshold) pressed += cfg.mapA
            if (rightY < -rightThreshold) pressed += cfg.mapB
            if (rightY > rightThreshold) pressed += cfg.mapX
        }

        var smashLeftX = leftX
        var smashLeftY = leftY
        var smashA = false
        if (cfg.rightStickAsSmash) {
            if (abs(rightX) > rightThreshold) {
                smashLeftX = rightX.sign
                smashA = true
            }
            if (abs(rightY) > rightThreshold) {
                smashLeftY = rightY.sign
                smashA = true
            }
        }
        if (smashA) pressed += cfg.mapA

        var hidPad = 0x00000FFF
        hidPad = applyHidBit(hidPad, pressed, cfg.mapA, 0)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapB, 1)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapSelect, 2)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapStart, 3)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadRight, 4)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadLeft, 5)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadUp, 6)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadDown, 7)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapR, 8)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapL, 9)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapX, 10)
        hidPad = applyHidBit(hidPad, pressed, cfg.mapY, 11)

        val irButtonsState =
            ((if (isPressed(pressed, cfg.mapZr)) 1 else 0) shl 1) or
                ((if (isPressed(pressed, cfg.mapZl)) 1 else 0) shl 2)

        val touchState = if (touchActive) {
            (1 shl 24) or ((touchY and 0xFFF) shl 12) or (touchX and 0xFFF)
        } else {
            0x02000000
        }

        val circlePadState = if (abs(smashLeftX) > 0.001f || abs(smashLeftY) > 0.001f) {
            val x = ((smashLeftX * cfg.cpadBound) + 0x800).roundToInt().coerceIn(0, 0xFFF)
            val y = ((smashLeftY * cfg.cpadBound) + 0x800).roundToInt().coerceIn(0, 0xFFF)
            ((y and 0xFFF) shl 12) or (x and 0xFFF)
        } else {
            0x007FF7FF
        }

        val cppState = if (
            (!cfg.disableCStick && (abs(rightX) > 0.001f || abs(rightY) > 0.001f)) ||
            irButtonsState != 0
        ) {
            val norm = (1f / sqrt(2f))
            val rotX = norm * (rightX + rightY)
            val rotY = norm * (rightY - rightX)
            val x = ((rotX * cfg.cppBound) + 0x80).roundToInt().coerceIn(0, 0xFF)
            val y = ((rotY * cfg.cppBound) + 0x80).roundToInt().coerceIn(0, 0xFF)
            (y shl 24) or (x shl 16) or ((irButtonsState and 0xFF) shl 8) or 0x81
        } else {
            0x80800081.toInt()
        }

        var special = uiSpecialMask
        if (isPressed(pressed, cfg.mapHome)) special = special or SPECIAL_HOME
        if (isPressed(pressed, cfg.mapPower)) special = special or SPECIAL_POWER
        if (isPressed(pressed, cfg.mapPowerLong)) special = special or SPECIAL_POWER_LONG

        val packetData = ByteBuffer
            .allocate(20)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(hidPad)
            .putInt(touchState)
            .putInt(circlePadState)
            .putInt(cppState)
            .putInt(special)
            .array()

        val address = runCatching { InetAddress.getByName(cfg.targetIp.trim()) }.getOrNull() ?: return
        val datagram = DatagramPacket(packetData, packetData.size, address, UDP_PORT)
        runCatching { socket?.send(datagram) }
    }

    private fun applyHidBit(hidPad: Int, pressed: Set<Int>, mappedKey: Int, bit: Int): Int {
        if (!isPressed(pressed, mappedKey)) return hidPad
        return hidPad and (1 shl bit).inv()
    }

    private fun isPressed(pressed: Set<Int>, mappedKey: Int): Boolean {
        if (mappedKey == KeyEvent.KEYCODE_UNKNOWN) return false
        return pressed.contains(mappedKey)
    }

    private fun centeredAxis(event: MotionEvent, device: InputDevice?, axis: Int): Float {
        val range = device?.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > range.flat) value else 0f
    }

    private fun isGamepadKey(keyCode: Int): Boolean {
        return keyCode in setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}
