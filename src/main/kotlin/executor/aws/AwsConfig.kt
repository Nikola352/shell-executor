package com.github.nikola352.executor.aws

/**
 * Configuration for the AWS EC2 executor.
 *
 * AWS credentials (access key ID and secret) are intentionally absent — they are read
 * automatically from `~/.aws/credentials`, environment variables, or the instance IAM role
 * by the AWS SDK's default credential provider chain.
 *
 * @property region AWS region (e.g. "eu-central-1")
 * @property amiId Ubuntu AMI ID for the target region — see AWS_SETUP.md to find the current one
 * @property keyName Name of the EC2 key pair that already exists in your AWS account
 * @property pemPath Absolute path to the downloaded .pem private key file — keep outside the project
 * @property securityGroupId ID of a security group that allows inbound TCP on [sshPort]
 * @property subnetId ID of a public subnet (auto-assign public IP must be enabled)
 * @property sshUser SSH user for the Ubuntu AMI (default: "ubuntu")
 * @property sshPort SSH port (default: 22)
 * @property instanceTimeoutSeconds How long to wait for the EC2 instance to reach the running state
 * @property sshTimeoutSeconds How long to retry SSH connections after the instance is running
 * @property sshRetryIntervalMs Pause between SSH connection attempts and instance state polls
 */
data class AwsConfig(
    val region: String,
    val amiId: String,
    val keyName: String,
    val pemPath: String,
    val securityGroupId: String,
    val subnetId: String,
    val sshUser: String = "ubuntu",
    val sshPort: Int = 22,
    val instanceTimeoutSeconds: Int = 120,
    val sshTimeoutSeconds: Int = 120,
    val sshRetryIntervalMs: Long = 5_000L,
)
