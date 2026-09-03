package com.mobilekeyb.bluetooth

import kotlin.math.roundToInt

object Reports {
    fun mouse(buttons: Int = 0, dx: Float = 0f, dy: Float = 0f, wheel: Float = 0f, horizontal: Float = 0f) =
        byteArrayOf(buttons.toByte(), dx.hidByte(), dy.hidByte(), wheel.hidByte(), horizontal.hidByte())

    fun keyboard(modifiers: Int = 0, vararg keys: Int): ByteArray = ByteArray(8).also { report ->
        report[0] = modifiers.toByte()
        keys.take(6).forEachIndexed { index, key -> report[index + 2] = key.toByte() }
    }

    private fun Float.hidByte() = roundToInt().coerceIn(-127, 127).toByte()
}
