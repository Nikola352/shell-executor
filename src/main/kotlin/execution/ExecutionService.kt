package com.github.nikola352.execution

import com.github.nikola352.NotFoundException
import com.github.nikola352.execution.api.dto.ExecutionRequest
import com.github.nikola352.execution.model.Execution
import com.github.nikola352.execution.model.ExecutionStatus
import com.github.nikola352.executor.ExecutorProvider
import com.github.nikola352.executor.withExecutor
import io.ktor.util.logging.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExecutionService(
    private val executionRepository: ExecutionRepository,
    private val executorProvider: ExecutorProvider,

    /*
    Acquiring an executor might contain cpu-bound heuristics or ML operations if it has more complex logic
    than always provisioning, so Dispatchers.Default is idiomatically correct here.
    However, all current implementations are IO-bound, so performance-wise, it would make more sense to use Dispatchers.IO.

    Scope is passed as an optional parameter to make different options possible based on the configuration
    and to make testing simpler.
    */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName ?: "ExecutionService")

    suspend fun getExecution(id: Int): Execution =
        executionRepository.get(id) ?: throw NotFoundException("Execution with id=$id not found")

    suspend fun startExecution(request: ExecutionRequest): Int {
        val id = executionRepository.create(request.command)

        scope.launch {
            try {
                executorProvider.withExecutor(request.resources) { executor ->
                    executionRepository.updateStatus(id, ExecutionStatus.IN_PROGRESS)
                    val result = executor.execute(request.command)
                    executionRepository.updateResult(id, result)
                }
            } catch (e: Exception) {
                logger.error("Execution $id failed: ${e.message}", e)
                executionRepository.updateFailed(id)
            }
        }

        return id
    }
}
