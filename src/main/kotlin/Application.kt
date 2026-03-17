package com.github.nikola352

import com.github.nikola352.plugins.configureDatabases
import com.github.nikola352.plugins.configureHTTP
import com.github.nikola352.plugins.configureRouting
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureDatabases()
    configureRouting()
}
