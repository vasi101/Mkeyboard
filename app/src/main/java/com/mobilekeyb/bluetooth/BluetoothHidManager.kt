package com.mobilekeyb.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

data class HidState(
    val supported: Boolean = Build.VERSION.SDK_INT >= 28,
    val registered: Boolean = false,
    val connectedDevice: BluetoothDevice? = null,
    val message: String = "Starting Bluetooth HID…"
)

@SuppressLint("MissingPermission")
class BluetoothHidManager(context: Context) : BluetoothProfile.ServiceListener {
    private val appContext = context.applicationContext
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val executor = Executor { it.run() }
    private var hid: BluetoothHidDevice? = null
    private val mutableState = MutableStateFlow(HidState(supported = adapter != null))
    val state = mutableState.asStateFlow()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            mutableState.value = mutableState.value.copy(
                registered = registered,
                message = if (registered) "Ready — choose a paired computer" else "HID registration failed"
            )
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, newState: Int) {
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            mutableState.value = mutableState.value.copy(
                connectedDevice = if (connected) device else null,
                message = if (connected) "Connected to ${device.name ?: device.address}" else "Disconnected"
            )
        }
    }

    init {
        if (adapter == null) mutableState.value = HidState(false, message = "Bluetooth is unavailable")
        else start()
    }

    fun start() {
        val bluetooth = adapter ?: return
        if (!bluetooth.isEnabled) {
            mutableState.value = mutableState.value.copy(
                registered = false,
                message = "Bluetooth is off — enable it to continue"
            )
            return
        }
        mutableState.value = mutableState.value.copy(message = "Starting Bluetooth HID…")
        if (!bluetooth.getProfileProxy(appContext, this, BluetoothProfile.HID_DEVICE)) {
            mutableState.value = mutableState.value.copy(message = "HID Device profile is unavailable")
        }
    }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        hid = proxy as? BluetoothHidDevice
        val settings = BluetoothHidDeviceAppSdpSettings(
            "Mobile Keyb", "Bluetooth touchpad and keyboard", "MobileKeyb",
            BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.bytes
        )
        val accepted = hid?.registerApp(settings, null, null, executor, callback) == true
        if (!accepted) mutableState.value = mutableState.value.copy(message = "Could not register HID profile")
    }

    override fun onServiceDisconnected(profile: Int) {
        hid = null
        mutableState.value = mutableState.value.copy(registered = false, connectedDevice = null, message = "HID service stopped")
    }

    fun pairedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.sortedBy { it.name } ?: emptyList()
    fun connect(device: BluetoothDevice) { mutableState.value = mutableState.value.copy(message = "Connecting…"); hid?.connect(device) }
    fun disconnect() { mutableState.value.connectedDevice?.let { hid?.disconnect(it) } }

    fun mouse(buttons: Int = 0, dx: Float = 0f, dy: Float = 0f, wheel: Float = 0f, horizontal: Float = 0f) =
        send(HidDescriptor.MOUSE_REPORT_ID, Reports.mouse(buttons, dx, dy, wheel, horizontal))

    fun click(button: Int) { mouse(buttons = button); mouse() }
    fun key(key: HidKey) { send(HidDescriptor.KEYBOARD_REPORT_ID, Reports.keyboard(key.modifier, key.code)); keyUp() }
    fun shortcut(modifier: Int, code: Int) { send(HidDescriptor.KEYBOARD_REPORT_ID, Reports.keyboard(modifier, code)); keyUp() }
    private fun keyUp() { send(HidDescriptor.KEYBOARD_REPORT_ID, Reports.keyboard()) }
    private fun send(id: Int, report: ByteArray): Boolean = mutableState.value.connectedDevice?.let { hid?.sendReport(it, id, report) } == true
    fun close() { runCatching { hid?.unregisterApp() }; adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
}
