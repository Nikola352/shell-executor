package com.github.nikola352

import com.github.nikola352.execution.model.Execution
import com.github.nikola352.execution.model.ExecutionStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    private fun testApp(block: suspend (HttpClient) -> Unit) = testApplication {
        environment {
            config = MapApplicationConfig(
                "postgres.url" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "postgres.driver" to "org.h2.Driver",
                "postgres.user" to "sa",
                "postgres.password" to "",
                "executor.type" to "stub",
                "docker.host" to "unix:///var/run/docker.sock",
                "docker.tls-verify" to "false",
                "docker.cert-path" to "",
            )
        }
        application {
            module()
        }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        block(client)
    }

    @Test
    fun `POST then GET full lifecycle returns FINISHED`() = testApp { client ->
        val response = client.post("/executions") {
            contentType(ContentType.Application.Json)
            setBody("""{"command":"echo hello","resources":{"cpuCount":1,"memoryMb":512}}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val id = response.body<Map<String, Int>>()["id"]!!

        val exec = pollUntilDone(client, id)
        assertEquals(ExecutionStatus.FINISHED, exec.status)
    }

    @Test
    fun `GET non-existent id returns 404`() = testApp { client ->
        val response = client.get("/executions/99999")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST with blank command returns 400`() = testApp { client ->
        val response = client.post("/executions") {
            contentType(ContentType.Application.Json)
            setBody("""{"command":"  ","resources":{"cpuCount":1,"memoryMb":512}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST with idempotency key twice returns same id and 201 on second call`() = testApp { client ->
        val key = UUID.randomUUID().toString()

        val first = client.post("/executions") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", key)
            setBody("""{"command":"echo hello","resources":{"cpuCount":1,"memoryMb":512}}""")
        }
        assertEquals(HttpStatusCode.Created, first.status)
        val firstId = first.body<Map<String, Int>>()["id"]!!

        val second = client.post("/executions") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", key)
            setBody("""{"command":"echo hello","resources":{"cpuCount":1,"memoryMb":512}}""")
        }
        assertEquals(HttpStatusCode.Created, second.status)
        val secondId = second.body<Map<String, Int>>()["id"]!!

        assertEquals(firstId, secondId)
    }

    @Test
    fun `POST with invalid idempotency key UUID returns 400`() = testApp { client ->
        val response = client.post("/executions") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "not-a-uuid")
            setBody("""{"command":"echo hello","resources":{"cpuCount":1,"memoryMb":512}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private suspend fun pollUntilDone(client: HttpClient, id: Int): Execution {
        repeat(20) {
            val exec = client.get("/executions/$id").body<Execution>()
            if (exec.status !in setOf(ExecutionStatus.QUEUED, ExecutionStatus.IN_PROGRESS)) return exec
            delay(50)
        }
        error("Execution $id did not complete")
    }
}
