package com.github.nikola352.plugins

import com.github.nikola352.NotFoundException
import com.github.nikola352.ValidationException
import com.github.nikola352.execution.ExecutionRepository
import com.github.nikola352.execution.ExecutionService
import com.github.nikola352.execution.api.dto.ExecutionRequest
import com.github.nikola352.execution.api.executionRoutes
import com.github.nikola352.executor.ExecutorProvider
import com.github.nikola352.executor.docker.DockerConfig
import com.github.nikola352.executor.docker.DockerExecutorProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respondText(cause.message ?: "Not found", status = HttpStatusCode.NotFound)
        }
        exception<ValidationException> { call, cause ->
            call.respondText(cause.message ?: "Bad request", status = HttpStatusCode.BadRequest)
        }
        exception<RequestValidationException> { call, cause ->
            call.respondText(cause.reasons.joinToString(), status = HttpStatusCode.BadRequest)
        }
        exception<BadRequestException> { call, cause ->
            call.respondText(cause.message ?: "Bad request", status = HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    install(RequestValidation) {
        validate<ExecutionRequest> { request ->
            if (request.command.isBlank())
                ValidationResult.Invalid("'command' must not be blank")
            else
                ValidationResult.Valid
        }
    }

    val executionRepository = ExecutionRepository()
    val dockerHost = environment.config.property("docker.host").getString()
    val tlsVerify = environment.config.property("docker.tls-verify").getString().toBoolean()
    val certPath = environment.config.property("docker.cert-path").getString().ifBlank { null }
    val executorProvider: ExecutorProvider = DockerExecutorProvider(DockerConfig(dockerHost, tlsVerify, certPath))
    val executionService = ExecutionService(executionRepository, executorProvider)

    routing {
        executionRoutes(executionService)
    }
}
