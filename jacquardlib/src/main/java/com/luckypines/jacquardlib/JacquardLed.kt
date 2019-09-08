package com.luckypines.jacquardlib

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import java.util.UUID

val JACQUARD_LED_SERVICE_UUID = UUID.fromString("d2f2bf0d-d165-445c-b0e1-2d6b642ec57b")
val JACQUARD_LED_CHARACTERISTIC_UUID = UUID.fromString("d2f2eabb-d165-445c-b0e1-2d6b642ec57b")

@ExperimentalUnsignedTypes
enum class LedCommand(val value: UByte) {
  Rainbow(0x10U),
  RedBlink(0x20U),
  WhiteLight(0x1bU)

  //0x28U: (NONE)
  //0x27U: (NONE)
  //0x26U: white circle, quick vibe
  //0x25U: white, quick blink, quick vibe
  //0x24U: no light, continuous vibe
  //0x23U: no light, fast long vibe
  //0x22U: no light, fast vibe
  //0x21U: white blink
  //
  //0x1fU: purple blink, intermittent vibe
  //0x1eU: pink blink, intermittent vibe
  //0x1dU: blue blink intermittent vibe
  //0x1cU: white, vibe, blink
  //
  //0x1aU: red, intermediate blink
  //0x19U: blue light
  //0x18U: blue light (the same?)
  //0x17U: small green light
  //0x16U: small green blink
  //0x15U: (NONE)
  //0x14U: small red, slow blink
  //0x13U: circling blue
  //0x12U: (NONE)
  //0x11U: small red fast blink
  //
  //0x0fU: green blink
  //0x0eU: yellow blink
  //0x0dU: blue fast blink with vibe
  //0x0cU: blue fancy blink with vibe
  //0x0bU: (NONE)
  //0x0aU: blue pulse
  //0x09U: white fancy blink with vibe
  //0x08U: another white fancy blink with vibe
  //0x07U: (NONE)
  //0x06U: circling white with vibe
  //0x05U: white vibe and blink
  //0x04U: white vibe and blink stronger
  //0x03U: (NONE)
  //0x02U: red pulse with vibe
  //0x01U: white pulse
  //0x00U:
}

// These UUIDs notify when led status changes
//private const val C_1 = "d2f2b8d0-d165-445c-b0e1-2d6b642ec57b" // ILLUMINATOR ID NOTIF?
//private const val C_2 = "d2f2b8d1-d165-445c-b0e1-2d6b642ec57b" // ILLUMI STOP NOTIF?, VIBRATOR NOTIF?

@ExperimentalUnsignedTypes
internal class JacquardLed(private val gatt: BluetoothGatt, private val service: BluetoothGattService) {

  internal fun illuminate(command: LedCommand) {
    service.getCharacteristic(JACQUARD_LED_CHARACTERISTIC_UUID)?.let { c ->
      val v = valueToWrite(command)
      c.value = v
      gatt.writeCharacteristic(c)
    }
  }

  private var currentId: UByte = 0x0U
  private val sequenceId: UByte get() {
    val id = currentId
    currentId = if (currentId < UByte.MAX_VALUE) (currentId + 0x1U).toUByte() else 0x0U
    return id
  }

  private fun valueToWrite(command: LedCommand): ByteArray {
    val b = UByteArray(19)

    // It seems these two bytes varies among snap tags
    b[0] = 0xc0U
    b[1] = 0x11U

    // Together with these 5 bytes, the first 7 bytes could be a tag's id?
    b[2] = 0x08U
    b[3] = 0x00U
    b[4] = 0x10U
    b[5] = 0x08U
    b[6] = 0x18U

    // The 8th byte seems like an id of the command.
    b[7] = sequenceId

    // The next 4 bytes don't seem to change
    b[8] = 0xdaU
    b[9] = 0x06U
    b[10] = 0x08U
    b[11] = 0x08U

    // The 13th byte seems like the command
    b[12] = command.value

    // The following 6 bytes don't seem to change
    b[13] = 0x10U
    b[14] = 0x78U
    b[15] = 0x30U
    b[16] = 0x01U
    b[17] = 0x38U
    b[18] = 0x01U

    return b.toByteArray()
  }
}
