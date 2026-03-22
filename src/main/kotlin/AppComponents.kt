package com.github.nikola352

import com.github.nikola352.execution.ExecutionRepository
import com.github.nikola352.execution.ExecutionService
import com.github.nikola352.executor.ExecutorProvider
import com.github.nikola352.executor.aws.AwsConfig
import com.github.nikola352.executor.aws.AwsExecutorProvider
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

        "aws" -> AwsExecutorProvider(
            AwsConfig(
                region = config.property("aws.region").getString(),
                amiId = config.property("aws.ami-id").getString(),
                keyName = config.property("aws.key-name").getString(),
                pemPath = config.property("aws.pem-path").getString(),
                securityGroupId = config.property("aws.security-group-id").getString(),
                subnetId = config.property("aws.subnet-id").getString(),
                sshUser = config.property("aws.ssh-user").getString(),
                sshPort = config.property("aws.ssh-port").getString().toInt(),
                instanceTimeoutSeconds = config.property("aws.instance-timeout-seconds").getString().toInt(),
                sshTimeoutSeconds = config.property("aws.ssh-timeout-seconds").getString().toInt(),
                sshRetryIntervalMs = config.property("aws.ssh-retry-interval-ms").getString().toLong(),
            )
        )

        "stub" -> StubExecutorProvider()
        else -> error("Unknown executor.type — must be 'docker', 'aws', or 'stub'")
    }

    val executionService = ExecutionService(repository, executorProvider)
}
