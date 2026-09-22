package com.mobilekeyb

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilekeyb.bluetooth.KeyboardMapper
import com.mobilekeyb.bluetooth.HidKey
import org.json.JSONArray
import org.json.JSONObject

data class CustomShortcut(val label: String, val modifier: Int, val code: Int) {
    companion object {
        fun load(prefs: SharedPreferences): List<CustomShortcut> = runCatching {
            val array = JSONArray(prefs.getString("shortcuts", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                CustomShortcut(item.getString("label"), item.getInt("modifier"), item.getInt("code"))
            }.filter { it.label.isNotBlank() && it.modifier in 0..255 && it.code in 1..0x73 }
        }.getOrDefault(emptyList())
        fun save(prefs: SharedPreferences, shortcuts: List<CustomShortcut>) {
            val array = JSONArray()
            shortcuts.forEach { array.put(JSONObject().put("label", it.label).put("modifier", it.modifier).put("code", it.code)) }
            prefs.edit().putString("shortcuts", array.toString()).apply()
        }
        fun key(text: String): HidKey? {
            val name = text.trim()
            KeyboardMapper.named.entries.firstOrNull { it.key.equals(name, true) }?.let { return it.value }
            val extra = mapOf("left" to 0x50, "right" to 0x4F, "up" to 0x52, "down" to 0x51,
                "space" to 0x2C, "home" to 0x4A, "end" to 0x4D, "pageup" to 0x4B, "pagedown" to 0x4E)
            extra[name.lowercase()]?.let { return HidKey(it) }
            if (name.startsWith("F", true)) name.drop(1).toIntOrNull()?.takeIf { it in 1..12 }?.let { return HidKey(0x39 + it) }
            return name.singleOrNull()?.let { KeyboardMapper.character(it.lowercaseChar()) }
        }
    }
}

@Composable
fun ShortcutEditor(shortcuts: List<CustomShortcut>, onChange: (List<CustomShortcut>) -> Unit, onClose: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var keyName by remember { mutableStateOf("") }
    var modifiers by remember { mutableIntStateOf(0) }
    val key = CustomShortcut.key(keyName)
    AlertDialog(onDismissRequest = onClose, title = { Text("Custom shortcuts") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.forEachIndexed { index, shortcut ->
                Row {
                    Text(shortcut.label, Modifier.weight(1f))
                    TextButton(onClick = { onChange(shortcuts.filterIndexed { i, _ -> i != index }) }) { Text("Delete") }
                }
            }
            OutlinedTextField(label, { label = it.take(30) }, label = { Text("Shortcut name") }, singleLine = true)
            OutlinedTextField(keyName, { keyName = it }, label = { Text("Key") }, singleLine = true,
                isError = keyName.isNotBlank() && key == null,
                supportingText = { Text("Letter, digit, F1–F12, Enter, Tab, Esc, Left, Right, Up, Down, Home, End, PageUp or PageDown") })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Ctrl" to KeyboardMapper.CTRL, "Alt" to KeyboardMapper.ALT, "Shift" to KeyboardMapper.SHIFT, "Win" to KeyboardMapper.META).forEach { (name, mask) ->
                    FilterChip(selected = modifiers and mask != 0, onClick = { modifiers = modifiers xor mask }, label = { Text(name) })
                }
            }
            Button(enabled = label.isNotBlank() && key != null, onClick = {
                key?.let { onChange(shortcuts + CustomShortcut(label.trim(), modifiers or it.modifier, it.code)) }
                label = ""; keyName = ""; modifiers = 0
            }) { Text("Save shortcut") }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
}
