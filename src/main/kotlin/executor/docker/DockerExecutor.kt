package com.github.nikola352.executor.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.github.nikola352.executor.ExecutionException
import com.github.nikola352.executor.ExecutionResult
import com.github.nikola352.executor.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * [Executor] backed by a running Docker container.
 *
 * @property containerId ID of the container in which commands are executed
 */
class DockerExecutor(
    private val dockerClient: DockerClient,
    internal val containerId: String,
) : Executor {

    /** Runs [command] inside the container via `sh -c`, capturing stdout, stderr, and exit code. */
    override suspend fun execute(command: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val execId = dockerClient.execCreateCmd(containerId)
                .withCmd("sh", "-c", command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec()
                .id

            val stdoutBuf = ByteArrayOutputStream()
            val stderrBuf = ByteArrayOutputStream()

            val callback = object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    when (frame.streamType) {
                        StreamType.STDOUT -> stdoutBuf.write(frame.payload)
                        StreamType.STDERR -> stderrBuf.write(frame.payload)
                        else -> {}
                    }
                }
            }

            dockerClient.execStartCmd(execId).exec(callback).awaitCompletion()

            val exitCode = dockerClient.inspectExecCmd(execId).exec().exitCodeLong?.toInt() ?: -1

            ExecutionResult(
                exitCode = exitCode,
                stdout = stdoutBuf.toString(Charsets.UTF_8),
                stderr = stderrBuf.toString(Charsets.UTF_8),
            )
        } catch (e: Exception) {
            throw ExecutionException("Command execution failed in container $containerId: ${e.message}", e)
        }
    }
}
