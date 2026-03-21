package com.github.nikola352.execution.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionStatus {
    QUEUED,
    IN_PROGRESS,
    FINISHED,
}
