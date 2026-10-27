package com.jadxdroid.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jadxdroid.app.Phase
import com.jadxdroid.app.R
import com.jadxdroid.app.UiState
import com.jadxdroid.app.update.UpdateUiState

private val busyPhases = setOf(Phase.QUEUED, Phase.COPYING, Phase.DECOMPILING, Phase.EXPORTING)
private val tabTitles = listOf(R.string.tab_decompile, R.string.tab_update)

@Composable
fun MainScreen(
    decompile: UiState,
    update: UpdateUiState,
    onPick: (Uri, Boolean) -> Unit,
    onCancel: () -> Unit,
    updateActions: UpdateActions,
) {
    // rememberSaveable keeps the selected tab and the toggle across rotation, process recreation and tab switches.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var skipResources by rememberSaveable { mutableStateOf(false) }
    // Scaffold applies the system-bar insets, so content stays clear of bars while drawing edge-to-edge.
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(title)) })
                }
            }
            if (tab == 0) {
                DecompileTab(decompile, skipResources, { skipResources = it }, onPick, onCancel)
            } else {
                UpdateScreen(update, updateActions)
            }
        }
    }
}

@Composable
private fun DecompileTab(
    state: UiState,
    skipResources: Boolean,
    onSkipChange: (Boolean) -> Unit,
    onPick: (Uri, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPick(uri, skipResources)
    }
    val busy = state.phase in busyPhases

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.option_skip_resources), modifier = Modifier.weight(1f))
            Switch(checked = skipResources, onCheckedChange = onSkipChange, enabled = !busy)
        }
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_pick))
        }
        StatusSection(state = state, busy = busy, onCancel = onCancel)
    }
}

@Composable
private fun StatusSection(state: UiState, busy: Boolean, onCancel: () -> Unit) {
    Text(stringResource(phaseLabel(state.phase)), style = MaterialTheme.typography.titleMedium)
    state.fileName?.let { Text(stringResource(R.string.file_label, it)) }
    if (busy) {
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.progress_format, state.done, state.total))
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
    state.resultPath?.let { Text(stringResource(R.string.result_path, it)) }
    if (state.phase == Phase.DONE) {
        Text(stringResource(R.string.result_counts, state.errors, state.warnings))
    }
    state.failure?.let { failure ->
        Text(stringResource(R.string.failure_label), style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Text(failure, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@StringRes
private fun phaseLabel(phase: Phase): Int = when (phase) {
    Phase.IDLE -> R.string.phase_idle
    Phase.QUEUED -> R.string.phase_queued
    Phase.COPYING -> R.string.phase_copying
    Phase.DECOMPILING -> R.string.phase_decompiling
    Phase.EXPORTING -> R.string.phase_exporting
    Phase.DONE -> R.string.phase_done
    Phase.FAILED -> R.string.phase_failed
    Phase.CANCELLED -> R.string.phase_cancelled
}
