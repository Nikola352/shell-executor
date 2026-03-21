package com.github.nikola352.executor.stub

import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.Executor
import com.github.nikola352.executor.ExecutorProvider
import io.ktor.util.logging.*

class StubExecutorProvider : ExecutorProvider {
    private val logger = KtorSimpleLogger("com.github.nikola352.executor.stub.StubExecutorProvider")

    override suspend fun acquire(requirements: ResourceRequirements): Executor {
        logger.info("Provisioning executor with resources: $requirements")
        return StubExecutor()
    }

    override suspend fun release(executor: Executor) {
        logger.info("Teardown of executor: $executor")
    }
}
