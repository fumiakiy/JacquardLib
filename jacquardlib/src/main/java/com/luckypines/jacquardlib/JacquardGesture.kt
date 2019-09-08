package com.luckypines.jacquardlib

import java.util.*

internal val JACQUARD_GESTURE_CHARACTERISTIC_UUID = UUID.fromString("d45c2030-4270-a125-a25d-ee458c085001")

enum class JacquardGestureType(val raw: ByteArray) {
  DOUBLE_TAP(ByteArray(1, { i -> 0x1 })),
  BRUSH_IN(ByteArray(1, { i -> 0x2 })),
  BRUSH_OUT(ByteArray(1, { i -> 0x3 })),
  COVER(ByteArray(1, { i -> 0x7 })),
  BRUSH_OUT_IN(ByteArray(1, { i -> 0x8 })),
  UNKNOWN(ByteArray(1, { i -> 0x0 }))
}

internal class JacquardGesture(private val listener: OnGestureRecognizedListener) {

  internal interface OnGestureRecognizedListener {
    fun onGestureRecognized(jacquardGesture: JacquardGestureType)
  }

  internal fun onGesture(value: ByteArray) {
    val gesture = from(value)
    if (gesture != JacquardGestureType.UNKNOWN) {
      listener.onGestureRecognized(gesture)
    }
  }

  private fun from(raw: ByteArray): JacquardGestureType {
    val gesture = JacquardGestureType.values().filter { it.raw.contentEquals(raw) }
    if (gesture.size == 0) {
      return JacquardGestureType.UNKNOWN
    } else {
      return gesture.last()
    }
  }
}
