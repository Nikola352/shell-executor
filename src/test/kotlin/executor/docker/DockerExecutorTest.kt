package com.github.nikola352.executor.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.*
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.github.nikola352.executor.ExecutionException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DockerExecutorTest {
    private val dockerClient = mockk<DockerClient>()
    private val containerId = "test-container-id"
    private val execId = "test-exec-id"

    private val executor = DockerExecutor(dockerClient, containerId)

    private fun setupExec(vararg frames: Frame, exitCode: Long? = 0L) {
        val execCreateCmd = mockk<ExecCreateCmd>()
        val execCreateResponse = mockk<ExecCreateCmdResponse>()
        val execStartCmd = mockk<ExecStartCmd>()
        val inspectExecCmd = mockk<InspectExecCmd>()
        val inspectExecResponse = mockk<InspectExecResponse>()

        every { dockerClient.execCreateCmd(containerId) } returns execCreateCmd
        every { execCreateCmd.withCmd("sh", "-c", any()) } returns execCreateCmd
        every { execCreateCmd.withAttachStdout(true) } returns execCreateCmd
        every { execCreateCmd.withAttachStderr(true) } returns execCreateCmd
        every { execCreateCmd.exec() } returns execCreateResponse
        every { execCreateResponse.id } returns execId

        every { dockerClient.execStartCmd(execId) } returns execStartCmd
        every { execStartCmd.exec(any()) } answers {
            val callback = firstArg<ResultCallback.Adapter<Frame>>()
            frames.forEach { callback.onNext(it) }
            callback.onComplete()
            callback
        }

        every { dockerClient.inspectExecCmd(execId) } returns inspectExecCmd
        every { inspectExecCmd.exec() } returns inspectExecResponse
        every { inspectExecResponse.exitCodeLong } returns exitCode
    }

    private fun frame(type: StreamType, data: String) = mockk<Frame> {
        every { streamType } returns type
        every { payload } returns data.toByteArray()
    }

    @Test
    fun `stdout frames are captured`() = runTest {
        setupExec(frame(StreamType.STDOUT, "hello\n"))

        val result = executor.execute("echo hello")

        assertEquals("hello\n", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `stderr frames are captured`() = runTest {
        setupExec(frame(StreamType.STDERR, "error\n"))

        val result = executor.execute(">&2 echo error")

        assertEquals("", result.stdout)
        assertEquals("error\n", result.stderr)
    }

    @Test
    fun `RAW frames are ignored`() = runTest {
        setupExec(
            frame(StreamType.RAW, "ignored"),
            frame(StreamType.STDOUT, "out\n"),
        )

        val result = executor.execute("echo out")

        assertEquals("out\n", result.stdout)
    }

    @Test
    fun `null exitCodeLong falls back to -1`() = runTest {
        setupExec(exitCode = null)

        val result = executor.execute("exit 0")

        assertEquals(-1, result.exitCode)
    }

    @Test
    fun `docker-java exception is wrapped in ExecutionException`() = runTest {
        every { dockerClient.execCreateCmd(containerId) } throws RuntimeException("docker socket error")

        assertFailsWith<ExecutionException> {
            executor.execute("echo hello")
        }
    }
}
