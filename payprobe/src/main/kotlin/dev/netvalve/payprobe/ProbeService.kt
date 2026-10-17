package dev.netvalve.payprobe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground service that runs the probe loop. It keeps measuring with the screen off.
 * Start it with the extras below; stop it with stopService().
 */
class ProbeService : Service() {

    private var executor: ScheduledExecutorService? = null
    private var sink: CsvSink? = null
    private var url: String = DEFAULT_URL
    private var label: String = "run"
    private var nextIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground service must call startForeground quickly, so do it first.
        enterForeground("Starting probe")
        if (executor != null) return START_NOT_STICKY // already running

        url = intent?.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL
        label = CsvFormat.safeLabel(intent?.getStringExtra(EXTRA_LABEL))
        val intervalMs = (intent?.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS) ?: DEFAULT_INTERVAL_MS)
            .coerceIn(200L, 60_000L)

        val dir = getExternalFilesDir(null) ?: filesDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "payprobe_${label}_$stamp.csv")
        sink = CsvSink(file, label)
        nextIndex = 0
        ProbeState.begin(url, label, file.absolutePath, System.currentTimeMillis())

        val ex = Executors.newSingleThreadScheduledExecutor()
        executor = ex
        ex.scheduleAtFixedRate({ runOnce() }, 0L, intervalMs, TimeUnit.MILLISECONDS)
        // Optional fixed run length, so repeated runs are exactly as long as each other.
        val durationSec = intent?.getIntExtra(EXTRA_DURATION_S, 0) ?: 0
        if (durationSec > 0) ex.schedule({ stopSelf() }, durationSec.toLong(), TimeUnit.SECONDS)
        return START_NOT_STICKY
    }

    private fun runOnce() {
        try {
            val wall = System.currentTimeMillis()
            val result = Prober.probe(url)
            val sample = ProbeSample(
                index = nextIndex++,
                wallClockMs = wall,
                elapsedMs = wall - ProbeState.startedWallMs,
                latencyMs = result.latencyMs,
                ok = result.ok,
                httpCode = result.httpCode,
                note = result.note,
            )
            sink?.write(sample)
            ProbeState.add(sample)
            val text = if (sample.ok) "${sample.latencyMs} ms" else "FAILED (${sample.note.ifEmpty { "http ${sample.httpCode}" }})"
            notify("$label: $text")
        } catch (_: Throwable) {
            // Never let one bad probe kill the schedule.
        }
    }

    override fun onDestroy() {
        executor?.shutdownNow()
        executor = null
        sink?.close()
        sink = null
        ProbeState.end()
        super.onDestroy()
    }

    // ---- notification ----------------------------------------------------------------

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "PayProbe", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PayProbe is measuring")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun enterForeground(text: String) {
        ensureChannel()
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_LABEL = "label"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val EXTRA_DURATION_S = "duration_s"
        const val DEFAULT_URL = "https://connectivitycheck.gstatic.com/generate_204"
        const val DEFAULT_INTERVAL_MS = 1_000L
        private const val CHANNEL_ID = "payprobe"
        private const val NOTIFICATION_ID = 1
    }
}
