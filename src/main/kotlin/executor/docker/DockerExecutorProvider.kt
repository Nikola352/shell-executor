package com.github.nikola352.executor.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.Executor
import com.github.nikola352.executor.ExecutorProvider
import com.github.nikola352.executor.ProvisioningException
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * [ExecutorProvider] that provisions an isolated Alpine Linux Docker container per [acquire] call
 * and destroys it on [release]. A single shared [DockerClient] is created at construction time.
 */
class DockerExecutorProvider(config: DockerConfig) : ExecutorProvider {
    private val logger = KtorSimpleLogger(this::class.qualifiedName ?: "DockerExecutorProvider")

    private val dockerClient: DockerClient

    init {
        val clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(config.host)
            .withDockerTlsVerify(config.tlsVerify)
            .apply { if (config.certPath != null) withDockerCertPath(config.certPath) }
            .build()

        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(URI.create(config.host))
            .sslConfig(clientConfig.sslConfig)
            .build()

        dockerClient = DockerClientImpl.getInstance(clientConfig, httpClient)
    }

    /** Pulls `alpine:latest`, creates and starts a container with the requested CPU/memory limits. */
    override suspend fun acquire(requirements: ResourceRequirements): Executor = withContext(Dispatchers.IO) {
        try {
            logger.info("Pulling alpine:latest image")
            dockerClient.pullImageCmd("alpine")
                .withTag("latest")
                .exec(PullImageResultCallback())
                .awaitCompletion()

            val hostConfig = HostConfig.newHostConfig()
                .withNanoCPUs(requirements.cpuCount * 1_000_000_000L)
                .withMemory(requirements.memoryMb * 1024L * 1024L)

            val container = dockerClient.createContainerCmd("alpine:latest")
                .withCmd("sh", "-c", "tail -f /dev/null") // keep the container alive
                .withHostConfig(hostConfig)
                .exec()

            dockerClient.startContainerCmd(container.id).exec()
            logger.info("Started container ${container.id}")

            DockerExecutor(dockerClient, container.id)
        } catch (e: Exception) {
            throw ProvisioningException("Failed to provision Docker container: ${e.message}", e)
        }
    }

    /** Stops and removes the container backing [executor]. */
    override suspend fun release(executor: Executor) = withContext(Dispatchers.IO) {
        val containerId = (executor as DockerExecutor).containerId
        logger.info("Stopping container $containerId")
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec()
        } catch (e: Exception) {
            logger.warn("Failed to stop container $containerId (may already be stopped): ${e.message}")
        }
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec()
            logger.info("Removed container $containerId")
        } catch (e: Exception) {
            logger.warn("Failed to remove container $containerId: ${e.message}")
        }
    }
}
