package com.github.nikola352.execution

import com.github.nikola352.NotFoundException
import com.github.nikola352.execution.api.dto.ExecutionRequest
import com.github.nikola352.execution.model.Execution
import com.github.nikola352.execution.model.ExecutionStatus
import com.github.nikola352.executor.ExecutorProvider
import com.github.nikola352.executor.withExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExecutionService(
    private val executionRepository: ExecutionRepository,
    private val executorProvider: ExecutorProvider,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun getExecution(id: Int): Execution =
        executionRepository.get(id) ?: throw NotFoundException("Execution with id=$id not found")

    suspend fun startExecution(request: ExecutionRequest): Int {
        val id = executionRepository.create(request.command)

        scope.launch {
            executorProvider.withExecutor(request.resources) { executor ->
                executionRepository.updateStatus(id, ExecutionStatus.IN_PROGRESS)
                val result = executor.execute(request.command)
                executionRepository.updateResult(id, result)
            }
        }

        return id
    }
}
