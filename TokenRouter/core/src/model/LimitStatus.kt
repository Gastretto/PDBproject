package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class LimitStatus(
    val companyId: Long,
    val creditLimit: Double,
    val creditSpend: Double,
    val remaining: Double,
    val hasCredits: Boolean,
)
