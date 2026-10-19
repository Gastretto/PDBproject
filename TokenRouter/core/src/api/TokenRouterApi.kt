package com.marlin.tokenrouter.api

import com.marlin.tokenrouter.model.Company
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json

class TokenRouterApi(
    private val baseUrl: String = "http://localhost:8080",
) {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCompanies(): List<Company> =
        json.decodeFromString(client.get("$baseUrl/api/companies").body())

    suspend fun getCompany(id: Long): Company =
        json.decodeFromString(client.get("$baseUrl/api/companies/$id").body())
}
