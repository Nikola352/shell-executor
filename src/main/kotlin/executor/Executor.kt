package com.github.nikola352.executor

/**
 * Represents a provisioned remote machine that is ready to execute shell commands.
 *
 * An [Executor] is acquired through [ExecutorProvider.acquire] and is guaranteed to be
 * exclusively available to the caller for the duration of its use. The caller is responsible
 * for releasing it via [ExecutorProvider.release] when done — prefer [ExecutorProvider.withExecutor]
 * to ensure release even on failure.
 */
interface Executor {
    /**
     * Executes the given shell [command] on the remote machine and returns the result.
     *
     * The command runs to completion before returning. Both stdout and stderr are captured
     * and available in the returned [ExecutionResult], along with the exit code.
     */
    suspend fun execute(command: String): ExecutionResult
}
