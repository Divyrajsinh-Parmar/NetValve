package dev.netvalve.payprobe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * One screen, built in code (no layout files, no Compose): label, URL, Start, Stop, Clear,
 * live numbers, a latency chart, and where the CSV lives.
 */
class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var labelInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var numbers: TextView
    private lateinit var chart: ChartView

    private val refresh = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 500L)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Targeting Android 15 means the app draws edge to edge, so keep content clear of the system bars.
        val pad = dp(16)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            content.setPadding(
                pad + insets.systemWindowInsetLeft,
                pad + insets.systemWindowInsetTop,
                pad + insets.systemWindowInsetRight,
                pad + insets.systemWindowInsetBottom,
            )
            insets
        }
        content.setPadding(pad, pad, pad, pad)

        content.addView(TextView(this).apply {
            text = "PayProbe"
            textSize = 24f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "One fresh HTTPS request per second. Tick PayProbe in NetValve's Apps list so its traffic goes through the tunnel."
            setTextColor(Color.DKGRAY)
            textSize = 13f
        })

        labelInput = EditText(this).apply {
            hint = "run label, e.g. C1_S1_run1"
            setText("test")
            setSingleLine()
        }
        urlInput = EditText(this).apply {
            hint = "probe URL"
            setText(ProbeService.DEFAULT_URL)
            setSingleLine()
            textSize = 13f
        }
        content.addView(labelInput)
        content.addView(urlInput)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply { text = "Start"; setOnClickListener { startProbe() } })
        buttons.addView(Button(this).apply { text = "Stop"; setOnClickListener { stopProbe() } })
        buttons.addView(Button(this).apply { text = "Clear"; setOnClickListener { ProbeState.clear(); render() } })
        content.addView(buttons)

        status = TextView(this).apply { setTextColor(Color.BLACK); textSize = 14f }
        numbers = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(Color.BLACK)
            textSize = 13f
        }
        content.addView(status)
        content.addView(numbers)

        chart = ChartView(this)
        content.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply {
            topMargin = dp(8)
        })

        content.addView(TextView(this).apply {
            text = "Get the CSV on the Mac:\nadb pull /sdcard/Android/data/dev.netvalve.payprobe/files/ ."
            setTextColor(Color.DKGRAY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(8), 0, 0)
        })

        setContentView(scroll)
        scroll.requestApplyInsets()

        // Dark status-bar icons on our white screen. This must come AFTER setContentView: before it the
        // window has no decor view yet and window.insetsController throws a NullPointerException
        // (that was the first-launch crash). It is cosmetic, so a failure here must never kill the app.
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(mask, mask)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresh)
    }

    override fun onPause() {
        ui.removeCallbacks(refresh)
        super.onPause()
    }

    private fun startProbe() {
        if (ProbeState.running) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val i = Intent(this, ProbeService::class.java)
            .putExtra(ProbeService.EXTRA_LABEL, labelInput.text.toString())
            .putExtra(ProbeService.EXTRA_URL, urlInput.text.toString().trim())
        startForegroundService(i)
        // The service marks the run as started within a few milliseconds.
        ui.postDelayed({ render() }, 300L)
    }

    private fun stopProbe() {
        stopService(Intent(this, ProbeService::class.java))
        ui.postDelayed({ render() }, 300L)
    }

    private fun render() {
        val s = ProbeState.summary()
        val last = ProbeState.last(1).firstOrNull()
        status.text = if (ProbeState.running) {
            "RUNNING  label=${ProbeState.label}\n${ProbeState.url}"
        } else if (s.count > 0) {
            "STOPPED  label=${ProbeState.label}\nCSV: ${ProbeState.csvPath}"
        } else {
            "Stopped. Press Start."
        }
        numbers.text = String.format(
            "n=%d  timeouts/fails=%d\nlast=%s ms\np50=%d  p95=%d  p99=%d  max=%d ms\nmean=%.0f ms",
            s.count, s.failures, last?.latencyMs?.toString() ?: "-", s.p50, s.p95, s.p99, s.max, s.mean,
        )
        chart.setData(ProbeState.last(CHART_POINTS))
    }

    private companion object {
        const val CHART_POINTS = 120
    }
}
