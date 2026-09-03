package com.mobilekeyb

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.text.BasicTextField
import kotlin.math.roundToInt
import com.mobilekeyb.bluetooth.*

class MainActivity : ComponentActivity() {
    private var manager: BluetoothHidManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF4355B9))) { PermissionGate { hid -> manager = hid; MobileKeybApp(hid) } } }
    }

    override fun onDestroy() { manager?.close(); super.onDestroy() }
}

@Composable
private fun PermissionGate(content: @Composable (BluetoothHidManager) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted && Build.VERSION.SDK_INT >= 31) launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
    if (granted) {
        val manager = remember { BluetoothHidManager(context.applicationContext) }
        content(manager)
    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = { if (Build.VERSION.SDK_INT >= 31) launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Allow Bluetooth access") } }
}

private enum class Page { Control, Settings }

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MobileKeybApp(hid: BluetoothHidManager) {
    var page by remember { mutableStateOf(Page.Control) }
    var sensitivity by remember { mutableFloatStateOf(1f) }
    var naturalScroll by remember { mutableStateOf(true) }
    val state by hid.state.collectAsState()
    val keyboardVisible = WindowInsets.isImeVisible
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hid.start()
    }
    val makeDiscoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    Scaffold(
        topBar = {
            ConnectionBar(
                state = state,
                hid = hid,
                requestBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                requestPairing = {
                    makeDiscoverable.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    )
                }
            )
        },
        bottomBar = {
            if (!keyboardVisible) {
                NavigationBar {
                    Page.entries.forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = { Icon(if (item == Page.Control) Icons.Default.Mouse else Icons.Default.Settings, null) },
                            label = { Text(item.name) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                Page.Control -> ControlPanel(hid, sensitivity, naturalScroll)
                Page.Settings -> SettingsPanel(sensitivity, { sensitivity = it }, naturalScroll, { naturalScroll = it })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ConnectionBar(
    state: HidState,
    hid: BluetoothHidManager,
    requestBluetooth: () -> Unit,
    requestPairing: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(title = { Column { Text("Mobile Keyb"); Text(state.message, style = MaterialTheme.typography.labelSmall) } }, actions = {
        if (state.message.startsWith("Bluetooth is off")) TextButton(onClick = requestBluetooth) { Text("Enable") }
        else if (state.connectedDevice != null) TextButton(onClick = hid::disconnect) { Text("Disconnect") }
        else if (state.registered) {
            TextButton(onClick = requestPairing) { Text("Pair new") }
            Box {
                TextButton(onClick = { menu = true }) { Text("Connect") }
                DropdownMenu(menu, { menu = false }) {
                    hid.pairedDevices().forEach { device ->
                        DropdownMenuItem(text = { Text(device.displayName()) }, onClick = { menu = false; hid.connect(device) })
                    }
                }
            }
        }
    })
}

@Suppress("DEPRECATION") private fun BluetoothDevice.displayName() = name ?: address

@Composable
private fun ControlPanel(hid: BluetoothHidManager, sensitivity: Float, naturalScroll: Boolean) {
    var keyboardActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxSize()) {
        Touchpad(hid, sensitivity, naturalScroll, Modifier.fillMaxSize()) { keyboardActive = false }
        HiddenKeyboardInput(hid, focusRequester)
        if (keyboardActive) {
            ShortcutStrip(
                hid,
                Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            )
        }
        FloatingKeyboardButton(
            modifier = Modifier.align(Alignment.BottomEnd),
            onClick = {
                keyboardActive = true
                focusRequester.requestFocus()
                keyboard?.show()
            }
        )
    }
}

@Composable
private fun Touchpad(
    hid: BluetoothHidManager,
    sensitivity: Float,
    naturalScroll: Boolean,
    modifier: Modifier = Modifier,
    onTouch: () -> Unit = {}
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    Column(modifier.fillMaxWidth().padding(12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE7E9F6), RoundedCornerShape(24.dp)).pointerInput(sensitivity, naturalScroll, touchSlop) {
            awaitEachGesture {
                val down = awaitFirstDown()
                focusManager.clearFocus()
                keyboard?.hide()
                onTouch()
                var moved = false
                var totalMovement = 0f
                var maxPointers = 1
                var lastCentroid = down.position
                var pointerCount = 1
                do {
                    val event = awaitPointerEvent()
                    val active = event.changes.filter { it.pressed }
                    maxPointers = maxOf(maxPointers, active.size)
                    if (active.isNotEmpty()) {
                        val centroid = active.map { it.position }.reduce(Offset::plus) / active.size.toFloat()
                        val delta = if (active.size == pointerCount) centroid - lastCentroid else Offset.Zero
                        if (active.size != pointerCount) pointerCount = active.size
                        totalMovement += delta.getDistance()
                        if (totalMovement > touchSlop) moved = true
                        if (active.size == 1) hid.mouse(dx = delta.x * sensitivity, dy = delta.y * sensitivity)
                        else if (active.size >= 2) hid.mouse(wheel = delta.y * (if (naturalScroll) -0.16f else 0.16f), horizontal = delta.x * 0.12f)
                        lastCentroid = centroid
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (!moved) hid.click(if (maxPointers >= 2) 2 else 1)
            }
        }, contentAlignment = Alignment.Center) { Text("TOUCHPAD", color = Color(0xFF74778A)) }
    }
}

@Composable
private fun HiddenKeyboardInput(hid: BluetoothHidManager, focusRequester: FocusRequester) {
    var input by remember { mutableStateOf(TextFieldValue()) }
    BasicTextField(
        value = input,
        onValueChange = { updated ->
            val prefix = input.text.commonPrefixWith(updated.text).length
            repeat(input.text.length - prefix) { KeyboardMapper.named["Back"]?.let(hid::key) }
            updated.text.substring(prefix).forEach { char -> KeyboardMapper.character(char)?.let(hid::key) }
            val kept = if (updated.text.length > 80) updated.text.takeLast(40) else updated.text
            input = TextFieldValue(kept, TextRange(kept.length))
        },
        modifier = Modifier.size(1.dp).focusRequester(focusRequester).graphicsLayer { alpha = 0.01f }
    )
}

@Composable
private fun FloatingKeyboardButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var x by remember { mutableFloatStateOf(-20f) }
    var y by remember { mutableFloatStateOf(-20f) }
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress { change, dragAmount ->
                    change.consume()
                    x = (x + dragAmount.x).coerceIn(-size.width * 8f, 0f)
                    y = (y + dragAmount.y).coerceIn(-size.height * 20f, 0f)
                }
            }
    ) { Icon(Icons.Default.Keyboard, contentDescription = "Open keyboard") }
}

@Composable
private fun ShortcutStrip(hid: BluetoothHidManager, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 76.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Shortcut("Win+Tab") { hid.shortcut(KeyboardMapper.META, 0x2B) }
        Shortcut("Alt+Tab") { hid.shortcut(KeyboardMapper.ALT, 0x2B) }
        Shortcut("Win+D") { hid.shortcut(KeyboardMapper.META, 0x07) }
        Shortcut("Win+E") { hid.shortcut(KeyboardMapper.META, 0x08) }
        Shortcut("Ctrl+C") { hid.shortcut(KeyboardMapper.CTRL, 0x06) }
        Shortcut("Ctrl+V") { hid.shortcut(KeyboardMapper.CTRL, 0x19) }
        Shortcut("Ctrl+Z") { hid.shortcut(KeyboardMapper.CTRL, 0x1D) }
        Shortcut("Ctrl+A") { hid.shortcut(KeyboardMapper.CTRL, 0x04) }
    }
}

@Composable private fun KeyButton(text: String, action: () -> Unit) { OutlinedButton(onClick = action, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(2.dp).defaultMinSize(minWidth = 34.dp, minHeight = 48.dp)) { Text(text, style = MaterialTheme.typography.labelMedium) } }
@Composable private fun Shortcut(text: String, action: () -> Unit) { AssistChip(onClick = action, label = { Text(text) }) }

@Composable
private fun SettingsPanel(sensitivity: Float, onSensitivity: (Float) -> Unit, natural: Boolean, onNatural: (Boolean) -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text("Pointer sensitivity: ${"%.1f".format(sensitivity)}×", style = MaterialTheme.typography.titleMedium)
        Slider(sensitivity, onSensitivity, valueRange = 0.3f..2.5f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Natural scrolling", Modifier.weight(1f)); Switch(natural, onNatural) }
        Spacer(Modifier.height(24.dp)); Text("Pair the phone in the computer's Bluetooth settings first, then use Connect at the top.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
