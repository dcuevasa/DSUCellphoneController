package com.example.cemuhookcellphonecontroller

import android.os.SystemClock
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
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class InputRedirectionClient(
    private val configProvider: () -> InputRedirectionConfig,
) {
    companion object {
        private const val DSU_PORT = 26760
        private const val PROTOCOL_VERSION = 1001
        private const val SERVER_ID = 1001
        private const val DEFAULT_CONTROLLER_SLOT = 0
        private val DEVICE_MAC = byteArrayOf(0, 0, 0, 0, 0, 0)
        private const val CLIENT_TIMEOUT_MS = 60_000L
        private const val SEND_INTERVAL_MS = 16L
        private const val RECEIVE_TIMEOUT_MS = 2

        private const val EVENT_PROTOCOL_VERSION = 0x100000
        private const val EVENT_CONTROLLER_INFO = 0x100001
        private const val EVENT_CONTROLLER_DATA = 0x100002

        private const val TOUCH_MAX = 0xFFF
        private const val TOUCH_MAX_X = 1919
        private const val TOUCH_MAX_Y = 941

        private const val GYRO_DEADZONE_DPS = 0.7f
        private const val ACCEL_DEADZONE_G = 0.03f
        private const val GYRO_CLAMP_DPS = 2000f
        private const val ACCEL_CLAMP_G = 8f

        private const val SPECIAL_HOME = 1
        private const val SPECIAL_TOUCH = 2
        private const val SPECIAL_GUIDE = 4

        private val MAGIC_DSUC = byteArrayOf('D'.code.toByte(), 'S'.code.toByte(), 'U'.code.toByte(), 'C'.code.toByte())
        private val MAGIC_DSUS = byteArrayOf('D'.code.toByte(), 'S'.code.toByte(), 'U'.code.toByte(), 'S'.code.toByte())
    }

    private data class Subscription(
        var flags: Int = 0,
        var slot: Int = DEFAULT_CONTROLLER_SLOT,
        var mac: ByteArray = ByteArray(6),
        var packetNum: Int = 0,
        var lastSeenMs: Long = SystemClock.elapsedRealtime(),
    )

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
        var accelXg: Float = 0f,
        var accelYg: Float = 0f,
        var accelZg: Float = 0f,
        var gyroPitchDps: Float = 0f,
        var gyroYawDps: Float = 0f,
        var gyroRollDps: Float = 0f,
        var motionTimestampMicros: Long = System.nanoTime() / 1_000L,
    )

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val subscriptionsLock = Any()
    private val state = State()
    private val subscriptions = mutableMapOf<InetSocketAddress, Subscription>()
    private var sendJob: Job? = null
    private var socket: DatagramSocket? = null

    fun isRunning(): Boolean = sendJob?.isActive == true

    fun activeSubscribers(): Int {
        synchronized(subscriptionsLock) {
            return subscriptions.size
        }
    }

    fun start() {
        if (isRunning()) return
        sendJob = scope.launch {
            socket = DatagramSocket(DSU_PORT).apply {
                soTimeout = RECEIVE_TIMEOUT_MS
            }
            while (isActive) {
                processIncomingPackets()
                sendFrameToSubscribers()
                cleanupInactiveSubscribers()
                delay(SEND_INTERVAL_MS)
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

        synchronized(subscriptionsLock) {
            subscriptions.clear()
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
    fun pressPower(pressed: Boolean) = setUiSpecial(SPECIAL_TOUCH, pressed)
    fun pressPowerLong(pressed: Boolean) = setUiSpecial(SPECIAL_GUIDE, pressed)

    fun updateImu(
        accelXg: Float,
        accelYg: Float,
        accelZg: Float,
        gyroPitchDps: Float,
        gyroYawDps: Float,
        gyroRollDps: Float,
        timestampMicros: Long,
    ) {
        synchronized(stateLock) {
            state.accelXg = sanitizeMotion(accelXg, ACCEL_CLAMP_G, ACCEL_DEADZONE_G)
            state.accelYg = sanitizeMotion(accelYg, ACCEL_CLAMP_G, ACCEL_DEADZONE_G)
            state.accelZg = sanitizeMotion(accelZg, ACCEL_CLAMP_G, ACCEL_DEADZONE_G)
            state.gyroPitchDps = sanitizeMotion(gyroPitchDps, GYRO_CLAMP_DPS, GYRO_DEADZONE_DPS)
            state.gyroYawDps = sanitizeMotion(gyroYawDps, GYRO_CLAMP_DPS, GYRO_DEADZONE_DPS)
            state.gyroRollDps = sanitizeMotion(gyroRollDps, GYRO_CLAMP_DPS, GYRO_DEADZONE_DPS)
            state.motionTimestampMicros = timestampMicros
        }
    }

    private fun processIncomingPackets() {
        repeat(6) {
            if (!receiveOnePacket()) return
        }
    }

    private fun receiveOnePacket(): Boolean {
        val localSocket = socket ?: return false
        val buffer = ByteArray(512)
        val packet = DatagramPacket(buffer, buffer.size)

        try {
            localSocket.receive(packet)
        } catch (_: SocketTimeoutException) {
            return false
        } catch (_: Exception) {
            return false
        }

        val cfg = configProvider()
        if (!isClientAllowed(cfg, packet.address)) {
            return true
        }

        val data = packet.data.copyOf(packet.length)
        handleIncomingPacket(data, InetSocketAddress(packet.address, packet.port))
        return true
    }

    private fun handleIncomingPacket(data: ByteArray, sender: InetSocketAddress) {
        if (data.size < 20) return
        if (!data.copyOfRange(0, 4).contentEquals(MAGIC_DSUC)) return

        val version = readU16LE(data, 4)
        if (version != PROTOCOL_VERSION) return

        val payloadLen = readU16LE(data, 6)
        val totalLen = 16 + payloadLen
        if (data.size < totalLen) return

        val packet = data.copyOf(totalLen)
        val receivedCrc = readU32LE(packet, 8)
        packet[8] = 0
        packet[9] = 0
        packet[10] = 0
        packet[11] = 0
        val computedCrc = crc32(packet)
        if (receivedCrc != computedCrc) return

        val eventType = readU32LE(packet, 16).toInt()
        val eventPayload = packet.copyOfRange(20, packet.size)

        when (eventType) {
            EVENT_PROTOCOL_VERSION -> {
                val payload = ByteBuffer.allocate(6)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(EVENT_PROTOCOL_VERSION)
                    .putShort(PROTOCOL_VERSION.toShort())
                    .array()
                sendPacket(sender, payload)
            }

            EVENT_CONTROLLER_INFO -> {
                if (eventPayload.size < 4) return
                val portsRequested = readI32LE(eventPayload, 0)
                if (portsRequested !in 0..4) return
                if (eventPayload.size < 4 + portsRequested) return

                for (i in 0 until portsRequested) {
                    val slot = eventPayload[4 + i].toInt() and 0xFF
                    val payload = ByteBuffer.allocate(16)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(EVENT_CONTROLLER_INFO)
                        .put(slot.toByte())
                        .put(2.toByte())
                        .put(2.toByte())
                        .put(2.toByte())
                        .put(DEVICE_MAC)
                        .put(0xEF.toByte())
                        .put(0.toByte())
                        .array()
                    sendPacket(sender, payload)
                }
            }

            EVENT_CONTROLLER_DATA -> {
                val flags = if (eventPayload.isNotEmpty()) eventPayload[0].toInt() and 0xFF else 0
                val rawSlot = if (eventPayload.size >= 2) eventPayload[1].toInt() and 0xFF else 0
                val slot = normalizeSlot(rawSlot)
                val mac = if (eventPayload.size >= 8) {
                    eventPayload.copyOfRange(2, 8)
                } else {
                    ByteArray(6)
                }

                synchronized(subscriptionsLock) {
                    val sub = subscriptions[sender] ?: Subscription()
                    sub.flags = flags
                    sub.slot = slot
                    sub.mac = mac
                    sub.lastSeenMs = SystemClock.elapsedRealtime()
                    subscriptions[sender] = sub
                }
            }
        }
    }

    private fun sendFrameToSubscribers() {
        val cfg = configProvider()
        val subscribersSnapshot = synchronized(subscriptionsLock) { subscriptions.toMap() }
        if (subscribersSnapshot.isEmpty()) return

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
                accelXg = state.accelXg,
                accelYg = state.accelYg,
                accelZg = state.accelZg,
                gyroPitchDps = state.gyroPitchDps,
                gyroYawDps = state.gyroYawDps,
                gyroRollDps = state.gyroRollDps,
                motionTimestampMicros = state.motionTimestampMicros,
            )
        }

        val pressedKeys = snapshot.pressedKeys
        val uiPressedKeys = snapshot.uiPressedKeys
        val lRawX = if (snapshot.uiLeftActive) snapshot.uiLeftX else snapshot.leftX
        val lRawY = if (snapshot.uiLeftActive) snapshot.uiLeftY else snapshot.leftY
        val rRawX = if (snapshot.uiRightActive) snapshot.uiRightX else snapshot.rightX
        val rRawY = if (snapshot.uiRightActive) snapshot.uiRightY else snapshot.rightY

        var leftX = lRawX
        var leftY = -lRawY
        var rightX = rRawX
        var rightY = -rRawY

        if (cfg.swapSticks) {
            leftX = rRawX
            leftY = -rRawY
            rightX = lRawX
            rightY = -lRawY
        }

        if (cfg.invertY) leftY = -leftY
        if (cfg.invertCppY) rightY = -rightY
        if (cfg.disableCStick) {
            rightX = 0f
            rightY = 0f
        }

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

        if (cfg.rightStickAsSmash) {
            var useSmash = false
            if (abs(rightX) > rightThreshold) {
                leftX = rightX.sign
                useSmash = true
            }
            if (abs(rightY) > rightThreshold) {
                leftY = rightY.sign
                useSmash = true
            }
            if (useSmash) pressed += cfg.mapA
        }

        val leftScale = cfg.cpadBound.coerceIn(10, 200) / 100f
        val rightScale = cfg.cppBound.coerceIn(10, 200) / 100f

        val lx = axisToByte(leftX * leftScale)
        val ly = axisToByte(leftY * leftScale)
        val rx = axisToByte(rightX * rightScale)
        val ry = axisToByte(rightY * rightScale)

        var homePressed = (snapshot.uiSpecialMask and SPECIAL_HOME) != 0 || isPressed(pressed, cfg.mapHome)
        val touchPressed = (snapshot.uiSpecialMask and SPECIAL_TOUCH) != 0 || isPressed(pressed, cfg.mapPower)
        if ((snapshot.uiSpecialMask and SPECIAL_GUIDE) != 0 || isPressed(pressed, cfg.mapPowerLong)) {
            homePressed = true
        }

        val buttons1 = buildButtons1(pressed, cfg)
        val buttons2 = buildButtons2(pressed, cfg)

        val analogDpadL = if (isPressed(pressed, cfg.mapDpadLeft)) 0xFF else 0x00
        val analogDpadD = if (isPressed(pressed, cfg.mapDpadDown)) 0xFF else 0x00
        val analogDpadR = if (isPressed(pressed, cfg.mapDpadRight)) 0xFF else 0x00
        val analogDpadU = if (isPressed(pressed, cfg.mapDpadUp)) 0xFF else 0x00

        val analogY = if (isPressed(pressed, cfg.mapY)) 0xFF else 0x00
        val analogB = if (isPressed(pressed, cfg.mapB)) 0xFF else 0x00
        val analogA = if (isPressed(pressed, cfg.mapA)) 0xFF else 0x00
        val analogX = if (isPressed(pressed, cfg.mapX)) 0xFF else 0x00

        val analogR1 = if (isPressed(pressed, cfg.mapR)) 0xFF else 0x00
        val analogL1 = if (isPressed(pressed, cfg.mapL)) 0xFF else 0x00
        val analogR2 = if (isPressed(pressed, cfg.mapZr)) 0xFF else 0x00
        val analogL2 = if (isPressed(pressed, cfg.mapZl)) 0xFF else 0x00

        val touchX = ((snapshot.touchX.coerceIn(0, TOUCH_MAX).toFloat() / TOUCH_MAX) * TOUCH_MAX_X)
            .roundToInt()
            .coerceIn(0, TOUCH_MAX_X)
        val touchY = ((snapshot.touchY.coerceIn(0, TOUCH_MAX).toFloat() / TOUCH_MAX) * TOUCH_MAX_Y)
            .roundToInt()
            .coerceIn(0, TOUCH_MAX_Y)
        val touch1 = buildTouchPacket(snapshot.touchActive, 0, touchX, touchY)
        val touch2 = buildTouchPacket(false, 0, 0, 0)
        val motionTimestampMicros = snapshot.motionTimestampMicros

        for ((addr, sub) in subscribersSnapshot) {
            if (!subscriptionMatches(sub)) continue

            val effectiveSlot = effectiveSlotForClient(sub)
            val packetNum = synchronized(subscriptionsLock) {
                val realSub = subscriptions[addr] ?: return@synchronized null
                realSub.packetNum = realSub.packetNum + 1
                realSub.packetNum
            } ?: continue

            val controllerData = ByteBuffer.allocate(80)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(effectiveSlot.toByte())
                .put(2.toByte())
                .put(2.toByte())
                .put(2.toByte())
                .put(DEVICE_MAC)
                .put(0xEF.toByte())
                .put(1.toByte())
                .putInt(packetNum)
                .put(buttons1.toByte())
                .put(buttons2.toByte())
                .put(if (homePressed) 1.toByte() else 0.toByte())
                .put(if (touchPressed) 1.toByte() else 0.toByte())
                .put(lx.toByte())
                .put(ly.toByte())
                .put(rx.toByte())
                .put(ry.toByte())
                .put(analogDpadL.toByte())
                .put(analogDpadD.toByte())
                .put(analogDpadR.toByte())
                .put(analogDpadU.toByte())
                .put(analogY.toByte())
                .put(analogB.toByte())
                .put(analogA.toByte())
                .put(analogX.toByte())
                .put(analogR1.toByte())
                .put(analogL1.toByte())
                .put(analogR2.toByte())
                .put(analogL2.toByte())
                .put(touch1)
                .put(touch2)
                .putLong(motionTimestampMicros)
                .putFloat(snapshot.accelXg)
                .putFloat(snapshot.accelYg)
                .putFloat(snapshot.accelZg)
                .putFloat(snapshot.gyroPitchDps)
                .putFloat(snapshot.gyroYawDps)
                .putFloat(snapshot.gyroRollDps)
                .array()

            val payload = ByteBuffer.allocate(4 + controllerData.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(EVENT_CONTROLLER_DATA)
                .put(controllerData)
                .array()

            sendPacket(addr, payload)
        }
    }

    private fun sendPacket(addr: InetSocketAddress, payload: ByteArray) {
        val localSocket = socket ?: return

        val header = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MAGIC_DSUS)
            .putShort(PROTOCOL_VERSION.toShort())
            .putShort(payload.size.toShort())
            .putInt(0)
            .putInt(SERVER_ID)
            .array()

        val packetWithoutCrc = ByteArray(header.size + payload.size)
        System.arraycopy(header, 0, packetWithoutCrc, 0, header.size)
        System.arraycopy(payload, 0, packetWithoutCrc, header.size, payload.size)

        val crc = crc32(packetWithoutCrc).toInt()
        val finalHeader = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MAGIC_DSUS)
            .putShort(PROTOCOL_VERSION.toShort())
            .putShort(payload.size.toShort())
            .putInt(crc)
            .putInt(SERVER_ID)
            .array()

        val finalPacket = ByteArray(finalHeader.size + payload.size)
        System.arraycopy(finalHeader, 0, finalPacket, 0, finalHeader.size)
        System.arraycopy(payload, 0, finalPacket, finalHeader.size, payload.size)

        runCatching {
            localSocket.send(DatagramPacket(finalPacket, finalPacket.size, addr.address, addr.port))
        }
    }

    private fun cleanupInactiveSubscribers() {
        val now = SystemClock.elapsedRealtime()
        synchronized(subscriptionsLock) {
            val iterator = subscriptions.iterator()
            while (iterator.hasNext()) {
                val (_, sub) = iterator.next()
                if (now - sub.lastSeenMs > CLIENT_TIMEOUT_MS) {
                    iterator.remove()
                }
            }
        }
    }

    private fun isClientAllowed(cfg: InputRedirectionConfig, address: InetAddress): Boolean {
        val filter = cfg.targetIp.trim()
        if (filter.isBlank()) return true
        return address.hostAddress == filter || address.hostName == filter
    }

    private fun subscriptionMatches(sub: Subscription): Boolean {
        // Some DSU clients use non-standard flags/slot values.
        // Keep this permissive so button events are never dropped.
        return true
    }

    private fun effectiveSlotForClient(sub: Subscription): Int {
        if (sub.slot in 0..3) {
            return sub.slot
        }
        return DEFAULT_CONTROLLER_SLOT
    }

    private fun normalizeSlot(rawSlot: Int): Int {
        // Some clients send 1..4 for "controller 1..4" UI labels.
        // DSU payload expects 0..3 slot values.
        return when (rawSlot) {
            in 1..4 -> rawSlot - 1
            else -> rawSlot
        }
    }

    private fun buildButtons1(pressed: Set<Int>, cfg: InputRedirectionConfig): Int {
        var value = 0
        if (isPressed(pressed, cfg.mapSelect)) value = value or (1 shl 0)
        if (isPressed(pressed, KeyEvent.KEYCODE_BUTTON_THUMBL)) value = value or (1 shl 1)
        if (isPressed(pressed, KeyEvent.KEYCODE_BUTTON_THUMBR)) value = value or (1 shl 2)
        if (isPressed(pressed, cfg.mapStart)) value = value or (1 shl 3)
        if (isPressed(pressed, cfg.mapDpadUp)) value = value or (1 shl 4)
        if (isPressed(pressed, cfg.mapDpadRight)) value = value or (1 shl 5)
        if (isPressed(pressed, cfg.mapDpadDown)) value = value or (1 shl 6)
        if (isPressed(pressed, cfg.mapDpadLeft)) value = value or (1 shl 7)
        return value
    }

    private fun buildButtons2(pressed: Set<Int>, cfg: InputRedirectionConfig): Int {
        var value = 0
        // DSU Buttons 2 bit order (MSB->LSB): Y, B, A, X, R1, L1, R2, L2
        if (isPressed(pressed, cfg.mapY)) value = value or (1 shl 7)
        if (isPressed(pressed, cfg.mapB)) value = value or (1 shl 6)
        if (isPressed(pressed, cfg.mapA)) value = value or (1 shl 5)
        if (isPressed(pressed, cfg.mapX)) value = value or (1 shl 4)
        if (isPressed(pressed, cfg.mapR)) value = value or (1 shl 3)
        if (isPressed(pressed, cfg.mapL)) value = value or (1 shl 2)
        if (isPressed(pressed, cfg.mapZr)) value = value or (1 shl 1)
        if (isPressed(pressed, cfg.mapZl)) value = value or (1 shl 0)
        return value
    }

    private fun buildTouchPacket(active: Boolean, touchId: Int, x: Int, y: Int): ByteArray {
        return ByteBuffer.allocate(6)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(if (active) 1.toByte() else 0.toByte())
            .put((touchId and 0xFF).toByte())
            .putShort((x and 0xFFFF).toShort())
            .putShort((y and 0xFFFF).toShort())
            .array()
    }

    private fun axisToByte(value: Float): Int {
        val clamped = value.coerceIn(-1f, 1f)
        return ((clamped * 127f) + 128f).roundToInt().coerceIn(0, 255)
    }

    private fun sanitizeMotion(value: Float, clamp: Float, deadzone: Float): Float {
        val clamped = value.coerceIn(-clamp, clamp)
        return if (abs(clamped) < deadzone) 0f else clamped
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

    private fun readU16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readI32LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readU32LE(data: ByteArray, offset: Int): Long {
        return readI32LE(data, offset).toLong() and 0xFFFFFFFFL
    }

    private fun crc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value and 0xFFFFFFFFL
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
