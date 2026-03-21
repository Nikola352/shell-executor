package com.github.nikola352.execution

import com.github.nikola352.execution.model.Execution
import com.github.nikola352.execution.model.ExecutionStatus
import com.github.nikola352.executor.ExecutionResult
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
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

    suspend fun updateStatus(id: Int, status: ExecutionStatus) {
        dbQuery {
            Executions.update({ Executions.id eq id }) {
                it[this.status] = status
            }
        }
    }

    suspend fun updateResult(id: Int, result: ExecutionResult) {
        dbQuery {
            Executions.update({ Executions.id eq id }) {
                it[status] = ExecutionStatus.FINISHED
                it[exitCode] = result.exitCode
                it[stdout] = result.stdout
                it[stderr] = result.stderr
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
