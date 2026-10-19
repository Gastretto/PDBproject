package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class LimitStatus(
    val companyId: Long,
    val tokensSpent: Long,
    val tokenLimit: Long,
    val remaining: Long,
    val hasCredit: Boolean,
)
