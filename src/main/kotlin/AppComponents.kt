package com.github.nikola352

import com.github.nikola352.execution.ExecutionRepository
import com.github.nikola352.execution.ExecutionService
import com.github.nikola352.executor.ExecutorProvider
import com.github.nikola352.executor.docker.DockerConfig
import com.github.nikola352.executor.docker.DockerExecutorProvider
import com.github.nikola352.executor.stub.StubExecutorProvider
import io.ktor.server.config.*

class AppComponents(config: ApplicationConfig) {
    val repository = ExecutionRepository()

    val executorProvider: ExecutorProvider = when (config.property("executor.type").getString()) {
        "docker" -> DockerExecutorProvider(
            DockerConfig(
                host = config.property("docker.host").getString(),
                tlsVerify = config.property("docker.tls-verify").getString().toBoolean(),
                certPath = config.property("docker.cert-path").getString().ifBlank { null },
            )
        )
        "stub" -> StubExecutorProvider()
        else -> error("Unknown executor.type — must be 'docker' or 'stub'")
    }

    val executionService = ExecutionService(repository, executorProvider)
}
