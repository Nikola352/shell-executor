package com.github.nikola352.executor.aws

import com.github.nikola352.executor.ExecutionException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.apache.sshd.client.channel.ChannelExec
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.future.DefaultOpenFuture
import org.apache.sshd.client.session.ClientSession
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AwsExecutorTest {
    private val session = mockk<ClientSession>()
    private val channel = mockk<ChannelExec>(relaxed = true)

    private val openFuture = DefaultOpenFuture("test", null).also { it.setOpened() }

    private val stdoutSlot = slot<OutputStream>()
    private val stderrSlot = slot<OutputStream>()

    private val executor = AwsExecutor(session, "i-12345")

    private fun setupChannel(stdout: String = "", stderr: String = "", exitStatus: Int? = 0) {
        every { session.createExecChannel(any()) } returns channel
        every { channel.open() } returns openFuture
        every { channel.setOut(capture(stdoutSlot)) } just Runs
        every { channel.setErr(capture(stderrSlot)) } just Runs
        every { channel.waitFor(any<Collection<ClientChannelEvent>>(), any<Long>()) } answers {
            stdoutSlot.captured.write(stdout.toByteArray())
            stderrSlot.captured.write(stderr.toByteArray())
            setOf(ClientChannelEvent.CLOSED)
        }
        every { channel.exitStatus } returns exitStatus
    }

    @Test
    fun `stdout is captured from exec channel`() = runTest {
        setupChannel(stdout = "hello\n")

        val result = executor.execute("echo hello")

        assertEquals("hello\n", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `stderr is captured from exec channel`() = runTest {
        setupChannel(stderr = "error\n")

        val result = executor.execute(">&2 echo error")

        assertEquals("", result.stdout)
        assertEquals("error\n", result.stderr)
    }

    @Test
    fun `exit code is returned`() = runTest {
        setupChannel(exitStatus = 42)

        val result = executor.execute("exit 42")

        assertEquals(42, result.exitCode)
    }

    @Test
    fun `null exit status falls back to -1`() = runTest {
        setupChannel(exitStatus = null)

        val result = executor.execute("exit 0")

        assertEquals(-1, result.exitCode)
    }

    @Test
    fun `SSH exception is wrapped in ExecutionException`() = runTest {
        every { session.createExecChannel(any()) } throws RuntimeException("SSH socket error")

        assertFailsWith<ExecutionException> {
            executor.execute("echo hello")
        }
    }
}
