package com.marlin.tokenrouter.server

import com.marlin.tokenrouter.server.db.Database
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

    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
        demoRoutes()
        apiRoutes()
    }
}
