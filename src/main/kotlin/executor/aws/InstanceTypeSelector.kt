package com.github.nikola352.executor.aws

import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.ProvisioningException

/**
 * Selects the cheapest EC2 instance type that satisfies [ResourceRequirements].
 */
object InstanceTypeSelector {

    // Ordered cheapest-first. t3 family preferred (burstable, cost-optimized for general workloads).
    // Memory values are in MiB. Larger families (m5) serve as fallback for high-memory requirements.
    private val INSTANCE_TYPES = listOf(
        InstanceTypeSpec("t3.nano", vcpu = 2, memoryMb = 512),
        InstanceTypeSpec("t3.micro", vcpu = 2, memoryMb = 1_024),
        InstanceTypeSpec("t3.small", vcpu = 2, memoryMb = 2_048),
        InstanceTypeSpec("t3.medium", vcpu = 2, memoryMb = 4_096),
        InstanceTypeSpec("t3.large", vcpu = 2, memoryMb = 8_192),
        InstanceTypeSpec("t3.xlarge", vcpu = 4, memoryMb = 16_384),
        InstanceTypeSpec("t3.2xlarge", vcpu = 8, memoryMb = 32_768),
        // m5 fallback for requirements that exceed t3.2xlarge
        InstanceTypeSpec("m5.xlarge", vcpu = 4, memoryMb = 16_384),
        InstanceTypeSpec("m5.2xlarge", vcpu = 8, memoryMb = 32_768),
        InstanceTypeSpec("m5.4xlarge", vcpu = 16, memoryMb = 65_536),
    )

    /**
     * Returns the name of the cheapest instance type satisfying [requirements].
     *
     * @throws ProvisioningException if no supported type meets the requirements
     */
    fun select(requirements: ResourceRequirements): String = INSTANCE_TYPES
        .firstOrNull { it.vcpu >= requirements.cpuCount && it.memoryMb >= requirements.memoryMb }?.name
        ?: throw ProvisioningException(
            "No supported instance type for requirements: ${requirements.cpuCount} vCPU, ${requirements.memoryMb} MiB"
        )
}
