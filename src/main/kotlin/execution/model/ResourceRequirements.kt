package com.github.nikola352.execution.model

import kotlinx.serialization.Serializable

@Serializable
data class ResourceRequirements(
    val cpuCount: Int,
    val memoryMb: Int,
)
