package com.mobilekeyb

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.BasicTextField
import kotlin.math.roundToInt
import com.mobilekeyb.bluetooth.*

class MainActivity : ComponentActivity() {
    private var manager: BluetoothHidManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("appearance", MODE_PRIVATE) }
            var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
            var wallpaper by remember { mutableStateOf(prefs.getString("wallpaper", null)?.let(Uri::parse)) }
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme(primary = Color(0xFFBBC3FF)) else lightColorScheme(primary = Color(0xFF4355B9))) {
                PermissionGate { hid ->
                    manager = hid
                    MobileKeybApp(
                        hid, darkTheme,
                        { darkTheme = it; prefs.edit().putBoolean("dark_theme", it).apply() },
                        wallpaper,
                        { wallpaper = it; prefs.edit().putString("wallpaper", it?.toString()).apply() }
                    )
                }
            }
        }
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

@Composable
private fun MobileKeybApp(hid: BluetoothHidManager, darkTheme: Boolean, onDarkTheme: (Boolean) -> Unit, wallpaperUri: Uri?, onWallpaper: (Uri?) -> Unit) {
    val context = LocalContext.current
    var settingsOpen by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(1f) }
    var naturalScroll by remember { mutableStateOf(true) }
    val state by hid.state.collectAsState()
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hid.start()
    }
    val makeDiscoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onWallpaper(it)
        }
    }
    val wallpaperBitmap = remember(wallpaperUri) {
        wallpaperUri?.let { uri -> runCatching { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.fillMaxSize()) {
        wallpaperBitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    Scaffold(
        containerColor = if (wallpaperBitmap != null) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            ConnectionBar(
                state = state,
                hid = hid,
                settingsOpen = settingsOpen,
                onSettings = { settingsOpen = !settingsOpen },
                requestBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                requestPairing = {
                    makeDiscoverable.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    )
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (settingsOpen) SettingsPanel(sensitivity, { sensitivity = it }, naturalScroll, { naturalScroll = it }, darkTheme, onDarkTheme, wallpaperUri != null, { wallpaperPicker.launch(arrayOf("image/*")) }, { onWallpaper(null) })
            else ControlPanel(hid, sensitivity, naturalScroll, wallpaperBitmap)
        }
    }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ConnectionBar(
    state: HidState,
    hid: BluetoothHidManager,
    settingsOpen: Boolean,
    onSettings: () -> Unit,
    requestBluetooth: () -> Unit,
    requestPairing: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onSettings) {
                Icon(if (settingsOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Settings, if (settingsOpen) "Back" else "Settings")
            }
        },
        title = { Column { Text(if (settingsOpen) "Settings" else "Mobile Keyb"); Text(state.message, style = MaterialTheme.typography.labelSmall) } },
        actions = {
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
        }
    )
}

@Suppress("DEPRECATION") private fun BluetoothDevice.displayName() = name ?: address

@Composable
private fun ControlPanel(hid: BluetoothHidManager, sensitivity: Float, naturalScroll: Boolean, wallpaper: ImageBitmap?) {
    var keyboardActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val buttonSizePx = with(density) { 56.dp.toPx() }
        val maxButtonX = with(density) { maxWidth.toPx() } - buttonSizePx
        val maxButtonY = with(density) { maxHeight.toPx() } - buttonSizePx
        Touchpad(hid, sensitivity, naturalScroll, Modifier.fillMaxSize(), wallpaper) { keyboardActive = false }
        HiddenKeyboardInput(hid, focusRequester)
        if (keyboardActive) {
            ShortcutStrip(
                hid,
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
            )
        }
        FloatingKeyboardButton(
            modifier = Modifier.align(Alignment.TopStart),
            maxX = maxButtonX.coerceAtLeast(0f),
            maxY = maxButtonY.coerceAtLeast(0f),
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
    wallpaper: ImageBitmap? = null,
    onTouch: () -> Unit = {}
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    Column(modifier.fillMaxWidth().padding(12.dp)) {
        val glassShape = RoundedCornerShape(24.dp)
        Box(Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(glassShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), glassShape)
            .pointerInput(sensitivity, naturalScroll, touchSlop) {
            var lastTapTime = 0L
            var lastTapPosition = Offset.Unspecified
            awaitEachGesture {
                val down = awaitFirstDown()
                focusManager.clearFocus()
                keyboard?.hide()
                onTouch()
                var dragArmed = lastTapPosition != Offset.Unspecified &&
                    down.uptimeMillis - lastTapTime <= 350L &&
                    (down.position - lastTapPosition).getDistance() <= touchSlop * 4f
                if (dragArmed) hid.mouse(buttons = 1)
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
                        if (active.size > 1 && dragArmed) {
                            hid.mouse()
                            dragArmed = false
                        }
                        totalMovement += delta.getDistance()
                        if (totalMovement > touchSlop) moved = true
                        if (active.size == 1) hid.mouse(buttons = if (dragArmed) 1 else 0, dx = delta.x * sensitivity, dy = delta.y * sensitivity)
                        else if (active.size >= 2) hid.mouse(wheel = delta.y * (if (naturalScroll) -0.16f else 0.16f), horizontal = delta.x * 0.12f)
                        lastCentroid = centroid
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (dragArmed) hid.mouse()
                when {
                    maxPointers >= 2 && !moved -> {
                        hid.click(2)
                        lastTapTime = 0L
                        lastTapPosition = Offset.Unspecified
                    }
                    dragArmed -> {
                        lastTapTime = 0L
                        lastTapPosition = Offset.Unspecified
                    }
                    !moved -> {
                        hid.click(1)
                        lastTapTime = down.uptimeMillis
                        lastTapPosition = down.position
                    }
                    else -> {
                        lastTapTime = 0L
                        lastTapPosition = Offset.Unspecified
                    }
                }
            }
        }, contentAlignment = Alignment.Center) {
            wallpaper?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(20.dp)
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (wallpaper == null) 0.82f else 0.30f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), glassShape)
            )
            Text("TOUCHPAD", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
        }
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
private fun FloatingKeyboardButton(
    modifier: Modifier = Modifier,
    maxX: Float,
    maxY: Float,
    onClick: () -> Unit
) {
    var x by remember { mutableFloatStateOf(maxX - 20f) }
    var y by remember { mutableFloatStateOf(maxY - 20f) }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    LaunchedEffect(maxX, maxY) {
        x = x.coerceIn(0f, maxX)
        y = y.coerceIn(0f, maxY)
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(56.dp)
            .pointerInput(maxX, maxY) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastPosition = down.position
                    var travel = 0f
                    var dragging = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.position - lastPosition
                        lastPosition = change.position
                        travel += delta.getDistance()
                        if (travel > touchSlop) dragging = true
                        if (dragging) {
                            x = (x + delta.x).coerceIn(0f, maxX)
                            y = (y + delta.y).coerceIn(0f, maxY)
                            change.consume()
                        }
                    } while (change.pressed)
                    if (!dragging) onClick()
                }
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Keyboard, contentDescription = "Open keyboard")
        }
    }
}

@Composable
private fun ShortcutStrip(hid: BluetoothHidManager, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
}

@Composable private fun KeyButton(text: String, action: () -> Unit) { OutlinedButton(onClick = action, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(2.dp).defaultMinSize(minWidth = 34.dp, minHeight = 48.dp)) { Text(text, style = MaterialTheme.typography.labelMedium) } }
@Composable private fun Shortcut(text: String, action: () -> Unit) { AssistChip(onClick = action, label = { Text(text) }) }

@Composable
private fun SettingsPanel(
    sensitivity: Float, onSensitivity: (Float) -> Unit,
    natural: Boolean, onNatural: (Boolean) -> Unit,
    darkTheme: Boolean, onDarkTheme: (Boolean) -> Unit,
    hasWallpaper: Boolean, chooseWallpaper: () -> Unit, clearWallpaper: () -> Unit
) {
    Column(Modifier.padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Dark theme", Modifier.weight(1f))
            Switch(darkTheme, onDarkTheme)
        }
        Spacer(Modifier.height(16.dp))
        Text("Custom wallpaper", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = chooseWallpaper) { Text(if (hasWallpaper) "Change wallpaper" else "Choose wallpaper") }
            if (hasWallpaper) OutlinedButton(onClick = clearWallpaper) { Text("Clear") }
        }
        Spacer(Modifier.height(24.dp))
        Text("Pointer sensitivity: ${"%.1f".format(sensitivity)}×", style = MaterialTheme.typography.titleMedium)
        Slider(sensitivity, onSensitivity, valueRange = 0.3f..2.5f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Natural scrolling", Modifier.weight(1f)); Switch(natural, onNatural) }
        Spacer(Modifier.height(24.dp)); Text("Pair the phone in the computer's Bluetooth settings first, then use Connect at the top.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
