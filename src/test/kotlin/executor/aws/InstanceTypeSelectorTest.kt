package com.github.nikola352.executor.aws

import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.ProvisioningException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstanceTypeSelectorTest {

    private fun requirements(cpuCount: Int, memoryMb: Int) =
        ResourceRequirements(cpuCount = cpuCount, memoryMb = memoryMb)

    @Test
    fun `minimal requirements select t3 nano`() {
        assertEquals("t3.nano", InstanceTypeSelector.select(requirements(1, 256)))
    }

    @Test
    fun `exact t3 nano boundary selects t3 nano`() {
        assertEquals("t3.nano", InstanceTypeSelector.select(requirements(2, 512)))
    }

    @Test
    fun `memory just above t3 nano selects t3 micro`() {
        assertEquals("t3.micro", InstanceTypeSelector.select(requirements(1, 513)))
    }

    @Test
    fun `t3 family is preferred when cpu and memory both fit`() {
        // 4 CPU + 4 GiB: t3.xlarge (4 vCPU, 16 GiB) is the first t3 with 4+ vCPU
        assertEquals("t3.xlarge", InstanceTypeSelector.select(requirements(4, 4_096)))
    }

    @Test
    fun `large memory within t3 range selects t3 2xlarge`() {
        assertEquals("t3.2xlarge", InstanceTypeSelector.select(requirements(1, 32_768)))
    }

    @Test
    fun `requirements exceeding all t3 types fall back to m5`() {
        assertEquals("m5.4xlarge", InstanceTypeSelector.select(requirements(16, 65_536)))
    }

    @Test
    fun `cpu requirement exceeding all supported types throws ProvisioningException`() {
        assertFailsWith<ProvisioningException> {
            InstanceTypeSelector.select(requirements(32, 512))
        }
    }

    @Test
    fun `memory requirement exceeding all supported types throws ProvisioningException`() {
        assertFailsWith<ProvisioningException> {
            InstanceTypeSelector.select(requirements(1, 999_999))
        }
    }

    @Test
    fun `exception message includes the unsatisfied requirements`() {
        val ex = assertFailsWith<ProvisioningException> {
            InstanceTypeSelector.select(requirements(64, 1_000_000))
        }
        assert(ex.message!!.contains("64")) { "message should include cpuCount: ${ex.message}" }
        assert(ex.message!!.contains("1000000")) { "message should include memoryMb: ${ex.message}" }
    }
}
