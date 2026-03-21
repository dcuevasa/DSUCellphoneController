package com.example.a3dsinputredirection

import android.content.SharedPreferences
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.a3dsinputredirection.databinding.ActivityMainBinding
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity(), TouchPadView.Listener, JoystickView.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var client: InputRedirectionClient

    private var config: InputRedirectionConfig = InputRedirectionConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("input_redirection", MODE_PRIVATE)
        config = InputRedirectionConfig.load(prefs)
        client = InputRedirectionClient { config.copy() }

        setupTabs()
        binding.touchPad.listener = this
        binding.leftStick.listener = this
        binding.rightStick.listener = this

        populateFormFromConfig()
        bindUiEvents()
        updateStatus()
    }

    override fun onStart() {
        super.onStart()
        if (binding.sendSwitch.isChecked) {
            client.start()
            updateStatus()
        }
    }

    override fun onStop() {
        super.onStop()
        client.stop()
        updateStatus()
    }

    override fun onTouchState(active: Boolean, x: Int, y: Int) {
        client.updateTouch(active, x, y)
    }

    override fun onStickMoved(view: JoystickView, active: Boolean, x: Float, y: Float) {
        if (view.id == binding.leftStick.id) {
            client.updateUiLeftStick(active, x, y)
        } else if (view.id == binding.rightStick.id) {
            client.updateUiRightStick(active, x, y)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isGamepadSource(event) && client.onKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            ev.action == MotionEvent.ACTION_MOVE &&
            client.onMotionEvent(ev)
        ) {
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun populateFormFromConfig() {
        binding.ipInput.setText(config.targetIp)
        binding.cpadBoundInput.setText(config.cpadBound.toString())
        binding.cppBoundInput.setText(config.cppBound.toString())

        binding.checkInvertY.isChecked = config.invertY
        binding.checkInvertCppY.isChecked = config.invertCppY
        binding.checkSwapSticks.isChecked = config.swapSticks
        binding.checkDisableC.isChecked = config.disableCStick
        binding.checkRsDpad.isChecked = config.rightStickAsDPad
        binding.checkRsAbxy.isChecked = config.rightStickAsAbxy
        binding.checkRsSmash.isChecked = config.rightStickAsSmash

        val wasRunning = prefs.getBoolean("sendEnabled", true)
        binding.sendSwitch.isChecked = wasRunning

        setupSpinner(binding.mapA, config.mapA) { config.mapA = it }
        setupSpinner(binding.mapB, config.mapB) { config.mapB = it }
        setupSpinner(binding.mapX, config.mapX) { config.mapX = it }
        setupSpinner(binding.mapY, config.mapY) { config.mapY = it }
        setupSpinner(binding.mapSelect, config.mapSelect) { config.mapSelect = it }
        setupSpinner(binding.mapStart, config.mapStart) { config.mapStart = it }
        setupSpinner(binding.mapRight, config.mapDpadRight) { config.mapDpadRight = it }
        setupSpinner(binding.mapLeft, config.mapDpadLeft) { config.mapDpadLeft = it }
        setupSpinner(binding.mapUp, config.mapDpadUp) { config.mapDpadUp = it }
        setupSpinner(binding.mapDown, config.mapDpadDown) { config.mapDpadDown = it }
        setupSpinner(binding.mapR, config.mapR) { config.mapR = it }
        setupSpinner(binding.mapL, config.mapL) { config.mapL = it }
        setupSpinner(binding.mapZr, config.mapZr) { config.mapZr = it }
        setupSpinner(binding.mapZl, config.mapZl) { config.mapZl = it }
        setupSpinner(binding.mapHome, config.mapHome) { config.mapHome = it }
        setupSpinner(binding.mapPower, config.mapPower) { config.mapPower = it }
        setupSpinner(binding.mapPowerLong, config.mapPowerLong) { config.mapPowerLong = it }
    }

    private fun bindUiEvents() {
        binding.ipInput.doAfterTextChanged {
            config.targetIp = it?.toString()?.trim().orEmpty()
            persistConfig()
            updateStatus()
        }

        binding.cpadBoundInput.doAfterTextChanged {
            config.cpadBound = it?.toString()?.toIntOrNull()?.coerceIn(1, 4095) ?: 1488
            persistConfig()
        }

        binding.cppBoundInput.doAfterTextChanged {
            config.cppBound = it?.toString()?.toIntOrNull()?.coerceIn(1, 255) ?: 127
            persistConfig()
        }

        bindCheckbox(binding.checkInvertY) {
            config.invertY = it
        }
        bindCheckbox(binding.checkInvertCppY) {
            config.invertCppY = it
        }
        bindCheckbox(binding.checkSwapSticks) {
            config.swapSticks = it
        }
        bindCheckbox(binding.checkDisableC) {
            config.disableCStick = it
        }
        bindCheckbox(binding.checkRsDpad) {
            config.rightStickAsDPad = it
        }
        bindCheckbox(binding.checkRsAbxy) {
            config.rightStickAsAbxy = it
        }
        bindCheckbox(binding.checkRsSmash) {
            config.rightStickAsSmash = it
        }

        bindSpecialButton(binding.btnHome) { pressed -> client.pressHome(pressed) }
        bindSpecialButton(binding.btnPower) { pressed -> client.pressPower(pressed) }
        bindSpecialButton(binding.btnPowerLong) { pressed -> client.pressPowerLong(pressed) }

        bindGamepadButton(binding.btnL, KeyEvent.KEYCODE_BUTTON_L1)
        bindGamepadButton(binding.btnR, KeyEvent.KEYCODE_BUTTON_R1)

        bindGamepadButton(binding.btnDpadUp, KeyEvent.KEYCODE_DPAD_UP)
        bindGamepadButton(binding.btnDpadDown, KeyEvent.KEYCODE_DPAD_DOWN)
        bindGamepadButton(binding.btnDpadLeft, KeyEvent.KEYCODE_DPAD_LEFT)
        bindGamepadButton(binding.btnDpadRight, KeyEvent.KEYCODE_DPAD_RIGHT)

        bindGamepadButton(binding.btnFaceTop, KeyEvent.KEYCODE_BUTTON_Y)
        bindGamepadButton(binding.btnFaceLeft, KeyEvent.KEYCODE_BUTTON_X)
        bindGamepadButton(binding.btnFaceRight, KeyEvent.KEYCODE_BUTTON_A)
        bindGamepadButton(binding.btnFaceBottom, KeyEvent.KEYCODE_BUTTON_B)

        bindGamepadButton(binding.btnSelect, KeyEvent.KEYCODE_BUTTON_SELECT)
        bindGamepadButton(binding.btnStart, KeyEvent.KEYCODE_BUTTON_START)

        binding.sendSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sendEnabled", checked).apply()
            if (checked) {
                client.start()
            } else {
                client.stop()
            }
            updateStatus()
        }
    }

    private fun bindCheckbox(switch: MaterialSwitch, onUpdate: (Boolean) -> Unit) {
        switch.setOnCheckedChangeListener { _, checked ->
            onUpdate(checked)
            persistConfig()
        }
    }

    private fun bindSpecialButton(button: Button, callback: (Boolean) -> Unit) {
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    callback(true)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    callback(false)
                    true
                }

                else -> false
            }
        }
    }

    private fun bindGamepadButton(button: Button, keyCode: Int) {
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    client.setUiKeyPressed(keyCode, true)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    client.setUiKeyPressed(keyCode, false)
                    true
                }

                else -> false
            }
        }
    }

    private fun setupTabs() {
        binding.controlTab.visibility = View.VISIBLE
        binding.optionsTab.visibility = View.GONE
        binding.btnTabControl.isChecked = true
        binding.btnTabOptions.isChecked = false

        binding.btnTabControl.setOnClickListener {
            binding.controlTab.visibility = View.VISIBLE
            binding.optionsTab.visibility = View.GONE
            binding.btnTabControl.isChecked = true
            binding.btnTabOptions.isChecked = false
        }

        binding.btnTabOptions.setOnClickListener {
            binding.controlTab.visibility = View.GONE
            binding.optionsTab.visibility = View.VISIBLE
            binding.btnTabControl.isChecked = false
            binding.btnTabOptions.isChecked = true
        }
    }

    private fun setupSpinner(spinner: Spinner, selectedKeyCode: Int, onSelected: (Int) -> Unit) {
        val labels = GamepadButtonOption.entries.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val index = GamepadButtonOption.entries.indexOfFirst { it.keyCode == selectedKeyCode }
            .takeIf { it >= 0 } ?: 0
        spinner.setSelection(index, false)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val mappedKeyCode = GamepadButtonOption.entries[position].keyCode
                onSelected(mappedKeyCode)
                persistConfig()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun persistConfig() {
        config.save(prefs)
    }

    private fun updateStatus() {
        val ip = config.targetIp.ifBlank { "(no IP)" }
        val status = if (client.isRunning()) "Sending" else "Paused"
        binding.statusText.text = "$status -> $ip:4950"
    }

    private fun isGamepadSource(event: KeyEvent): Boolean {
        val source = event.source
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }
}