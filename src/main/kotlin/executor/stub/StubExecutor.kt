package com.github.nikola352.executor.stub

import com.github.nikola352.executor.ExecutionResult
import com.github.nikola352.executor.Executor

class StubExecutor : Executor {
    override suspend fun execute(command: String) = ExecutionResult(
        0,
        "output",
        "error",
    )
}
