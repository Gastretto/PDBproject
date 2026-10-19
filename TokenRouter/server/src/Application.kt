package com.marlin.tokenrouter.server

import com.marlin.tokenrouter.server.postgres.Database
import com.marlin.tokenrouter.server.influx.Influx
import com.marlin.tokenrouter.server.routes.apiRoutes
import com.marlin.tokenrouter.server.routes.demoRoutes
import com.marlin.tokenrouter.sayHello
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    Database.init()
    if (Influx.ping()) {
        println("[influx] connected to ${System.getenv("INFLUX_URL") ?: "http://localhost:8086"}")
    } else {
        println("[influx] WARNING: not reachable — /demo/influx/* endpoints will return 502")
    }

    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
        demoRoutes()
        apiRoutes()
    }
}
