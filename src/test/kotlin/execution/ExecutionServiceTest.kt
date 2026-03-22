package com.github.nikola352.execution

import com.github.nikola352.NotFoundException
import com.github.nikola352.execution.api.dto.ExecutionRequest
import com.github.nikola352.execution.model.Execution
import com.github.nikola352.execution.model.ExecutionStatus
import com.github.nikola352.execution.model.ResourceRequirements
import com.github.nikola352.executor.*
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.util.*
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
        coEvery { repository.create(any(), isNull()) } returns 1
        coEvery { executorProvider.acquire(any()) } coAnswers {
            error("should not be called synchronously")
        }

        val id = service.startExecution(request)

        assertEquals(1, id)
    }

    @Test
    fun `startExecution calls updateStatus then updateResult after completion`() = runTest {
        val result = ExecutionResult(exitCode = 0, stdout = "hello\n", stderr = "")
        coEvery { repository.create(any(), isNull()) } returns 42
        coEvery { executorProvider.acquire(any()) } returns executor
        coEvery { executor.execute(any()) } returns result
        coEvery { executorProvider.release(any()) } returns Unit
        coEvery { repository.updateStatus(any(), any()) } returns Unit
        coEvery { repository.updateResult(any(), any()) } returns Unit

        service.startExecution(request)
        testScope.testScheduler.advanceUntilIdle()

        coVerify(ordering = Ordering.ORDERED) {
            repository.updateStatus(42, ExecutionStatus.IN_PROGRESS)
            repository.updateResult(42, result)
        }
    }

    @Test
    fun `startExecution calls updateFailed when acquire throws ProvisioningException`() = runTest {
        coEvery { repository.create(any(), isNull()) } returns 7
        coEvery { executorProvider.acquire(any()) } throws ProvisioningException("no machines available")
        coEvery { repository.updateFailed(any()) } returns Unit

        service.startExecution(request)
        testScope.testScheduler.advanceUntilIdle()

        coVerify { repository.updateFailed(7) }
    }

    @Test
    fun `startExecution calls updateFailed when execute throws ExecutionException`() = runTest {
        coEvery { repository.create(any(), isNull()) } returns 8
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
    fun `startExecution with idempotency key returns existing id without launching coroutine`() = runTest {
        val key = UUID.randomUUID()
        val existing = Execution(id = 5, command = "echo hello", status = ExecutionStatus.FINISHED)
        coEvery { repository.findByIdempotencyKey(key) } returns existing
        coEvery { executorProvider.acquire(any()) } coAnswers {
            error("should not be called for existing execution")
        }

        val id = service.startExecution(request, key)

        assertEquals(5, id)
        coVerify(exactly = 0) { repository.create(any(), any()) }
    }

    @Test
    fun `startExecution with idempotency key creates and returns new id when not found`() = runTest {
        val key = UUID.randomUUID()
        coEvery { repository.findByIdempotencyKey(key) } returns null
        coEvery { repository.create(any(), key) } returns 10
        coEvery { executorProvider.acquire(any()) } returns executor
        coEvery { executor.execute(any()) } returns ExecutionResult(exitCode = 0, stdout = "", stderr = "")
        coEvery { executorProvider.release(any()) } returns Unit
        coEvery { repository.updateStatus(any(), any()) } returns Unit
        coEvery { repository.updateResult(any(), any()) } returns Unit

        val id = service.startExecution(request, key)

        assertEquals(10, id)
    }

    @Test
    fun `startExecution with idempotency key retries find on unique constraint violation`() = runTest {
        val key = UUID.randomUUID()
        val existing = Execution(id = 3, command = "echo hello", status = ExecutionStatus.QUEUED)
        val uniqueViolation = mockkClass(ExposedSQLException::class, relaxed = true)
        every { uniqueViolation.sqlState } returns "23505"
        coEvery { repository.findByIdempotencyKey(key) } returnsMany listOf(null, existing)
        coEvery { repository.create(any(), key) } throws uniqueViolation

        val id = service.startExecution(request, key)

        assertEquals(3, id)
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
