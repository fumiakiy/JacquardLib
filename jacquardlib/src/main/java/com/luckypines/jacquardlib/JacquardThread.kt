package com.luckypines.jacquardlib

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import java.util.*

// This one seems to be the legacy UUID
// val JACQUARD_THREAD_CHARACTERISTIC_UUID = UUID.fromString("d45c2010-4270-a125-a25d-ee458c085001")
internal val JACQUARD_THREAD_CHARACTERISTIC_UUID = UUID.fromString("d45c20b0-4270-a125-a25d-ee458c085001")

internal class JacquardThread(
  private val gatt: BluetoothGatt,
  private val service: BluetoothGattService,
  private val listener: OnThreadListener) {

  internal interface OnThreadListener {
    fun onThread(value: ByteArray)
  }

  internal fun onThread(value: ByteArray) {
    // IDK what the first three bytes represent
    val d = value.sliceArray(IntRange(3, 18))
    listener.onThread(d)
  }
}