package com.github.nikola352.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases() {
    val url = environment.config.property("postgres.url").getString()
    val user = environment.config.property("postgres.user").getString()
    val password = environment.config.property("postgres.password").getString()
    val driver = environment.config.property("postgres.driver").getString()

    Database.connect(
        url = url,
        driver = driver,
        user = user,
        password = password,
    )
}
