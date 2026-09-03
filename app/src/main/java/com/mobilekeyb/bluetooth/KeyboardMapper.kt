package com.mobilekeyb.bluetooth

data class HidKey(val code: Int, val modifier: Int = 0)

object KeyboardMapper {
    const val CTRL = 0x01
    const val SHIFT = 0x02
    const val ALT = 0x04
    const val META = 0x08

    fun character(char: Char): HidKey? = when {
        char in 'a'..'z' -> HidKey(0x04 + (char - 'a'))
        char in 'A'..'Z' -> HidKey(0x04 + (char - 'A'), SHIFT)
        char in '1'..'9' -> HidKey(0x1E + (char - '1'))
        char == '0' -> HidKey(0x27)
        char == ' ' -> HidKey(0x2C)
        char == '\n' -> HidKey(0x28)
        char == '-' -> HidKey(0x2D)
        char == '_' -> HidKey(0x2D, SHIFT)
        char == '=' -> HidKey(0x2E)
        char == '+' -> HidKey(0x2E, SHIFT)
        char == '[' -> HidKey(0x2F)
        char == '{' -> HidKey(0x2F, SHIFT)
        char == ']' -> HidKey(0x30)
        char == '}' -> HidKey(0x30, SHIFT)
        char == '\\' -> HidKey(0x31)
        char == '|' -> HidKey(0x31, SHIFT)
        char == ';' -> HidKey(0x33)
        char == ':' -> HidKey(0x33, SHIFT)
        char == '\'' -> HidKey(0x34)
        char == '"' -> HidKey(0x34, SHIFT)
        char == '`' -> HidKey(0x35)
        char == '~' -> HidKey(0x35, SHIFT)
        char == ',' -> HidKey(0x36)
        char == '<' -> HidKey(0x36, SHIFT)
        char == '.' -> HidKey(0x37)
        char == '>' -> HidKey(0x37, SHIFT)
        char == '/' -> HidKey(0x38)
        char == '?' -> HidKey(0x38, SHIFT)
        char == '!' -> HidKey(0x1E, SHIFT)
        char == '@' -> HidKey(0x1F, SHIFT)
        char == '#' -> HidKey(0x20, SHIFT)
        char == '$' -> HidKey(0x21, SHIFT)
        char == '%' -> HidKey(0x22, SHIFT)
        char == '^' -> HidKey(0x23, SHIFT)
        char == '&' -> HidKey(0x24, SHIFT)
        char == '*' -> HidKey(0x25, SHIFT)
        char == '(' -> HidKey(0x26, SHIFT)
        char == ')' -> HidKey(0x27, SHIFT)
        else -> null
    }

    val named = mapOf(
        "Esc" to HidKey(0x29), "Tab" to HidKey(0x2B), "Enter" to HidKey(0x28),
        "Back" to HidKey(0x2A), "Del" to HidKey(0x4C), "←" to HidKey(0x50),
        "→" to HidKey(0x4F), "↑" to HidKey(0x52), "↓" to HidKey(0x51)
    )
}
