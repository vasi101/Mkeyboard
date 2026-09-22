package com.mobilekeyb.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { handler.post(it) }
    private val prefs = appContext.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private var candidateIndex = 0
    private var closed = false
    private var requestingProxy = false
    private var registering = false
    private var heldModifiers = 0
    private val retry = Runnable { maintainConnection() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_ON -> start()
                BluetoothAdapter.STATE_OFF -> {
                    heldModifiers = 0
                    registering = false
                    mutableState.value = mutableState.value.copy(registered = false, connectedDevice = null, message = "Bluetooth is off - enable it to continue")
                }
            }
        }
    }
    private fun scheduleRetry() {
        handler.removeCallbacks(retry)
        if (!closed) handler.postDelayed(retry, 10_000)
    }
    private fun maintainConnection() {
        if (closed) return
        runCatching {
            if (adapter?.isEnabled == true) {
                when {
                    hid == null -> start()
                    !mutableState.value.registered -> register()
                    mutableState.value.connectedDevice == null -> {
                        autoConnectPairedDevice()
                    }
                }
            }
        }
        scheduleRetry()
    }
    private fun autoConnectPairedDevice() {
        if (!prefs.getBoolean("auto_connect", true)) return
        val profile = hid ?: return
        // Do not start another attempt while the Bluetooth stack is still connecting.
        if (profile.getDevicesMatchingConnectionStates(intArrayOf(
                BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_CONNECTED)).isNotEmpty()) return
        val devices = pairedDevices()
        val address = prefs.getString("host", null)
        val preferred = devices.firstOrNull { it.address == address }
        val computers = devices.filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER }
        val target = preferred ?: computers.takeIf { it.isNotEmpty() }?.let {
            it[(candidateIndex++ % it.size)]
        }
        if (target == null) {
            mutableState.value = mutableState.value.copy(message = "Pair a computer or choose Connect")
            return
        }
        mutableState.value = mutableState.value.copy(message = "Auto-connecting to ${target.name ?: target.address}")
        profile.connect(target)
    }
    private var hid: BluetoothHidDevice? = null
    private val mutableState = MutableStateFlow(HidState(supported = adapter != null))
    val state = mutableState.asStateFlow()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            if (closed) return
            registering = false
            if (!registered) heldModifiers = 0
            mutableState.value = mutableState.value.copy(
                connectedDevice = if (registered) mutableState.value.connectedDevice else null,
                registered = registered,
                message = if (registered) "Ready — choose a paired computer" else "HID registration lost - retrying"
            )
            if (registered) {
                handler.removeCallbacks(retry)
                handler.post(retry)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, newState: Int) {
            if (closed) return
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            if (!connected) heldModifiers = 0
            if (connected && !prefs.getBoolean("auto_connect", true)) {
                hid?.disconnect(device)
                return
            }
            if (connected) prefs.edit().putString("host", device.address).apply()
            mutableState.value = mutableState.value.copy(
                connectedDevice = if (connected) device else mutableState.value.connectedDevice?.takeUnless { it.address == device.address },
                message = if (connected) "Connected to ${device.name ?: device.address}" else "Disconnected"
            )
        }
    }

    init {
        if (adapter == null) mutableState.value = HidState(false, message = "Bluetooth is unavailable")
        else {
            appContext.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            start()
            scheduleRetry()
        }
    }

    fun start() {
        if (closed) return
        val bluetooth = adapter ?: return
        if (!bluetooth.isEnabled) {
            mutableState.value = mutableState.value.copy(
                registered = false,
                message = "Bluetooth is off — enable it to continue"
            )
            return
        }
        mutableState.value = mutableState.value.copy(message = "Starting Bluetooth HID…")
        if (hid != null) { if (!mutableState.value.registered) register(); return }
        if (requestingProxy) return
        requestingProxy = true
        if (!bluetooth.getProfileProxy(appContext, this, BluetoothProfile.HID_DEVICE)) {
            requestingProxy = false
            mutableState.value = mutableState.value.copy(message = "HID Device profile is unavailable")
        }
    }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        requestingProxy = false
        if (closed) { adapter?.closeProfileProxy(profile, proxy); return }
        hid = proxy as? BluetoothHidDevice
        register()
    }

    private fun register() {
        if (closed || registering || mutableState.value.registered) return
        registering = true
        val settings = BluetoothHidDeviceAppSdpSettings(
            "Mobile Keyb", "Bluetooth touchpad and keyboard", "MobileKeyb",
            BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.bytes
        )
        val accepted = hid?.registerApp(settings, null, null, executor, callback) == true
        if (!accepted) registering = false
        if (!accepted) mutableState.value = mutableState.value.copy(message = "HID unavailable. Stop other keyboard apps (including release/debug), then retry.")
    }

    override fun onServiceDisconnected(profile: Int) {
        heldModifiers = 0
        requestingProxy = false
        registering = false
        hid = null
        mutableState.value = mutableState.value.copy(registered = false, connectedDevice = null, message = "HID service stopped")
    }

    fun pairedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.sortedBy { it.name } ?: emptyList()
    fun connect(device: BluetoothDevice) {
        prefs.edit().putString("host", device.address).putBoolean("auto_connect", true).apply()
        mutableState.value.connectedDevice?.takeIf { it.address != device.address }?.let { hid?.disconnect(it) }
        mutableState.value = mutableState.value.copy(message = "Connecting...")
        if (mutableState.value.registered) hid?.connect(device) else start()
        scheduleRetry()
    }
    fun disconnect() {
        heldModifiers = 0
        keyUp()
        val address = prefs.getString("host", null)
        prefs.edit().putBoolean("auto_connect", false).apply()
        val device = mutableState.value.connectedDevice ?: pairedDevices().firstOrNull { it.address == address }
        device?.let { hid?.disconnect(it) }
        hid?.getDevicesMatchingConnectionStates(intArrayOf(BluetoothProfile.STATE_CONNECTING))?.forEach { hid?.disconnect(it) }
    }

    fun mouse(buttons: Int = 0, dx: Float = 0f, dy: Float = 0f, wheel: Float = 0f, horizontal: Float = 0f) =
        send(HidDescriptor.MOUSE_REPORT_ID, Reports.mouse(buttons, dx, dy, wheel, horizontal))

    fun click(button: Int) { mouse(buttons = button); mouse() }
    fun holdModifier(modifier: Int) {
        heldModifiers = heldModifiers or modifier
        keyUp()
    }
    fun releaseModifier(modifier: Int) {
        if (heldModifiers and modifier == 0) return
        heldModifiers = heldModifiers and modifier.inv()
        keyUp()
    }
    fun key(key: HidKey) { shortcut(key.modifier, key.code) }
    fun shortcut(modifier: Int, code: Int) { send(HidDescriptor.KEYBOARD_REPORT_ID, Reports.keyboard(heldModifiers or modifier, code)); keyUp() }
    private fun keyUp() { send(HidDescriptor.KEYBOARD_REPORT_ID, Reports.keyboard(heldModifiers)) }
    private fun send(id: Int, report: ByteArray): Boolean = mutableState.value.connectedDevice?.let { hid?.sendReport(it, id, report) } == true
    fun close() {
        heldModifiers = 0
        keyUp()
        closed = true
        handler.removeCallbacksAndMessages(null)
        runCatching { appContext.unregisterReceiver(receiver) }
        runCatching { hid?.unregisterApp() }
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
    }
}
