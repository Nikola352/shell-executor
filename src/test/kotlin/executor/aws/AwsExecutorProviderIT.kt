package com.github.nikola352.executor.aws

import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.withExecutor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@Tag("aws")
class AwsExecutorProviderIT {

    private val config = AwsConfig(
        region = System.getenv("AWS_REGION") ?: "eu-central-1",
        amiId = System.getenv("AWS_AMI_ID") ?: error("AWS_AMI_ID env var required"),
        keyName = System.getenv("AWS_KEY_NAME") ?: error("AWS_KEY_NAME env var required"),
        pemPath = System.getenv("AWS_PEM_PATH") ?: error("AWS_PEM_PATH env var required"),
        securityGroupId = System.getenv("AWS_SECURITY_GROUP_ID") ?: error("AWS_SECURITY_GROUP_ID env var required"),
        subnetId = System.getenv("AWS_SUBNET_ID") ?: error("AWS_SUBNET_ID env var required"),
    )

    private val defaultRequirements = ResourceRequirements(cpuCount = 1, memoryMb = 512)

    @Test
    fun `withExecutor echo hello returns exit 0 and stdout`() = runTest(timeout = 10.minutes) {
        val provider = AwsExecutorProvider(config)

        val result = provider.withExecutor(defaultRequirements) { it.execute("echo hello") }

        assertEquals(0, result.exitCode)
        assertEquals("hello\n", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `withExecutor false returns exit code 1`() = runTest(timeout = 10.minutes) {
        val provider = AwsExecutorProvider(config)

        val result = provider.withExecutor(defaultRequirements) { it.execute("false") }

        assertEquals(1, result.exitCode)
    }

    @Test
    fun `withExecutor stderr command captures stderr`() = runTest(timeout = 10.minutes) {
        val provider = AwsExecutorProvider(config)

        val result = provider.withExecutor(defaultRequirements) { it.execute("sh -c '>&2 echo err'") }

        assertEquals("err\n", result.stderr)
    }
}
