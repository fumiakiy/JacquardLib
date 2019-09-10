# JacquardLib
Unofficial, incomplete, experimental software development kit for Jacquard(tm) by Google

This is a source code of a software development kit that allows Android apps to connect to your snap tag that comes with your Levi's Commuter Trucker Jacket with Jacquard by Google.

# Install

`implementation 'com.luckypines:jacquardlib:0.0.1'`

# Synopsis

```
private lateinit var jacquardSnapTag: JacquardSnapTag

...
override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContentView(R.layout.activity_main)
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
  launch(Dispatchers.Main) {
    jacquardSnapTag.connect(this@MainActivity)
  }
}

override fun onPause() {
  super.onPause()
  launch(Dispatchers.Main) {
    jacquardSnapTag.disconnect()
  }
}

...

override fun onGesture(gesture: JacquardGestureType) {
  runOnUiThread({
    gestureStatus.text = gesture.name
  })
}
```

# License

MIT
