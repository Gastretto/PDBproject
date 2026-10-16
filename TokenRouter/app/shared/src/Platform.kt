package com.marlin.tokenrouter

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
