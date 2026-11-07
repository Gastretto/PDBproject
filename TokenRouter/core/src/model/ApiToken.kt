package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiToken(
    val id: Long,
    val companyId: Long,
    val userId: Long,
    val tokenPrefix: String,
    val revoked: Boolean,
)
