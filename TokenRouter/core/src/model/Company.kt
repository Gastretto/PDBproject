package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class Company(
    val id: Long,
    val name: String,
    val credit_limit: Double,
    val credit_spend: Double,
    val cost_per_token:Double,
    val hasCredits:Boolean, // used for fast access for the proxy
    val validApiKeyIds: List<ApiKey> = emptyList(),
)
