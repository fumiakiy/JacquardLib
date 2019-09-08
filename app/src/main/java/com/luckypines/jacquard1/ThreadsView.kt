package com.luckypines.jacquard1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.FrameLayout

class ThreadsView: FrameLayout {
  constructor(context: Context) : super(context, null)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    paint = Paint()
    paint.style = Paint.Style.FILL_AND_STROKE
    paint.color = Color.BLACK

    setWillNotDraw(false)
  }

  private val paint: Paint
  private val lineWidths = 16f * 31f

  var threads: ByteArray? = null
    set(value) {
      field = value
      if (value != null) {
        invalidate()
      }
    }

  private fun threadsToPaths(): List<Path> {
    var margin = 16f
    val paths: MutableList<Path> = mutableListOf()
    threads?.forEach { byte ->
      val lineWidth = (byte / 255f) * 16f
      val p = Path()
      p.moveTo(margin, 16f)
      p.lineTo(margin, measuredHeight - 32f)
      p.lineTo(margin + lineWidth, measuredHeight - 32f)
      p.lineTo(margin + lineWidth, 16f)
      p.close()
      paths.add(p)
      margin += 16f + 16f
    }
    return paths
  }

  override fun draw(canvas: Canvas?) {
    super.draw(canvas)
    canvas?.let {
      it.save()
      it.scale(measuredWidth / lineWidths, 1f)
      threadsToPaths().forEach { path -> it.drawPath(path, paint) }
      it.restore()
    }
  }
}