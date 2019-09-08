package com.luckypines.jacquard1

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.luckypines.jacquardlib.JacquardGestureType
import com.luckypines.jacquardlib.JacquardSnapTag
import com.luckypines.jacquardlib.LedCommand
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity(),
  JacquardSnapTag.OnSnapTagConnectionStatusChangedListener,
    JacquardSnapTag.OnGestureListener,
    JacquardSnapTag.OnThreadListener {

  private lateinit var jacquardSnapTag: JacquardSnapTag

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
  }

  override fun onResume() {
    super.onResume()
    GlobalScope.launch(Dispatchers.Main) {
      jacquardSnapTag.connect(this@MainActivity)
    }
  }

  override fun onPause() {
    super.onPause()
    GlobalScope.launch(Dispatchers.Main) {
      jacquardSnapTag.disconnect()
    }
  }

  override fun onSnapTagConnectionStateChanged(status: JacquardSnapTag.Status) {
    runOnUiThread({
      statusText.text = "$status"
    })
  }

  override fun onGesture(gesture: JacquardGestureType) {
    runOnUiThread({
      gestureStatus.text = gesture.name
    })
  }

  override fun onThread(values: ByteArray) {
    runOnUiThread({
      threads.threads = values
    })
  }
}
