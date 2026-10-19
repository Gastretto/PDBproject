package com.marlin.tokenrouter.server.routes

import com.marlin.tokenrouter.model.ApiKey
import com.marlin.tokenrouter.model.Company
import com.marlin.tokenrouter.model.User
import com.marlin.tokenrouter.server.db.ApiKeys
import com.marlin.tokenrouter.server.db.Companies
import com.marlin.tokenrouter.server.db.Users
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Routing.demoRoutes() {
    get("/demo/pg") {
        val company = transaction {
            val row = Companies.selectAll().firstOrNull() ?: return@transaction null
            val companyId = row[Companies.id]

            val users = Users.selectAll()
                .filter { it[Users.companyId] == companyId }
                .map {
                    User(
                        id = it[Users.id],
                        companyId = companyId,
                        login = it[Users.login],
                        name = it[Users.name],
                    )
                }

            val keys = ApiKeys.selectAll()
                .filter { it[ApiKeys.companyId] == companyId }
                .map {
                    ApiKey(
                        key = it[ApiKeys.key],
                        companyId = companyId,
                        userId = it[ApiKeys.userId],
                        active = it[ApiKeys.active],
                    )
                }

            Company(
                id = companyId,
                name = row[Companies.name],
                prefix = row[Companies.prefix],
                tokenLimit = row[Companies.tokenLimit],
                tokensSpent = row[Companies.tokensSpent],
                validApiKeys = keys,
                validLogins = users,
            )
        }

        if (company == null) {
            call.respondText("no company found", status = HttpStatusCode.NotFound)
        } else {
            call.respondText(
                Json.encodeToString(company),
                contentType = ContentType.Application.Json,
            )
        }
    }
}
