package dev.netvalve.payprobe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Line chart of the most recent latencies. No chart library: about 60 lines of Canvas. */
class ChartView(context: Context) : View(context) {

    private var data: List<ProbeSample> = emptyList()
    private val density = resources.displayMetrics.density

    private val bg = Paint().apply { color = Color.rgb(245, 245, 245) }
    private val grid = Paint().apply { color = Color.rgb(215, 215, 215); strokeWidth = density }
    private val line = Paint().apply {
        color = Color.rgb(25, 118, 210); strokeWidth = 2 * density; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val fail = Paint().apply { color = Color.rgb(211, 47, 47); isAntiAlias = true }
    private val text = Paint().apply { color = Color.DKGRAY; textSize = 11 * density; isAntiAlias = true }

    fun setData(samples: List<ProbeSample>) {
        data = samples
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        val left = 40 * density
        val top = 8 * density
        val bottom = h - 8 * density
        val right = w - 8 * density
        val plotH = bottom - top

        val maxLat = (data.maxOfOrNull { it.latencyMs } ?: 100L).coerceAtLeast(100L)
        val scaleMax = niceCeil(maxLat)

        for (i in 0..4) {
            val y = bottom - plotH * i / 4f
            canvas.drawLine(left, y, right, y, grid)
            canvas.drawText((scaleMax * i / 4).toString(), 4 * density, y + 4 * density, text)
        }
        canvas.drawText("ms", 4 * density, top + 12 * density, text)
        if (data.isEmpty()) {
            canvas.drawText("no samples yet", left + 8 * density, top + plotH / 2, text)
            return
        }

        val n = data.size
        val stepX = if (n > 1) (right - left) / (n - 1) else 0f
        val path = Path()
        data.forEachIndexed { i, s ->
            val x = left + stepX * i
            val y = bottom - plotH * (s.latencyMs.toFloat() / scaleMax)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, line)
        data.forEachIndexed { i, s ->
            if (!s.ok) {
                val x = left + stepX * i
                val y = bottom - plotH * (s.latencyMs.toFloat() / scaleMax)
                canvas.drawCircle(x, y, 4 * density, fail)
            }
        }
    }

    /** Round up to 100, 200, 500, 1000, 2000, 5000, ... so the axis labels stay readable. */
    private fun niceCeil(v: Long): Long {
        var base = 100L
        while (true) {
            for (m in longArrayOf(1, 2, 5)) if (v <= base * m) return base * m
            base *= 10
        }
    }
}
