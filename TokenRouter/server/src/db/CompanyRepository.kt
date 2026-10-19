package com.marlin.tokenrouter.server.db

import com.marlin.tokenrouter.model.ApiKey
import com.marlin.tokenrouter.model.Company
import com.marlin.tokenrouter.model.User
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun allCompanies(full: Boolean): List<Company> = transaction {
    Companies.selectAll().map { row ->
        val companyId = row[Companies.id]
        toCompany(row[Companies.id], row[Companies.name], row[Companies.prefix],
            row[Companies.tokenLimit], row[Companies.tokensSpent], companyId, full)
    }
}

fun findCompany(id: Long, full: Boolean): Company? = transaction {
    Companies.selectAll().firstOrNull { it[Companies.id] == id }?.let { row ->
        val companyId = row[Companies.id]
        toCompany(companyId, row[Companies.name], row[Companies.prefix],
            row[Companies.tokenLimit], row[Companies.tokensSpent], companyId, full)
    }
}

private fun toCompany(
    id: Long,
    name: String,
    prefix: String,
    tokenLimit: Long,
    tokensSpent: Long,
    companyId: Long,
    full: Boolean,
): Company {
    val users = if (full) {
        Users.selectAll()
            .filter { it[Users.companyId] == companyId }
            .map {
                User(
                    id = it[Users.id],
                    companyId = companyId,
                    login = it[Users.login],
                    name = it[Users.name],
                )
            }
    } else emptyList()

    val keys = if (full) {
        ApiKeys.selectAll()
            .filter { it[ApiKeys.companyId] == companyId }
            .map {
                ApiKey(
                    key = it[ApiKeys.key],
                    companyId = companyId,
                    userId = it[ApiKeys.userId],
                    active = it[ApiKeys.active],
                )
            }
    } else emptyList()

    return Company(
        id = id,
        name = name,
        prefix = prefix,
        tokenLimit = tokenLimit,
        tokensSpent = tokensSpent,
        validApiKeys = keys,
        validLogins = users,
    )
}
