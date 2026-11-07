package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val companyId: Long,
    val login: String,
    val name: String,
)
