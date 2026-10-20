package dev.netvalve.calibration

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.netvalve.data.model.NetworkType
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.di.AppScope
import dev.netvalve.log.LogCategory
import dev.netvalve.log.Logger
import dev.netvalve.repository.SettingsRepository
import dev.netvalve.scheduler.SchedMode
import dev.netvalve.service.TrafficEngine
import dev.netvalve.service.TunnelState
import dev.netvalve.service.VpnController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the shared speed R (download) right without the user doing anything.
 *
 * While protection is on (and the production settings are in use: all apps except the ticked ones,
 * Priority Control on) it
 *  1. applies the last known R of the current network type at once (0 = no shaping when there is none),
 *  2. after a short settle time measures the download speed ([LinkSpeedTest]) and stores and applies
 *     R = 0.9 x the steady speed,
 *  3. does this again whenever the network changes, and every [RatePolicy.REMEASURE_INTERVAL_MS] at
 *     a quiet moment.
 * A failed measurement keeps the last known value; with none known the rate is 0 (not scheduled),
 * so a failure can never slow the internet down. The shared upload rate is always set to 0.
 *
 * The test downloads through this app's own sockets, which are outside the tunnel in the production
 * settings (the app itself is always excluded), so it sees the real link and the pacer does not
 * limit it. Everything is logged to the Logs tab (category SYSTEM, lines start with "auto speed:").
 */
@Singleton
class AutoRateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: VpnController,
    private val engine: TrafficEngine,
    private val settingsRepository: SettingsRepository,
    private val repository: CalibrationRepository,
    private val logger: Logger,
    @AppScope private val scope: CoroutineScope,
) {
    private val speedTest = LinkSpeedTest()

    private data class NetInfo(val type: NetworkType, val handle: Long)

    /** Call once from Application.onCreate. */
    fun start() {
        scope.launch {
            controller.status
                .map { it.state == TunnelState.RUNNING || it.state == TunnelState.PAUSED }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) watchNetworks() }
        }
    }

    private suspend fun watchNetworks() {
        try {
            if (!productionSettings()) {
                logger.i(LogCategory.SYSTEM, "auto speed: off (not the all-apps mode with Priority Control)")
                return
            }
            networkChanges().distinctUntilChanged().collectLatest { net -> onNetwork(net) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.e(LogCategory.ERROR, "auto speed: stopped by an error: ${t.message}")
        }
    }

    private suspend fun productionSettings(): Boolean {
        val s = settingsRepository.current()
        return s.selectionMode == SelectionMode.ALL_EXCEPT && s.schedMode == SchedMode.PRIORITY_PACER
    }

    private suspend fun onNetwork(net: NetInfo) {
        if (!productionSettings()) return // the settings were changed in Test tools meanwhile
        val key = RatePolicy.networkKey(net.type)
        if (key == null) {
            logger.i(LogCategory.SYSTEM, "auto speed: network ${net.type} is not measured")
            return
        }
        val known = repository.get(key)?.bytesPerSec
        apply(RatePolicy.choose(measured = null, known = known), "network $key, known value")
        delay(RatePolicy.CHANGE_DEBOUNCE_MS)
        while (true) {
            measureAndApply(key)
            delay(RatePolicy.REMEASURE_INTERVAL_MS)
            while (!RatePolicy.isQuiet(engine.statsFlow.value.liveDownloadBps)) {
                delay(RatePolicy.BUSY_RETRY_MS)
            }
        }
    }

    private suspend fun measureAndApply(key: String) {
        if (!productionSettings()) return
        val measured: Long? = try {
            speedTest.measureDownload()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.w(LogCategory.SYSTEM, "auto speed: measuring failed (${t.javaClass.simpleName}: ${t.message})")
            null
        }
        val known = repository.get(key)?.bytesPerSec
        if (RatePolicy.isUsable(measured)) {
            repository.put(key, CalibrationEntry(measured!!, System.currentTimeMillis()))
        }
        val rate = RatePolicy.choose(measured, known)
        val how = when {
            RatePolicy.isUsable(measured) -> "measured ${mbps(measured!!)} (0.9 x speed)"
            rate > 0 -> "measuring failed, kept the last known value"
            else -> "measuring failed, no known value: no shaping"
        }
        apply(rate, "network $key, $how")
    }

    private suspend fun apply(rate: Long, why: String) {
        settingsRepository.updateSettings {
            if (it.schedRateDownBytesPerSec == rate && it.schedRateUpBytesPerSec == 0L) {
                it
            } else {
                it.copy(schedRateDownBytesPerSec = rate, schedRateUpBytesPerSec = 0L)
            }
        }
        logger.i(LogCategory.SYSTEM, "auto speed: shared download ${if (rate > 0) mbps(rate) else "0 (not scheduled)"}; $why")
    }

    private fun mbps(bytesPerSec: Long): String =
        String.format(Locale.US, "%.1f Mbps", bytesPerSec * 8 / 1_000_000.0)

    /** The default network of THIS app (the real Wi-Fi or mobile link, because the app is outside the tunnel). */
    private fun networkChanges(): Flow<NetInfo> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
                    else -> NetworkType.OTHER
                }
                trySend(NetInfo(type, network.networkHandle))
            }

            override fun onLost(network: Network) {
                trySend(NetInfo(NetworkType.NONE, 0L))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
}
