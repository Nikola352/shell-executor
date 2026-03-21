package com.github.nikola352.execution

import com.github.nikola352.NotFoundException
import com.github.nikola352.execution.api.dto.ExecutionRequest
import com.github.nikola352.execution.model.Execution
import com.github.nikola352.execution.model.ExecutionStatus
import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecutionServiceTest {
    private val repository = mockk<ExecutionRepository>()
    private val executorProvider = mockk<ExecutorProvider>()
    private val executor = mockk<Executor>()
    private val testScope = TestScope(StandardTestDispatcher())

    private val service = ExecutionService(
        executionRepository = repository,
        executorProvider = executorProvider,
        scope = testScope,
    )

    private val resources = ResourceRequirements(cpuCount = 1, memoryMb = 512)
    private val request = ExecutionRequest(command = "echo hello", resources = resources)

    @Test
    fun `startExecution returns id immediately without waiting for coroutine`() = runTest {
        coEvery { repository.create(any()) } returns 1
        coEvery { executorProvider.acquire(any()) } coAnswers {
            error("should not be called synchronously")
        }

        val id = service.startExecution(request)

        assertEquals(1, id)
    }

    @Test
    fun `startExecution calls updateStatus then updateResult after completion`() = runTest {
        val result = ExecutionResult(exitCode = 0, stdout = "hello\n", stderr = "")
        coEvery { repository.create(any()) } returns 42
        coEvery { executorProvider.acquire(any()) } returns executor
        coEvery { executor.execute(any()) } returns result
        coEvery { executorProvider.release(any()) } returns Unit
        coEvery { repository.updateStatus(any(), any()) } returns Unit
        coEvery { repository.updateResult(any(), any()) } returns Unit

        service.startExecution(request)
        testScope.testScheduler.advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            repository.updateStatus(42, ExecutionStatus.IN_PROGRESS)
            repository.updateResult(42, result)
        }
    }

    @Test
    fun `startExecution calls updateFailed when acquire throws ProvisioningException`() = runTest {
        coEvery { repository.create(any()) } returns 7
        coEvery { executorProvider.acquire(any()) } throws ProvisioningException("no machines available")
        coEvery { repository.updateFailed(any()) } returns Unit

        service.startExecution(request)
        testScope.testScheduler.advanceUntilIdle()

        coVerify { repository.updateFailed(7) }
    }

    @Test
    fun `startExecution calls updateFailed when execute throws ExecutionException`() = runTest {
        coEvery { repository.create(any()) } returns 8
        coEvery { executorProvider.acquire(any()) } returns executor
        coEvery { executor.execute(any()) } throws ExecutionException("docker error")
        coEvery { executorProvider.release(any()) } returns Unit
        coEvery { repository.updateStatus(any(), any()) } returns Unit
        coEvery { repository.updateFailed(any()) } returns Unit

        service.startExecution(request)
        testScope.testScheduler.advanceUntilIdle()

        coVerify { repository.updateFailed(8) }
    }

    @Test
    fun `getExecution returns result from repository`() = runTest {
        val execution = Execution(id = 1, command = "ls", status = ExecutionStatus.FINISHED)
        coEvery { repository.get(1) } returns execution

        val result = service.getExecution(1)

        assertEquals(execution, result)
    }

    @Test
    fun `getExecution throws NotFoundException when repository returns null`() = runTest {
        coEvery { repository.get(99) } returns null

        assertFailsWith<NotFoundException> {
            service.getExecution(99)
        }
    }
}
