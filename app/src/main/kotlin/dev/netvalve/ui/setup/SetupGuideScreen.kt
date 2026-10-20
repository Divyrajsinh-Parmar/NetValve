package dev.netvalve.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What the guide screen can ask the activity to do. */
class SetupActions(
    /** Shows the system dialog (or settings page) for this permission. */
    val allow: (SetupPermission) -> Unit,
    /** Opens this app's page in the phone's Settings (for a permission the system will not ask for again). */
    val openAppSettings: () -> Unit,
)

private fun title(p: SetupPermission): String = when (p) {
    SetupPermission.NOTIFICATIONS -> "Notifications"
    SetupPermission.VPN -> "Network protection"
    SetupPermission.BATTERY -> "Keep running in the background"
}

private fun explanation(p: SetupPermission): String = when (p) {
    SetupPermission.NOTIFICATIONS ->
        "Android shows a small notification while protection is on, so you can see that it is running."
    SetupPermission.VPN ->
        "NetValve needs your permission to manage your phone's internet connection " +
            "(Android calls this a VPN). Android asks only once."
    SetupPermission.BATTERY ->
        "Allow NetValve to ignore battery saving, so the phone does not stop protection in the background."
}

/**
 * First-run guide: shown instead of the app until every permission is allowed (or, for the two
 * optional ones, skipped after they were tried once). It asks one permission at a time.
 */
@Composable
fun SetupGuideScreen(
    snapshot: PermissionSnapshot,
    current: SetupPermission,
    onSkip: (SetupPermission) -> Unit,
    actions: SetupActions,
) {
    // Remember which steps the person already tried, so "Skip" only appears after one real attempt.
    var attempted by remember { mutableStateOf(setOf<SetupPermission>()) }
    val triedCurrent = current in attempted

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set up NetValve", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Allow these once. NetValve will not ask again for anything that is already allowed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SetupGuide.order.forEach { p ->
                val granted = snapshot.isGranted(p)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (granted) "Allowed" else "Not allowed yet",
                        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title(p),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (p == current) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(title(current), style = MaterialTheme.typography.titleLarge)
            Text(explanation(current), style = MaterialTheme.typography.bodyLarge)

            Button(
                onClick = {
                    attempted = attempted + current
                    actions.allow(current)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Allow", style = MaterialTheme.typography.titleMedium)
            }

            if (triedCurrent && current == SetupPermission.NOTIFICATIONS) {
                OutlinedButton(onClick = actions.openAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open app settings")
                }
            }
            if (triedCurrent && SetupGuide.canSkip(current)) {
                TextButton(onClick = { onSkip(current) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip this one")
                }
            }
        }
    }
}
