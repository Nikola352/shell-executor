package com.github.nikola352.executor.aws

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.model.*
import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.Executor
import com.github.nikola352.executor.ExecutorProvider
import com.github.nikola352.executor.ProvisioningException
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.keyprovider.FileKeyPairProvider
import java.nio.file.Paths
import java.security.KeyPair

/**
 * [ExecutorProvider] that provisions a new EC2 instance on every [acquire] call
 * and permanently terminates it on [release].
 *
 * Instance type selection is a pure CPU heuristic that chooses the cheapest option that satisfies the requirements.
 *
 * AWS credentials are loaded automatically by the SDK's default credential provider chain
 * from `~/.aws/credentials`, environment variables, or an IAM instance role.
 * No credentials are stored in application config.
 */
class AwsExecutorProvider(private val config: AwsConfig) : ExecutorProvider {
    private val logger = KtorSimpleLogger(this::class.qualifiedName ?: "AwsExecutorProvider")

    private val ec2 = Ec2Client { region = config.region }
    private val sshClient: SshClient = SshClient.setUpDefaultClient().also { it.start() }
    private val privateKey: KeyPair = FileKeyPairProvider(Paths.get(config.pemPath))
        .loadKeys(null)
        .first()

    /**
     * Selects an EC2 instance type to satisfy [requirements], launches a **new** instance,
     * waits for it to be running and reachable, and establishes an authenticated SSH session.
     */
    override suspend fun acquire(requirements: ResourceRequirements): Executor {
        val instanceTypeName = InstanceTypeSelector.select(requirements)
        logger.info("Selected instance type $instanceTypeName for $requirements")

        return withContext(Dispatchers.IO) {
            try {
                val instanceId = launchInstance(instanceTypeName)
                logger.info("Launched EC2 instance $instanceId ($instanceTypeName)")

                val publicIp = waitForRunning(instanceId)
                logger.info("Instance $instanceId is running at $publicIp")

                val session = connectWithRetry(publicIp, instanceId)
                logger.info("SSH session established to instance $instanceId")

                AwsExecutor(session, instanceId)
            } catch (e: ProvisioningException) {
                throw e
            } catch (e: Exception) {
                throw ProvisioningException("Failed to provision EC2 instance: ${e.message}", e)
            }
        }
    }

    /**
     * Closes the SSH session, then terminates the EC2 instance.
     */
    override suspend fun release(executor: Executor) = withContext(Dispatchers.IO) {
        val aws = executor as AwsExecutor
        runCatching { aws.session.close() }
            .onFailure { logger.warn("Failed to close SSH session for ${aws.instanceId}: ${it.message}") }
        runCatching {
            ec2.terminateInstances(TerminateInstancesRequest {
                instanceIds = listOf(aws.instanceId)
            })
        }.onFailure { logger.warn("Failed to terminate instance ${aws.instanceId}: ${it.message}") }
        logger.info("Released EC2 instance ${aws.instanceId}")
    }

    private suspend fun launchInstance(instanceTypeName: String): String {
        val response = ec2.runInstances(RunInstancesRequest {
            imageId = config.amiId
            instanceType = InstanceType.fromValue(instanceTypeName)
            minCount = 1
            maxCount = 1
            keyName = config.keyName
            networkInterfaces = listOf(
                InstanceNetworkInterfaceSpecification {
                    deviceIndex = 0
                    associatePublicIpAddress = true
                    subnetId = config.subnetId
                    groups = listOf(config.securityGroupId)
                }
            )
        })
        return response.instances?.firstOrNull()?.instanceId
            ?: throw ProvisioningException("RunInstances returned no instance ID")
    }

    private suspend fun waitForRunning(instanceId: String): String {
        val deadline = System.currentTimeMillis() + config.instanceTimeoutSeconds * 1_000L
        while (System.currentTimeMillis() < deadline) {
            val instance = ec2.describeInstances(
                DescribeInstancesRequest { instanceIds = listOf(instanceId) }
            ).reservations?.firstOrNull()?.instances?.firstOrNull()

            if (instance?.state?.name == InstanceStateName.Running && instance.publicIpAddress != null) {
                return instance.publicIpAddress!!
            }
            delay(config.sshRetryIntervalMs)
        }
        throw ProvisioningException(
            "Instance $instanceId did not reach running state within ${config.instanceTimeoutSeconds}s"
        )
    }

    private suspend fun connectWithRetry(host: String, instanceId: String): ClientSession {
        val deadline = System.currentTimeMillis() + config.sshTimeoutSeconds * 1_000L
        var lastException: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val session = sshClient
                    .connect(config.sshUser, host, config.sshPort)
                    .verify(10_000L)
                    .session
                session.serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE
                session.addPublicKeyIdentity(privateKey)
                session.auth().verify(10_000L)
                return session
            } catch (e: Exception) {
                lastException = e
                logger.debug("SSH not ready on $instanceId at $host, retrying in ${config.sshRetryIntervalMs}ms: ${e.message}")
                delay(config.sshRetryIntervalMs)
            }
        }
        throw ProvisioningException(
            "SSH on instance $instanceId did not become ready within ${config.sshTimeoutSeconds}s: ${lastException?.message}",
            lastException,
        )
    }
}
