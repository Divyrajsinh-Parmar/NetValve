package dev.netvalve.ui.demo

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.netvalve.demo.DemoReport
import dev.netvalve.demo.DemoRunner
import dev.netvalve.demo.DemoUiState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DemoViewModel @Inject constructor(private val runner: DemoRunner) : ViewModel() {
    val state: StateFlow<DemoUiState> = runner.state

    fun start() = runner.start()
    fun cancel() = runner.cancel()
    fun reportText(): String = DemoReport.text(runner.state.value.progress?.results.orEmpty())
}
