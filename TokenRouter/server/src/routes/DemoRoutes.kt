package com.marlin.tokenrouter.server.routes

import com.marlin.tokenrouter.server.db.findCompany
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json

fun Routing.demoRoutes() {
    get("/demo/pg") {
        val company = findCompany(id = 1, full = true)
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
