package com.marlin.tokenrouter.server.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object Database {
    private const val DEFAULT_URL = "jdbc:postgresql://localhost:5432/tokenrouter"
    private const val DEFAULT_USER = "tokenrouter"
    private const val DEFAULT_PASSWORD = "tokenrouter"

    private const val DEMO_PASSWORD_HASH =
        "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

    private val secureRandom = SecureRandom()

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
            it[creditLimit] = BigDecimal("10.000000")
            it[creditSpend] = BigDecimal.ZERO
        } get Companies.id

        val aliceId = Users.insert {
            it[Users.companyId] = companyId
            it[login] = "alice"
            it[name] = "Alice Novak"
            it[pwdHash] = DEMO_PASSWORD_HASH
        } get Users.id

        val bobId = Users.insert {
            it[Users.companyId] = companyId
            it[login] = "bob"
            it[name] = "Bob Svoboda"
            it[pwdHash] = DEMO_PASSWORD_HASH
        } get Users.id

        val gptId = Models.insert {
            it[codeName] = "gpt-4o-mini"
            it[providerUrl] = "https://api.openai.com/v1"
            it[pricePerMInput] = BigDecimal("0.150000")
            it[pricePerMOutput] = BigDecimal("0.600000")
            it[defaultPriceQuantifier] = BigDecimal("1.2000")
        } get Models.id

        Models.insert {
            it[codeName] = "claude-3-5-sonnet"
            it[providerUrl] = "https://api.anthropic.com/v1"
            it[pricePerMInput] = BigDecimal("3.000000")
            it[pricePerMOutput] = BigDecimal("15.000000")
            it[defaultPriceQuantifier] = BigDecimal("1.2000")
        }

        CompanyModelPrices.insert {
            it[CompanyModelPrices.companyId] = companyId
            it[modelId] = gptId
            it[pricePerMInput] = BigDecimal("0.140000")
            it[pricePerMOutput] = BigDecimal("0.550000")
        }

        val plaintextToken = "acme-${randomHex(32)}"
        ApiTokens.insert {
            it[ApiTokens.companyId] = companyId
            it[userId] = aliceId
            it[tokenHash] = sha256(plaintextToken)
            it[tokenPrefix] = plaintextToken.take(16)
        }

        println(
            "[db] seeded demo company 'acme' " +
                "(id=$companyId, users: alice=$aliceId, bob=$bobId)",
        )
        println("[db] demo API token (shown once): $plaintextToken")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append((byte.toInt() and 0xFF).toString(16).padStart(2, '0'))
            }
        }
    }

    private fun randomHex(length: Int): String {
        require(length % 2 == 0) { "length must be even" }
        val bytes = ByteArray(length / 2)
        secureRandom.nextBytes(bytes)
        return buildString(length) {
            bytes.forEach { byte ->
                append((byte.toInt() and 0xFF).toString(16).padStart(2, '0'))
            }
        }
    }
}
