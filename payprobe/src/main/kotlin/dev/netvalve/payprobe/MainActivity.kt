package dev.netvalve.payprobe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * One screen, built in code (no layout files, no Compose): how to use it, run settings (label, length,
 * URL), Start / Stop / Clear, live numbers in plain words, a latency chart, and where the CSV lives.
 */
class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var labelInput: EditText
    private lateinit var savedAs: TextView
    private lateinit var durationInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var statusPill: TextView
    private lateinit var runInfo: TextView
    private lateinit var tileTypical: TextView
    private lateinit var tileSlow: TextView
    private lateinit var tileFails: TextView
    private lateinit var tileCount: TextView
    private lateinit var details: TextView
    private lateinit var csvInfo: TextView
    private lateinit var chart: ChartView

    private val refresh = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 500L)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int = 14, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 16, Color.rgb(226, 232, 240))
        setPadding(dp(14), dp(12), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(10) }
    }

    private fun heading(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.rgb(15, 23, 42))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun body(text: String, size: Float = 13f, color: Int = Color.rgb(71, 85, 105)): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
        }

    private fun actionButton(text: String, color: Int, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(color, 12)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) }
    }

    /** A big number with a small caption underneath; returns the number view so render() can update it. */
    private fun tile(caption: String, row: LinearLayout): TextView {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(Color.rgb(241, 245, 249), 12)
            setPadding(dp(4), dp(10), dp(4), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        }
        val value = TextView(this).apply {
            text = "-"
            textSize = 20f
            setTextColor(Color.rgb(15, 23, 42))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        box.addView(value)
        box.addView(TextView(this).apply {
            text = caption
            textSize = 11f
            setTextColor(Color.rgb(100, 116, 139))
            gravity = Gravity.CENTER
        })
        row.addView(box)
        return value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(244, 247, 250))
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

        // ---- title
        content.addView(TextView(this).apply {
            text = "PayProbe"
            textSize = 28f
            setTextColor(Color.rgb(15, 23, 42))
            setTypeface(null, Typeface.BOLD)
        })
        content.addView(body("Times one small web request every second. A low, steady number means your connection feels fast.", 14f))

        // ---- how to use
        val howTo = card()
        howTo.addView(heading("How to use"))
        howTo.addView(body("1. In NetValve, open the Apps tab and tick PayProbe.\n2. Start protection in NetValve.\n3. Type a run name below, then press Start.\n4. Wait for the run to finish (or press Stop)."))
        content.addView(howTo)

        // ---- settings
        val settings = card()
        settings.addView(heading("Run settings"))
        labelInput = EditText(this).apply {
            hint = "Run name, e.g. L0_mobile_r1"
            setText("test")
            setSingleLine()
        }
        savedAs = body("", 12f, Color.rgb(100, 116, 139))
        labelInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                savedAs.text = "Saved as: ${CsvFormat.safeLabel(s?.toString())}  (letters, numbers, _ and - only)"
            }
        })
        savedAs.text = "Saved as: ${CsvFormat.safeLabel(labelInput.text.toString())}  (letters, numbers, _ and - only)"
        durationInput = EditText(this).apply {
            hint = "Run length in seconds (0 = until I press Stop)"
            setText("90")
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        urlInput = EditText(this).apply {
            hint = "Address to time"
            setText(ProbeService.DEFAULT_URL)
            setSingleLine()
            textSize = 13f
        }
        settings.addView(labelInput)
        settings.addView(savedAs)
        settings.addView(durationInput)
        settings.addView(urlInput)
        content.addView(settings)

        // ---- buttons
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
        }
        buttons.addView(actionButton("Start", Color.rgb(22, 163, 74)) { startProbe() })
        buttons.addView(actionButton("Stop", Color.rgb(220, 38, 38)) { stopProbe() })
        buttons.addView(actionButton("Clear", Color.rgb(100, 116, 139)) { ProbeState.clear(); render() })
        content.addView(buttons)

        // ---- live status
        val live = card()
        statusPill = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        live.addView(statusPill, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        runInfo = body("", 13f)
        runInfo.setPadding(0, dp(6), 0, dp(8))
        live.addView(runInfo)
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tileTypical = tile("Typical (p50) ms", tiles)
        tileSlow = tile("Slow (p95) ms", tiles)
        tileFails = tile("Failures", tiles)
        tileCount = tile("Samples", tiles)
        live.addView(tiles)
        details = body("", 12f)
        details.typeface = Typeface.MONOSPACE
        details.setPadding(0, dp(8), 0, 0)
        live.addView(details)
        content.addView(live)

        // ---- chart
        val chartCard = card()
        chartCard.addView(heading("Latency over time"))
        chart = ChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        content.addView(chartCard)

        // ---- where the file is
        val files = card()
        files.addView(heading("Your data"))
        csvInfo = body("", 12f)
        csvInfo.typeface = Typeface.MONOSPACE
        files.addView(csvInfo)
        files.addView(body("Copy the CSV to the Mac:\nadb pull /sdcard/Android/data/dev.netvalve.payprobe/files/ .", 12f).apply {
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(files)

        setContentView(scroll)
        scroll.requestApplyInsets()

        // Dark status-bar icons on our light screen. This must come AFTER setContentView: before it the
        // window has no decor view yet and window.insetsController throws a NullPointerException
        // (that was the first-launch crash). It is cosmetic, so a failure here must never kill the app.
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(mask, mask)
            }
        }
        render()
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
        val durationSec = durationInput.text.toString().trim().toIntOrNull()
        if (durationSec == null || durationSec < 0) {
            Toast.makeText(this, "Run length must be a whole number of seconds (0 = until Stop).", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val i = Intent(this, ProbeService::class.java)
            .putExtra(ProbeService.EXTRA_LABEL, labelInput.text.toString())
            .putExtra(ProbeService.EXTRA_URL, urlInput.text.toString().trim())
            .putExtra(ProbeService.EXTRA_DURATION_S, durationSec)
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
        val running = ProbeState.running

        val (pillText, pillColor) = when {
            running -> "● RUNNING" to Color.rgb(22, 163, 74)
            s.count > 0 -> "FINISHED" to Color.rgb(37, 99, 235)
            else -> "READY" to Color.rgb(100, 116, 139)
        }
        statusPill.text = pillText
        statusPill.background = rounded(pillColor, 20)

        runInfo.text = if (s.count > 0) {
            "Run: ${ProbeState.label}   ·   ${(last?.elapsedMs ?: 0L) / 1000} s so far"
        } else {
            "No run yet. Type a run name and press Start."
        }

        if (s.count > 0) {
            tileTypical.text = s.p50.toString()
            tileSlow.text = s.p95.toString()
            tileFails.text = s.failures.toString()
            tileCount.text = s.count.toString()
        } else {
            tileTypical.text = "-"
            tileSlow.text = "-"
            tileFails.text = "-"
            tileCount.text = "-"
        }
        tileFails.setTextColor(if (s.failures > 0) Color.rgb(220, 38, 38) else Color.rgb(15, 23, 42))

        details.text = String.format(
            "last %s ms · p99 %d ms · max %d ms · mean %.0f ms",
            last?.latencyMs?.toString() ?: "-", s.p99, s.max, s.mean,
        )
        csvInfo.text = if (ProbeState.csvPath.isNotEmpty()) "File: ${ProbeState.csvPath}" else "No file yet. It is created when you press Start."
        chart.setData(ProbeState.last(CHART_POINTS))
    }

    private companion object {
        const val CHART_POINTS = 120
    }
}
