package com.github.nikola352.executor

data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
