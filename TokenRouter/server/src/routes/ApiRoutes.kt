package com.marlin.tokenrouter.server.routes

import com.marlin.tokenrouter.server.postgres.allCompanies
import com.marlin.tokenrouter.server.postgres.findCompany
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json

private val json = Json { prettyPrint = false }

fun Routing.apiRoutes() {
    get("/api/companies") {
        call.respondText(
            json.encodeToString(allCompanies(full = false)),
            contentType = ContentType.Application.Json,
        )
    }

    get("/api/companies/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
        val company = id?.let { findCompany(it, full = true) }
        if (company == null) {
            call.respondText("company not found", status = HttpStatusCode.NotFound)
        } else {
            call.respondText(
                json.encodeToString(company),
                contentType = ContentType.Application.Json,
            )
        }
    }
}
