package com.github.nikola352.executor

import com.github.nikola352.execution.model.ResourceRequirements

/**
 * Manages the lifecycle of [Executor] instances, each backed by a remote machine.
 *
 * Conceptually, [acquire] provisions a new machine and [release] destroys it. Implementations
 * may optimize this with a pre-warmed pool, but callers should not rely on that.
 *
 * The caller **must** call [release] after each [acquire], or the machine will remain reserved
 * indefinitely. Prefer [withExecutor] to ensure release even if an exception is thrown.
 */
interface ExecutorProvider {

    /**
     * Returns an [Executor] backed by a machine satisfying [requirements], reserved exclusively
     * for the caller. Conceptually provisions a new machine; implementations may use a pool.
     */
    suspend fun acquire(requirements: ResourceRequirements): Executor

    /**
     * Releases the [executor]. Conceptually destroys the underlying machine; implementations
     * may return it to a pool instead. Must be called exactly once per [acquire].
     */
    suspend fun release(executor: Executor)
}

/**
 * Acquires an [Executor] for the given [requirements], runs [block] with it, and releases it
 * afterward - guaranteed even if [block] throws.
 */
suspend fun <T> ExecutorProvider.withExecutor(
    requirements: ResourceRequirements,
    block: suspend (Executor) -> T
): T {
    val executor = acquire(requirements)
    try {
        return block(executor)
    } finally {
        release(executor)
    }
}
