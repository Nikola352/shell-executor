package com.github.nikola352.executor.aws

data class InstanceTypeSpec(
    val name: String,
    val vcpu: Int,
    val memoryMb: Int,
)
