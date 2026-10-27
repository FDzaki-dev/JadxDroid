package com.jadxdroid.app.ui

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jadxdroid.app.R
import com.jadxdroid.app.update.UpdatePhase
import com.jadxdroid.app.update.UpdateUiState

private const val SHA_PREVIEW_CHARS = 12

@Composable
fun UpdateScreen(state: UpdateUiState, actions: UpdateActions) {
    // Opening the tab starts the check by itself; the snapshot card is already on screen while it loads.
    LaunchedEffect(Unit) { actions.onAutoCheck() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.headlineSmall)
        SnapshotCard(state)
        UpdateStatusSection(state, actions)
    }
}

@Composable
private fun SnapshotCard(state: UpdateUiState) {
    val context = LocalContext.current
    val release = state.release
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_snapshot_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.update_installed, state.installed.name, state.installed.code))
            if (release == null) {
                Text(stringResource(R.string.update_snapshot_loading), style = MaterialTheme.typography.bodySmall)
            } else {
                val size = Formatter.formatShortFileSize(context, release.asset.size)
                Text(stringResource(R.string.update_latest, release.versionName, release.publishedDate, size))
                if (release.notes.isNotBlank()) {
                    Text(stringResource(R.string.update_notes_label), style = MaterialTheme.typography.titleSmall)
                    Text(release.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusSection(state: UpdateUiState, actions: UpdateActions) {
    when (state.phase) {
        UpdatePhase.IDLE, UpdatePhase.CHECKING -> LoadingStatus(R.string.update_checking)
        UpdatePhase.UP_TO_DATE -> {
            Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = actions.onCheck) { Text(stringResource(R.string.action_check)) }
        }
        UpdatePhase.AVAILABLE -> {
            Text(stringResource(R.string.update_available), style = MaterialTheme.typography.titleMedium)
            Button(onClick = actions.onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_download))
            }
        }
        UpdatePhase.DOWNLOADING -> DownloadStatus(state, actions.onCancel)
        UpdatePhase.READY -> {
            val sha = state.ready?.sha256?.take(SHA_PREVIEW_CHARS).orEmpty()
            Text(stringResource(R.string.update_ready, sha), style = MaterialTheme.typography.titleMedium)
            Button(onClick = actions.onInstall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_install))
            }
        }
        UpdatePhase.INSTALLING -> LoadingStatus(R.string.update_installing)
        UpdatePhase.CHECK_FAILED -> FailureStatus(
            label = R.string.update_failure_check,
            failure = state.failure,
            retryLabel = R.string.action_check,
            onRetry = actions.onCheck,
        )
        UpdatePhase.DOWNLOAD_FAILED -> FailureStatus(
            label = R.string.update_failure_download,
            failure = state.failure,
            retryLabel = R.string.action_retry_download,
            onRetry = actions.onDownload,
        )
        UpdatePhase.INSTALL_FAILED -> FailureStatus(
            label = R.string.update_failure_install,
            failure = state.failure,
            retryLabel = R.string.action_retry_install,
            onRetry = actions.onInstall,
        )
    }
}

@Composable
private fun LoadingStatus(@StringRes label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DownloadStatus(state: UpdateUiState, onCancel: () -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.titleMedium)
    if (state.total > 0) {
        LinearProgressIndicator(
            progress = { (state.done.toFloat() / state.total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        val done = Formatter.formatShortFileSize(context, state.done)
        val total = Formatter.formatShortFileSize(context, state.total)
        Text(stringResource(R.string.update_bytes, done, total))
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
}

@Composable
private fun FailureStatus(
    @StringRes label: Int,
    failure: String?,
    @StringRes retryLabel: Int,
    onRetry: () -> Unit,
) {
    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
    SelectionContainer {
        Text(failure.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = onRetry) { Text(stringResource(retryLabel)) }
}
