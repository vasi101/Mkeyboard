package com.mobilekeyb

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.res.painterResource
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("appearance", MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", systemDark)) }
            var wallpaper by remember { mutableStateOf(prefs.getString("wallpaper", null)?.let(Uri::parse)) }
            var setupComplete by remember { mutableStateOf(prefs.getBoolean("setup_complete", false)) }
            val scheme = if (darkTheme) darkColorScheme(
                primary = Color(0xFF59D6FF), background = Color(0xFF090B14),
                surface = Color(0xFF111522), surfaceVariant = Color(0xFF1A2030)
            ) else lightColorScheme(
                primary = Color(0xFF006B88), background = Color(0xFFF4F6FA),
                surface = Color.White, surfaceVariant = Color(0xFFE9EEF5)
            )
            MaterialTheme(colorScheme = scheme) {
                if (!setupComplete) {
                    FirstRunSetup {
                        prefs.edit().putBoolean("setup_complete", true).apply()
                        setupComplete = true
                    }
                } else {
                    PermissionGate { hid ->
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
    }

}

@Composable
private fun FirstRunSetup(onComplete: () -> Unit) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var page by remember { mutableIntStateOf(0) }
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        if (it) page = 2
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (adapter?.isEnabled == true) page = 3
    }
    val dark = isSystemInDarkTheme()
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF10152A), Color(0xFF080A12))
                else listOf(Color(0xFFEAF8FF), Color(0xFFF7F7FB), Color.White)
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mobile Keyb", style = MaterialTheme.typography.titleMedium)
                Text("${page + 1} of 4", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = { (page + 1) / 4f },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(5.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
            )
            Spacer(Modifier.weight(.7f))
            Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f), modifier = Modifier.size(112.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (page) { 0 -> Icons.Default.Keyboard; 1 -> Icons.Default.Security; 2 -> Icons.Default.Bluetooth; else -> Icons.Default.CheckCircle },
                        null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                when (page) { 0 -> "Your computer, at your fingertips"; 1 -> "Allow nearby device access"; 2 -> "Turn on Bluetooth"; else -> "You're ready to connect" },
                style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                when (page) {
                    0 -> "Use your phone as a smooth wireless touchpad and keyboard. No software is needed on your computer."
                    1 -> "Mobile Keyb uses Bluetooth only to appear as a mouse and keyboard and connect to computers you choose."
                    2 -> "Bluetooth must be enabled to register the keyboard and touchpad profile."
                    else -> "Pair this phone from your computer's Bluetooth settings, then choose the computer inside Mobile Keyb."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            if (page == 1) SetupPrivacyCard()
            Button(
                onClick = {
                    when (page) {
                        0 -> page = if (permissionGranted) 2 else 1
                        1 -> if (Build.VERSION.SDK_INT >= 31) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) else { permissionGranted = true; page = 2 }
                        2 -> if (adapter?.isEnabled == true) page = 3 else bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        else -> onComplete()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)
            ) {
                Text(when (page) { 0 -> "Get started"; 1 -> "Allow Bluetooth access"; 2 -> "Enable Bluetooth"; else -> "Open control pad" })
                Spacer(Modifier.width(10.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetupPrivacyCard() {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .75f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column { Text("Private by design", style = MaterialTheme.typography.titleSmall); Text("No location data is collected or shared.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PermissionGate(content: @Composable (BluetoothHidManager) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) {
        val manager by HidConnectionService.manager.collectAsState()
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            val connectionPrefs = context.getSharedPreferences("connection", android.content.Context.MODE_PRIVATE)
            if (Build.VERSION.SDK_INT >= 33 && !connectionPrefs.getBoolean("notification_requested", false)) {
                connectionPrefs.edit().putBoolean("notification_requested", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        DisposableEffect(context) {
            context.startForegroundService(Intent(context, HidConnectionService::class.java))
            onDispose { }
        }
        manager?.let { content(it) } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { context.startForegroundService(Intent(context, HidConnectionService::class.java)) }) {
                Text("Start Bluetooth keyboard")
            }
        }
    } else Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(16.dp)); Text("Bluetooth access needed", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp)); Text("Permission was removed. Allow it again to connect to your computer.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(22.dp)); Button(onClick = { if (Build.VERSION.SDK_INT >= 31) launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }, modifier = Modifier.fillMaxWidth()) { Text("Allow access") }
            }
        }
    }
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
    val launchPairing = {
        hid.start()
        makeDiscoverable.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        )
    }
    val pairingPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchPairing()
    }
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
                    if (Build.VERSION.SDK_INT >= 31 &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                        pairingPermission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                    } else launchPairing()
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)),
        navigationIcon = {
            IconButton(onClick = onSettings) {
                Icon(if (settingsOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Settings, if (settingsOpen) "Back" else "Settings")
            }
        },
        title = { Column {
            Text(if (settingsOpen) "Settings" else "Mobile Keyb", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.connectedDevice != null) Color(0xFF32C774) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)))
                Spacer(Modifier.width(6.dp)); Text(state.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } },
        actions = {
        if (state.message.startsWith("Bluetooth is off")) TextButton(onClick = requestBluetooth) { Text("Enable") }
        else if (state.connectedDevice != null) TextButton(onClick = hid::disconnect) { Text("Disconnect") }
        else {
            TextButton(onClick = requestPairing) { Text("Pair new") }
            if (!state.registered) {
                TextButton(onClick = hid::start) { Text("Retry") }
            } else {
            Box {
                TextButton(onClick = { menu = true }) { Text("Connect") }
                DropdownMenu(menu, { menu = false }) {
                    if (hid.pairedDevices().isEmpty()) {
                        DropdownMenuItem(text = { Text("Pair a new computer") }, onClick = { menu = false; requestPairing() })
                    }
                    hid.pairedDevices().forEach { device ->
                        DropdownMenuItem(text = { Text(device.displayName()) }, onClick = { menu = false; hid.connect(device) })
                    }
                }
            }
            }
        }
        }
    )
}

@Suppress("DEPRECATION") private fun BluetoothDevice.displayName() = try { name ?: address } catch (_: SecurityException) { address }

@Composable
private fun ControlPanel(hid: BluetoothHidManager, sensitivity: Float, naturalScroll: Boolean, wallpaper: ImageBitmap?) {
    val prefs = LocalContext.current.getSharedPreferences("keyboard", android.content.Context.MODE_PRIVATE)
    var keyboardLocked by remember { mutableStateOf(prefs.getBoolean("locked", false)) }
    var keyboardActive by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(keyboardLocked) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(keyboardActive) {
        if (keyboardActive) { focusRequester.requestFocus(); keyboard?.show() }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val buttonSizePx = with(density) { 56.dp.toPx() }
        val maxButtonX = with(density) { maxWidth.toPx() } - buttonSizePx
        val maxButtonY = with(density) { maxHeight.toPx() } - buttonSizePx
        Touchpad(hid, sensitivity, naturalScroll, Modifier.fillMaxSize(), wallpaper) {
            if (!keyboardLocked) { keyboardActive = false; focusManager.clearFocus(); keyboard?.hide() }
        }
        HiddenKeyboardInput(hid, focusRequester)
        if (keyboardActive) {
            ShortcutStrip(
                hid, keyboardLocked,
                { keyboardLocked = !keyboardLocked; prefs.edit().putBoolean("locked", keyboardLocked).apply() },
                { keyboardActive = false; keyboardLocked = false; prefs.edit().putBoolean("locked", false).apply(); focusManager.clearFocus(); keyboard?.hide() },
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
    val currentOnTouch by rememberUpdatedState(onTouch)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        val glassShape = RoundedCornerShape(32.dp)
        Box(Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(glassShape)
            .border(1.dp, Color.White.copy(alpha = 0.30f), glassShape)
            .pointerInput(sensitivity, naturalScroll, touchSlop) {
            var lastTapTime = 0L
            var lastTapPosition = Offset.Unspecified
            var remainderX = 0f
            var remainderY = 0f
            var remainderWheel = 0f
            var remainderHorizontal = 0f
            awaitEachGesture {
                val down = awaitFirstDown()
                currentOnTouch()
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
                        if (active.size == 1) {
                            val acceleration = 1f + (delta.getDistance() / 32f).coerceIn(0f, 1f) * 0.55f
                            remainderX += delta.x * sensitivity * acceleration
                            remainderY += delta.y * sensitivity * acceleration
                            val reportX = remainderX.toInt().coerceIn(-127, 127)
                            val reportY = remainderY.toInt().coerceIn(-127, 127)
                            if (reportX != 0 || reportY != 0) {
                                remainderX -= reportX
                                remainderY -= reportY
                                hid.mouse(buttons = if (dragArmed) 1 else 0, dx = reportX.toFloat(), dy = reportY.toFloat())
                            }
                        } else if (active.size >= 2) {
                            remainderWheel += delta.y * (if (naturalScroll) -0.16f else 0.16f)
                            remainderHorizontal += delta.x * 0.12f
                            val reportWheel = remainderWheel.toInt().coerceIn(-127, 127)
                            val reportHorizontal = remainderHorizontal.toInt().coerceIn(-127, 127)
                            if (reportWheel != 0 || reportHorizontal != 0) {
                                remainderWheel -= reportWheel
                                remainderHorizontal -= reportHorizontal
                                hid.mouse(wheel = reportWheel.toFloat(), horizontal = reportHorizontal.toFloat())
                            }
                        }
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
                    .background(
                        if (wallpaper == null) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)))
                        else Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), MaterialTheme.colorScheme.surface.copy(alpha = .34f)))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), glassShape)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.size(54.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp).graphicsLayer { rotationZ = -45f }) }
                }
                Spacer(Modifier.height(14.dp))
                Text("Touchpad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                Text("Tap to click • Two fingers to scroll", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f))
            }
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
    val currentOnClick by rememberUpdatedState(onClick)
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
                    if (!dragging) currentOnClick()
                }
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Keyboard, contentDescription = "Open keyboard")
        }
    }
}

@Composable
private fun ShortcutStrip(hid: BluetoothHidManager, locked: Boolean, onLock: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard", android.content.Context.MODE_PRIVATE) }
    var shortcuts by remember { mutableStateOf(CustomShortcut.load(prefs)) }
    var editing by remember { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconToggleButton(checked = locked, onCheckedChange = { onLock() }) {
                    Icon(
                        imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (locked) "Unlock keyboard" else "Lock keyboard"
                    )
                }
                Shortcut("Hide", onHide)
                KeyboardMapper.named.forEach { (label, key) -> Shortcut(label) { hid.key(key) } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindowsKey(hid)
                Shortcut("Win+Tab") { hid.shortcut(KeyboardMapper.META, 0x2B) }
                Shortcut("Alt+Tab") { hid.shortcut(KeyboardMapper.ALT, 0x2B) }
                Shortcut("Win+D") { hid.shortcut(KeyboardMapper.META, 0x07) }
                Shortcut("Win+E") { hid.shortcut(KeyboardMapper.META, 0x08) }
                Shortcut("Ctrl+C") { hid.shortcut(KeyboardMapper.CTRL, 0x06) }
                Shortcut("Ctrl+V") { hid.shortcut(KeyboardMapper.CTRL, 0x19) }
                Shortcut("Ctrl+Z") { hid.shortcut(KeyboardMapper.CTRL, 0x1D) }
                Shortcut("Ctrl+A") { hid.shortcut(KeyboardMapper.CTRL, 0x04) }
                shortcuts.forEach { shortcut -> Shortcut(shortcut.label) { hid.shortcut(shortcut.modifier, shortcut.code) } }
                Shortcut("Customize") { editing = true }
            }
        }
    }
    if (editing) ShortcutEditor(shortcuts, { shortcuts = it; CustomShortcut.save(prefs, it) }, { editing = false })
}

@Composable
private fun WindowsKey(hid: BluetoothHidManager) {
    var pressed by remember { mutableStateOf(false) }
    DisposableEffect(hid) {
        onDispose { hid.releaseModifier(KeyboardMapper.META) }
    }
    Box(
        Modifier.size(48.dp)
            .semantics {
                contentDescription = "Windows key"
                role = Role.Button
                onClick { hid.shortcut(KeyboardMapper.META, 0); true }
            }
            .pointerInput(hid) {
                detectTapGestures(onPress = {
                    pressed = true
                    hid.holdModifier(KeyboardMapper.META)
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        hid.releaseModifier(KeyboardMapper.META)
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(width = 48.dp, height = 32.dp),
            shape = MaterialTheme.shapes.small,
            color = if (pressed) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_windows), null, Modifier.size(18.dp))
            }
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
