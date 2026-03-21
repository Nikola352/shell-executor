package com.github.nikola352.executor.docker

/**
 * Configuration for connecting to a Docker daemon.
 *
 * @property host Docker daemon URI (e.g. `unix:///var/run/docker.sock` or `tcp://host:2376`)
 * @property tlsVerify whether to verify the server's TLS certificate
 * @property certPath path to the directory containing TLS certs; `null` disables TLS
 */
data class DockerConfig(
    val host: String,
    val tlsVerify: Boolean,
    val certPath: String?,
)
