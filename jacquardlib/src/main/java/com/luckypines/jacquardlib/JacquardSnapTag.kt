package com.luckypines.jacquardlib

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

private val JACQUARD_PRIMARY_SERVICE_UUID = UUID.fromString("d45c2000-4270-a125-a25d-ee458c085001")
private val JACQUARD_PIN_CHARACTERISTIC_UUID = UUID.fromString("d45c2070-4270-a125-a25d-ee458c085001")
private val CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private val TAG = "JacquardSnapTag"


@ExperimentalUnsignedTypes
/**
 * Represents the functionality of a "snap tag" that is connected to your jacket.
 * The class requires coarse location permission because it is required for BlueTooth LE scanner
 * to scan a device and connect to it.
 *
 * Create an instance of this class by passing a `BluetoothManager`.
 *
 * Make sure the app has a location permission and call `connect(Context)`.
 *
 * When the app is done with the snap tag, call `disconnect`.
 *
 * Connection status between the library and the snap tag can be acquired either by accessing
 * `status` property or by listening to `OnSnapTagConnectionStatusChangedListener`. Please note
 * that the callback method `onSnapTagConnectionStatusChanged` can be called from any thread; so
 * that means you should almost always do `runOnUiThread` or similar to handle the status in your
 * app.
 *
 * The gesture that the connected snap tag recognizes can be acquired by listening to
 * `OnGestureListener`. Please note that the callback method `onGesture` can be called from
 * any thread; so that means you should almost always do `runOnUiThread` or similar to handle
 * it in your app.
 *
 * The "analog" data that a snap tag returns to the library can be acquired by listening to
 * `OnThreadListener`. Please note that the callback method `onThread` can be called from any
 * thread; so that means you should almost always do `runOnUiThread` or similar to handle
 * the data in your app.
 *
 * Finally, your app can call `illuminate` method after connecting to the snap tag to light up
 * the snap tag when you want it to.
 */
class JacquardSnapTag(bluetoothManager: BluetoothManager) {
  enum class Status {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    BONDING,
    SETTING_UP,
    READY,
    DISCONNECTING
  }

  interface OnSnapTagConnectionStatusChangedListener {
    /**
     * Called when the status of the connection between the library and a snap tag is changed.
     */
    fun onSnapTagConnectionStateChanged(status: Status)
  }

  interface OnThreadListener {
    /**
     * Called when the library receives a set of data from the snap tag about how each thread was
     * touched.
     */
    fun onThread(values: ByteArray)
  }

  interface OnGestureListener {
    /**
     * Called when the snap tag recognizes a gesture.
     */
    fun onGesture(gesture: JacquardGestureType)
  }

  private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
  private val notificationCharacteristics = listOf(
    JACQUARD_THREAD_CHARACTERISTIC_UUID,
    JACQUARD_GESTURE_CHARACTERISTIC_UUID
  )
  private var notificationIndex = 0

  private var bluetoothLeScanner: BluetoothLeScanner? = null
  private var bluetoothGatt: BluetoothGatt? = null
  private var _status: Status = Status.DISCONNECTED
  private var jacquardThread: JacquardThread? = null
  private var jacquardLed: JacquardLed? = null
  private var jacquardGesture: JacquardGesture? = null

  /**
   * Represents the current status of connection between the library and a snap tag.
   */
  val status: Status get() = _status
  /**
   * Set a listener to receive status update about the connection to a snap tag.
   */
  var onSnapTagConnectionStateChangedListener: OnSnapTagConnectionStatusChangedListener? = null
  /**
   * Set a listener to receive data about how each thread was touched.
   */
  var onThreadListener: OnThreadListener? = null
  /**
   * Set a listener to receive a gesture when it is recognized by the connected snap tag.
   */
  var onGestureListener: OnGestureListener? = null

  /**
   * Call when the `context` is ready to connect to a snap tag.
   */
  suspend fun connect(context: Context) {
    val p = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
    if (p != PackageManager.PERMISSION_GRANTED) {
      return
    }
    val device = scan()
    bluetoothLeScanner?.stopScan(object: ScanCallback() {})
    if (device == null) return
    connect(context, device)
  }

  /**
   * Light up the connected snap tag.
   * You can stop the current illumination by calling this method again with the same LedCommand.
   */
  fun illuminate(command: LedCommand) {
    if (bluetoothGatt == null) return
    if (status !== Status.READY) return
    jacquardLed?.illuminate(command)
  }

  /**
   * Disconnect from the connected snap tag.
   */
  fun disconnect() {
    setStatus(Status.DISCONNECTING)
    bluetoothLeScanner?.stopScan(object: ScanCallback() {})
    bluetoothLeScanner = null
    bluetoothGatt?.disconnect()
    bluetoothGatt = null
    jacquardGesture = null
    jacquardThread = null
    jacquardLed = null
    setStatus(Status.DISCONNECTED)
  }

  private fun onGattConnected(gatt: BluetoothGatt) {
    bluetoothGatt = gatt
    // Now let's find services
    gatt.discoverServices()
    // The result will be called back to `onServiceDiscovered` below
  }

  private fun onPrimaryServiceDiscovered(service: BluetoothGattService) {
    setStatus(Status.BONDING)
    // Let's find the jacket's pin code to create bond
    bluetoothGatt?.let { gatt ->
      // Read the pin value from the jacket and create bond in the callback.
      service.getCharacteristic(JACQUARD_PIN_CHARACTERISTIC_UUID)?.let { c ->
        gatt.readCharacteristic(c)
        // the result will be there at `onCharacteristicRead` below
      }
    }
  }

  private fun onPinFound(c: BluetoothGattCharacteristic) {
    // Got the pin value.
    val v = c.value
    val pin = v.copyOfRange(6, v.size)
    // Create bond using the pin between the snap tag and the phone
    bluetoothGatt?.let { gatt ->
      gatt.device?.setPin(pin)
      gatt.device?.createBond()
      // TODO createBond could fail, or could take some time.
      // The correct way to do this is to install a broadcast receiver.

      // Now let's set up characteristic notifications
      setStatus(Status.SETTING_UP)
      notificationIndex = 0
      setupNotification(c.service)
    }
  }

  private fun setupNotification(service: BluetoothGattService) {
    if (notificationIndex < notificationCharacteristics.size) {
      service.getCharacteristic(notificationCharacteristics[notificationIndex])
        ?.descriptors
        ?.filter { it.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID }
        ?.lastOrNull()
        ?.let { d ->
          d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
          bluetoothGatt?.let {
            it.writeDescriptor(d)
            it.setCharacteristicNotification(d.characteristic, true)
            // The result will be there at `onDescriptorWrite` below
          }
        }
    } else {
      onSetupNotificationComplete(service)
    }
  }

  private fun onSetupNotificationComplete(service: BluetoothGattService) {
    jacquardThread = JacquardThread(bluetoothGatt!!, service, object: JacquardThread.OnThreadListener {
      override fun onThread(value: ByteArray) {
        onThreadListener?.onThread(value)
      }
    })
    jacquardGesture = JacquardGesture(object: JacquardGesture.OnGestureRecognizedListener {
      override fun onGestureRecognized(jacquardGesture: JacquardGestureType) {
        onGestureListener?.onGesture(jacquardGesture)
      }
    })

    setStatus(Status.READY)
  }

  private fun setStatus(status: Status) {
    this._status = status
    onSnapTagConnectionStateChangedListener?.onSnapTagConnectionStateChanged(this.status)
  }

  private suspend fun scan(): BluetoothDevice? = suspendCancellableCoroutine { cont ->
    setStatus(Status.SCANNING)
    bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    val jacquardUuid = JACQUARD_PRIMARY_SERVICE_UUID
    val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(jacquardUuid)).build()
    val scanSetting = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
      .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
      .build()
    bluetoothLeScanner?.startScan(listOf(scanFilter), scanSetting, object: ScanCallback() {
      override fun onScanFailed(errorCode: Int) {
        if (cont.isActive) {
          Log.e(TAG, "Scan Error: $errorCode")
          cont.resume(null)
        }
      }

      override fun onScanResult(callbackType: Int, result: ScanResult?) {
        if (cont.isActive) {
          if (result == null) {
            cont.resume(null)
          } else {
            cont.resume(result.device)
          }
        }
      }
    })
  }

  private fun connect(context: Context, device: BluetoothDevice) {
    device.connectGatt(context, false, object : BluetoothGattCallback() {
      override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        when (status) {
          // IDK what really is the status `19` but it seems required to handle it like success
          BluetoothGatt.GATT_SUCCESS, 19 -> {
            when (newState) {
              BluetoothProfile.STATE_CONNECTED -> {
                setStatus(Status.CONNECTING)
                onGattConnected(gatt!!)
              }
              BluetoothProfile.STATE_DISCONNECTING -> {
                setStatus(Status.DISCONNECTING)
              }
              BluetoothProfile.STATE_DISCONNECTED -> {
                setStatus(Status.DISCONNECTED)
                bluetoothGatt = null
              }
            }
          }
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        when (status) {
          BluetoothGatt.GATT_SUCCESS -> {
            if (this@JacquardSnapTag.status == Status.CONNECTING) {
              gatt?.getService(JACQUARD_PRIMARY_SERVICE_UUID)?.let {
                onPrimaryServiceDiscovered(it)
              }
            }
            if (this@JacquardSnapTag.jacquardLed == null) {
              gatt?.getService(JACQUARD_LED_SERVICE_UUID)?.let {
                jacquardLed = JacquardLed(gatt, it)
              }
            }
          }
        }
      }

      override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
      ) {
        if (descriptor == null) return
        if (descriptor.characteristic?.service != null
          && descriptor.characteristic?.service?.uuid == JACQUARD_PRIMARY_SERVICE_UUID) {
          notificationIndex++
          setupNotification(descriptor.characteristic?.service!!)
        }
      }

      override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
      ) {
        val c = characteristic ?: return
        if (c.uuid == JACQUARD_PIN_CHARACTERISTIC_UUID) {
          onPinFound(c)
        }
      }

      override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
      ) {
        val c = characteristic ?: return
        if (c.uuid == JACQUARD_GESTURE_CHARACTERISTIC_UUID) {
          jacquardGesture?.onGesture(c.value)
        } else if (c.uuid == JACQUARD_THREAD_CHARACTERISTIC_UUID) {
          jacquardThread?.onThread(c.value)
        }
      }
    })
  }
}
