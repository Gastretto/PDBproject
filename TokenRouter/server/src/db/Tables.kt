package com.marlin.tokenrouter.server.db

import org.jetbrains.exposed.sql.Table

object Companies : Table("companies") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val prefix = varchar("prefix", 64).uniqueIndex()
    val tokenLimit = long("token_limit")
    val tokensSpent = long("tokens_spent").default(0)

    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val companyId = long("company_id").references(Companies.id)
    val login = varchar("login", 255).uniqueIndex()
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}

object ApiKeys : Table("api_keys") {
    val key = varchar("key", 80)
    val companyId = long("company_id").references(Companies.id)
    val userId = long("user_id").references(Users.id)
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(key)
}
