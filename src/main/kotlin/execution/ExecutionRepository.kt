package com.github.nikola352.execution

import com.github.nikola352.execution.model.Execution
import com.github.nikola352.execution.model.ExecutionStatus
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class ExecutionRepository {
    object Executions : Table() {
        val id = integer("id").autoIncrement()
        val command = text("command")
        val status = enumeration<ExecutionStatus>("status")
        val exitCode = integer("exit_code").nullable()
        val stdout = text("stdout").nullable()
        val stderr = text("stderr").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction {
            SchemaUtils.create(Executions)
        }
    }

    suspend fun get(id: Int): Execution? = dbQuery {
        Executions.selectAll()
            .where { Executions.id eq id }
            .map { Execution(it[Executions.id], it[Executions.command], it[Executions.status]) }
            .singleOrNull()
    }

    suspend fun create(command: String): Int = dbQuery {
        Executions.insert {
            it[this.command] = command
            it[status] = ExecutionStatus.QUEUED
        }[Executions.id]
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
