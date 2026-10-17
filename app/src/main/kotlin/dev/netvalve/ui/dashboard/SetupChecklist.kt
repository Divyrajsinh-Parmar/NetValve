package dev.netvalve.ui.dashboard

/**
 * The "what is still missing?" list on the Home screen. Pure Kotlin (no Android, no Compose) so the
 * rules are tested on a JVM and the screen only draws the result.
 *
 * The order is the order a person should do things in. DONE = fine, TODO = needed before Priority
 * Control can show an effect, WARN = works but not recommended.
 */
enum class StepState { DONE, TODO, WARN }

enum class SetupStepId { TUNNEL, ONLY_SELECTED, APPS, SPEEDS, PRIORITY }

data class SetupStep(
    val id: SetupStepId,
    val title: String,
    val detail: String,
    val state: StepState,
)

object SetupChecklist {

    fun evaluate(
        tunnelRunning: Boolean,
        onlySelectedMode: Boolean,
        selectedAppCount: Int,
        priorityOn: Boolean,
        sharedDownBytesPerSec: Long,
        sharedUpBytesPerSec: Long,
    ): List<SetupStep> {
        val tunnel = if (tunnelRunning) {
            SetupStep(SetupStepId.TUNNEL, "Protection is running", "Traffic of the chosen apps goes through NetValve.", StepState.DONE)
        } else {
            SetupStep(SetupStepId.TUNNEL, "Start protection", "Press \"Start protection\" at the top and allow the VPN request.", StepState.TODO)
        }

        val mode = if (onlySelectedMode) {
            SetupStep(SetupStepId.ONLY_SELECTED, "Only the apps you pick are controlled", "Recommended for tests: everything else is left alone.", StepState.DONE)
        } else {
            SetupStep(SetupStepId.ONLY_SELECTED, "All apps go through the tunnel", "Recommended for tests: turn on \"Control only the apps I pick\" in Tunnel settings.", StepState.WARN)
        }

        // In "all except" mode every app is controlled unless ticked, so there is always something to control.
        val appsReady = !onlySelectedMode || selectedAppCount > 0
        val apps = if (appsReady) {
            SetupStep(SetupStepId.APPS, "Apps are chosen", appsDetail(onlySelectedMode, selectedAppCount), StepState.DONE)
        } else {
            SetupStep(SetupStepId.APPS, "Choose the apps to control", "Open the Apps tab and tick PayProbe (and NetValve itself for the Load test).", StepState.TODO)
        }

        val speeds = when {
            sharedDownBytesPerSec > 0L && sharedUpBytesPerSec > 0L ->
                SetupStep(SetupStepId.SPEEDS, "Shared speed is set", "Download and upload are both limited to the shared speed.", StepState.DONE)
            sharedDownBytesPerSec > 0L || sharedUpBytesPerSec > 0L ->
                SetupStep(SetupStepId.SPEEDS, "Shared speed is set for one direction only", "The other direction is not prioritised. Set both, about 90% of the measured speed.", StepState.WARN)
            else ->
                SetupStep(SetupStepId.SPEEDS, "Set the shared speed", "Enter your measured speed and press \"Use 90%\". Without it Priority Control does nothing.", StepState.TODO)
        }

        val priority = if (priorityOn) {
            SetupStep(SetupStepId.PRIORITY, "Priority Control is ON", "High apps are served first, then Medium, then Low.", StepState.DONE)
        } else {
            SetupStep(SetupStepId.PRIORITY, "Turn Priority Control ON", "Switch it on when you want the with-priority run.", StepState.TODO)
        }

        return listOf(tunnel, mode, apps, speeds, priority)
    }

    fun doneCount(steps: List<SetupStep>): Int = steps.count { it.state == StepState.DONE }

    fun allDone(steps: List<SetupStep>): Boolean = steps.all { it.state == StepState.DONE }

    private fun appsDetail(onlySelectedMode: Boolean, count: Int): String =
        if (onlySelectedMode) "$count app(s) ticked." else "Every app except the ticked ones."
}
