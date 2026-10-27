package com.jadxdroid.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadxdroid.app.ui.JadxDroidTheme
import com.jadxdroid.app.ui.MainScreen
import com.jadxdroid.app.ui.UpdateActions
import com.jadxdroid.app.update.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Optional: the foreground service still runs when the notification is hidden.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestNotificationPermission()
        setContent {
            JadxDroidTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
                val updateActions = remember {
                    UpdateActions(
                        onAutoCheck = updateViewModel::checkIfNeeded,
                        onCheck = updateViewModel::checkForUpdate,
                        onDownload = updateViewModel::download,
                        onCancel = updateViewModel::cancelDownload,
                        onInstall = updateViewModel::install,
                    )
                }
                MainScreen(
                    decompile = state,
                    update = updateState,
                    onPick = viewModel::start,
                    onCancel = viewModel::cancel,
                    updateActions = updateActions,
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        // POST_NOTIFICATIONS only exists on API 33+; Android 12/12L show foreground notifications without it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
