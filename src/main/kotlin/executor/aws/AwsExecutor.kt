package com.github.nikola352.executor.aws

import com.github.nikola352.executor.ExecutionException
import com.github.nikola352.executor.ExecutionResult
import com.github.nikola352.executor.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import java.io.ByteArrayOutputStream

/**
 * [Executor] that runs commands on a remote EC2 instance via an established SSH session.
 *
 * The [session] is opened by [AwsExecutorProvider.acquire] and closed by [AwsExecutorProvider.release].
 * Each [execute] call opens a new exec channel on the same session.
 *
 * @property session An already-authenticated MINA SSHD [ClientSession]
 * @property instanceId EC2 instance ID
 */
class AwsExecutor(
    internal val session: ClientSession,
    internal val instanceId: String,
) : Executor {

    override suspend fun execute(command: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val stdoutBuf = ByteArrayOutputStream()
            val stderrBuf = ByteArrayOutputStream()

            val channel = session.createExecChannel(command)
            channel.out = stdoutBuf
            channel.err = stderrBuf
            channel.open().verify(30_000L)
            channel.waitFor(setOf(ClientChannelEvent.CLOSED), 0L)

            ExecutionResult(
                exitCode = channel.exitStatus ?: -1,
                stdout = stdoutBuf.toString(Charsets.UTF_8),
                stderr = stderrBuf.toString(Charsets.UTF_8),
            )
        } catch (e: Exception) {
            throw ExecutionException("Command execution failed on EC2 instance $instanceId: ${e.message}", e)
        }
    }
}
