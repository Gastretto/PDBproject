package com.marlin.tokenrouter.server.routes

import com.marlin.tokenrouter.model.UsageEvent
import com.marlin.tokenrouter.server.postgres.findCompany
import com.marlin.tokenrouter.server.influx.Influx
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

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

    post("/demo/influx/usage") {
        val body = call.receiveText()
        val event = runCatching { json.decodeFromString<UsageEvent>(body) }.getOrNull()
            ?: randomUsageEvent()

        runCatching { Influx.writeUsage(event) }
            .onFailure { e ->
                call.respondText("influx write failed: ${e.message}", status = HttpStatusCode.BadGateway)
                return@post
            }

        call.respondText(
            json.encodeToString(event),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.Created,
        )
    }

    get("/demo/influx/usage") {
        val range = call.request.queryParameters["range"] ?: "-1h"
        runCatching { Influx.usageSummary(range) }
            .onSuccess { summary ->
                call.respondText(
                    json.encodeToString(summary),
                    contentType = ContentType.Application.Json,
                )
            }
            .onFailure { e ->
                call.respondText("influx query failed: ${e.message}", status = HttpStatusCode.BadGateway)
            }
    }

    get("/demo/influx/ping") {
        val ok = Influx.ping()
        call.respondText(
            """{"influx": ${if (ok) "\"up\"" else "\"down\""}}""",
            contentType = ContentType.Application.Json,
            status = if (ok) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
        )
    }
}

private fun randomUsageEvent(): UsageEvent {
    val userId = if (kotlin.random.Random.nextBoolean()) 1L else 2L
    return UsageEvent(
        companyId = 1,
        userId = userId,
        promptTokens = kotlin.random.Random.nextInt(50, 2000),
        completionTokens = kotlin.random.Random.nextInt(50, 2000),
        timestampMs = System.currentTimeMillis(),
    )
}
