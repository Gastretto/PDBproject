package com.marlin.tokenrouter.server.db

import com.marlin.tokenrouter.model.ApiKey
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object Database {
    private const val DEFAULT_URL = "jdbc:postgresql://localhost:5432/tokenrouter"
    private const val DEFAULT_USER = "tokenrouter"
    private const val DEFAULT_PASSWORD = "tokenrouter"

    lateinit var dataSource: HikariDataSource
        private set

    fun init() {
        val url = System.getenv("JDBC_DATABASE_URL") ?: DEFAULT_URL
        val user = System.getenv("JDBC_DATABASE_USER") ?: DEFAULT_USER
        val password = System.getenv("JDBC_DATABASE_PASSWORD") ?: DEFAULT_PASSWORD

        val config = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 5
        }
        dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        seedIfEmpty()
    }

    private fun seedIfEmpty() = transaction {
        if (Companies.selectAll().count() > 0) return@transaction

        val companyId = Companies.insert {
            it[name] = "Acme"
            it[prefix] = "acme"
            it[tokenLimit] = 10_000
            it[tokensSpent] = 0
        } get Companies.id

        val aliceId = Users.insert {
            it[Users.companyId] = companyId
            it[login] = "alice"
            it[name] = "Alice Novak"
        } get Users.id

        val bobId = Users.insert {
            it[Users.companyId] = companyId
            it[login] = "bob"
            it[name] = "Bob Svoboda"
        } get Users.id

        ApiKeys.insert {
            it[key] = ApiKey.generate("acme", companyId, aliceId).key
            it[ApiKeys.companyId] = companyId
            it[userId] = aliceId
            it[active] = true
        }

        println("[db] seeded demo company 'acme' (id=$companyId, users: alice=$aliceId, bob=$bobId)")
    }
}
