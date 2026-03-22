package com.github.nikola352.execution.api

import com.github.nikola352.ValidationException
import com.github.nikola352.execution.ExecutionService
import com.github.nikola352.execution.api.dto.ExecutionRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.executionRoutes(service: ExecutionService) {
    post("/executions") {
        val idempotencyKey = call.request.headers["Idempotency-Key"]?.let {
            runCatching { UUID.fromString(it) }.getOrElse {
                throw ValidationException("Idempotency-Key must be a valid UUID")
            }
        }
        val request = call.receive<ExecutionRequest>()
        val id = service.startExecution(request, idempotencyKey)
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
    }

    get("/executions/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: throw ValidationException("Path parameter 'id' must be a valid integer")
        call.respond(service.getExecution(id))
    }
}
