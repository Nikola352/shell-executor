package com.github.nikola352.executor.docker

import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.ProvisioningException
import com.github.nikola352.executor.withExecutor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("docker")
class DockerExecutorProviderIT {

    private val defaultConfig = DockerConfig(
        host = "unix:///var/run/docker.sock",
        tlsVerify = false,
        certPath = null,
    )

    private val defaultRequirements = ResourceRequirements(cpuCount = 1, memoryMb = 256)

    private fun buildDockerClient(host: String = defaultConfig.host) =
        DockerClientImpl.getInstance(
            DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .withDockerTlsVerify(false)
                .build(),
            ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(host))
                .build(),
        )

    @Test
    fun `acquire returns executor with running container`() = runTest {
        val provider = DockerExecutorProvider(defaultConfig)
        val executor = provider.acquire(defaultRequirements)
        try {
            val containerId = (executor as DockerExecutor).containerId
            val info = buildDockerClient().inspectContainerCmd(containerId).exec()
            assertTrue(info.state?.running ?: false, "container should be running")
        } finally {
            provider.release(executor)
        }
    }

    @Test
    fun `release removes container`() = runTest {
        val provider = DockerExecutorProvider(defaultConfig)
        val executor = provider.acquire(defaultRequirements)
        val containerId = (executor as DockerExecutor).containerId

        provider.release(executor)

        assertFailsWith<NotFoundException> {
            buildDockerClient().inspectContainerCmd(containerId).exec()
        }
    }

    @Test
    fun `withExecutor echo hello returns exit 0 and stdout`() = runTest {
        val provider = DockerExecutorProvider(defaultConfig)

        val result = provider.withExecutor(defaultRequirements) { it.execute("echo hello") }

        assertEquals(0, result.exitCode)
        assertEquals("hello\n", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `withExecutor exit 1 returns exit code 1`() = runTest {
        val provider = DockerExecutorProvider(defaultConfig)

        val result = provider.withExecutor(defaultRequirements) { it.execute("exit 1") }

        assertEquals(1, result.exitCode)
    }

    @Test
    fun `withExecutor stderr echo captures stderr`() = runTest {
        val provider = DockerExecutorProvider(defaultConfig)

        val result = provider.withExecutor(defaultRequirements) { it.execute(">&2 echo err") }

        assertEquals("err\n", result.stderr)
    }

    @Test
    fun `bad Docker host causes acquire to throw ProvisioningException`() = runTest {
        val provider = DockerExecutorProvider(
            DockerConfig(host = "tcp://127.0.0.1:9999", tlsVerify = false, certPath = null)
        )

        assertFailsWith<ProvisioningException> {
            provider.acquire(defaultRequirements)
        }
    }

    @Test
    fun `container resource limits match ResourceRequirements`() = runTest {
        val requirements = ResourceRequirements(cpuCount = 2, memoryMb = 128)
        val provider = DockerExecutorProvider(defaultConfig)

        provider.withExecutor(requirements) { executor ->
            val containerId = (executor as DockerExecutor).containerId
            val info = buildDockerClient().inspectContainerCmd(containerId).exec()
            val hostConfig = info.hostConfig
            assertEquals(2 * 1_000_000_000L, hostConfig?.nanoCPUs)
            assertEquals(128 * 1024L * 1024L, hostConfig?.memory)
        }
    }
}
