package com.github.nikola352.executor

sealed class ExecutorException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when a machine could not be provisioned (acquire failed). The command never ran. */
class ProvisioningException(message: String, cause: Throwable? = null) : ExecutorException(message, cause)

/** Thrown when the executor failed mid-command (the machine was up but the Docker API errored). */
class ExecutionException(message: String, cause: Throwable? = null) : ExecutorException(message, cause)
