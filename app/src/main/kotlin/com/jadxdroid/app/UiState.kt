package com.jadxdroid.app

enum class Phase { IDLE, QUEUED, COPYING, DECOMPILING, EXPORTING, DONE, FAILED, CANCELLED }

data class UiState(
    val phase: Phase = Phase.IDLE,
    val fileName: String? = null,
    val done: Int = 0,
    val total: Int = 0,
    val resultPath: String? = null,
    val errors: Int = 0,
    val warnings: Int = 0,
    val failure: String? = null,
)
