package com.marlin.tokenrouter.server.postgres

import com.marlin.tokenrouter.model.ApiToken
import com.marlin.tokenrouter.model.Company
import com.marlin.tokenrouter.model.User
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun allCompanies(full: Boolean): List<Company> = transaction {
    Companies.selectAll().map { row -> toCompany(row, full) }
}

fun findCompany(id: Long, full: Boolean): Company? = transaction {
    Companies.selectAll()
        .where { Companies.id eq id }
        .singleOrNull()
        ?.let { row -> toCompany(row, full) }
}

private fun toCompany(row: ResultRow, full: Boolean): Company {
    val companyId = row[Companies.id]

    val users = if (full) {
        Users.selectAll()
            .where { Users.companyId eq companyId }
            .map { user ->
                User(
                    id = user[Users.id],
                    companyId = companyId,
                    login = user[Users.login],
                    name = user[Users.name],
                )
            }
    } else {
        emptyList()
    }

    val apiTokens = if (full) {
        ApiTokens.selectAll()
            .where { ApiTokens.companyId eq companyId }
            .map { token ->
                ApiToken(
                    id = token[ApiTokens.id],
                    companyId = companyId,
                    userId = token[ApiTokens.userId],
                    tokenPrefix = token[ApiTokens.tokenPrefix],
                    revoked = token[ApiTokens.revokedAt] != null,
                )
            }
    } else {
        emptyList()
    }

    return Company(
        id = companyId,
        name = row[Companies.name],
        prefix = row[Companies.prefix],
        creditLimit = row[Companies.creditLimit].toDouble(),
        creditSpend = row[Companies.creditSpend].toDouble(),
        hasCredits = row[Companies.hasCredits],
        apiTokens = apiTokens,
        users = users,
    )
}
