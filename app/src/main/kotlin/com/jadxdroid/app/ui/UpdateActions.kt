package com.jadxdroid.app.ui

import androidx.compose.runtime.Immutable

/** Callbacks the Update tab raises. The activity builds one instance and remembers it. */
@Immutable
class UpdateActions(
    val onAutoCheck: () -> Unit,
    val onCheck: () -> Unit,
    val onDownload: () -> Unit,
    val onCancel: () -> Unit,
    val onInstall: () -> Unit,
)
