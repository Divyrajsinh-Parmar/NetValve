package dev.netvalve

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.netvalve.service.BatteryOptimizations
import dev.netvalve.service.VpnController
import dev.netvalve.ui.navigation.AppActions
import dev.netvalve.ui.navigation.NetValveNavGraph
import dev.netvalve.ui.setup.PermissionSnapshot
import dev.netvalve.ui.setup.SetupActions
import dev.netvalve.ui.setup.SetupGuide
import dev.netvalve.ui.setup.SetupGuideScreen
import dev.netvalve.ui.setup.SetupPermission
import dev.netvalve.ui.theme.NetValveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity Compose host. It owns the pieces that legitimately need an
 * Activity — the VPN-consent result contract, the notifications permission, and
 * intent launches — and passes them into the Compose tree as [AppActions] so the
 * screens/ViewModels stay free of Activity plumbing.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var controller: VpnController

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) controller.start()
        }

    // Used by the first-run guide: the consent is only collected here, the tunnel is NOT started.
    private val setupConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* onResume re-checks */ }

    private val notificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* onResume re-checks */ }

    // Counts every return to the screen (after a system dialog or a Settings page) so the first-run
    // guide reads the permissions from the phone again.
    private val resumeTick = mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick.value = resumeTick.value + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NetValveTheme {
                val tick by resumeTick
                // Read live from the phone every time the app comes back to the front: nothing is
                // remembered, so an allowed permission is never asked again.
                val snapshot = remember(tick) { readPermissions() }
                var skipped by remember { mutableStateOf(setOf<SetupPermission>()) }
                val step = SetupGuide.nextStep(snapshot, skipped)
                if (step != null) {
                    SetupGuideScreen(
                        snapshot = snapshot,
                        current = step,
                        onSkip = { skipped = skipped + it },
                        actions = remember { buildSetupActions() },
                    )
                } else {
                    val actions = remember { buildActions() }
                    NetValveNavGraph(actions = actions)
                }
            }
        }
    }

    private fun readPermissions() = PermissionSnapshot(
        notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
        vpn = controller.consentIntent() == null,
        battery = BatteryOptimizations.isIgnoringBatteryOptimizations(this),
    )

    private fun buildSetupActions() = SetupActions(
        allow = { permission -> requestPermission(permission) },
        openAppSettings = {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
                )
            }
        },
    )

    /** Shows the system dialog or Settings page for one permission of the first-run guide. */
    private fun requestPermission(permission: SetupPermission) {
        when (permission) {
            SetupPermission.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationsPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            SetupPermission.VPN -> {
                val consent = controller.consentIntent()
                if (consent != null) setupConsentLauncher.launch(consent)
            }
            SetupPermission.BATTERY -> {
                runCatching { startActivity(BatteryOptimizations.requestExemptionIntent(this)) }
            }
        }
    }

    private fun buildActions() = AppActions(
        toggleVpn = { enable ->
            if (enable) {
                val consent = controller.consentIntent()
                if (consent != null) consentLauncher.launch(consent) else controller.start()
            } else {
                controller.stop()
            }
        },
        restartTunnel = { controller.restart() },
        requestUsageAccess = {
            runCatching {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        requestBatteryExemption = {
            runCatching { startActivity(BatteryOptimizations.requestExemptionIntent(this)) }
        },
        openVendorSettings = {
            BatteryOptimizations.vendorHint(this)?.settingsIntent?.let { runCatching { startActivity(it) } }
        },
        exportLogs = { text -> shareText(text) },
    )

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NetValve logs")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(send, "Export logs")) }
    }
}
