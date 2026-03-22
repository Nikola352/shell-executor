package com.github.nikola352.execution.model

import kotlinx.serialization.Serializable

@Serializable
data class Execution(
    val id: Int,
    val command: String,
    val status: ExecutionStatus,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
)
