package com.luckypines.jacquard1

import PresentationApiClient
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.luckypines.jacquardlib.JacquardGestureType
import com.luckypines.jacquardlib.JacquardSnapTag
import com.luckypines.jacquardlib.LedCommand
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext


private const val REQUEST_LOCATION_PERMISSION = 9001

@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity(),
  CoroutineScope,
  JacquardSnapTag.OnSnapTagConnectionStatusChangedListener,
  JacquardSnapTag.OnGestureListener,
  JacquardSnapTag.OnThreadListener {

  private val job = SupervisorJob()
  private lateinit var jacquardSnapTag: JacquardSnapTag
  private lateinit var apiClient: PresentationApiClient

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main + job

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    threads.threads = ByteArray(15, { i -> (i * 10).toByte() })
    illuminateButton.setOnClickListener {
      val command = when(ledPattern.checkedRadioButtonId) {
        R.id.rainbow -> LedCommand.Rainbow
        R.id.redBlink -> LedCommand.RedBlink
        R.id.whiteLight -> LedCommand.WhiteLight
        else -> throw IllegalArgumentException()
      }
      jacquardSnapTag.illuminate(command)
    }

    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    jacquardSnapTag = JacquardSnapTag(bluetoothManager)
    jacquardSnapTag.onSnapTagConnectionStateChangedListener = this
    jacquardSnapTag.onGestureListener = this
    jacquardSnapTag.onThreadListener = this

//    val certInputStream = assets.open("cert.pem")
    apiClient = PresentationApiClient()

    statusText.setOnClickListener {
      onGesture(JacquardGestureType.BRUSH_OUT)
    }
    statusText.setOnLongClickListener {
      onGesture(JacquardGestureType.BRUSH_IN)
      return@setOnLongClickListener true
    }
  }

  override fun onDestroy() {
    coroutineContext.cancelChildren()
    super.onDestroy()
  }

  override fun onResume() {
    super.onResume()
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
      == PackageManager.PERMISSION_GRANTED) {
      launch(Dispatchers.Main) {
        jacquardSnapTag.connect(this@MainActivity)
      }
    } else {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
        REQUEST_LOCATION_PERMISSION
      )
    }
  }

  override fun onPause() {
    super.onPause()
    launch(Dispatchers.Main) {
      jacquardSnapTag.disconnect()
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode != REQUEST_LOCATION_PERMISSION) return
    if (grantResults.size == 0) return
    if (grantResults[0] != PackageManager.PERMISSION_GRANTED) return
    launch(Dispatchers.Main) {
      jacquardSnapTag.connect(this@MainActivity)
    }
  }

  override fun onSnapTagConnectionStateChanged(status: JacquardSnapTag.Status) {
    runOnUiThread({
      statusText.text = "$status"
    })
  }

  override fun onGesture(gesture: JacquardGestureType) {
    val url = "http://${urlText.text}"
    runOnUiThread({
      gestureStatus.text = gesture.name
      when(gesture) {
        JacquardGestureType.BRUSH_OUT ->
          launch(Dispatchers.IO) {
            apiClient.call(url, "forward")
          }
        JacquardGestureType.BRUSH_IN ->
          launch(Dispatchers.IO) {
            apiClient.call(url, "back")
          }
        JacquardGestureType.DOUBLE_TAP ->
          launch(Dispatchers.IO) {
            apiClient.call(url, "start")
          }
      }
    })
  }

  override fun onThread(values: ByteArray) {
    runOnUiThread({
      threads.threads = values
    })
  }
}
