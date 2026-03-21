package com.github.nikola352.execution.api.dto

import com.github.nikola352.execution.model.ResourceRequirements
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionRequest(
    val command: String,
    val resources: ResourceRequirements,
)
