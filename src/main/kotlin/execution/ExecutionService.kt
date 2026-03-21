package com.github.nikola352.execution

import com.github.nikola352.NotFoundException
import com.github.nikola352.execution.api.dto.ExecutionRequest
import com.github.nikola352.execution.model.Execution

class ExecutionService(
    private val executionRepository: ExecutionRepository,
) {
    suspend fun getExecution(id: Int): Execution =
        executionRepository.get(id) ?: throw NotFoundException("Execution with id=$id not found")

    suspend fun startExecution(request: ExecutionRequest): Int {
        val id = executionRepository.create(request.command)
        return id
    }
}
